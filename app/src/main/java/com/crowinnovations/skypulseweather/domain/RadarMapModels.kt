package com.crowinnovations.skypulseweather.domain

import androidx.compose.ui.geometry.Offset
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

enum class RadarBaseMapStyle(val label: String) {
    SATELLITE_HYBRID("Satellite"),
    STREET_MAP("Street Map")
}

enum class RadarLayerMode(val label: String) {
    RADAR_AND_CLOUDS("Radar + Clouds"),
    RADAR_ONLY("Radar"),
    CLOUDS_ONLY("Clouds")
}

enum class HazardPolygonKind(
    val shortBadge: String,
    val symbol: String,
    val priority: Int,
    val isWarning: Boolean
) {
    TORNADO_WARNING("TORNADO WARN", "\uD83C\uDF2A\uFE0F", 100, true),
    TORNADO_WATCH("TORNADO WATCH", "\uD83C\uDF2A\uFE0F", 90, false),
    HURRICANE_WARNING("HURRICANE", "\uD83C\uDF00", 95, true),
    HURRICANE_WATCH("TROPICAL WATCH", "\uD83C\uDF00", 85, false),
    SEVERE_THUNDERSTORM_WARNING("SVR T-STORM", "\u26C8\uFE0F", 80, true),
    SEVERE_THUNDERSTORM_WATCH("SVR WATCH", "\u26C8\uFE0F", 70, false),
    FLASH_FLOOD_WARNING("FLASH FLOOD", "\uD83C\uDF0A", 78, true),
    WINTER_STORM_WARNING("WINTER STORM", "\u2744\uFE0F", 75, true),
    WINTER_WEATHER_ADVISORY("WINTER ADV", "\uD83C\uDF28\uFE0F", 60, false),
    OTHER_SEVERE("WEATHER ALERT", "\u26A0\uFE0F", 50, false)
}

data class LatLonPoint(
    val latitude: Double,
    val longitude: Double
)

data class GeoBounds(
    val northLat: Double,
    val southLat: Double,
    val westLon: Double,
    val eastLon: Double,
    val widthMiles: Double,
    val heightMiles: Double,
    val areaSquareMiles: Double
) {
    fun contains(lat: Double, lon: Double, marginDeg: Double = 0.25): Boolean {
        return lat in (southLat - marginDeg)..(northLat + marginDeg) &&
            lon in (westLon - marginDeg)..(eastLon + marginDeg)
    }

    fun formattedCoverageBadge(): String {
        val w = widthMiles.coerceAtLeast(1.0)
        val h = heightMiles.coerceAtLeast(1.0)
        val sqMi = areaSquareMiles.coerceAtLeast(1.0)
        return when {
            sqMi < 100.0 -> "~${sqMi.roundToInt()} sq mi · Local"
            sqMi < 4_000.0 -> "~${formatCompactInt(sqMi.roundToInt())} sq mi (${w.roundToInt()}×${h.roundToInt()} mi)"
            sqMi < 45_000.0 -> "~${formatCompactInt(sqMi.roundToInt())} sq mi · Regional"
            sqMi < 950_000.0 -> "Multi-State · ${w.roundToInt()}×${h.roundToInt()} mi"
            else -> "Continental · ${w.roundToInt()}×${h.roundToInt()} mi"
        }
    }

    private fun formatCompactInt(value: Int): String = String.format(Locale.US, "%,d", value)
}

data class RadarTimelineFrame(
    val index: Int,
    val offsetMinutes: Int,
    val epochMillis: Long,
    val isForecast: Boolean,
    val relativeLabel: String,
    val clockLabel: String,
    val iemNexradLayer: String?,
    val iemHrrrLayer: String?,
    val includeMrmsPrecipType: Boolean,
    val gibsIsoTimestamp: String,
    val forecastHourStep: Int
)

