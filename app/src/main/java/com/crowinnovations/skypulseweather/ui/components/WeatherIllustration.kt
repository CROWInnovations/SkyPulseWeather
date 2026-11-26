package com.crowinnovations.skypulseweather.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.crowinnovations.skypulseweather.domain.LunarPhaseCalculator
import com.crowinnovations.skypulseweather.domain.MoonPhaseInfo
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Photorealistic 3D weather illustration used in Hourly strip, 5-Day rows, and Settings.
 * Nighttime icons composite the true 3D spherical `LunarBitmapRenderer` Moon with our
 * sculpted 3D volumetric cloud (`cloud_icon_3d`) reflecting directional silver-cyan moonlight.
 */
@Composable
fun WeatherIllustration(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    isDay: Boolean = condition != WeatherCondition.CLEAR_NIGHT && condition != WeatherCondition.PARTLY_CLOUDY_NIGHT,
    animated: Boolean = false,
    moonPhaseOverride: MoonPhaseInfo? = null
) {
    val context = LocalContext.current
    val moonPhase = moonPhaseOverride ?: remember { LunarPhaseCalculator.calculate() }

    val moonBitmap: ImageBitmap? = remember(isDay, moonPhase.phaseAngleDeg.roundToInt(), moonPhase.isWaxing) {
        if (!isDay) {
            LunarBitmapRenderer.renderMoonBitmap(context, moonPhase, sizePx = 128).asImageBitmap()
        } else {
            null
        }
    }

    val isStorm = condition == WeatherCondition.RAIN ||
        condition == WeatherCondition.DRIZZLE ||
        condition == WeatherCondition.THUNDERSTORM

    val cloudBitmap: ImageBitmap = remember(isDay, isStorm) {
        LunarBitmapRenderer.renderCloudIconBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStorm
        ).asImageBitmap()
    }

    val floatPhase = if (animated) {
        val inf = rememberInfiniteTransition(label = "icon3dMotion")
        val p by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4_200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "iconPhase"
        )
        p
    } else {
        0f
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val bobY = if (animated) sin(floatPhase * 2f * PI.toFloat()) * (h * 0.035f) else 0f
        val spinDeg = if (animated) floatPhase * 360f else 0f

        when (condition) {
            WeatherCondition.CLEAR_DAY -> {
                draw3DRadiantSunIcon(
                    cx = w * 0.50f,
                    cy = h * 0.50f + bobY * 0.4f,
                    radius = w * 0.25f,
                    rayRotationDeg = spinDeg
                )
            }

            WeatherCondition.CLEAR_NIGHT -> {
                drawLunarSpriteWithHalo(
                    moon = moonBitmap,
                    cx = w * 0.50f,
                    cy = h * 0.50f + bobY * 0.4f,
                    radius = w * 0.34f
                )
            }

            WeatherCondition.PARTLY_CLOUDY_DAY -> {
                draw3DRadiantSunIcon(
                    cx = w * 0.62f,
                    cy = h * 0.33f,
                    radius = w * 0.21f,
                    rayRotationDeg = spinDeg
                )
                drawCloudSprite(
                    cloud = cloudBitmap,
                    left = w * 0.04f,
                    top = h * 0.32f + bobY,
                    width = w * 0.88f,
                    height = h * 0.64f
                )
            }

            WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                drawLunarSpriteWithHalo(
                    moon = moonBitmap,
                    cx = w * 0.62f,
                    cy = h * 0.34f,
                    radius = w * 0.26f
                )
                drawCloudSprite(
                    cloud = cloudBitmap,
                    left = w * 0.03f,
                    top = h * 0.34f + bobY,
                    width = w * 0.90f,
                    height = h * 0.64f
                )
            }

            WeatherCondition.OVERCAST -> {
                if (!isDay) {
                    drawLunarSpriteWithHalo(
                        moon = moonBitmap,
                        cx = w * 0.63f,
                        cy = h * 0.32f,
                        radius = w * 0.24f
                    )
                    drawCloudSprite(
                        cloud = cloudBitmap,
                        left = w * 0.03f,
                        top = h * 0.32f + bobY,
                        width = w * 0.92f,
                        height = h * 0.66f
                    )
                } else {
                    drawCloudSprite(
                        cloud = cloudBitmap,
                        left = w * 0.18f,
                        top = h * 0.12f - bobY * 0.5f,
                        width = w * 0.74f,
                        height = h * 0.54f,
                        alpha = 0.78f
                    )
                    drawCloudSprite(
                        cloud = cloudBitmap,
                        left = w * 0.03f,
                        top = h * 0.28f + bobY,
                        width = w * 0.90f,
                        height = h * 0.66f
                    )
                }
            }

            WeatherCondition.FOG -> {
                if (!isDay) {
                    drawLunarSpriteWithHalo(
                        moon = moonBitmap,
                        cx = w * 0.64f,
                        cy = h * 0.28f,
                        radius = w * 0.22f
                    )
                }
                drawCloudSprite(
                    cloud = cloudBitmap,
                    left = w * 0.08f,
                    top = h * 0.18f + bobY * 0.5f,
                    width = w * 0.84f,
                    height = h * 0.58f
                )
                drawLine(
                    color = Color(0xCC94A3B8),
                    start = Offset(w * 0.16f, h * 0.76f),
                    end = Offset(w * 0.84f, h * 0.76f),
                    strokeWidth = h * 0.055f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0x99CBD5E1),
                    start = Offset(w * 0.24f, h * 0.87f),
                    end = Offset(w * 0.76f, h * 0.87f),
                    strokeWidth = h * 0.055f,
                    cap = StrokeCap.Round
                )
            }

            WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> {
                if (!isDay) {
                    drawLunarSpriteWithHalo(
                        moon = moonBitmap,
                        cx = w * 0.65f,
                        cy = h * 0.26f,
                        radius = w * 0.21f,
                        alpha = 0.88f
                    )
                }
                drawCloudSprite(
                    cloud = cloudBitmap,
                    left = w * 0.04f,
                    top = h * 0.14f + bobY * 0.6f,
                    width = w * 0.88f,
                    height = h * 0.60f
                )
                val drops = listOf(0.30f, 0.49f, 0.68f)
                drops.forEachIndexed { idx, fx ->
                    val dropAnim = if (animated) ((floatPhase * 3f + idx * 0.33f) % 1f) * (h * 0.10f) else 0f
                    val sx = w * fx - dropAnim * 0.25f
                    val sy = h * 0.68f + dropAnim
                    val ex = sx - w * 0.055f
                    val ey = sy + h * 0.18f
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF38BDF8), Color(0xFFE0F2FE)),
                            start = Offset(sx, sy),
                            end = Offset(ex, ey)
                        ),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = w * 0.058f,
                        cap = StrokeCap.Round
                    )
                }
            }

            WeatherCondition.SNOW -> {
                if (!isDay) {
                    drawLunarSpriteWithHalo(
                        moon = moonBitmap,
                        cx = w * 0.65f,
                        cy = h * 0.26f,
                        radius = w * 0.21f,
                        alpha = 0.88f
                    )
                }
                drawCloudSprite(
                    cloud = cloudBitmap,
                    left = w * 0.04f,
                    top = h * 0.14f + bobY * 0.5f,
                    width = w * 0.88f,
                    height = h * 0.60f
                )
                val flakes = listOf(0.31f to 0.76f, 0.50f to 0.85f, 0.69f to 0.76f)
                flakes.forEachIndexed { idx, (fx, fy) ->
                    val drift = if (animated) ((floatPhase * 2f + idx * 0.3f) % 1f) * (h * 0.07f) else 0f
                    val c = Offset(w * fx, h * fy + drift)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFF7DD3FC), Color.Transparent),
                            center = c,
                            radius = w * 0.08f
                        ),
                        radius = w * 0.08f,
                        center = c
                    )
                }
            }

            WeatherCondition.THUNDERSTORM -> {
                drawCloudSprite(
                    cloud = cloudBitmap,
                    left = w * 0.05f,
                    top = h * 0.08f + bobY * 0.5f,
                    width = w * 0.90f,
                    height = h * 0.62f
                )
                val bolt = Path().apply {
                    moveTo(w * 0.56f, h * 0.52f)
                    lineTo(w * 0.41f, h * 0.73f)
                    lineTo(w * 0.52f, h * 0.73f)
                    lineTo(w * 0.45f, h * 0.95f)
                    lineTo(w * 0.66f, h * 0.68f)
                    lineTo(w * 0.54f, h * 0.68f)
                    close()
                }
                drawPath(
                    path = bolt,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B)),
                        startY = h * 0.52f,
                        endY = h * 0.95f
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawLunarSpriteWithHalo(
    moon: ImageBitmap?,
    cx: Float,
    cy: Float,
    radius: Float,
    alpha: Float = 1f
) {
    val haloR = radius * 1.75f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x66E0F2FE),
                Color(0x2E7DD3FC),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = haloR
        ),
        radius = haloR,
        center = Offset(cx, cy)
    )
    if (moon != null) {
        val dia = (radius * 2f).roundToInt().coerceAtLeast(12)
        drawImage(
            image = moon,
            dstOffset = IntOffset((cx - dia * 0.5f).roundToInt(), (cy - dia * 0.5f).roundToInt()),
            dstSize = IntSize(dia, dia),
            alpha = alpha,
            filterQuality = FilterQuality.High
        )
    }
}

