package com.crowinnovations.skypulseweather.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.crowinnovations.skypulseweather.data.remote.NwsApi
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.UsStateAbbreviations
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ResolvedCoordinates(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val subtitle: String,
    val countryCode: String
)

@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val nwsApi: NwsApi
) {
    fun hasForegroundLocationPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentCoordinates(): ResolvedCoordinates? {
        if (!hasForegroundLocationPermission()) return null

        val location: Location = acquirePlatformOrFusedLocation() ?: return null
        if (!InputSecurityValidator.areValidCoordinates(location.latitude, location.longitude)) {
            return null
        }
        val resolved = reverseGeocode(location.latitude, location.longitude)
        return resolved.copy(
            displayName = InputSecurityValidator.sanitizeDisplayText(resolved.displayName).ifBlank { "Current Location" },
            subtitle = InputSecurityValidator.sanitizeDisplayText(resolved.subtitle).ifBlank { "Current GPS Location" },
            countryCode = InputSecurityValidator.sanitizeCountryCode(resolved.countryCode)
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun acquirePlatformOrFusedLocation(): Location? {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // 1. Try Play Services FusedLocationProviderClient with crisp 3.5s bound
        val fusedLive = withTimeoutOrNull(3_500L) {
            runCatching {
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cts.token
                ).await()
            }.getOrNull()
        }
        if (fusedLive != null) return fusedLive

        val fusedLast = withTimeoutOrNull(1_500L) {
            runCatching { fusedLocationClient.lastLocation.await() }.getOrNull()
        }
        if (fusedLast != null) return fusedLast

        if (locationManager == null) return null

        // 2. Fallback to Android platform LocationManager (handles hardware GPS and AOSP location providers)
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            val lastKnown = runCatching {
                if (locationManager.isProviderEnabled(provider) ||
                    provider == LocationManager.PASSIVE_PROVIDER
                ) {
                    locationManager.getLastKnownLocation(provider)
                } else null
            }.getOrNull()
            if (lastKnown != null) return lastKnown
        }

        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val enabled = runCatching { locationManager.isProviderEnabled(provider) }
                .getOrDefault(false)
            if (!enabled) continue

            val livePlatform = withTimeoutOrNull(4_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    @Suppress("DEPRECATION")
                    val signal = androidx.core.os.CancellationSignal()
                    cont.invokeOnCancellation {
                        @Suppress("DEPRECATION")
                        signal.cancel()
                    }
                    runCatching {
                        @Suppress("DEPRECATION")
                        LocationManagerCompat.getCurrentLocation(
                            locationManager,
                            provider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (livePlatform != null) return livePlatform
        }

        return null
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): ResolvedCoordinates =
        withContext(Dispatchers.IO) {
            // 1. Try Android platform Geocoder first
            val geocoderResult = runCatching {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.US)
                    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lon, 1) { list ->
                                if (cont.isActive) cont.resume(list)
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lon, 1) ?: emptyList()
                    }
                    addresses.firstOrNull()
                } else null
            }.getOrNull()

            if (geocoderResult != null) {
                val city = geocoderResult.locality
                    ?: geocoderResult.subAdminArea
                    ?: geocoderResult.featureName
                val region = UsStateAbbreviations.abbreviate(geocoderResult.adminArea)
                val country = geocoderResult.countryCode ?: "US"
                if (!city.isNullOrBlank()) {
                    val display = if (!region.isNullOrBlank() && country.equals("US", ignoreCase = true)) {
                        "$city, $region"
                    } else if (!geocoderResult.countryName.isNullOrBlank()) {
                        "$city, ${geocoderResult.countryName}"
                    } else {
                        city
                    }
                    return@withContext ResolvedCoordinates(
                        latitude = lat,
                        longitude = lon,
                        displayName = display,
                        subtitle = "Current GPS Location",
                        countryCode = country
                    )
                }
            }

            // 2. Fallback to NOAA/NWS /points/{lat},{lon} for US coordinates
            val nwsPlace = runCatching {
                val latStr = String.format(Locale.US, "%.4f", lat)
                val lonStr = String.format(Locale.US, "%.4f", lon)
                nwsApi.getPointMetadata(latStr, lonStr)
                    .properties
                    ?.relativeLocation
                    ?.properties
            }.getOrNull()

            if (!nwsPlace?.city.isNullOrBlank()) {
                val state = nwsPlace?.state?.takeIf { it.isNotBlank() }
                val display = if (state != null) "${nwsPlace.city}, $state" else nwsPlace!!.city!!
                return@withContext ResolvedCoordinates(
                    latitude = lat,
                    longitude = lon,
                    displayName = display,
                    subtitle = "Current GPS Location",
                    countryCode = "US"
                )
            }

            // 3. Coordinate fallback when offline without cached geocode
            val coordLabel = String.format(Locale.US, "%.2f°, %.2f°", lat, lon)
            ResolvedCoordinates(
                latitude = lat,
                longitude = lon,
                displayName = "Current Location",
                subtitle = "GPS ($coordLabel)",
                countryCode = "US"
            )
        }
}
