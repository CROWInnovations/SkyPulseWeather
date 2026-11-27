package com.crowinnovations.skypulseweather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.crowinnovations.skypulseweather.MainActivity
import com.crowinnovations.skypulseweather.R
import com.crowinnovations.skypulseweather.data.local.AppPreferences
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.data.repository.WeatherRepository
import com.crowinnovations.skypulseweather.domain.BackingTone
import com.crowinnovations.skypulseweather.domain.DateDisplayPosition
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.LunarPhaseCalculator
import com.crowinnovations.skypulseweather.domain.MoonPhaseInfo
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.safeClamp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun prefsRepository(): UserPreferencesRepository
}

internal data class WidgetDayUi(
    val dateDayLabel: String,
    val glyph: Bitmap,
    val highLowLabel: String,
    val precipPercent: Int,
    val showRainPercentBesideCloud: Boolean
)

internal data class WidgetDataModel(
    val locationId: Long,
    val locationName: String,
    val currentTemp: String,
    val highLowCompact: String,
    val highLowLabeled: String,
    val conditionOnly: String,
    val feelsOnly: String,
    val humidityLine: String,
    val subtitleSmall: String,
    val highLowSkyFeelsWide: String,
    val highLowSkyFeelsCompact: String,
    val windAndUvLine: String,
    val updatedLabel: String,
    val alertSummary: String?,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val cloudCoverPercent: Int,
    val hasTornadoWarning: Boolean,
    val moonPhase: MoonPhaseInfo,
    val fetchedAtMillis: Long,
    val days: List<WidgetDayUi>
)

/**
 * Publishes exact-sized Jetpack Glance `RemoteViews` for all SkyPulse widget receivers
 * (`5x3`, `5x2`, `4x2`, `2x2`, `2x1`, `1x1`) and attaches the root-level `RemoteCollectionItems` adapter
 * onto `R.id.widget_ten_day_list` so the 10-day forecast row can be swiped right and left natively.
 */
object WeatherWidgetPublisher {

    const val ACTION_REFRESH_WIDGET = "com.crowinnovations.skypulseweather.ACTION_REFRESH_WIDGET"
    const val EXTRA_LOCATION_ID = "extra_location_id"
    const val ACTION_SCROLL_FORECAST = "com.crowinnovations.skypulseweather.ACTION_SCROLL_FORECAST"
    const val EXTRA_TARGET_OFFSET = "extra_target_day_offset"

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glanceComposer = GlanceRemoteViews()

    private val receiverClasses = listOf(
        WeatherWidget5x3Receiver::class.java,
        WeatherWidget5x2Receiver::class.java,
        WeatherGlanceReceiver::class.java,
        WeatherWidget2x2Receiver::class.java,
        WeatherWidget2x1Receiver::class.java,
        WeatherWidget1x1Receiver::class.java
    )

    suspend fun publishAll(
        context: Context,
        explicitScrollOffset: Int? = null
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val allIds = receiverClasses.flatMap { cls ->
            runCatching {
                manager.getAppWidgetIds(ComponentName(appContext, cls)).toList()
            }.getOrDefault(emptyList())
        }.distinct()
        if (allIds.isNotEmpty()) {
            publishIds(appContext, allIds.toIntArray(), explicitScrollOffset)
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun publishIds(
        context: Context,
        appWidgetIds: IntArray,
        explicitScrollOffset: Int? = null
    ) = withContext(Dispatchers.IO) {
        if (appWidgetIds.isEmpty()) return@withContext
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WidgetEntryPoint::class.java
        )
        val repository = entryPoint.weatherRepository()
        val (locations, weatherMap, prefs) = repository.getWidgetInitialState()
        val activeScrollOffset = (explicitScrollOffset ?: prefs.widgetForecastDayOffset).safeClamp(0, 5)

        val chosenLocation = locations.firstOrNull { it.id == prefs.activeLocationId }
            ?: locations.firstOrNull()
        val chosenSnapshot = chosenLocation?.let { weatherMap[it.id] }
        val model = buildWidgetDataModel(
            context = appContext,
            resolved = chosenLocation?.let { it to chosenSnapshot },
            tempUnit = prefs.tempUnit,
            windUnit = prefs.windUnit
        )

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_LOCATION_ID, model.locationId)
        }
        val safeFontScale = appContext.resources.configuration.fontScale.safeClamp(0.85f, 1.45f)
        val manager = AppWidgetManager.getInstance(appContext)

