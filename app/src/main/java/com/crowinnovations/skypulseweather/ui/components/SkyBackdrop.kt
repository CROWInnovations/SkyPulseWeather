package com.crowinnovations.skypulseweather.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.crowinnovations.skypulseweather.R
import com.crowinnovations.skypulseweather.domain.LunarPhaseCalculator
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import com.crowinnovations.skypulseweather.domain.safeClamp
import com.crowinnovations.skypulseweather.ui.theme.SkyPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@DrawableRes
fun sceneDrawableFor(
    condition: WeatherCondition,
    isDay: Boolean,
    hasTornadoWarning: Boolean = false,
    cloudCoverPercent: Int = 50
): Int {
    if (hasTornadoWarning) return R.drawable.sky_storm_crow_tornado
    return when (condition) {
        WeatherCondition.CLEAR_DAY -> R.drawable.sky_sunny_clear_3d
        WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.sky_partly_sunny_3d
        WeatherCondition.OVERCAST, WeatherCondition.FOG -> {
            if (isDay) R.drawable.sky_cloudy_3d else R.drawable.sky_cloudy_night_3d
        }
        WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> R.drawable.sky_rain_3d
        WeatherCondition.THUNDERSTORM -> R.drawable.sky_thunderstorm_3d
        WeatherCondition.CLEAR_NIGHT -> {
            if (cloudCoverPercent >= 28) R.drawable.sky_cloudy_night_3d else R.drawable.sky_clear_night_3d
        }
        WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.sky_cloudy_night_3d
        WeatherCondition.SNOW -> R.drawable.sky_snow_3d
    }
}

/**
 * Full-screen 3D weather scene engine:
 * - Strictly matches expected weather (tornado shown ONLY during an active Tornado Warning)
 * - Photorealistic 3D Radiant Sun & sunbeams when sunny/partly sunny
 * - Photorealistic 3D Spherical Waxing/Waning Moon (`LunarBitmapRenderer`) across ALL nighttime skies
 * - Real volumetric 3D cloud layers (`cloud_volumetric_3d`) reflecting directional moonlight / sunlight
 */
