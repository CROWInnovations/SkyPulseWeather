package com.crowinnovations.skypulseweather.data.repository

import android.content.Context
import com.crowinnovations.skypulseweather.widget.WeatherWidgetPublisher
import com.crowinnovations.skypulseweather.data.local.AppPreferences
import com.crowinnovations.skypulseweather.data.local.CachedWeatherEntity
import com.crowinnovations.skypulseweather.data.local.SavedLocationEntity
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.data.local.WeatherDao
import com.crowinnovations.skypulseweather.data.location.LocationTracker
import com.crowinnovations.skypulseweather.data.remote.GeocodingApi
import com.crowinnovations.skypulseweather.data.remote.NwsApi
import com.crowinnovations.skypulseweather.data.remote.OpenMeteoApi
import com.crowinnovations.skypulseweather.data.remote.OpenMeteoForecastResponse
import com.crowinnovations.skypulseweather.data.remote.ZippopotamApi
import com.crowinnovations.skypulseweather.domain.DailyForecastItem
import com.crowinnovations.skypulseweather.domain.HourlyForecastItem
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.LocationSearchCandidate
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.UsStateAbbreviations
import com.crowinnovations.skypulseweather.domain.WeatherAlert
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.normalizeCacheCell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

sealed class SyncResult {
    data object Updated : SyncResult()
    data object ServedFromFreshCache : SyncResult()
    data class OfflineFallback(val reason: String) : SyncResult()
}