        for (widgetId in appWidgetIds) {
            runCatching {
                val options = manager.getAppWidgetOptions(widgetId) ?: Bundle()
                val providerInfo = manager.getAppWidgetInfo(widgetId)
                val targetSizes = resolveTargetWidgetSizesDp(appContext, options, providerInfo?.provider?.className)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSizes.size > 1) {
                    val sizeMap = LinkedHashMap<SizeF, RemoteViews>()
                    for ((wDp, hDp) in targetSizes) {
                        val rv = composeSingleSizeRemoteViews(
                            context = appContext,
                            options = options,
                            model = model,
                            prefs = prefs,
                            scrollOffset = activeScrollOffset,
                            wDp = wDp,
                            hDp = hDp,
                            safeFontScale = safeFontScale,
                            launchIntent = launchIntent
                        )
                        sizeMap[SizeF(wDp, hDp)] = rv
                    }
                    manager.updateAppWidget(widgetId, RemoteViews(sizeMap))
                } else {
                    val (wDp, hDp) = targetSizes.first()
                    val rv = composeSingleSizeRemoteViews(
                        context = appContext,
                        options = options,
                        model = model,
                        prefs = prefs,
                        scrollOffset = activeScrollOffset,
                        wDp = wDp,
                        hDp = hDp,
                        safeFontScale = safeFontScale,
                        launchIntent = launchIntent
                    )
                    manager.updateAppWidget(widgetId, rv)
                }
            }
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun composeSingleSizeRemoteViews(
        context: Context,
        options: Bundle,
        model: WidgetDataModel,
        prefs: AppPreferences,
        scrollOffset: Int,
        wDp: Float,
        hDp: Float,
        safeFontScale: Float,
        launchIntent: Intent
    ): RemoteViews {
        val isLargeForecastMode = wDp > 212f && hDp >= 145f && model.days.isNotEmpty()
        val isTall = hDp >= 225f
        val isCompactHeight = !isTall
        val topPadFloat = if (wDp < 108f) 4f else if (isCompactHeight) 5f else 6f
        val rawHostRatio = options.getFloat("hsResizeRatio", 1f)
        val hostResizeRatio = if (rawHostRatio in 0.5f..1.5f) {
            rawHostRatio.safeClamp(0.65f, 1.25f)
        } else {
            1f
        }
        val maxVirtualClockSpCap = (74f / hostResizeRatio).safeClamp(44f, 114f)
        val dataFontDelta = prefs.widgetDataFontDeltaSp.safeClamp(
            UserPreferencesRepository.MIN_WIDGET_DATA_FONT_DELTA_SP,
            UserPreferencesRepository.MAX_WIDGET_DATA_FONT_DELTA_SP
        ).toFloat()
        val sliderBackingColor = resolveWidgetSliderBackingColor(
            tone = prefs.widgetSliderBackingTone,
            transparencyDeltaPct = prefs.widgetSliderTransparencyDeltaPct
        )
        val isLightOpaqueSlider = prefs.widgetSliderBackingTone == BackingTone.LIGHTEN &&
            sliderBackingColor.alpha >= 0.45f
        val sliderPrimaryTextArgb = if (isLightOpaqueSlider) 0xFF0B1324.toInt() else 0xFFF8FAFC.toInt()
        val sliderRainTextArgb = if (isLightOpaqueSlider) 0xFF0369A1.toInt() else 0xFF7DD3FC.toInt()

        val hasInlineDate = prefs.widgetDateDisplayPosition == DateDisplayPosition.LEFT_OF_TIME ||
            prefs.widgetDateDisplayPosition == DateDisplayPosition.RIGHT_OF_TIME
        val hasBelowDate = prefs.widgetDateDisplayPosition == DateDisplayPosition.BELOW_TIME
        val is24h = android.text.format.DateFormat.is24HourFormat(context)
        val stripExtraBoostDp = (dataFontDelta * 2.6f * safeFontScale) / hostResizeRatio
        val stripHeightFloat = if (isTall) {
            ((68f / hostResizeRatio).safeClamp(68f, 78f) + stripExtraBoostDp).safeClamp(68f, (hDp * 0.34f).coerceAtLeast(78f))
        } else {
            ((56f / hostResizeRatio).safeClamp(56f, 64f) + stripExtraBoostDp).safeClamp(56f, (hDp * 0.40f).coerceAtLeast(64f))
        }

        val maxHeightClockSp = when {
            wDp < 106f || hDp < 106f -> if (hasBelowDate) 11f else 15f
            hDp < 145f -> if (hasBelowDate) 14f else 22f
            !isLargeForecastMode -> {
                val budget = (hDp - 88f).coerceAtLeast(18f)
                val rawMax = if (hasBelowDate) {
                    (budget - 5f) / (2.04f * safeFontScale)
                } else {
                    (budget - 3f) / (1.22f * safeFontScale)
                }
                rawMax.safeClamp(12f, (44f / hostResizeRatio).coerceAtLeast(12f))
            }
            else -> {
                val tenDayOverhead = stripHeightFloat + (if (isTall && model.days.size > 5) 28f else 10f)
                val alertOverhead = if (isTall && model.alertSummary != null) 28f else 0f
                val minTelemetryAndHeroH = (if (isCompactHeight) 98f else 130f) + dataFontDelta * 5.5f
                val budget = (hDp - topPadFloat * 2f - tenDayOverhead - alertOverhead - minTelemetryAndHeroH)
                    .coerceAtLeast(20f)
                val rawMax = if (hasBelowDate) {
                    (budget - 5f) / (2.04f * safeFontScale)
                } else {
                    (budget - 3f) / (1.22f * safeFontScale)
                }
                rawMax.safeClamp(14f, maxVirtualClockSpCap)
            }
        }
        val availClockWidthDp = (wDp - 20f).coerceAtLeast(52f)
        val charCountEst = when {
            hasInlineDate && is24h -> 14.5f
            hasInlineDate -> 17.5f
            is24h -> 5.5f
            else -> 8.5f
        }
        val inlineCharUnits = charCountEst * 0.56f * safeFontScale
        val maxWidthClockSp = (availClockWidthDp / inlineCharUnits.coerceAtLeast(1.8f))
            .safeClamp(8.5f, maxVirtualClockSpCap)
        val minAllowedClockSp = if (wDp < 106f) 8.5f else 11f
        val effectiveMaxClockSp = minOf(maxHeightClockSp, maxWidthClockSp).coerceAtLeast(minAllowedClockSp)
        val desiredVirtualClockSp = prefs.widgetTimeFontSizeSp.toFloat() / hostResizeRatio
        val effectiveClockSp = desiredVirtualClockSp.safeClamp(minAllowedClockSp, effectiveMaxClockSp)
        val effectiveDateBelowSp = (effectiveClockSp * 0.72f).safeClamp(8.5f, 48f)

        val clockRowHeightFloat = if (hasBelowDate) {
            (effectiveClockSp + effectiveDateBelowSp) * safeFontScale * 1.20f + 5f
        } else {
            effectiveClockSp * safeFontScale * 1.22f + 3f
        }
        val clockRowHeightDp = clockRowHeightFloat.dp
        val clockRemoteViews = if (!prefs.widgetShowTimeDisplay) {
            null
        } else {
            buildStyledTopClockRemoteViews(
                context = context,
                fontSizeSp = effectiveClockSp,
                belowDateFontSizeSp = effectiveDateBelowSp,
                fontStyle = prefs.widgetTimeFontStyle,
                textColorArgb = prefs.widgetTimeTextColorArgb,
                datePosition = prefs.widgetDateDisplayPosition
            )
        }

        // Top fraction reserved strictly for the centered Time/Date display so Moon, Sun & clouds sit below it
        val topReservedFraction = if (prefs.widgetShowTimeDisplay) {
            ((topPadFloat + clockRowHeightFloat + 2f) / hDp.coerceAtLeast(60f)).safeClamp(0.10f, 0.40f)
        } else {
            0f
        }

        // Use the full open right-hand celestial space down to right above the 10-day header (+ Moon Phase label)
        val maxMoonBottomFraction = if (isLargeForecastMode) {
            val tenDayH = stripHeightFloat + (if (isTall && model.days.size > 5) 28f else 10f)
            val alertH = if (isTall && model.alertSummary != null) 29f else 0f
            val moonPhaseLabelH = if (!model.isDay) (18f + dataFontDelta * 1.4f) else 0f
            val maxBottomDp = (hDp - topPadFloat - tenDayH - alertH - moonPhaseLabelH - 6f)
                .coerceAtLeast(hDp * (topReservedFraction + 0.18f))
            (maxBottomDp / hDp.coerceAtLeast(80f)).safeClamp(topReservedFraction + 0.18f, 0.74f)
        } else {
            ((hDp - 44f) / hDp.coerceAtLeast(60f)).safeClamp(topReservedFraction + 0.18f, 0.72f)
        }

        val isLargerThan2x2 = (wDp > 212f && hDp >= 108f) || (wDp >= 108f && hDp > 212f)
        val (targetW, targetH) = WidgetBitmapRenderer.targetCanvasSizeForAspect(wDp, hDp)
        val sceneBitmap = WidgetBitmapRenderer.renderWidgetSceneBackdrop(
            context = context,
            condition = model.condition,
            isDay = model.isDay,
            cloudCoverPercent = model.cloudCoverPercent,
            hasTornadoWarning = model.hasTornadoWarning,
            moonPhase = model.moonPhase,
            widthPx = targetW,
            heightPx = targetH,
            topReservedFraction = topReservedFraction,
            maxMoonBottomFraction = maxMoonBottomFraction,
            suppressStaticRainStreaks = isLargerThan2x2
        )

        val isTiny1x1 = wDp < 106f && hDp < 106f
        val motionRemoteViews = if (isTiny1x1) {
            null
        } else {
            val cloudFrames = WidgetBitmapRenderer.renderDriftingCloudFrames(
                context = context,
                condition = model.condition,
                isDay = model.isDay,
                cloudCoverPercent = model.cloudCoverPercent,
                widthPx = targetW,
                heightPx = targetH,
                topReservedFraction = topReservedFraction
            )
            val sunRayFrames = WidgetBitmapRenderer.renderSolarRayFrames(
                condition = model.condition,
                isDay = model.isDay,
                hasTornadoWarning = model.hasTornadoWarning,
                widthPx = targetW,
                heightPx = targetH,
                topReservedFraction = topReservedFraction
            )
            val fallingRainCurtain = WidgetBitmapRenderer.renderSeamlessFallingRainCurtain(
                condition = model.condition,
                hasTornadoWarning = model.hasTornadoWarning,
                isLargerThan2x2 = isLargerThan2x2,
                widthPx = targetW,
                heightPx = targetH
            )
            if (cloudFrames == null && sunRayFrames == null && fallingRainCurtain == null) {
                null
            } else {
                RemoteViews(context.packageName, R.layout.widget_cloud_motion).apply {
                    if (cloudFrames != null) {
                        setViewVisibility(R.id.widget_cloud_flipper, View.VISIBLE)
                        setImageViewBitmap(R.id.widget_cloud_frame_1, cloudFrames.first)
                        setImageViewBitmap(R.id.widget_cloud_frame_2, cloudFrames.second)
                        setImageViewBitmap(R.id.widget_cloud_frame_3, cloudFrames.third)
                    } else {
                        setViewVisibility(R.id.widget_cloud_flipper, View.GONE)
                    }

                    if (sunRayFrames != null) {
                        setViewVisibility(R.id.widget_sun_pulse_flipper, View.VISIBLE)
                        setImageViewBitmap(R.id.widget_sun_pulse_frame_1, sunRayFrames.first)
                        setImageViewBitmap(R.id.widget_sun_pulse_frame_2, sunRayFrames.second)
                        setImageViewBitmap(R.id.widget_sun_pulse_frame_3, sunRayFrames.third)
                    } else {
                        setViewVisibility(R.id.widget_sun_pulse_flipper, View.GONE)
                    }

                    if (fallingRainCurtain != null) {
                        setViewVisibility(R.id.widget_rain_splash_flipper, View.VISIBLE)
                        setImageViewBitmap(R.id.widget_rain_splash_frame_1, fallingRainCurtain)
                        setImageViewBitmap(R.id.widget_rain_splash_frame_2, fallingRainCurtain)
                    } else {
                        setViewVisibility(R.id.widget_rain_splash_flipper, View.GONE)
                    }
                }
            }
        }

        val stripWidthDp = (wDp - 12f).coerceAtLeast(140f)
        val useNativeSwipeStrip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isLargeForecastMode
        val stripContainerRemoteViews = if (useNativeSwipeStrip) {
            createSwipeStripShellRemoteViews(context, stripWidthDp, stripHeightFloat)
        } else {
            null
        }

        val compositionResult = glanceComposer.compose(
            context = context,
            size = DpSize(wDp.dp, hDp.dp),
            appWidgetOptions = options
        ) {
            GlanceTheme {
                WidgetCardSurface(
                    context = context,
                    model = model,
                    prefs = prefs,
                    sceneBackdrop = sceneBitmap,
                    cloudMotionRemoteViews = motionRemoteViews,
                    topClockRemoteViews = clockRemoteViews,
                    topClockHeightDp = clockRowHeightDp,
                    topReservedFraction = topReservedFraction,
                    maxMoonBottomFraction = maxMoonBottomFraction,
                    swipeStripShellRemoteViews = stripContainerRemoteViews,
                    dayOffset = scrollOffset,
                    widthDp = wDp,
                    heightDp = hDp,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = safeFontScale,
                    launchIntent = launchIntent
                )
            }
        }

        val rootRv = compositionResult.remoteViews
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useNativeSwipeStrip) {
            attachSwipeableTenDayAdapterToRoot(
                context = context,
                rootRv = rootRv,
                days = model.days,
                stripWidthDp = stripWidthDp,
                stripHeightDp = stripHeightFloat,
                dataFontDeltaSp = dataFontDelta,
                primaryTextArgb = sliderPrimaryTextArgb,
                rainTextArgb = sliderRainTextArgb,
                hostResizeRatio = hostResizeRatio,
                fontScale = safeFontScale,
                isTall = isTall,
                scrollOffset = scrollOffset,
                launchIntent = launchIntent
            )
        }
        return rootRv
    }

    private fun resolveTargetWidgetSizesDp(
        context: Context,
        options: Bundle,
        providerClassName: String?
    ): List<Pair<Float, Float>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val reported = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width > 32f && it.height > 32f }
                .orEmpty()
            if (reported.isNotEmpty()) {
                return reported
                    .distinctBy { "${(it.width / 8f).toInt()}_${(it.height / 8f).toInt()}" }
                    .take(2)
                    .map { it.width to it.height }
            }
        }

        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).toFloat()
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).toFloat()
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()

        val isLandscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val chosenW = if (isLandscape && maxW > 32f) maxW else if (minW > 32f) minW else maxW
        val chosenH = if (isLandscape && minH > 32f) minH else if (maxH > 32f) maxH else minH
        if (chosenW > 32f && chosenH > 32f) {
            return listOf(chosenW to chosenH)
        }

        return when {
            providerClassName?.endsWith("WeatherWidget1x1Receiver") == true -> listOf(76f to 76f)
            providerClassName?.endsWith("WeatherWidget2x1Receiver") == true -> listOf(165f to 82f)
            providerClassName?.endsWith("WeatherWidget2x2Receiver") == true -> listOf(165f to 165f)
            providerClassName?.endsWith("WeatherWidget5x2Receiver") == true -> listOf(390f to 215f)
            providerClassName?.endsWith("WeatherWidget5x3Receiver") == true -> listOf(390f to 310f)
            else -> listOf(320f to 215f)
        }
    }
}

