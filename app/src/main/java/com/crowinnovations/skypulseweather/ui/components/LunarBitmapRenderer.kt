package com.crowinnovations.skypulseweather.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.crowinnovations.skypulseweather.R
import com.crowinnovations.skypulseweather.domain.MoonPhaseInfo
import com.crowinnovations.skypulseweather.domain.safeClamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Photorealistic 3D Lunar & Volumetric Cloud Bitmap Engine shared by both Jetpack Compose
 * (Full-Screen SkyBackdrop + Forecast Icons) and Jetpack Glance Home-Screen Widgets.
 *
 * Uses high-resolution telescopic lunar albedo photography (`R.drawable.moon_albedo_3d`) with
 * per-pixel 3D spherical Lommel-Seeliger reflectance, high-relief crater-gradient normal
 * perturbation along the waxing/waning terminator, and high-contrast obsidian-sapphire Earthshine
 * so the exact waxing or waning phase is immediately unmistakable at any display size.
 */
object LunarBitmapRenderer {

    private class AlbedoData(
        val size: Int,
        val pixels: IntArray
    )

    private class Buffer2D(
        val width: Int,
        val height: Int,
        val pixels: IntArray
    )

    @Volatile
    private var cachedAlbedo: AlbedoData? = null

    @Volatile
    private var cachedIcon2D: Buffer2D? = null

    private val cachedWispVariants = arrayOfNulls<Buffer2D>(3)

    private val moonBitmapCache = object : LruCache<String, Bitmap>(18) {}
    private val cloudSpriteCache = object : LruCache<String, Bitmap>(20) {}

    private fun getAlbedo(context: Context): AlbedoData? {
        cachedAlbedo?.let { return it }
        synchronized(this) {
            cachedAlbedo?.let { return it }
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeResource(
                context.applicationContext.resources,
                R.drawable.moon_albedo_3d,
                opts
            ) ?: return null
            val s = min(bmp.width, bmp.height)
            val px = IntArray(s * s)
            bmp.getPixels(px, 0, s, 0, 0, s, s)
            bmp.recycle()
            return AlbedoData(s, px).also { cachedAlbedo = it }
        }
    }

    private fun ensureCloudIcon2D(context: Context): Buffer2D? {
        cachedIcon2D?.let { return it }
        synchronized(this) {
            cachedIcon2D?.let { return it }
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeResource(
                context.applicationContext.resources,
                R.drawable.cloud_icon_3d,
                opts
            ) ?: return null
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            return Buffer2D(w, h, px).also { cachedIcon2D = it }
        }
    }

    private fun ensureCloudWisp2D(context: Context, variant: Int): Buffer2D? {
        val safeIdx = variant.safeClamp(0, 2)
        cachedWispVariants[safeIdx]?.let { return it }
        synchronized(this) {
            cachedWispVariants[safeIdx]?.let { return it }
            val resId = when (safeIdx) {
                1 -> R.drawable.cloud_volumetric_var2_3d
                2 -> R.drawable.cloud_volumetric_var3_3d
                else -> R.drawable.cloud_volumetric_3d
            }
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeResource(
                context.applicationContext.resources,
                resId,
                opts
            ) ?: return null
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            return Buffer2D(w, h, px).also { cachedWispVariants[safeIdx] = it }
        }
    }

