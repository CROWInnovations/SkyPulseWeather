package com.crowinnovations.skypulseweather.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.crowinnovations.skypulseweather.domain.GeoBounds
import com.crowinnovations.skypulseweather.domain.HazardPolygonKind
import com.crowinnovations.skypulseweather.domain.LatLonPoint
import com.crowinnovations.skypulseweather.domain.RadarBaseMapStyle
import com.crowinnovations.skypulseweather.domain.RadarProjection
import com.crowinnovations.skypulseweather.domain.RadarTimelineFrame
import com.crowinnovations.skypulseweather.domain.RegionalPrecipMarker
import com.crowinnovations.skypulseweather.domain.SevereHazardFeature
import com.crowinnovations.skypulseweather.domain.TropicalCycloneFeature
import com.crowinnovations.skypulseweather.domain.safeClamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TileKind {
    BASE_MAP,
    BASE_LABELS,
    CLOUD_SATELLITE,
    RADAR_PRECIP,
    MRMS_PRECIP_TYPE
}

@Singleton
class RadarMapRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    // 52 MiB in-memory tile bitmap cache for instant 60-120fps pinch-zoom, pan & timeline scrubbing
    private val tileCache = object : LruCache<String, Bitmap>(52 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    // Dedicated high-concurrency tile HTTP client (24 parallel requests/host + 64 MiB persistent disk cache)
    // inheriting all TLS 1.2/1.3 + safe-public-host security interceptors from AppModule.provideOkHttpClient()
    private val tileHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        }
        val diskCache = runCatching {
            // Delete legacy v2 cache folder if present so no stale placeholder tiles remain on disk
            runCatching { File(appContext.cacheDir, "skypulse_radar_tiles_v2").deleteRecursively() }
            Cache(File(appContext.cacheDir, "skypulse_radar_tiles_v3"), 64L * 1024L * 1024L)
        }.getOrNull()

        okHttpClient.newBuilder()
            .dispatcher(dispatcher)
            .apply { if (diskCache != null) cache(diskCache) }
            .build()
    }

    // Repository-owned non-cancelling scope so panning/zooming never aborts in-flight HTTP tile downloads
    private val tileFetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(24))
    private val inFlightTiles = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val completionCallbacks = ConcurrentHashMap<String, CopyOnWriteArrayList<() -> Unit>>()

    fun getCachedTile(cacheKey: String): Bitmap? = tileCache.get(cacheKey)

    fun buildBaseTileUrl(style: RadarBaseMapStyle, z: Int, x: Int, y: Int): String = when (style) {
        RadarBaseMapStyle.SATELLITE_HYBRID ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        RadarBaseMapStyle.STREET_MAP ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$z/$y/$x"
    }

    fun buildSatelliteReferenceLabelsUrl(z: Int, x: Int, y: Int): String =
        "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/$z/$y/$x"

    fun buildCloudTileUrl(frame: RadarTimelineFrame, centerLon: Double, z: Int, x: Int, y: Int): String? {
        if (frame.isForecast) return null
        val inAmericas = centerLon in -172.0..-25.0
        return if (inAmericas) {
            val layer = if (centerLon < -106.0) {
                "GOES-West_ABI_Band13_Clean_Infrared"
            } else {
                "GOES-East_ABI_Band13_Clean_Infrared"
            }
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/$layer/default/${frame.gibsIsoTimestamp}/GoogleMapsCompatible_Level6/$z/$y/$x.png"
        } else {
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate/default/default/GoogleMapsCompatible_Level6/$z/$y/$x.png"
        }
    }

    fun buildRadarTileUrl(frame: RadarTimelineFrame, centerLat: Double, centerLon: Double, z: Int, x: Int, y: Int): String? {
        val inNorthAmericaRadar = centerLat in 15.0..62.0 && centerLon in -165.0..-52.0
        return if (inNorthAmericaRadar) {
            val layer = if (frame.isForecast) frame.iemHrrrLayer else frame.iemNexradLayer
            layer?.let { "https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/$it/$z/$x/$y.png" }
        } else if (!frame.isForecast) {
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate/default/default/GoogleMapsCompatible_Level6/$z/$y/$x.png"
        } else {
            null
        }
    }

    fun buildMrmsPrecipTypeTileUrl(z: Int, x: Int, y: Int): String {
        val bbox = RadarProjection.tileWebMercatorBbox3857(z, x, y)
        return "https://opengeo.ncep.noaa.gov/geoserver/conus/conus_pcpn_typ/ows" +
            "?service=WMS&version=1.1.1&request=GetMap&layers=conus_pcpn_typ&styles=" +
            "&bbox=$bbox&width=256&height=256&srs=EPSG:3857&format=image/png&transparent=true"
    }

    /**
     * Enqueues a non-cancelling background tile download and invokes [onTileReady] as soon as decoded.
     * Panning or zooming the Compose UI never cancels an in-flight HTTP download.
     */
    fun prefetchTileAsync(url: String, kind: TileKind, onTileReady: () -> Unit) {
        val cacheKey = "${kind.name}:$url"
        if (tileCache.get(cacheKey) != null) return

        completionCallbacks.computeIfAbsent(cacheKey) { CopyOnWriteArrayList() }.add(onTileReady)
        if (inFlightTiles.containsKey(cacheKey)) return

        val job = tileFetchScope.async {
            try {
                val decoded = downloadAndProcessTile(url, kind)
                if (decoded != null) {
                    tileCache.put(cacheKey, decoded)
                }
                decoded
            } catch (_: Throwable) {
                null
            } finally {
                inFlightTiles.remove(cacheKey)
                val listeners = completionCallbacks.remove(cacheKey)
                if (tileCache.get(cacheKey) != null && listeners != null) {
                    listeners.forEach { cb -> runCatching { cb() } }
                }
            }
        }
        if (inFlightTiles.putIfAbsent(cacheKey, job) != null) {
            job.cancel()
        }
    }

    suspend fun fetchTileBitmap(url: String, kind: TileKind): Bitmap? {
        val cacheKey = "${kind.name}:$url"
        tileCache.get(cacheKey)?.let { return it }
        inFlightTiles[cacheKey]?.let { return it.await() }

        val deferred = tileFetchScope.async {
            try {
                val decoded = downloadAndProcessTile(url, kind)
                if (decoded != null) {
                    tileCache.put(cacheKey, decoded)
                }
                decoded
            } catch (_: Throwable) {
                null
            } finally {
                inFlightTiles.remove(cacheKey)
            }
        }
        val prev = inFlightTiles.putIfAbsent(cacheKey, deferred)
        return if (prev != null) {
            deferred.cancel()
            prev.await()
        } else {
            deferred.await()
        }
    }

    private fun downloadAndProcessTile(url: String, kind: TileKind): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/png, image/jpeg, image/*, */*")
            .build()
        return tileHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val bytes = resp.body?.bytes() ?: return@use null
            if (bytes.isEmpty() || bytes.size > 3_500_000) return@use null
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@use null
            when {
                kind == TileKind.CLOUD_SATELLITE && url.contains("ABI_Band13_Clean_Infrared") ->
                    convertInfraredSatelliteToAlphaClouds(raw)
                kind == TileKind.RADAR_PRECIP ->
                    cleanAndSmoothRadarBitmap(raw, stripClearAirNoise = true)
                kind == TileKind.MRMS_PRECIP_TYPE ->
                    cleanAndSmoothRadarBitmap(raw, stripClearAirNoise = false)
                else -> raw
            }
        }
    }

    suspend fun fetchHazardsAndStorms(
        bounds: GeoBounds,
        centerLat: Double,
        centerLon: Double,
        primaryName: String,
        primaryLat: Double,
        primaryLon: Double
    ): RadarOverlayBundle = coroutineScope {
        val safeCenterLat = roundCoord2Km(centerLat)
        val safeCenterLon = roundCoord2Km(centerLon)
        val safePrimaryLat = roundCoord2Km(primaryLat)
        val safePrimaryLon = roundCoord2Km(primaryLon)

        val hazardsDeferred = async { fetchNwsHazardFeatures(bounds, safeCenterLat, safeCenterLon) }
        val cyclonesDeferred = async { fetchActiveTropicalCyclones() }
        val precipDeferred = async {
            fetchRegionalPrecipGrid(
                bounds = bounds,
                centerLat = safeCenterLat,
                centerLon = safeCenterLon,
                primaryName = primaryName,
                primaryLat = safePrimaryLat,
                primaryLon = safePrimaryLon
            )
        }

        RadarOverlayBundle(
            hazards = hazardsDeferred.await(),
            cyclones = cyclonesDeferred.await(),
            precipMarkers = precipDeferred.await()
        )
    }

    private suspend fun fetchNwsHazardFeatures(
        bounds: GeoBounds,
        centerLat: Double,
        centerLon: Double
    ): List<SevereHazardFeature> = withContext(Dispatchers.IO) {
        val states = RadarProjection.intersectingUsStates(bounds, centerLat, centerLon, maxStates = 10)
        val url = when {
            states.isNotEmpty() && bounds.areaSquareMiles <= 650_000.0 ->
                "https://api.weather.gov/alerts/active?status=actual&area=${states.joinToString(",")}"
            centerLat in 17.0..72.0 && centerLon in -179.0..-64.0 ->
                "https://api.weather.gov/alerts/active?status=actual&severity=Extreme,Severe"
            else -> return@withContext emptyList()
        }

        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/geo+json, application/json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string() ?: return@use emptyList()
                parseNwsHazardGeoJson(json, body, bounds)
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchActiveTropicalCyclones(): List<TropicalCycloneFeature> = withContext(Dispatchers.IO) {
        runCatching {
            val stormsUrl = "https://www.nhc.noaa.gov/CurrentStorms.json"
            val coneUrl = "https://mapservices.weather.noaa.gov/tropical/rest/services/tropical/NHC_tropical_weather/MapServer/7/query?where=1%3D1&outFields=*&f=geojson"

            val stormsBody = okHttpClient.newCall(
                Request.Builder().url(stormsUrl).header("Accept", "application/json").build()
            ).execute().use { if (it.isSuccessful) it.body?.string() else null }

            val conesBody = runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(coneUrl).header("Accept", "application/geo+json, application/json").build()
                ).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull()

            parseNhcCyclonesJson(json, stormsBody, conesBody)
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchRegionalPrecipGrid(
        bounds: GeoBounds,
        centerLat: Double,
        centerLon: Double,
        primaryName: String,
        primaryLat: Double,
        primaryLon: Double
    ): List<RegionalPrecipMarker> = withContext(Dispatchers.IO) {
        val latStep = ((bounds.northLat - bounds.southLat) * 0.28).safeClamp(0.18, 6.5)
        val lonStep = ((abs(bounds.eastLon - bounds.westLon)).let { if (it < 0.05) 0.5 else it } * 0.28)
            .safeClamp(0.22, 8.0)

        data class SampleSpec(val label: String, val lat: Double, val lon: Double, val isPrimary: Boolean)

        val candidates = mutableListOf(
            SampleSpec(primaryName, primaryLat, primaryLon, isPrimary = true)
        )
        val offsets = listOf(
            "NW" to (latStep to -lonStep),
            "N" to (latStep to 0.0),
            "NE" to (latStep to lonStep),
            "W" to (0.0 to -lonStep),
            "Center" to (0.0 to 0.0),
            "E" to (0.0 to lonStep),
            "SW" to (-latStep to -lonStep),
            "S" to (-latStep to 0.0),
            "SE" to (-latStep to lonStep)
        )
        for ((dir, delta) in offsets) {
            val sLat = roundCoord2Km(
                RadarProjection.clampLatitude(centerLat + delta.first)
            )
            val sLon = roundCoord2Km(
                RadarProjection.normalizeLongitude(centerLon + delta.second)
            )
            val nearPrimary = abs(sLat - primaryLat) < latStep * 0.45 && abs(sLon - primaryLon) < lonStep * 0.45
            if (!nearPrimary) {
                candidates += SampleSpec("$dir Sector", sLat, sLon, isPrimary = false)
            }
        }

        val finalSpecs = candidates.take(9)
        val latsParam = finalSpecs.joinToString(",") { String.format(Locale.US, "%.2f", it.lat) }
        val lonsParam = finalSpecs.joinToString(",") { String.format(Locale.US, "%.2f", it.lon) }

        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latsParam&longitude=$lonsParam" +
            "&daily=precipitation_sum,snowfall_sum,weather_code" +
            "&hourly=precipitation,snowfall,cloud_cover" +
            "&precipitation_unit=inch&forecast_days=2&timezone=GMT"

        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string() ?: return@use emptyList()
                val root = json.parseToJsonElement(body)
                val items: List<JsonObject> = when (root) {
                    is JsonArray -> root.mapNotNull { it as? JsonObject }
                    is JsonObject -> listOf(root)
                    else -> emptyList()
                }
                items.mapIndexedNotNull { idx, obj ->
                    val spec = finalSpecs.getOrNull(idx) ?: return@mapIndexedNotNull null
                    parseOpenMeteoGridPoint(
                        obj = obj,
                        id = "pt_$idx",
                        label = spec.label,
                        lat = spec.lat,
                        lon = spec.lon,
                        isPrimary = spec.isPrimary
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private val WIND_MPH_REGEX = Regex("""(\d{2,3})\s*(?:mph|MPH)""")
        private val INCHES_REGEX = Regex("""(\d+(?:\.\d+)?(?:\s*to\s*\d+(?:\.\d+)?)?)\s*inch(?:es)?""", RegexOption.IGNORE_CASE)

        private fun roundCoord2Km(deg: Double): Double = (deg * 50.0).roundToInt() / 50.0

        /**
         * Converts NASA GOES Clean-IR grayscale imagery into soft, natural, volumetric clouds:
         * 1. Maps IR brightness temperature via a smooth Hermite `smoothstep` curve (no harsh step cutoff).
         * 2. Runs a 2-iteration separable Gaussian-like blur (`radius = 4`) across cloud density to
         *    completely dissolve coarse satellite grid blocks into billowy cloud edges.
         * 3. Upscales 1.5x (`384x384`) for smooth bicubic GPU sampling at any zoom level.
         */
        internal fun convertInfraredSatelliteToAlphaClouds(src: Bitmap): Bitmap {
            val w = src.width
            val h = src.height
            if (w <= 0 || h <= 0) return src
            val count = w * h
            val pixels = IntArray(count)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            var density = FloatArray(count)
            for (i in 0 until count) {
                val c = pixels[i]
                val a = (c ushr 24) and 0xFF
                if (a < 16) continue
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 84f) {
                    val t = ((lum - 84f) / 128f).safeClamp(0f, 1f)
                    // Smooth Hermite S-curve (3t^2 - 2t^3) eliminates quantized brightness banding
                    density[i] = t * t * (3f - 2f * t)
                }
            }

            // Two separable box-blur passes (radius 4 + radius 3) approximate a wide, silky Gaussian cloud kernel
            density = blurFloatChannelSeparable(density, w, h, radius = 4)
            density = blurFloatChannelSeparable(density, w, h, radius = 3)

            for (i in 0 until count) {
                val d = density[i]
                if (d < 0.015f) {
                    pixels[i] = 0
                } else {
                    val cloudAlpha = (d * 204f).roundToInt().safeClamp(0, 206)
                    // Soft silver-pearl cloud base transitioning to bright sunlit white cloud tops
                    val shade = d.safeClamp(0f, 1f)
                    val outR = (226f + shade * 29f).roundToInt().safeClamp(0, 255)
                    val outG = (236f + shade * 19f).roundToInt().safeClamp(0, 255)
                    val outB = (248f + shade * 7f).roundToInt().safeClamp(0, 255)
                    pixels[i] = (cloudAlpha shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }

            val smoothed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            smoothed.setPixels(pixels, 0, w, 0, 0, w, h)
            if (smoothed !== src) src.recycle()

            val targetSize = 384
            return if (w < targetSize && h < targetSize) {
                val upscaled = Bitmap.createScaledBitmap(smoothed, targetSize, targetSize, true)
                if (upscaled !== smoothed) smoothed.recycle()
                upscaled
            } else {
                smoothed
            }
        }

        /**
         * Cleans and smooths weather radar tiles into broadcast-TV quality contoured precipitation bands:
         * 1. Strips non-precipitating sub-12 dBZ clear-air Bragg scatter / radar clutter (muted slate-gray bins).
         * 2. Despeckles isolated 1-pixel ground clutter spikes.
         * 3. Applies separable premultiplied-ARGB contour smoothing + 384x384 bilinear upscaling so
         *    green -> yellow -> orange -> red storm cores look organic rather than blocky squares.
         */
        internal fun cleanAndSmoothRadarBitmap(src: Bitmap, stripClearAirNoise: Boolean): Bitmap {
            val w = src.width
            val h = src.height
            if (w <= 0 || h <= 0) return src
            val count = w * h
            val pixels = IntArray(count)
            src.getPixels(pixels, 0, w, 0, 0, w, h)

            if (stripClearAirNoise) {
                for (i in 0 until count) {
                    val c = pixels[i]
                    val a = (c ushr 24) and 0xFF
                    if (a < 20) {
                        pixels[i] = 0
                        continue
                    }
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    if (isSubThresholdClearAirRadarNoise(r, g, b)) {
                        pixels[i] = 0
                    }
                }

                // Despeckle isolated 1px radar noise spikes (require >= 2 active neighbors in 3x3 window)
                val cleaned = pixels.copyOf()
                for (y in 1 until (h - 1)) {
                    val row = y * w
                    for (x in 1 until (w - 1)) {
                        val idx = row + x
                        if ((pixels[idx] ushr 24) == 0) continue
                        var neighbors = 0
                        for (dy in -1..1) {
                            val nr = (y + dy) * w
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                if ((pixels[nr + x + dx] ushr 24) > 24) neighbors++
                            }
                        }
                        if (neighbors < 2) cleaned[idx] = 0
                    }
                }
                System.arraycopy(cleaned, 0, pixels, 0, count)
            }

            // Smooth in premultiplied ARGB space (2 passes, radius 2 + radius 1) for crisp anti-aliased dBZ contours
            var smoothedPixels = blurPremultipliedArgbSeparable(pixels, w, h, radius = 2)
            smoothedPixels = blurPremultipliedArgbSeparable(smoothedPixels, w, h, radius = 1)

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(smoothedPixels, 0, w, 0, 0, w, h)
            if (out !== src) src.recycle()

            val targetSize = 384
            return if (w < targetSize && h < targetSize) {
                val upscaled = Bitmap.createScaledBitmap(out, targetSize, targetSize, true)
                if (upscaled !== out) out.recycle()
                upscaled
            } else {
                out
            }
        }

        internal fun isSubThresholdClearAirRadarNoise(r: Int, g: Int, b: Int): Boolean {
            // Preserve extreme hail/tornado magenta & white cores (high R + high B)
            if (r >= 175 && b >= 165) return false
            // Preserve true rain/storm greens, yellows, oranges, reds (G > B or R > B)
            if (g > b + 8 || r > b + 8) return false
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val chroma = maxC - minC
            // IEM N0Q / HRRR sub-12 dBZ clear-air clutter is muted gray/slate-lavender/pale-cyan:
            // e.g. RGB(87,109,164), RGB(116,131,172), RGB(136,146,178), RGB(94,173,207)
            if (b >= g && b >= r && r in 55..190 && chroma < 115) return true
            if (maxC < 145 && chroma < 55) return true
            return false
        }

        private fun blurFloatChannelSeparable(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
            if (radius <= 0) return src
            val horiz = FloatArray(w * h)
            val out = FloatArray(w * h)
            val inv = 1f / (radius * 2 + 1)

            for (y in 0 until h) {
                val row = y * w
                var sum = src[row] * (radius + 1)
                for (k in 1..radius) {
                    sum += src[row + k.coerceAtMost(w - 1)]
                }
                for (x in 0 until w) {
                    horiz[row + x] = sum * inv
                    val removeX = (x - radius).coerceAtLeast(0)
                    val addX = (x + radius + 1).coerceAtMost(w - 1)
                    sum += src[row + addX] - src[row + removeX]
                }
            }

            for (x in 0 until w) {
                var sum = horiz[x] * (radius + 1)
                for (k in 1..radius) {
                    sum += horiz[k.coerceAtMost(h - 1) * w + x]
                }
                for (y in 0 until h) {
                    out[y * w + x] = sum * inv
                    val removeY = (y - radius).coerceAtLeast(0)
                    val addY = (y + radius + 1).coerceAtMost(h - 1)
                    sum += horiz[addY * w + x] - horiz[removeY * w + x]
                }
            }
            return out
        }

        private fun blurPremultipliedArgbSeparable(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
            if (radius <= 0) return src
            val count = w * h
            val pA = FloatArray(count)
            val pR = FloatArray(count)
            val pG = FloatArray(count)
            val pB = FloatArray(count)

            for (i in 0 until count) {
                val c = src[i]
                val a = ((c ushr 24) and 0xFF) / 255f
                pA[i] = a
                if (a > 0f) {
                    pR[i] = ((c ushr 16) and 0xFF) * a
                    pG[i] = ((c ushr 8) and 0xFF) * a
                    pB[i] = (c and 0xFF) * a
                }
            }

            val bA = blurFloatChannelSeparable(pA, w, h, radius)
            val bR = blurFloatChannelSeparable(pR, w, h, radius)
            val bG = blurFloatChannelSeparable(pG, w, h, radius)
            val bB = blurFloatChannelSeparable(pB, w, h, radius)

            val out = IntArray(count)
            for (i in 0 until count) {
                val a = bA[i]
                if (a < 0.03f) {
                    out[i] = 0
                } else {
                    val outA = (a * 255f).roundToInt().safeClamp(1, 255)
                    val outR = (bR[i] / a).roundToInt().safeClamp(0, 255)
                    val outG = (bG[i] / a).roundToInt().safeClamp(0, 255)
                    val outB = (bB[i] / a).roundToInt().safeClamp(0, 255)
                    out[i] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
                }
            }
            return out
        }

        internal fun parseNwsHazardGeoJson(
            json: Json,
            rawGeoJson: String,
            bounds: GeoBounds? = null
        ): List<SevereHazardFeature> {
            if (rawGeoJson.isBlank()) return emptyList()
            val root = runCatching { json.parseToJsonElement(rawGeoJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val features = root["features"] as? JsonArray ?: return emptyList()

            val results = mutableListOf<SevereHazardFeature>()
            val seenIds = HashSet<String>()

            for (elem in features) {
                val featObj = elem as? JsonObject ?: continue
                val props = featObj["properties"] as? JsonObject ?: continue
                val event = props.str("event")?.trim().orEmpty()
                if (event.isBlank()) continue

                val kind = classifyHazardKind(event, props.str("severity").orEmpty()) ?: continue
                val rawId = props.str("id") ?: featObj.str("id") ?: "$event:${props.str("areaDesc")}"
                if (!seenIds.add(rawId)) continue

                val rings = parseGeoJsonRings(featObj["geometry"])
                if (rings.isEmpty()) continue

                val centroid = computePolygonCentroid(rings.first())
                if (bounds != null && !bounds.contains(centroid.latitude, centroid.longitude, marginDeg = 3.5)) {
                    val anyVertexVisible = rings.any { ring ->
                        ring.any { pt -> bounds.contains(pt.latitude, pt.longitude, marginDeg = 1.5) }
                    }
                    if (!anyVertexVisible) continue
                }

                val params = props["parameters"] as? JsonObject
                val tornadoDet = params.firstParamString("tornadoDetection")
                val tornadoThreat = params.firstParamString("tornadoDamageThreat")
                val tstormThreat = params.firstParamString("thunderstormDamageThreat")
                val floodThreat = params.firstParamString("flashFloodDamageThreat")
                val maxWindParam = params.firstParamString("maxWindGust")
                val maxHailParam = params.firstParamString("maxHailSize")

                val headline = props.str("headline") ?: event
                val description = props.str("description").orEmpty()
                val combinedText = "$headline\n$description"

                val windText = when {
                    !maxWindParam.isNullOrBlank() -> "Wind $maxWindParam"
                    else -> WIND_MPH_REGEX.find(combinedText)?.groupValues?.getOrNull(1)?.let { "${it} mph winds" }
                }

                val hailText = maxHailParam?.takeIf { it.isNotBlank() && it != "0.00" }?.let { "Hail $it" }

                val categoryOrThreat = buildList {
                    if (!tornadoThreat.isNullOrBlank()) add(tornadoThreat.uppercase(Locale.US))
                    else if (!tstormThreat.isNullOrBlank()) add(tstormThreat.uppercase(Locale.US))
                    else if (!floodThreat.isNullOrBlank()) add(floodThreat.uppercase(Locale.US))

                    if (!tornadoDet.isNullOrBlank()) {
                        val compactDet = when {
                            tornadoDet.contains("OBSERVED", ignoreCase = true) -> "OBSERVED"
                            tornadoDet.contains("RADAR", ignoreCase = true) -> "RADAR IND."
                            else -> tornadoDet.uppercase(Locale.US)
                        }
                        add(compactDet)
                    } else if (!kind.isWarning && (kind == HazardPolygonKind.TORNADO_WATCH || kind == HazardPolygonKind.SEVERE_THUNDERSTORM_WATCH)) {
                        add("WATCH IN PLACE")
                    }
                }.joinToString(" · ").ifBlank { null }

                val accumText = INCHES_REGEX.find(description)?.groupValues?.getOrNull(1)?.let { "$it in" }
                val expiresFormatted = props.str("expires")?.let { rawExp ->
                    runCatching {
                        val odt = OffsetDateTime.parse(rawExp)
                            .atZoneSameInstant(ZoneId.systemDefault())
                        "Until " + DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(odt)
                    }.getOrNull()
                }

                results += SevereHazardFeature(
                    id = rawId,
                    kind = kind,
                    eventTitle = event,
                    headline = headline,
                    areaDesc = props.str("areaDesc").orEmpty(),
                    rings = rings,
                    centroid = centroid,
                    categoryOrThreat = categoryOrThreat,
                    windSpeedText = windText,
                    hailSizeText = hailText,
                    expectedRainOrSnowText = accumText,
                    expiresText = expiresFormatted
                )
            }

            return results.sortedByDescending { it.kind.priority }.take(60)
        }

        internal fun classifyHazardKind(event: String, severity: String): HazardPolygonKind? {
            val e = event.lowercase(Locale.US)
            return when {
                "tornado warning" in e -> HazardPolygonKind.TORNADO_WARNING
                "tornado watch" in e -> HazardPolygonKind.TORNADO_WATCH
                "hurricane warning" in e || "typhoon warning" in e || "extreme wind warning" in e ->
                    HazardPolygonKind.HURRICANE_WARNING
                "hurricane watch" in e || "tropical storm warning" in e || "tropical storm watch" in e ->
                    HazardPolygonKind.HURRICANE_WATCH
                "severe thunderstorm warning" in e -> HazardPolygonKind.SEVERE_THUNDERSTORM_WARNING
                "severe thunderstorm watch" in e -> HazardPolygonKind.SEVERE_THUNDERSTORM_WATCH
                "flash flood warning" in e || "flood warning" in e -> HazardPolygonKind.FLASH_FLOOD_WARNING
                "winter storm warning" in e || "blizzard warning" in e || "ice storm warning" in e || "snow squall warning" in e ->
                    HazardPolygonKind.WINTER_STORM_WARNING
                "winter storm watch" in e || "winter weather advisory" in e || "freezing rain advisory" in e ->
                    HazardPolygonKind.WINTER_WEATHER_ADVISORY
                severity.equals("Extreme", ignoreCase = true) || severity.equals("Severe", ignoreCase = true) ->
                    HazardPolygonKind.OTHER_SEVERE
                else -> null
            }
        }

        internal fun parseNhcCyclonesJson(
            json: Json,
            currentStormsBody: String?,
            conesGeoJsonBody: String? = null
        ): List<TropicalCycloneFeature> {
            if (currentStormsBody.isNullOrBlank()) return emptyList()
            val root = runCatching { json.parseToJsonElement(currentStormsBody).jsonObject }.getOrNull()
                ?: return emptyList()
            val activeStorms = root["activeStorms"] as? JsonArray ?: return emptyList()
            if (activeStorms.isEmpty()) return emptyList()

            val coneMap = parseTropicalConesByStormName(json, conesGeoJsonBody)
            val cyclones = mutableListOf<TropicalCycloneFeature>()

            for (el in activeStorms) {
                val obj = el as? JsonObject ?: continue
                val id = obj.str("id") ?: continue
                val name = obj.str("name") ?: "Tropical System"
                val classification = obj.str("classification")?.uppercase(Locale.US).orEmpty()
                val lat = obj.num("lat") ?: parseDirectionalCoord(obj.str("latitude")) ?: continue
                val lon = obj.num("lon") ?: parseDirectionalCoord(obj.str("longitude")) ?: continue

                val rawIntensity = obj.intVal("intensity") ?: 35
                val windMph = (rawIntensity * 1.15078).roundToInt()
                val categoryBadge = classifySaffirSimpsonBadge(classification, windMph)
                val expectedRain = estimateTropicalRainfallInches(windMph)
                val movement = obj.str("movementDir")?.let { dir ->
                    val spd = obj.str("movementSpeed") ?: ""
                    "Moving $dir ${spd} mph".trim()
                }
                val matchedCones = coneMap.entries
                    .firstOrNull { (k, _) -> name.contains(k, ignoreCase = true) || k.contains(name, ignoreCase = true) }
                    ?.value.orEmpty()

                cyclones += TropicalCycloneFeature(
                    id = id,
                    name = name,
                    categoryBadge = categoryBadge,
                    maxSustainedWindMph = windMph,
                    gustWindMph = (windMph * 1.22).roundToInt(),
                    movementText = movement,
                    expectedRainfallInches = expectedRain,
                    center = LatLonPoint(lat, lon),
                    coneRings = matchedCones
                )
            }
            return cyclones
        }

        internal fun classifySaffirSimpsonBadge(classification: String, windMph: Int): String = when {
            windMph >= 157 -> "CAT 5"
            windMph >= 130 -> "CAT 4"
            windMph >= 111 -> "CAT 3"
            windMph >= 96 -> "CAT 2"
            windMph >= 74 -> "CAT 1"
            windMph >= 39 || classification == "TS" || classification == "STS" -> "TS"
            else -> "TD"
        }

        private fun estimateTropicalRainfallInches(windMph: Int): String = when {
            windMph >= 130 -> "10–20\""
            windMph >= 96 -> "6–14\""
            windMph >= 74 -> "4–10\""
            windMph >= 39 -> "3–7\""
            else -> "2–5\""
        }

        private fun parseDirectionalCoord(raw: String?): Double? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim().uppercase(Locale.US)
            val sign = if (trimmed.endsWith("S") || trimmed.endsWith("W")) -1.0 else 1.0
            val numeric = trimmed.trimEnd('N', 'S', 'E', 'W').toDoubleOrNull() ?: return null
            return numeric * sign
        }

        private fun parseTropicalConesByStormName(json: Json, body: String?): Map<String, List<List<LatLonPoint>>> {
            if (body.isNullOrBlank()) return emptyMap()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyMap()
            val features = root["features"] as? JsonArray ?: return emptyMap()
            val out = mutableMapOf<String, MutableList<List<LatLonPoint>>>()
            for (f in features) {
                val fObj = f as? JsonObject ?: continue
                val props = fObj["properties"] as? JsonObject ?: continue
                val stormName = props.str("stormname") ?: props.str("STORMNAME") ?: continue
                val rings = parseGeoJsonRings(fObj["geometry"])
                if (rings.isNotEmpty()) {
                    out.getOrPut(stormName) { mutableListOf() }.addAll(rings)
                }
            }
            return out
        }

        private fun parseOpenMeteoGridPoint(
            obj: JsonObject,
            id: String,
            label: String,
            lat: Double,
            lon: Double,
            isPrimary: Boolean
        ): RegionalPrecipMarker {
            val daily = obj["daily"] as? JsonObject
            val precipArr = daily?.get("precipitation_sum") as? JsonArray
            val snowArr = daily?.get("snowfall_sum") as? JsonArray
            val codeArr = daily?.get("weather_code") as? JsonArray

            val day0Rain = precipArr?.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0
            val day1Rain = (precipArr?.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0) * 0.5
            val day0Snow = snowArr?.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: 0.0
            val day1Snow = (snowArr?.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: 0.0) * 0.5
            val wmoCode = codeArr?.getOrNull(0)?.jsonPrimitive?.intOrNull ?: 0

            val hourly = obj["hourly"] as? JsonObject
            val hCloud = (hourly?.get("cloud_cover") as? JsonArray)
                ?.take(24)
                ?.map { (it.jsonPrimitive.intOrNull ?: 0).safeClamp(0, 100) }
                .orEmpty()
            val hRain = (hourly?.get("precipitation") as? JsonArray)
                ?.take(24)
                ?.map { (it.jsonPrimitive.doubleOrNull ?: 0.0).coerceAtLeast(0.0) }
                .orEmpty()
            val hSnow = (hourly?.get("snowfall") as? JsonArray)
                ?.take(24)
                ?.map { (it.jsonPrimitive.doubleOrNull ?: 0.0).coerceAtLeast(0.0) }
                .orEmpty()

            return RegionalPrecipMarker(
                id = id,
                label = label,
                point = LatLonPoint(lat, lon),
                isPrimaryLocation = isPrimary,
                expectedRainInches = (day0Rain + day1Rain).coerceAtLeast(0.0),
                expectedSnowInches = (day0Snow + day1Snow).coerceAtLeast(0.0),
                weatherCode = wmoCode,
                hourlyCloudCoverPct = hCloud,
                hourlyRainInches = hRain,
                hourlySnowInches = hSnow
            )
        }

        private fun parseGeoJsonRings(geometryElem: JsonElement?): List<List<LatLonPoint>> {
            val geom = geometryElem as? JsonObject ?: return emptyList()
            val type = geom.str("type") ?: return emptyList()
            val coords = geom["coordinates"] as? JsonArray ?: return emptyList()
            val rings = mutableListOf<List<LatLonPoint>>()

            when (type) {
                "Polygon" -> {
                    val outer = coords.firstOrNull() as? JsonArray
                    val parsed = parseRingPoints(outer)
                    if (parsed.size >= 3) rings += parsed
                }
                "MultiPolygon" -> {
                    for (poly in coords) {
                        val polyArr = poly as? JsonArray ?: continue
                        val outer = polyArr.firstOrNull() as? JsonArray
                        val parsed = parseRingPoints(outer)
                        if (parsed.size >= 3) rings += parsed
                    }
                }
            }
            return rings
        }

        private fun parseRingPoints(ringArr: JsonArray?): List<LatLonPoint> {
            if (ringArr == null || ringArr.isEmpty()) return emptyList()
            val total = ringArr.size
            val stride = if (total > 180) (total / 120).coerceAtLeast(2) else 1
            val pts = ArrayList<LatLonPoint>((total / stride) + 2)
            var idx = 0
            while (idx < total) {
                val pair = ringArr[idx] as? JsonArray
                val lon = pair?.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                val lat = pair?.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    pts += LatLonPoint(lat, lon)
                }
                idx += stride
            }
            return pts
        }

        private fun computePolygonCentroid(ring: List<LatLonPoint>): LatLonPoint {
            if (ring.isEmpty()) return LatLonPoint(0.0, 0.0)
            var latSum = 0.0
            var lonSum = 0.0
            for (pt in ring) {
                latSum += pt.latitude
                lonSum += pt.longitude
            }
            return LatLonPoint(latSum / ring.size, lonSum / ring.size)
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.num(key: String): Double? =
            (this[key] as? JsonPrimitive)?.doubleOrNull

        private fun JsonObject.intVal(key: String): Int? =
            (this[key] as? JsonPrimitive)?.intOrNull

        private fun JsonObject?.firstParamString(key: String): String? {
            if (this == null) return null
            return when (val v = this[key]) {
                is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> v.contentOrNull
                else -> null
            }
        }
    }
}

data class RadarOverlayBundle(
    val hazards: List<SevereHazardFeature> = emptyList(),
    val cyclones: List<TropicalCycloneFeature> = emptyList(),
    val precipMarkers: List<RegionalPrecipMarker> = emptyList()
)