private fun DrawScope.drawCloudSprite(
    cloud: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    alpha: Float = 1f
) {
    drawImage(
        image = cloud,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(12), height.roundToInt().coerceAtLeast(10)),
        alpha = alpha,
        filterQuality = FilterQuality.High
    )
}

private fun DrawScope.draw3DRadiantSunIcon(
    cx: Float,
    cy: Float,
    radius: Float,
    rayRotationDeg: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x99FEF08A),
                Color(0x55FB923C),
                Color(0x28F472B6),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = radius * 1.95f
        ),
        radius = radius * 1.95f,
        center = Offset(cx, cy)
    )
    for (i in 0 until 8) {
        val rad = Math.toRadians((i * 45.0) + rayRotationDeg)
        val x1 = cx + (radius * 1.16f) * cos(rad).toFloat()
        val y1 = cy + (radius * 1.16f) * sin(rad).toFloat()
        val x2 = cx + (radius * 1.52f) * cos(rad).toFloat()
        val y2 = cy + (radius * 1.52f) * sin(rad).toFloat()
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFF97316)),
                start = Offset(x1, y1),
                end = Offset(x2, y2)
            ),
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = radius * 0.20f,
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFFFBEB),
                Color(0xFFFDE047),
                Color(0xFFF59E0B),
                Color(0xFFEA580C)
            ),
            center = Offset(cx - radius * 0.28f, cy - radius * 0.28f),
            radius = radius * 1.38f
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}
