package com.crowinnovations.skypulseweather.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crowinnovations.skypulseweather.domain.BackingTone
import com.crowinnovations.skypulseweather.domain.DateDisplayPosition
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.ThemeMode
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.safeClamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "skypulse_preferences"
)

data class AppPreferences(
    val tempUnit: TempUnit = TempUnit.FAHRENHEIT,
    val windUnit: WindUnit = WindUnit.MPH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeLocationId: Long = -1L,
    val gatewayBaseUrl: String = "",
    // Full Screen Display settings
    val fullScreenShowTimeDisplay: Boolean = false,
    val fullScreenDateDisplayPosition: DateDisplayPosition = DateDisplayPosition.OFF,
    val fullScreenTimeFontSizeSp: Int = UserPreferencesRepository.DEFAULT_TIME_FONT_SP,
    val fullScreenTimeFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    val fullScreenTimeTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    val fullScreenTempFontSizeSp: Int = UserPreferencesRepository.DEFAULT_FULL_SCREEN_TEMP_FONT_SP,
    val fullScreenTempFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    val fullScreenTempTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    val forecastTransparencyDeltaPct: Int = UserPreferencesRepository.DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT,
    val forecastBackingTone: BackingTone = BackingTone.DEFAULT,
    val forecastFontBoostSp: Int = UserPreferencesRepository.DEFAULT_FORECAST_FONT_BOOST_SP,
    // Widget Display settings
    val widgetShowTimeDisplay: Boolean = false,
    val widgetDateDisplayPosition: DateDisplayPosition = DateDisplayPosition.OFF,
    val widgetTimeFontSizeSp: Int = UserPreferencesRepository.DEFAULT_TIME_FONT_SP,
    val widgetTimeFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    val widgetTimeTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    val widgetTempFontSizeSp: Int = UserPreferencesRepository.DEFAULT_WIDGET_TEMP_FONT_SP,
    val widgetTempFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    val widgetTempTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    val widgetForecastDayOffset: Int = 0,
    val widgetDataFontDeltaSp: Int = UserPreferencesRepository.DEFAULT_WIDGET_DATA_FONT_DELTA_SP,
    val widgetSliderTransparencyDeltaPct: Int = UserPreferencesRepository.DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT,
    val widgetSliderBackingTone: BackingTone = BackingTone.DEFAULT
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.weatherDataStore

    companion object {
        private val KEY_TEMP_UNIT = stringPreferencesKey("temp_unit")
        private val KEY_WIND_UNIT = stringPreferencesKey("wind_unit")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACTIVE_LOCATION_ID = longPreferencesKey("active_location_id")
        private val KEY_GATEWAY_URL = stringPreferencesKey("gateway_base_url")

        // Legacy shared keys (used only as read fallback for existing widget clock setup)
        private val KEY_LEGACY_SHOW_TIME_DISPLAY = booleanPreferencesKey("show_time_display")
        private val KEY_LEGACY_DATE_DISPLAY_POSITION = stringPreferencesKey("date_display_position")
        private val KEY_LEGACY_TIME_FONT_SIZE_SP = intPreferencesKey("time_font_size_sp")
        private val KEY_LEGACY_TIME_FONT_STYLE = stringPreferencesKey("time_font_style")
        private val KEY_LEGACY_TIME_TEXT_COLOR = stringPreferencesKey("time_text_color")
        private val KEY_LEGACY_TIME_TEXT_COLOR_ARGB = intPreferencesKey("time_text_color_argb")

        // Dedicated Full Screen Display keys
        private val KEY_FULL_SCREEN_SHOW_TIME = booleanPreferencesKey("full_screen_show_time_display")
        private val KEY_FULL_SCREEN_DATE_POSITION = stringPreferencesKey("full_screen_date_display_position")
        private val KEY_FULL_SCREEN_TIME_FONT_SP = intPreferencesKey("full_screen_time_font_size_sp")
        private val KEY_FULL_SCREEN_TIME_FONT_STYLE = stringPreferencesKey("full_screen_time_font_style")
        private val KEY_FULL_SCREEN_TIME_COLOR_ARGB = intPreferencesKey("full_screen_time_text_color_argb")
        private val KEY_FULL_SCREEN_TEMP_FONT_SP = intPreferencesKey("full_screen_temp_font_size_sp")
        private val KEY_FULL_SCREEN_TEMP_FONT_STYLE = stringPreferencesKey("full_screen_temp_font_style")
        private val KEY_FULL_SCREEN_TEMP_COLOR_ARGB = intPreferencesKey("full_screen_temp_text_color_argb")
        private val KEY_FORECAST_TRANSPARENCY_DELTA_PCT = intPreferencesKey("forecast_transparency_delta_pct")
        private val KEY_FORECAST_BACKING_TONE = stringPreferencesKey("forecast_backing_tone")
        private val KEY_FORECAST_FONT_BOOST_SP = intPreferencesKey("forecast_font_boost_sp")

        // Dedicated Widget Display keys
        private val KEY_WIDGET_SHOW_TIME = booleanPreferencesKey("widget_show_time_display")
        private val KEY_WIDGET_DATE_POSITION = stringPreferencesKey("widget_date_display_position")
        private val KEY_WIDGET_TIME_FONT_SP = intPreferencesKey("widget_time_font_size_sp")
        private val KEY_WIDGET_TIME_FONT_STYLE = stringPreferencesKey("widget_time_font_style")
        private val KEY_WIDGET_TIME_COLOR_ARGB = intPreferencesKey("widget_time_text_color_argb")
        private val KEY_WIDGET_TEMP_FONT_SIZE_SP = intPreferencesKey("widget_temp_font_size_sp")
        private val KEY_WIDGET_TEMP_FONT_STYLE = stringPreferencesKey("widget_temp_font_style")
        private val KEY_WIDGET_TEMP_TEXT_COLOR = stringPreferencesKey("widget_temp_text_color")
        private val KEY_WIDGET_TEMP_TEXT_COLOR_ARGB = intPreferencesKey("widget_temp_text_color_argb")
        private val KEY_WIDGET_DAY_OFFSET = intPreferencesKey("widget_forecast_day_offset")
        private val KEY_WIDGET_DATA_FONT_DELTA_SP = intPreferencesKey("widget_data_font_delta_sp")
        private val KEY_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = intPreferencesKey("widget_slider_transparency_delta_pct")
        private val KEY_WIDGET_SLIDER_BACKING_TONE = stringPreferencesKey("widget_slider_backing_tone")

        private val KEY_INITIAL_SEED_DONE = booleanPreferencesKey("initial_seed_done")
        private val KEY_BUDGET_EPOCH_DAY = longPreferencesKey("budget_epoch_day")
        private val KEY_BUDGET_CALLS = intPreferencesKey("budget_calls_today")

        const val FOLLOW_PRIMARY_LOCATION = -1L
        const val DAILY_PROVIDER_CALL_BUDGET = 600

        const val MIN_TIME_FONT_SP = 14
        const val MAX_TIME_FONT_SP = 74
        const val DEFAULT_TIME_FONT_SP = 22
        const val TIME_FONT_STEP_SP = 2

        const val MIN_FULL_SCREEN_TEMP_FONT_SP = 26
        const val MAX_FULL_SCREEN_TEMP_FONT_SP = 74
        const val DEFAULT_FULL_SCREEN_TEMP_FONT_SP = 46
        const val FULL_SCREEN_TEMP_FONT_STEP_SP = 4

        const val MIN_WIDGET_TEMP_FONT_SP = 26
        const val MAX_WIDGET_TEMP_FONT_SP = 74
        const val DEFAULT_WIDGET_TEMP_FONT_SP = 46
        const val WIDGET_TEMP_FONT_STEP_SP = 4

        const val MIN_FORECAST_TRANSPARENCY_DELTA_PCT = -60
        const val MAX_FORECAST_TRANSPARENCY_DELTA_PCT = 60
        const val DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT = 0
        const val FORECAST_TRANSPARENCY_STEP_PCT = 10

        const val MIN_FORECAST_FONT_BOOST_SP = 0
        const val MAX_FORECAST_FONT_BOOST_SP = 10
        const val DEFAULT_FORECAST_FONT_BOOST_SP = 0
        const val FORECAST_FONT_BOOST_STEP_SP = 1

        const val MIN_WIDGET_DATA_FONT_DELTA_SP = 0
        const val MAX_WIDGET_DATA_FONT_DELTA_SP = 5
        const val DEFAULT_WIDGET_DATA_FONT_DELTA_SP = 0
        const val WIDGET_DATA_FONT_DELTA_STEP_SP = 1

        const val MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = -60
        const val MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = 60
        const val DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = 0
        const val WIDGET_SLIDER_TRANSPARENCY_STEP_PCT = 10

        private fun widgetBindingKey(appWidgetId: Int) =
            longPreferencesKey("widget_loc_$appWidgetId")
    }

    val preferencesFlow: Flow<AppPreferences> = dataStore.data.map { prefs ->
        val unit = runCatching {
            TempUnit.valueOf(prefs[KEY_TEMP_UNIT] ?: TempUnit.FAHRENHEIT.name)
        }.getOrDefault(TempUnit.FAHRENHEIT)

        val wind = runCatching {
            WindUnit.valueOf(prefs[KEY_WIND_UNIT] ?: WindUnit.MPH.name)
        }.getOrDefault(WindUnit.MPH)

        val theme = runCatching {
            ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val rawGateway = prefs[KEY_GATEWAY_URL] ?: ""
        val safeGateway = when (val v = InputSecurityValidator.validateHttpsGatewayUrl(rawGateway)) {
            is InputSecurityValidator.GatewayValidationResult.Valid -> v.normalizedUrl
            is InputSecurityValidator.GatewayValidationResult.Invalid -> ""
        }

        // Full Screen Time & Date
        val fsShowTime = prefs[KEY_FULL_SCREEN_SHOW_TIME] ?: false
        val fsDatePosition = runCatching {
            DateDisplayPosition.valueOf(prefs[KEY_FULL_SCREEN_DATE_POSITION] ?: DateDisplayPosition.OFF.name)
        }.getOrDefault(DateDisplayPosition.OFF)
        val fsTimeFontSp = (prefs[KEY_FULL_SCREEN_TIME_FONT_SP] ?: DEFAULT_TIME_FONT_SP)
            .safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
        val fsTimeStyle = runCatching {
            DisplayFontStyle.valueOf(prefs[KEY_FULL_SCREEN_TIME_FONT_STYLE] ?: DisplayFontStyle.DEFAULT.name)
        }.getOrDefault(DisplayFontStyle.DEFAULT)
        val fsTimeColorArgb = (prefs[KEY_FULL_SCREEN_TIME_COLOR_ARGB] ?: DisplayTextColor.WHITE.argb) or 0xFF000000.toInt()

        // Full Screen Temperature
        val fsTempSp = (prefs[KEY_FULL_SCREEN_TEMP_FONT_SP] ?: DEFAULT_FULL_SCREEN_TEMP_FONT_SP)
            .safeClamp(MIN_FULL_SCREEN_TEMP_FONT_SP, MAX_FULL_SCREEN_TEMP_FONT_SP)
        val fsTempStyle = runCatching {
            DisplayFontStyle.valueOf(prefs[KEY_FULL_SCREEN_TEMP_FONT_STYLE] ?: DisplayFontStyle.DEFAULT.name)
        }.getOrDefault(DisplayFontStyle.DEFAULT)
        val fsTempColorArgb = (prefs[KEY_FULL_SCREEN_TEMP_COLOR_ARGB] ?: DisplayTextColor.WHITE.argb) or 0xFF000000.toInt()

        // Full Screen Forecast Cards
        val safeTransparencyDeltaPct = (prefs[KEY_FORECAST_TRANSPARENCY_DELTA_PCT] ?: DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT)
            .safeClamp(MIN_FORECAST_TRANSPARENCY_DELTA_PCT, MAX_FORECAST_TRANSPARENCY_DELTA_PCT)
        val fsBackingTone = runCatching {
            BackingTone.valueOf(prefs[KEY_FORECAST_BACKING_TONE] ?: BackingTone.DEFAULT.name)
        }.getOrDefault(BackingTone.DEFAULT)
        val safeForecastFontBoostSp = (prefs[KEY_FORECAST_FONT_BOOST_SP] ?: DEFAULT_FORECAST_FONT_BOOST_SP)
            .safeClamp(MIN_FORECAST_FONT_BOOST_SP, MAX_FORECAST_FONT_BOOST_SP)

        // Widget Time & Date (with seamless fallback to legacy shared time keys)
        val legacyShowTime = prefs[KEY_LEGACY_SHOW_TIME_DISPLAY] ?: false
        val wShowTime = prefs[KEY_WIDGET_SHOW_TIME] ?: legacyShowTime
        val wDatePosition = runCatching {
            DateDisplayPosition.valueOf(
                prefs[KEY_WIDGET_DATE_POSITION]
                    ?: prefs[KEY_LEGACY_DATE_DISPLAY_POSITION]
                    ?: DateDisplayPosition.OFF.name
            )
        }.getOrDefault(DateDisplayPosition.OFF)
        val wTimeFontSp = (
            prefs[KEY_WIDGET_TIME_FONT_SP]
                ?: prefs[KEY_LEGACY_TIME_FONT_SIZE_SP]
                ?: DEFAULT_TIME_FONT_SP
            ).safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
        val wTimeStyle = runCatching {
            DisplayFontStyle.valueOf(
                prefs[KEY_WIDGET_TIME_FONT_STYLE]
                    ?: prefs[KEY_LEGACY_TIME_FONT_STYLE]
                    ?: DisplayFontStyle.DEFAULT.name
            )
        }.getOrDefault(DisplayFontStyle.DEFAULT)
        val legacyTimeColor = runCatching {
            DisplayTextColor.valueOf(prefs[KEY_LEGACY_TIME_TEXT_COLOR] ?: DisplayTextColor.WHITE.name)
        }.getOrDefault(DisplayTextColor.WHITE)
        val wTimeColorArgb = (
            prefs[KEY_WIDGET_TIME_COLOR_ARGB]
                ?: prefs[KEY_LEGACY_TIME_TEXT_COLOR_ARGB]
                ?: legacyTimeColor.argb
            ) or 0xFF000000.toInt()

        // Widget Temperature
        val safeWidgetTempSp = (prefs[KEY_WIDGET_TEMP_FONT_SIZE_SP] ?: DEFAULT_WIDGET_TEMP_FONT_SP)
            .safeClamp(MIN_WIDGET_TEMP_FONT_SP, MAX_WIDGET_TEMP_FONT_SP)
        val widgetTempStyle = runCatching {
            DisplayFontStyle.valueOf(prefs[KEY_WIDGET_TEMP_FONT_STYLE] ?: DisplayFontStyle.DEFAULT.name)
        }.getOrDefault(DisplayFontStyle.DEFAULT)
        val legacyWidgetTempColor = runCatching {
            DisplayTextColor.valueOf(prefs[KEY_WIDGET_TEMP_TEXT_COLOR] ?: DisplayTextColor.WHITE.name)
        }.getOrDefault(DisplayTextColor.WHITE)
        val widgetTempColorArgb = (prefs[KEY_WIDGET_TEMP_TEXT_COLOR_ARGB] ?: legacyWidgetTempColor.argb) or 0xFF000000.toInt()

        // Widget 10-Day Slider & Data
        val safeDayOffset = (prefs[KEY_WIDGET_DAY_OFFSET] ?: 0).safeClamp(0, 5)
        val safeWidgetDataFontDeltaSp = (prefs[KEY_WIDGET_DATA_FONT_DELTA_SP] ?: DEFAULT_WIDGET_DATA_FONT_DELTA_SP)
            .safeClamp(MIN_WIDGET_DATA_FONT_DELTA_SP, MAX_WIDGET_DATA_FONT_DELTA_SP)
        val safeWidgetSliderTransparency = (
            prefs[KEY_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT]
                ?: DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT
            ).safeClamp(MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT)
        val widgetSliderTone = runCatching {
            BackingTone.valueOf(prefs[KEY_WIDGET_SLIDER_BACKING_TONE] ?: BackingTone.DEFAULT.name)
        }.getOrDefault(BackingTone.DEFAULT)

        AppPreferences(
            tempUnit = unit,
            windUnit = wind,
            themeMode = theme,
            activeLocationId = prefs[KEY_ACTIVE_LOCATION_ID] ?: -1L,
            gatewayBaseUrl = safeGateway,
            fullScreenShowTimeDisplay = fsShowTime,
            fullScreenDateDisplayPosition = fsDatePosition,
            fullScreenTimeFontSizeSp = fsTimeFontSp,
            fullScreenTimeFontStyle = fsTimeStyle,
            fullScreenTimeTextColorArgb = fsTimeColorArgb,
            fullScreenTempFontSizeSp = fsTempSp,
            fullScreenTempFontStyle = fsTempStyle,
            fullScreenTempTextColorArgb = fsTempColorArgb,
            forecastTransparencyDeltaPct = safeTransparencyDeltaPct,
            forecastBackingTone = fsBackingTone,
            forecastFontBoostSp = safeForecastFontBoostSp,
            widgetShowTimeDisplay = wShowTime,
            widgetDateDisplayPosition = wDatePosition,
            widgetTimeFontSizeSp = wTimeFontSp,
            widgetTimeFontStyle = wTimeStyle,
            widgetTimeTextColorArgb = wTimeColorArgb,
            widgetTempFontSizeSp = safeWidgetTempSp,
            widgetTempFontStyle = widgetTempStyle,
            widgetTempTextColorArgb = widgetTempColorArgb,
            widgetForecastDayOffset = safeDayOffset,
            widgetDataFontDeltaSp = safeWidgetDataFontDeltaSp,
            widgetSliderTransparencyDeltaPct = safeWidgetSliderTransparency,
            widgetSliderBackingTone = widgetSliderTone
        )
    }

    suspend fun getPreferencesOnce(): AppPreferences = preferencesFlow.first()

    suspend fun setTempUnit(unit: TempUnit) {
        dataStore.edit { it[KEY_TEMP_UNIT] = unit.name }
    }

    suspend fun setWindUnit(unit: WindUnit) {
        dataStore.edit { it[KEY_WIND_UNIT] = unit.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    // Full Screen Time & Date setters
    suspend fun setFullScreenShowTimeDisplay(enabled: Boolean) {
        dataStore.edit { it[KEY_FULL_SCREEN_SHOW_TIME] = enabled }
    }

    suspend fun setFullScreenDateDisplayPosition(position: DateDisplayPosition) {
        dataStore.edit { prefs ->
            prefs[KEY_FULL_SCREEN_DATE_POSITION] = position.name
            if (position != DateDisplayPosition.OFF) {
                prefs[KEY_FULL_SCREEN_SHOW_TIME] = true
            }
        }
    }

    suspend fun adjustFullScreenTimeFontSize(deltaSp: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_FULL_SCREEN_TIME_FONT_SP] ?: DEFAULT_TIME_FONT_SP)
                .safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
            prefs[KEY_FULL_SCREEN_TIME_FONT_SP] = (current + deltaSp)
                .safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
        }
    }

    suspend fun setFullScreenTimeFontStyle(style: DisplayFontStyle) {
        dataStore.edit { it[KEY_FULL_SCREEN_TIME_FONT_STYLE] = style.name }
    }

    suspend fun setFullScreenTimeTextColorArgb(argb: Int) {
        dataStore.edit { it[KEY_FULL_SCREEN_TIME_COLOR_ARGB] = argb or 0xFF000000.toInt() }
    }

    // Full Screen Temperature setters
    suspend fun adjustFullScreenTempFontSize(deltaSp: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_FULL_SCREEN_TEMP_FONT_SP] ?: DEFAULT_FULL_SCREEN_TEMP_FONT_SP)
                .safeClamp(MIN_FULL_SCREEN_TEMP_FONT_SP, MAX_FULL_SCREEN_TEMP_FONT_SP)
            prefs[KEY_FULL_SCREEN_TEMP_FONT_SP] = (current + deltaSp)
                .safeClamp(MIN_FULL_SCREEN_TEMP_FONT_SP, MAX_FULL_SCREEN_TEMP_FONT_SP)
        }
    }

    suspend fun setFullScreenTempFontStyle(style: DisplayFontStyle) {
        dataStore.edit { it[KEY_FULL_SCREEN_TEMP_FONT_STYLE] = style.name }
    }

    suspend fun setFullScreenTempTextColorArgb(argb: Int) {
        dataStore.edit { it[KEY_FULL_SCREEN_TEMP_COLOR_ARGB] = argb or 0xFF000000.toInt() }
    }

    // Full Screen Forecast setters
    suspend fun adjustForecastTransparencyDelta(deltaPct: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_FORECAST_TRANSPARENCY_DELTA_PCT] ?: DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT)
                .safeClamp(MIN_FORECAST_TRANSPARENCY_DELTA_PCT, MAX_FORECAST_TRANSPARENCY_DELTA_PCT)
            prefs[KEY_FORECAST_TRANSPARENCY_DELTA_PCT] = (current + deltaPct)
                .safeClamp(MIN_FORECAST_TRANSPARENCY_DELTA_PCT, MAX_FORECAST_TRANSPARENCY_DELTA_PCT)
        }
    }

    suspend fun setForecastBackingTone(tone: BackingTone) {
        dataStore.edit { it[KEY_FORECAST_BACKING_TONE] = tone.name }
    }

    suspend fun adjustForecastFontBoost(deltaSp: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_FORECAST_FONT_BOOST_SP] ?: DEFAULT_FORECAST_FONT_BOOST_SP)
                .safeClamp(MIN_FORECAST_FONT_BOOST_SP, MAX_FORECAST_FONT_BOOST_SP)
            prefs[KEY_FORECAST_FONT_BOOST_SP] = (current + deltaSp)
                .safeClamp(MIN_FORECAST_FONT_BOOST_SP, MAX_FORECAST_FONT_BOOST_SP)
        }
    }

    // Widget Time & Date setters
    suspend fun setWidgetShowTimeDisplay(enabled: Boolean) {
        dataStore.edit { it[KEY_WIDGET_SHOW_TIME] = enabled }
    }

    suspend fun setWidgetDateDisplayPosition(position: DateDisplayPosition) {
        dataStore.edit { prefs ->
            prefs[KEY_WIDGET_DATE_POSITION] = position.name
            if (position != DateDisplayPosition.OFF) {
                prefs[KEY_WIDGET_SHOW_TIME] = true
            }
        }
    }

    suspend fun adjustWidgetTimeFontSize(deltaSp: Int) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_WIDGET_TIME_FONT_SP]
                    ?: prefs[KEY_LEGACY_TIME_FONT_SIZE_SP]
                    ?: DEFAULT_TIME_FONT_SP
                ).safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
            prefs[KEY_WIDGET_TIME_FONT_SP] = (current + deltaSp)
                .safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
        }
    }

    suspend fun setWidgetTimeFontStyle(style: DisplayFontStyle) {
        dataStore.edit { it[KEY_WIDGET_TIME_FONT_STYLE] = style.name }
    }

    suspend fun setWidgetTimeTextColorArgb(argb: Int) {
        dataStore.edit { it[KEY_WIDGET_TIME_COLOR_ARGB] = argb or 0xFF000000.toInt() }
    }

    // Widget Temperature setters
    suspend fun adjustWidgetTempFontSize(deltaSp: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_WIDGET_TEMP_FONT_SIZE_SP] ?: DEFAULT_WIDGET_TEMP_FONT_SP)
                .safeClamp(MIN_WIDGET_TEMP_FONT_SP, MAX_WIDGET_TEMP_FONT_SP)
            prefs[KEY_WIDGET_TEMP_FONT_SIZE_SP] = (current + deltaSp)
                .safeClamp(MIN_WIDGET_TEMP_FONT_SP, MAX_WIDGET_TEMP_FONT_SP)
        }
    }

    suspend fun setWidgetTempFontStyle(style: DisplayFontStyle) {
        dataStore.edit { it[KEY_WIDGET_TEMP_FONT_STYLE] = style.name }
    }

    suspend fun setWidgetTempTextColorArgb(argb: Int) {
        dataStore.edit { it[KEY_WIDGET_TEMP_TEXT_COLOR_ARGB] = argb or 0xFF000000.toInt() }
    }

    // Widget 10-Day Slider & Data setters
    suspend fun setWidgetForecastDayOffset(offset: Int) {
        val clamped = offset.safeClamp(0, 5)
        dataStore.edit { it[KEY_WIDGET_DAY_OFFSET] = clamped }
    }

    suspend fun adjustWidgetDataFontDelta(deltaSp: Int) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_WIDGET_DATA_FONT_DELTA_SP] ?: DEFAULT_WIDGET_DATA_FONT_DELTA_SP)
                .safeClamp(MIN_WIDGET_DATA_FONT_DELTA_SP, MAX_WIDGET_DATA_FONT_DELTA_SP)
            prefs[KEY_WIDGET_DATA_FONT_DELTA_SP] = (current + deltaSp)
                .safeClamp(MIN_WIDGET_DATA_FONT_DELTA_SP, MAX_WIDGET_DATA_FONT_DELTA_SP)
        }
    }

    suspend fun adjustWidgetSliderTransparencyDelta(deltaPct: Int) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT]
                    ?: DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT
                ).safeClamp(MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT)
            prefs[KEY_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT] = (current + deltaPct)
                .safeClamp(MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT)
        }
    }

    suspend fun setWidgetSliderBackingTone(tone: BackingTone) {
        dataStore.edit { it[KEY_WIDGET_SLIDER_BACKING_TONE] = tone.name }
    }

    suspend fun setActiveLocationId(locationId: Long) {
        dataStore.edit { it[KEY_ACTIVE_LOCATION_ID] = locationId }
    }

    suspend fun setGatewayBaseUrl(url: String) {
        val safeUrl = when (val res = InputSecurityValidator.validateHttpsGatewayUrl(url)) {
            is InputSecurityValidator.GatewayValidationResult.Valid -> res.normalizedUrl
            is InputSecurityValidator.GatewayValidationResult.Invalid -> ""
        }
        dataStore.edit { it[KEY_GATEWAY_URL] = safeUrl }
    }

    suspend fun setWidgetLocation(appWidgetId: Int, locationId: Long) {
        dataStore.edit { it[widgetBindingKey(appWidgetId)] = locationId }
    }

    suspend fun getWidgetLocation(appWidgetId: Int): Long {
        val prefs = dataStore.data.first()
        return prefs[widgetBindingKey(appWidgetId)] ?: FOLLOW_PRIMARY_LOCATION
    }

    suspend fun clearWidgetLocation(appWidgetId: Int) {
        dataStore.edit { it.remove(widgetBindingKey(appWidgetId)) }
    }

    suspend fun isInitialSeedDone(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[KEY_INITIAL_SEED_DONE] ?: false
    }

    suspend fun markInitialSeedDone() {
        dataStore.edit { it[KEY_INITIAL_SEED_DONE] = true }
    }

    suspend fun tryConsumeApiBudget(): Boolean {
        val today = LocalDate.now().toEpochDay()
        var allowed = true
        dataStore.edit { prefs ->
            val recordedDay = prefs[KEY_BUDGET_EPOCH_DAY] ?: -1L
            val count = if (recordedDay == today) (prefs[KEY_BUDGET_CALLS] ?: 0) else 0
            if (count >= DAILY_PROVIDER_CALL_BUDGET) {
                allowed = false
            } else {
                prefs[KEY_BUDGET_EPOCH_DAY] = today
                prefs[KEY_BUDGET_CALLS] = count + 1
                allowed = true
            }
        }
        return allowed
    }
}
