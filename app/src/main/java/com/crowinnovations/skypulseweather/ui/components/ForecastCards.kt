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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowinnovations.skypulseweather.domain.DailyForecastItem
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
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.cardSurface)
            .border(width = 1.dp, color = palette.cardBorder, shape = shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun HeroWeatherSection(
    snapshot: WeatherSnapshot,
    tempUnit: TempUnit,
    windUnit: WindUnit = WindUnit.MPH,
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // Reserve right 40% in portrait so the giant upper-right 3D Moon shines completely unobstructed
        val rightStageWidth = if (reserveRightCelestialStage) {
            (maxWidth * 0.40f).coerceAtLeast(110.dp)
        } else {
            8.dp
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // 100% larger hero temperature degrees (up to 142.sp, auto-fitted safely)
                    AutoFitSingleLineText(
                        text = currentFormatted,
                        color = Color(0xFFF8FAFC),
                        maxFontSize = 142.sp,
                        minFontSize = 68.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.displayLarge.copy(shadow = heroShadow),
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
    modifier: Modifier = Modifier
) {
    if (hourly.isEmpty()) return

    GlassCard(
        palette = palette,
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
                    tint = palette.secondaryText,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                SingleLineText(
                    text = "HOURLY FORECAST",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SingleLineText(
                    text = "Next 24h",
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelSmall
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
                            .widthIn(min = 60.dp)
                            .heightIn(min = 78.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.cardBorder.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        SingleLineText(
                            text = item.displayHour,
                            color = palette.primaryText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (item.displayHour == "Now") FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        WeatherIllustration(
                            condition = item.condition,
                            isDay = item.isDay,
                            size = 27.dp
                        )

                        SingleLineText(
                            text = if (item.precipProbability > 0) "${item.precipProbability}%" else " ",
                            color = palette.accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        SingleLineText(
                            text = tempUnit.format(item.tempCelsius),
                            color = palette.primaryText,
                            style = MaterialTheme.typography.titleSmall,
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
    modifier: Modifier = Modifier
) {
    val tenDays = daily.take(10)
    if (tenDays.isEmpty()) return

    val overallMin = tenDays.minOf { it.lowCelsius }
    val overallMax = tenDays.maxOf { it.highCelsius }
    val safeSpan = (overallMax - overallMin).coerceAtLeast(1.0)
    val fontScaleFactor = LocalDensity.current.fontScale.safeClamp(1f, 1.35f)
    val hasAnyHighPrecipDay = tenDays.any { it.showRainPercentBesideCloud }
    val headerLabel = if (tenDays.size > 5) "${tenDays.size}-DAY FORECAST" else "5-DAY FORECAST"

    GlassCard(
        palette = palette,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = palette.secondaryText,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                SingleLineText(
                    text = headerLabel,
                    color = palette.secondaryText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SingleLineText(
                    text = "Open-Meteo · NWS",
                    color = palette.secondaryText.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            tenDays.forEachIndexed { idx, day ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = palette.cardBorder.copy(alpha = 0.30f)
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
                        .heightIn(min = 34.dp)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AutoFitSingleLineText(
                        text = day.monthDayWeekLabel,
                        color = palette.primaryText,
                        maxFontSize = 11.8.sp,
                        minFontSize = 8.5.sp,
                        fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width((68f * fontScaleFactor).dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Row(
                        modifier = if (hasAnyHighPrecipDay) {
                            Modifier.width((58f * fontScaleFactor).dp)
                        } else {
                            Modifier.width(28.dp)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeatherIllustration(
                            condition = day.condition,
                            size = 24.dp
                        )
                        if (day.showRainPercentBesideCloud) {
                            Spacer(modifier = Modifier.width(3.dp))
                            SingleLineText(
                                text = "${day.precipProbability.safeClamp(0, 100)}%",
                                color = palette.accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                    ) {
                        val trackW = size.width
                        val trackH = size.height
                        if (trackW > 0f && trackH > 0f) {
                            val r = CornerRadius(trackH / 2f, trackH / 2f)
                            drawRoundRect(
                                color = palette.secondaryText.copy(alpha = 0.22f),
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

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.widthIn(min = (86f * fontScaleFactor).dp)
                    ) {
                        SingleLineText(
                            text = "${hiVal}° / ${loVal}°",
                            color = palette.primaryText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        SingleLineText(
                            text = "Precip:${day.precipProbability.safeClamp(0, 100)}%",
                            color = palette.accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