@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weatherDao: WeatherDao,
    private val prefsRepository: UserPreferencesRepository,
    private val openMeteoApi: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
    private val zippopotamApi: ZippopotamApi,
    private val nwsApi: NwsApi,
    private val locationTracker: LocationTracker,
    private val json: Json
) {
    companion object {
        const val DEFAULT_OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val CURRENT_CADENCE_MS = 15L * 60L * 1000L // 15 minutes (Open-Meteo HRRR + WorkManager minimum)
        const val ALERTS_CADENCE_MS = 15L * 60L * 1000L  // 15 minutes
        private val US_ZIP_REGEX = Regex("^\\d{5}(-\\d{4})?$")
        private val HOUR_FORMATTER = DateTimeFormatter.ofPattern("h a", Locale.US)
        private val DAY_SHORT_FORMATTER = DateTimeFormatter.ofPattern("EEE", Locale.US)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cellMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexForCell(cellKey: String): Mutex =
        cellMutexes.computeIfAbsent(cellKey) { Mutex() }

    val locationsFlow: Flow<List<SavedLocation>> =
        weatherDao.observeLocations().map { entities -> entities.map { it.toDomain() } }

    val weatherMapFlow: Flow<Map<Long, WeatherSnapshot>> =
        weatherDao.observeAllWeather().map { entities ->
            entities.associate { entity -> entity.locationId to entity.toDomain(json) }
        }

    suspend fun ensureInitialSeedIfNeeded() = withContext(Dispatchers.IO) {
        if (!prefsRepository.isInitialSeedDone()) {
            val existing = weatherDao.getLocationsOnce()
            if (existing.isEmpty()) {
                val id = weatherDao.insertLocation(
                    SavedLocationEntity(
                        name = "Baltimore, MD",
                        subtitle = "US ZIP 21201 · Maryland",
                        latitude = 39.2946,
                        longitude = -76.6252,
                        zipCode = "21201",
                        countryCode = "US",
                        isGps = false,
                        sortOrder = 0
                    )
                )
                prefsRepository.setActiveLocationId(id)
            }
            prefsRepository.markInitialSeedDone()
        }
    }

    suspend fun searchLocations(rawQuery: String): Result<List<LocationSearchCandidate>> =
        withContext(Dispatchers.IO) {
            val sanitized = InputSecurityValidator.sanitizeSearchInput(rawQuery).trim()
            if (sanitized.isBlank()) return@withContext Result.success(emptyList())
            val query = InputSecurityValidator.validateLocationSearchQuery(sanitized)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Enter a valid 5-digit ZIP code or city name (letters and numbers only).")
                )

            val results = mutableListOf<LocationSearchCandidate>()
            var lastError: Throwable? = null

            // 1. Check if input is a 5-digit US ZIP code (e.g. 21201 or 90210-1234)
            if (US_ZIP_REGEX.matches(query)) {
                val zip5 = query.take(5)
                runCatching {
                    val zipResp = zippopotamApi.lookupUsZip(zip5)
                    zipResp.places.forEach { place ->
                        val lat = place.latitude.toDoubleOrNull()
                        val lon = place.longitude.toDoubleOrNull()
                        if (lat != null && lon != null && InputSecurityValidator.areValidCoordinates(lat, lon)) {
                            val stateAbbr = InputSecurityValidator.sanitizeDisplayText(
                                place.stateAbbreviation.ifBlank {
                                    UsStateAbbreviations.abbreviate(place.state) ?: ""
                                },
                                maxLength = 24
                            )
                            val placeName = InputSecurityValidator.sanitizeDisplayText(place.placeName, maxLength = 80)
                            val stateFull = InputSecurityValidator.sanitizeDisplayText(place.state, maxLength = 60)
                            if (placeName.isNotBlank()) {
                                val display = if (stateAbbr.isNotBlank()) {
                                    "$placeName, $stateAbbr"
                                } else {
                                    placeName
                                }
                                results += LocationSearchCandidate(
                                    displayName = display,
                                    subtitle = "US ZIP $zip5 · $stateFull",
                                    latitude = lat,
                                    longitude = lon,
                                    zipCode = zip5,
                                    countryCode = "US"
                                )
                            }
                        }
                    }
                }.onFailure { lastError = it }
            }

            // 2. Also query Open-Meteo Geocoding for city names or international postal codes
            if (results.isEmpty() || !US_ZIP_REGEX.matches(query)) {
                runCatching {
                    val geoResp = geocodingApi.searchLocations(query = query, count = 8)
                    geoResp.results.orEmpty().forEach { item ->
                        if (!InputSecurityValidator.areValidCoordinates(item.latitude, item.longitude)) {
                            return@forEach
                        }
                        val countryCode = InputSecurityValidator.sanitizeCountryCode(item.countryCode)
                        val cleanName = InputSecurityValidator.sanitizeDisplayText(item.name, maxLength = 80)
                        if (cleanName.isBlank()) return@forEach
                        val cleanAdmin1 = InputSecurityValidator.sanitizeDisplayText(item.admin1, maxLength = 60)
                        val cleanCountry = InputSecurityValidator.sanitizeDisplayText(item.country, maxLength = 60)
                        val stateAbbr = if (countryCode == "US") {
                            UsStateAbbreviations.abbreviate(cleanAdmin1)
                        } else {
                            cleanAdmin1.ifBlank { null }
                        }
                        val display = when {
                            countryCode == "US" && !stateAbbr.isNullOrBlank() ->
                                "$cleanName, $stateAbbr"
                            cleanCountry.isNotBlank() ->
                                "$cleanName, $cleanCountry"
                            else -> cleanName
                        }
                        val subParts = buildList {
                            if (cleanAdmin1.isNotBlank()) add(cleanAdmin1)
                            if (cleanCountry.isNotBlank()) add(cleanCountry)
                            val firstZip = InputSecurityValidator.sanitizeDisplayText(item.postcodes?.firstOrNull(), maxLength = 16)
                            if (firstZip.isNotBlank()) add("Postal $firstZip")
                        }
                        results += LocationSearchCandidate(
                            displayName = display,
                            subtitle = subParts.joinToString(" · ").ifBlank {
                                String.format(Locale.US, "%.2f°, %.2f°", item.latitude, item.longitude)
                            },
                            latitude = item.latitude,
                            longitude = item.longitude,
                            zipCode = InputSecurityValidator.sanitizeDisplayText(item.postcodes?.firstOrNull(), maxLength = 16).ifBlank { null },
                            countryCode = countryCode
                        )
                    }
                }.onFailure { if (results.isEmpty()) lastError = it }
            }

            val deduplicated = results.distinctBy {
                "${(it.latitude * 25).roundToInt()}_${(it.longitude * 25).roundToInt()}"
            }

            if (deduplicated.isEmpty() && lastError != null && US_ZIP_REGEX.matches(query)) {
                Result.success(emptyList())
            } else if (deduplicated.isEmpty() && lastError != null) {
                Result.failure(lastError!!)
            } else {
                Result.success(deduplicated)
            }
        }

    suspend fun addManualLocation(candidate: LocationSearchCandidate): Long =
        withContext(Dispatchers.IO) {
            val existing = weatherDao.getLocationsOnce().firstOrNull {
                !it.isGps && normalizeCacheCell(it.latitude, it.longitude) ==
                    normalizeCacheCell(candidate.latitude, candidate.longitude)
            }
            val id = if (existing != null) {
                existing.id
            } else {
                val nextOrder = weatherDao.getMaxSortOrder() + 1
                weatherDao.insertLocation(
                    SavedLocationEntity(
                        name = candidate.displayName,
                        subtitle = candidate.subtitle,
                        latitude = candidate.latitude,
                        longitude = candidate.longitude,
                        zipCode = candidate.zipCode,
                        countryCode = candidate.countryCode,
                        isGps = false,
                        sortOrder = nextOrder
                    )
                )
            }
            prefsRepository.setActiveLocationId(id)
            refreshLocation(id, force = true)
            notifyWidgetsChanged()
            id
        }

    suspend fun upsertAndRefreshGpsLocation(): Result<Long> = withContext(Dispatchers.IO) {
        val resolved = locationTracker.getCurrentCoordinates()
            ?: return@withContext Result.failure(
                IllegalStateException("Unable to acquire foreground GPS coordinates. Check Location Services or permission.")
            )

        val existingGps = weatherDao.getGpsLocation()
        val locationId = if (existingGps != null) {
            weatherDao.updateLocation(
                existingGps.copy(
                    name = resolved.displayName,
                    subtitle = resolved.subtitle,
                    latitude = resolved.latitude,
                    longitude = resolved.longitude,
                    countryCode = resolved.countryCode
                )
            )
            existingGps.id
        } else {
            weatherDao.insertLocation(
                SavedLocationEntity(
                    name = resolved.displayName,
                    subtitle = resolved.subtitle,
                    latitude = resolved.latitude,
                    longitude = resolved.longitude,
                    countryCode = resolved.countryCode,
                    isGps = true,
                    sortOrder = -1
                )
            )
        }
        prefsRepository.setActiveLocationId(locationId)
        refreshLocation(locationId, force = true)
        notifyWidgetsChanged()
        Result.success(locationId)
    }

    suspend fun deleteLocation(locationId: Long) = withContext(Dispatchers.IO) {
        weatherDao.deleteLocationById(locationId)
        val remaining = weatherDao.getLocationsOnce()
        val prefs = prefsRepository.getPreferencesOnce()
        if (prefs.activeLocationId == locationId) {
            val nextId = remaining.firstOrNull()?.id ?: -1L
            prefsRepository.setActiveLocationId(nextId)
        }
        notifyWidgetsChanged()
    }

    suspend fun refreshAllLocations(
        force: Boolean = false,
        updateGpsIfPermitted: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        ensureInitialSeedIfNeeded()

        if (updateGpsIfPermitted && locationTracker.hasForegroundLocationPermission()) {
            val existingGps = weatherDao.getGpsLocation()
            if (existingGps != null) {
                locationTracker.getCurrentCoordinates()?.let { resolved ->
                    weatherDao.updateLocation(
                        existingGps.copy(
                            name = resolved.displayName,
                            subtitle = resolved.subtitle,
                            latitude = resolved.latitude,
                            longitude = resolved.longitude,
                            countryCode = resolved.countryCode
                        )
                    )
                }
            }
        }

        val locations = weatherDao.getLocationsOnce()
        if (locations.isEmpty()) return@withContext SyncResult.ServedFromFreshCache

        var anyUpdated = false
        var lastOfflineReason: String? = null

        for (loc in locations) {
            when (val res = refreshLocation(loc.id, force = force)) {
                is SyncResult.Updated -> anyUpdated = true
                is SyncResult.ServedFromFreshCache -> Unit
                is SyncResult.OfflineFallback -> lastOfflineReason = res.reason
            }
        }

        notifyWidgetsChanged()

        when {
            anyUpdated -> SyncResult.Updated
            lastOfflineReason != null -> SyncResult.OfflineFallback(lastOfflineReason!!)
            else -> SyncResult.ServedFromFreshCache
        }
    }

    suspend fun refreshLocation(locationId: Long, force: Boolean = false): SyncResult =
        withContext(Dispatchers.IO) {
            val location = weatherDao.getLocationById(locationId)
                ?: return@withContext SyncResult.ServedFromFreshCache

            val cellKey = normalizeCacheCell(location.latitude, location.longitude)
            val mutex = mutexForCell(cellKey)

            mutex.withLock {
                val now = System.currentTimeMillis()
                val existingForLoc = weatherDao.getWeatherForLocation(locationId)

                if (!force &&
                    existingForLoc != null &&
                    existingForLoc.uvIndex >= 0.0 &&
                    existingForLoc.toDomain(json).daily.size >= 10 &&
                    (now - existingForLoc.fetchedAtMillis) < CURRENT_CADENCE_MS
                ) {
                    return@withLock SyncResult.ServedFromFreshCache
                }

                // Reuse fresh geographic cell cache from another saved location in the same 0.02° cell
                val cellCached = weatherDao.getFreshestWeatherForCell(cellKey)
                if (!force &&
                    cellCached != null &&
                    cellCached.uvIndex >= 0.0 &&
                    cellCached.toDomain(json).daily.size >= 10 &&
                    (now - cellCached.fetchedAtMillis) < CURRENT_CADENCE_MS
                ) {
                    weatherDao.upsertWeather(cellCached.copy(locationId = locationId))
                    return@withLock SyncResult.ServedFromFreshCache
                }

                if (!prefsRepository.tryConsumeApiBudget()) {
                    return@withLock SyncResult.OfflineFallback("Daily API budget reached; showing cached forecast.")
                }

                val prefs = prefsRepository.getPreferencesOnce()
                val forecastUrl = when (val validated = InputSecurityValidator.validateHttpsGatewayUrl(prefs.gatewayBaseUrl)) {
                    is InputSecurityValidator.GatewayValidationResult.Valid ->
                        validated.normalizedUrl.ifBlank { DEFAULT_OPEN_METEO_FORECAST_URL }
                    is InputSecurityValidator.GatewayValidationResult.Invalid ->
                        DEFAULT_OPEN_METEO_FORECAST_URL
                }

                val forecastResult = runCatching {
                    openMeteoApi.getForecast(
                        url = forecastUrl,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }

                val forecastResp = forecastResult.getOrElse { err ->
                    return@withLock SyncResult.OfflineFallback(
                        err.localizedMessage ?: "Offline — showing cached forecast."
                    )
                }

                val shouldFetchAlerts = location.countryCode.equals("US", ignoreCase = true) &&
                    (force || existingForLoc == null || (now - existingForLoc.alertsFetchedAtMillis) >= ALERTS_CADENCE_MS)

                val alerts: List<WeatherAlert> = if (shouldFetchAlerts) {
                    runCatching {
                        val point = String.format(
                            Locale.US,
                            "%.4f,%.4f",
                            location.latitude,
                            location.longitude
                        )
                        nwsApi.getActiveAlerts(point).features.mapNotNull { feature ->
                            val p = feature.properties ?: return@mapNotNull null
                            val event = InputSecurityValidator.sanitizeDisplayText(p.event, maxLength = 100)
                                .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            WeatherAlert(
                                id = InputSecurityValidator.sanitizeDisplayText(p.id ?: feature.id, maxLength = 120)
                                    .ifBlank { event },
                                event = event,
                                severity = InputSecurityValidator.sanitizeDisplayText(p.severity, maxLength = 40)
                                    .ifBlank { "Advisory" },
                                headline = InputSecurityValidator.sanitizeDisplayText(p.headline, maxLength = 240)
                                    .ifBlank { event },
                                description = InputSecurityValidator.sanitizeMultilineAlertText(p.description),
                                instruction = InputSecurityValidator.sanitizeMultilineAlertText(p.instruction),
                                expiresIso = InputSecurityValidator.sanitizeDisplayText(p.expires, maxLength = 48)
                            )
                        }.take(4)
                    }.getOrElse {
                        existingForLoc?.toDomain(json)?.alerts ?: emptyList()
                    }
                } else {
                    existingForLoc?.toDomain(json)?.alerts ?: emptyList()
                }

                val snapshot = mapForecastToSnapshot(
                    locationId = locationId,
                    cellKey = cellKey,
                    response = forecastResp,
                    alerts = alerts,
                    nowMillis = now,
                    alertsFetchedMillis = if (shouldFetchAlerts) now else (existingForLoc?.alertsFetchedAtMillis ?: now)
                )

                weatherDao.upsertWeather(CachedWeatherEntity.fromDomain(snapshot, json))
                SyncResult.Updated
            }
        }

    /**
     * Atomically updates the active (checked) location in an application-scoped coroutine
     * that survives Activity backgrounding, immediately pushing the checked location to all
     * Home-Screen Widgets and then refreshing stale forecast data if needed.
     */
    fun selectActiveLocation(locationId: Long) {
        if (locationId <= 0L) return
        appScope.launch {
            prefsRepository.setActiveLocationId(locationId)
            notifyWidgetsChanged()
            val syncResult = refreshLocation(locationId, force = false)
            if (syncResult is SyncResult.Updated) {
                notifyWidgetsChanged()
            }
        }
    }

    suspend fun notifyWidgetsChanged() {
        runCatching {
            prefsRepository.setWidgetForecastDayOffset(0)
            WeatherWidgetPublisher.publishAll(context, explicitScrollOffset = 0)
        }
    }

    /**
     * Preloads initial state for Jetpack Glance widgets, ensuring the currently checked
     * (`activeLocationId`) location has up-to-date weather + UV telemetry before frame 1.
     */
    suspend fun getWidgetInitialState(): Triple<List<SavedLocation>, Map<Long, WeatherSnapshot>, AppPreferences> =
        withContext(Dispatchers.IO) {
            ensureInitialSeedIfNeeded()
            val prefs = prefsRepository.getPreferencesOnce()
            val allLocations = locationsFlow.first()
            val chosen = allLocations.firstOrNull { it.id == prefs.activeLocationId }
                ?: allLocations.firstOrNull()

            var weatherMap = weatherMapFlow.first()
            if (chosen != null) {
                val cached = weatherMap[chosen.id]
                if (cached == null || cached.uvIndex < 0.0 || cached.daily.size < 10) {
                    refreshLocation(chosen.id, force = false)
                    weatherMap = weatherMapFlow.first()
                }
            }
            Triple(allLocations, weatherMap, prefs)
        }

    private fun mapForecastToSnapshot(
        locationId: Long,
        cellKey: String,
        response: OpenMeteoForecastResponse,
        alerts: List<WeatherAlert>,
        nowMillis: Long,
        alertsFetchedMillis: Long
    ): WeatherSnapshot {
        val zoneId = runCatching { ZoneId.of(response.timezone) }.getOrDefault(ZoneId.systemDefault())
        val nowLocalHour = LocalDateTime.now(zoneId).truncatedTo(ChronoUnit.HOURS)
        val todayDate = LocalDate.now(zoneId)

        val hourlyDto = response.hourly
        val hourlyItems = mutableListOf<HourlyForecastItem>()
        if (hourlyDto != null) {
            for (i in hourlyDto.time.indices) {
                val rawTime = hourlyDto.time[i]
                val parsed = runCatching { LocalDateTime.parse(rawTime) }.getOrNull() ?: continue
                if (parsed.isBefore(nowLocalHour)) continue
                val temp = hourlyDto.temperature2m.getOrNull(i) ?: continue
                val precip = hourlyDto.precipitationProbability.getOrNull(i) ?: 0
                val code = hourlyDto.weatherCode.getOrNull(i) ?: 0
                val isDayHour = (hourlyDto.isDay.getOrNull(i) ?: 1) == 1
                val label = if (hourlyItems.isEmpty()) "Now" else parsed.format(HOUR_FORMATTER)
                hourlyItems += HourlyForecastItem(
                    isoTime = rawTime,
                    displayHour = label,
                    tempCelsius = temp,
                    precipProbability = precip,
                    weatherCode = code,
                    isDay = isDayHour
                )
                if (hourlyItems.size >= 24) break
            }
        }

        val dailyDto = response.daily
        val dailyItems = mutableListOf<DailyForecastItem>()
        if (dailyDto != null) {
            for (i in dailyDto.time.indices) {
                val rawDate = dailyDto.time[i]
                val parsedDate = runCatching { LocalDate.parse(rawDate) }.getOrNull() ?: continue
                val high = dailyDto.temperatureMax.getOrNull(i) ?: continue
                val low = dailyDto.temperatureMin.getOrNull(i) ?: continue
                val precip = dailyDto.precipitationProbabilityMax.getOrNull(i) ?: 0
                val code = dailyDto.weatherCode.getOrNull(i) ?: 0
                val shortDay = parsedDate.format(DAY_SHORT_FORMATTER)
                val dayLabel = if (parsedDate == todayDate || i == 0) "Today" else shortDay
                dailyItems += DailyForecastItem(
                    isoDate = rawDate,
                    dayLabel = dayLabel,
                    shortDayLabel = shortDay,
                    highCelsius = high,
                    lowCelsius = low,
                    precipProbability = precip,
                    weatherCode = code
                )
                if (dailyItems.size >= 10) break
            }
        }

        val current = response.current
        val currentTemp = current?.temperature2m ?: hourlyItems.firstOrNull()?.tempCelsius ?: 20.0
        val feelsLike = current?.apparentTemperature ?: currentTemp
        val isDay = (current?.isDay ?: 1) == 1
        val weatherCode = current?.weatherCode ?: dailyItems.firstOrNull()?.weatherCode ?: 0
        val todayItem = dailyItems.firstOrNull()
        val highTemp = todayItem?.highCelsius ?: maxOf(currentTemp, feelsLike)
        val lowTemp = todayItem?.lowCelsius ?: minOf(currentTemp, feelsLike)
        val currentPrecipProb = maxOf(
            hourlyItems.firstOrNull()?.precipProbability ?: 0,
            todayItem?.precipProbability ?: 0
        )

        return WeatherSnapshot(
            locationId = locationId,
            cacheCellKey = cellKey,
            currentTempCelsius = currentTemp,
            feelsLikeCelsius = feelsLike,
            highCelsius = highTemp,
            lowCelsius = lowTemp,
            precipProbability = currentPrecipProb,
            humidityPercent = current?.relativeHumidity2m ?: 0,
            windMph = current?.windSpeed10m ?: -1.0,
            uvIndex = current?.uvIndex ?: (if (!isDay) 0.0 else -1.0),
            weatherCode = weatherCode,
            isDay = isDay,
            conditionLabel = WeatherCondition.descriptionFromWmo(weatherCode, isDay),
            cloudCoverPercent = current?.cloudCover ?: WeatherSnapshot.defaultCloudCoverForWmo(weatherCode),
            hourly = hourlyItems,
            daily = dailyItems,
            alerts = alerts,
            timezoneId = response.timezone,
            fetchedAtMillis = nowMillis,
            forecastFetchedAtMillis = nowMillis,
            alertsFetchedAtMillis = alertsFetchedMillis
        )
    }
}