@Composable
fun SkyBackdrop(
    condition: WeatherCondition,
    isDay: Boolean,
    cloudCoverPercent: Int,
    hasTornadoWarning: Boolean = false,
    topClockReserveDp: Float = 0f,
    palette: SkyPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val safeCloudCover = cloudCoverPercent.safeClamp(0, 100)
    val sceneRes = sceneDrawableFor(
        condition = condition,
        isDay = isDay,
        hasTornadoWarning = hasTornadoWarning,
        cloudCoverPercent = safeCloudCover
    )
    val moonPhase = remember(condition, isDay) { LunarPhaseCalculator.calculate() }

    val moonBitmap: ImageBitmap? = remember(isDay, moonPhase.phaseAngleDeg.roundToInt(), moonPhase.isWaxing) {
        if (!isDay) {
            LunarBitmapRenderer.renderMoonBitmap(context, moonPhase, sizePx = 384).asImageBitmap()
        } else {
            null
        }
    }

    val isStormCondition = condition == WeatherCondition.RAIN ||
        condition == WeatherCondition.DRIZZLE ||
        condition == WeatherCondition.THUNDERSTORM

    val volumetricCloudWisp0: ImageBitmap = remember(isDay, isStormCondition) {
        LunarBitmapRenderer.renderVolumetricWispBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStormCondition,
            variant = 0
        ).asImageBitmap()
    }
    val volumetricCloudWisp1: ImageBitmap = remember(isDay, isStormCondition) {
        LunarBitmapRenderer.renderVolumetricWispBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStormCondition,
            variant = 1
        ).asImageBitmap()
    }
    val volumetricCloudWisp2: ImageBitmap = remember(isDay, isStormCondition) {
        LunarBitmapRenderer.renderVolumetricWispBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStormCondition,
            variant = 2
        ).asImageBitmap()
    }

    val infinite = rememberInfiniteTransition(label = "sky3dMotion")

    val cameraPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cameraPhase"
    )

    val cloudPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 52_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloudPhase"
    )

    val precipPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "precipPhase"
    )

    val lightCycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lightCycle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.gradientTop, palette.gradientMid, palette.gradientBottom)
                )
            )
    ) {
        val camAngle = cameraPhase * 2f * PI.toFloat()
        val zoom = 1.09f + 0.022f * sin(camAngle)

        Crossfade(
            targetState = sceneRes,
            animationSpec = tween(durationMillis = 750),
            label = "sceneCrossfade"
        ) { resId ->
            Image(
                painter = painterResource(id = resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = sin(camAngle) * (size.width * 0.028f)
                        translationY = cos(camAngle) * (size.height * 0.012f)
                    }
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val isLandscape = w > h
            val minTopClearancePx = if (topClockReserveDp > 0f) {
                topClockReserveDp * density + 18f
            } else {
                0f
            }
            // Giant Moon occupying >= 35-40% of the upper-right display area across all screen sizes
            val celestialRadius = if (isLandscape) {
                (h * 0.215f).safeClamp(48f, w * 0.175f)
            } else {
                (w * 0.195f).safeClamp(52f, h * 0.175f)
            }
            val minCenterY = (celestialRadius + minTopClearancePx.coerceAtLeast(if (isLandscape) 8f else 24f))
            val celestialCenter = if (isLandscape) {
                Offset(
                    x = (w - celestialRadius - w * 0.03f).safeClamp(w * 0.65f, w * 0.90f),
                    y = (h * 0.30f).safeClamp(minCenterY, (h * 0.56f).coerceAtLeast(minCenterY))
                )
            } else {
                Offset(
                    x = (w - celestialRadius - w * 0.03f).safeClamp(w * 0.66f, w * 0.86f),
                    y = (h * 0.245f).safeClamp(minCenterY, (h * 0.42f).coerceAtLeast(minCenterY))
                )
            }

            // Readability scrim behind foreground celestial & cloud layers
            drawRect(color = palette.scrimOverlay)

            if (hasTornadoWarning) {
                drawLightningFlashOverlay(w, h, lightCycle)
                for (i in 0 until 20) {
                    val t = (precipPhase + i * 0.05f) % 1f
                    val y = h * (0.85f - t * 0.62f)
                    val funnelRadius = w * (0.04f + t * 0.18f)
                    val spin = (precipPhase * 2f + i * 0.21f) * 2f * PI.toFloat()
                    val x = w * 0.48f + cos(spin) * funnelRadius
                    drawCircle(
                        color = if (i % 4 == 0) Color(0x99FBBF24) else Color(0x8894A3B8),
                        radius = if (i % 3 == 0) 3.2f else 2.1f,
                        center = Offset(x, y)
                    )
                }
                drawRainStreaksOverlay(w, h, precipPhase, count = 30, windFactor = 0.18f)
                return@Canvas
            }

            if (!isDay) {
                // =========================================================================
                // NIGHTTIME PHOTOREALISTIC 3D MOON & MOONLIT VOLUMETRIC CLOUDS
                // =========================================================================
                val starAlphaScale = (1f - safeCloudCover / 135f).safeClamp(0.18f, 1f)
                drawTwinklingStars(w, h, lightCycle, starAlphaScale)

                val illum = moonPhase.illuminationFraction
                val moonVisibility = when (condition) {
                    WeatherCondition.CLEAR_NIGHT -> 1.0f
                    WeatherCondition.PARTLY_CLOUDY_NIGHT -> 0.98f
                    WeatherCondition.OVERCAST, WeatherCondition.FOG -> 0.93f
                    else -> 0.84f
                }

                // 1. Multi-layered soft optical lunar corona (pure atmospheric scatter, zero hard edges)
                val haloPower = (0.38f + 0.62f * illum) * moonVisibility
                val outerHaloR = celestialRadius * (2.8f + 1.2f * illum)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE0F2FE).copy(alpha = 0.44f * haloPower),
                            Color(0xFFBAE6FD).copy(alpha = 0.24f * haloPower),
                            Color(0xFF38BDF8).copy(alpha = 0.09f * haloPower),
                            Color.Transparent
                        ),
                        center = celestialCenter,
                        radius = outerHaloR
                    ),
                    radius = outerHaloR,
                    center = celestialCenter
                )

                // 2. Photorealistic 3D Spherical Lunar Bitmap (exact astronomical waxing/waning phase)
                if (moonBitmap != null) {
                    val moonDia = (celestialRadius * 2f).roundToInt().coerceAtLeast(24)
                    val topLeft = IntOffset(
                        (celestialCenter.x - moonDia * 0.5f).roundToInt(),
                        (celestialCenter.y - moonDia * 0.5f).roundToInt()
                    )
                    drawImage(
                        image = moonBitmap,
                        dstOffset = topLeft,
                        dstSize = IntSize(moonDia, moonDia),
                        alpha = moonVisibility,
                        filterQuality = FilterQuality.High
                    )
                }

                // 3. Real 3D Volumetric Cloud Variations drifting across the sky & lower rim of the giant Moon
                if (safeCloudCover >= 15 || condition != WeatherCondition.CLEAR_NIGHT) {
                    drawDriftingVolumetricCloud(
                        wisp = volumetricCloudWisp0,
                        w = w,
                        h = h,
                        progress = cloudPhase,
                        yCenterFrac = if (isLandscape) 0.52f else 0.355f,
                        widthFrac = 0.80f,
                        baseAlpha = (0.34f + (safeCloudCover / 100f) * 0.28f).safeClamp(0.32f, 0.64f),
                        lightSource = celestialCenter,
                        isMoonlight = true
                    )
                    drawDriftingVolumetricCloud(
                        wisp = volumetricCloudWisp1,
                        w = w,
                        h = h,
                        progress = (cloudPhase * 0.78f + 0.36f) % 1f,
                        yCenterFrac = if (isLandscape) 0.44f else 0.295f,
                        widthFrac = 0.72f,
                        baseAlpha = (0.28f + (safeCloudCover / 100f) * 0.24f).safeClamp(0.26f, 0.56f),
                        lightSource = celestialCenter,
                        isMoonlight = true
                    )

                    if (safeCloudCover >= 45 || condition == WeatherCondition.OVERCAST || isStormCondition) {
                        // Deeper parallax 3D moonlit cloud bank (distinct towering asymmetric cumulus variation)
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp2,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.62f + 0.68f) % 1f,
                            yCenterFrac = if (isLandscape) 0.68f else 0.52f,
                            widthFrac = 0.90f,
                            baseAlpha = (0.30f + (safeCloudCover / 100f) * 0.26f).safeClamp(0.28f, 0.58f),
                            lightSource = celestialCenter,
                            isMoonlight = true
                        )
                    }
                }

                // 4. Nighttime precipitation / lightning
                when (condition) {
                    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> {
                        drawRainStreaksOverlay(w, h, precipPhase, count = 40, windFactor = 0.11f)
                    }
                    WeatherCondition.THUNDERSTORM -> {
                        drawLightningFlashOverlay(w, h, lightCycle)
                        drawRainStreaksOverlay(w, h, precipPhase, count = 36, windFactor = 0.14f)
                    }
                    WeatherCondition.SNOW -> {
                        drawSnowflakesOverlay(w, h, cloudPhase, lightCycle)
                    }
                    else -> Unit
                }
            } else {
                // =========================================================================
                // DAYTIME PHOTOREALISTIC 3D SUNSHIMMER & VOLUMETRIC MOTION CLOUDS
                // (Preserves the photorealistic 3D Sun in sky_sunny_clear_3d / sky_partly_sunny_3d!)
                // =========================================================================
                when (condition) {
                    WeatherCondition.CLEAR_DAY -> {
                        // Gently pulsing radiant Sun + 3 varied faint wispy cloud formations slowly flowing in the lower background
                        drawSubtleSolarShimmer(
                            cx = w * 0.74f,
                            cy = h * 0.19f,
                            radius = celestialRadius * 1.25f,
                            lightCycle = lightCycle
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp1,
                            w = w,
                            h = h,
                            progress = cloudPhase,
                            yCenterFrac = 0.50f,
                            widthFrac = 0.54f,
                            baseAlpha = 0.16f,
                            lightSource = Offset(w * 0.74f, h * 0.19f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp0,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.68f + 0.37f) % 1f,
                            yCenterFrac = 0.62f,
                            widthFrac = 0.46f,
                            baseAlpha = 0.14f,
                            lightSource = Offset(w * 0.74f, h * 0.19f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp2,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.52f + 0.74f) % 1f,
                            yCenterFrac = 0.72f,
                            widthFrac = 0.42f,
                            baseAlpha = 0.12f,
                            lightSource = Offset(w * 0.74f, h * 0.19f),
                            isMoonlight = false
                        )
                    }

                    WeatherCondition.PARTLY_CLOUDY_DAY -> {
                        drawSubtleSolarShimmer(
                            cx = w * 0.74f,
                            cy = h * 0.20f,
                            radius = celestialRadius,
                            lightCycle = lightCycle
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp0,
                            w = w,
                            h = h,
                            progress = cloudPhase,
                            yCenterFrac = 0.30f,
                            widthFrac = 0.80f,
                            baseAlpha = 0.60f,
                            lightSource = Offset(w * 0.74f, h * 0.20f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp1,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.76f + 0.34f) % 1f,
                            yCenterFrac = 0.44f,
                            widthFrac = 0.76f,
                            baseAlpha = 0.48f,
                            lightSource = Offset(w * 0.74f, h * 0.20f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp2,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.58f + 0.71f) % 1f,
                            yCenterFrac = 0.58f,
                            widthFrac = 0.86f,
                            baseAlpha = 0.44f,
                            lightSource = Offset(w * 0.74f, h * 0.20f),
                            isMoonlight = false
                        )
                    }

                    WeatherCondition.OVERCAST, WeatherCondition.FOG -> {
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp0,
                            w = w,
                            h = h,
                            progress = cloudPhase,
                            yCenterFrac = 0.24f,
                            widthFrac = 0.88f,
                            baseAlpha = 0.54f,
                            lightSource = Offset(w * 0.65f, h * 0.10f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp1,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.72f + 0.42f) % 1f,
                            yCenterFrac = 0.38f,
                            widthFrac = 0.82f,
                            baseAlpha = 0.46f,
                            lightSource = Offset(w * 0.65f, h * 0.10f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp2,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.55f + 0.78f) % 1f,
                            yCenterFrac = 0.54f,
                            widthFrac = 0.90f,
                            baseAlpha = 0.42f,
                            lightSource = Offset(w * 0.65f, h * 0.10f),
                            isMoonlight = false
                        )
                    }

                    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> {
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp0,
                            w = w,
                            h = h,
                            progress = cloudPhase,
                            yCenterFrac = 0.21f,
                            widthFrac = 0.92f,
                            baseAlpha = 0.54f,
                            lightSource = Offset(w * 0.5f, 0f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp2,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.68f + 0.48f) % 1f,
                            yCenterFrac = 0.36f,
                            widthFrac = 0.86f,
                            baseAlpha = 0.46f,
                            lightSource = Offset(w * 0.5f, 0f),
                            isMoonlight = false
                        )
                        drawRainStreaksOverlay(w, h, precipPhase, count = 42, windFactor = 0.11f)
                    }

                    WeatherCondition.THUNDERSTORM -> {
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp2,
                            w = w,
                            h = h,
                            progress = cloudPhase,
                            yCenterFrac = 0.21f,
                            widthFrac = 0.94f,
                            baseAlpha = 0.58f,
                            lightSource = Offset(w * 0.55f, h * 0.2f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp1,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.70f + 0.46f) % 1f,
                            yCenterFrac = 0.35f,
                            widthFrac = 0.88f,
                            baseAlpha = 0.48f,
                            lightSource = Offset(w * 0.55f, h * 0.2f),
                            isMoonlight = false
                        )
                        drawLightningFlashOverlay(w, h, lightCycle)
                        drawRainStreaksOverlay(w, h, precipPhase, count = 38, windFactor = 0.14f)
                    }

                    WeatherCondition.SNOW -> {
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp0,
                            w = w,
                            h = h,
                            progress = cloudPhase,
                            yCenterFrac = 0.23f,
                            widthFrac = 0.82f,
                            baseAlpha = 0.48f,
                            lightSource = Offset(w * 0.7f, h * 0.1f),
                            isMoonlight = false
                        )
                        drawDriftingVolumetricCloud(
                            wisp = volumetricCloudWisp1,
                            w = w,
                            h = h,
                            progress = (cloudPhase * 0.66f + 0.50f) % 1f,
                            yCenterFrac = 0.38f,
                            widthFrac = 0.78f,
                            baseAlpha = 0.40f,
                            lightSource = Offset(w * 0.7f, h * 0.1f),
                            isMoonlight = false
                        )
                        drawSnowflakesOverlay(w, h, cloudPhase, lightCycle)
                    }

                    else -> Unit
                }
            }
        }

        content()
    }
}