    /**
     * Maps astronomical illumination fraction to a visually unmistakable 3D selenocentric
     * terminator angle so Waxing (right-lit, left-shadowed) vs Waning (left-lit, right-shadowed)
     * is immediately clear at a single glance on both widgets and full-screen displays.
     */
    private fun visualTerminatorAngleDeg(moonPhase: MoonPhaseInfo): Float {
        val illum = moonPhase.illuminationFraction.safeClamp(0f, 1f)
        return when {
            illum >= 0.988f -> 0f // True Full Moon peak
            illum >= 0.56f -> {
                // Gibbous (56%..98.8%): 56°..84° -> 78%..55% lit width with bold curved 3D shadow limb!
                val t = ((0.988f - illum) / (0.988f - 0.56f)).safeClamp(0f, 1f)
                56f + t * 28f
            }
            illum >= 0.44f -> {
                // First / Third Quarter: crisp ~90° vertical 3D cratered terminator
                val t = ((0.56f - illum) / 0.12f).safeClamp(0f, 1f)
                84f + t * 12f
            }
            illum >= 0.02f -> {
                // Crescent (2%..44%): 96°..152° -> bold luminous 3D crescent
                val t = ((0.44f - illum) / 0.42f).safeClamp(0f, 1f)
                96f + t * 56f
            }
            else -> 170f // New Moon with faint silver limb rim
        }.safeClamp(0f, 174f)
    }