data class SevereHazardFeature(
    val id: String,
    val kind: HazardPolygonKind,
    val eventTitle: String,
    val headline: String,
    val areaDesc: String,
    val rings: List<List<LatLonPoint>>,
    val centroid: LatLonPoint,
    val categoryOrThreat: String? = null,
    val windSpeedText: String? = null,
    val hailSizeText: String? = null,
    val expectedRainOrSnowText: String? = null,
    val expiresText: String? = null
) {
    fun mapCalloutSummary(): String {
        val parts = mutableListOf<String>()
        parts += "${kind.symbol} ${kind.shortBadge}"
        if (!categoryOrThreat.isNullOrBlank()) {
            parts += categoryOrThreat
        }
        if (!windSpeedText.isNullOrBlank()) {
            parts += windSpeedText
        }
        return parts.joinToString(" · ")
    }
}

data class TropicalCycloneFeature(
    val id: String,
    val name: String,
    val categoryBadge: String,
    val maxSustainedWindMph: Int,
    val gustWindMph: Int? = null,
    val movementText: String? = null,
    val expectedRainfallInches: String,
    val center: LatLonPoint,
    val forecastTrack: List<LatLonPoint> = emptyList(),
    val coneRings: List<List<LatLonPoint>> = emptyList()
) {
    fun mapCalloutSummary(windUnit: WindUnit): String {
        val windFormatted = windUnit.format(maxSustainedWindMph.toDouble())
        return "\uD83C\uDF00 $name ($categoryBadge) · $windFormatted · Rain $expectedRainfallInches"
    }
}

data class RegionalPrecipMarker(
    val id: String,
    val label: String,
    val point: LatLonPoint,
    val isPrimaryLocation: Boolean,
    val expectedRainInches: Double,
    val expectedSnowInches: Double,
    val weatherCode: Int,
    val hourlyCloudCoverPct: List<Int> = emptyList(),
    val hourlyRainInches: List<Double> = emptyList(),
    val hourlySnowInches: List<Double> = emptyList()
) {
    val hasSignificantSnow: Boolean
        get() = expectedSnowInches >= 0.05

    val hasSignificantRain: Boolean
        get() = expectedRainInches >= 0.02

    fun formattedBadgeText(): String? {
        val snowStr = formatInches(expectedSnowInches)
        val rainStr = formatInches(expectedRainInches)
        return when {
            hasSignificantSnow && hasSignificantRain -> "\uD83C\uDF28\uFE0F Snow $snowStr · Rain $rainStr"
            hasSignificantSnow -> "\u2744\uFE0F Snow $snowStr"
            hasSignificantRain -> "\uD83C\uDF27\uFE0F Rain $rainStr"
            isPrimaryLocation -> "\u2601\uFE0F Dry (0.0\")"
            else -> null
        }
    }

    companion object {
        fun formatInches(inches: Double): String {
            val safe = inches.coerceAtLeast(0.0)
            return when {
                safe < 0.01 -> "0.0\""
                safe < 1.0 -> String.format(Locale.US, "%.2f\"", safe)
                else -> String.format(Locale.US, "%.1f\"", safe)
            }
        }
    }
}

sealed interface SelectedRadarOverlayItem {
    data class Hazard(val feature: SevereHazardFeature) : SelectedRadarOverlayItem
    data class Cyclone(val cyclone: TropicalCycloneFeature) : SelectedRadarOverlayItem
    data class Precip(val marker: RegionalPrecipMarker) : SelectedRadarOverlayItem
}

object RadarProjection {
    // 512px (@2x retina mobile slippy-tile display size) keeps map labels crisp and tile loads fast on high-DPI phones
    const val TILE_SIZE_PX = 512.0
    const val MIN_ZOOM = 3.0f
    const val MAX_ZOOM = 13.8f
    const val DEFAULT_LOCAL_ZOOM = 7.2f
    private const val MAX_MERCATOR_LAT = 85.05112878
    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686

    fun clampLatitude(lat: Double): Double = lat.safeClamp(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)

    fun normalizeLongitude(lon: Double): Double {
        if (!lon.isFinite()) return 0.0
        var wrapped = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (wrapped == -180.0 && lon > 0.0) wrapped = 180.0
        return wrapped
    }

    fun lonToWorldX(lon: Double): Double {
        return (normalizeLongitude(lon) + 180.0) / 360.0
    }

    fun latToWorldY(lat: Double): Double {
        val clamped = clampLatitude(lat)
        val rad = Math.toRadians(clamped)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    }

    fun worldXToLon(worldX: Double): Double {
        val wrapped = ((worldX % 1.0) + 1.0) % 1.0
        return wrapped * 360.0 - 180.0
    }