/**
 * Drifts a photorealistic 3D volumetric cloud bitmap smoothly across the viewport, dynamically
 * boosting silver-cyan moonlight glow (or golden sunlight) when passing near `lightSource`.
 */
private fun DrawScope.drawDriftingVolumetricCloud(
    wisp: ImageBitmap,
    w: Float,
    h: Float,
    progress: Float,
    yCenterFrac: Float,
    widthFrac: Float,
    baseAlpha: Float,
    lightSource: Offset,
    isMoonlight: Boolean
) {
    val dstW = (w * widthFrac).roundToInt().coerceAtLeast(64)
    val aspect = wisp.height.toFloat() / wisp.width.coerceAtLeast(1).toFloat()
    val dstH = (dstW * aspect).roundToInt().coerceAtLeast(36)

    val span = w + dstW * 1.30f
    val leftF = -dstW * 0.65f + progress * span
    val topF = h * yCenterFrac - dstH * 0.5f

    val cx = leftF + dstW * 0.5f
    val cy = topF + dstH * 0.42f
    val dist = hypot(lightSource.x - cx, lightSource.y - cy)
    val proximity = (1f - dist / (w * 0.85f)).safeClamp(0f, 1f)

    // Subtle atmospheric moonlight scatter halo on cloud crown when drifting close to the Moon
    if (isMoonlight && proximity > 0.15f) {
        val glowR = dstW * 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFBAE6FD).copy(alpha = 0.20f * proximity * baseAlpha),
                    Color(0xFF38BDF8).copy(alpha = 0.08f * proximity * baseAlpha),
                    Color.Transparent
                ),
                center = Offset(cx + (lightSource.x - cx) * 0.25f, cy - dstH * 0.12f),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(cx + (lightSource.x - cx) * 0.25f, cy - dstH * 0.12f)
        )
    }

    val effectiveAlpha = (baseAlpha * (0.85f + 0.22f * proximity)).safeClamp(0.05f, 0.88f)
    drawImage(
        image = wisp,
        dstOffset = IntOffset(leftF.roundToInt(), topF.roundToInt()),
        dstSize = IntSize(dstW, dstH),
        alpha = effectiveAlpha,
        filterQuality = FilterQuality.Medium
    )
}

