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
        assertEquals("20001", InputSecurityValidator.validateLocationSearchQuery("  20001\u200B "))
        assertEquals("St. John's, NL", InputSecurityValidator.validateLocationSearchQuery("St. John's, NL"))
        assertEquals("München", InputSecurityValidator.validateLocationSearchQuery("München"))

        // Injection, HTML, path traversal, and control payloads rejected
        assertNull(InputSecurityValidator.validateLocationSearchQuery("<script>alert(1)</script>"))
        assertNull(InputSecurityValidator.validateLocationSearchQuery("20001; DROP TABLE saved_locations;--"))
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
        assertEquals(0.5f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.FONT_SIZE_STEP_SP, 0.001f)
        assertEquals(74f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_TIME_FONT_SP, 0.001f)
        assertEquals(74f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_FULL_SCREEN_TEMP_FONT_SP, 0.001f)
        assertEquals(74f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_WIDGET_TEMP_FONT_SP, 0.001f)
        assertEquals(7f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MIN_FORECAST_FONT_SP, 0.001f)
        assertEquals(11f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_FORECAST_FONT_SP, 0.001f)
        assertEquals(22f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_FORECAST_FONT_SP, 0.001f)
        assertEquals(7f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MIN_WIDGET_DETAILS_FONT_SP, 0.001f)
        assertEquals(10f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_WIDGET_DETAILS_FONT_SP, 0.001f)
        assertEquals(18f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_WIDGET_DETAILS_FONT_SP, 0.001f)
        assertEquals(7f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MIN_WIDGET_DATA_FONT_SP, 0.001f)
        assertEquals(9f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_WIDGET_DATA_FONT_SP, 0.001f)
        assertEquals(17f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.MAX_WIDGET_DATA_FONT_SP, 0.001f)
        assertEquals(60f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_WIDGET_TEMP_FONT_SP, 0.001f)
        assertEquals(16f, com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_WIDGET_TIME_FONT_SP, 0.001f)
        assertEquals(0xFF6AFF25.toInt(), com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_WIDGET_TEMP_COLOR_ARGB)
        assertEquals(0xFF18FF18.toInt(), com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository.DEFAULT_WIDGET_TIME_COLOR_ARGB)
        assertEquals("12sp", com.crowinnovations.skypulseweather.ui.components.formatFontSpLabel(12.0f))
        assertEquals("12.5sp", com.crowinnovations.skypulseweather.ui.components.formatFontSpLabel(12.5f))
        assertEquals("11.5sp", com.crowinnovations.skypulseweather.ui.components.formatFontSpLabel(11.5f))
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
        // Out-of-the-box widget defaults match S25 widget preset while Full Screen time stays off
        assertTrue(defaults.widgetShowTimeDisplay)
        assertEquals(com.crowinnovations.skypulseweather.domain.DateDisplayPosition.LEFT_OF_TIME, defaults.widgetDateDisplayPosition)
        assertEquals(16f, defaults.widgetTimeFontSizeSp, 0.001f)
        assertEquals(DisplayFontStyle.SERIF, defaults.widgetTimeFontStyle)
        assertEquals(0xFF18FF18.toInt(), defaults.widgetTimeTextColorArgb)
        assertEquals(60f, defaults.widgetTempFontSizeSp, 0.001f)
        assertEquals(DisplayFontStyle.DEFAULT, defaults.widgetTempFontStyle)
        assertEquals(0xFF6AFF25.toInt(), defaults.widgetTempTextColorArgb)
        assertEquals(10f, defaults.widgetDetailsFontSizeSp, 0.001f)
        assertEquals(9f, defaults.widgetDataFontSizeSp, 0.001f)
        assertEquals(com.crowinnovations.skypulseweather.domain.BackingTone.DARKEN, defaults.widgetSliderBackingTone)
        assertEquals(10, defaults.widgetSliderTransparencyDeltaPct)

        val updatedWidgetOnly = defaults.copy(
            widgetTempFontSizeSp = 66.5f,
            widgetTempFontStyle = DisplayFontStyle.SERIF,
            widgetTempTextColorArgb = DisplayTextColor.MINT.argb,
            widgetShowTimeDisplay = false,
            widgetTimeTextColorArgb = DisplayTextColor.GOLD.argb,
            widgetDataFontSizeSp = 11.5f,
            widgetSliderBackingTone = com.crowinnovations.skypulseweather.domain.BackingTone.LIGHTEN,
            widgetSliderTransparencyDeltaPct = -30
        )
        // Full-screen fields remain completely untouched when widget fields change
        assertEquals(46f, updatedWidgetOnly.fullScreenTempFontSizeSp, 0.001f)
        assertEquals(DisplayFontStyle.SERIF, updatedWidgetOnly.fullScreenTempFontStyle)
        assertEquals(DisplayTextColor.WHITE.argb, updatedWidgetOnly.fullScreenTempTextColorArgb)
        assertFalse(updatedWidgetOnly.fullScreenShowTimeDisplay)
        assertEquals(com.crowinnovations.skypulseweather.domain.DateDisplayPosition.OFF, updatedWidgetOnly.fullScreenDateDisplayPosition)
        assertEquals(11f, updatedWidgetOnly.forecastFontSizeSp, 0.001f)

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

        // Widget details-below-temp backing checks (clear by default, opaque when darkened or transparency decreased)
        val clearDetailsDefault = com.crowinnovations.skypulseweather.widget.resolveWidgetDetailsBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.DEFAULT,
            0
        )
        val smokedDetails = com.crowinnovations.skypulseweather.widget.resolveWidgetDetailsBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.DEFAULT,
            -30
        )
        val darkenedDetails = com.crowinnovations.skypulseweather.widget.resolveWidgetDetailsBackingColor(
            com.crowinnovations.skypulseweather.domain.BackingTone.DARKEN,
            -20
        )
        assertEquals(0.0f, clearDetailsDefault.alpha, 0.001f)
        assertTrue(smokedDetails.alpha > 0.40f)
        assertTrue(darkenedDetails.alpha > 0.65f)
    }

    @Test
    fun radarProjectionPinchZoomSquareMilesAndMultiStateLookupWork() {
        val centerLat = 38.6270
        val centerLon = -90.1994
        val screenPos = com.crowinnovations.skypulseweather.domain.RadarProjection.latLonToScreenOffset(
            lat = 39.0997,
            lon = -94.5786,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 7.0f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2200f
        )
        val roundTrip = com.crowinnovations.skypulseweather.domain.RadarProjection.screenOffsetToLatLon(
            screenX = screenPos.x,
            screenY = screenPos.y,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 7.0f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 2200f
        )
        assertEquals(39.0997, roundTrip.latitude, 0.02)
        assertEquals(-94.5786, roundTrip.longitude, 0.02)

        // Local square-mile focus when zoomed in
        val localBounds = com.crowinnovations.skypulseweather.domain.RadarProjection.computeViewportBounds(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 12.5f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1800f
        )
        assertTrue(localBounds.areaSquareMiles < 120.0)
        assertTrue(localBounds.formattedCoverageBadge().contains("sq mi"))

        // Multi-state coverage when zoomed out
        val multiStateBounds = com.crowinnovations.skypulseweather.domain.RadarProjection.computeViewportBounds(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 6.0f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1800f
        )
        assertTrue(multiStateBounds.areaSquareMiles > 80_000.0)
        assertTrue(multiStateBounds.formattedCoverageBadge().contains("Multi-State"))

        val continentalBounds = com.crowinnovations.skypulseweather.domain.RadarProjection.computeViewportBounds(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = 4.2f,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1800f
        )
        assertTrue(continentalBounds.formattedCoverageBadge().contains("Continental"))

        val visibleStates = com.crowinnovations.skypulseweather.domain.RadarProjection.intersectingUsStates(
            bounds = multiStateBounds,
            centerLat = centerLat,
            centerLon = centerLon
        )
        assertTrue(visibleStates.contains("MO"))
        assertTrue(visibleStates.contains("IL"))
        assertTrue(visibleStates.size >= 4)

        val timeline = com.crowinnovations.skypulseweather.domain.RadarProjection.buildTimelineFrames(1_795_838_000_000L)
        assertTrue(timeline.any { !it.isForecast && it.offsetMinutes < 0 })
        assertTrue(timeline.any { !it.isForecast && it.offsetMinutes == 0 && it.relativeLabel == "LIVE NOW" })
        assertTrue(timeline.any { it.isForecast && it.forecastHourStep == 12 })
    }

    @Test
    fun nwsTornadoWarningWatchNhcCycloneAndInchesParsingWork() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val sampleNwsGeoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "id": "urn:oid:2.49.0.1.840.0.tor1",
                  "type": "Feature",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[-94.6, 36.9], [-94.1, 36.9], [-94.1, 37.3], [-94.6, 37.3], [-94.6, 36.9]]]
                  },
                  "properties": {
                    "id": "tor-warn-1",
                    "event": "Tornado Warning",
                    "severity": "Extreme",
                    "headline": "Tornado Warning issued for Jasper County",
                    "areaDesc": "Jasper, MO",
                    "description": "At 515 PM CDT, a confirmed large and extremely dangerous tornado was located near Joplin.",
                    "parameters": {
                      "tornadoDetection": ["OBSERVED"],
                      "tornadoDamageThreat": ["CATASTROPHIC"],
                      "maxWindGust": ["95 mph"],
                      "maxHailSize": ["2.00"]
                    }
                  }
                },
                {
                  "id": "urn:oid:2.49.0.1.840.0.torwatch1",
                  "type": "Feature",
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[-96.0, 35.5], [-93.5, 35.5], [-93.5, 38.0], [-96.0, 38.0], [-96.0, 35.5]]]
                  },
                  "properties": {
                    "id": "tor-watch-1",
                    "event": "Tornado Watch",
                    "severity": "Severe",
                    "headline": "Tornado Watch 214 remains in effect",
                    "areaDesc": "Eastern OK; Western AR; Southwest MO",
                    "description": "Tornadoes and scattered gusts up to 75 mph expected.",
                    "parameters": {}
                  }
                }
              ]
            }
        """.trimIndent()

        val hazards = com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.parseNwsHazardGeoJson(
            json = json,
            rawGeoJson = sampleNwsGeoJson
        )
        assertEquals(2, hazards.size)
        val torWarn = hazards[0]
        assertEquals(com.crowinnovations.skypulseweather.domain.HazardPolygonKind.TORNADO_WARNING, torWarn.kind)
        assertTrue(torWarn.mapCalloutSummary().contains("CATASTROPHIC"))
        assertTrue(torWarn.mapCalloutSummary().contains("OBSERVED"))
        assertTrue(torWarn.mapCalloutSummary().contains("95 mph"))

        val torWatch = hazards[1]
        assertEquals(com.crowinnovations.skypulseweather.domain.HazardPolygonKind.TORNADO_WATCH, torWatch.kind)
        assertTrue(torWatch.mapCalloutSummary().contains("75 mph"))

        val sampleNhc = """
            {
              "activeStorms": [
                {
                  "id": "al092026",
                  "name": "Helene",
                  "classification": "HU",
                  "intensity": 115,
                  "lat": 27.4,
                  "lon": -84.2,
                  "movementDir": "NNE",
                  "movementSpeed": "16"
                }
              ]
            }
        """.trimIndent()
        val cyclones = com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.parseNhcCyclonesJson(
            json = json,
            currentStormsBody = sampleNhc
        )
        assertEquals(1, cyclones.size)
        assertEquals("CAT 4", cyclones[0].categoryBadge)
        assertTrue(cyclones[0].maxSustainedWindMph >= 130)
        assertEquals("10–20\"", cyclones[0].expectedRainfallInches)

        val snowMarker = com.crowinnovations.skypulseweather.domain.RegionalPrecipMarker(
            id = "m1",
            label = "NW Sector",
            point = com.crowinnovations.skypulseweather.domain.LatLonPoint(41.0, -90.0),
            isPrimaryLocation = false,
            expectedRainInches = 0.0,
            expectedSnowInches = 4.6,
            weatherCode = 75
        )
        assertEquals("\u2744\uFE0F Snow 4.6\"", snowMarker.formattedBadgeText())

        val rainMarker = com.crowinnovations.skypulseweather.domain.RegionalPrecipMarker(
            id = "m2",
            label = "SE Sector",
            point = com.crowinnovations.skypulseweather.domain.LatLonPoint(35.0, -89.0),
            isPrimaryLocation = false,
            expectedRainInches = 1.85,
            expectedSnowInches = 0.0,
            weatherCode = 65
        )
        assertEquals("\uD83C\uDF27\uFE0F Rain 1.9\"", rainMarker.formattedBadgeText())
    }

    @Test
    fun widgetDetailsAndSliderFontSpMatchSelectedSettingsWithoutDefaultDeltaInflation() {
        // Verify 9.0sp selected in Settings maps directly to 9.0sp (primary) and 8.8sp (secondary), NEVER 13.4sp
        val (primary9, secondary9, moon9) = com.crowinnovations.skypulseweather.widget.resolveWidgetDetailsPhysicalSp(
            selectedDetailsSp = 9.0f,
            isTall = true
        )
        assertEquals(9.0f, primary9, 0.01f)
        assertEquals(8.8f, secondary9, 0.01f)
        assertEquals(8.8f, moon9, 0.01f)

        // Verify minimum 7.0sp and maximum 18.0sp scale 1:1 without floor inflation or cap clipping
        val (primary7, secondary7, _) = com.crowinnovations.skypulseweather.widget.resolveWidgetDetailsPhysicalSp(
            selectedDetailsSp = 7.0f,
            isTall = true
        )
        assertEquals(7.0f, primary7, 0.01f)
        assertEquals(6.8f, secondary7, 0.01f)

        val (primary18, secondary18, _) = com.crowinnovations.skypulseweather.widget.resolveWidgetDetailsPhysicalSp(
            selectedDetailsSp = 18.0f,
            isTall = true
        )
        assertEquals(18.0f, primary18, 0.01f)
        assertEquals(17.8f, secondary18, 0.01f)

        // Verify virtual sp converter NEVER inflates 7.0sp or 9.0sp above desiredPhysicalSp / hostResizeRatio
        val (target7, floor7) = com.crowinnovations.skypulseweather.widget.computeTargetAndFloorVirtualSp(
            desiredPhysicalSp = 7.0f,
            minReadablePhysicalSp = 6.0f,
            hostResizeRatio = 1.0f
        )
        assertEquals(7.0f, target7, 0.01f)
        assertTrue(floor7 <= target7)

        // Verify 10-day slider 9.5sp maps directly to 9.5sp Hi/Lo and 9.0sp Date, NEVER 12.0sp / 11.4sp
        val (date95, hiLo95, rain95) = com.crowinnovations.skypulseweather.widget.resolveWidgetSliderPhysicalSp(
            selectedSliderSp = 9.5f,
            isTall = true
        )
        assertEquals(9.5f, hiLo95, 0.01f)
        assertEquals(9.0f, date95, 0.01f)
        assertEquals(9.1f, rain95, 0.01f)
    }

    @Test
    fun clearAirRadarClutterFilterStripsSlateNoiseAndPreservesTruePrecipitation() {
        // Sub-12 dBZ slate-gray / pale-lavender clear-air clutter from N0Q/HRRR is stripped
        assertTrue(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(87, 109, 164))
        assertTrue(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(116, 131, 172))
        assertTrue(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(136, 146, 178))

        // True rain/storm greens, yellows, oranges, reds, and hail magentas are preserved
        assertFalse(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(0, 220, 40))
        assertFalse(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(245, 225, 10))
        assertFalse(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(220, 30, 25))
        assertFalse(com.crowinnovations.skypulseweather.data.remote.RadarMapRepository.isSubThresholdClearAirRadarNoise(215, 40, 210))
    }
}
