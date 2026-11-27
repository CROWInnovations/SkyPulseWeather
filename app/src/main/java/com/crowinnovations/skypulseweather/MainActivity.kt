package com.crowinnovations.skypulseweather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crowinnovations.skypulseweather.ui.WeatherScreen
import com.crowinnovations.skypulseweather.ui.WeatherViewModel
import com.crowinnovations.skypulseweather.ui.theme.SkyPulseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOCATION_ID = "extra_location_id"
    }

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.filterTouchesWhenObscured = true
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        handleWidgetDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SkyPulseTheme(themeMode = uiState.preferences.themeMode) {
                WeatherScreen(
                    uiState = uiState,
                    onSelectLocation = viewModel::selectLocation,
                    onRefresh = { viewModel.refreshWeather(force = true, updateGps = true) },
                    onOpenLocations = viewModel::openLocationsSheet,
                    onCloseLocations = viewModel::closeLocationsSheet,
                    onOpenSettings = viewModel::openSettingsDialog,
                    onCloseSettings = viewModel::closeSettingsDialog,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onExecuteSearch = viewModel::executeSearch,
                    onSelectCandidate = viewModel::addCandidateLocation,
                    onEnableGps = viewModel::enableOrRefreshGpsLocation,
                    onPermissionDenied = viewModel::onLocationPermissionDenied,
                    onDeleteLocation = viewModel::deleteLocation,
                    onTempUnitSelect = viewModel::setTempUnit,
                    onWindUnitSelect = viewModel::setWindUnit,
                    onThemeModeSelect = viewModel::setThemeMode,
                    onShowTimeDisplaySelect = viewModel::setShowTimeDisplay,
                    onDateDisplayPositionSelect = viewModel::setDateDisplayPosition,
                    onAdjustTimeFontSize = viewModel::adjustTimeFontSize,
                    onTimeFontStyleSelect = viewModel::setTimeFontStyle,
                    onTimeTextColorSelect = viewModel::setTimeTextColorArgb,
                    onAdjustWidgetTempFontSize = viewModel::adjustWidgetTempFontSize,
                    onWidgetTempFontStyleSelect = viewModel::setWidgetTempFontStyle,
                    onWidgetTempTextColorSelect = viewModel::setWidgetTempTextColorArgb,
                    onAdjustForecastTransparency = viewModel::adjustForecastTransparencyDelta,
                    onAdjustForecastFontBoost = viewModel::adjustForecastFontBoost,
                    onGatewaySave = viewModel::setGatewayBaseUrl
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetWidgetSliderToStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetDeepLink(intent)
    }

    private fun handleWidgetDeepLink(intent: Intent?) {
        val locId = runCatching {
            intent?.getLongExtra(EXTRA_LOCATION_ID, -1L) ?: -1L
        }.getOrDefault(-1L)
        if (locId in 1L..1_000_000L) {
            viewModel.selectLocation(locId)
        }
    }
}