    /**
     * Renders a large, tack-sharp 3D spherical Moon bitmap for any astronomical waxing/waning phase.
     */
    fun renderMoonBitmap(
        context: Context,
        moonPhase: MoonPhaseInfo,
        sizePx: Int = 360
    ): Bitmap {
        val safeSize = sizePx.safeClamp(40, 448)
        val qSize = ((safeSize + 4) / 8 * 8).safeClamp(40, 448)

        val effectiveAngleDeg = visualTerminatorAngleDeg(moonPhase)

        val cacheKey = "v2:$qSize:${effectiveAngleDeg.roundToInt()}:${moonPhase.isWaxing}"
        synchronized(moonBitmapCache) {
            moonBitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        }

        val albedo = getAlbedo(context)
            ?: return Bitmap.createBitmap(qSize, qSize, Bitmap.Config.ARGB_8888)

        val srcSize = albedo.size
        val srcPx = albedo.pixels
        val outPx = IntArray(qSize * qSize)

        val outCenter = (qSize - 1) * 0.5f
        val outR = qSize * 0.488f
        val invOutR = 1f / outR

        val rad = effectiveAngleDeg * PI / 180.0
        val sx = ((if (moonPhase.isWaxing) 1.0 else -1.0) * sin(rad)).toFloat()
        val sz = cos(rad).toFloat()

        val srcMaxIdx = srcSize - 2
        val srcScale = (srcSize - 1).toFloat()

        for (y in 0 until qSize) {
            val ny = (y - outCenter) * invOutR
            val ny2 = ny * ny
            val rowOffset = y * qSize

            for (x in 0 until qSize) {
                val nx = (x - outCenter) * invOutR
                val r2 = nx * nx + ny2
                if (r2 > 1.024f) {
                    outPx[rowOffset + x] = 0
                    continue
                }

                val r = sqrt(r2)
                val clampedR2 = if (r2 > 1f) 1f else r2
                val nz = sqrt((1f - clampedR2).coerceAtLeast(0f))

                val ax = (((nx * 0.488f) + 0.5f) * srcScale).roundToInt().safeClamp(1, srcMaxIdx)
                val ay = (((ny * 0.488f) + 0.5f) * srcScale).roundToInt().safeClamp(1, srcMaxIdx)

                val c = srcPx[ay * srcSize + ax]
                val cr = (c ushr 16) and 0xFF
                val cg = (c ushr 8) and 0xFF
                val cb = c and 0xFF

                // High-relief 3D crater & mountain slope perturbation right along the terminator!
                val step = if (srcSize >= 256) 2 else 1
                val cx1 = srcPx[ay * srcSize + min(srcSize - 1, ax + step)]
                val cx0 = srcPx[ay * srcSize + max(0, ax - step)]
                val cy1 = srcPx[min(srcSize - 1, ay + step) * srcSize + ax]
                val cy0 = srcPx[max(0, ay - step) * srcSize + ax]

                val lumX1 = ((cx1 ushr 16) and 0xFF) + ((cx1 ushr 8) and 0xFF) + (cx1 and 0xFF)
                val lumX0 = ((cx0 ushr 16) and 0xFF) + ((cx0 ushr 8) and 0xFF) + (cx0 and 0xFF)
                val lumY1 = ((cy1 ushr 16) and 0xFF) + ((cy1 ushr 8) and 0xFF) + (cy1 and 0xFF)
                val lumY0 = ((cy0 ushr 16) and 0xFF) + ((cy0 ushr 8) and 0xFF) + (cy0 and 0xFF)

                val dLx = (lumX1 - lumX0) * (0.72f / 765f)
                val dLy = (lumY1 - lumY0) * (0.72f / 765f)

                val bnx = nx - dLx
                val bny = ny - dLy
                val bInvLen = 1f / sqrt(bnx * bnx + bny * bny + nz * nz).coerceAtLeast(1e-4f)

                val rawDot = nx * sx + nz * sz
                val bumpDot = (bnx * bInvLen) * sx + (nz * bInvLen) * sz
                val termWeight = (1f - abs(rawDot - 0.05f) / 0.28f).coerceIn(0f, 1f)
                val dot = rawDot * (1f - 0.68f * termWeight) + bumpDot * (0.68f * termWeight)

                // Crisp penumbra + bright Lommel-Seeliger sunlit lunar surface
                val light = when {
                    dot <= -0.025f -> 0f
                    dot < 0.11f -> {
                        val t = ((dot + 0.025f) / 0.135f).coerceIn(0f, 1f)
                        val smooth = t * t * (3f - 2f * t)
                        smooth * 0.72f
                    }
                    else -> {
                        min(1.10f, 0.58f + 0.54f * dot.pow(0.38f))
                    }
                }

                // Deep, high-contrast obsidian-sapphire night side so waxing/waning curve pops boldly!
                val darkLum = cr * 0.30f + cg * 0.59f + cb * 0.11f
                val unlitMix = (1f - min(1f, light * 1.45f)).coerceIn(0f, 1f)
                val earthshine = 0.045f + 0.025f * (1f - nz)

                // Subtle silver-blue outer limb rim on the dark side so the full lunar sphere stays legible
                val darkRimGlow = if (r in 0.945f..0.995f && unlitMix > 0.2f) {
                    val rt = 1f - abs(r - 0.974f) / 0.026f
                    rt.coerceIn(0f, 1f) * 34f * unlitMix
                } else {
                    0f
                }

                val outR8 = (cr * light * 1.05f + (darkLum * 0.55f + 18f) * earthshine * unlitMix + darkRimGlow * 0.65f)
                    .roundToInt().safeClamp(0, 255)
                val outG8 = (cg * light * 1.04f + (darkLum * 0.80f + 34f) * earthshine * unlitMix + darkRimGlow * 0.85f)
                    .roundToInt().safeClamp(0, 255)
                val outB8 = (cb * light * 1.01f + (darkLum * 1.25f + 64f) * earthshine * unlitMix + darkRimGlow * 1.15f)
                    .roundToInt().safeClamp(0, 255)

                val limbFade = if (r > 0.982f) {
                    ((1.014f - r) / 0.032f).coerceIn(0f, 1f)
                } else {
                    1f
                }
                // High-contrast 89% night-side opacity so bright sky clouds behind the Moon don't wash out the shadow!
                val phaseOpacity = 0.89f + 0.11f * min(1f, light * 2.0f)
                val outA8 = (limbFade * phaseOpacity * 255f).roundToInt().safeClamp(0, 255)

                outPx[rowOffset + x] = (outA8 shl 24) or (outR8 shl 16) or (outG8 shl 8) or outB8
            }
        }

        val result = Bitmap.createBitmap(qSize, qSize, Bitmap.Config.ARGB_8888)
        result.setPixels(outPx, 0, qSize, 0, 0, qSize, qSize)
        synchronized(moonBitmapCache) {
            moonBitmapCache.put(cacheKey, result)
        }
        return result
    }

