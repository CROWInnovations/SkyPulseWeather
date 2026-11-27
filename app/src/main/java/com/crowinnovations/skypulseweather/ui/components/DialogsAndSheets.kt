package com.crowinnovations.skypulseweather.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crowinnovations.skypulseweather.R
import com.crowinnovations.skypulseweather.data.local.AppPreferences
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.domain.BackingTone
import com.crowinnovations.skypulseweather.domain.DateDisplayPosition
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.crowinnovations.skypulseweather.domain.LocationSearchCandidate
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.ThemeMode
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.safeClamp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageLocationsSheet(
    locations: List<SavedLocation>,
    weatherByLocation: Map<Long, WeatherSnapshot>,
    activeLocationId: Long,
    tempUnit: TempUnit,
    searchQuery: String,
    searchResults: List<LocationSearchCandidate>,
    isSearching: Boolean,
    isLocatingGps: Boolean,
    searchError: String?,
    onQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onSelectCandidate: (LocationSearchCandidate) -> Unit,
    onUseGpsClick: () -> Unit,
    onSelectSavedLocation: (Long) -> Unit,
    onDeleteLocation: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleLineText(
                    text = "Locations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Text(
                text = "Foreground location is accessed only when tapped below—never in the background.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onUseGpsClick,
                enabled = !isLocatingGps,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isLocatingGps) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SingleLineText(
                    text = if (isLocatingGps) "Acquiring GPS Location…" else "Use Current GPS Location",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    SingleLineText(
                        text = "Search US ZIP (21201) or city…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onExecuteSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search ZIP or city")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() })
            )

            if (!searchError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = searchError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                if (searchResults.isNotEmpty()) {
                    item {
                        SingleLineText(
                            text = "SEARCH RESULTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    itemsIndexed(searchResults) { idx, candidate ->
                        if (idx > 0) {
                            HorizontalDivider(thickness = 0.5.dp, color = hairline)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onSelectCandidate(candidate) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                SingleLineText(
                                    text = candidate.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                SingleLineText(
                                    text = candidate.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = hairline
                        )
                    }
                }

                item {
                    SingleLineText(
                        text = "SAVED LOCATIONS (${locations.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                itemsIndexed(locations, key = { _, it -> it.id }) { idx, loc ->
                    if (idx > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = hairline)
                    }
                    val isSelected = loc.id == activeLocationId
                    val snap = weatherByLocation[loc.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onSelectSavedLocation(loc.id) }
                            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (loc.isGps) {
                                Icons.Default.MyLocation
                            } else {
                                Icons.Default.LocationOn
                            },
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SingleLineText(
                                text = loc.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            SingleLineText(
                                text = snap?.conditionLabel ?: loc.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (snap != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            SingleLineText(
                                text = tempUnit.format(snap.currentTempCelsius),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteLocation(loc.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove ${loc.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    preferences: AppPreferences,
    onTempUnitSelect: (TempUnit) -> Unit,
    onWindUnitSelect: (WindUnit) -> Unit,
    onThemeModeSelect: (ThemeMode) -> Unit,
    // Full Screen Display callbacks
    onFullScreenShowTimeSelect: (Boolean) -> Unit,
    onFullScreenDatePositionSelect: (DateDisplayPosition) -> Unit,
    onAdjustFullScreenTimeFontSize: (Float) -> Unit,
    onFullScreenTimeFontStyleSelect: (DisplayFontStyle) -> Unit,
    onFullScreenTimeTextColorSelect: (Int) -> Unit,
    onAdjustFullScreenTempFontSize: (Float) -> Unit,
    onFullScreenTempFontStyleSelect: (DisplayFontStyle) -> Unit,
    onFullScreenTempTextColorSelect: (Int) -> Unit,
    onAdjustForecastTransparency: (Int) -> Unit = {},
    onForecastBackingToneSelect: (BackingTone) -> Unit = {},
    onAdjustForecastFontSize: (Float) -> Unit = {},
    // Widget Display callbacks
    onWidgetShowTimeSelect: (Boolean) -> Unit,
    onWidgetDatePositionSelect: (DateDisplayPosition) -> Unit,
    onAdjustWidgetTimeFontSize: (Float) -> Unit,
    onWidgetTimeFontStyleSelect: (DisplayFontStyle) -> Unit,
    onWidgetTimeTextColorSelect: (Int) -> Unit,
    onAdjustWidgetTempFontSize: (Float) -> Unit,
    onWidgetTempFontStyleSelect: (DisplayFontStyle) -> Unit,
    onWidgetTempTextColorSelect: (Int) -> Unit,
    onAdjustWidgetDataFontSize: (Float) -> Unit = {},
    onAdjustWidgetSliderTransparency: (Int) -> Unit = {},
    onWidgetSliderBackingToneSelect: (BackingTone) -> Unit = {},
    onGatewaySave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var gatewayInput by rememberSaveable(preferences.gatewayBaseUrl) {
        mutableStateOf(preferences.gatewayBaseUrl)
    }
    var gatewayError by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
    val hostSafePadding = WindowInsets.safeDrawing.asPaddingValues()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogSafePadding = WindowInsets.safeDrawing.asPaddingValues()
        val safeTop = maxOf(
            hostSafePadding.calculateTopPadding(),
            dialogSafePadding.calculateTopPadding(),
            32.dp
        ) + 10.dp
        val safeBottom = maxOf(
            hostSafePadding.calculateBottomPadding(),
            dialogSafePadding.calculateBottomPadding(),
            56.dp
        ) + 14.dp

        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = safeTop,
                    bottom = safeBottom
                )
        ) {
            val maxDialogHeight = maxHeight.coerceAtLeast(300.dp)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(0.75.dp, hairline)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Compact sleek top Logo Banner
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.68f)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.settings_logo_banner),
                            contentDescription = "SkyPulse Weather Logo Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            surfaceColor.copy(alpha = 0.85f),
                                            surfaceColor
                                        )
                                    )
                                )
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 2.dp)
                        ) {
                            AutoFitSingleLineText(
                                text = "SkyPulse Weather App",
                                color = MaterialTheme.colorScheme.onSurface,
                                maxFontSize = 16.5.sp,
                                minFontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AutoFitSingleLineText(
                                text = "\"No Ads. No Data Collection. No Paywalls.\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxFontSize = 11.2.sp,
                                minFontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                textAlign = TextAlign.Center,
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Scrollable grouped settings body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Group 1: Global Units (Shared across Full Screen & Widget)
                        SettingsGroupCard(title = "GENERAL UNITS", hairline = hairline) {
                            CompactSettingRow(label = "Temperature") {
                                CompactSegmentedControl(
                                    options = listOf(
                                        "°F" to (preferences.tempUnit == TempUnit.FAHRENHEIT),
                                        "°C" to (preferences.tempUnit == TempUnit.CELSIUS)
                                    ),
                                    onSelectIndex = { idx ->
                                        onTempUnitSelect(if (idx == 0) TempUnit.FAHRENHEIT else TempUnit.CELSIUS)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Wind Speed") {
                                CompactSegmentedControl(
                                    options = listOf(
                                        "mph" to (preferences.windUnit == WindUnit.MPH),
                                        "km/h" to (preferences.windUnit == WindUnit.KMH)
                                    ),
                                    onSelectIndex = { idx ->
                                        onWindUnitSelect(if (idx == 0) WindUnit.MPH else WindUnit.KMH)
                                    }
                                )
                            }
                        }

                        // ============================================================
                        // SECTION A: FULL SCREEN DISPLAY
                        // ============================================================
                        Spacer(modifier = Modifier.height(2.dp))
                        SettingsDisplaySectionBanner(
                            title = "FULL SCREEN DISPLAY",
                            subtitle = "App window theme, forecast cards & typography",
                            icon = Icons.Default.Smartphone,
                            accentColor = MaterialTheme.colorScheme.primary
                        )

                        SettingsGroupCard(title = "FULL SCREEN · FORECAST & THEME", hairline = hairline) {
                            CompactSettingRow(label = "Theme") {
                                CompactSegmentedControl(
                                    options = listOf(
                                        "Auto" to (preferences.themeMode == ThemeMode.SYSTEM),
                                        "Light" to (preferences.themeMode == ThemeMode.LIGHT),
                                        "Dark" to (preferences.themeMode == ThemeMode.DARK)
                                    ),
                                    compactItems = true,
                                    onSelectIndex = { idx ->
                                        val mode = when (idx) {
                                            0 -> ThemeMode.SYSTEM
                                            1 -> ThemeMode.LIGHT
                                            else -> ThemeMode.DARK
                                        }
                                        onThemeModeSelect(mode)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Card Backing") {
                                CompactSegmentedControl(
                                    options = BackingTone.entries.map { tone ->
                                        tone.label to (preferences.forecastBackingTone == tone)
                                    },
                                    compactItems = true,
                                    onSelectIndex = { idx ->
                                        BackingTone.entries.getOrNull(idx)?.let(onForecastBackingToneSelect)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Transparency") {
                                val trDelta = preferences.forecastTransparencyDeltaPct
                                val trLabel = when {
                                    trDelta == 0 -> "Default"
                                    trDelta > 0 -> "+${trDelta}%"
                                    else -> "${trDelta}%"
                                }
                                FontBoostStepper(
                                    currentLabel = trLabel,
                                    canDecrease = trDelta > UserPreferencesRepository.MIN_FORECAST_TRANSPARENCY_DELTA_PCT,
                                    canIncrease = trDelta < UserPreferencesRepository.MAX_FORECAST_TRANSPARENCY_DELTA_PCT,
                                    onDecrease = {
                                        onAdjustForecastTransparency(-UserPreferencesRepository.FORECAST_TRANSPARENCY_STEP_PCT)
                                    },
                                    onIncrease = {
                                        onAdjustForecastTransparency(UserPreferencesRepository.FORECAST_TRANSPARENCY_STEP_PCT)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Font Size") {
                                FontSizeStepper(
                                    currentSp = preferences.forecastFontSizeSp,
                                    canDecrease = preferences.forecastFontSizeSp > UserPreferencesRepository.MIN_FORECAST_FONT_SP + 0.01f,
                                    canIncrease = preferences.forecastFontSizeSp < UserPreferencesRepository.MAX_FORECAST_FONT_SP - 0.01f,
                                    onDecrease = {
                                        onAdjustForecastFontSize(-UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    },
                                    onIncrease = {
                                        onAdjustForecastFontSize(UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    }
                                )
                            }
                        }

                        SettingsGroupCard(title = "FULL SCREEN · TEMPERATURE", hairline = hairline) {
                            CompactSettingRow(label = "Font Size") {
                                FontSizeStepper(
                                    currentSp = preferences.fullScreenTempFontSizeSp,
                                    canDecrease = preferences.fullScreenTempFontSizeSp > UserPreferencesRepository.MIN_FULL_SCREEN_TEMP_FONT_SP + 0.01f,
                                    canIncrease = preferences.fullScreenTempFontSizeSp < UserPreferencesRepository.MAX_FULL_SCREEN_TEMP_FONT_SP - 0.01f,
                                    onDecrease = {
                                        onAdjustFullScreenTempFontSize(-UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    },
                                    onIncrease = {
                                        onAdjustFullScreenTempFontSize(UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Font Style") {
                                CompactSegmentedControl(
                                    options = DisplayFontStyle.entries.map { style ->
                                        style.label to (preferences.fullScreenTempFontStyle == style)
                                    },
                                    compactItems = true,
                                    onSelectIndex = { idx ->
                                        DisplayFontStyle.entries.getOrNull(idx)?.let(onFullScreenTempFontStyleSelect)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Color") {
                                ColorWheelPickerButton(
                                    selectedArgb = preferences.fullScreenTempTextColorArgb,
                                    dialogTitle = "Full Screen Temperature Color:",
                                    onColorSelected = onFullScreenTempTextColorSelect
                                )
                            }
                        }

                        SettingsGroupCard(title = "FULL SCREEN · TOP TIME & DATE", hairline = hairline) {
                            CompactSettingRow(label = "Show Time") {
                                CompactSegmentedControl(
                                    options = listOf(
                                        "Off" to !preferences.fullScreenShowTimeDisplay,
                                        "On" to preferences.fullScreenShowTimeDisplay
                                    ),
                                    onSelectIndex = { idx ->
                                        onFullScreenShowTimeSelect(idx == 1)
                                    }
                                )
                            }

                            CompactSettingRow(label = "Date Display") {
                                DatePositionDropdownSelector(
                                    selected = preferences.fullScreenDateDisplayPosition,
                                    onSelect = onFullScreenDatePositionSelect
                                )
                            }

                            AnimatedVisibility(visible = preferences.fullScreenShowTimeDisplay) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CompactSettingRow(label = "Font Size") {
                                        FontSizeStepper(
                                            currentSp = preferences.fullScreenTimeFontSizeSp,
                                            canDecrease = preferences.fullScreenTimeFontSizeSp > UserPreferencesRepository.MIN_TIME_FONT_SP + 0.01f,
                                            canIncrease = preferences.fullScreenTimeFontSizeSp < UserPreferencesRepository.MAX_TIME_FONT_SP - 0.01f,
                                            onDecrease = {
                                                onAdjustFullScreenTimeFontSize(-UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                            },
                                            onIncrease = {
                                                onAdjustFullScreenTimeFontSize(UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                            }
                                        )
                                    }
                                    CompactSettingRow(label = "Font Style") {
                                        CompactSegmentedControl(
                                            options = DisplayFontStyle.entries.map { style ->
                                                style.label to (preferences.fullScreenTimeFontStyle == style)
                                            },
                                            compactItems = true,
                                            onSelectIndex = { idx ->
                                                DisplayFontStyle.entries.getOrNull(idx)?.let(onFullScreenTimeFontStyleSelect)
                                            }
                                        )
                                    }
                                    CompactSettingRow(label = "Color") {
                                        ColorWheelPickerButton(
                                            selectedArgb = preferences.fullScreenTimeTextColorArgb,
                                            dialogTitle = "Full Screen Time Color:",
                                            onColorSelected = onFullScreenTimeTextColorSelect
                                        )
                                    }
                                }
                            }
                        }

                        // ============================================================
                        // SECTION B: WIDGET DISPLAY
                        // ============================================================
                        Spacer(modifier = Modifier.height(2.dp))
                        SettingsDisplaySectionBanner(
                            title = "WIDGET DISPLAY",
                            subtitle = "Home screen widget 10-day slider, data & colors",
                            icon = Icons.Default.Widgets,
                            accentColor = MaterialTheme.colorScheme.secondary
                        )

                        SettingsGroupCard(title = "WIDGET · 10-DAY SLIDER & DATA", hairline = hairline) {
                            CompactSettingRow(label = "Font Size") {
                                FontSizeStepper(
                                    currentSp = preferences.widgetDataFontSizeSp,
                                    canDecrease = preferences.widgetDataFontSizeSp > UserPreferencesRepository.MIN_WIDGET_DATA_FONT_SP + 0.01f,
                                    canIncrease = preferences.widgetDataFontSizeSp < UserPreferencesRepository.MAX_WIDGET_DATA_FONT_SP - 0.01f,
                                    onDecrease = {
                                        onAdjustWidgetDataFontSize(-UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    },
                                    onIncrease = {
                                        onAdjustWidgetDataFontSize(UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Slider Backing") {
                                CompactSegmentedControl(
                                    options = BackingTone.entries.map { tone ->
                                        tone.label to (preferences.widgetSliderBackingTone == tone)
                                    },
                                    compactItems = true,
                                    onSelectIndex = { idx ->
                                        BackingTone.entries.getOrNull(idx)?.let(onWidgetSliderBackingToneSelect)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Transparency") {
                                val wTrDelta = preferences.widgetSliderTransparencyDeltaPct
                                val wTrLabel = when {
                                    wTrDelta == 0 -> "Default"
                                    wTrDelta > 0 -> "+${wTrDelta}%"
                                    else -> "${wTrDelta}%"
                                }
                                FontBoostStepper(
                                    currentLabel = wTrLabel,
                                    canDecrease = wTrDelta > UserPreferencesRepository.MIN_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT,
                                    canIncrease = wTrDelta < UserPreferencesRepository.MAX_WIDGET_SLIDER_TRANSPARENCY_DELTA_PCT,
                                    onDecrease = {
                                        onAdjustWidgetSliderTransparency(-UserPreferencesRepository.WIDGET_SLIDER_TRANSPARENCY_STEP_PCT)
                                    },
                                    onIncrease = {
                                        onAdjustWidgetSliderTransparency(UserPreferencesRepository.WIDGET_SLIDER_TRANSPARENCY_STEP_PCT)
                                    }
                                )
                            }
                        }

                        SettingsGroupCard(title = "WIDGET · TEMPERATURE", hairline = hairline) {
                            CompactSettingRow(label = "Font Size") {
                                FontSizeStepper(
                                    currentSp = preferences.widgetTempFontSizeSp,
                                    canDecrease = preferences.widgetTempFontSizeSp > UserPreferencesRepository.MIN_WIDGET_TEMP_FONT_SP + 0.01f,
                                    canIncrease = preferences.widgetTempFontSizeSp < UserPreferencesRepository.MAX_WIDGET_TEMP_FONT_SP - 0.01f,
                                    onDecrease = {
                                        onAdjustWidgetTempFontSize(-UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    },
                                    onIncrease = {
                                        onAdjustWidgetTempFontSize(UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Font Style") {
                                CompactSegmentedControl(
                                    options = DisplayFontStyle.entries.map { style ->
                                        style.label to (preferences.widgetTempFontStyle == style)
                                    },
                                    compactItems = true,
                                    onSelectIndex = { idx ->
                                        DisplayFontStyle.entries.getOrNull(idx)?.let(onWidgetTempFontStyleSelect)
                                    }
                                )
                            }
                            CompactSettingRow(label = "Color") {
                                ColorWheelPickerButton(
                                    selectedArgb = preferences.widgetTempTextColorArgb,
                                    dialogTitle = "Widget Temperature Color:",
                                    onColorSelected = onWidgetTempTextColorSelect
                                )
                            }
                        }

                        SettingsGroupCard(title = "WIDGET · TOP TIME & DATE", hairline = hairline) {
                            CompactSettingRow(label = "Show Time") {
                                CompactSegmentedControl(
                                    options = listOf(
                                        "Off" to !preferences.widgetShowTimeDisplay,
                                        "On" to preferences.widgetShowTimeDisplay
                                    ),
                                    onSelectIndex = { idx ->
                                        onWidgetShowTimeSelect(idx == 1)
                                    }
                                )
                            }

                            CompactSettingRow(label = "Date Display") {
                                DatePositionDropdownSelector(
                                    selected = preferences.widgetDateDisplayPosition,
                                    onSelect = onWidgetDatePositionSelect
                                )
                            }

                            AnimatedVisibility(visible = preferences.widgetShowTimeDisplay) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    CompactSettingRow(label = "Font Size") {
                                        FontSizeStepper(
                                            currentSp = preferences.widgetTimeFontSizeSp,
                                            canDecrease = preferences.widgetTimeFontSizeSp > UserPreferencesRepository.MIN_TIME_FONT_SP + 0.01f,
                                            canIncrease = preferences.widgetTimeFontSizeSp < UserPreferencesRepository.MAX_TIME_FONT_SP - 0.01f,
                                            onDecrease = {
                                                onAdjustWidgetTimeFontSize(-UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                            },
                                            onIncrease = {
                                                onAdjustWidgetTimeFontSize(UserPreferencesRepository.FONT_SIZE_STEP_SP)
                                            }
                                        )
                                    }
                                    CompactSettingRow(label = "Font Style") {
                                        CompactSegmentedControl(
                                            options = DisplayFontStyle.entries.map { style ->
                                                style.label to (preferences.widgetTimeFontStyle == style)
                                            },
                                            compactItems = true,
                                            onSelectIndex = { idx ->
                                                DisplayFontStyle.entries.getOrNull(idx)?.let(onWidgetTimeFontStyleSelect)
                                            }
                                        )
                                    }
                                    CompactSettingRow(label = "Color") {
                                        ColorWheelPickerButton(
                                            selectedArgb = preferences.widgetTimeTextColorArgb,
                                            dialogTitle = "Widget Time Color:",
                                            onColorSelected = onWidgetTimeTextColorSelect
                                        )
                                    }
                                }
                            }
                        }

                        // Group 4: Network, Privacy & Attribution
                        SettingsGroupCard(title = "NETWORK & PRIVACY", hairline = hairline) {
                            SingleLineText(
                                text = "HTTPS Gateway Override (Optional)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val inputBorderColor = if (gatewayError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                hairline
                            }
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                    .border(1.dp, inputBorderColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (gatewayInput.isEmpty()) {
                                    SingleLineText(
                                        text = "https://api.open-meteo.com/v1/forecast",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                                }
                                BasicTextField(
                                    value = gatewayInput,
                                    onValueChange = { raw ->
                                        gatewayInput = raw
                                            .replace(Regex("[\\p{Cntrl}\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]"), "")
                                            .take(InputSecurityValidator.MAX_GATEWAY_URL_LENGTH)
                                        gatewayError = null
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (gatewayError != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = gatewayError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = hairline)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp)
                                    .clickable { showFullPrivacyPolicy = !showFullPrivacyPolicy }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                SingleLineText(
                                    text = "Privacy Policy & Data Safety",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showFullPrivacyPolicy) {
                                        Icons.Default.ExpandLess
                                    } else {
                                        Icons.Default.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            AnimatedVisibility(visible = showFullPrivacyPolicy) {
                                Text(
                                    text = "• Zero Accounts, Ads, or Tracking: Collects no personal identifiers, advertising IDs, or analytics.\n" +
                                        "• Foreground-Only Location: GPS is requested only when tapped while open, never in the background, and normalized to ~2 km cache cells over TLS 1.2/1.3.\n" +
                                        "• Local Storage: Saved locations stay on-device (excluded from cloud backup) and can be deleted anytime.\n" +
                                        "• Providers: Open-Meteo.com (CC BY 4.0) · NOAA/NWS · Zippopotam.us.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = hairline)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        runCatching { uriHandler.openUri("https://crowinnovations.com") }
                                    }
                                    .padding(top = 5.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SingleLineText(
                                            text = "Brought to you by ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        SingleLineText(
                                            text = "https://crowinnovations.com",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open website",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    SingleLineText(
                                        text = "Open-Meteo · NOAA/NWS · 15m auto-sync",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                    )
                                }
                            }
                        }
                    }

                    // Pinned Action Footer — always visible well above Android's navigation/back bar
                    HorizontalDivider(thickness = 0.5.dp, color = hairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleLineText(
                            text = "Version ${com.crowinnovations.skypulseweather.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            SingleLineText(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                when (val res = InputSecurityValidator.validateHttpsGatewayUrl(gatewayInput)) {
                                    is InputSecurityValidator.GatewayValidationResult.Valid -> {
                                        onGatewaySave(res.normalizedUrl)
                                        onDismiss()
                                    }
                                    is InputSecurityValidator.GatewayValidationResult.Invalid -> {
                                        gatewayError = res.userMessage
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            SingleLineText(
                                text = "Done",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDisplaySectionBanner(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.48f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            SingleLineText(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold
            )
            SingleLineText(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    hairline: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(0.75.dp, hairline, shape)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        SingleLineText(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        content()
    }
}

@Composable
private fun CompactSettingRow(
    label: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 33.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoFitSingleLineText(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            maxFontSize = 13.sp,
            minFontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        control()
    }
}

@Composable
private fun CompactSegmentedControl(
    options: List<Pair<String, Boolean>>,
    compactItems: Boolean = false,
    onSelectIndex: (Int) -> Unit
) {
    val outerShape = RoundedCornerShape(8.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    Row(
        modifier = Modifier
            .clip(outerShape)
            .border(width = 0.8.dp, color = borderColor, shape = outerShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        options.forEachIndexed { index, (label, selected) ->
            val segShape = RoundedCornerShape(6.5.dp)
            Box(
                modifier = Modifier
                    .heightIn(min = 25.dp)
                    .widthIn(min = if (compactItems) 32.dp else 42.dp)
                    .clip(segShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelectIndex(index) }
                    .padding(horizontal = if (compactItems) 6.dp else 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                SingleLineText(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

internal data class HexColorCell(
    val q: Int,
    val r: Int,
    val unitX: Float,
    val unitY: Float,
    val argb: Int,
    val isGrayscaleStrip: Boolean = false
)

internal object HoneycombColorWheel {
    const val RADIUS = 6
    private val SQRT_3 = sqrt(3f)

    val wheelCells: List<HexColorCell> by lazy {
        val list = ArrayList<HexColorCell>(127)
        for (r in -RADIUS..RADIUS) {
            val qMin = maxOf(-RADIUS, -r - RADIUS)
            val qMax = minOf(RADIUS, -r + RADIUS)
            for (q in qMin..qMax) {
                val ux = SQRT_3 * (q + r * 0.5f)
                val uy = 1.5f * r
                val argb = if (q == 0 && r == 0) {
                    0xFFFFFFFF.toInt()
                } else {
                    val angleDeg = Math.toDegrees(atan2(uy.toDouble(), ux.toDouble())).toFloat()
                    val hue = ((angleDeg - 60f) % 360f + 360f) % 360f
                    val ring = maxOf(abs(q), abs(r), abs(q + r)).toFloat() / RADIUS.toFloat()
                    val radial = (hypot(ux, uy) / (RADIUS * SQRT_3)).safeClamp(0f, 1f)
                    val t = (0.55f * radial + 0.45f * ring).safeClamp(0.05f, 1f)
                    val (sat, light) = if (t <= 0.56f) {
                        val p = (t / 0.56f).safeClamp(0f, 1f)
                        1.0f to (0.94f - 0.44f * p)
                    } else {
                        val p = ((t - 0.56f) / 0.44f).safeClamp(0f, 1f)
                        (1.0f - 0.08f * p) to (0.50f - 0.30f * p)
                    }
                    hslToArgb(hue, sat, light)
                }
                list.add(HexColorCell(q = q, r = r, unitX = ux, unitY = uy, argb = argb))
            }
        }
        list
    }

    val grayscaleCells: List<HexColorCell> by lazy {
        val grays = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFE2E8F0.toInt(),
            0xFFCBD5E1.toInt(),
            0xFF94A3B8.toInt(),
            0xFF64748B.toInt(),
            0xFF475569.toInt(),
            0xFF334155.toInt(),
            0xFF1E293B.toInt(),
            0xFF000000.toInt()
        )
        val mid = (grays.size - 1) / 2f
        val rowY = 1.5f * RADIUS + 2.35f
        grays.mapIndexed { idx, argb ->
            HexColorCell(
                q = idx - 4,
                r = RADIUS + 2,
                unitX = (idx - mid) * SQRT_3 * 1.06f,
                unitY = rowY,
                argb = argb,
                isGrayscaleStrip = true
            )
        }
    }

    val allCells: List<HexColorCell> by lazy { wheelCells + grayscaleCells }

    fun findClosestCellIndex(targetArgb: Int): Int {
        val tr = (targetArgb shr 16) and 0xFF
        val tg = (targetArgb shr 8) and 0xFF
        val tb = targetArgb and 0xFF
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        allCells.forEachIndexed { idx, cell ->
            val cr = (cell.argb shr 16) and 0xFF
            val cg = (cell.argb shr 8) and 0xFF
            val cb = cell.argb and 0xFF
            val dr = tr - cr
            val dg = tg - cg
            val db = tb - cb
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = idx
            }
        }
        return bestIdx
    }

    fun formatHex(argb: Int): String {
        val rgb = argb and 0xFFFFFF
        return String.format("#%06X", rgb)
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c * 0.5f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().safeClamp(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().safeClamp(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().safeClamp(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

@Composable
private fun ColorWheelPickerButton(
    selectedArgb: Int,
    dialogTitle: String,
    onColorSelected: (Int) -> Unit
) {
    var showWheelDialog by rememberSaveable { mutableStateOf(false) }
    val safeArgb = selectedArgb or 0xFF000000.toInt()
    val swatchColor = Color(safeArgb)
    val pillShape = RoundedCornerShape(8.dp)
    val hexText = HoneycombColorWheel.formatHex(safeArgb)

    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = pillShape
            )
            .clickable { showWheelDialog = true }
            .heightIn(min = 27.dp)
            .padding(start = 7.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(1.dp, Color(0x880F172A), CircleShape)
        )
        SingleLineText(
            text = hexText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "Open color wheel",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showWheelDialog) {
        HoneycombColorPickerDialog(
            title = dialogTitle,
            initialArgb = safeArgb,
            onDismiss = { showWheelDialog = false },
            onSelectColor = { chosenArgb ->
                onColorSelected(chosenArgb)
                showWheelDialog = false
            }
        )
    }
}

@Composable
private fun HoneycombColorPickerDialog(
    title: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onSelectColor: (Int) -> Unit
) {
    val cells = remember { HoneycombColorWheel.allCells }
    var selectedCellIdx by rememberSaveable(initialArgb) {
        mutableIntStateOf(HoneycombColorWheel.findClosestCellIndex(initialArgb))
    }
    var draftArgb by rememberSaveable(initialArgb) {
        mutableIntStateOf(initialArgb or 0xFF000000.toInt())
    }
    val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .widthIn(max = 356.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, hairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SingleLineText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.02f)
                ) {
                    val hexPath = remember { Path() }

                    fun buildHexPolygon(path: Path, cx: Float, cy: Float, radius: Float) {
                        path.reset()
                        for (i in 0 until 6) {
                            val angleRad = Math.toRadians((60.0 * i - 30.0))
                            val vx = cx + radius * cos(angleRad).toFloat()
                            val vy = cy + radius * sin(angleRad).toFloat()
                            if (i == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
                        }
                        path.close()
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cells) {
                                fun pickAt(offset: Offset) {
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    if (w <= 0f || h <= 0f) return
                                    val cellR = min(w / 23.4f, h / 23.6f)
                                    val originX = w * 0.5f
                                    val originY = h * 0.455f
                                    var nearestIdx = -1
                                    var nearestDistSq = Float.MAX_VALUE
                                    val maxHitDistSq = (cellR * 1.25f) * (cellR * 1.25f)
                                    cells.forEachIndexed { idx, cell ->
                                        val cx = originX + cell.unitX * cellR
                                        val cy = originY + cell.unitY * cellR
                                        val dx = offset.x - cx
                                        val dy = offset.y - cy
                                        val d2 = dx * dx + dy * dy
                                        if (d2 < nearestDistSq && d2 <= maxHitDistSq) {
                                            nearestDistSq = d2
                                            nearestIdx = idx
                                        }
                                    }
                                    if (nearestIdx >= 0) {
                                        selectedCellIdx = nearestIdx
                                        draftArgb = cells[nearestIdx].argb
                                    }
                                }

                                detectTapGestures { pos -> pickAt(pos) }
                            }
                            .pointerInput(cells) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        val cellR = min(w / 23.4f, h / 23.6f)
                                        val originX = w * 0.5f
                                        val originY = h * 0.455f
                                        var nearestIdx = -1
                                        var nearestDistSq = Float.MAX_VALUE
                                        val maxHitDistSq = (cellR * 1.35f) * (cellR * 1.35f)
                                        cells.forEachIndexed { idx, cell ->
                                            val cx = originX + cell.unitX * cellR
                                            val cy = originY + cell.unitY * cellR
                                            val d2 = (pos.x - cx) * (pos.x - cx) + (pos.y - cy) * (pos.y - cy)
                                            if (d2 < nearestDistSq && d2 <= maxHitDistSq) {
                                                nearestDistSq = d2
                                                nearestIdx = idx
                                            }
                                        }
                                        if (nearestIdx >= 0) {
                                            selectedCellIdx = nearestIdx
                                            draftArgb = cells[nearestIdx].argb
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        val cellR = min(w / 23.4f, h / 23.6f)
                                        val originX = w * 0.5f
                                        val originY = h * 0.455f
                                        var nearestIdx = -1
                                        var nearestDistSq = Float.MAX_VALUE
                                        val maxHitDistSq = (cellR * 1.35f) * (cellR * 1.35f)
                                        cells.forEachIndexed { idx, cell ->
                                            val cx = originX + cell.unitX * cellR
                                            val cy = originY + cell.unitY * cellR
                                            val d2 = (pos.x - cx) * (pos.x - cx) + (pos.y - cy) * (pos.y - cy)
                                            if (d2 < nearestDistSq && d2 <= maxHitDistSq) {
                                                nearestDistSq = d2
                                                nearestIdx = idx
                                            }
                                        }
                                        if (nearestIdx >= 0) {
                                            selectedCellIdx = nearestIdx
                                            draftArgb = cells[nearestIdx].argb
                                        }
                                    }
                                )
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) return@Canvas
                        val cellR = min(w / 23.4f, h / 23.6f)
                        val fillR = cellR * 1.035f
                        val originX = w * 0.5f
                        val originY = h * 0.455f

                        cells.forEach { cell ->
                            val cx = originX + cell.unitX * cellR
                            val cy = originY + cell.unitY * cellR
                            buildHexPolygon(hexPath, cx, cy, if (cell.isGrayscaleStrip) cellR * 0.96f else fillR)
                            drawPath(path = hexPath, color = Color(cell.argb))
                            if (cell.isGrayscaleStrip) {
                                drawPath(
                                    path = hexPath,
                                    color = Color(0x440F172A),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        // Selected hexagon double black + white ring indicator (matching reference wheel)
                        val activeCell = cells.getOrNull(selectedCellIdx)
                        if (activeCell != null) {
                            val sx = originX + activeCell.unitX * cellR
                            val sy = originY + activeCell.unitY * cellR
                            buildHexPolygon(hexPath, sx, sy, cellR * 1.14f)
                            drawPath(
                                path = hexPath,
                                color = Color.Black,
                                style = Stroke(
                                    width = 3.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            buildHexPolygon(hexPath, sx, sy, cellR * 1.02f)
                            drawPath(
                                path = hexPath,
                                color = Color.White,
                                style = Stroke(
                                    width = 2.0.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(draftArgb))
                            .border(1.2.dp, hairline, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SingleLineText(
                        text = HoneycombColorWheel.formatHex(draftArgb),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        SingleLineText(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { onSelectColor(draftArgb) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        SingleLineText(
                            text = "Select",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

internal fun formatFontSpLabel(sp: Float): String {
    val twice = (sp * 2f).roundToInt()
    return if (twice % 2 == 0) {
        "${twice / 2}sp"
    } else {
        String.format(java.util.Locale.US, "%.1fsp", twice / 2f)
    }
}

@Composable
private fun FontSizeStepper(
    currentSp: Float,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    FontBoostStepper(
        currentLabel = formatFontSpLabel(currentSp),
        canDecrease = canDecrease,
        canIncrease = canIncrease,
        onDecrease = onDecrease,
        onIncrease = onIncrease
    )
}

@Composable
private fun FontBoostStepper(
    currentLabel: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    val outerShape = RoundedCornerShape(8.dp)
    val btnShape = RoundedCornerShape(6.5.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .clip(outerShape)
            .border(width = 0.8.dp, color = borderColor, shape = outerShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 25.dp)
                .widthIn(min = 30.dp)
                .clip(btnShape)
                .background(
                    if (canDecrease) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(enabled = canDecrease, onClick = onDecrease)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            SingleLineText(
                text = "−",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (canDecrease) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                textAlign = TextAlign.Center
            )
        }

        SingleLineText(
            text = currentLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 52.dp)
        )

        Box(
            modifier = Modifier
                .heightIn(min = 25.dp)
                .widthIn(min = 30.dp)
                .clip(btnShape)
                .background(
                    if (canIncrease) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(enabled = canIncrease, onClick = onIncrease)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            SingleLineText(
                text = "+",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (canIncrease) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DatePositionDropdownSelector(
    selected: DateDisplayPosition,
    onSelect: (DateDisplayPosition) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(8.dp)
    val isActive = selected != DateDisplayPosition.OFF

    Box {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    if (isActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                )
                .border(
                    width = 0.75.dp,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    },
                    shape = pillShape
                )
                .clickable { expanded = true }
                .heightIn(min = 26.dp)
                .padding(start = 11.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SingleLineText(
                text = selected.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select date display position",
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DateDisplayPosition.entries.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        SingleLineText(
                            text = option.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
