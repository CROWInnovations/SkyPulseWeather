package com.crowinnovations.skypulseweather.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class MoonPhaseInfo(
    val cycleFraction: Float,         // 0.0 = New, 0.25 = First Quarter, 0.5 = Full, 0.75 = Last Quarter
    val illuminationFraction: Float,  // 0.0 .. 1.0
    val phaseAngleDeg: Float,         // 0 = Full, 90 = Quarter, 180 = New
    val isWaxing: Boolean,
    val phaseName: String
) {
    val illuminationPercent: Int
        get() = (illuminationFraction * 100f).roundToInt().safeClamp(0, 100)
}

/**
 * Computes astronomically accurate lunar phase, illumination fraction, and waxing/waning
 * orientation using Jean Meeus's Astronomical Algorithms (Chapter 48) orbital anomaly equations.
 */
object LunarPhaseCalculator {

    private fun norm360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0.0) r + 360.0 else r
    }

    private fun degToRad(deg: Double): Double = deg * PI / 180.0

    fun calculate(epochMillis: Long = System.currentTimeMillis()): MoonPhaseInfo {
        // Julian Ephemeris Day from Unix epoch
        val jd = (epochMillis.toDouble() / 86_400_000.0) + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val t2 = t * t

        // Mean elongation of the Moon (D), Sun's mean anomaly (M), Moon's mean anomaly (Mp)
        val d = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t2)
        val m = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t2)
        val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t2)

        // True selenocentric phase angle i (unsigned 0..180 degrees: 0 = Full Moon, 180 = New Moon)
        val rawSignedPhase = 180.0 - d -
            6.289 * sin(degToRad(mp)) +
            2.100 * sin(degToRad(m)) -
            1.274 * sin(degToRad(2.0 * d - mp)) -
            0.658 * sin(degToRad(2.0 * d)) -
            0.214 * sin(degToRad(2.0 * mp)) -
            0.110 * sin(degToRad(d))
        val phaseAngle = abs(rawSignedPhase).safeClamp(0.0, 180.0)

        val illum = ((1.0 + cos(degToRad(phaseAngle))) / 2.0).safeClamp(0.0, 1.0)
        val waxing = d < 180.0

        // True elongation corrected for eccentricity (0..360 -> 0..1 cycle)
        val trueElongation = norm360(if (waxing) (180.0 - phaseAngle) else (180.0 + phaseAngle))
        val cycle = (trueElongation / 360.0).safeClamp(0.0, 1.0)

        val label = when {
            illum < 0.02 -> "New Moon"
            illum > 0.985 -> "Full Moon"
            waxing && illum < 0.46 -> "Waxing Crescent"
            waxing && illum <= 0.54 -> "First Quarter"
            waxing -> "Waxing Gibbous"
            !waxing && illum > 0.54 -> "Waning Gibbous"
            !waxing && illum >= 0.46 -> "Third Quarter"
            else -> "Waning Crescent"
        }

        return MoonPhaseInfo(
            cycleFraction = cycle.toFloat(),
            illuminationFraction = illum.toFloat(),
            phaseAngleDeg = phaseAngle.toFloat(),
            isWaxing = waxing,
            phaseName = label
        )
    }
}