/**
 * Adds a gentle living golden aura shimmer to complement (never cover) the photorealistic 3D Sun
 * in `sky_sunny_clear_3d` and `sky_partly_sunny_3d`.
 */
private fun DrawScope.drawSubtleSolarShimmer(
    cx: Float,
    cy: Float,
    radius: Float,
    lightCycle: Float
) {
    val pulse = 0.92f + 0.10f * sin(lightCycle * 2f * PI.toFloat())
    val center = Offset(cx, cy)
    val r = radius * 2.8f * pulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x30FEF08A),
                Color(0x18FB923C),
                Color(0x0AF472B6),
                Color.Transparent
            ),
            center = center,
            radius = r
        ),
        radius = r,
        center = center
    )
}

private fun DrawScope.drawTwinklingStars(
    w: Float,
    h: Float,
    lightCycle: Float,
    alphaScale: Float
) {
    val stars = listOf(
        0.10f to 0.07f, 0.24f to 0.13f, 0.39f to 0.06f,
        0.54f to 0.11f, 0.62f to 0.07f, 0.16f to 0.22f,
        0.48f to 0.19f, 0.91f to 0.10f, 0.31f to 0.17f,
        0.08f to 0.29f, 0.58f to 0.26f
    )
    stars.forEachIndexed { idx, (fx, fy) ->
        val twinkle = (0.28f + 0.68f * (0.5f + 0.5f * sin((lightCycle + idx * 0.17f) * 2f * PI.toFloat()))) * alphaScale
        drawCircle(
            color = Color(0xFFE0F2FE).copy(alpha = twinkle.safeClamp(0f, 1f)),
            radius = if (idx % 3 == 0) 2.6f else 1.7f,
            center = Offset(w * fx, h * fy)
        )
    }
}

