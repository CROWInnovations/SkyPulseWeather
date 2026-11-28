package com.crowinnovations.skypulseweather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.crowinnovations.skypulseweather.domain.safeClamp
import com.crowinnovations.skypulseweather.sync.WeatherSyncWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseSkyPulseWidgetReceiver : AppWidgetProvider() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == WeatherWidgetPublisher.ACTION_NOOP_SLIDER_TAP) {
            // Intentional no-op: absorbs slider item taps cleanly so swiping/tapping the 10-day strip never opens Settings or MainActivity
            return
        }
        if (action == WeatherWidgetPublisher.ACTION_REFRESH_WIDGET) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            val locId = intent.getLongExtra(WeatherWidgetPublisher.EXTRA_LOCATION_ID, -1L)
            receiverScope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        appContext,
                        WidgetEntryPoint::class.java
                    )
                    val repo = entryPoint.weatherRepository()
                    entryPoint.prefsRepository().setWidgetForecastDayOffset(0)
                    if (locId > 0L) {
                        repo.refreshLocation(locId, force = true)
                    } else {
                        repo.refreshAllLocations(force = true)
                    }
                    WeatherWidgetPublisher.publishAll(appContext, explicitScrollOffset = 0)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == WeatherWidgetPublisher.ACTION_SCROLL_FORECAST) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            val targetOffset = intent.getIntExtra(WeatherWidgetPublisher.EXTRA_TARGET_OFFSET, 0)
                .safeClamp(0, 5)
            receiverScope.launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        appContext,
                        WidgetEntryPoint::class.java
                    )
                    entryPoint.prefsRepository().setWidgetForecastDayOffset(targetOffset)
                    WeatherWidgetPublisher.publishAll(appContext, explicitScrollOffset = targetOffset)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSyncWorker.schedulePeriodicSync(context)
        WeatherSyncWorker.enqueueImmediateSync(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherSyncWorker.schedulePeriodicSync(context)
        WeatherSyncWorker.enqueueImmediateSync(context)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    WidgetEntryPoint::class.java
                )
                entryPoint.prefsRepository().setWidgetForecastDayOffset(0)
                WeatherWidgetPublisher.publishIds(appContext, appWidgetIds, explicitScrollOffset = 0)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                WeatherWidgetPublisher.publishIds(appContext, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            WidgetEntryPoint::class.java
        )
        val prefs = entryPoint.prefsRepository()
        receiverScope.launch {
            appWidgetIds.forEach { widgetId ->
                prefs.clearWidgetLocation(widgetId)
            }
        }
    }
}

/** 5×3 Expanded Full Weather & Swipeable 10-Day Forecast Widget */
class WeatherWidget5x3Receiver : BaseSkyPulseWidgetReceiver()

/** 5×2 Wide Full Weather & Swipeable 10-Day Forecast Widget */
class WeatherWidget5x2Receiver : BaseSkyPulseWidgetReceiver()

/** 4×2 Full Weather & Swipeable 10-Day Forecast Widget */
class WeatherGlanceReceiver : BaseSkyPulseWidgetReceiver()

/** 2×2 Square Weather Widget */
class WeatherWidget2x2Receiver : BaseSkyPulseWidgetReceiver()

/** 2×1 Compact Strip Weather Widget */
class WeatherWidget2x1Receiver : BaseSkyPulseWidgetReceiver()

/** 1×1 Mini Tile Weather Widget */
class WeatherWidget1x1Receiver : BaseSkyPulseWidgetReceiver()
