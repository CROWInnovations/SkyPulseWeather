package com.crowinnovations.skypulseweather.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowinnovations.skypulseweather.data.local.UserPreferencesRepository
import com.crowinnovations.skypulseweather.domain.BackingTone
import com.crowinnovations.skypulseweather.domain.DailyForecastItem
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.DisplayTextColor
import com.crowinnovations.skypulseweather.domain.HourlyForecastItem
import com.crowinnovations.skypulseweather.domain.LunarPhaseCalculator
import com.crowinnovations.skypulseweather.domain.TempUnit
import com.crowinnovations.skypulseweather.domain.WeatherAlert
import com.crowinnovations.skypulseweather.domain.WeatherSnapshot
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.safeClamp
import com.crowinnovations.skypulseweather.ui.theme.SkyPalette

@Composable
fun GlassCard(
    palette: SkyPalette,
    modifier: Modifier = Modifier,
    surfaceColorOverride: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(surfaceColorOverride ?: palette.cardSurface)
            .border(width = 1.dp, color = palette.cardBorder, shape = shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

internal fun resolveForecastCardSurfaceColor(
    palette: SkyPalette,
    transparencyDeltaPct: Int,
    backingTone: BackingTone = BackingTone.DEFAULT
): Color {
    val baseSurface = when (backingTone) {
        BackingTone.DEFAULT -> palette.cardSurface
        BackingTone.DARKEN -> Color(0xFF040914).copy(
            alpha = (palette.cardSurface.alpha + 0.14f).safeClamp(0.24f, 0.96f)
        )
        BackingTone.LIGHTEN -> Color(0xFFEFF6FF).copy(
            alpha = (palette.cardSurface.alpha * 0.72f).safeClamp(0.20f, 0.88f)
        )
    }
    if (transparencyDeltaPct == 0 && backingTone == BackingTone.DEFAULT) return baseSurface
    val adjustedAlpha = (baseSurface.alpha - (transparencyDeltaPct / 100f)).safeClamp(0.06f, 0.98f)
    return baseSurface.copy(alpha = adjustedAlpha)
}

internal fun resolveEffectiveForecastPalette(
    palette: SkyPalette,
    transparencyDeltaPct: Int,
    backingTone: BackingTone
): SkyPalette {
    val surface = resolveForecastCardSurfaceColor(palette, transparencyDeltaPct, backingTone)
    return when {
        backingTone == BackingTone.LIGHTEN && surface.alpha >= 0.45f -> palette.copy(
            cardSurface = surface,
            cardBorder = Color(0xB3FFFFFF),
            primaryText = Color(0xFF0B1324),
            secondaryText = Color(0xFF334155),
            accent = Color(0xFF0284C7)
        )
        backingTone == BackingTone.DARKEN -> palette.copy(
            cardSurface = surface,
            cardBorder = Color(0x45FFFFFF),
            primaryText = Color(0xFFF8FAFC),
            secondaryText = Color(0xFFDCE7F5),
            accent = Color(0xFF38BDF8)
        )
        else -> palette.copy(cardSurface = surface)
    }
}

@Composable
fun HeroWeatherSection(
    snapshot: WeatherSnapshot,
    tempUnit: TempUnit,
    windUnit: WindUnit = WindUnit.MPH,
    tempFontSizeSp: Int = UserPreferencesRepository.DEFAULT_WIDGET_TEMP_FONT_SP,
    tempFontStyle: DisplayFontStyle = DisplayFontStyle.DEFAULT,
    tempTextColorArgb: Int = DisplayTextColor.WHITE.argb,
    palette: SkyPalette,
    reserveRightCelestialStage: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentFormatted = tempUnit.format(snapshot.currentTempCelsius)
    val feelsFormatted = tempUnit.format(snapshot.feelsLikeCelsius)
    val highFormatted = tempUnit.format(snapshot.highCelsius)
    val lowFormatted = tempUnit.format(snapshot.lowCelsius)
    val safeHumidity = snapshot.humidityPercent.safeClamp(0, 100)
    val moonPhase = remember(snapshot.fetchedAtMillis) {
        LunarPhaseCalculator.calculate()
    }
    val heroShadow = Shadow(
        color = Color(0xC8030813),
        offset = Offset(0f, 3f),
        blurRadius = 14f
    )
    val safeTempPrefSp = tempFontSizeSp.safeClamp(
        UserPreferencesRepository.MIN_WIDGET_TEMP_FONT_SP,
        UserPreferencesRepository.MAX_WIDGET_TEMP_FONT_SP
    )
    val heroMaxSp = (safeTempPrefSp * 2.35f).safeClamp(58f, 176f).sp
    val heroMinSp = (safeTempPrefSp * 0.90f).safeClamp(24f, 64f).sp
    val heroFontFamily = when (tempFontStyle) {
        DisplayFontStyle.SERIF -> FontFamily.Serif
        DisplayFontStyle.MONO -> FontFamily.Monospace
        DisplayFontStyle.DEFAULT, DisplayFontStyle.ITALIC -> FontFamily.SansSerif
    }
    val heroFontStyle = if (tempFontStyle == DisplayFontStyle.ITALIC) {
        FontStyle.Italic
    } else {
        FontStyle.Normal
    }
    val heroTempColor = Color(tempTextColorArgb or 0xFF000000.toInt())

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        val hasUpperRightCelestial = !snapshot.isDay ||
            snapshot.condition == com.crowinnovations.skypulseweather.domain.WeatherCondition.CLEAR_DAY ||
            snapshot.condition == com.crowinnovations.skypulseweather.domain.WeatherCondition.PARTLY_CLOUDY_DAY
        val rightStageWidth = if (reserveRightCelestialStage && hasUpperRightCelestial) {
            (maxWidth * 0.34f).coerceAtLeast(96.dp)
        } else {
            8.dp
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AutoFitSingleLineText(
                        text = currentFormatted,
                        color = heroTempColor,
                        maxFontSize = heroMaxSp,
                        minFontSize = heroMinSp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = heroFontFamily,
                            fontStyle = heroFontStyle,
                            shadow = heroShadow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Outside Humidity displayed directly below the temperature degrees
                    AutoFitSingleLineText(
                        text = "Humidity $safeHumidity%",
                        color = Color(0xFFBAE6FD),
                        maxFontSize = 19.sp,
                        minFontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium.copy(shadow = heroShadow),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(rightStageWidth))
            }

            Spacer(modifier = Modifier.height(4.dp))

            AutoFitSingleLineText(
                text = "${snapshot.conditionLabel}  ·  Feels like $feelsFormatted  ·  H: $highFormatted / L: $lowFormatted",
                color = Color(0xFFF8FAFC),
                maxFontSize = 15.sp,
                minFontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall.copy(shadow = heroShadow),
                modifier = Modifier.fillMaxWidth()
            )

            val windAndUvLine = snapshot.formatWindAndUvLine(windUnit = windUnit, compact = false)
            if (windAndUvLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                AutoFitSingleLineText(
                    text = windAndUvLine,
                    color = Color(0xFFE2E8F0),
                    maxFontSize = 13.5.sp,
                    minFontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge.copy(shadow = heroShadow),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!snapshot.isDay) {
                Spacer(modifier = Modifier.height(2.dp))
                AutoFitSingleLineText(
                    text = "${moonPhase.phaseName} · ${moonPhase.illuminationPercent}% illuminated",
                    color = Color(0xFFBAE6FD),
                    maxFontSize = 13.sp,
                    minFontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = heroShadow),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SevereAlertsBanner(
    alerts: List<WeatherAlert>,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val primaryAlert = alerts.first()
    val extraCount = alerts.size - 1
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xD9451A03))
            .border(1.dp, Color(0x99F59E0B), shape)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Weather Alert",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            SingleLineText(
                text = if (extraCount > 0) {
                    "${primaryAlert.event} (+$extraCount more)"
                } else {
                    "${primaryAlert.event} · ${primaryAlert.severity}"
                },
                color = Color(0xFFFEF3C7),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse alert" else "Expand alert",
                tint = Color(0xFFFDE68A)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                alerts.forEachIndexed { idx, alert ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0x44FDE68A)
                        )
                    }
                    Text(
                        text = alert.headline,
                        color = Color(0xFFFDE68A),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (alert.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alert.description.trim(),
                            color = Color(0xFFFEF3C7),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (alert.instruction.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Action: ${alert.instruction.trim()}",
                            color = Color(0xFFFCD34D),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyForecastCard(
    hourly: List<HourlyForecastItem>,
    tempUnit: TempUnit,
    palette: SkyPalette,
    transparencyDeltaPct: Int = 0,
    backingTone: BackingTone = BackingTone.DEFAULT,
    fontBoostSp: Int = 0,
    modifier: Modifier = Modifier
) {
    if (hourly.isEmpty()) return

    val safeBoost = fontBoostSp.safeClamp(
        UserPreferencesRepository.MIN_FORECAST_FONT_BOOST_SP,
        UserPreferencesRepository.MAX_FORECAST_FONT_BOOST_SP
    )
    val effPalette = resolveEffectiveForecastPalette(palette, transparencyDeltaPct, backingTone)
    val pillAlpha = if (transparencyDeltaPct < 0) 0.22f else 0.14f
    val headerStyle = if (safeBoost == 0) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelMedium.copy(fontSize = (12f + minOf(safeBoost, 4) * 0.45f).sp)
    }
    val subHeaderStyle = if (safeBoost == 0) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelSmall.copy(fontSize = (11f + minOf(safeBoost, 3) * 0.35f).sp)
    }
    val hourTextStyle = if (safeBoost == 0) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelSmall.copy(fontSize = (11f + safeBoost).sp)
    }
    val precipTextStyle = if (safeBoost == 0) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelSmall.copy(fontSize = (11f + safeBoost * 0.8f).sp)
    }
    val tempTextStyle = if (safeBoost == 0) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.titleSmall.copy(fontSize = (14f + safeBoost).sp)
    }

    GlassCard(
        palette = effPalette,
        surfaceColorOverride = effPalette.cardSurface,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = effPalette.secondaryText,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                SingleLineText(
                    text = "HOURLY FORECAST",
                    color = effPalette.secondaryText,
                    style = headerStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SingleLineText(
                    text = "Next 24h",
                    color = effPalette.secondaryText,
                    style = subHeaderStyle
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(hourly, key = { it.isoTime }) { item ->
                    Column(
                        modifier = Modifier
                            .widthIn(min = (60 + safeBoost * 5).dp)
                            .heightIn(min = (78 + safeBoost * 6).dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(effPalette.cardBorder.copy(alpha = pillAlpha))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        SingleLineText(
                            text = item.displayHour,
                            color = effPalette.primaryText,
                            style = hourTextStyle,
                            fontWeight = if (item.displayHour == "Now") FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        WeatherIllustration(
                            condition = item.condition,
                            isDay = item.isDay,
                            size = (27 + safeBoost).dp
                        )

                        SingleLineText(
                            text = if (item.precipProbability > 0) "${item.precipProbability}%" else " ",
                            color = effPalette.accent,
                            style = precipTextStyle,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        SingleLineText(
                            text = tempUnit.format(item.tempCelsius),
                            color = effPalette.primaryText,
                            style = tempTextStyle,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FiveDayForecastCard(
    daily: List<DailyForecastItem>,
    tempUnit: TempUnit,
    palette: SkyPalette,
    transparencyDeltaPct: Int = 0,
    backingTone: BackingTone = BackingTone.DEFAULT,
    fontBoostSp: Int = 0,
    modifier: Modifier = Modifier
) {
    val tenDays = daily.take(10)
    if (tenDays.isEmpty()) return

    val safeBoost = fontBoostSp.safeClamp(
        UserPreferencesRepository.MIN_FORECAST_FONT_BOOST_SP,
        UserPreferencesRepository.MAX_FORECAST_FONT_BOOST_SP
    )
    val effPalette = resolveEffectiveForecastPalette(palette, transparencyDeltaPct, backingTone)
    val overallMin = tenDays.minOf { it.lowCelsius }
    val overallMax = tenDays.maxOf { it.highCelsius }
    val safeSpan = (overallMax - overallMin).coerceAtLeast(1.0)
    val fontScaleFactor = LocalDensity.current.fontScale.safeClamp(1f, 1.35f)
    val hasAnyHighPrecipDay = tenDays.any { it.showRainPercentBesideCloud }
    val headerLabel = if (tenDays.size > 5) "${tenDays.size}-DAY FORECAST" else "5-DAY FORECAST"
    val headerStyle = if (safeBoost == 0) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelMedium.copy(fontSize = (12f + minOf(safeBoost, 4) * 0.4f).sp)
    }
    val subHeaderStyle = if (safeBoost == 0) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelSmall.copy(fontSize = (11f + minOf(safeBoost, 2) * 0.35f).sp)
    }
    val hiLoMaxSp = (14f + safeBoost).sp
    val precipMaxSp = (11f + safeBoost * 0.8f).sp
    val dateMaxSp = (11.8f + safeBoost).sp

    GlassCard(
        palette = effPalette,
        surfaceColorOverride = effPalette.cardSurface,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val rowWidthDp = maxWidth.value.coerceAtLeast(220f)
            val rawDateW = (68f + safeBoost * 4.5f) * fontScaleFactor
            val rawIconW = if (hasAnyHighPrecipDay) {
                (58f + safeBoost * 3.1f) * fontScaleFactor
            } else {
                28f + safeBoost * 0.9f
            }
            val rawRightW = (86f + safeBoost * 4.5f) * fontScaleFactor
            val minBarAndGaps = if (rowWidthDp >= 310f) 34f else 22f
            val maxCombinedCols = (rowWidthDp - minBarAndGaps).coerceAtLeast(180f)
            val rawSum = (rawDateW + rawIconW + rawRightW).coerceAtLeast(1f)
            val colScale = (maxCombinedCols / rawSum).safeClamp(0.45f, 1f)
            val dateColWidth = (rawDateW * colScale).dp
            val iconColWidth = (rawIconW * colScale).dp
            val rightColWidth = (rawRightW * colScale).dp
            val iconSizeDp = ((24f + safeBoost * 0.8f) * colScale.safeClamp(0.75f, 1f)).dp

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = effPalette.secondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SingleLineText(
                        text = headerLabel,
                        color = effPalette.secondaryText,
                        style = headerStyle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    SingleLineText(
                        text = "Open-Meteo · NWS",
                        color = effPalette.secondaryText.copy(alpha = 0.72f),
                        style = subHeaderStyle
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                tenDays.forEachIndexed { idx, day ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = effPalette.cardBorder.copy(alpha = 0.30f)
                        )
                    }

                    val startFraction = ((day.lowCelsius - overallMin) / safeSpan)
                        .toFloat()
                        .safeClamp(0f, 0.92f)
                    val endFraction = ((day.highCelsius - overallMin) / safeSpan)
                        .toFloat()
                        .safeClamp((startFraction + 0.08f).safeClamp(0f, 1f), 1f)

                    val hiVal = tempUnit.convert(day.highCelsius)
                    val loVal = tempUnit.convert(day.lowCelsius)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (34 + safeBoost * 3).dp)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoFitSingleLineText(
                            text = day.monthDayWeekLabel,
                            color = effPalette.primaryText,
                            maxFontSize = dateMaxSp,
                            minFontSize = 8.5.sp,
                            fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(dateColWidth)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Row(
                            modifier = Modifier.width(iconColWidth),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WeatherIllustration(
                                condition = day.condition,
                                size = iconSizeDp
                            )
                            if (day.showRainPercentBesideCloud) {
                                Spacer(modifier = Modifier.width(3.dp))
                                AutoFitSingleLineText(
                                    text = "${day.precipProbability.safeClamp(0, 100)}%",
                                    color = effPalette.accent,
                                    maxFontSize = precipMaxSp,
                                    minFontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height((5f + safeBoost * 0.45f).dp)
                        ) {
                            val trackW = size.width
                            val trackH = size.height
                            if (trackW > 0f && trackH > 0f) {
                                val r = CornerRadius(trackH / 2f, trackH / 2f)
                                drawRoundRect(
                                    color = effPalette.secondaryText.copy(alpha = 0.22f),
                                    size = Size(trackW, trackH),
                                    cornerRadius = r
                                )
                                val barLeft = (trackW * startFraction).safeClamp(0f, trackW)
                                val barRight = (trackW * endFraction).safeClamp(barLeft, trackW)
                                val barWidth = (barRight - barLeft).coerceAtLeast(trackH)
                                    .safeClamp(0f, (trackW - barLeft).coerceAtLeast(0f))
                                if (barWidth > 0f) {
                                    drawRoundRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF38BDF8), Color(0xFFFBBF24)),
                                            startX = barLeft,
                                            endX = barLeft + barWidth
                                        ),
                                        topLeft = Offset(barLeft, 0f),
                                        size = Size(barWidth, trackH),
                                        cornerRadius = r
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(7.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.width(rightColWidth)
                        ) {
                            AutoFitSingleLineText(
                                text = "${hiVal}° / ${loVal}°",
                                color = effPalette.primaryText,
                                maxFontSize = hiLoMaxSp,
                                minFontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.End,
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AutoFitSingleLineText(
                                text = "Precip:${day.precipProbability.safeClamp(0, 100)}%",
                                color = effPalette.accent,
                                maxFontSize = precipMaxSp,
                                minFontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.End,
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