private fun DrawScope.drawSnowflakesOverlay(
    w: Float,
    h: Float,
    cloudPhase: Float,
    lightCycle: Float
) {
    for (i in 0 until 34) {
        val near = i % 3 == 0
        val seedX = ((i * 47 + 11) % 100) / 100f
        val p = (cloudPhase * (if (near) 5f else 3f) + i * 0.11f) % 1f
        val sway = sin((lightCycle + i * 0.14f) * 2f * PI.toFloat()) * (if (near) 22f else 12f)
        val x = w * seedX + sway
        val y = p * h
        val r = if (near) 5.2f else 2.8f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (near) 0.88f else 0.62f),
                    Color(0xFFBAE6FD).copy(alpha = 0.25f),
                    Color.Transparent
                ),
                center = Offset(x, y),
                radius = r * 1.8f
            ),
            radius = r * 1.8f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawLightningFlashOverlay(w: Float, h: Float, lightCycle: Float) {
    val flashIntensity = when {
        lightCycle in 0.14f..0.17f -> 0.34f
        lightCycle in 0.19f..0.23f -> 0.50f
        lightCycle in 0.64f..0.67f -> 0.28f
        else -> 0f
    }
    if (flashIntensity > 0f) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFC4B5FD).copy(alpha = flashIntensity),
                    Color(0xFF38BDF8).copy(alpha = flashIntensity * 0.55f),
                    Color.Transparent
                ),
                center = Offset(w * 0.55f, h * 0.24f),
                radius = w * 0.95f
            )
        )
    }
}