    fun renderCloudIconBitmap(
        context: Context,
        isMoonlit: Boolean,
        isStorm: Boolean
    ): Bitmap {
        val key = "icon:$isMoonlit:$isStorm"
        synchronized(cloudSpriteCache) {
            cloudSpriteCache.get(key)?.let { if (!it.isRecycled) return it }
        }
        val raw = ensureCloudIcon2D(context)
            ?: return Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)

        val shaded = shadeCloudBuffer(
            raw = raw,
            lightXFrac = if (isMoonlit) 0.76f else 0.72f,
            lightYFrac = if (isMoonlit) 0.14f else 0.10f,
            isMoonlit = isMoonlit,
            isStorm = isStorm
        )
        synchronized(cloudSpriteCache) {
            cloudSpriteCache.put(key, shaded)
        }
        return shaded
    }

    fun renderVolumetricWispBitmap(
        context: Context,
        isMoonlit: Boolean,
        isStorm: Boolean,
        variant: Int = 0
    ): Bitmap {
        val safeVar = variant.safeClamp(0, 2)
        val key = "wisp:$safeVar:$isMoonlit:$isStorm"
        synchronized(cloudSpriteCache) {
            cloudSpriteCache.get(key)?.let { if (!it.isRecycled) return it }
        }
        val raw = ensureCloudWisp2D(context, safeVar)
            ?: return Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888)

        val shaded = shadeCloudBuffer(
            raw = raw,
            lightXFrac = if (isMoonlit) 0.74f else 0.68f,
            lightYFrac = 0.08f,
            isMoonlit = isMoonlit,
            isStorm = isStorm
        )
        synchronized(cloudSpriteCache) {
            cloudSpriteCache.put(key, shaded)
        }
        return shaded
    }

    private fun shadeCloudBuffer(
        raw: Buffer2D,
        lightXFrac: Float,
        lightYFrac: Float,
        isMoonlit: Boolean,
        isStorm: Boolean
    ): Bitmap {
        val w = raw.width
        val h = raw.height
        val src = raw.pixels
        val out = IntArray(w * h)

        val lx = w * lightXFrac
        val ly = h * lightYFrac
        val invMaxDist = 1f / (sqrt((w * w + h * h).toFloat()) * 0.78f).coerceAtLeast(1f)

        for (y in 0 until h) {
            val dy = y - ly
            val row = y * w
            for (x in 0 until w) {
                val c = src[row + x]
                val a = (c ushr 24) and 0xFF
                if (a == 0) {
                    out[row + x] = 0
                    continue
                }
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF

                val dx = x - lx
                val dist = sqrt(dx * dx + dy * dy)
                val t = (1f - dist * invMaxDist).coerceIn(0f, 1f)
                val prox = t * t * (3f - 2f * t)

                val rMul: Float
                val gMul: Float
                val bMul: Float

                when {
                    isMoonlit && isStorm -> {
                        rMul = 0.18f + 0.66f * prox
                        gMul = 0.28f + 0.62f * prox
                        bMul = 0.46f + 0.54f * prox
                    }
                    isMoonlit -> {
                        rMul = 0.26f + 0.74f * prox
                        gMul = 0.38f + 0.65f * prox
                        bMul = 0.58f + 0.50f * prox
                    }
                    isStorm -> {
                        rMul = 0.34f + 0.56f * prox
                        gMul = 0.38f + 0.55f * prox
                        bMul = 0.46f + 0.50f * prox
                    }
                    else -> {
                        rMul = 0.72f + 0.32f * prox
                        gMul = 0.76f + 0.27f * prox
                        bMul = 0.84f + 0.18f * prox
                    }
                }

                val nr = (r * rMul).roundToInt().safeClamp(0, 255)
                val ng = (g * gMul).roundToInt().safeClamp(0, 255)
                val nb = (b * bMul).roundToInt().safeClamp(0, 255)
                out[row + x] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
    }
}
