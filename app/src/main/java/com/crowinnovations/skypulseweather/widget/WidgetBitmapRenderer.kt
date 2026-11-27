package com.crowinnovations.skypulseweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.crowinnovations.skypulseweather.domain.DisplayFontStyle
import com.crowinnovations.skypulseweather.domain.LunarPhaseCalculator
import com.crowinnovations.skypulseweather.domain.MoonPhaseInfo
import com.crowinnovations.skypulseweather.domain.WeatherCondition
import com.crowinnovations.skypulseweather.domain.safeClamp
import com.crowinnovations.skypulseweather.ui.components.LunarBitmapRenderer
import com.crowinnovations.skypulseweather.ui.components.sceneDrawableFor
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders exact-aspect, backwards-compatible (API 26+) 3D weather widget canvases matching
 * the exact weather outside:
 * - Sunny (`CLEAR_DAY`): Unobstructed photorealistic 3D Sun with a gentle breathing golden solar
 *   pulse (`renderSolarPulseOverlay`) + random faint wispy clouds slowly flowing in the background.
 * - Partly Sunny / Cloudy / Stormy: Weather-matched 3D clouds drifting at calm half-speed (11s).
 * - Nighttime: Giant upper-right 3D Waxing/Waning Moon (>= 36-44%) + weather-matched night sky/clouds.
 */
internal data class StyledTempBitmap(
    val bitmap: Bitmap,
    val widthDp: Float,
    val heightDp: Float
)

object WidgetBitmapRenderer {

    /**
     * Measures single-line bold text width in widget virtual `dp` using `Typeface.DEFAULT_BOLD`
     * with OEM font width headroom so hero temperatures align cleanly beneath `City, State`.
     */
    internal fun measureWidgetTextWidthDp(
        context: Context,
        text: String,
        virtualFontSizeSp: Float,
        fontScale: Float
    ): Float {
        if (text.isEmpty()) return 0f
        val density = context.resources.displayMetrics.density.safeClamp(1.5f, 4.0f)
        val safeFontScale = fontScale.safeClamp(0.85f, 1.45f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = virtualFontSizeSp * safeFontScale * density
        }
        return (paint.measureText(text) * 1.07f / density) + 6f
    }

    /**
     * Renders the hero temperature string (`"40°"`, `"-12°"`, `"102°"`) cropped tightly to its
     * true glyph ink bounds (eliminating `TextView`'s ~42% empty diacritic-ascent and descender
     * box bloat) while compensating for launcher host downscaling
     * (`hsResizeRatio`) so `74sp` in Settings renders at true physical `74sp` on screen.
     */
    internal fun renderStyledTemperatureBitmap(
        context: Context,
        tempText: String,
        requestedRealSp: Float,
        hostResizeRatio: Float,
        fontScale: Float,
        maxWidthDp: Float,
        maxHeightDp: Float,
        fontStyle: DisplayFontStyle,
        textColorArgb: Int
    ): StyledTempBitmap {
        val safeText = tempText.ifBlank { "--°" }
        val density = context.resources.displayMetrics.density.safeClamp(1.5f, 4.0f)
        val safeHostRatio = hostResizeRatio.safeClamp(0.65f, 1.25f)
        val virtualPxPerDp = (density * safeHostRatio).coerceAtLeast(1.0f)
        val safeFontScale = fontScale.safeClamp(0.85f, 1.45f)
        val safeMaxW = maxWidthDp.coerceAtLeast(24f)
        val safeMaxH = maxHeightDp.coerceAtLeast(16f)

        val typeface = when (fontStyle) {
            DisplayFontStyle.DEFAULT -> Typeface.create("sans-serif", Typeface.BOLD)
            DisplayFontStyle.SERIF -> Typeface.create("serif", Typeface.BOLD)
            DisplayFontStyle.MONO -> Typeface.create("monospace", Typeface.BOLD)
            DisplayFontStyle.ITALIC -> Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
        }

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            this.color = textColorArgb
        }

        val shadowRadiusPx = 2.4f * density
        val shadowDyPx = 1.2f * density
        val padLeftPx = ceil(shadowRadiusPx + 1.0f * density).toInt()
        val padRightPx = ceil(shadowRadiusPx + 1.6f * density).toInt()
        val padTopPx = ceil(shadowRadiusPx + 0.6f * density).toInt()
        val padBottomPx = ceil(shadowRadiusPx + shadowDyPx + 1.0f * density).toInt()

        var realSp = requestedRealSp.safeClamp(10f, 96f)
        val inkBounds = Rect()

        fun measureAt(spVal: Float): Triple<Float, Float, Pair<Float, Float>> {
            paint.textSize = spVal * safeFontScale * density
            paint.getTextBounds(safeText, 0, safeText.length, inkBounds)
            val adv = paint.measureText(safeText)
            val leftPx = min(0f, inkBounds.left.toFloat())
            val rightPx = max(adv, inkBounds.right.toFloat())
            val totalWPx = (rightPx - leftPx) + padLeftPx + padRightPx
            val totalHPx = inkBounds.height().coerceAtLeast(1) + padTopPx + padBottomPx
            return Triple(totalWPx / virtualPxPerDp, totalHPx / virtualPxPerDp, leftPx to rightPx)
        }

