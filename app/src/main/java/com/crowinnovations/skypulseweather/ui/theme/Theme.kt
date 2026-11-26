package com.crowinnovations.skypulseweather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crowinnovations.skypulseweather.domain.ThemeMode
import com.crowinnovations.skypulseweather.domain.WeatherCondition

data class SkyPalette(
    val gradientTop: Color,
    val gradientMid: Color,
    val gradientBottom: Color,
    val scrimOverlay: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF06162E),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    secondaryContainer = Color(0xFF172B44),
    background = Color(0xFF091222),
    surface = Color(0xFF111D33),
    surfaceVariant = Color(0xFF1B2B46),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0284C7),
    secondaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFFF1F6FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EEF8),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun rememberIsDarkTheme(themeMode: ThemeMode): Boolean {
    val sysDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> sysDark
    }
}

@Composable
fun SkyPulseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = rememberIsDarkTheme(themeMode)
    MaterialTheme(
        colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
        content = content
    )
}

/**
 * Calibrates glassmorphic card contrast & accents over the rich 3D weather scene photos
 * in both Dark and Light appearance modes.
 */
fun skyPaletteFor(condition: WeatherCondition, isDarkTheme: Boolean): SkyPalette {
    return if (isDarkTheme) {
        when (condition) {
            WeatherCondition.CLEAR_DAY, WeatherCondition.PARTLY_CLOUDY_DAY -> SkyPalette(
                gradientTop = Color(0xFF0C2D59),
                gradientMid = Color(0xFF19508C),
                gradientBottom = Color(0xFF0F2342),
                scrimOverlay = Color(0x42061020),
                cardSurface = Color(0x8C081529),
                cardBorder = Color(0x48FFFFFF),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFFDCE7F5),
                accent = Color(0xFFFBBF24)
            )
            WeatherCondition.THUNDERSTORM -> SkyPalette(
                gradientTop = Color(0xFF0B0E22),
                gradientMid = Color(0xFF1E1D42),
                gradientBottom = Color(0xFF0E1328),
                scrimOverlay = Color(0x36050814),
                cardSurface = Color(0x94090F22),
                cardBorder = Color(0x52A78BFA),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFFE2E8F0),
                accent = Color(0xFF38BDF8)
            )
            WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> SkyPalette(
                gradientTop = Color(0xFF0A1D2E),
                gradientMid = Color(0xFF15364F),
                gradientBottom = Color(0xFF0C1E30),
                scrimOverlay = Color(0x3805101C),
                cardSurface = Color(0x90081626),
                cardBorder = Color(0x457DD3FC),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFFD8E6F3),
                accent = Color(0xFF38BDF8)
            )
            WeatherCondition.SNOW -> SkyPalette(
                gradientTop = Color(0xFF162B4A),
                gradientMid = Color(0xFF28466E),
                gradientBottom = Color(0xFF162844),
                scrimOverlay = Color(0x3D081428),
                cardSurface = Color(0x8F0C1B33),
                cardBorder = Color(0x4DE2E8F0),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFFE2ECF7),
                accent = Color(0xFF7DD3FC)
            )
            else -> SkyPalette(
                gradientTop = Color(0xFF081226),
                gradientMid = Color(0xFF162847),
                gradientBottom = Color(0xFF0E1B33),
                scrimOverlay = Color(0x36050B18),
                cardSurface = Color(0x8F091426),
                cardBorder = Color(0x42FFFFFF),
                primaryText = Color(0xFFF8FAFC),
                secondaryText = Color(0xFFD8E3F0),
                accent = Color(0xFF7DD3FC)
            )
        }
    } else {
        // Light mode: rich colorful 3D sky photos remain vivid while frosted pearl cards stay crisp & readable
        when (condition) {
            WeatherCondition.CLEAR_DAY, WeatherCondition.PARTLY_CLOUDY_DAY -> SkyPalette(
                gradientTop = Color(0xFF3898EC),
                gradientMid = Color(0xFF7CC4FA),
                gradientBottom = Color(0xFFDDF0FF),
                scrimOverlay = Color(0x1406152B),
                cardSurface = Color(0xD9FFFFFF),
                cardBorder = Color(0xF2FFFFFF),
                primaryText = Color(0xFF0B1324),
                secondaryText = Color(0xFF334155),
                accent = Color(0xFFD97706)
            )
            WeatherCondition.THUNDERSTORM -> SkyPalette(
                gradientTop = Color(0xFF1E2246),
                gradientMid = Color(0xFF37386B),
                gradientBottom = Color(0xFF232946),
                scrimOverlay = Color(0x1F050814),
                cardSurface = Color(0xDEFFFFFF),
                cardBorder = Color(0xF0FFFFFF),
                primaryText = Color(0xFF0B1324),
                secondaryText = Color(0xFF334155),
                accent = Color(0xFF0284C7)
            )
            else -> SkyPalette(
                gradientTop = Color(0xFF4B79A1),
                gradientMid = Color(0xFF7FA6C9),
                gradientBottom = Color(0xFFDCE8F5),
                scrimOverlay = Color(0x1A061224),
                cardSurface = Color(0xDCFFFFFF),
                cardBorder = Color(0xF2FFFFFF),
                primaryText = Color(0xFF0B1324),
                secondaryText = Color(0xFF334155),
                accent = Color(0xFF0284C7)
            )
        }
    }
}
