package com.crowinnovations.skypulseweather.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
import kotlin.math.roundToInt

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "skypulse_preferences"
)

fun Float.snapHalfSp(): Float = (this * 2f).roundToInt() / 2f

data class AppPreferences(
    val tempUnit: TempUnit = TempUnit.FAHRENHEIT,
    val windUnit: WindUnit = WindUnit.MPH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeLocationId: Long = -1L,
    val gatewayBaseUrl: String = "",
    // Full Screen Display settings
    val fullScreenShowTimeDisplay: Boolean = UserPreferencesRepository.DEFAULT_FULL_SCREEN_SHOW_TIME,
    val fullScreenDateDisplayPosition: DateDisplayPosition = UserPreferencesRepository.DEFAULT_FULL_SCREEN_DATE_POSITION,
    val fullScreenTimeFontSizeSp: Float = UserPreferencesRepository.DEFAULT_TIME_FONT_SP,
    val fullScreenTimeFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    val fullScreenTimeTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    val fullScreenTempFontSizeSp: Float = UserPreferencesRepository.DEFAULT_FULL_SCREEN_TEMP_FONT_SP,
    val fullScreenTempFontStyle: DisplayFontStyle = UserPreferencesRepository.DEFAULT_FULL_SCREEN_TEMP_FONT_STYLE,
    val fullScreenTempTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    val forecastTransparencyDeltaPct: Int = UserPreferencesRepository.DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT,
    val forecastBackingTone: BackingTone = BackingTone.DEFAULT,
    val forecastFontSizeSp: Float = UserPreferencesRepository.DEFAULT_FORECAST_FONT_SP,
    // Widget Display settings
    val widgetShowTimeDisplay: Boolean = UserPreferencesRepository.DEFAULT_WIDGET_SHOW_TIME,
    val widgetDateDisplayPosition: DateDisplayPosition = UserPreferencesRepository.DEFAULT_WIDGET_DATE_POSITION,
    val widgetTimeFontSizeSp: Float = UserPreferencesRepository.DEFAULT_WIDGET_TIME_FONT_SP,
    val widgetTimeFontStyle: DisplayFontStyle = UserPreferencesRepository.DEFAULT_WIDGET_TIME_FONT_STYLE,
    val widgetTimeTextColorArgb: Int = UserPreferencesRepository.DEFAULT_WIDGET_TIME_COLOR_ARGB,
    val widgetTempFontSizeSp: Float = UserPreferencesRepository.DEFAULT_WIDGET_TEMP_FONT_SP,
    val widgetTempFontStyle: DisplayFontStyle = UserPreferencesRepository.DEFAULT_WIDGET_TEMP_FONT_STYLE,
    val widgetTempTextColorArgb: Int = UserPreferencesRepository.DEFAULT_WIDGET_TEMP_COLOR_ARGB,
    val widgetDetailsFontSizeSp: Float = UserPreferencesRepository.DEFAULT_WIDGET_DETAILS_FONT_SP,
    val widgetDetailsTransparencyDeltaPct: Int = UserPreferencesRepository.DEFAULT_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT,
    val widgetDetailsBackingTone: BackingTone = BackingTone.DEFAULT,
    val widgetForecastDayOffset: Int = 0,
    val widgetDataFontSizeSp: Float = UserPreferencesRepository.DEFAULT_WIDGET_DATA_FONT_SP,
    val widgetSliderTransparencyDeltaPct: Int = UserPreferencesRepository.DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT,
    val widgetSliderBackingTone: BackingTone = UserPreferencesRepository.DEFAULT_WIDGET_SLIDER_BACKING_TONE
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

        // Legacy integer keys (for seamless in-place migration)
        private val KEY_LEGACY_SHOW_TIME_DISPLAY = booleanPreferencesKey("show_time_display")
        private val KEY_LEGACY_DATE_DISPLAY_POSITION = stringPreferencesKey("date_display_position")
        private val KEY_LEGACY_TIME_FONT_SIZE_SP = intPreferencesKey("time_font_size_sp")
        private val KEY_LEGACY_TIME_FONT_STYLE = stringPreferencesKey("time_font_style")
        private val KEY_LEGACY_TIME_TEXT_COLOR = stringPreferencesKey("time_text_color")
        private val KEY_LEGACY_TIME_TEXT_COLOR_ARGB = intPreferencesKey("time_text_color_argb")
        private val KEY_FULL_SCREEN_TIME_FONT_SP_LEGACY = intPreferencesKey("full_screen_time_font_size_sp")
        private val KEY_FULL_SCREEN_TEMP_FONT_SP_LEGACY = intPreferencesKey("full_screen_temp_font_size_sp")
        private val KEY_FORECAST_FONT_BOOST_SP_LEGACY = intPreferencesKey("forecast_font_boost_sp")
        private val KEY_WIDGET_TIME_FONT_SP_LEGACY = intPreferencesKey("widget_time_font_size_sp")
        private val KEY_WIDGET_TEMP_FONT_SIZE_SP_LEGACY = intPreferencesKey("widget_temp_font_size_sp")
        private val KEY_WIDGET_DATA_FONT_DELTA_SP_LEGACY = intPreferencesKey("widget_data_font_delta_sp")

        // Dedicated Full Screen Display keys (Float sp with 0.5sp precision)
        private val KEY_FULL_SCREEN_SHOW_TIME = booleanPreferencesKey("full_screen_show_time_display")
        private val KEY_FULL_SCREEN_DATE_POSITION = stringPreferencesKey("full_screen_date_display_position")
        private val KEY_FULL_SCREEN_TIME_FONT_SP_F = floatPreferencesKey("full_screen_time_font_sp_f")
        private val KEY_FULL_SCREEN_TIME_FONT_STYLE = stringPreferencesKey("full_screen_time_font_style")
        private val KEY_FULL_SCREEN_TIME_COLOR_ARGB = intPreferencesKey("full_screen_time_text_color_argb")
        private val KEY_FULL_SCREEN_TEMP_FONT_SP_F = floatPreferencesKey("full_screen_temp_font_sp_f")
        private val KEY_FULL_SCREEN_TEMP_FONT_STYLE = stringPreferencesKey("full_screen_temp_font_style")
        private val KEY_FULL_SCREEN_TEMP_COLOR_ARGB = intPreferencesKey("full_screen_temp_text_color_argb")
        private val KEY_FORECAST_TRANSPARENCY_DELTA_PCT = intPreferencesKey("forecast_transparency_delta_pct")
        private val KEY_FORECAST_BACKING_TONE = stringPreferencesKey("forecast_backing_tone")
        private val KEY_FORECAST_FONT_SP_F = floatPreferencesKey("full_screen_forecast_font_sp_f")

        // Dedicated Widget Display keys (Float sp with 0.5sp precision)
        private val KEY_WIDGET_SHOW_TIME = booleanPreferencesKey("widget_show_time_display")
        private val KEY_WIDGET_DATE_POSITION = stringPreferencesKey("widget_date_display_position")
        private val KEY_WIDGET_TIME_FONT_SP_F = floatPreferencesKey("widget_time_font_sp_f")
        private val KEY_WIDGET_TIME_FONT_STYLE = stringPreferencesKey("widget_time_font_style")
        private val KEY_WIDGET_TIME_COLOR_ARGB = intPreferencesKey("widget_time_text_color_argb")
        private val KEY_WIDGET_TEMP_FONT_SP_F = floatPreferencesKey("widget_temp_font_sp_f")
        private val KEY_WIDGET_TEMP_FONT_STYLE = stringPreferencesKey("widget_temp_font_style")
        private val KEY_WIDGET_TEMP_TEXT_COLOR = stringPreferencesKey("widget_temp_text_color")
        private val KEY_WIDGET_TEMP_TEXT_COLOR_ARGB = intPreferencesKey("widget_temp_text_color_argb")
        private val KEY_WIDGET_DETAILS_FONT_SP_F = floatPreferencesKey("widget_details_font_sp_f")
        private val KEY_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT = intPreferencesKey("widget_details_transparency_delta_pct")
        private val KEY_WIDGET_DETAILS_BACKING_TONE = stringPreferencesKey("widget_details_backing_tone")
        private val KEY_WIDGET_DAY_OFFSET = intPreferencesKey("widget_forecast_day_offset")
        private val KEY_WIDGET_DATA_FONT_SP_F = floatPreferencesKey("widget_data_font_sp_f")
        private val KEY_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = intPreferencesKey("widget_slider_transparency_delta_pct")
        private val KEY_WIDGET_SLIDER_BACKING_TONE = stringPreferencesKey("widget_slider_backing_tone")

        private val KEY_INITIAL_SEED_DONE = booleanPreferencesKey("initial_seed_done")
        private val KEY_BUDGET_EPOCH_DAY = longPreferencesKey("budget_epoch_day")
        private val KEY_BUDGET_CALLS = intPreferencesKey("budget_calls_today")

        const val FOLLOW_PRIMARY_LOCATION = -1L
        const val DAILY_PROVIDER_CALL_BUDGET = 600

        // Universal 0.5sp increment step across all font size controls
        const val FONT_SIZE_STEP_SP = 0.5f
        private const val LEGACY_BASE_FONT_SP = 12.0f

        const val MIN_TIME_FONT_SP = 10.0f
        const val MAX_TIME_FONT_SP = 74.0f
        const val DEFAULT_TIME_FONT_SP = 22.0f
        const val DEFAULT_WIDGET_TIME_FONT_SP = 16.0f

        const val DEFAULT_FULL_SCREEN_SHOW_TIME = false
        val DEFAULT_FULL_SCREEN_DATE_POSITION = DateDisplayPosition.OFF

        const val MIN_FULL_SCREEN_TEMP_FONT_SP = 20.0f
        const val MAX_FULL_SCREEN_TEMP_FONT_SP = 74.0f
        const val DEFAULT_FULL_SCREEN_TEMP_FONT_SP = 46.0f
        val DEFAULT_FULL_SCREEN_TEMP_FONT_STYLE = DisplayFontStyle.SERIF

        const val DEFAULT_WIDGET_SHOW_TIME = true
        val DEFAULT_WIDGET_DATE_POSITION = DateDisplayPosition.LEFT_OF_TIME
        val DEFAULT_WIDGET_TIME_FONT_STYLE = DisplayFontStyle.SERIF
        const val DEFAULT_WIDGET_TIME_COLOR_ARGB = 0xFF18FF18.toInt()

        const val MIN_WIDGET_TEMP_FONT_SP = 20.0f
        const val MAX_WIDGET_TEMP_FONT_SP = 74.0f
        const val DEFAULT_WIDGET_TEMP_FONT_SP = 60.0f
        val DEFAULT_WIDGET_TEMP_FONT_STYLE = DisplayFontStyle.DEFAULT
        const val DEFAULT_WIDGET_TEMP_COLOR_ARGB = 0xFF6AFF25.toInt()

        const val MIN_FORECAST_TRANSPARENCY_DELTA_PCT = -60
        const val MAX_FORECAST_TRANSPARENCY_DELTA_PCT = 60
        const val DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT = 0
        const val FORECAST_TRANSPARENCY_STEP_PCT = 10

        const val MIN_FORECAST_FONT_SP = 7.0f
        const val MAX_FORECAST_FONT_SP = 22.0f
        const val DEFAULT_FORECAST_FONT_SP = 11.0f

        const val MIN_WIDGET_DETAILS_FONT_SP = 7.0f
        const val MAX_WIDGET_DETAILS_FONT_SP = 18.0f
        const val DEFAULT_WIDGET_DETAILS_FONT_SP = 10.0f

        const val MIN_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT = -60
        const val MAX_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT = 60
        const val DEFAULT_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT = 0
        const val WIDGET_DETAILS_TRANSPARENCY_STEP_PCT = 10

        const val MIN_WIDGET_DATA_FONT_SP = 7.0f
        const val MAX_WIDGET_DATA_FONT_SP = 17.0f
        const val DEFAULT_WIDGET_DATA_FONT_SP = 9.0f

        const val MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = -60
        const val MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = 60
        const val DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT = 10
        const val WIDGET_SLIDER_TRANSPARENCY_STEP_PCT = 10
        val DEFAULT_WIDGET_SLIDER_BACKING_TONE = BackingTone.DARKEN

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
        val fsShowTime = prefs[KEY_FULL_SCREEN_SHOW_TIME] ?: DEFAULT_FULL_SCREEN_SHOW_TIME
        val fsDatePosition = runCatching {
            DateDisplayPosition.valueOf(prefs[KEY_FULL_SCREEN_DATE_POSITION] ?: DEFAULT_FULL_SCREEN_DATE_POSITION.name)
        }.getOrDefault(DEFAULT_FULL_SCREEN_DATE_POSITION)
        val fsTimeFontSp = (
            prefs[KEY_FULL_SCREEN_TIME_FONT_SP_F]
                ?: prefs[KEY_FULL_SCREEN_TIME_FONT_SP_LEGACY]?.toFloat()
                ?: DEFAULT_TIME_FONT_SP
            ).snapHalfSp().safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
        val fsTimeStyle = runCatching {
            DisplayFontStyle.valueOf(prefs[KEY_FULL_SCREEN_TIME_FONT_STYLE] ?: DisplayFontStyle.DEFAULT.name)
        }.getOrDefault(DisplayFontStyle.DEFAULT)
        val fsTimeColorArgb = (prefs[KEY_FULL_SCREEN_TIME_COLOR_ARGB] ?: DisplayTextColor.WHITE.argb) or 0xFF000000.toInt()

        // Full Screen Temperature
        val fsTempSp = (
            prefs[KEY_FULL_SCREEN_TEMP_FONT_SP_F]
                ?: prefs[KEY_FULL_SCREEN_TEMP_FONT_SP_LEGACY]?.toFloat()
                ?: DEFAULT_FULL_SCREEN_TEMP_FONT_SP
            ).snapHalfSp().safeClamp(MIN_FULL_SCREEN_TEMP_FONT_SP, MAX_FULL_SCREEN_TEMP_FONT_SP)
        val fsTempStyle = runCatching {
            DisplayFontStyle.valueOf(prefs[KEY_FULL_SCREEN_TEMP_FONT_STYLE] ?: DEFAULT_FULL_SCREEN_TEMP_FONT_STYLE.name)
        }.getOrDefault(DEFAULT_FULL_SCREEN_TEMP_FONT_STYLE)
        val fsTempColorArgb = (prefs[KEY_FULL_SCREEN_TEMP_COLOR_ARGB] ?: DisplayTextColor.WHITE.argb) or 0xFF000000.toInt()

        // Full Screen Forecast Cards
        val safeTransparencyDeltaPct = (prefs[KEY_FORECAST_TRANSPARENCY_DELTA_PCT] ?: DEFAULT_FORECAST_TRANSPARENCY_DELTA_PCT)
            .safeClamp(MIN_FORECAST_TRANSPARENCY_DELTA_PCT, MAX_FORECAST_TRANSPARENCY_DELTA_PCT)
        val fsBackingTone = runCatching {
            BackingTone.valueOf(prefs[KEY_FORECAST_BACKING_TONE] ?: BackingTone.DEFAULT.name)
        }.getOrDefault(BackingTone.DEFAULT)
        val safeForecastFontSp = (
            prefs[KEY_FORECAST_FONT_SP_F]
                ?: prefs[KEY_FORECAST_FONT_BOOST_SP_LEGACY]?.let { LEGACY_BASE_FONT_SP + it.toFloat() }
                ?: DEFAULT_FORECAST_FONT_SP
            ).snapHalfSp().safeClamp(MIN_FORECAST_FONT_SP, MAX_FORECAST_FONT_SP)

        // Widget Time & Date (with seamless fallback to legacy keys only if set)
        val wShowTime = prefs[KEY_WIDGET_SHOW_TIME]
            ?: prefs[KEY_LEGACY_SHOW_TIME_DISPLAY]
            ?: DEFAULT_WIDGET_SHOW_TIME
        val wDatePosition = runCatching {
            DateDisplayPosition.valueOf(
                prefs[KEY_WIDGET_DATE_POSITION]
                    ?: prefs[KEY_LEGACY_DATE_DISPLAY_POSITION]
                    ?: DEFAULT_WIDGET_DATE_POSITION.name
            )
        }.getOrDefault(DEFAULT_WIDGET_DATE_POSITION)
        val wTimeFontSp = (
            prefs[KEY_WIDGET_TIME_FONT_SP_F]
                ?: prefs[KEY_WIDGET_TIME_FONT_SP_LEGACY]?.toFloat()
                ?: prefs[KEY_LEGACY_TIME_FONT_SIZE_SP]?.toFloat()
                ?: DEFAULT_WIDGET_TIME_FONT_SP
            ).snapHalfSp().safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
        val wTimeStyle = runCatching {
            DisplayFontStyle.valueOf(
                prefs[KEY_WIDGET_TIME_FONT_STYLE]
                    ?: prefs[KEY_LEGACY_TIME_FONT_STYLE]
                    ?: DEFAULT_WIDGET_TIME_FONT_STYLE.name
            )
        }.getOrDefault(DEFAULT_WIDGET_TIME_FONT_STYLE)
        val legacyTimeColor = prefs[KEY_LEGACY_TIME_TEXT_COLOR]?.let {
            runCatching { DisplayTextColor.valueOf(it) }.getOrNull()
        }
        val wTimeColorArgb = (
            prefs[KEY_WIDGET_TIME_COLOR_ARGB]
                ?: prefs[KEY_LEGACY_TIME_TEXT_COLOR_ARGB]
                ?: legacyTimeColor?.argb
                ?: DEFAULT_WIDGET_TIME_COLOR_ARGB
            ) or 0xFF000000.toInt()

        // Widget Temperature
        val safeWidgetTempSp = (
            prefs[KEY_WIDGET_TEMP_FONT_SP_F]
                ?: prefs[KEY_WIDGET_TEMP_FONT_SIZE_SP_LEGACY]?.toFloat()
                ?: DEFAULT_WIDGET_TEMP_FONT_SP
            ).snapHalfSp().safeClamp(MIN_WIDGET_TEMP_FONT_SP, MAX_WIDGET_TEMP_FONT_SP)
        val widgetTempStyle = runCatching {
            DisplayFontStyle.valueOf(prefs[KEY_WIDGET_TEMP_FONT_STYLE] ?: DEFAULT_WIDGET_TEMP_FONT_STYLE.name)
        }.getOrDefault(DEFAULT_WIDGET_TEMP_FONT_STYLE)
        val legacyWidgetTempColor = prefs[KEY_WIDGET_TEMP_TEXT_COLOR]?.let {
            runCatching { DisplayTextColor.valueOf(it) }.getOrNull()
        }
        val widgetTempColorArgb = (
            prefs[KEY_WIDGET_TEMP_TEXT_COLOR_ARGB]
                ?: legacyWidgetTempColor?.argb
                ?: DEFAULT_WIDGET_TEMP_COLOR_ARGB
            ) or 0xFF000000.toInt()

        // Widget Details Below Temp (H:/L:, Feels, Sky/Humidity, Wind/UV)
        val safeWidgetDetailsFontSp = (
            prefs[KEY_WIDGET_DETAILS_FONT_SP_F]
                ?: DEFAULT_WIDGET_DETAILS_FONT_SP
            ).snapHalfSp().safeClamp(MIN_WIDGET_DETAILS_FONT_SP, MAX_WIDGET_DETAILS_FONT_SP)
        val safeWidgetDetailsTransparency = (
            prefs[KEY_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT]
                ?: DEFAULT_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT
            ).safeClamp(MIN_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT)
        val widgetDetailsTone = runCatching {
            BackingTone.valueOf(prefs[KEY_WIDGET_DETAILS_BACKING_TONE] ?: BackingTone.DEFAULT.name)
        }.getOrDefault(BackingTone.DEFAULT)

        // Widget 10-Day Forecast Slider
        val safeDayOffset = (prefs[KEY_WIDGET_DAY_OFFSET] ?: 0).safeClamp(0, 5)
        val safeWidgetDataFontSp = (
            prefs[KEY_WIDGET_DATA_FONT_SP_F]
                ?: prefs[KEY_WIDGET_DATA_FONT_DELTA_SP_LEGACY]?.let { LEGACY_BASE_FONT_SP + it.toFloat() }
                ?: DEFAULT_WIDGET_DATA_FONT_SP
            ).snapHalfSp().safeClamp(MIN_WIDGET_DATA_FONT_SP, MAX_WIDGET_DATA_FONT_SP)
        val safeWidgetSliderTransparency = (
            prefs[KEY_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT]
                ?: DEFAULT_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT
            ).safeClamp(MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT)
        val widgetSliderTone = runCatching {
            BackingTone.valueOf(prefs[KEY_WIDGET_SLIDER_BACKING_TONE] ?: DEFAULT_WIDGET_SLIDER_BACKING_TONE.name)
        }.getOrDefault(DEFAULT_WIDGET_SLIDER_BACKING_TONE)

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
            forecastFontSizeSp = safeForecastFontSp,
            widgetShowTimeDisplay = wShowTime,
            widgetDateDisplayPosition = wDatePosition,
            widgetTimeFontSizeSp = wTimeFontSp,
            widgetTimeFontStyle = wTimeStyle,
            widgetTimeTextColorArgb = wTimeColorArgb,
            widgetTempFontSizeSp = safeWidgetTempSp,
            widgetTempFontStyle = widgetTempStyle,
            widgetTempTextColorArgb = widgetTempColorArgb,
            widgetDetailsFontSizeSp = safeWidgetDetailsFontSp,
            widgetDetailsTransparencyDeltaPct = safeWidgetDetailsTransparency,
            widgetDetailsBackingTone = widgetDetailsTone,
            widgetForecastDayOffset = safeDayOffset,
            widgetDataFontSizeSp = safeWidgetDataFontSp,
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

    suspend fun adjustFullScreenTimeFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_FULL_SCREEN_TIME_FONT_SP_F]
                    ?: prefs[KEY_FULL_SCREEN_TIME_FONT_SP_LEGACY]?.toFloat()
                    ?: DEFAULT_TIME_FONT_SP
                ).snapHalfSp().safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
            prefs[KEY_FULL_SCREEN_TIME_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
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
    suspend fun adjustFullScreenTempFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_FULL_SCREEN_TEMP_FONT_SP_F]
                    ?: prefs[KEY_FULL_SCREEN_TEMP_FONT_SP_LEGACY]?.toFloat()
                    ?: DEFAULT_FULL_SCREEN_TEMP_FONT_SP
                ).snapHalfSp().safeClamp(MIN_FULL_SCREEN_TEMP_FONT_SP, MAX_FULL_SCREEN_TEMP_FONT_SP)
            prefs[KEY_FULL_SCREEN_TEMP_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
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

    suspend fun adjustForecastFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_FORECAST_FONT_SP_F]
                    ?: prefs[KEY_FORECAST_FONT_BOOST_SP_LEGACY]?.let { LEGACY_BASE_FONT_SP + it.toFloat() }
                    ?: DEFAULT_FORECAST_FONT_SP
                ).snapHalfSp().safeClamp(MIN_FORECAST_FONT_SP, MAX_FORECAST_FONT_SP)
            prefs[KEY_FORECAST_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
                .safeClamp(MIN_FORECAST_FONT_SP, MAX_FORECAST_FONT_SP)
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

    suspend fun adjustWidgetTimeFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_WIDGET_TIME_FONT_SP_F]
                    ?: prefs[KEY_WIDGET_TIME_FONT_SP_LEGACY]?.toFloat()
                    ?: prefs[KEY_LEGACY_TIME_FONT_SIZE_SP]?.toFloat()
                    ?: DEFAULT_WIDGET_TIME_FONT_SP
                ).snapHalfSp().safeClamp(MIN_TIME_FONT_SP, MAX_TIME_FONT_SP)
            prefs[KEY_WIDGET_TIME_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
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
    suspend fun adjustWidgetTempFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_WIDGET_TEMP_FONT_SP_F]
                    ?: prefs[KEY_WIDGET_TEMP_FONT_SIZE_SP_LEGACY]?.toFloat()
                    ?: DEFAULT_WIDGET_TEMP_FONT_SP
                ).snapHalfSp().safeClamp(MIN_WIDGET_TEMP_FONT_SP, MAX_WIDGET_TEMP_FONT_SP)
            prefs[KEY_WIDGET_TEMP_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
                .safeClamp(MIN_WIDGET_TEMP_FONT_SP, MAX_WIDGET_TEMP_FONT_SP)
        }
    }

    suspend fun setWidgetTempFontStyle(style: DisplayFontStyle) {
        dataStore.edit { it[KEY_WIDGET_TEMP_FONT_STYLE] = style.name }
    }

    suspend fun setWidgetTempTextColorArgb(argb: Int) {
        dataStore.edit { it[KEY_WIDGET_TEMP_TEXT_COLOR_ARGB] = argb or 0xFF000000.toInt() }
    }

    // Widget Details Below Temp setters
    suspend fun adjustWidgetDetailsFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (prefs[KEY_WIDGET_DETAILS_FONT_SP_F] ?: DEFAULT_WIDGET_DETAILS_FONT_SP)
                .snapHalfSp()
                .safeClamp(MIN_WIDGET_DETAILS_FONT_SP, MAX_WIDGET_DETAILS_FONT_SP)
            prefs[KEY_WIDGET_DETAILS_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
                .safeClamp(MIN_WIDGET_DETAILS_FONT_SP, MAX_WIDGET_DETAILS_FONT_SP)
        }
    }

    suspend fun adjustWidgetDetailsTransparencyDelta(deltaPct: Int) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT]
                    ?: DEFAULT_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT
                ).safeClamp(MIN_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT)
            prefs[KEY_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT] = (current + deltaPct)
                .safeClamp(MIN_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT, MAX_WIDGET_DETAILS_TRANSPARENCY_DELTA_PCT)
        }
    }

    suspend fun setWidgetDetailsBackingTone(tone: BackingTone) {
        dataStore.edit { it[KEY_WIDGET_DETAILS_BACKING_TONE] = tone.name }
    }

    // Widget 10-Day Slider setters
    suspend fun setWidgetForecastDayOffset(offset: Int) {
        val clamped = offset.safeClamp(0, 5)
        dataStore.edit { it[KEY_WIDGET_DAY_OFFSET] = clamped }
    }

    suspend fun adjustWidgetDataFontSize(deltaSp: Float) {
        dataStore.edit { prefs ->
            val current = (
                prefs[KEY_WIDGET_DATA_FONT_SP_F]
                    ?: prefs[KEY_WIDGET_DATA_FONT_DELTA_SP_LEGACY]?.let { LEGACY_BASE_FONT_SP + it.toFloat() }
                    ?: DEFAULT_WIDGET_DATA_FONT_SP
                ).snapHalfSp().safeClamp(MIN_WIDGET_DATA_FONT_SP, MAX_WIDGET_DATA_FONT_SP)
            prefs[KEY_WIDGET_DATA_FONT_SP_F] = (current + deltaSp)
                .snapHalfSp()
                .safeClamp(MIN_WIDGET_DATA_FONT_SP, MAX_WIDGET_DATA_FONT_SP)
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