        var (wDp, hDp, lr) = measureAt(realSp)
        if (wDp > safeMaxW || hDp > safeMaxH) {
            val fitScale = min(safeMaxW / wDp.coerceAtLeast(1f), safeMaxH / hDp.coerceAtLeast(1f))
                .safeClamp(0.20f, 1.0f)
            realSp = (realSp * fitScale).coerceAtLeast(9f)
            val remeasured = measureAt(realSp)
            wDp = remeasured.first
            hDp = remeasured.second
            lr = remeasured.third
        }

        val bmpW = ceil(wDp * virtualPxPerDp).toInt().coerceAtLeast(8)
        val bmpH = ceil(hDp * virtualPxPerDp).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        paint.setShadowLayer(
            shadowRadiusPx,
            0f,
            shadowDyPx,
            0xD8020617.toInt()
        )
        val drawX = padLeftPx - lr.first
        val drawY = padTopPx - inkBounds.top.toFloat()
        canvas.drawText(safeText, drawX, drawY, paint)

        return StyledTempBitmap(
            bitmap = bitmap,
            widthDp = bmpW / virtualPxPerDp,
            heightDp = bmpH / virtualPxPerDp
        )
    }

    private const val SUNNY_SRC_ASPECT = 900f / 1600f
    private const val SUNNY_SRC_SUN_X = 0.7486f
    private const val SUNNY_SRC_SUN_Y = 0.2030f

    /**
     * Computes `(moonX, moonY, moonR)` in widget coordinates `(w, h)` so both canvas rendering
     * and Glance centered Moon Phase labels align to the exact bottom of the 3D Moon, while
     * staying strictly below any top centered Time display (`topReservedFraction`).
     */
    fun computeMoonStageGeometry(
        w: Float,
        h: Float,
        topReservedFraction: Float = 0f,
        maxStageBottomFraction: Float = 0.64f
    ): Triple<Float, Float, Float> {
        val safeW = w.coerceAtLeast(52f)
        val safeH = h.coerceAtLeast(52f)
        val safeTopFrac = topReservedFraction.safeClamp(0f, 0.40f)
        val safeBotFrac = maxStageBottomFraction.safeClamp(safeTopFrac + 0.18f, 0.76f)
        val bandH = (safeH * (safeBotFrac - safeTopFrac)).coerceAtLeast(24f)

        val maxAllowedDia = min(safeW * 0.42f, bandH * 0.96f)
        val minDia = min(42f, maxAllowedDia).coerceAtLeast(20f)
        val targetDia = max(safeW * 0.38f, min(safeW, safeH) * 0.46f)
            .safeClamp(minDia, maxAllowedDia.coerceAtLeast(minDia))
        val moonR = targetDia * 0.5f
        val moonX = (safeW - moonR - safeW * 0.028f)
            .safeClamp(moonR + 4f, (safeW - moonR - 4f).coerceAtLeast(moonR + 4f))
        val topMargin = safeH * (if (safeTopFrac > 0f) 0.008f else if (safeW > safeH * 1.55f) 0.022f else 0.028f)
        val minMoonY = safeH * safeTopFrac + moonR + topMargin
        val maxMoonY = (safeH * safeBotFrac - moonR).coerceAtLeast(minMoonY)
        val extraBandSlack = (maxMoonY - minMoonY).coerceAtLeast(0f)
        val moonY = (minMoonY + extraBandSlack * 0.22f).safeClamp(moonR + 2f, maxMoonY)
        return Triple(moonX, moonY, moonR)
    }

    fun computeMoonBottomDp(
        widthDp: Float,
        heightDp: Float,
        topReservedFraction: Float = 0f,
        maxStageBottomFraction: Float = 0.64f
    ): Float {
        val (_, moonY, moonR) = computeMoonStageGeometry(
            widthDp,
            heightDp,
            topReservedFraction,
            maxStageBottomFraction
        )
        return moonY + moonR
    }

    /**
     * Computes normalized `(sunXNorm, sunYNorm, cropTopNorm)` for `sky_sunny_clear_3d` so the
     * 3D Sun sits cleanly in the upper-right stage (below any top Time display) and animated
     * sunbeams originate at its exact center.
     */
    fun computeSunnyCropAndSunCenter(
        w: Float,
        h: Float,
        topReservedFraction: Float = 0f
    ): Triple<Float, Float, Float> {
        val safeTopFrac = topReservedFraction.safeClamp(0f, 0.32f)
        val dstRatio = (w / h.coerceAtLeast(1f)).safeClamp(0.35f, 3.8f)
        if (SUNNY_SRC_ASPECT > dstRatio) {
            val cwNorm = (dstRatio / SUNNY_SRC_ASPECT).safeClamp(0.1f, 1f)
            val cxNorm = ((1f - cwNorm) * 0.5f).safeClamp(0f, 1f - cwNorm)
            val sunXNorm = ((SUNNY_SRC_SUN_X - cxNorm) / cwNorm).safeClamp(0.55f, 0.90f)
            val sunYNorm = (SUNNY_SRC_SUN_Y + safeTopFrac * 0.65f).safeClamp(0.18f, 0.46f)
            return Triple(sunXNorm, sunYNorm, 0f)
        } else {
            val chNorm = (SUNNY_SRC_ASPECT / dstRatio).safeClamp(0.1f, 1f)
            val baseDesiredY = if (dstRatio > 1.75f) 0.29f else 0.27f
            val desiredSunY = (baseDesiredY + safeTopFrac * 0.72f).safeClamp(0.24f, 0.48f)
            val maxCyNorm = (1f - chNorm).coerceAtLeast(0f)
            val cyNorm = (SUNNY_SRC_SUN_Y - desiredSunY * chNorm).safeClamp(0f, maxCyNorm)
            val actualPhotoSunY = ((SUNNY_SRC_SUN_Y - cyNorm) / chNorm).safeClamp(0.16f, 0.48f)
            val effectiveSunY = max(actualPhotoSunY, safeTopFrac + 0.16f).safeClamp(0.18f, 0.48f)
            return Triple(SUNNY_SRC_SUN_X, effectiveSunY, cyNorm)
        }
    }

    fun targetCanvasSizeForAspect(widthDp: Float, heightDp: Float): Pair<Int, Int> {
        val safeWDp = widthDp.coerceAtLeast(52f)
        val safeHDp = heightDp.coerceAtLeast(52f)
        val aspect = (safeWDp / safeHDp).safeClamp(0.35f, 3.6f)
        val maxPixels = 112_000f
        val hPx = sqrt(maxPixels / aspect).roundToInt().safeClamp(128, 460)
        val wPx = (hPx * aspect).roundToInt().safeClamp(128, 460)
        return wPx to hPx
    }

    fun renderWidgetSceneBackdrop(
        context: Context,
        condition: WeatherCondition,
        isDay: Boolean,
        cloudCoverPercent: Int,
        hasTornadoWarning: Boolean,
        moonPhase: MoonPhaseInfo = LunarPhaseCalculator.calculate(),
        widthPx: Int = 408,
        heightPx: Int = 320,
        topReservedFraction: Float = 0f,
        maxMoonBottomFraction: Float = 0.64f,
        suppressStaticRainStreaks: Boolean = false
    ): Bitmap {
        val safeW = widthPx.safeClamp(144, 500)
        val safeH = heightPx.safeClamp(128, 500)
        val outBmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)

        val w = safeW.toFloat()
        val h = safeH.toFloat()
        val safeClouds = cloudCoverPercent.safeClamp(0, 100)

        val cornerRadiusPx = min(w, h) * 0.085f
        val roundClip = Path().apply {
            addRoundRect(RectF(0f, 0f, w, h), cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        }
        canvas.clipPath(roundClip)

        val isStorm = condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.THUNDERSTORM

        // 1. Decode matching photorealistic 3D sky scene (tornado strictly ONLY when hasTornadoWarning == true)
        val drawableRes = sceneDrawableFor(
            condition = condition,
            isDay = isDay,
            hasTornadoWarning = hasTornadoWarning,
            cloudCoverPercent = safeClouds
        )
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 2
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val rawPhoto = runCatching {
            BitmapFactory.decodeResource(context.resources, drawableRes, opts)
        }.getOrNull()

        val isFullSunny = isDay && condition == WeatherCondition.CLEAR_DAY && !hasTornadoWarning
        val safeTopFrac = topReservedFraction.safeClamp(0f, 0.40f)
        val (sunnyXNorm, sunnyYNorm, sunnyCyNorm) = computeSunnyCropAndSunCenter(w, h, safeTopFrac)

        if (rawPhoto != null) {
            val srcRatio = rawPhoto.width.toFloat() / rawPhoto.height.coerceAtLeast(1).toFloat()
            val dstRatio = w / h
            val srcRect = if (srcRatio > dstRatio) {
                val cw = (rawPhoto.height * dstRatio).roundToInt()
                val cx = ((rawPhoto.width - cw) / 2).coerceAtLeast(0)
                Rect(cx, 0, (cx + cw).coerceAtMost(rawPhoto.width), rawPhoto.height)
            } else {
                val ch = (rawPhoto.width / dstRatio).roundToInt()
                val cy = if (isFullSunny) {
                    (rawPhoto.height * sunnyCyNorm).roundToInt()
                        .safeClamp(0, (rawPhoto.height - ch).coerceAtLeast(0))
                } else {
                    val yAnchor = if (isDay && condition == WeatherCondition.PARTLY_CLOUDY_DAY) {
                        0.08f
                    } else {
                        0.20f
                    }
                    ((rawPhoto.height - ch) * yAnchor).roundToInt().coerceAtLeast(0)
                }
                Rect(0, cy, rawPhoto.width, (cy + ch).coerceAtMost(rawPhoto.height))
            }

            if (isFullSunny) {
                // Reduce full-sun white glare by ~12% and warm highlights toward rich solar amber-orange
                val sunnyMatrix = ColorMatrix(
                    floatArrayOf(
                        0.89f, 0f, 0f, 0f, 4f,
                        0f, 0.85f, 0f, 0f, -2f,
                        0f, 0f, 0.81f, 0f, -6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(sunnyMatrix)
            }
            canvas.drawBitmap(rawPhoto, srcRect, Rect(0, 0, safeW, safeH), paint)
            paint.colorFilter = null
            rawPhoto.recycle()
        } else {
            paint.color = Color.parseColor("#0B1B34")
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        // 2. Readability scrim (balanced on Sunny days to curb glare while keeping sky rich)
        paint.style = Paint.Style.FILL
        val topScrim = if (isFullSunny) "#2E081426" else "#48050D1C"
        val midScrim = if (isFullSunny) "#24081426" else "#36061022"
        val botScrim = if (isFullSunny) "#74051020" else "#86040A17"
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor(topScrim),
                Color.parseColor(midScrim),
                Color.parseColor(botScrim)
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 3. Upper-Right 3D Celestial Stage (strictly below topReservedFraction when Time Display is ON)
        if (!hasTornadoWarning) {
            if (!isDay) {
                val (moonX, moonY, moonR) = computeMoonStageGeometry(
                    w = w,
                    h = h,
                    topReservedFraction = safeTopFrac,
                    maxStageBottomFraction = maxMoonBottomFraction
                )
                val targetDia = moonR * 2f

                val illum = moonPhase.illuminationFraction
                val coronaR = moonR * 1.90f
                val peakAlpha = (115 * (0.45f + 0.55f * illum)).roundToInt().safeClamp(40, 128)
                paint.style = Paint.Style.FILL
                paint.shader = RadialGradient(
                    moonX, moonY, coronaR,
                    intArrayOf(
                        Color.argb(peakAlpha, 224, 242, 254),
                        Color.argb((peakAlpha * 0.48f).roundToInt(), 125, 211, 252),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.50f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(moonX, moonY, coronaR, paint)
                paint.shader = null

                val moonBmp = LunarBitmapRenderer.renderMoonBitmap(
                    context = context,
                    moonPhase = moonPhase,
                    sizePx = (targetDia * 1.35f).roundToInt().safeClamp(128, 384)
                )
                val moonDst = RectF(moonX - moonR, moonY - moonR, moonX + moonR, moonY + moonR)
                canvas.drawBitmap(moonBmp, null, moonDst, paint)

                val isClearNight = condition == WeatherCondition.CLEAR_NIGHT && safeClouds < 18
                if (!isClearNight) {
                    val farWisp = LunarBitmapRenderer.renderVolumetricWispBitmap(
                        context = context,
                        isMoonlit = true,
                        isStorm = isStorm
                    )
                    val fw = moonR * 2.20f
                    val fh = fw * 0.54f
                    val fLeft = (moonX - fw * 0.52f).safeClamp(w * 0.25f, (w - fw).coerceAtLeast(w * 0.25f))
                    val fTop = moonY + moonR * 0.40f
                    paint.alpha = 110
                    canvas.drawBitmap(farWisp, null, RectF(fLeft, fTop, fLeft + fw, fTop + fh), paint)
                    paint.alpha = 255
                }
            } else if (isFullSunny) {
                // Warm golden-orange glaze over the sun disc so it glows warm amber-orange instead of harsh white
                val sunX = w * sunnyXNorm
                val sunY = h * sunnyYNorm
                val coronaR = min(w, h) * 0.40f
                paint.style = Paint.Style.FILL
                paint.shader = RadialGradient(
                    sunX, sunY, coronaR,
                    intArrayOf(
                        Color.parseColor("#78EA580C"),
                        Color.parseColor("#5EF97316"),
                        Color.parseColor("#38F59E0B"),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.34f, 0.68f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(sunX, sunY, coronaR, paint)

                // Sculpted warm golden-amber solar disc (soft cream-gold center -> rich orange rim)
                val discR = min(w, h) * 0.145f
                paint.shader = RadialGradient(
                    sunX - discR * 0.16f,
                    sunY - discR * 0.16f,
                    discR * 1.25f,
                    intArrayOf(
                        Color.parseColor("#E6FFF3B0"),
                        Color.parseColor("#E0FACC15"),
                        Color.parseColor("#D8FB923C"),
                        Color.parseColor("#BAEA580C"),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.36f, 0.68f, 0.90f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(sunX, sunY, discR * 1.22f, paint)
                paint.shader = null
            }

            // When Time Display is ON, keep top header band clean above the Moon/Sun so Time sits strictly on top
            if (safeTopFrac > 0.01f) {
                val bandBottom = h * (safeTopFrac + 0.04f)
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(
                    0f, 0f, 0f, bandBottom,
                    intArrayOf(
                        Color.parseColor(if (isFullSunny) "#B80B3B78" else "#B8060F20"),
                        Color.parseColor(if (isFullSunny) "#660E4B94" else "#66071328"),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.72f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, bandBottom, paint)
                paint.shader = null
            }

            if (isStorm && !suppressStaticRainStreaks) {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 2.2f
                paint.shader = null
                paint.color = Color.parseColor("#68BAE6FD")
                for (i in 0 until 16) {
                    val rx = w * ((i * 23 + 7) % 100) / 100f
                    val ry = h * ((i * 37 + 13) % 85) / 100f
                    canvas.drawLine(rx, ry, rx - 7f, ry + 24f, paint)
                }
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#44FFFFFF")
        canvas.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), cornerRadiusPx, cornerRadiusPx, paint)

        return outBmp
    }

    /**
     * Renders a vertically seamless 3-depth 3D falling rain curtain for widgets larger than `2x2`.
     * Paired with linear `-100%p -> 0%p` / `0%p -> +100%p` translation (zero alpha fade, zero splashes),
     * rain falls in a smooth, continuous, unbroken stream without pulsing or hitting the screen.
     */
    fun renderSeamlessFallingRainCurtain(
        condition: WeatherCondition,
        hasTornadoWarning: Boolean,
        isLargerThan2x2: Boolean,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        val isRainy = condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.THUNDERSTORM ||
            hasTornadoWarning
        if (!isLargerThan2x2 || !isRainy) return null

        val safeW = (widthPx * 0.88f).roundToInt().safeClamp(260, 384)
        val safeH = (heightPx * 0.88f).roundToInt().safeClamp(180, 288)
        val w = safeW.toFloat()
        val h = safeH.toFloat()
        val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val isDrizzle = condition == WeatherCondition.DRIZZLE && !hasTornadoWarning
        val streakCount = if (isDrizzle) 36 else 52
        val slantFactor = if (condition == WeatherCondition.THUNDERSTORM || hasTornadoWarning) 0.11f else 0.08f

        for (i in 0 until streakCount) {
            val depthLayer = when {
                i % 5 == 0 -> 2 // Foreground 3D rain
                i % 2 == 0 -> 1 // Mid-ground rain
                else -> 0       // Far atmospheric rain
            }
            val seedX = (((i * 61 + 19) % 1000) / 1000f)
            val seedY = (((i * 137 + 43) % 1000) / 1000f)
            val sx = w * (0.02f + seedX * 0.96f)
            val sy = h * seedY

            val streakLen = when (depthLayer) {
                0 -> h * 0.105f
                1 -> h * 0.165f
                else -> h * 0.235f
            }
            val strokeW = when (depthLayer) {
                0 -> 0.85f
                1 -> 1.35f
                else -> 1.90f
            }
            val ex = sx - streakLen * slantFactor
            val ey = sy + streakLen

            val midColor = when (depthLayer) {
                0 -> Color.parseColor("#327DD3FC")
                1 -> Color.parseColor("#547DD3FC")
                else -> Color.parseColor("#7EBAE6FD")
            }
            val tipColor = when (depthLayer) {
                0 -> Color.parseColor("#5ABAE6FD")
                1 -> Color.parseColor("#98E0F2FE")
                else -> Color.parseColor("#D8F8FAFC")
            }

            paint.strokeWidth = strokeW
            for (wrap in -1..1) {
                val dy = wrap * h
                val y0 = sy + dy
                val y1 = ey + dy
                if (y1 >= -4f && y0 <= h + 4f) {
                    paint.shader = LinearGradient(
                        sx, y0, ex, y1,
                        intArrayOf(Color.TRANSPARENT, midColor, tipColor),
                        floatArrayOf(0f, 0.58f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawLine(sx, y0, ex, y1, paint)
                }
            }
        }
        paint.shader = null
        return bmp
    }

    /**
     * Renders 3 distinct rotating & pulsing warm golden-orange solar ray frames for `widget_sun_pulse_flipper`
     * so full Sunny (`CLEAR_DAY`) widgets show visibly radiating, rotating sunbeams without harsh white glare.
     */
    fun renderSolarRayFrames(
        condition: WeatherCondition,
        isDay: Boolean,
        hasTornadoWarning: Boolean,
        widthPx: Int,
        heightPx: Int,
        topReservedFraction: Float = 0f
    ): Triple<Bitmap, Bitmap, Bitmap>? {
        if (hasTornadoWarning || !isDay || condition != WeatherCondition.CLEAR_DAY) {
            return null
        }

        val safeTopFrac = topReservedFraction.safeClamp(0f, 0.34f)
        val safeW = (widthPx * 0.54f).roundToInt().safeClamp(152, 232)
        val safeH = (heightPx * 0.54f).roundToInt().safeClamp(116, 188)
        val w = safeW.toFloat()
        val h = safeH.toFloat()
        val (sunXNorm, sunYNorm, _) = computeSunnyCropAndSunCenter(
            widthPx.toFloat(),
            heightPx.toFloat(),
            safeTopFrac
        )
        val sunX = w * sunXNorm
        val sunY = h * sunYNorm
        val baseReach = max(w, h) * 0.78f
        val innerStart = min(w, h) * 0.10f
        val rayCount = 14
        val stepDeg = 360f / rayCount

        fun buildFrame(frameIndex: Int): Bitmap {
            val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val phaseAngleOffset = frameIndex * (stepDeg / 3f)

            // Warm amber-orange corona ring (zero pure white so sun stays rich & glare-free)
            val ringR = min(w, h) * (0.34f + 0.03f * frameIndex)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                sunX, sunY, ringR,
                intArrayOf(
                    Color.parseColor("#66FACC15"),
                    Color.parseColor("#54FB923C"),
                    Color.parseColor("#2CF97316"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sunX, sunY, ringR, paint)

            // 14 tapered crepuscular golden-orange rays rotating & pulsing across frames 0 -> 1 -> 2
            paint.shader = RadialGradient(
                sunX, sunY, baseReach,
                intArrayOf(
                    Color.parseColor("#CCFB923C"),
                    Color.parseColor("#B0FBBF24"),
                    Color.parseColor("#5EF97316"),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.24f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )

            val path = Path()
            for (i in 0 until rayCount) {
                val centerDeg = i * stepDeg + phaseAngleOffset
                val isPrimary = (i + frameIndex) % 2 == 0
                val reach = if (isPrimary) baseReach else baseReach * 0.62f
                val halfSpreadDeg = if (isPrimary) 4.4f else 2.8f

                val radCenter = Math.toRadians(centerDeg.toDouble())
                val radLeft = Math.toRadians((centerDeg - halfSpreadDeg * 0.55f).toDouble())
                val radRight = Math.toRadians((centerDeg + halfSpreadDeg * 0.55f).toDouble())
                val radOuterLeft = Math.toRadians((centerDeg - halfSpreadDeg).toDouble())
                val radOuterRight = Math.toRadians((centerDeg + halfSpreadDeg).toDouble())

                path.reset()
                path.moveTo(
                    sunX + innerStart * cos(radLeft).toFloat(),
                    sunY + innerStart * sin(radLeft).toFloat()
                )
                path.lineTo(
                    sunX + reach * cos(radOuterLeft).toFloat(),
                    sunY + reach * sin(radOuterLeft).toFloat()
                )
                path.lineTo(
                    sunX + (reach * 1.04f) * cos(radCenter).toFloat(),
                    sunY + (reach * 1.04f) * sin(radCenter).toFloat()
                )
                path.lineTo(
                    sunX + reach * cos(radOuterRight).toFloat(),
                    sunY + reach * sin(radOuterRight).toFloat()
                )
                path.lineTo(
                    sunX + innerStart * cos(radRight).toFloat(),
                    sunY + innerStart * sin(radRight).toFloat()
                )
                path.close()
                if (safeTopFrac > 0.01f) {
                    canvas.save()
                    canvas.clipRect(0f, h * safeTopFrac, w, h)
                    canvas.drawPath(path, paint)
                    canvas.restore()
                } else {
                    canvas.drawPath(path, paint)
                }
            }
            paint.shader = null
            return bmp
        }

        return Triple(buildFrame(0), buildFrame(1), buildFrame(2))
    }

    /**
     * Renders 3 distinct weather-appropriate drifting 3D cloud variations (11s per pass = 33s cycle)
     * so floating clouds never look like a single repeating image:
     * - Sunny (`CLEAR_DAY`): 3 varied staggered frames of ultra-faint wispy clouds flowing slowly
     *   in the lower background — strictly away from the upper-right Sun!
     * - Clear Night (`CLEAR_NIGHT` with `< 15%` clouds): Returns `null` so starry night stays clear.
     * - Partly Sunny / Cloudy / Night Clouds / Rain / Snow: 3 distinct 3D cloud formations (`variant = 0, 1, 2`).
     */
    fun renderDriftingCloudFrames(
        context: Context,
        condition: WeatherCondition,
        isDay: Boolean,
        cloudCoverPercent: Int,
        widthPx: Int,
        heightPx: Int,
        topReservedFraction: Float = 0f
    ): Triple<Bitmap, Bitmap, Bitmap>? {
        val safeClouds = cloudCoverPercent.safeClamp(0, 100)
        if (!isDay && condition == WeatherCondition.CLEAR_NIGHT && safeClouds < 15) {
            return null
        }

        val safeTopFrac = topReservedFraction.safeClamp(0f, 0.32f)

        val safeW = (widthPx * 0.46f).roundToInt().safeClamp(144, 184)
        val safeH = (heightPx * 0.46f).roundToInt().safeClamp(108, 144)
        val w = safeW.toFloat()
        val h = safeH.toFloat()

        val isStorm = condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.THUNDERSTORM

        val wisp0 = LunarBitmapRenderer.renderVolumetricWispBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStorm,
            variant = 0
        )
        val wisp1 = LunarBitmapRenderer.renderVolumetricWispBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStorm,
            variant = 1
        )
        val wisp2 = LunarBitmapRenderer.renderVolumetricWispBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStorm,
            variant = 2
        )

        // SUNNY DAY (`CLEAR_DAY`): 3 distinct faint background wisp variations (never crossing the upper-right Sun!)
        if (isDay && condition == WeatherCondition.CLEAR_DAY) {
            val frame1 = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            val c1 = Canvas(frame1)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            p.alpha = 50
            c1.drawBitmap(wisp1, null, RectF(w * 0.06f, h * 0.44f, w * 0.48f, h * 0.62f), p)
            p.alpha = 36
            c1.drawBitmap(wisp0, null, RectF(w * 0.36f, h * 0.56f, w * 0.68f, h * 0.72f), p)

            val frame2 = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            val c2 = Canvas(frame2)
            p.alpha = 46
            c2.drawBitmap(wisp2, null, RectF(w * 0.12f, h * 0.32f, w * 0.46f, h * 0.50f), p)
            p.alpha = 40
            c2.drawBitmap(wisp1, null, RectF(w * 0.28f, h * 0.52f, w * 0.66f, h * 0.68f), p)

            val frame3 = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
            val c3 = Canvas(frame3)
            p.alpha = 48
            c3.drawBitmap(wisp0, null, RectF(w * 0.16f, h * 0.38f, w * 0.52f, h * 0.56f), p)
            p.alpha = 38
            c3.drawBitmap(wisp2, null, RectF(w * 0.08f, h * 0.56f, w * 0.44f, h * 0.73f), p)

            return Triple(frame1, frame2, frame3)
        }

        // CLOUDY / PARTLY CLOUDY / OVERCAST / RAIN / SNOW / MOONLIT NIGHT CLOUDS:
        // Render 3 distinct 3D cloud silhouettes & depth arrangements across the 33s cycle
        val sculpted = LunarBitmapRenderer.renderCloudIconBitmap(
            context = context,
            isMoonlit = !isDay,
            isStorm = isStorm
        )

        val (celX, celY, celR) = computeMoonStageGeometry(w, h, safeTopFrac, 0.56f)

        val cw = (celR * 1.90f).coerceAtMost(w * 0.44f)
        val ch = cw * 0.68f
        val minCloudTop = max(h * 0.18f, h * safeTopFrac + 2f)
        val cLeft = (celX - cw * 0.72f).safeClamp(w * 0.24f, w * 0.82f - cw)
        val cTop = (celY + celR * 0.48f - ch * 0.16f)
            .safeClamp(minCloudTop, (h * 0.68f - ch).coerceAtLeast(minCloudTop))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Variation Frame 1: Billowing volumetric wisp (variant 0) + sculpted 3D foreground cloud
        val frame1 = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        Canvas(frame1).apply {
            val wispW = w * 0.48f
            val wispH = wispW * 0.56f
            paint.alpha = if (!isDay) 150 else 172
            drawBitmap(wisp0, null, RectF(w * 0.14f, h * 0.21f, w * 0.14f + wispW, h * 0.21f + wispH), paint)
            paint.alpha = 228
            drawBitmap(sculpted, null, RectF(cLeft, cTop, cLeft + cw, cTop + ch), paint)
        }

        // Variation Frame 2: Windswept stratocumulus tendrils (variant 1) + asymmetric 3D cumulus cluster (variant 2)
        val frame2 = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        Canvas(frame2).apply {
            val streakW = w * 0.60f
            val streakH = streakW * 0.52f
            paint.alpha = if (!isDay) 162 else 184
            drawBitmap(wisp1, null, RectF(w * 0.07f, h * 0.26f, w * 0.07f + streakW, h * 0.26f + streakH), paint)

            val clusterW = (cw * 1.18f).coerceAtMost(w * 0.52f)
            val clusterH = clusterW * 0.58f
            val clLeft = (cLeft - w * 0.04f).safeClamp(w * 0.20f, w * 0.88f - clusterW)
            val clTop = (cTop + h * 0.03f).safeClamp(h * 0.20f, h * 0.72f - clusterH)
            paint.alpha = if (!isDay) 210 else 224
            drawBitmap(wisp2, null, RectF(clLeft, clTop, clLeft + clusterW, clTop + clusterH), paint)
        }

        // Variation Frame 3: High asymmetric cumulus + trailing puffs (variant 2) + low stratocumulus wisp + compact cloud
        val frame3 = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        Canvas(frame3).apply {
            val highW = w * 0.52f
            val highH = highW * 0.56f
            paint.alpha = if (!isDay) 156 else 178
            drawBitmap(wisp2, null, RectF(w * 0.18f, h * 0.15f, w * 0.18f + highW, h * 0.15f + highH), paint)

            val lowW = w * 0.46f
            val lowH = lowW * 0.50f
            paint.alpha = if (!isDay) 128 else 148
            drawBitmap(wisp1, null, RectF(w * 0.24f, h * 0.44f, w * 0.24f + lowW, h * 0.44f + lowH), paint)

            val altCw = cw * 0.85f
            val altCh = ch * 0.85f
            val altLeft = (cLeft + w * 0.05f).safeClamp(w * 0.26f, w * 0.86f - altCw)
            val altTop = (cTop + h * 0.04f).safeClamp(h * 0.22f, h * 0.72f - altCh)
            paint.alpha = 216
            drawBitmap(sculpted, null, RectF(altLeft, altTop, altLeft + altCw, altTop + altCh), paint)
        }

        return Triple(frame1, frame2, frame3)
    }

    fun renderMiniGlyph(
        context: Context,
        condition: WeatherCondition,
        sizePx: Int = 50
    ): Bitmap {
        val safe = sizePx.coerceAtLeast(32)
        val bmp = Bitmap.createBitmap(safe, safe, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val s = safe.toFloat()

        val isStorm = condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.THUNDERSTORM
        val cloud = LunarBitmapRenderer.renderCloudIconBitmap(
            context = context,
            isMoonlit = false,
            isStorm = isStorm
        )

        when (condition) {
            WeatherCondition.CLEAR_DAY, WeatherCondition.CLEAR_NIGHT -> {
                draw3DRadiantSunBitmap(canvas, paint, s * 0.5f, s * 0.5f, s * 0.28f)
            }
            WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                draw3DRadiantSunBitmap(canvas, paint, s * 0.62f, s * 0.34f, s * 0.21f)
                canvas.drawBitmap(cloud, null, RectF(s * 0.04f, s * 0.30f, s * 0.92f, s * 0.96f), paint)
            }
            WeatherCondition.OVERCAST, WeatherCondition.FOG -> {
                canvas.drawBitmap(cloud, null, RectF(s * 0.04f, s * 0.20f, s * 0.96f, s * 0.88f), paint)
            }
            WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> {
                canvas.drawBitmap(cloud, null, RectF(s * 0.04f, s * 0.10f, s * 0.94f, s * 0.72f), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = s * 0.065f
                paint.shader = null
                paint.color = Color.parseColor("#38BDF8")
                canvas.drawLine(s * 0.34f, s * 0.70f, s * 0.29f, s * 0.90f, paint)
                canvas.drawLine(s * 0.53f, s * 0.70f, s * 0.48f, s * 0.90f, paint)
                canvas.drawLine(s * 0.71f, s * 0.70f, s * 0.66f, s * 0.90f, paint)
            }
            WeatherCondition.SNOW -> {
                canvas.drawBitmap(cloud, null, RectF(s * 0.04f, s * 0.10f, s * 0.94f, s * 0.72f), paint)
                paint.style = Paint.Style.FILL
                paint.shader = null
                paint.color = Color.parseColor("#E0F2FE")
                canvas.drawCircle(s * 0.34f, s * 0.80f, s * 0.055f, paint)
                canvas.drawCircle(s * 0.52f, s * 0.86f, s * 0.055f, paint)
                canvas.drawCircle(s * 0.70f, s * 0.80f, s * 0.055f, paint)
            }
            WeatherCondition.THUNDERSTORM -> {
                canvas.drawBitmap(cloud, null, RectF(s * 0.04f, s * 0.08f, s * 0.94f, s * 0.70f), paint)
                drawLightningBolt(canvas, paint, s, s)
            }
        }
        return bmp
    }

    private fun drawLightningBolt(canvas: Canvas, paint: Paint, w: Float, h: Float) {
        val bolt = Path().apply {
            moveTo(w * 0.56f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.74f)
            lineTo(w * 0.53f, h * 0.74f)
            lineTo(w * 0.46f, h * 0.95f)
            lineTo(w * 0.65f, h * 0.69f)
            lineTo(w * 0.53f, h * 0.69f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, h * 0.54f, 0f, h * 0.95f,
            Color.parseColor("#FEF08A"),
            Color.parseColor("#F59E0B"),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(bolt, paint)
        paint.shader = null
    }

    private fun draw3DRadiantSunBitmap(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float) {
        val safeHaloR = minOf(
            r * 1.55f,
            (cx - 2f).coerceAtLeast(r),
            (cy - 2f).coerceAtLeast(r),
            (canvas.width - cx - 2f).coerceAtLeast(r),
            (canvas.height - cy - 2f).coerceAtLeast(r)
        ).coerceAtLeast(r * 1.05f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, safeHaloR,
            intArrayOf(
                Color.parseColor("#99FEF08A"),
                Color.parseColor("#55FB923C"),
                Color.parseColor("#24F472B6"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.42f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, safeHaloR, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * 0.20f
        paint.color = Color.parseColor("#FBBF24")
        for (i in 0 until 8) {
            val rad = Math.toRadians((i * 45).toDouble())
            val x1 = cx + (r * 1.18f) * cos(rad).toFloat()
            val y1 = cy + (r * 1.18f) * sin(rad).toFloat()
            val x2 = cx + (r * 1.50f) * cos(rad).toFloat()
            val y2 = cy + (r * 1.50f) * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.25f, r * 1.35f,
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#FEF08A"),
                Color.parseColor("#F59E0B"),
                Color.parseColor("#EA580C")
            ),
            floatArrayOf(0f, 0.38f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }
}
