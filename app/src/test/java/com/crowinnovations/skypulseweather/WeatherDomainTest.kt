package com.crowinnovations.skypulseweather

import com.crowinnovations.skypulseweather.domain.DailyForecastItem
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.LunarPhaseCalculator
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.UsStateAbbreviations
import com.crowinnovations.skypulseweather.domain.WeatherAlert
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.normalizeCacheCell
import com.crowinnovations.skypulseweather.domain.safeClamp
import com.crowinnovations.skypulseweather.widget.WidgetBitmapRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherDomainTest {

    @Test
    fun tempUnitConvertsAccurately() {
        assertEquals("68°", TempUnit.FAHRENHEIT.format(20.0))
        assertEquals("32°F", TempUnit.FAHRENHEIT.format(0.0, showUnitLetter = true))
        assertEquals("20°C", TempUnit.CELSIUS.format(20.0, showUnitLetter = true))
    }

    @Test
    fun safeClampNeverThrowsOnInvertedRange() {
        assertEquals(10f, 5f.safeClamp(10f, 2f), 0.001f)
        assertEquals(15, 25.safeClamp(15, 5))
        assertEquals(0.5, 0.5.safeClamp(0.0, 1.0), 0.001)
    }

    @Test
    fun wmoCodesMapToExpectedConditions() {
        assertEquals(WeatherCondition.CLEAR_DAY, WeatherCondition.fromWmo(0, isDay = true))
        assertEquals(WeatherCondition.CLEAR_NIGHT, WeatherCondition.fromWmo(0, isDay = false))
        assertEquals(WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.fromWmo(2, isDay = true))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmo(63, isDay = true))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmo(73, isDay = true))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmo(95, isDay = false))
    }

    @Test
    fun lunarPhaseCalculatesNewQuarterAndFullAccurately() {
        // 2000-01-06 18:14 UTC — Astronomical New Moon
        val newMoon = LunarPhaseCalculator.calculate(947_182_440_000L)
        assertEquals("New Moon", newMoon.phaseName)
        assertTrue(newMoon.illuminationPercent <= 2)

        // 2026-11-23 05:38 UTC — Waxing Gibbous (~97% illuminated)
        val nov23_2026 = java.time.Instant.parse("2026-11-23T05:38:00Z").toEpochMilli()
        val waxingGibbous = LunarPhaseCalculator.calculate(nov23_2026)
        assertEquals("Waxing Gibbous", waxingGibbous.phaseName)
        assertTrue(waxingGibbous.isWaxing)
        assertTrue(waxingGibbous.illuminationPercent in 94..99)

        // 2026-12-05 12:00 UTC — Waning Crescent
        val dec05_2026 = java.time.Instant.parse("2026-12-05T12:00:00Z").toEpochMilli()
        val waningCrescent = LunarPhaseCalculator.calculate(dec05_2026)
        assertEquals("Waning Crescent", waningCrescent.phaseName)
        assertFalse(waningCrescent.isWaxing)
        assertTrue(waningCrescent.illuminationPercent in 5..40)
    }

    @Test
    fun tornadoWarningTriggersStrictlyOnlyForTornadoWarning() {
        fun snapWithAlerts(vararg events: String) = WeatherSnapshot(
            locationId = 1L,
            cacheCellKey = "39.30,-76.62",
            currentTempCelsius = 18.0,
            feelsLikeCelsius = 18.0,
            highCelsius = 22.0,
            lowCelsius = 12.0,
            precipProbability = 40,
            humidityPercent = 70,
            windMph = 12.0,
            weatherCode = 95,
            isDay = true,
            conditionLabel = "Thunderstorm",
            cloudCoverPercent = 90,
            hourly = emptyList(),
            daily = emptyList(),
            alerts = events.map {
                WeatherAlert(
                    id = it,
                    event = it,
                    severity = "Severe",
                    headline = it,
                    description = it
                )
            },
            timezoneId = "America/New_York",
            fetchedAtMillis = 1000L,
            forecastFetchedAtMillis = 1000L,
            alertsFetchedAtMillis = 1000L
        )

        assertFalse(snapWithAlerts().hasTornadoWarning)
        assertFalse(snapWithAlerts("Special Weather Statement", "Severe Thunderstorm Warning", "Tornado Watch").hasTornadoWarning)
        assertTrue(snapWithAlerts("Tornado Warning").hasTornadoWarning)
    }

    @Test
    fun cacheCellNormalizesNearbyPointsToSameGrid() {
        val c1 = normalizeCacheCell(39.2946, -76.6252)
        val c2 = normalizeCacheCell(39.2989, -76.6211)
        assertEquals(c1, c2)
    }

    @Test
    fun usStateAbbreviationsResolve() {
        assertEquals("MD", UsStateAbbreviations.abbreviate("Maryland"))
        assertEquals("CA", UsStateAbbreviations.abbreviate("California"))
        assertEquals("NY", UsStateAbbreviations.abbreviate("NY"))
    }

    @Test
    fun windAndUvTelemetryFormatsProfessionally() {
        fun sample(windMph: Double, uvIndex: Double, isDay: Boolean) = WeatherSnapshot(
            locationId = 1L,
            cacheCellKey = "39.30,-76.62",
            currentTempCelsius = 20.0,
            feelsLikeCelsius = 20.0,
            highCelsius = 24.0,
            lowCelsius = 14.0,
            precipProbability = 10,
            humidityPercent = 55,
            windMph = windMph,
            uvIndex = uvIndex,
            weatherCode = 1,
            isDay = isDay,
            conditionLabel = "Mostly sunny",
            cloudCoverPercent = 20,
            hourly = emptyList(),
            daily = emptyList(),
            alerts = emptyList(),
            timezoneId = "America/New_York",
            fetchedAtMillis = 1000L,
            forecastFetchedAtMillis = 1000L,
            alertsFetchedAtMillis = 1000L
        )

        val dayMod = sample(windMph = 11.4, uvIndex = 5.2, isDay = true)
        assertEquals("Wind 11 mph  ·  UV 5 (Moderate)", dayMod.formatWindAndUvLine(WindUnit.MPH, compact = false))
        assertEquals("Wind 11 mph · UV 5 (Mod)", dayMod.formatWindAndUvLine(WindUnit.MPH, compact = true))
        assertEquals("Wind 18 km/h · UV 5 (Mod)", dayMod.formatWindAndUvLine(WindUnit.KMH, compact = true))

        val nightKnownWind = sample(windMph = 6.8, uvIndex = -1.0, isDay = false)
        assertEquals("Wind 7 mph  ·  UV 0 (Low)", nightKnownWind.formatWindAndUvLine(WindUnit.MPH, compact = false))
        assertEquals("Wind 11 km/h  ·  UV 0 (Low)", nightKnownWind.formatWindAndUvLine(WindUnit.KMH, compact = false))

        val unknownWindKnownUv = sample(windMph = -1.0, uvIndex = 9.0, isDay = true)
        assertEquals("UV 9 (Very High)", unknownWindKnownUv.formatWindAndUvLine(WindUnit.MPH, compact = false))
        assertEquals("UV 9 (V.High)", unknownWindKnownUv.formatWindAndUvLine(WindUnit.KMH, compact = true))
    }

    @Test
    fun inputSecurityValidatorEnforcesStrictAllowlistAndSsrfProtection() {
        // Valid ZIPs and Unicode cities pass cleanly
        assertEquals("21201", InputSecurityValidator.validateLocationSearchQuery("  21201\u200B "))
        assertEquals("St. John's, NL", InputSecurityValidator.validateLocationSearchQuery("St. John's, NL"))
        assertEquals("München", InputSecurityValidator.validateLocationSearchQuery("München"))

        // Injection, HTML, path traversal, and control payloads rejected
        assertNull(InputSecurityValidator.validateLocationSearchQuery("<script>alert(1)</script>"))
        assertNull(InputSecurityValidator.validateLocationSearchQuery("21201; DROP TABLE saved_locations;--"))
        assertNull(InputSecurityValidator.validateLocationSearchQuery("../../etc/passwd"))
        assertNull(InputSecurityValidator.validateLocationSearchQuery("a"))

        // Coordinate bounds validation
        assertTrue(InputSecurityValidator.areValidCoordinates(39.2946, -76.6252))
        assertFalse(InputSecurityValidator.areValidCoordinates(95.0, -76.0))
        assertFalse(InputSecurityValidator.areValidCoordinates(Double.NaN, 0.0))

        // Gateway SSRF & HTTPS validation
        assertTrue(InputSecurityValidator.validateHttpsGatewayUrl("") is InputSecurityValidator.GatewayValidationResult.Valid)
        assertTrue(
            InputSecurityValidator.validateHttpsGatewayUrl("https://api.open-meteo.com/v1/forecast")
                is InputSecurityValidator.GatewayValidationResult.Valid
        )
        assertTrue(
            InputSecurityValidator.validateHttpsGatewayUrl("http://api.open-meteo.com/v1/forecast")
                is InputSecurityValidator.GatewayValidationResult.Invalid
        )
        assertTrue(
            InputSecurityValidator.validateHttpsGatewayUrl("https://localhost/v1/forecast")
                is InputSecurityValidator.GatewayValidationResult.Invalid
        )
        assertTrue(
            InputSecurityValidator.validateHttpsGatewayUrl("https://169.254.169.254/latest/meta-data")
                is InputSecurityValidator.GatewayValidationResult.Invalid
        )
        assertTrue(
            InputSecurityValidator.validateHttpsGatewayUrl("https://192.168.1.1/v1/forecast")
                is InputSecurityValidator.GatewayValidationResult.Invalid
        )
        assertTrue(
            InputSecurityValidator.validateHttpsGatewayUrl("https://admin:secret@api.open-meteo.com/v1/forecast")
                is InputSecurityValidator.GatewayValidationResult.Invalid
        )
    }

    @Test
    fun dailyForecastFormatsMonthDayWeekAndHighPrecipCorrectly() {
        val nov23Mon = DailyForecastItem(
            isoDate = "2026-11-23",
            dayLabel = "Mon",
            shortDayLabel = "Mon",
            highCelsius = 10.0,
            lowCelsius = 1.0,
            precipProbability = 50,
            weatherCode = 61
        )
        assertEquals("11/23 Mon", nov23Mon.monthDayWeekLabel)
        assertFalse(nov23Mon.showRainPercentBesideCloud) // strictly > 50%

        val nov26ThuRain = DailyForecastItem(
            isoDate = "2026-11-26",
            dayLabel = "Thu",
            shortDayLabel = "Thu",
            highCelsius = 13.0,
            lowCelsius = 5.0,
            precipProbability = 99,
            weatherCode = 63
        )
        assertEquals("11/26 Thu", nov26ThuRain.monthDayWeekLabel)
        assertTrue(nov26ThuRain.showRainPercentBesideCloud)

        val clearDayAnomaly = nov26ThuRain.copy(weatherCode = 0)
        assertFalse(clearDayAnomaly.showRainPercentBesideCloud)
    }

    @Test
    fun widgetMoonAndSunnyStageGeometryStaysBoundedAcrossAspectRatios() {
        for ((w, h) in listOf(406f to 321f, 374f to 250f, 390f to 215f, 380f to 168f, 180f to 180f, 80f to 80f)) {
            val moonBottom = WidgetBitmapRenderer.computeMoonBottomDp(w, h)
            assertTrue(moonBottom in 20f..(h * 0.78f))
            val (sunX, sunY, cy) = WidgetBitmapRenderer.computeSunnyCropAndSunCenter(w, h)
            assertTrue(sunX in 0.50f..0.90f)
            assertTrue(sunY in 0.15f..0.48f)
            assertTrue(cy in 0f..1f)

            // When top Time display is switched ON, Moon top rim must stay strictly below reserved clock band
            val topClockFrac = 0.16f
            val (_, moonYWithClock, moonRWithClock) = WidgetBitmapRenderer.computeMoonStageGeometry(
                w = w,
                h = h,
                topReservedFraction = topClockFrac,
                maxStageBottomFraction = 0.68f
            )
            assertTrue((moonYWithClock - moonRWithClock) >= h * topClockFrac)
            if (w >= 370f && h >= 245f) {
                // Ensure the 3D Moon remains large and prominent on 4x2 / 5x2 widgets even when Time is displayed
                assertTrue(moonRWithClock * 2f >= h * 0.44f)
            }
        }
        assertEquals(74, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_TIME_FONT_SP)
        assertEquals(74, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_FULL_SCREEN_TEMP_FONT_SP)
        assertEquals(74, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_WIDGET_TEMP_FONT_SP)
        assertEquals(10, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_FORECAST_FONT_BOOST_SP)
        assertEquals(0, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MIN_WIDGET_DATA_FONT_DELTA_SP)
        assertEquals(5, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_WIDGET_DATA_FONT_DELTA_SP)
        assertEquals(127, com.crowinnovations.skypulseweather.ui.components.HoneycombColorWheel.wheelCells.size)
        assertEquals(
            "#FFFFFF",
            com.crowinnovations.skypulseweather.ui.components.HoneycombColorWheel.formatHex(
                com.crowinnovations.skypulseweather.ui.components.HoneycombColorWheel.wheelCells
                    .first { it.q == 0 && it.r == 0 }.argb
            )
        )
        assertEquals(4, DisplayFontStyle.entries.size)
        assertEquals(6, DisplayTextColor.entries.size)
        assertEquals(
            listOf("Off", "Left of Time", "Right of Time", "Below Time"),
            com.crowinnovations.skypulseweather.domain.DateDisplayPosition.entries.map { it.label }
        )
        assertEquals(
            listOf("Darken", "Default", "Lighten"),
            com.crowinnovations.skypulseweather.domain.BackingTone.entries.map { it.label }
        )
    }

    @Test
    fun separatedFullScreenAndWidgetSettingsAndSliderBackingWorkIndependently() {
        val defaults = com.crowinnovations.skypulseweather.data.local.AppPreferences()
        val updatedWidgetOnly = defaults.copy(
            widgetTempFontSizeSp = 66,
            widgetTempFontStyle = DisplayFontStyle.SERIF,
            widgetTempTextColorArgb = DisplayTextColor.MINT.argb,
            widgetShowTimeDisplay = true,
            widgetTimeTextColorArgb = DisplayTextColor.GOLD.argb,
            widgetDataFontDeltaSp = 5,
            widgetSliderBackingTone = com.crowinnovations.skypulseweather.domain.BackingTone.DARKEN,
            widgetSliderTransparencyDeltaPct = -30
        )
        // Full-screen fields remain completely untouched when widget fields change
        assertEquals(46, updatedWidgetOnly.fullScreenTempFontSizeSp)
        assertEquals(DisplayFontStyle.DEFAULT, updatedWidgetOnly.fullScreenTempFontStyle)
        assertEquals(DisplayTextColor.WHITE.argb, updatedWidgetOnly.fullScreenTempTextColorArgb)
        assertFalse(updatedWidgetOnly.fullScreenShowTimeDisplay)
        assertEquals(0, updatedWidgetOnly.forecastFontBoostSp)

        // Widget slider backing transparency & tone checks
        val defaultSlider = com.crowinnovations.skypulseweather.widget.resolveWidgetSliderBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.DEFAULT,
            0
        )
        val darkenedOpaqueSlider = com.crowinnovations.skypulseweather.widget.resolveWidgetSliderBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.DARKEN,
            -40
        )
        val fullyTransparentSlider = com.crowinnovations.skypulseweather.widget.resolveWidgetSliderBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.DEFAULT,
            60
        )
        val lightenedOpaqueSlider = com.crowinnovations.skypulseweather.widget.resolveWidgetSliderBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.LIGHTEN,
            -40
        )
        assertTrue(darkenedOpaqueSlider.alpha > defaultSlider.alpha)
        assertEquals(0.0f, fullyTransparentSlider.alpha, 0.001f)
        assertTrue(lightenedOpaqueSlider.red > 0.85f && lightenedOpaqueSlider.alpha > 0.70f)
    }
}
