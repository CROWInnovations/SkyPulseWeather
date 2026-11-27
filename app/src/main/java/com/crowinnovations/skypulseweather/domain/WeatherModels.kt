package com.crowinnovations.skypulseweather.domain

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class TempUnit(val symbol: String, val label: String) {
    FAHRENHEIT("°F", "Fahrenheit (°F)"),
    CELSIUS("°C", "Celsius (°C)");

    fun convert(tempCelsius: Double): Int {
        return when (this) {
            FAHRENHEIT -> (tempCelsius * 9.0 / 5.0 + 32.0).roundToInt()
            CELSIUS -> tempCelsius.roundToInt()
        }
    }

    fun format(tempCelsius: Double, showUnitLetter: Boolean = false): String {
        val value = convert(tempCelsius)
        return if (showUnitLetter) "$value$symbol" else "$value°"
    }
}

enum class WindUnit(val symbol: String, val label: String) {
    MPH("mph", "mph"),
    KMH("km/h", "km/h");

    fun convert(windMph: Double): Int {
        return when (this) {
            MPH -> windMph.roundToInt().coerceAtLeast(0)
            KMH -> (windMph * 1.60934).roundToInt().coerceAtLeast(0)
        }
    }

    fun format(windMph: Double): String = "${convert(windMph)} $symbol"
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class DisplayFontStyle(val label: String) {
    DEFAULT("Default"),
    SERIF("Serif"),
    MONO("Mono"),
    ITALIC("Italic")
}

enum class DisplayTextColor(val label: String, val argb: Int) {
    WHITE("White", 0xFFF8FAFC.toInt()),
    SKY("Sky", 0xFF38BDF8.toInt()),
    GOLD("Gold", 0xFFFBBF24.toInt()),
    ORANGE("Orange", 0xFFFB923C.toInt()),
    MINT("Mint", 0xFF4ADE80.toInt()),
    VIOLET("Violet", 0xFFC084FC.toInt())
}

enum class DateDisplayPosition(val label: String) {
    OFF("Off"),
    LEFT_OF_TIME("Left of Time"),
    RIGHT_OF_TIME("Right of Time"),
    BELOW_TIME("Below Time")
}

enum class BackingTone(val label: String) {
    DARKEN("Darken"),
    DEFAULT("Default"),
    LIGHTEN("Lighten")
}

enum class WeatherCondition(val label: String) {
    CLEAR_DAY("Clear"),
    CLEAR_NIGHT("Clear night"),
    PARTLY_CLOUDY_DAY("Partly cloudy"),
    PARTLY_CLOUDY_NIGHT("Partly cloudy"),
    OVERCAST("Overcast"),
    FOG("Foggy"),
    DRIZZLE("Light rain"),
    RAIN("Rain"),
    SNOW("Snow"),
    THUNDERSTORM("Thunderstorm");

