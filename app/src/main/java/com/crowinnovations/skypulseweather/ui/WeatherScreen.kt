package com.crowinnovations.skypulseweather.ui

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowinnovations.skypulseweather.R
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.domain.BackingTone
import com.crowinnovations.skypulseweather.domain.DateDisplayPosition
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.safeClamp
import com.crowinnovations.skypulseweather.ui.components.AutoFitSingleLineText
import com.crowinnovations.skypulseweather.ui.components.FiveDayForecastCard
import com.crowinnovations.skypulseweather.ui.components.GlassCard
import com.crowinnovations.skypulseweather.ui.components.HeroWeatherSection
import com.crowinnovations.skypulseweather.ui.components.HourlyForecastCard
import com.crowinnovations.skypulseweather.ui.components.ManageLocationsSheet
import com.crowinnovations.skypulseweather.ui.components.SevereAlertsBanner
import com.crowinnovations.skypulseweather.ui.components.SettingsDialog
import com.crowinnovations.skypulseweather.ui.components.SingleLineText
import com.crowinnovations.skypulseweather.ui.components.SkyBackdrop
import com.crowinnovations.skypulseweather.ui.theme.SkyPalette
import com.crowinnovations.skypulseweather.ui.theme.rememberIsDarkTheme
import com.crowinnovations.skypulseweather.ui.theme.skyPaletteFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeatherScreen(
    uiState: WeatherUiState,
    onSelectLocation: (Long) -> Unit,
    onRefresh: () -> Unit,
    onOpenLocations: () -> Unit,
    onCloseLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onSelectCandidate: (com.crowinnovations.skypulseweather.domain.LocationSearchCandidate) -> Unit,
    onEnableGps: () -> Unit,
    onPermissionDenied: () -> Unit,
    onDeleteLocation: (Long) -> Unit,
    onTempUnitSelect: (TempUnit) -> Unit,
    onWindUnitSelect: (WindUnit) -> Unit,
    onThemeModeSelect: (com.crowinnovations.skypulseweather.domain.ThemeMode) -> Unit,
    onFullScreenShowTimeSelect: (Boolean) -> Unit,
    onFullScreenDatePositionSelect: (DateDisplayPosition) -> Unit,
    onAdjustFullScreenTimeFontSize: (Int) -> Unit,
    onFullScreenTimeFontStyleSelect: (DisplayFontStyle) -> Unit,
    onFullScreenTimeTextColorSelect: (Int) -> Unit,
    onAdjustFullScreenTempFontSize: (Int) -> Unit,
    onFullScreenTempFontStyleSelect: (DisplayFontStyle) -> Unit,
    onFullScreenTempTextColorSelect: (Int) -> Unit,
    onAdjustForecastTransparency: (Int) -> Unit = {},
    onForecastBackingToneSelect: (BackingTone) -> Unit = {},
    onAdjustForecastFontBoost: (Int) -> Unit = {},
    onWidgetShowTimeSelect: (Boolean) -> Unit,
    onWidgetDatePositionSelect: (DateDisplayPosition) -> Unit,
    onAdjustWidgetTimeFontSize: (Int) -> Unit,
    onWidgetTimeFontStyleSelect: (DisplayFontStyle) -> Unit,
    onWidgetTimeTextColorSelect: (Int) -> Unit,
    onAdjustWidgetTempFontSize: (Int) -> Unit,
    onWidgetTempFontStyleSelect: (DisplayFontStyle) -> Unit,
    onWidgetTempTextColorSelect: (Int) -> Unit,
    onAdjustWidgetDataFontDelta: (Int) -> Unit = {},
    onAdjustWidgetSliderTransparency: (Int) -> Unit = {},
    onWidgetSliderBackingToneSelect: (BackingTone) -> Unit = {},
    onGatewaySave: (String) -> Unit
) {
    val isDark = rememberIsDarkTheme(uiState.preferences.themeMode)
    val activeSnap = uiState.activeSnapshot
    val currentCondition = activeSnap?.condition ?: WeatherCondition.PARTLY_CLOUDY_DAY
    val currentIsDay = activeSnap?.isDay ?: true
    val currentCloudCover = activeSnap?.effectiveCloudCover ?: 35
    val hasTornadoWarning = activeSnap?.hasTornadoWarning == true
    val palette = skyPaletteFor(currentCondition, isDark)
    val scope = rememberCoroutineScope()

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val granted = grantMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grantMap[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            onEnableGps()
        } else {
            onPermissionDenied()
        }
    }

    val requestGpsAction = {
        locationPermLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    val topClockReserveDp = if (!uiState.preferences.fullScreenShowTimeDisplay) {
        0f
    } else if (uiState.preferences.fullScreenDateDisplayPosition == DateDisplayPosition.BELOW_TIME) {
        (uiState.preferences.fullScreenTimeFontSizeSp * 2.05f + 18f).safeClamp(0f, 150f)
    } else {
        (uiState.preferences.fullScreenTimeFontSizeSp * 1.25f + 14f).safeClamp(0f, 120f)
    }

    SkyBackdrop(
        condition = currentCondition,
        isDay = currentIsDay,
        cloudCoverPercent = currentCloudCover,
        hasTornadoWarning = hasTornadoWarning,
        topClockReserveDp = topClockReserveDp,
        palette = palette
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            if (uiState.preferences.fullScreenShowTimeDisplay) {
                TopCenteredClockBar(
                    fontSizeSp = uiState.preferences.fullScreenTimeFontSizeSp,
                    fontStylePref = uiState.preferences.fullScreenTimeFontStyle,
                    textColorArgb = uiState.preferences.fullScreenTimeTextColorArgb,
                    datePosition = uiState.preferences.fullScreenDateDisplayPosition
                )
            }

            // Single-line non-wrapping Top Header Bar
            TopHeaderBar(
                activeLocation = uiState.activeLocation,
                locationCount = uiState.locations.size,
                isRefreshing = uiState.isRefreshing,
                palette = palette,
                onOpenLocations = onOpenLocations,
                onRefresh = onRefresh,
                onOpenSettings = onOpenSettings
            )

            // Multi-location pill bar when user has 2+ saved locations
            if (uiState.locations.size > 1) {
                MultiLocationPillRow(
                    locations = uiState.locations,
                    activeLocationId = uiState.activeLocationId,
                    weatherMap = uiState.weatherByLocation,
                    tempUnit = uiState.preferences.tempUnit,
                    palette = palette,
                    onSelectLocation = onSelectLocation
                )
            }

            // Single-line Updated / Offline status subtitle
            StatusTimestampRow(
                snapshot = uiState.activeSnapshot,
                offlineMessage = uiState.offlineMessage,
                palette = palette
            )

            if (uiState.locations.isEmpty()) {
                EmptyLocationsContent(
                    palette = palette,
                    onAddLocation = onOpenLocations,
                    onUseGps = requestGpsAction
                )
            } else {
                val targetIndex = uiState.locations
                    .indexOfFirst { it.id == uiState.activeLocationId }
                    .let { if (it < 0) 0 else it }
                    .safeClamp(0, (uiState.locations.size - 1).coerceAtLeast(0))

                val pagerState = rememberPagerState(
                    initialPage = targetIndex,
                    pageCount = { uiState.locations.size }
                )
                val latestLocations by rememberUpdatedState(uiState.locations)
                val latestActiveId by rememberUpdatedState(uiState.activeLocationId)
                var isProgrammaticScroll by remember { mutableStateOf(false) }

                // Sync external selection (pill tap, sheet pick, or newly added location) -> Pager
                LaunchedEffect(uiState.activeLocationId, uiState.locations.size) {
                    val idx = uiState.locations.indexOfFirst { it.id == uiState.activeLocationId }
                    if (idx >= 0 && idx != pagerState.currentPage) {
                        isProgrammaticScroll = true
                        try {
                            pagerState.animateScrollToPage(idx)
                        } finally {
                            isProgrammaticScroll = false
                        }
                    }
                }

                // Sync manual user swipe -> Active Location without feedback race
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
                        .distinctUntilChanged()
                        .collect { (page, scrolling) ->
                            if (!scrolling && !isProgrammaticScroll) {
                                val loc = latestLocations.getOrNull(page)
                                if (loc != null && loc.id != latestActiveId) {
                                    scope.launch { onSelectLocation(loc.id) }
                                }
                            }
                        }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    val location = uiState.locations.getOrNull(pageIndex)
                    val snapshot = location?.let { uiState.weatherByLocation[it.id] }
                    val pagePalette = skyPaletteFor(
                        snapshot?.condition ?: currentCondition,
                        isDark
                    )

                    LocationForecastPage(
                        location = location,
                        snapshot = snapshot,
                        isRefreshing = uiState.isRefreshing,
                        tempUnit = uiState.preferences.tempUnit,
                        windUnit = uiState.preferences.windUnit,
                        tempFontSizeSp = uiState.preferences.fullScreenTempFontSizeSp,
                        tempFontStyle = uiState.preferences.fullScreenTempFontStyle,
                        tempTextColorArgb = uiState.preferences.fullScreenTempTextColorArgb,
                        forecastTransparencyDeltaPct = uiState.preferences.forecastTransparencyDeltaPct,
                        forecastBackingTone = uiState.preferences.forecastBackingTone,
                        forecastFontBoostSp = uiState.preferences.forecastFontBoostSp,
                        palette = pagePalette,
                        onRetry = onRefresh
                    )
                }
            }
        }

        if (uiState.showLocationsSheet) {
            ManageLocationsSheet(
                locations = uiState.locations,
                weatherByLocation = uiState.weatherByLocation,
                activeLocationId = uiState.activeLocationId,
                tempUnit = uiState.preferences.tempUnit,
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                isLocatingGps = uiState.isLocatingGps,
                searchError = uiState.searchError,
                onQueryChange = onSearchQueryChange,
                onExecuteSearch = onExecuteSearch,
                onSelectCandidate = onSelectCandidate,
                onUseGpsClick = requestGpsAction,
                onSelectSavedLocation = onSelectLocation,
                onDeleteLocation = onDeleteLocation,
                onDismiss = onCloseLocations
            )
        }

        if (uiState.showSettingsDialog) {
            SettingsDialog(
                preferences = uiState.preferences,
                onTempUnitSelect = onTempUnitSelect,
                onWindUnitSelect = onWindUnitSelect,
                onThemeModeSelect = onThemeModeSelect,
                onFullScreenShowTimeSelect = onFullScreenShowTimeSelect,
                onFullScreenDatePositionSelect = onFullScreenDatePositionSelect,
                onAdjustFullScreenTimeFontSize = onAdjustFullScreenTimeFontSize,
                onFullScreenTimeFontStyleSelect = onFullScreenTimeFontStyleSelect,
                onFullScreenTimeTextColorSelect = onFullScreenTimeTextColorSelect,
                onAdjustFullScreenTempFontSize = onAdjustFullScreenTempFontSize,
                onFullScreenTempFontStyleSelect = onFullScreenTempFontStyleSelect,
                onFullScreenTempTextColorSelect = onFullScreenTempTextColorSelect,
                onAdjustForecastTransparency = onAdjustForecastTransparency,
                onForecastBackingToneSelect = onForecastBackingToneSelect,
                onAdjustForecastFontBoost = onAdjustForecastFontBoost,
                onWidgetShowTimeSelect = onWidgetShowTimeSelect,
                onWidgetDatePositionSelect = onWidgetDatePositionSelect,
                onAdjustWidgetTimeFontSize = onAdjustWidgetTimeFontSize,
                onWidgetTimeFontStyleSelect = onWidgetTimeFontStyleSelect,
                onWidgetTimeTextColorSelect = onWidgetTimeTextColorSelect,
                onAdjustWidgetTempFontSize = onAdjustWidgetTempFontSize,
                onWidgetTempFontStyleSelect = onWidgetTempFontStyleSelect,
                onWidgetTempTextColorSelect = onWidgetTempTextColorSelect,
                onAdjustWidgetDataFontDelta = onAdjustWidgetDataFontDelta,
                onAdjustWidgetSliderTransparency = onAdjustWidgetSliderTransparency,
                onWidgetSliderBackingToneSelect = onWidgetSliderBackingToneSelect,
                onGatewaySave = onGatewaySave,
                onDismiss = onCloseSettings
            )
        }
    }
}