private fun buildStyledTopClockRemoteViews(
    context: Context,
    fontSizeSp: Float,
    belowDateFontSizeSp: Float,
    fontStyle: DisplayFontStyle,
    textColorArgb: Int,
    datePosition: DateDisplayPosition
): RemoteViews {
    val safeColorArgb = textColorArgb or 0xFF000000.toInt()
    val allClockIds = listOf(
        R.id.widget_clock_default,
        R.id.widget_clock_serif,
        R.id.widget_clock_mono,
        R.id.widget_clock_italic
    )
    val activeClockId = when (fontStyle) {
        DisplayFontStyle.DEFAULT -> R.id.widget_clock_default
        DisplayFontStyle.SERIF -> R.id.widget_clock_serif
        DisplayFontStyle.MONO -> R.id.widget_clock_mono
        DisplayFontStyle.ITALIC -> R.id.widget_clock_italic
    }
    val allBelowDateIds = listOf(
        R.id.widget_date_below_default,
        R.id.widget_date_below_serif,
        R.id.widget_date_below_mono,
        R.id.widget_date_below_italic
    )
    val activeBelowDateId = when (fontStyle) {
        DisplayFontStyle.DEFAULT -> R.id.widget_date_below_default
        DisplayFontStyle.SERIF -> R.id.widget_date_below_serif
        DisplayFontStyle.MONO -> R.id.widget_date_below_mono
        DisplayFontStyle.ITALIC -> R.id.widget_date_below_italic
    }

    val (fmt12, fmt24) = when (datePosition) {
        DateDisplayPosition.OFF -> "h:mm a" to "HH:mm"
        DateDisplayPosition.LEFT_OF_TIME -> "MMM d   h:mm a" to "MMM d   HH:mm"
        DateDisplayPosition.RIGHT_OF_TIME -> "h:mm a   MMM d" to "HH:mm   MMM d"
        DateDisplayPosition.BELOW_TIME -> "h:mm a" to "HH:mm"
    }

    return RemoteViews(context.packageName, R.layout.widget_top_clock).apply {
        for (id in allClockIds) {
            setViewVisibility(id, if (id == activeClockId) View.VISIBLE else View.GONE)
        }
        setCharSequence(activeClockId, "setFormat12Hour", fmt12)
        setCharSequence(activeClockId, "setFormat24Hour", fmt24)
        setTextViewTextSize(activeClockId, TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
        setTextColor(activeClockId, safeColorArgb)

        if (datePosition == DateDisplayPosition.BELOW_TIME) {
            setViewVisibility(R.id.widget_date_below_container, View.VISIBLE)
            for (id in allBelowDateIds) {
                setViewVisibility(id, if (id == activeBelowDateId) View.VISIBLE else View.GONE)
            }
            setTextViewTextSize(activeBelowDateId, TypedValue.COMPLEX_UNIT_SP, belowDateFontSizeSp)
            setTextColor(activeBelowDateId, safeColorArgb)
        } else {
            setViewVisibility(R.id.widget_date_below_container, View.GONE)
        }
    }
}

private const val SWIPE_LIST_SCALE = 0.025f

@RequiresApi(Build.VERSION_CODES.S)
private fun createSwipeStripShellRemoteViews(
    context: Context,
    stripWidthDp: Float,
    stripHeightDp: Float
): RemoteViews {
    val invScale = 1f / SWIPE_LIST_SCALE
    return RemoteViews(context.packageName, R.layout.widget_ten_day_strip).apply {
        setViewLayoutWidth(R.id.widget_ten_day_list, stripHeightDp * invScale, TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutHeight(R.id.widget_ten_day_list, stripWidthDp * invScale, TypedValue.COMPLEX_UNIT_DIP)
    }
}

private fun autoFitWidgetLineSp(
    context: Context,
    text: String,
    availWidthDp: Float,
    desiredPhysicalSp: Float,
    minVirtualSp: Float,
    maxVirtualSp: Float,
    hostResizeRatio: Float,
    fontScale: Float
): Float {
    val safeMax = maxVirtualSp.coerceAtLeast(minVirtualSp)
    val safeRatio = hostResizeRatio.safeClamp(0.65f, 1.25f)
    val targetVirtual = (desiredPhysicalSp / safeRatio).safeClamp(minVirtualSp, safeMax)
    if (text.isEmpty()) return targetVirtual
    val safeAvailW = availWidthDp.coerceAtLeast(24f)
    val measuredW = WidgetBitmapRenderer.measureTightBoldTextWidthDp(
        context = context,
        text = text,
        virtualFontSizeSp = targetVirtual,
        fontScale = fontScale
    )
    if (measuredW <= safeAvailW) return targetVirtual
    val scaled = targetVirtual * (safeAvailW / measuredW.coerceAtLeast(1f))
    return scaled.safeClamp(minVirtualSp, safeMax)
}

internal fun resolveWidgetSliderBackingColor(
    tone: BackingTone,
    transparencyDeltaPct: Int
): Color {
    val safeDelta = transparencyDeltaPct.safeClamp(
        UserPreferencesRepository.MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT,
        UserPreferencesRepository.MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT
    )
    return when (tone) {
        BackingTone.DEFAULT -> {
            val alpha = (0.34f - (safeDelta / 100f)).safeClamp(0.0f, 0.94f)
            Color(0xFF0B172A).copy(alpha = alpha)
        }
        BackingTone.DARKEN -> {
            val alpha = (0.56f - (safeDelta / 100f)).safeClamp(0.06f, 0.96f)
            Color(0xFF030712).copy(alpha = alpha)
        }
        BackingTone.LIGHTEN -> {
            val alpha = (0.34f - (safeDelta / 100f)).safeClamp(0.04f, 0.92f)
            Color(0xFFEFF6FF).copy(alpha = alpha)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun attachSwipeableTenDayAdapterToRoot(
    context: Context,
    rootRv: RemoteViews,
    days: List<WidgetDayUi>,
    stripWidthDp: Float,
    stripHeightDp: Float,
    dataFontDeltaSp: Float,
    primaryTextArgb: Int,
    rainTextArgb: Int,
    hostResizeRatio: Float,
    fontScale: Float,
    isTall: Boolean,
    scrollOffset: Int,
    launchIntent: Intent
) {
    val invScale = 1f / SWIPE_LIST_SCALE
    val visibleSlotCount = minOf(5, days.size).coerceAtLeast(1)
    val defaultColWidthDp = (stripWidthDp / visibleSlotCount).coerceAtLeast(42f)
    val rawIconDp = if (isTall) 20f else 17f
    val widestDate = days.maxByOrNull { it.dateDayLabel.length }?.dateDayLabel ?: "12/28 Wed"
    val widestHiLo = days.maxByOrNull { it.highLowLabel.length }?.highLowLabel ?: "99° / 99°"

    val safeRatio = hostResizeRatio.safeClamp(0.65f, 1.25f)
    val desiredDatePhysicalSp = (if (isTall) 11.4f else 10.6f) + dataFontDeltaSp
    val desiredHiLoPhysicalSp = (if (isTall) 12.0f else 11.2f) + dataFontDeltaSp
    val desiredRainPhysicalSp = (if (isTall) 11.2f else 10.2f) + dataFontDeltaSp * 0.85f

    // Dynamically widen swipeable day columns with generous inter-column padding when font size is boosted
    // so +1sp..+5sp grows visibly without '...' truncation or adjacent high/low labels touching
    val targetVirtualDateSp = (desiredDatePhysicalSp / safeRatio).safeClamp(9.4f, 22.0f)
    val targetVirtualHiLoSp = (desiredHiLoPhysicalSp / safeRatio).safeClamp(9.8f, 22.5f)
    val neededDateW = (WidgetBitmapRenderer.measureTightBoldTextWidthDp(
        context = context,
        text = widestDate,
        virtualFontSizeSp = targetVirtualDateSp,
        fontScale = fontScale
    ) * 1.12f) + 12f
    val neededHiLoW = (WidgetBitmapRenderer.measureTightBoldTextWidthDp(
        context = context,
        text = widestHiLo,
        virtualFontSizeSp = targetVirtualHiLoSp,
        fontScale = fontScale
    ) * 1.12f) + 12f
    val maxSingleColWidthDp = (stripWidthDp / 2.5f).coerceAtLeast(defaultColWidthDp)
    val colWidthDp = if (dataFontDeltaSp > 0f) {
        maxOf(defaultColWidthDp, neededDateW, neededHiLoW).safeClamp(defaultColWidthDp, maxSingleColWidthDp)
    } else {
        defaultColWidthDp
    }

    val safeTextFitColW = (colWidthDp - if (dataFontDeltaSp > 0f) 9f else 5f).coerceAtLeast(32f)
    var baseDateSp = autoFitWidgetLineSp(
        context = context,
        text = widestDate,
        availWidthDp = safeTextFitColW,
        desiredPhysicalSp = desiredDatePhysicalSp,
        minVirtualSp = 9.4f,
        maxVirtualSp = (if (isTall) 14.0f else 12.8f) + (dataFontDeltaSp / safeRatio),
        hostResizeRatio = hostResizeRatio,
        fontScale = fontScale
    )
    var baseHiLoSp = autoFitWidgetLineSp(
        context = context,
        text = widestHiLo,
        availWidthDp = safeTextFitColW,
        desiredPhysicalSp = desiredHiLoPhysicalSp,
        minVirtualSp = 9.8f,
        maxVirtualSp = (if (isTall) 14.6f else 13.4f) + (dataFontDeltaSp / safeRatio),
        hostResizeRatio = hostResizeRatio,
        fontScale = fontScale
    )
    val baseRainPctSp = autoFitWidgetLineSp(
        context = context,
        text = "100%",
        availWidthDp = (colWidthDp - rawIconDp - 2.5f).coerceAtLeast(22f),
        desiredPhysicalSp = desiredRainPhysicalSp,
        minVirtualSp = 9.0f,
        maxVirtualSp = (if (isTall) 13.8f else 12.4f) + (dataFontDeltaSp / safeRatio),
        hostResizeRatio = hostResizeRatio,
        fontScale = fontScale
    )

    // Guarantee vertical height safety across all OS fontScale settings (0.85x..1.45x)
    val availTextVerticalDp = (stripHeightDp - rawIconDp - 4f).coerceAtLeast(22f)
    val maxCombinedSp = (availTextVerticalDp / (fontScale * 1.18f)).coerceAtLeast(18f)
    val combinedSp = baseDateSp + baseHiLoSp
    if (combinedSp > maxCombinedSp) {
        val vScale = (maxCombinedSp / combinedSp).safeClamp(0.65f, 1f)
        baseDateSp = (baseDateSp * vScale).coerceAtLeast(8.8f)
        baseHiLoSp = (baseHiLoSp * vScale).coerceAtLeast(9.2f)
    }

    val dateSp = baseDateSp * invScale
    val rainPctSp = minOf(baseRainPctSp, baseHiLoSp) * invScale
    val hiLoSp = baseHiLoSp * invScale
    val iconSizeDp = rawIconDp * invScale
    val scaledColW = colWidthDp * invScale
    val scaledStripH = stripHeightDp * invScale

    val builder = RemoteViews.RemoteCollectionItems.Builder()
        .setHasStableIds(true)
        .setViewTypeCount(1)

    days.forEachIndexed { idx, day ->
        val itemRv = RemoteViews(context.packageName, R.layout.widget_ten_day_item).apply {
            setViewLayoutWidth(R.id.day_item_root, scaledStripH, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(R.id.day_item_root, scaledColW, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutWidth(R.id.day_item_upright, scaledColW, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(R.id.day_item_upright, scaledStripH, TypedValue.COMPLEX_UNIT_DIP)

            setTextViewText(R.id.day_item_date, day.dateDayLabel)
            setTextViewTextSize(R.id.day_item_date, TypedValue.COMPLEX_UNIT_SP, dateSp)
            setTextColor(R.id.day_item_date, primaryTextArgb)

            setImageViewBitmap(R.id.day_item_icon, day.glyph)
            setViewLayoutWidth(R.id.day_item_icon, iconSizeDp, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(R.id.day_item_icon, iconSizeDp, TypedValue.COMPLEX_UNIT_DIP)

            if (day.showRainPercentBesideCloud) {
                setViewVisibility(R.id.day_item_rain_pct, View.VISIBLE)
                setTextViewText(R.id.day_item_rain_pct, "${day.precipPercent}%")
                setTextViewTextSize(R.id.day_item_rain_pct, TypedValue.COMPLEX_UNIT_SP, rainPctSp)
                setTextColor(R.id.day_item_rain_pct, rainTextArgb)
            } else {
                setViewVisibility(R.id.day_item_rain_pct, View.GONE)
            }

            setTextViewText(R.id.day_item_hilo, day.highLowLabel)
            setTextViewTextSize(R.id.day_item_hilo, TypedValue.COMPLEX_UNIT_SP, hiLoSp)
            setTextColor(R.id.day_item_hilo, primaryTextArgb)
        }
        builder.addItem(idx.toLong(), itemRv)
    }

    val targetAdapterPosition = if (scrollOffset <= 0) {
        0
    } else {
        (scrollOffset + visibleSlotCount - 1).safeClamp(0, (days.size - 1).coerceAtLeast(0))
    }
    rootRv.setRemoteAdapter(R.id.widget_ten_day_list, builder.build())
    rootRv.setScrollPosition(R.id.widget_ten_day_list, targetAdapterPosition)
}

internal fun buildWidgetDataModel(
    context: Context,
    resolved: Pair<SavedLocation, WeatherSnapshot?>?,
    tempUnit: TempUnit,
    windUnit: WindUnit
): WidgetDataModel {
    val moonPhase = LunarPhaseCalculator.calculate()

    if (resolved == null) {
        return WidgetDataModel(
            locationId = -1L,
            locationName = "SkyPulse",
            currentTemp = "--°",
            highLowCompact = "--° / --°",
            highLowLabeled = "H: --° / L: --°",
            conditionOnly = "Tap to set up",
            feelsOnly = "Feels --°",
            humidityLine = "Humidity --%",
            subtitleSmall = "Tap to add a ZIP or GPS location",
            highLowSkyFeelsWide = "H: --° / L: --°   ·   Tap to add a ZIP or GPS location",
            highLowSkyFeelsCompact = "H: --° / L: --° · Tap to set up",
            windAndUvLine = "",
            updatedLabel = "",
            alertSummary = null,
            condition = WeatherCondition.PARTLY_CLOUDY_DAY,
            isDay = true,
            cloudCoverPercent = 35,
            hasTornadoWarning = false,
            moonPhase = moonPhase,
            fetchedAtMillis = 0L,
            days = emptyList()
        )
    }

    val (location, snapshot) = resolved
    if (snapshot == null) {
        return WidgetDataModel(
            locationId = location.id,
            locationName = location.name,
            currentTemp = "--°",
            highLowCompact = "--° / --°",
            highLowLabeled = "H: --° / L: --°",
            conditionOnly = "Syncing…",
            feelsOnly = "Feels --°",
            humidityLine = "Humidity --%",
            subtitleSmall = "Tap to load forecast",
            highLowSkyFeelsWide = "H: --° / L: --°   ·   Syncing forecast…",
            highLowSkyFeelsCompact = "H: --° / L: --° · Syncing…",
            windAndUvLine = "",
            updatedLabel = "Offline",
            alertSummary = null,
            condition = WeatherCondition.PARTLY_CLOUDY_DAY,
            isDay = true,
            cloudCoverPercent = 35,
            hasTornadoWarning = false,
            moonPhase = moonPhase,
            fetchedAtMillis = 0L,
            days = emptyList()
        )
    }

    val curStr = tempUnit.format(snapshot.currentTempCelsius)
    val feelsStr = tempUnit.format(snapshot.feelsLikeCelsius)
    val highStr = tempUnit.format(snapshot.highCelsius)
    val lowStr = tempUnit.format(snapshot.lowCelsius)
    val safeHumidity = snapshot.humidityPercent.safeClamp(0, 100)
    val windUv = snapshot.formatWindAndUvLine(windUnit = windUnit, compact = true)

    val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
    val updatedTime = timeFmt.format(Date(snapshot.fetchedAtMillis))

    val glyphCache = mutableMapOf<WeatherCondition, Bitmap>()
    val dayItems = snapshot.daily.take(10).map { d ->
        val glyph = glyphCache.getOrPut(d.condition) {
            WidgetBitmapRenderer.renderMiniGlyph(context, d.condition, 72)
        }
        val hiVal = tempUnit.convert(d.highCelsius)
        val loVal = tempUnit.convert(d.lowCelsius)
        WidgetDayUi(
            dateDayLabel = d.monthDayWeekLabel,
            glyph = glyph,
            highLowLabel = "${hiVal}° / ${loVal}°",
            precipPercent = d.precipProbability.safeClamp(0, 100),
            showRainPercentBesideCloud = d.showRainPercentBesideCloud
        )
    }

    val firstAlert = snapshot.alerts.firstOrNull()?.let { alert ->
        "⚠ ${alert.event}"
    }

    val hlLabeled = "H: $highStr / L: $lowStr"
    return WidgetDataModel(
        locationId = location.id,
        locationName = location.name,
        currentTemp = curStr,
        highLowCompact = "$highStr / $lowStr",
        highLowLabeled = hlLabeled,
        conditionOnly = snapshot.conditionLabel,
        feelsOnly = "Feels $feelsStr",
        humidityLine = "Humidity $safeHumidity%",
        subtitleSmall = "${snapshot.conditionLabel} · Feels $feelsStr",
        highLowSkyFeelsWide = "$hlLabeled   ·   ${snapshot.conditionLabel}   ·   Feels $feelsStr",
        highLowSkyFeelsCompact = "$hlLabeled  ·  ${snapshot.conditionLabel}  ·  Feels $feelsStr",
        windAndUvLine = windUv,
        updatedLabel = "$updatedTime ↻",
        alertSummary = firstAlert,
        condition = snapshot.condition,
        isDay = snapshot.isDay,
        cloudCoverPercent = snapshot.effectiveCloudCover,
        hasTornadoWarning = snapshot.hasTornadoWarning,
        moonPhase = moonPhase,
        fetchedAtMillis = snapshot.fetchedAtMillis,
        days = dayItems
    )
}

private fun createRefreshWidgetBroadcastIntent(context: Context, locationId: Long): Intent {
    return Intent(context, WeatherGlanceReceiver::class.java).apply {
        action = WeatherWidgetPublisher.ACTION_REFRESH_WIDGET
        data = Uri.parse("skypulse://widget/refresh/$locationId")
        putExtra(WeatherWidgetPublisher.EXTRA_LOCATION_ID, locationId)
    }
}

private fun createScrollForecastBroadcastIntent(context: Context, targetOffset: Int): Intent {
    return Intent(context, WeatherGlanceReceiver::class.java).apply {
        action = WeatherWidgetPublisher.ACTION_SCROLL_FORECAST
        data = Uri.parse("skypulse://widget/scroll/$targetOffset")
        putExtra(WeatherWidgetPublisher.EXTRA_TARGET_OFFSET, targetOffset)
    }
}

@Composable
private fun WidgetCardSurface(
    context: Context,
    model: WidgetDataModel,
    prefs: AppPreferences,
    sceneBackdrop: Bitmap,
    cloudMotionRemoteViews: RemoteViews?,
    topClockRemoteViews: RemoteViews?,
    topClockHeightDp: androidx.compose.ui.unit.Dp,
    topReservedFraction: Float,
    maxMoonBottomFraction: Float,
    swipeStripShellRemoteViews: RemoteViews?,
    dayOffset: Int,
    widthDp: Float,
    heightDp: Float,
    hostResizeRatio: Float,
    fontScale: Float,
    launchIntent: Intent
) {
    val primaryColor = ColorProvider(Color(0xFFF8FAFC))
    val humidityColor = ColorProvider(Color(0xFFBAE6FD))
    val windUvColor = ColorProvider(Color(0xFFE2E8F0))
    val secondaryColor = ColorProvider(Color(0xFFDCE7F5))
    val dividerColor = ColorProvider(Color(0x42FFFFFF))
    val pillBgColor = ColorProvider(Color(0x520F172A))
    val rainTint = ColorProvider(Color(0xFF7DD3FC))
    val alertTint = ColorProvider(Color(0xFFFDE68A))

    val isMini1x1 = widthDp < 108f
    val isStrip2x1 = widthDp >= 108f && heightDp < 108f
    val isSquare2x2 = widthDp in 108f..212f && heightDp >= 108f
    val cornerRad = if (isMini1x1 || isStrip2x1) 18.dp else 24.dp

    val tempScaleRatio = prefs.widgetTempFontSizeSp.toFloat() /
        UserPreferencesRepository.DEFAULT_WIDGET_TEMP_FONT_SP.toFloat()
    val dataFontDelta = prefs.widgetDataFontDeltaSp.safeClamp(
        UserPreferencesRepository.MIN_WIDGET_DATA_FONT_DELTA_SP,
        UserPreferencesRepository.MAX_WIDGET_DATA_FONT_DELTA_SP
    ).toFloat()
    val safeHostRatio = hostResizeRatio.safeClamp(0.65f, 1.25f)
    val virtualDeltaCapBoost = dataFontDelta / safeHostRatio
    val sliderBackingColor = resolveWidgetSliderBackingColor(
        tone = prefs.widgetSliderBackingTone,
        transparencyDeltaPct = prefs.widgetSliderTransparencyDeltaPct
    )
    val isLightOpaqueSlider = prefs.widgetSliderBackingTone == BackingTone.LIGHTEN &&
        sliderBackingColor.alpha >= 0.45f
    val sliderPrimaryColor = ColorProvider(
        if (isLightOpaqueSlider) Color(0xFF0B1324) else Color(0xFFF8FAFC)
    )
    val sliderSecondaryColor = ColorProvider(
        if (isLightOpaqueSlider) Color(0xFF1E293B) else Color(0xFFDCE7F5)
    )
    val sliderRainTint = ColorProvider(
        if (isLightOpaqueSlider) Color(0xFF0369A1) else Color(0xFF7DD3FC)
    )
    val sliderDividerColor = ColorProvider(
        if (isLightOpaqueSlider) Color(0x380F172A) else Color(0x42FFFFFF)
    )

    val rootBoxModifier = if (swipeStripShellRemoteViews != null) {
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(cornerRad)
    } else {
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(cornerRad)
            .clickable(actionStartActivity(launchIntent))
    }

    Box(
        modifier = rootBoxModifier
    ) {
        Image(
            provider = ImageProvider(sceneBackdrop),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize()
        )

        if (cloudMotionRemoteViews != null) {
            AndroidRemoteViews(
                remoteViews = cloudMotionRemoteViews,
                modifier = GlanceModifier.fillMaxSize()
            )
        }

        when {
            // 1. 1×1 Mini Tile Layout: Optional Top Time + Styled Temp + City/H-L
            isMini1x1 -> {
                val maxMiniSp = ((minOf(widthDp, heightDp) * 0.34f) / fontScale).safeClamp(15f, 26f)
                val miniTempSp = (maxMiniSp * tempScaleRatio.safeClamp(0.75f, 1.25f)).safeClamp(14f, maxMiniSp * 1.15f)
                val miniClockUsedH = if (topClockRemoteViews != null) topClockHeightDp.value else 13f
                val miniHlUsedH = if (heightDp >= 64f) 13f else 0f
                val miniAvailH = (heightDp - 10f - miniClockUsedH - miniHlUsedH).coerceAtLeast(16f)
                val miniCityShort = model.locationName.substringBefore(",")
                val miniCitySp = ((widthDp - 10f) / ((miniCityShort.length * 0.60f + 1.4f) * fontScale))
                    .safeClamp(7.6f, 9.8f).sp
                val miniTempBmp = WidgetBitmapRenderer.renderStyledTemperatureBitmap(
                    context = context,
                    tempText = model.currentTemp,
                    requestedRealSp = miniTempSp * hostResizeRatio,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale,
                    maxWidthDp = (widthDp - 10f).coerceAtLeast(28f),
                    maxHeightDp = miniAvailH,
                    fontStyle = prefs.widgetTempFontStyle,
                    textColorArgb = prefs.widgetTempTextColorArgb
                )
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.Top
                ) {
                    if (topClockRemoteViews != null) {
                        AndroidRemoteViews(
                            remoteViews = topClockRemoteViews,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(topClockHeightDp)
                        )
                    } else {
                        Text(
                            text = miniCityShort,
                            maxLines = 1,
                            style = TextStyle(
                                color = secondaryColor,
                                fontSize = miniCitySp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Image(
                        provider = ImageProvider(miniTempBmp.bitmap),
                        contentDescription = model.currentTemp,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier
                            .width(miniTempBmp.widthDp.dp)
                            .height(miniTempBmp.heightDp.dp)
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (heightDp >= 64f) {
                        val miniHlSp = autoFitWidgetLineSp(
                            context = context,
                            text = model.highLowCompact,
                            availWidthDp = (widthDp - 10f).coerceAtLeast(32f),
                            desiredPhysicalSp = 10.8f + dataFontDelta * 0.75f,
                            minVirtualSp = 9.8f,
                            maxVirtualSp = 13.4f + virtualDeltaCapBoost * 0.75f,
                            hostResizeRatio = hostResizeRatio,
                            fontScale = fontScale
                        ).sp
                        Text(
                            text = model.highLowCompact,
                            maxLines = 1,
                            style = TextStyle(
                                color = humidityColor,
                                fontSize = miniHlSp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }

            // 2. 2×1 Horizontal Strip Layout
            isStrip2x1 -> {
                val maxStripSp = ((heightDp * 0.40f) / fontScale).safeClamp(18f, 36f)
                val stripTempSp = (maxStripSp * tempScaleRatio.safeClamp(0.75f, 1.30f)).safeClamp(16f, maxStripSp * 1.20f)
                val stripClockUsedH = if (topClockRemoteViews != null) topClockHeightDp.value else 0f
                val stripAvailH = (heightDp - 10f - stripClockUsedH).coerceAtLeast(18f)
                val stripTempBmp = WidgetBitmapRenderer.renderStyledTemperatureBitmap(
                    context = context,
                    tempText = model.currentTemp,
                    requestedRealSp = stripTempSp * hostResizeRatio,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale,
                    maxWidthDp = (widthDp * 0.40f).coerceAtLeast(42f),
                    maxHeightDp = stripAvailH,
                    fontStyle = prefs.widgetTempFontStyle,
                    textColorArgb = prefs.widgetTempTextColorArgb
                )
                val stripAvailCityW = (widthDp - stripTempBmp.widthDp - 26f).coerceAtLeast(56f)
                val stripCitySp = autoFitWidgetLineSp(
                    context = context,
                    text = model.locationName,
                    availWidthDp = stripAvailCityW,
                    desiredPhysicalSp = 12.2f,
                    minVirtualSp = 10.4f,
                    maxVirtualSp = 15.2f,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                ).sp
                val fullStripSub = "${model.highLowLabeled} · ${model.conditionOnly}"
                val fullStripSubW = WidgetBitmapRenderer.measureTightBoldTextWidthDp(
                    context = context,
                    text = fullStripSub,
                    virtualFontSizeSp = (11.2f / hostResizeRatio).safeClamp(11.0f, 14.0f),
                    fontScale = fontScale
                )
                val chosenStripSub = if (fullStripSubW <= stripAvailCityW) {
                    fullStripSub
                } else {
                    model.highLowLabeled
                }
                val stripSubSp = autoFitWidgetLineSp(
                    context = context,
                    text = chosenStripSub,
                    availWidthDp = stripAvailCityW,
                    desiredPhysicalSp = 11.4f + dataFontDelta,
                    minVirtualSp = 10.4f,
                    maxVirtualSp = 14.4f + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                ).sp
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (topClockRemoteViews != null) {
                        AndroidRemoteViews(
                            remoteViews = topClockRemoteViews,
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(topClockHeightDp)
                        )
                    }
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = model.locationName,
                                maxLines = 1,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = primaryColor,
                                    fontSize = stripCitySp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = chosenStripSub,
                                maxLines = 1,
                                style = TextStyle(
                                    color = humidityColor,
                                    fontSize = stripSubSp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Image(
                            provider = ImageProvider(stripTempBmp.bitmap),
                            contentDescription = model.currentTemp,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier
                                .width(stripTempBmp.widthDp.dp)
                                .height(stripTempBmp.heightDp.dp)
                        )
                    }
                }
            }

            // 3. 2×2 Square Card Layout: Optional Top Centered Time -> Top-Left City & Centered Temp -> Bottom H/L/Feels + Sky/Humidity + Wind/UV
            isSquare2x2 -> {
                val sqClockUsedH = if (topClockRemoteViews != null) topClockHeightDp.value + 2f else 0f
                val sqFullRowW = (widthDp - 20f).coerceAtLeast(88f)
                val sqShowWind = heightDp >= 152f && model.windAndUvLine.isNotEmpty()
                val sqHlLineText = if (model.locationId == -1L || model.days.isEmpty()) {
                    model.highLowSkyFeelsCompact
                } else {
                    "${model.highLowLabeled} · ${model.feelsOnly}"
                }
                val sqCondHumText = if (model.locationId == -1L || model.days.isEmpty()) {
                    model.humidityLine
                } else {
                    "${model.conditionOnly} · ${model.humidityLine}"
                }
                val sqHlSp = autoFitWidgetLineSp(
                    context = context,
                    text = sqHlLineText,
                    availWidthDp = sqFullRowW,
                    desiredPhysicalSp = 11.8f + dataFontDelta,
                    minVirtualSp = 10.6f,
                    maxVirtualSp = 14.8f + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )
                val sqCondHumSp = autoFitWidgetLineSp(
                    context = context,
                    text = sqCondHumText,
                    availWidthDp = sqFullRowW,
                    desiredPhysicalSp = 11.4f + dataFontDelta,
                    minVirtualSp = 10.2f,
                    maxVirtualSp = 14.2f + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )
                val sqWindSp = autoFitWidgetLineSp(
                    context = context,
                    text = model.windAndUvLine,
                    availWidthDp = sqFullRowW,
                    desiredPhysicalSp = 11.4f + dataFontDelta,
                    minVirtualSp = 10.2f,
                    maxVirtualSp = 14.2f + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )
                val sqTelemetryH = ((sqHlSp + sqCondHumSp + (if (sqShowWind) sqWindSp else 0f)) * fontScale * 1.24f) + 4f
                val sqAvailH = (heightDp - 12f - sqClockUsedH - 18f - sqTelemetryH - 4f).coerceAtLeast(22f)
                val sqAvailCityRowW = (widthDp - 42f).coerceAtLeast(72f)
                val sqMaxCitySp = (13.0f / hostResizeRatio).safeClamp(11.5f, 16.0f)
                val sqCitySpFloat = (sqAvailCityRowW / ((model.locationName.length * 0.62f + 1.6f) * fontScale))
                    .safeClamp(10.0f, sqMaxCitySp)
                val sqAvailLeftW = (widthDp * 0.64f).coerceAtLeast(64f)
                val sqTempBmp = WidgetBitmapRenderer.renderStyledTemperatureBitmap(
                    context = context,
                    tempText = model.currentTemp,
                    requestedRealSp = prefs.widgetTempFontSizeSp.toFloat() * 0.80f,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale,
                    maxWidthDp = sqAvailLeftW,
                    maxHeightDp = sqAvailH,
                    fontStyle = prefs.widgetTempFontStyle,
                    textColorArgb = prefs.widgetTempTextColorArgb
                )
                val sqCityWidthDp = WidgetBitmapRenderer.measureWidgetTextWidthDp(
                    context = context,
                    text = model.locationName,
                    virtualFontSizeSp = sqCitySpFloat,
                    fontScale = fontScale
                ).coerceAtMost(sqAvailCityRowW)
                val sqOpticalLeadDp = (sqTempBmp.heightDp * 0.10f).safeClamp(0f, 8f)
                val sqTempVisualW = sqTempBmp.widthDp + sqOpticalLeadDp
                val sqCityLeadDp = ((sqTempVisualW - sqCityWidthDp) * 0.5f).coerceAtLeast(0f)
                val sqTempLeadDp = if (sqCityWidthDp >= sqTempVisualW) {
                    ((sqCityWidthDp - sqTempBmp.widthDp) * 0.5f + sqOpticalLeadDp * 0.5f).coerceAtLeast(0f)
                } else {
                    sqOpticalLeadDp
                }

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    if (topClockRemoteViews != null) {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            AndroidRemoteViews(
                                remoteViews = topClockRemoteViews,
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(topClockHeightDp)
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                        }
                    }
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sqCityLeadDp > 0.5f) {
                            Spacer(modifier = GlanceModifier.width(sqCityLeadDp.dp))
                        }
                        Text(
                            text = model.locationName,
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = primaryColor,
                                fontSize = sqCitySpFloat.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            )
                        )
                        Text(
                            text = "↻",
                            maxLines = 1,
                            modifier = GlanceModifier
                                .clickable(
                                    actionSendBroadcast(
                                        createRefreshWidgetBroadcastIntent(context, model.locationId)
                                    )
                                )
                                .padding(horizontal = 3.dp),
                            style = TextStyle(
                                color = rainTint,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sqTempLeadDp > 0.5f) {
                            Spacer(modifier = GlanceModifier.width(sqTempLeadDp.dp))
                        }
                        Image(
                            provider = ImageProvider(sqTempBmp.bitmap),
                            contentDescription = model.currentTemp,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier
                                .width(sqTempBmp.widthDp.dp)
                                .height(sqTempBmp.heightDp.dp)
                        )
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = sqHlLineText,
                            maxLines = 1,
                            style = TextStyle(
                                color = primaryColor,
                                fontSize = sqHlSp.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = sqCondHumText,
                            maxLines = 1,
                            style = TextStyle(
                                color = humidityColor,
                                fontSize = sqCondHumSp.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (sqShowWind) {
                            Text(
                                text = model.windAndUvLine,
                                maxLines = 1,
                                style = TextStyle(
                                    color = windUvColor,
                                    fontSize = sqWindSp.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // 4. 5×2 / 4×2 / 4×3 Full Forecast Card with Horizontally Swipeable 10-Day Forecast Strip
            else -> {
                val showTenDayStrip = heightDp >= 145f && model.days.isNotEmpty()
                val isTall = heightDp >= 225f
                val isCompactHeight = !isTall
                val topPadFloat = if (isCompactHeight) 5f else 6f
                val clockOverhead = if (topClockRemoteViews != null) {
                    topClockHeightDp.value + 2f
                } else {
                    0f
                }
                val effectiveH = (heightDp - clockOverhead).coerceAtLeast(110f)

                // Exact Moon horizontal center `(moonX)` so Moon Phase centers directly under the Moon
                val (moonX, _, _) = WidgetBitmapRenderer.computeMoonStageGeometry(
                    w = widthDp,
                    h = heightDp,
                    topReservedFraction = topReservedFraction,
                    maxStageBottomFraction = maxMoonBottomFraction
                )
                val innerRightEdgeX = (widthDp - 10f).coerceAtLeast(60f)
                val halfSpanToRightEdge = (innerRightEdgeX - moonX).coerceAtLeast(46f)
                val rightSpaceFloat = (halfSpanToRightEdge * 2f)
                    .safeClamp(92f, (widthDp * 0.45f).coerceAtLeast(92f))
                val rightCelestialColDp = rightSpaceFloat.dp

                val availLeftWidthDp = (widthDp - rightSpaceFloat - 22f).coerceAtLeast(80f)
                val hasNightMoonStage = !model.isDay && !model.hasTornadoWarning && model.highLowLabeled.isNotEmpty()
                val isSunnyDayStage = model.isDay && model.condition == WeatherCondition.CLEAR_DAY && !model.hasTornadoWarning
                val availCityRowWidthDp = when {
                    hasNightMoonStage -> (availLeftWidthDp - 24f).coerceAtLeast(110f)
                    isSunnyDayStage -> (widthDp * 0.66f).coerceAtLeast(130f)
                    else -> (widthDp - 48f).coerceAtLeast(140f)
                }
                val maxCitySpCap = (16.5f / hostResizeRatio).safeClamp(14.5f, 20.0f)
                val locNameSpFloat = (availCityRowWidthDp / ((model.locationName.length * 0.62f + 1.8f) * fontScale))
                    .safeClamp(11.0f, maxCitySpCap)
                val locNameSp = locNameSpFloat.sp

                val stripExtraBoostDp = (dataFontDelta * 2.6f * fontScale) / safeHostRatio
                val stripHeightFloat = if (isTall) {
                    ((68f / hostResizeRatio).safeClamp(68f, 78f) + stripExtraBoostDp)
                        .safeClamp(68f, (heightDp * 0.34f).coerceAtLeast(78f))
                } else {
                    ((56f / hostResizeRatio).safeClamp(56f, 64f) + stripExtraBoostDp)
                        .safeClamp(56f, (heightDp * 0.40f).coerceAtLeast(64f))
                }
                val stripSectionOverhead = if (showTenDayStrip) {
                    stripHeightFloat + (if (isTall && model.days.size > 5) 28f else 10f)
                } else {
                    0f
                }
                val alertOverhead = if (isTall && model.alertSummary != null) {
                    28f + dataFontDelta * 0.9f
                } else {
                    0f
                }
                val showWindLine = model.windAndUvLine.isNotEmpty() && (isTall || effectiveH >= 152f)
                val stripWidthFloat = (widthDp - 20f).coerceAtLeast(150f)
                val hasMoonPhaseCol = hasNightMoonStage
                val telemetryWidthFloat = if (hasMoonPhaseCol) {
                    availLeftWidthDp
                } else {
                    stripWidthFloat
                }

                // Balanced 3-line telemetry so H:/L: and Wind render large & bold without wrapping
                val hlPrimaryLineText = if (model.locationId == -1L || model.days.isEmpty()) {
                    model.highLowSkyFeelsCompact
                } else {
                    "${model.highLowLabeled} · ${model.feelsOnly}"
                }
                val condHumidityLineText = if (model.locationId == -1L || model.days.isEmpty()) {
                    model.humidityLine
                } else {
                    "${model.conditionOnly} · ${model.humidityLine}"
                }
                val safeTelemetryAvailW = (telemetryWidthFloat - 6f).coerceAtLeast(72f)
                val hlPrimarySpFloat = autoFitWidgetLineSp(
                    context = context,
                    text = hlPrimaryLineText,
                    availWidthDp = safeTelemetryAvailW,
                    desiredPhysicalSp = (if (isTall) 13.4f else 12.2f) + dataFontDelta,
                    minVirtualSp = 10.4f,
                    maxVirtualSp = (if (isTall) 16.8f else 15.2f) + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )
                val condHumiditySpFloat = autoFitWidgetLineSp(
                    context = context,
                    text = condHumidityLineText,
                    availWidthDp = safeTelemetryAvailW,
                    desiredPhysicalSp = (if (isTall) 12.8f else 11.6f) + dataFontDelta,
                    minVirtualSp = 9.8f,
                    maxVirtualSp = (if (isTall) 15.8f else 14.4f) + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )
                val windUvSpFloat = autoFitWidgetLineSp(
                    context = context,
                    text = model.windAndUvLine,
                    availWidthDp = safeTelemetryAvailW,
                    desiredPhysicalSp = (if (isTall) 12.8f else 11.8f) + dataFontDelta,
                    minVirtualSp = 10.0f,
                    maxVirtualSp = (if (isTall) 15.8f else 14.6f) + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )

                val lowerTelemetryOverhead = ((hlPrimarySpFloat + condHumiditySpFloat +
                    (if (showWindLine) windUvSpFloat else 0f)) * fontScale * 1.38f) + 9f
                val cityHeaderOverhead = (locNameSpFloat * fontScale * 1.34f) + 6f

                val stageAvailableHeightDp = (heightDp - topPadFloat - 4f - clockOverhead -
                    stripSectionOverhead - alertOverhead - 2f)
                    .coerceAtLeast(64f)
                val maxHeroTempHeightDp = (stageAvailableHeightDp - cityHeaderOverhead - lowerTelemetryOverhead - 4f)
                    .coerceAtLeast(22f)

                val heroTempBmp = WidgetBitmapRenderer.renderStyledTemperatureBitmap(
                    context = context,
                    tempText = model.currentTemp,
                    requestedRealSp = prefs.widgetTempFontSizeSp.toFloat(),
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale,
                    maxWidthDp = (availLeftWidthDp - 8f).coerceAtLeast(52f),
                    maxHeightDp = maxHeroTempHeightDp,
                    fontStyle = prefs.widgetTempFontStyle,
                    textColorArgb = prefs.widgetTempTextColorArgb
                )

                val cityTextWidthDp = WidgetBitmapRenderer.measureWidgetTextWidthDp(
                    context = context,
                    text = model.locationName,
                    virtualFontSizeSp = locNameSpFloat,
                    fontScale = fontScale
                ).coerceAtMost(availCityRowWidthDp)
                // Optical balance for trailing superscript '°' so the digits sit visually dead-center under City, State
                val tempOpticalLeadDp = if (model.currentTemp.endsWith("°")) {
                    (heroTempBmp.heightDp * 0.11f).safeClamp(2f, 12f)
                } else {
                    0f
                }
                val tempVisualWidthDp = heroTempBmp.widthDp + tempOpticalLeadDp
                val cityLeadOffsetDp = ((tempVisualWidthDp - cityTextWidthDp) * 0.5f).coerceAtLeast(0f)
                val tempLeadOffsetDp = if (cityTextWidthDp >= tempVisualWidthDp) {
                    ((cityTextWidthDp - heroTempBmp.widthDp) * 0.5f + tempOpticalLeadDp * 0.5f).coerceAtLeast(0f)
                } else {
                    tempOpticalLeadDp
                }

                // Moon Phase centered directly under the large 3D Moon in the right-hand open space
                val moonPhaseSpFloat = autoFitWidgetLineSp(
                    context = context,
                    text = model.moonPhase.phaseName,
                    availWidthDp = (rightSpaceFloat - 4f).coerceAtLeast(72f),
                    desiredPhysicalSp = (if (isTall) 11.8f else 10.8f) + dataFontDelta * 0.75f,
                    minVirtualSp = if (isCompactHeight) 9.8f else 11.0f,
                    maxVirtualSp = (if (isTall) 13.4f else 12.2f) + virtualDeltaCapBoost * 0.75f,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                )
                val moonPhaseHeightFloat = moonPhaseSpFloat * fontScale * 1.38f + 5f
                val rawMoonBottomDp = WidgetBitmapRenderer.computeMoonBottomDp(
                    widthDp = widthDp,
                    heightDp = heightDp,
                    topReservedFraction = topReservedFraction,
                    maxStageBottomFraction = maxMoonBottomFraction
                )
                val desiredMoonPhaseTop = rawMoonBottomDp - topPadFloat - clockOverhead + 1.5f
                val moonPhaseTopSpacerDp = desiredMoonPhaseTop
                    .safeClamp(6f, (stageAvailableHeightDp - moonPhaseHeightFloat - 6f).coerceAtLeast(6f)).dp

                val moonPhaseRemoteViews = if (hasNightMoonStage) {
                    RemoteViews(context.packageName, R.layout.widget_celestial_labels).apply {
                        setViewVisibility(R.id.widget_moon_phase_label, View.VISIBLE)
                        setTextViewText(R.id.widget_moon_phase_label, model.moonPhase.phaseName)
                        setTextViewTextSize(
                            R.id.widget_moon_phase_label,
                            TypedValue.COMPLEX_UNIT_SP,
                            moonPhaseSpFloat
                        )
                        setViewVisibility(R.id.widget_celestial_hilo_label, View.GONE)
                    }
                } else {
                    null
                }

                val fallbackSlots = if (dataFontDelta >= 3f && stripWidthFloat < 340f) {
                    minOf(4, model.days.size)
                } else {
                    minOf(5, model.days.size)
                }
                val visibleCount = fallbackSlots.coerceAtLeast(1)
                val maxOffset = (model.days.size - visibleCount).coerceAtLeast(0)
                val safeOffset = dayOffset.safeClamp(0, maxOffset)
                val visibleDays = model.days.drop(safeOffset).take(visibleCount)
                val canScrollLeft = safeOffset > 0
                val canScrollRight = safeOffset < maxOffset
                val nextLeftOffset = (safeOffset - visibleCount).coerceAtLeast(0)
                val nextRightOffset = (safeOffset + visibleCount).coerceAtMost(maxOffset)

                val useNativeSwipeStrip = swipeStripShellRemoteViews != null
                val navControlWidth = if (!useNativeSwipeStrip && maxOffset > 0) 24f else 0f
                val colWidthDp = ((stripWidthFloat - navControlWidth) / visibleCount.coerceAtLeast(1))
                    .coerceAtLeast(34f)
                val widestDateLabel = visibleDays.maxByOrNull { it.dateDayLabel.length }?.dateDayLabel ?: "12/28 Wed"
                val widestHiLoLabel = visibleDays.maxByOrNull { it.highLowLabel.length }?.highLowLabel ?: "99° / 99°"
                val fallbackDateSp = autoFitWidgetLineSp(
                    context = context,
                    text = widestDateLabel,
                    availWidthDp = colWidthDp - 3f,
                    desiredPhysicalSp = (if (isTall) 11.4f else 10.6f) + dataFontDelta,
                    minVirtualSp = 9.4f,
                    maxVirtualSp = (if (isTall) 14.0f else 12.8f) + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                ).sp
                val dayHiLoSp = autoFitWidgetLineSp(
                    context = context,
                    text = widestHiLoLabel,
                    availWidthDp = colWidthDp - 3f,
                    desiredPhysicalSp = (if (isTall) 12.0f else 11.2f) + dataFontDelta,
                    minVirtualSp = 9.8f,
                    maxVirtualSp = (if (isTall) 14.6f else 13.4f) + virtualDeltaCapBoost,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                ).sp
                val dayRainPctSp = autoFitWidgetLineSp(
                    context = context,
                    text = "100%",
                    availWidthDp = (colWidthDp - (if (isTall) 20f else 17f) - 2.5f).coerceAtLeast(22f),
                    desiredPhysicalSp = (if (isTall) 11.2f else 10.2f) + dataFontDelta * 0.85f,
                    minVirtualSp = 9.0f,
                    maxVirtualSp = (if (isTall) 13.8f else 12.4f) + virtualDeltaCapBoost * 0.85f,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                ).sp
                val tenDayHeaderSp = autoFitWidgetLineSp(
                    context = context,
                    text = "10-DAY FORECAST · SWIPE ⇄   Next 5 ›",
                    availWidthDp = (stripWidthFloat - 16f).coerceAtLeast(120f),
                    desiredPhysicalSp = 11.2f + dataFontDelta * 0.65f,
                    minVirtualSp = 10.4f,
                    maxVirtualSp = 13.6f + virtualDeltaCapBoost * 0.65f,
                    hostResizeRatio = hostResizeRatio,
                    fontScale = fontScale
                ).sp

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(top = topPadFloat.dp, bottom = 4.dp)
                ) {
                    // Upper clickable weather stage (tapping opens full-screen app; bottom 10-day slider stays pure swipe)
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .padding(horizontal = 10.dp)
                            .clickable(actionStartActivity(launchIntent))
                    ) {
                        // 1. Optional Top Centered Time Display — sits strictly above City/State, Temp & Moon/Sun
                        if (topClockRemoteViews != null) {
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                AndroidRemoteViews(
                                    remoteViews = topClockRemoteViews,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(topClockHeightDp)
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                            }
                        }

                    if (moonPhaseRemoteViews != null) {
                        // Nighttime 2-column stage: Left holds unconstrained City, centered Temp & Telemetry;
                        // Right gives the 3D Moon the open starry stage with Moon Phase centered underneath it.
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (cityLeadOffsetDp > 0.5f) {
                                        Spacer(modifier = GlanceModifier.width(cityLeadOffsetDp.dp))
                                    }
                                    Text(
                                        text = model.locationName,
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight(),
                                        style = TextStyle(
                                            color = primaryColor,
                                            fontSize = locNameSp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Start
                                        )
                                    )
                                    if (model.updatedLabel.isNotEmpty()) {
                                        Text(
                                            text = "↻",
                                            maxLines = 1,
                                            modifier = GlanceModifier
                                                .clickable(
                                                    actionSendBroadcast(
                                                        createRefreshWidgetBroadcastIntent(context, model.locationId)
                                                    )
                                                )
                                                .padding(horizontal = 4.dp),
                                            style = TextStyle(
                                                color = rainTint,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (tempLeadOffsetDp > 0.5f) {
                                        Spacer(modifier = GlanceModifier.width(tempLeadOffsetDp.dp))
                                    }
                                    Image(
                                        provider = ImageProvider(heroTempBmp.bitmap),
                                        contentDescription = model.currentTemp,
                                        contentScale = ContentScale.Fit,
                                        modifier = GlanceModifier
                                            .width(heroTempBmp.widthDp.dp)
                                            .height(heroTempBmp.heightDp.dp)
                                    )
                                }

                                Spacer(modifier = GlanceModifier.defaultWeight())

                                Text(
                                    text = hlPrimaryLineText,
                                    maxLines = 1,
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    style = TextStyle(
                                        color = primaryColor,
                                        fontSize = hlPrimarySpFloat.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = condHumidityLineText,
                                    maxLines = 1,
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    style = TextStyle(
                                        color = humidityColor,
                                        fontSize = condHumiditySpFloat.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (showWindLine) {
                                    Text(
                                        text = model.windAndUvLine,
                                        maxLines = 1,
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        style = TextStyle(
                                            color = windUvColor,
                                            fontSize = windUvSpFloat.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Column(
                                modifier = GlanceModifier
                                    .width(rightCelestialColDp)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = GlanceModifier.height(moonPhaseTopSpacerDp))
                                AndroidRemoteViews(
                                    remoteViews = moonPhaseRemoteViews,
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(moonPhaseHeightFloat.dp)
                                )
                            }
                        }
                    } else {
                        // Daytime / Storm layout: Full-width unconstrained City + top-right Refresh, Centered Temp, Bottom Full-Width Telemetry
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cityLeadOffsetDp > 0.5f) {
                                    Spacer(modifier = GlanceModifier.width(cityLeadOffsetDp.dp))
                                }
                                Text(
                                    text = model.locationName,
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight(),
                                    style = TextStyle(
                                        color = primaryColor,
                                        fontSize = locNameSp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Start
                                    )
                                )
                                if (model.updatedLabel.isNotEmpty()) {
                                    Text(
                                        text = "↻",
                                        maxLines = 1,
                                        modifier = GlanceModifier
                                            .clickable(
                                                actionSendBroadcast(
                                                    createRefreshWidgetBroadcastIntent(context, model.locationId)
                                                )
                                            )
                                            .padding(horizontal = 4.dp),
                                        style = TextStyle(
                                            color = rainTint,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (tempLeadOffsetDp > 0.5f) {
                                    Spacer(modifier = GlanceModifier.width(tempLeadOffsetDp.dp))
                                }
                                Image(
                                    provider = ImageProvider(heroTempBmp.bitmap),
                                    contentDescription = model.currentTemp,
                                    contentScale = ContentScale.Fit,
                                    modifier = GlanceModifier
                                        .width(heroTempBmp.widthDp.dp)
                                        .height(heroTempBmp.heightDp.dp)
                                )
                            }
                        }

                        Column(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = hlPrimaryLineText,
                                maxLines = 1,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = primaryColor,
                                    fontSize = hlPrimarySpFloat.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = condHumidityLineText,
                                maxLines = 1,
                                style = TextStyle(
                                    color = humidityColor,
                                    fontSize = condHumiditySpFloat.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (showWindLine) {
                                Text(
                                    text = model.windAndUvLine,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = windUvColor,
                                        fontSize = windUvSpFloat.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                        if (isTall && model.alertSummary != null) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .background(ImageProvider(R.drawable.widget_alert_bg))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = model.alertSummary,
                                    maxLines = 1,
                                    style = TextStyle(
                                        color = alertTint,
                                        fontSize = (11f + dataFontDelta * 0.65f).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // 4. Bottom 10-Day Forecast Strip with customizable Backing Tone & Transparency
                    if (showTenDayStrip) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        val sliderBackingModifier = if (sliderBackingColor.alpha > 0.005f) {
                            GlanceModifier
                                .fillMaxWidth()
                                .cornerRadius(16.dp)
                                .background(ColorProvider(sliderBackingColor))
                                .padding(top = 3.dp, bottom = 2.dp)
                        } else {
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(top = 1.dp)
                        }
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Column(modifier = sliderBackingModifier) {
                                if (isTall && model.days.size > 5) {
                                    val toggleScrollAction = actionSendBroadcast(
                                        createScrollForecastBroadcastIntent(
                                            context,
                                            if (canScrollRight) nextRightOffset else nextLeftOffset
                                        )
                                    )
                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .clickable(toggleScrollAction),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (useNativeSwipeStrip) {
                                                "10-DAY FORECAST · SWIPE ⇄"
                                            } else {
                                                "10-DAY FORECAST · DAYS ${safeOffset + 1}–${safeOffset + visibleDays.size}"
                                            },
                                            maxLines = 1,
                                            modifier = GlanceModifier.defaultWeight(),
                                            style = TextStyle(
                                                color = sliderSecondaryColor,
                                                fontSize = tenDayHeaderSp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = if (canScrollRight) "Next 5 ›" else "‹ First 5",
                                            maxLines = 1,
                                            modifier = GlanceModifier
                                                .cornerRadius(8.dp)
                                                .background(pillBgColor)
                                                .clickable(toggleScrollAction)
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = TextStyle(
                                                color = ColorProvider(Color(0xFF7DD3FC)),
                                                fontSize = tenDayHeaderSp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                }

                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .height(1.dp)
                                        .background(sliderDividerColor)
                                ) {}

                                if (swipeStripShellRemoteViews != null) {
                                    AndroidRemoteViews(
                                        remoteViews = swipeStripShellRemoteViews,
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 2.dp)
                                            .height(stripHeightFloat.dp)
                                    )
                                } else {
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (canScrollLeft) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = GlanceModifier
                                                    .width(22.dp)
                                                    .height(if (isTall) 50.dp else 40.dp)
                                                    .cornerRadius(8.dp)
                                                    .background(pillBgColor)
                                                    .clickable(
                                                        actionSendBroadcast(
                                                            createScrollForecastBroadcastIntent(context, nextLeftOffset)
                                                        )
                                                    )
                                            ) {
                                                Text(
                                                    text = "‹",
                                                    maxLines = 1,
                                                    style = TextStyle(
                                                        color = ColorProvider(Color(0xFF7DD3FC)),
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.width(2.dp))
                                        }

                                        visibleDays.forEach { day ->
                                            Column(
                                                modifier = GlanceModifier.defaultWeight(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = day.dateDayLabel,
                                                    maxLines = 1,
                                                    style = TextStyle(
                                                        color = sliderPrimaryColor,
                                                        fontSize = fallbackDateSp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                                Spacer(modifier = GlanceModifier.height(if (isCompactHeight) 1.dp else 2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Image(
                                                        provider = ImageProvider(day.glyph),
                                                        contentDescription = day.dateDayLabel,
                                                        modifier = GlanceModifier.size(if (isTall) 20.dp else 17.dp)
                                                    )
                                                    if (day.showRainPercentBesideCloud) {
                                                        Spacer(modifier = GlanceModifier.width(2.dp))
                                                        Text(
                                                            text = "${day.precipPercent}%",
                                                            maxLines = 1,
                                                            style = TextStyle(
                                                                color = sliderRainTint,
                                                                fontSize = dayRainPctSp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = GlanceModifier.height(if (isCompactHeight) 1.dp else 2.dp))
                                                Text(
                                                    text = day.highLowLabel,
                                                    maxLines = 1,
                                                    style = TextStyle(
                                                        color = sliderPrimaryColor,
                                                        fontSize = dayHiLoSp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                            }
                                        }

                                        if (canScrollRight) {
                                            Spacer(modifier = GlanceModifier.width(2.dp))
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = GlanceModifier
                                                    .width(22.dp)
                                                    .height(if (isTall) 50.dp else 40.dp)
                                                    .cornerRadius(8.dp)
                                                    .background(pillBgColor)
                                                    .clickable(
                                                        actionSendBroadcast(
                                                            createScrollForecastBroadcastIntent(context, nextRightOffset)
                                                        )
                                                    )
                                            ) {
                                                Text(
                                                    text = "›",
                                                    maxLines = 1,
                                                    style = TextStyle(
                                                        color = ColorProvider(Color(0xFF7DD3FC)),
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
