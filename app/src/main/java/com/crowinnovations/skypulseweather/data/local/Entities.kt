package com.crowinnovations.skypulseweather.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.crowinnovations.skypulseweather.domain.DailyForecastItem
import com.crowinnovations.skypulseweather.domain.HourlyForecastItem
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.WeatherAlert
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "saved_locations",
    indices = [Index(value = ["isGps"]), Index(value = ["sortOrder"])]
)
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val zipCode: String? = null,
    val countryCode: String = "US",
    val isGps: Boolean = false,
    val sortOrder: Int = 0
) {
    fun toDomain(): SavedLocation = SavedLocation(
        id = id,
        name = name,
        subtitle = subtitle,
        latitude = latitude,
        longitude = longitude,
        zipCode = zipCode,
        countryCode = countryCode,
        isGps = isGps,
        sortOrder = sortOrder
    )
}

@Entity(
    tableName = "cached_weather",
    foreignKeys = [
        ForeignKey(
            entity = SavedLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cacheCellKey"])]
)
data class CachedWeatherEntity(
    @PrimaryKey val locationId: Long,
    val cacheCellKey: String,
    val currentTempCelsius: Double,
    val feelsLikeCelsius: Double,
    val highCelsius: Double,
    val lowCelsius: Double,
    val precipProbability: Int,
    val humidityPercent: Int,
    val windMph: Double,
    val uvIndex: Double = -1.0,
    val weatherCode: Int,
    val isDay: Boolean,
    val conditionLabel: String,
    val cloudCoverPercent: Int = -1,
    val hourlyJson: String,
    val dailyJson: String,
    val alertsJson: String,
    val timezoneId: String,
    val fetchedAtMillis: Long,
    val forecastFetchedAtMillis: Long,
    val alertsFetchedAtMillis: Long
) {
    fun toDomain(json: Json): WeatherSnapshot {
        val hourly = runCatching { json.decodeFromString<List<HourlyForecastItem>>(hourlyJson) }
            .getOrDefault(emptyList())
        val daily = runCatching { json.decodeFromString<List<DailyForecastItem>>(dailyJson) }
            .getOrDefault(emptyList())
        val alerts = runCatching { json.decodeFromString<List<WeatherAlert>>(alertsJson) }
            .getOrDefault(emptyList())
        return WeatherSnapshot(
            locationId = locationId,
            cacheCellKey = cacheCellKey,
            currentTempCelsius = currentTempCelsius,
            feelsLikeCelsius = feelsLikeCelsius,
            highCelsius = highCelsius,
            lowCelsius = lowCelsius,
            precipProbability = precipProbability,
            humidityPercent = humidityPercent,
            windMph = windMph,
            uvIndex = uvIndex,
            weatherCode = weatherCode,
            isDay = isDay,
            conditionLabel = conditionLabel,
            cloudCoverPercent = cloudCoverPercent,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
            timezoneId = timezoneId,
            fetchedAtMillis = fetchedAtMillis,
            forecastFetchedAtMillis = forecastFetchedAtMillis,
            alertsFetchedAtMillis = alertsFetchedAtMillis
        )
    }

    companion object {
        fun fromDomain(snapshot: WeatherSnapshot, json: Json): CachedWeatherEntity =
            CachedWeatherEntity(
                locationId = snapshot.locationId,
                cacheCellKey = snapshot.cacheCellKey,
                currentTempCelsius = snapshot.currentTempCelsius,
                feelsLikeCelsius = snapshot.feelsLikeCelsius,
                highCelsius = snapshot.highCelsius,
                lowCelsius = snapshot.lowCelsius,
                precipProbability = snapshot.precipProbability,
                humidityPercent = snapshot.humidityPercent,
                windMph = snapshot.windMph,
                uvIndex = snapshot.uvIndex,
                weatherCode = snapshot.weatherCode,
                isDay = snapshot.isDay,
                conditionLabel = snapshot.conditionLabel,
                cloudCoverPercent = snapshot.cloudCoverPercent,
                hourlyJson = json.encodeToString(snapshot.hourly),
                dailyJson = json.encodeToString(snapshot.daily),
                alertsJson = json.encodeToString(snapshot.alerts),
                timezoneId = snapshot.timezoneId,
                fetchedAtMillis = snapshot.fetchedAtMillis,
                forecastFetchedAtMillis = snapshot.forecastFetchedAtMillis,
                alertsFetchedAtMillis = snapshot.alertsFetchedAtMillis
            )
    }
}