private fun DrawScope.drawRainStreaksOverlay(
    w: Float,
    h: Float,
    precipPhase: Float,
    count: Int,
    windFactor: Float
) {
    for (i in 0 until count) {
        val depthLayer = i % 3
        val speedMult = if (depthLayer == 2) 2f else 1f
        val seedX = ((i * 53 + 17) % 100) / 100f
        val seedT = ((i * 29) % 100) / 100f
        val p = ((precipPhase * speedMult) + seedT) % 1f
        val x = (w * (seedX * 1.14f)) - p * w * windFactor
        val y = p * (h + 80f) - 40f

        val streakLen = when (depthLayer) {
            0 -> 24f
            1 -> 40f
            else -> 60f
        }
        val strokeW = when (depthLayer) {
            0 -> 1.4f
            1 -> 2.3f
            else -> 3.3f
        }
        val alpha = when (depthLayer) {
            0 -> 0.28f
            1 -> 0.46f
            else -> 0.72f
        }
        val end = Offset(x - streakLen * 0.18f, y + streakLen)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF7DD3FC).copy(alpha = alpha * 0.7f),
                    Color(0xFFE0F2FE).copy(alpha = alpha)
                ),
                start = Offset(x, y),
                end = end
            ),
            start = Offset(x, y),
            end = end,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        if (depthLayer == 2) {
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = 2.1f,
                center = end
            )
        }
    }
}