    companion object {
        fun fromWmo(code: Int, isDay: Boolean = true): WeatherCondition {
            return when (code) {
                0 -> if (isDay) CLEAR_DAY else CLEAR_NIGHT
                1, 2 -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
                3 -> OVERCAST
                45, 48 -> FOG
                51, 53, 55, 56, 57 -> DRIZZLE
                61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
                71, 73, 75, 77, 85, 86 -> SNOW
                95, 96, 99 -> THUNDERSTORM
                else -> if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
            }
        }

        fun descriptionFromWmo(code: Int, isDay: Boolean = true): String {
            return when (code) {
                0 -> if (isDay) "Sunny" else "Clear"
                1 -> if (isDay) "Mostly sunny" else "Mostly clear"
                2 -> "Partly cloudy"
                3 -> "Overcast"
                45, 48 -> "Foggy"
                51, 53, 55 -> "Drizzle"
                56, 57 -> "Freezing drizzle"
                61 -> "Light rain"
                63 -> "Moderate rain"
                65 -> "Heavy rain"
                66, 67 -> "Freezing rain"
                71 -> "Light snow"
                73 -> "Snow"
                75 -> "Heavy snow"
                77 -> "Snow grains"
                80, 81 -> "Rain showers"
                82 -> "Heavy showers"
                85, 86 -> "Snow showers"
                95 -> "Thunderstorm"
                96, 99 -> "Severe thunderstorm"
                else -> fromWmo(code, isDay).label
            }
        }
    }
}

data class SavedLocation(
    val id: Long = 0L,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val zipCode: String? = null,
    val countryCode: String = "US",
    val isGps: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class HourlyForecastItem(
    val isoTime: String,
    val displayHour: String,
    val tempCelsius: Double,
    val precipProbability: Int,
    val weatherCode: Int,
    val isDay: Boolean
) {
    val condition: WeatherCondition
        get() = WeatherCondition.fromWmo(weatherCode, isDay)
}

@Serializable
data class DailyForecastItem(
    val isoDate: String,
    val dayLabel: String,
    val shortDayLabel: String,
    val highCelsius: Double,
    val lowCelsius: Double,
    val precipProbability: Int,
    val weatherCode: Int
) {
    val condition: WeatherCondition
        get() = WeatherCondition.fromWmo(weatherCode, isDay = true)

    /**
     * Compact month/day + short weekday format (e.g. "11/24 Tue") for 10-day forecast rows.
     */
    val monthDayWeekLabel: String
        get() = runCatching {
            val parsed = java.time.LocalDate.parse(isoDate)
            val dow = parsed.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.US
            )
            "${parsed.monthValue}/${parsed.dayOfMonth} $dow"
        }.getOrElse { shortDayLabel }

    /**
     * True on rainy / cloud-bearing forecast days where precipitation probability exceeds 50%.
     */
    val showRainPercentBesideCloud: Boolean
        get() = precipProbability > 50 &&
            condition != WeatherCondition.CLEAR_DAY &&
            condition != WeatherCondition.CLEAR_NIGHT
}

@Serializable
data class WeatherAlert(
    val id: String,
    val event: String,
    val severity: String,
    val headline: String,
    val description: String,
    val instruction: String = "",
    val expiresIso: String = ""
)

data class WeatherSnapshot(
    val locationId: Long,
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
    val hourly: List<HourlyForecastItem>,
    val daily: List<DailyForecastItem>,
    val alerts: List<WeatherAlert>,
    val timezoneId: String,
    val fetchedAtMillis: Long,
    val forecastFetchedAtMillis: Long,
    val alertsFetchedAtMillis: Long
) {
    val condition: WeatherCondition
        get() = WeatherCondition.fromWmo(weatherCode, isDay)

    val effectiveCloudCover: Int
        get() = if (cloudCoverPercent in 0..100) {
            cloudCoverPercent
        } else {
            defaultCloudCoverForWmo(weatherCode)
        }

    /**
     * Strictly true ONLY when an active Tornado Warning is in effect for this location.
     */
    val hasTornadoWarning: Boolean
        get() = alerts.any { alert ->
            alert.event.contains("tornado warning", ignoreCase = true)
        }

    /**
     * Enterprise-formatted Wind Speed & EPA/WMO UV Index telemetry line.
     */
    fun formatWindAndUvLine(
        windUnit: WindUnit = WindUnit.MPH,
        compact: Boolean = false
    ): String {
        val parts = mutableListOf<String>()
        if (windMph >= 0.0 && windMph.isFinite()) {
            parts += "Wind ${windUnit.format(windMph)}"
        }
        val safeUv = if (uvIndex >= 0.0 && uvIndex.isFinite()) uvIndex else if (!isDay) 0.0 else -1.0
        if (safeUv >= 0.0) {
            val uvRounded = safeUv.roundToInt().coerceAtLeast(0)
            val category = when {
                uvRounded <= 2 -> "Low"
                uvRounded <= 5 -> if (compact) "Mod" else "Moderate"
                uvRounded <= 7 -> "High"
                uvRounded <= 10 -> if (compact) "V.High" else "Very High"
                else -> "Extreme"
            }
            parts += "UV $uvRounded ($category)"
        }
        val separator = if (compact) " · " else "  ·  "
        return parts.joinToString(separator)
    }

    companion object {
        fun defaultCloudCoverForWmo(code: Int): Int = when (code) {
            0 -> 4
            1 -> 24
            2 -> 56
            3 -> 96
            45, 48 -> 88
            else -> 90
        }
    }
}

data class LocationSearchCandidate(
    val displayName: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val zipCode: String? = null,
    val countryCode: String = "US"
)

object UsStateAbbreviations {
    private val stateMap = mapOf(
        "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
        "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
        "district of columbia" to "DC", "florida" to "FL", "georgia" to "GA", "hawaii" to "HI",
        "idaho" to "ID", "illinois" to "IL", "indiana" to "IN", "iowa" to "IA",
        "kansas" to "KS", "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME",
        "maryland" to "MD", "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN",
        "mississippi" to "MS", "missouri" to "MO", "montana" to "MT", "nebraska" to "NE",
        "nevada" to "NV", "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM",
        "new york" to "NY", "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH",
        "oklahoma" to "OK", "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI",
        "south carolina" to "SC", "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX",
        "utah" to "UT", "vermont" to "VT", "virginia" to "VA", "washington" to "WA",
        "west virginia" to "WV", "wisconsin" to "WI", "wyoming" to "WY", "puerto rico" to "PR"
    )

    fun abbreviate(stateOrRegion: String?): String? {
        if (stateOrRegion.isNullOrBlank()) return null
        val trimmed = stateOrRegion.trim()
        if (trimmed.length == 2 && trimmed.all { it.isLetter() }) return trimmed.uppercase()
        return stateMap[trimmed.lowercase()] ?: trimmed
    }
}