    fun worldYToLat(worldY: Double): Double {
        val clamped = worldY.safeClamp(0.0, 1.0)
        val n = PI - 2.0 * PI * clamped
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    fun worldSizePx(zoom: Float): Double {
        return TILE_SIZE_PX * 2.0.pow(zoom.toDouble())
    }

    fun latLonToScreenOffset(
        lat: Double,
        lon: Double,
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Offset {
        val worldPx = worldSizePx(zoom)
        val cx = lonToWorldX(centerLon) * worldPx
        val cy = latToWorldY(centerLat) * worldPx
        var px = lonToWorldX(lon) * worldPx
        val py = latToWorldY(lat) * worldPx
        val dx = px - cx
        if (dx > worldPx / 2.0) px -= worldPx
        if (dx < -worldPx / 2.0) px += worldPx
        return Offset(
            x = (viewportWidthPx / 2.0 + (px - cx)).toFloat(),
            y = (viewportHeightPx / 2.0 + (py - cy)).toFloat()
        )
    }

    fun screenOffsetToLatLon(
        screenX: Float,
        screenY: Float,
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): LatLonPoint {
        val worldPx = worldSizePx(zoom)
        val cx = lonToWorldX(centerLon) * worldPx
        val cy = latToWorldY(centerLat) * worldPx
        val targetWorldX = (cx + (screenX - viewportWidthPx / 2.0)) / worldPx
        val targetWorldY = ((cy + (screenY - viewportHeightPx / 2.0)) / worldPx).safeClamp(0.02, 0.98)
        return LatLonPoint(
            latitude = worldYToLat(targetWorldY),
            longitude = worldXToLon(targetWorldX)
        )
    }

    fun tileWebMercatorBbox3857(z: Int, x: Int, y: Int): String {
        val n = 2.0.pow(z.toDouble())
        val halfCircumference = EARTH_CIRCUMFERENCE_METERS / 2.0
        val minX = (x / n) * EARTH_CIRCUMFERENCE_METERS - halfCircumference
        val maxX = ((x + 1) / n) * EARTH_CIRCUMFERENCE_METERS - halfCircumference
        val maxY = halfCircumference - (y / n) * EARTH_CIRCUMFERENCE_METERS
        val minY = halfCircumference - ((y + 1) / n) * EARTH_CIRCUMFERENCE_METERS
        return String.format(Locale.US, "%.2f,%.2f,%.2f,%.2f", minX, minY, maxX, maxY)
    }

    fun computeViewportBounds(
        centerLat: Double,
        centerLon: Double,
        zoom: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): GeoBounds {
        val safeW = viewportWidthPx.coerceAtLeast(120f)
        val safeH = viewportHeightPx.coerceAtLeast(120f)
        val topLeft = screenOffsetToLatLon(0f, 0f, centerLat, centerLon, zoom, safeW, safeH)
        val bottomRight = screenOffsetToLatLon(safeW, safeH, centerLat, centerLon, zoom, safeW, safeH)

        val north = maxOf(topLeft.latitude, bottomRight.latitude)
        val south = minOf(topLeft.latitude, bottomRight.latitude)
        val latSpan = (north - south).coerceAtLeast(0.01)

        val worldPx = worldSizePx(zoom)
        val lonSpanDeg = ((safeW / worldPx) * 360.0).safeClamp(0.01, 350.0)
        val west = normalizeLongitude(centerLon - lonSpanDeg / 2.0)
        val east = normalizeLongitude(centerLon + lonSpanDeg / 2.0)

        val milesPerDegLat = 69.055
        val milesPerDegLon = (69.172 * cos(Math.toRadians(clampLatitude(centerLat)))).coerceAtLeast(12.0)
        val widthMiles = (lonSpanDeg * milesPerDegLon).coerceAtLeast(1.0)
        val heightMiles = (latSpan * milesPerDegLat).coerceAtLeast(1.0)

        return GeoBounds(
            northLat = north,
            southLat = south,
            westLon = west,
            eastLon = east,
            widthMiles = widthMiles,
            heightMiles = heightMiles,
            areaSquareMiles = widthMiles * heightMiles
        )
    }

    fun buildTimelineFrames(nowEpochMillis: Long = System.currentTimeMillis()): List<RadarTimelineFrame> {
        // Snap to 10-minute UTC boundary; allow 20-minute satellite ingest safety lag for GIBS timestamps
        val tenMinMs = 10 * 60_000L
        val snappedNowMs = (nowEpochMillis / tenMinMs) * tenMinMs
        val gibsLatestSafeMs = snappedNowMs - (20 * 60_000L)
        val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            .withZone(ZoneId.systemDefault())
        val gibsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:00'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)

        data class FrameSpec(
            val offsetMin: Int,
            val isForecast: Boolean,
            val relLabel: String,
            val nexradLayer: String?,
            val hrrrLayer: String?,
            val forecastStepH: Int
        )

        val specs = listOf(
            FrameSpec(-110, false, "-1h 50m", "nexrad-n0q-900913-m50m", null, 0),
            FrameSpec(-90, false, "-1h 30m", "nexrad-n0q-900913-m45m", null, 0),
            FrameSpec(-70, false, "-1h 10m", "nexrad-n0q-900913-m35m", null, 0),
            FrameSpec(-50, false, "-50m", "nexrad-n0q-900913-m50m", null, 0),
            FrameSpec(-40, false, "-40m", "nexrad-n0q-900913-m40m", null, 0),
            FrameSpec(-30, false, "-30m", "nexrad-n0q-900913-m30m", null, 0),
            FrameSpec(-20, false, "-20m", "nexrad-n0q-900913-m20m", null, 0),
            FrameSpec(-10, false, "-10m", "nexrad-n0q-900913-m10m", null, 0),
            // Quality-controlled NOAA MRMS Seamless Hybrid Scan Reflectivity on LIVE NOW (zero clear-air clutter)
            FrameSpec(0, false, "LIVE NOW", "q2-hsr-900913", null, 0),
            FrameSpec(60, true, "+1h Fcst", null, "hrrr::REFD-F0060-0", 1),
            FrameSpec(120, true, "+2h Fcst", null, "hrrr::REFD-F0120-0", 2),
            FrameSpec(180, true, "+3h Fcst", null, "hrrr::REFD-F0180-0", 3),
            FrameSpec(360, true, "+6h Fcst", null, "hrrr::REFD-F0360-0", 6),
            FrameSpec(540, true, "+9h Fcst", null, "hrrr::REFD-F0540-0", 9),
            FrameSpec(720, true, "+12h Fcst", null, "hrrr::REFD-F0720-0", 12)
        )

        return specs.mapIndexed { idx, s ->
            val frameMs = snappedNowMs + s.offsetMin * 60_000L
            val gibsMs = minOf(frameMs, gibsLatestSafeMs)
            RadarTimelineFrame(
                index = idx,
                offsetMinutes = s.offsetMin,
                epochMillis = frameMs,
                isForecast = s.isForecast,
                relativeLabel = s.relLabel,
                clockLabel = clockFmt.format(Instant.ofEpochMilli(frameMs)),
                iemNexradLayer = s.nexradLayer,
                iemHrrrLayer = s.hrrrLayer,
                includeMrmsPrecipType = (s.offsetMin == 0),
                gibsIsoTimestamp = gibsFmt.format(Instant.ofEpochMilli(gibsMs)),
                forecastHourStep = s.forecastStepH
            )
        }
    }

    private data class UsStateBox(
        val code: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    ) {
        val centerLat: Double get() = (minLat + maxLat) * 0.5
        val centerLon: Double get() = (minLon + maxLon) * 0.5
    }

    private val US_STATE_BOXES = listOf(
        UsStateBox("AL", 30.1, 35.0, -88.5, -84.9),
        UsStateBox("AK", 51.2, 71.4, -179.1, -129.9),
        UsStateBox("AZ", 31.3, 37.0, -114.8, -109.0),
        UsStateBox("AR", 33.0, 36.5, -94.6, -89.6),
        UsStateBox("CA", 32.5, 42.0, -124.5, -114.1),
        UsStateBox("CO", 37.0, 41.0, -109.1, -102.0),
        UsStateBox("CT", 41.0, 42.1, -73.7, -71.8),
        UsStateBox("DE", 38.4, 39.8, -75.8, -75.0),
        UsStateBox("DC", 38.8, 39.0, -77.1, -76.9),
        UsStateBox("FL", 24.5, 31.0, -87.6, -80.0),
        UsStateBox("GA", 30.4, 35.0, -85.6, -80.8),
        UsStateBox("HI", 18.9, 22.3, -160.3, -154.8),
        UsStateBox("ID", 42.0, 49.0, -117.2, -111.0),
        UsStateBox("IL", 37.0, 42.5, -91.5, -87.5),
        UsStateBox("IN", 37.8, 41.8, -88.1, -84.8),
        UsStateBox("IA", 40.4, 43.5, -96.6, -90.1),
        UsStateBox("KS", 37.0, 40.0, -102.1, -94.6),
        UsStateBox("KY", 36.5, 39.1, -89.6, -81.9),
        UsStateBox("LA", 28.9, 33.0, -94.0, -88.8),
        UsStateBox("ME", 43.1, 47.5, -71.1, -66.9),
        UsStateBox("MD", 37.9, 39.7, -79.5, -75.0),
        UsStateBox("MA", 41.2, 42.9, -73.5, -69.9),
        UsStateBox("MI", 41.7, 48.3, -90.4, -82.4),
        UsStateBox("MN", 43.5, 49.4, -97.2, -89.5),
        UsStateBox("MS", 30.2, 35.0, -91.7, -88.1),
        UsStateBox("MO", 36.0, 40.6, -95.8, -89.1),
        UsStateBox("MT", 44.4, 49.0, -116.1, -104.0),
        UsStateBox("NE", 40.0, 43.0, -104.1, -95.3),
        UsStateBox("NV", 35.0, 42.0, -120.0, -114.0),
        UsStateBox("NH", 42.7, 45.3, -72.6, -70.6),
        UsStateBox("NJ", 38.9, 41.4, -75.6, -73.9),
        UsStateBox("NM", 31.3, 37.0, -109.1, -103.0),
        UsStateBox("NY", 40.5, 45.0, -79.8, -71.8),
        UsStateBox("NC", 33.8, 36.6, -84.3, -75.4),
        UsStateBox("ND", 45.9, 49.0, -104.1, -96.6),
        UsStateBox("OH", 38.4, 42.0, -84.8, -80.5),
        UsStateBox("OK", 33.6, 37.0, -103.0, -94.4),
        UsStateBox("OR", 42.0, 46.3, -124.6, -116.5),
        UsStateBox("PA", 39.7, 42.3, -80.5, -74.7),
        UsStateBox("RI", 41.1, 42.0, -71.9, -71.1),
        UsStateBox("SC", 32.0, 35.2, -83.4, -78.5),
        UsStateBox("SD", 42.5, 45.9, -104.1, -96.4),
        UsStateBox("TN", 35.0, 36.7, -90.3, -81.6),
        UsStateBox("TX", 25.8, 36.5, -106.6, -93.5),
        UsStateBox("UT", 37.0, 42.0, -114.1, -109.0),
        UsStateBox("VT", 42.7, 45.0, -73.4, -71.5),
        UsStateBox("VA", 36.5, 39.5, -83.7, -75.2),
        UsStateBox("WA", 45.5, 49.0, -124.8, -116.9),
        UsStateBox("WV", 37.2, 40.6, -82.6, -77.7),
        UsStateBox("WI", 42.5, 47.1, -92.9, -86.8),
        UsStateBox("WY", 41.0, 45.0, -111.1, -104.0),
        UsStateBox("PR", 17.8, 18.6, -67.3, -65.2)
    )

    fun intersectingUsStates(bounds: GeoBounds, centerLat: Double, centerLon: Double, maxStates: Int = 10): List<String> {
        if (bounds.westLon > bounds.eastLon) {
            return emptyList()
        }
        return US_STATE_BOXES
            .filter { st ->
                st.maxLat >= bounds.southLat &&
                    st.minLat <= bounds.northLat &&
                    st.maxLon >= bounds.westLon &&
                    st.minLon <= bounds.eastLon
            }
            .sortedBy { st ->
                val dLat = st.centerLat - centerLat
                val dLon = st.centerLon - centerLon
                dLat * dLat + dLon * dLon
            }
            .take(max(1, maxStates))
            .map { it.code }
    }
}
