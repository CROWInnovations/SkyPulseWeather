package com.crowinnovations.skypulseweather.domain

import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Safe range clamping helpers enforcing maxVal >= minVal so narrow viewports,
 * rotated screens, or flat temperature ranges never throw empty-range exceptions.
 */
fun Float.safeClamp(minVal: Float, maxVal: Float): Float {
    val safeMax = maxVal.coerceAtLeast(minVal)
    return this.coerceIn(minVal, safeMax)
}

fun Double.safeClamp(minVal: Double, maxVal: Double): Double {
    val safeMax = maxVal.coerceAtLeast(minVal)
    return this.coerceIn(minVal, safeMax)
}

fun Int.safeClamp(minVal: Int, maxVal: Int): Int {
    val safeMax = maxVal.coerceAtLeast(minVal)
    return this.coerceIn(minVal, safeMax)
}

fun Dp.safeClamp(minVal: Dp, maxVal: Dp): Dp {
    val safeMax = if (maxVal < minVal) minVal else maxVal
    return this.coerceIn(minVal, safeMax)
}

fun normalizeCacheCell(latitude: Double, longitude: Double): String {
    val normLat = (latitude * 50.0).roundToInt() / 50.0
    val normLon = (longitude * 50.0).roundToInt() / 50.0
    return String.format(java.util.Locale.US, "%.2f,%.2f", normLat, normLon)
}