@Composable
private fun TopCenteredClockBar(
    fontSizeSp: Int,
    fontStylePref: DisplayFontStyle,
    textColorArgb: Int,
    datePosition: DateDisplayPosition
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val delayToNextSecond = (1_000L - (now % 1_000L)).coerceIn(200L, 1_000L)
            delay(delayToNextSecond)
            nowMillis = System.currentTimeMillis()
        }
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formattedTime = remember(nowMillis / 1_000L, is24Hour) {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(nowMillis))
    }
    val formattedDate = remember(nowMillis / 30_000L) {
        SimpleDateFormat("MMM d", Locale.US).format(Date(nowMillis))
    }
    val safeSp = fontSizeSp.safeClamp(
        UserPreferencesRepository.MIN_TIME_FONT_SP,
        UserPreferencesRepository.MAX_TIME_FONT_SP
    )
    val belowDateSp = (safeSp * 0.72f).safeClamp(12f, 44f)
    val clockShadow = Shadow(
        color = Color(0xCC020617),
        offset = Offset(0f, 2f),
        blurRadius = 10f
    )
    val chosenFamily = when (fontStylePref) {
        DisplayFontStyle.SERIF -> FontFamily.Serif
        DisplayFontStyle.MONO -> FontFamily.Monospace
        DisplayFontStyle.DEFAULT, DisplayFontStyle.ITALIC -> FontFamily.SansSerif
    }
    val chosenFontStyle = if (fontStylePref == DisplayFontStyle.ITALIC) {
        FontStyle.Italic
    } else {
        FontStyle.Normal
    }
    val resolvedTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontFamily = chosenFamily,
        fontStyle = chosenFontStyle,
        shadow = clockShadow
    )
    val resolvedColor = Color(textColorArgb or 0xFF000000.toInt())

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp, start = 16.dp, end = 16.dp)
    ) {
        when (datePosition) {
            DateDisplayPosition.OFF -> {
                AutoFitSingleLineText(
                    text = formattedTime,
                    color = resolvedColor,
                    maxFontSize = safeSp.sp,
                    minFontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = resolvedTextStyle,
                    textAlign = TextAlign.Center,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DateDisplayPosition.LEFT_OF_TIME -> {
                AutoFitSingleLineText(
                    text = "$formattedDate   $formattedTime",
                    color = resolvedColor,
                    maxFontSize = safeSp.sp,
                    minFontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = resolvedTextStyle,
                    textAlign = TextAlign.Center,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DateDisplayPosition.RIGHT_OF_TIME -> {
                AutoFitSingleLineText(
                    text = "$formattedTime   $formattedDate",
                    color = resolvedColor,
                    maxFontSize = safeSp.sp,
                    minFontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = resolvedTextStyle,
                    textAlign = TextAlign.Center,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DateDisplayPosition.BELOW_TIME -> {
                AutoFitSingleLineText(
                    text = formattedTime,
                    color = resolvedColor,
                    maxFontSize = safeSp.sp,
                    minFontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = resolvedTextStyle,
                    textAlign = TextAlign.Center,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                AutoFitSingleLineText(
                    text = formattedDate,
                    color = resolvedColor,
                    maxFontSize = belowDateSp.sp,
                    minFontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = resolvedTextStyle,
                    textAlign = TextAlign.Center,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    activeLocation: SavedLocation?,
    locationCount: Int,
    isRefreshing: Boolean,
    palette: SkyPalette,
    onOpenLocations: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flexible constrained location selector pill
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.cardSurface)
                .border(1.dp, palette.cardBorder, RoundedCornerShape(50))
                .clickable(onClick = onOpenLocations)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (activeLocation?.isGps == true) {
                    Icons.Default.MyLocation
                } else {
                    Icons.Default.LocationOn
                },
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            AutoFitSingleLineText(
                text = activeLocation?.name ?: "Add Location",
                color = palette.primaryText,
                maxFontSize = 17.sp,
                minFontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (locationCount > 1) {
                Spacer(modifier = Modifier.width(5.dp))
                SingleLineText(
                    text = "$locationCount",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Switch or add location",
                tint = palette.secondaryText,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        HeaderCircleActionButton(
            onClick = onOpenLocations,
            palette = palette
        ) {
            Icon(
                imageVector = Icons.Default.AddLocationAlt,
                contentDescription = "Add ZIP or GPS Location",
                tint = palette.primaryText,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        HeaderCircleActionButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            palette = palette
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = palette.primaryText
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh forecast",
                    tint = palette.primaryText,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        HeaderCircleActionButton(
            onClick = onOpenSettings,
            palette = palette
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = palette.primaryText,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun HeaderCircleActionButton(
    onClick: () -> Unit,
    palette: SkyPalette,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.cardSurface)
            .border(1.dp, palette.cardBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        content()
    }
}

@Composable
private fun MultiLocationPillRow(
    locations: List<SavedLocation>,
    activeLocationId: Long,
    weatherMap: Map<Long, WeatherSnapshot>,
    tempUnit: TempUnit,
    palette: SkyPalette,
    onSelectLocation: (Long) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(locations, key = { it.id }) { loc ->
            val selected = loc.id == activeLocationId
            val snap = weatherMap[loc.id]
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(shape)
                    .background(
                        if (selected) palette.primaryText.copy(alpha = 0.20f)
                        else palette.cardSurface
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) palette.accent else palette.cardBorder,
                        shape = shape
                    )
                    .clickable { onSelectLocation(loc.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loc.isGps) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                SingleLineText(
                    text = if (snap != null) {
                        "${loc.name} · ${tempUnit.format(snap.currentTempCelsius)}"
                    } else {
                        loc.name
                    },
                    color = palette.primaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatusTimestampRow(
    snapshot: WeatherSnapshot?,
    offlineMessage: String?,
    palette: SkyPalette
) {
    val timeText = snapshot?.let {
        val fmt = SimpleDateFormat("h:mm a", Locale.US)
        "Updated ${fmt.format(Date(it.fetchedAtMillis))} · 15m auto-sync"
    } ?: "Syncing live weather…"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!offlineMessage.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        SingleLineText(
            text = if (!offlineMessage.isNullOrBlank() && snapshot != null) {
                "$offlineMessage · $timeText"
            } else {
                offlineMessage ?: timeText
            },
            color = palette.secondaryText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LocationForecastPage(
    location: SavedLocation?,
    snapshot: WeatherSnapshot?,
    isRefreshing: Boolean,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    tempFontSizeSp: Int = UserPreferencesRepository.DEFAULT_FULL_SCREEN_TEMP_FONT_SP,
    tempFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    tempTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    forecastTransparencyDeltaPct: Int = 0,
    forecastBackingTone: BackingTone = BackingTone.DEFAULT,
    forecastFontBoostSp: Int = 0,
    palette: SkyPalette,
    onRetry: () -> Unit
) {
    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(palette = palette) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(color = palette.primaryText)
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleLineText(
                            text = "Loading forecast for ${location?.name ?: "location"}…",
                            color = palette.primaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        SingleLineText(
                            text = "No cached forecast yet",
                            color = palette.primaryText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRetry) {
                            SingleLineText(text = "Retry Sync")
                        }
                    }
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val isWideLandscape = maxWidth >= 600.dp && maxWidth > maxHeight

        if (isWideLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroWeatherSection(
                        snapshot = snapshot,
                        tempUnit = tempUnit,
                        windUnit = windUnit,
                        tempFontSizeSp = tempFontSizeSp,
                        tempFontStyle = tempFontStyle,
                        tempTextColorArgb = tempTextColorArgb,
                        palette = palette,
                        reserveRightCelestialStage = false
                    )
                    SevereAlertsBanner(alerts = snapshot.alerts)
                }

                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = (viewportHeight - 12.dp).coerceAtLeast(0.dp))
                ) {
                    HourlyForecastCard(
                        hourly = snapshot.hourly,
                        tempUnit = tempUnit,
                        palette = palette,
                        transparencyDeltaPct = forecastTransparencyDeltaPct,
                        backingTone = forecastBackingTone,
                        fontBoostSp = forecastFontBoostSp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    FiveDayForecastCard(
                        daily = snapshot.daily,
                        tempUnit = tempUnit,
                        palette = palette,
                        transparencyDeltaPct = forecastTransparencyDeltaPct,
                        backingTone = forecastBackingTone,
                        fontBoostSp = forecastFontBoostSp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                if (snapshot.alerts.isNotEmpty()) {
                    SevereAlertsBanner(alerts = snapshot.alerts)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                HeroWeatherSection(
                    snapshot = snapshot,
                    tempUnit = tempUnit,
                    windUnit = windUnit,
                    tempFontSizeSp = tempFontSizeSp,
                    tempFontStyle = tempFontStyle,
                    tempTextColorArgb = tempTextColorArgb,
                    palette = palette,
                    reserveRightCelestialStage = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                HourlyForecastCard(
                    hourly = snapshot.hourly,
                    tempUnit = tempUnit,
                    palette = palette,
                    transparencyDeltaPct = forecastTransparencyDeltaPct,
                    backingTone = forecastBackingTone,
                    fontBoostSp = forecastFontBoostSp
                )

                // Flexible spring aligns the 5-Day Forecast card flush to the bottom of the display screen
                Spacer(modifier = Modifier.height(6.dp))
                Spacer(modifier = Modifier.weight(1f))

                FiveDayForecastCard(
                    daily = snapshot.daily,
                    tempUnit = tempUnit,
                    palette = palette,
                    transparencyDeltaPct = forecastTransparencyDeltaPct,
                    backingTone = forecastBackingTone,
                    fontBoostSp = forecastFontBoostSp
                )
            }
        }
    }
}

@Composable
private fun EmptyLocationsContent(
    palette: SkyPalette,
    onAddLocation: () -> Unit,
    onUseGps: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(palette = palette) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleLineText(
                    text = "Add a Weather Location",
                    color = palette.primaryText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onUseGps,
                        modifier = Modifier.weight(1f)
                    ) {
                        SingleLineText(text = "Use GPS")
                    }
                    Button(
                        onClick = onAddLocation,
                        modifier = Modifier.weight(1f)
                    ) {
                        SingleLineText(text = "ZIP / City")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributionFooter(palette: SkyPalette) {
    SingleLineText(
        text = "Forecast: Open-Meteo · Alerts: NOAA/NWS · 15m auto-sync",
        color = palette.secondaryText.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
