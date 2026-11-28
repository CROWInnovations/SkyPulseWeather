package com.crowinnovations.skypulseweather.ui.components

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowinnovations.skypulseweather.data.remote.RadarMapRepository
import com.crowinnovations.skypulseweather.data.remote.RadarOverlayBundle
import com.crowinnovations.skypulseweather.data.remote.TileKind
import com.crowinnovations.skypulseweather.domain.HazardPolygonKind
import com.crowinnovations.skypulseweather.domain.LatLonPoint
import com.crowinnovations.skypulseweather.domain.RadarBaseMapStyle
import com.crowinnovations.skypulseweather.domain.RadarLayerMode
import com.crowinnovations.skypulseweather.domain.RadarProjection
import com.crowinnovations.skypulseweather.domain.RadarTimelineFrame
import com.crowinnovations.skypulseweather.domain.RegionalPrecipMarker
import com.crowinnovations.skypulseweather.domain.SavedLocation
import com.crowinnovations.skypulseweather.domain.SelectedRadarOverlayItem
import com.crowinnovations.skypulseweather.domain.SevereHazardFeature
import com.crowinnovations.skypulseweather.domain.TropicalCycloneFeature
import com.crowinnovations.skypulseweather.domain.WindUnit
import com.crowinnovations.skypulseweather.domain.safeClamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

private data class ScreenHitTarget(
    val center: Offset,
    val radiusPx: Float,
    val item: SelectedRadarOverlayItem
)

@Composable
fun WeatherRadarMapScreen(
    activeLocation: SavedLocation?,
    windUnit: WindUnit,
    radarRepository: RadarMapRepository,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val homeLat = activeLocation?.latitude ?: 38.9072
    val homeLon = activeLocation?.longitude ?: -77.0369
    val homeName = activeLocation?.name ?: "Current Region"

    var centerLat by rememberSaveable(activeLocation?.id) { mutableStateOf(homeLat) }
    var centerLon by rememberSaveable(activeLocation?.id) { mutableStateOf(homeLon) }
    var zoom by rememberSaveable(activeLocation?.id) {
        mutableFloatStateOf(RadarProjection.DEFAULT_LOCAL_ZOOM)
    }

    var baseMapStyle by rememberSaveable { mutableStateOf(RadarBaseMapStyle.SATELLITE_HYBRID) }
    var layerMode by rememberSaveable { mutableStateOf(RadarLayerMode.RADAR_AND_CLOUDS) }
    var showHazardSymbols by rememberSaveable { mutableStateOf(true) }
    var overlayOpacity by rememberSaveable { mutableFloatStateOf(0.78f) }
    var isPlaying by rememberSaveable { mutableStateOf(true) }

    val frames = remember { RadarProjection.buildTimelineFrames() }
    val liveNowIndex = remember(frames) {
        frames.indexOfFirst { it.offsetMinutes == 0 }.let { if (it < 0) 0 else it }
    }
    var frameIndex by rememberSaveable { mutableIntStateOf(liveNowIndex) }
    val safeFrameIdx = frameIndex.safeClamp(0, (frames.size - 1).coerceAtLeast(0))
    val currentFrame = frames[safeFrameIdx]
    val prevLiveFrame = frames.getOrNull((safeFrameIdx - 1).coerceAtLeast(0)) ?: currentFrame

    var tileGeneration by remember { mutableLongStateOf(0L) }
    var lastInteractionMs by remember { mutableLongStateOf(0L) }
    var isLoadingOverlays by remember { mutableStateOf(false) }
    var overlayBundle by remember { mutableStateOf(RadarOverlayBundle()) }
    var selectedItem by remember { mutableStateOf<SelectedRadarOverlayItem?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current

    val pulseTransition = rememberInfiniteTransition(label = "stormPulse")
    val warningPulse by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warningPulseScale"
    )

    // Smooth auto-play animation loop (pauses briefly while user is actively panning/pinch-zooming)
    LaunchedEffect(isPlaying, frames.size) {
        if (!isPlaying || frames.size <= 1) return@LaunchedEffect
        while (isPlaying) {
            val dwellMs = when {
                frameIndex == liveNowIndex -> 1_650L
                frameIndex == frames.lastIndex -> 1_350L
                else -> 780L
            }
            delay(dwellMs)
            val recentlyMoved = (System.currentTimeMillis() - lastInteractionMs) < 900L
            if (isPlaying && !recentlyMoved) {
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060D1A))
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(200f) }
        val viewportHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(200f) }

        val bounds = remember(centerLat, centerLon, zoom, viewportWidthPx, viewportHeightPx) {
            RadarProjection.computeViewportBounds(
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
        }

        // Debounced fetch of NWS tornado/storm polygons, NHC hurricanes, and expected snow/rain inches
        val quantizedViewportKey = remember(centerLat, centerLon, zoom) {
            val step = if (zoom >= 7f) 0.25 else 1.2
            val qLat = (centerLat / step).roundToInt()
            val qLon = (centerLon / step).roundToInt()
            val qZoom = (zoom * 1.5f).roundToInt()
            "$qLat:$qLon:$qZoom"
        }

        LaunchedEffect(quantizedViewportKey, homeLat, homeLon) {
            delay(280L)
            isLoadingOverlays = true
            overlayBundle = radarRepository.fetchHazardsAndStorms(
                bounds = bounds,
                centerLat = centerLat,
                centerLon = centerLon,
                primaryName = homeName,
                primaryLat = homeLat,
                primaryLon = homeLon
            )
            isLoadingOverlays = false
        }

        // Non-cancelling priority tile loader:
        // 1. Center-outward visible base + city/state label tiles (+ 1-tile pan preload halo) FIRST
        // 2. Current frame cloud & radar tiles SECOND
        // 3. Next animation frame warm-up ONLY when user is not actively dragging/zooming
        val baseTileZoom = floor(zoom.toDouble()).toInt().safeClamp(3, 13)
        val visibleBaseTiles = remember(centerLat, centerLon, zoom, viewportWidthPx, viewportHeightPx, baseTileZoom) {
            computeVisibleTileCoords(
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                tileZoom = baseTileZoom,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                padTiles = 1
            )
        }
        val regionIsAmericas = centerLon in -172.0..-25.0
        val regionInNorthAmericaRadar = centerLat in 15.0..62.0 && centerLon in -165.0..-52.0
        val notifyTileReady: () -> Unit = remember {
            { tileGeneration++ }
        }

        LaunchedEffect(visibleBaseTiles, baseTileZoom, baseMapStyle, layerMode, safeFrameIdx, regionIsAmericas, regionInNorthAmericaRadar) {
            // Priority 1: Base map + crisp state/city boundary labels (center-outward order)
            for ((tx, ty) in visibleBaseTiles) {
                val baseUrl = radarRepository.buildBaseTileUrl(baseMapStyle, baseTileZoom, tx, ty)
                radarRepository.prefetchTileAsync(baseUrl, TileKind.BASE_MAP, notifyTileReady)
                if (baseMapStyle == RadarBaseMapStyle.SATELLITE_HYBRID) {
                    val lblUrl = radarRepository.buildSatelliteReferenceLabelsUrl(baseTileZoom, tx, ty)
                    radarRepository.prefetchTileAsync(lblUrl, TileKind.BASE_LABELS, notifyTileReady)
                }
            }
            // Pre-warm parent overview zoom labels (only ~2-4 tiles) so zooming out or panning always has 100% coherent city/state names
            if (baseMapStyle == RadarBaseMapStyle.SATELLITE_HYBRID && baseTileZoom > 3) {
                val parentZ = baseTileZoom - 1
                val parentLabelTiles = computeVisibleTileCoords(
                    centerLat, centerLon, zoom, parentZ, viewportWidthPx, viewportHeightPx, padTiles = 1
                )
                for ((px, py) in parentLabelTiles) {
                    val pUrl = radarRepository.buildSatelliteReferenceLabelsUrl(parentZ, px, py)
                    radarRepository.prefetchTileAsync(pUrl, TileKind.BASE_LABELS, notifyTileReady)
                }
            }

            val isActivelyMoving = (System.currentTimeMillis() - lastInteractionMs) < 700L
            val framesToWarm = if (isActivelyMoving) {
                listOf(currentFrame)
            } else {
                listOfNotNull(currentFrame, frames.getOrNull((safeFrameIdx + 1) % frames.size))
            }

            // Priority 2: Moving Clouds satellite tiles
            if (layerMode == RadarLayerMode.RADAR_AND_CLOUDS || layerMode == RadarLayerMode.CLOUDS_ONLY) {
                val cloudZ = baseTileZoom.coerceAtMost(6)
                val cloudTiles = computeVisibleTileCoords(
                    centerLat, centerLon, zoom, cloudZ, viewportWidthPx, viewportHeightPx, padTiles = 0
                )
                for (f in framesToWarm) {
                    for ((cx, cy) in cloudTiles) {
                        val cUrl = radarRepository.buildCloudTileUrl(f, centerLon, cloudZ, cx, cy) ?: continue
                        radarRepository.prefetchTileAsync(cUrl, TileKind.CLOUD_SATELLITE, notifyTileReady)
                    }
                }
            }

            // Priority 3: Weather Radar & Precipitation Type tiles (MRMS / NEXRAD / HRRR / IMERG)
            if (layerMode == RadarLayerMode.RADAR_AND_CLOUDS || layerMode == RadarLayerMode.RADAR_ONLY) {
                val maxRadarNativeZ = if (regionInNorthAmericaRadar) 10 else 6
                val radarZ = baseTileZoom.coerceAtMost(maxRadarNativeZ)
                val radarTiles = computeVisibleTileCoords(
                    centerLat, centerLon, zoom, radarZ, viewportWidthPx, viewportHeightPx, padTiles = 0
                )
                for (f in framesToWarm) {
                    for ((rx, ry) in radarTiles) {
                        val rUrl = radarRepository.buildRadarTileUrl(f, centerLat, centerLon, radarZ, rx, ry)
                        if (rUrl != null) {
                            radarRepository.prefetchTileAsync(rUrl, TileKind.RADAR_PRECIP, notifyTileReady)
                        }
                        if (f.includeMrmsPrecipType && regionInNorthAmericaRadar && radarZ <= 8) {
                            val ptypUrl = radarRepository.buildMrmsPrecipTypeTileUrl(radarZ, rx, ry)
                            radarRepository.prefetchTileAsync(ptypUrl, TileKind.MRMS_PRECIP_TYPE, notifyTileReady)
                        }
                    }
                }
            }
        }

        val hitTargets = remember { mutableListOf<ScreenHitTarget>() }

        // Interactive Multi-Touch Pinch-Zoom & Pan Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewportWidthPx, viewportHeightPx) {
                    detectTransformGestures(panZoomLock = false) { centroid, pan, zoomChange, _ ->
                        lastInteractionMs = System.currentTimeMillis()
                        val oldZoom = zoom
                        val newZoom = (oldZoom + (kotlin.math.ln(zoomChange.toDouble()) / kotlin.math.ln(2.0)).toFloat())
                            .safeClamp(RadarProjection.MIN_ZOOM, RadarProjection.MAX_ZOOM)

                        // Anchor pinch-zoom around finger centroid while applying pan
                        val anchorBefore = RadarProjection.screenOffsetToLatLon(
                            screenX = centroid.x - pan.x,
                            screenY = centroid.y - pan.y,
                            centerLat = centerLat,
                            centerLon = centerLon,
                            zoom = oldZoom,
                            viewportWidthPx = viewportWidthPx,
                            viewportHeightPx = viewportHeightPx
                        )
                        val newWorldPx = RadarProjection.worldSizePx(newZoom)
                        val anchorWorldX = RadarProjection.lonToWorldX(anchorBefore.longitude) * newWorldPx
                        val anchorWorldY = RadarProjection.latToWorldY(anchorBefore.latitude) * newWorldPx

                        val newCenterWorldX = (anchorWorldX - (centroid.x - viewportWidthPx / 2.0)) / newWorldPx
                        val newCenterWorldY = ((anchorWorldY - (centroid.y - viewportHeightPx / 2.0)) / newWorldPx)
                            .safeClamp(0.06, 0.94)

                        zoom = newZoom
                        centerLat = RadarProjection.worldYToLat(newCenterWorldY)
                        centerLon = RadarProjection.worldXToLon(newCenterWorldX)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val tappedGeo = RadarProjection.screenOffsetToLatLon(
                                tapOffset.x, tapOffset.y, centerLat, centerLon, zoom, viewportWidthPx, viewportHeightPx
                            )
                            val nextZoom = (zoom + 1.15f).safeClamp(RadarProjection.MIN_ZOOM, RadarProjection.MAX_ZOOM)
                            zoom = nextZoom
                            centerLat = (centerLat + tappedGeo.latitude) * 0.5
                            centerLon = RadarProjection.normalizeLongitude((centerLon + tappedGeo.longitude) * 0.5)
                        },
                        onTap = { tapOffset ->
                            val matched = hitTargets.firstOrNull { target ->
                                hypot(
                                    (tapOffset.x - target.center.x).toDouble(),
                                    (tapOffset.y - target.center.y).toDouble()
                                ) <= target.radiusPx
                            }
                            selectedItem = matched?.item
                        }
                    )
                }
        ) {
            // Observe tileGeneration so newly decoded tiles trigger immediate redraw
            val genRead = tileGeneration
            if (genRead < 0L) return@Canvas

            val safeAlpha = overlayOpacity.safeClamp(0.25f, 1.0f)

            // 1. Base Map Layer (Esri Satellite, Carto Dark, or Carto Voyager)
            drawSlippyTileLayer(
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                displayTileZoom = baseTileZoom,
                maxNativeZoom = 13,
                alpha = 1.0f,
                resolveBitmap = { z, x, y ->
                    val url = radarRepository.buildBaseTileUrl(baseMapStyle, z, x, y)
                    radarRepository.getCachedTile("${TileKind.BASE_MAP.name}:$url")
                },
                fallbackParentLookup = true
            )

            // 2. Moving Clouds Layer (GOES-East / GOES-West IR alpha clouds + Forecast Cloud Field)
            if (layerMode == RadarLayerMode.RADAR_AND_CLOUDS || layerMode == RadarLayerMode.CLOUDS_ONLY) {
                val cloudAlpha = if (layerMode == RadarLayerMode.CLOUDS_ONLY) {
                    (safeAlpha * 0.92f).safeClamp(0.2f, 0.95f)
                } else {
                    (safeAlpha * 0.68f).safeClamp(0.18f, 0.82f)
                }
                if (!currentFrame.isForecast) {
                    drawSlippyTileLayer(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom,
                        displayTileZoom = baseTileZoom.coerceAtMost(9),
                        maxNativeZoom = 6,
                        alpha = cloudAlpha,
                        filterQuality = FilterQuality.High,
                        resolveBitmap = { z, x, y ->
                            val primaryUrl = radarRepository.buildCloudTileUrl(currentFrame, centerLon, z, x, y)
                            val primaryBmp = primaryUrl?.let {
                                radarRepository.getCachedTile("${TileKind.CLOUD_SATELLITE.name}:$it")
                            }
                            if (primaryBmp != null) {
                                primaryBmp
                            } else {
                                val fallbackUrl = radarRepository.buildCloudTileUrl(prevLiveFrame, centerLon, z, x, y)
                                fallbackUrl?.let {
                                    radarRepository.getCachedTile("${TileKind.CLOUD_SATELLITE.name}:$it")
                                }
                            }
                        },
                        fallbackParentLookup = true
                    )
                } else {
                    drawForecastCloudAndPrecipField(
                        markers = overlayBundle.precipMarkers,
                        forecastStepHours = currentFrame.forecastHourStep,
                        includeClouds = true,
                        includePrecip = (layerMode == RadarLayerMode.RADAR_AND_CLOUDS),
                        alpha = cloudAlpha,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom
                    )
                }
            }

            // 3. Weather Radar & Severity / Snow / Mix Layer
            if (layerMode == RadarLayerMode.RADAR_AND_CLOUDS || layerMode == RadarLayerMode.RADAR_ONLY) {
                val inNorthAmerica = centerLat in 15.0..62.0 && centerLon in -165.0..-52.0
                val maxRadarNative = if (inNorthAmerica) 9 else 6

                // Optional MRMS Surface Precip Type underlay on LIVE NOW (Snow cyan/blue, Ice purple, Rain green/red)
                if (currentFrame.includeMrmsPrecipType && inNorthAmerica) {
                    drawSlippyTileLayer(
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom,
                        displayTileZoom = baseTileZoom.coerceAtMost(8),
                        maxNativeZoom = 8,
                        alpha = (safeAlpha * 0.42f).safeClamp(0.15f, 0.55f),
                        resolveBitmap = { z, x, y ->
                            val url = radarRepository.buildMrmsPrecipTypeTileUrl(z, x, y)
                            radarRepository.getCachedTile("${TileKind.MRMS_PRECIP_TYPE.name}:$url")
                        },
                        fallbackParentLookup = false
                    )
                }

                drawSlippyTileLayer(
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    displayTileZoom = baseTileZoom.coerceAtMost(10),
                    maxNativeZoom = maxRadarNative,
                    alpha = safeAlpha,
                    filterQuality = FilterQuality.High,
                    resolveBitmap = { z, x, y ->
                        val primaryUrl = radarRepository.buildRadarTileUrl(currentFrame, centerLat, centerLon, z, x, y)
                        val primaryBmp = primaryUrl?.let {
                            radarRepository.getCachedTile("${TileKind.RADAR_PRECIP.name}:$it")
                        }
                        if (primaryBmp != null) {
                            primaryBmp
                        } else {
                            val fallbackUrl = radarRepository.buildRadarTileUrl(prevLiveFrame, centerLat, centerLon, z, x, y)
                            fallbackUrl?.let {
                                radarRepository.getCachedTile("${TileKind.RADAR_PRECIP.name}:$it")
                            }
                        }
                    },
                    fallbackParentLookup = true
                )

                if (currentFrame.isForecast && layerMode == RadarLayerMode.RADAR_ONLY) {
                    drawForecastCloudAndPrecipField(
                        markers = overlayBundle.precipMarkers,
                        forecastStepHours = currentFrame.forecastHourStep,
                        includeClouds = false,
                        includePrecip = true,
                        alpha = safeAlpha,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom
                    )
                }
            }

            // 4. Crisp State Borders, Roads & City Labels ON TOP of clouds/radar in Satellite Hybrid mode.
            // Uses coherent single-zoom rendering so adjacent tiles NEVER mix zoom levels and slice state names (e.g. Pennsylvania) in half!
            if (baseMapStyle == RadarBaseMapStyle.SATELLITE_HYBRID) {
                drawCoherentLabelTileLayer(
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    preferredTileZoom = baseTileZoom,
                    alpha = 0.96f,
                    resolveBitmap = { z, x, y ->
                        val url = radarRepository.buildSatelliteReferenceLabelsUrl(z, x, y)
                        radarRepository.getCachedTile("${TileKind.BASE_LABELS.name}:$url")
                    }
                )
            }

            // 5. Severe Polygons, Hurricanes, Tornado Symbols, Expected Rain/Snow Inches & Active Location Pin
            hitTargets.clear()
            if (showHazardSymbols) {
                drawCycloneOverlays(
                    cyclones = overlayBundle.cyclones,
                    windUnit = windUnit,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    pulseScale = warningPulse,
                    densityPx = density.density,
                    hitTargets = hitTargets
                )

                drawSevereHazardPolygonsAndBadges(
                    hazards = overlayBundle.hazards,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    pulseScale = warningPulse,
                    densityPx = density.density,
                    hitTargets = hitTargets
                )

                drawPrecipAccumulationCallouts(
                    markers = overlayBundle.precipMarkers,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    zoom = zoom,
                    densityPx = density.density,
                    hitTargets = hitTargets
                )
            }

            drawActiveLocationPin(
                homeLat = homeLat,
                homeLon = homeLon,
                homeName = homeName,
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                densityPx = density.density
            )
        }

        // Foreground Safe-Area Controls (Top HUD, Right Map Zoom/Recenter Controls, Optional Inspector, Bottom Scrubber)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 10.dp)
        ) {
            // Top Single-Line Non-Wrapping HUD Bar
            RadarTopHudBar(
                locationName = homeName,
                coverageText = bounds.formattedCoverageBadge(),
                isLoadingOverlays = isLoadingOverlays,
                baseMapStyle = baseMapStyle,
                showHazardSymbols = showHazardSymbols,
                activeHazardCount = overlayBundle.hazards.size + overlayBundle.cyclones.size,
                onBack = onClose,
                onCycleBaseMap = {
                    val styles = RadarBaseMapStyle.entries
                    baseMapStyle = styles[(styles.indexOf(baseMapStyle) + 1) % styles.size]
                },
                onToggleHazards = { showHazardSymbols = !showHazardSymbols },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Right-Edge Pinch/Tap Zoom & 1-Tap Recenter Column
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(bottom = if (isLandscape) 64.dp else 120.dp)
            ) {
                RadarMapFloatingButton(
                    contentDescription = "Center on $homeName",
                    onClick = {
                        centerLat = homeLat
                        centerLon = homeLon
                        zoom = RadarProjection.DEFAULT_LOCAL_ZOOM
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(19.dp)
                    )
                }

                RadarMapFloatingButton(
                    contentDescription = "Zoom in",
                    onClick = {
                        zoom = (zoom + 0.9f).safeClamp(RadarProjection.MIN_ZOOM, RadarProjection.MAX_ZOOM)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                RadarMapFloatingButton(
                    contentDescription = "Zoom out to multi-state view",
                    onClick = {
                        zoom = (zoom - 0.9f).safeClamp(RadarProjection.MIN_ZOOM, RadarProjection.MAX_ZOOM)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom Stack: Selected Storm/Precip Detail Card (if tapped) + Compact Radar Playback & Legend Dock
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                selectedItem?.let { item ->
                    RadarSelectedOverlayCard(
                        item = item,
                        windUnit = windUnit,
                        onDismiss = { selectedItem = null }
                    )
                }

                RadarBottomControlDock(
                    currentFrame = currentFrame,
                    frameIndex = safeFrameIdx,
                    frameCount = frames.size,
                    isPlaying = isPlaying,
                    layerMode = layerMode,
                    overlayOpacity = overlayOpacity,
                    isLandscape = isLandscape,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onScrubFrame = { newIdx ->
                        isPlaying = false
                        frameIndex = newIdx.safeClamp(0, (frames.size - 1).coerceAtLeast(0))
                    },
                    onSelectLayerMode = { layerMode = it },
                    onOpacityChange = { overlayOpacity = it.safeClamp(0.25f, 1.0f) }
                )
            }
        }
    }
}

@Composable
private fun RadarTopHudBar(
    locationName: String,
    coverageText: String,
    isLoadingOverlays: Boolean,
    baseMapStyle: RadarBaseMapStyle,
    showHazardSymbols: Boolean,
    activeHazardCount: Int,
    onBack: () -> Unit,
    onCycleBaseMap: () -> Unit,
    onToggleHazards: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xD90B1324))
            .border(1.dp, Color(0x3894A3B8), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x26FFFFFF))
                .clickable(onClick = onBack)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to forecast",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            AutoFitSingleLineText(
                text = locationName,
                color = Color.White,
                maxFontSize = 15.sp,
                minFontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            AutoFitSingleLineText(
                text = coverageText,
                color = Color(0xFF93C5FD),
                maxFontSize = 11.2.sp,
                minFontSize = 8.2.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoadingOverlays) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF38BDF8)
            )
        }

        // Base Map Switcher Pill (Satellite / Dark / Map)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x24FFFFFF))
                .border(1.dp, Color(0x4094A3B8), RoundedCornerShape(50))
                .clickable(onClick = onCycleBaseMap)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            SingleLineText(
                text = baseMapStyle.label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Hazards / Symbols Toggle Pill
        val hazardBg = if (showHazardSymbols) {
            if (activeHazardCount > 0) Color(0x44EF4444) else Color(0x3338BDF8)
        } else {
            Color(0x18FFFFFF)
        }
        val hazardBorder = if (showHazardSymbols) {
            if (activeHazardCount > 0) Color(0xFFEF4444) else Color(0xFF38BDF8)
        } else {
            Color(0x3094A3B8)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(50))
                .background(hazardBg)
                .border(1.dp, hazardBorder, RoundedCornerShape(50))
                .clickable(onClick = onToggleHazards)
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Toggle storm warnings and accumulation badges",
                tint = if (activeHazardCount > 0 && showHazardSymbols) Color(0xFFFCA5A5) else Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            SingleLineText(
                text = if (activeHazardCount > 0) "Alerts ($activeHazardCount)" else "Symbols",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RadarMapFloatingButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xD90B1324))
            .border(1.dp, Color(0x4494A3B8), CircleShape)
            .clickable(onClick = onClick)
    ) {
        val desc = contentDescription
        if (desc.isNotEmpty()) {
            content()
        }
    }
}

@Composable
private fun RadarSelectedOverlayCard(
    item: SelectedRadarOverlayItem,
    windUnit: WindUnit,
    onDismiss: () -> Unit
) {
    val title: String
    val subtitle: String
    val detailLine: String
    val accent: Color

    when (item) {
        is SelectedRadarOverlayItem.Hazard -> {
            val h = item.feature
            title = "${h.kind.symbol} ${h.eventTitle}"
            subtitle = buildList {
                h.categoryOrThreat?.let { add(it) }
                h.windSpeedText?.let { add(it) }
                h.hailSizeText?.let { add(it) }
                h.expectedRainOrSnowText?.let { add("Expected $it") }
                h.expiresText?.let { add(it) }
            }.joinToString(" · ").ifBlank { h.headline }
            detailLine = h.areaDesc.ifBlank { h.headline }
            accent = when (h.kind) {
                HazardPolygonKind.TORNADO_WARNING -> Color(0xFFEF4444)
                HazardPolygonKind.TORNADO_WATCH -> Color(0xFFF59E0B)
                HazardPolygonKind.HURRICANE_WARNING, HazardPolygonKind.HURRICANE_WATCH -> Color(0xFFD946EF)
                HazardPolygonKind.WINTER_STORM_WARNING, HazardPolygonKind.WINTER_WEATHER_ADVISORY -> Color(0xFF38BDF8)
                HazardPolygonKind.FLASH_FLOOD_WARNING -> Color(0xFF10B981)
                else -> Color(0xFFFB923C)
            }
        }
        is SelectedRadarOverlayItem.Cyclone -> {
            val c = item.cyclone
            title = "\uD83C\uDF00 ${c.name} · ${c.categoryBadge}"
            val sustained = windUnit.format(c.maxSustainedWindMph.toDouble())
            val gusts = c.gustWindMph?.let { "Gusts ${windUnit.format(it.toDouble())}" }
            subtitle = listOfNotNull(
                "Sustained $sustained",
                gusts,
                "Expected Rain ${c.expectedRainfallInches}"
            ).joinToString(" · ")
            detailLine = c.movementText ?: "Active Tropical Cyclone Advisory"
            accent = Color(0xFFD946EF)
        }
        is SelectedRadarOverlayItem.Precip -> {
            val m = item.marker
            title = if (m.hasSignificantSnow) "\u2744\uFE0F ${m.label} · Snow & Precip"
            else "\uD83C\uDF27\uFE0F ${m.label} · Expected Precip"
            subtitle = "Expected Rain: ${RegionalPrecipMarker.formatInches(m.expectedRainInches)}  ·  Expected Snow: ${RegionalPrecipMarker.formatInches(m.expectedSnowInches)}"
            detailLine = "Next 24–36h forecast accumulation around (${String.format(java.util.Locale.US, "%.2f, %.2f", m.point.latitude, m.point.longitude)})"
            accent = if (m.hasSignificantSnow) Color(0xFF38BDF8) else Color(0xFF4ADE80)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEB0B1324))
            .border(1.2.dp, accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SingleLineText(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            SingleLineText(
                text = subtitle,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleLineText(
                text = detailLine,
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x24FFFFFF))
                .clickable(onClick = onDismiss)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss details",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RadarBottomControlDock(
    currentFrame: RadarTimelineFrame,
    frameIndex: Int,
    frameCount: Int,
    isPlaying: Boolean,
    layerMode: RadarLayerMode,
    overlayOpacity: Float,
    isLandscape: Boolean,
    onTogglePlay: () -> Unit,
    onScrubFrame: (Int) -> Unit,
    onSelectLayerMode: (RadarLayerMode) -> Unit,
    onOpacityChange: (Float) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 3.dp else 5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE00B1324))
            .border(1.dp, Color(0x3894A3B8), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 9.dp)
    ) {
        // Row 1: Compact Weather Type & Severity Color Legend Bar
        RadarSeverityLegendRow()

        // Row 2: Layer Mode Pills + Opacity Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadarLayerMode.entries.forEach { mode ->
                    val selected = mode == layerMode
                    val itemWeight = if (mode == RadarLayerMode.RADAR_AND_CLOUDS) 1.42f else 0.86f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(itemWeight)
                            .heightIn(min = 28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color(0xFF0284C7) else Color(0x1FFFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (selected) Color(0xFF7DD3FC) else Color(0x2E94A3B8),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelectLayerMode(mode) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    ) {
                        AutoFitSingleLineText(
                            text = mode.label,
                            color = Color.White,
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            SingleLineText(
                text = "${(overlayOpacity * 100f).roundToInt()}%",
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )

            Slider(
                value = overlayOpacity,
                onValueChange = onOpacityChange,
                valueRange = 0.25f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF38BDF8),
                    inactiveTrackColor = Color(0x3894A3B8)
                ),
                modifier = Modifier
                    .width(if (isLandscape) 86.dp else 78.dp)
                    .height(24.dp)
            )
        }

        // Row 3: Play/Pause + LIVE/FORECAST Status Badge + Time Scrubber
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0284C7))
                    .clickable(onClick = onTogglePlay)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause radar loop" else "Play radar loop",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column(modifier = Modifier.widthIn(min = 78.dp, max = 104.dp)) {
                val badgeColor = when {
                    currentFrame.offsetMinutes == 0 -> Color(0xFF22C55E)
                    currentFrame.isForecast -> Color(0xFFF59E0B)
                    else -> Color(0xFF38BDF8)
                }
                SingleLineText(
                    text = currentFrame.relativeLabel,
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                SingleLineText(
                    text = currentFrame.clockLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            val maxSliderVal = (frameCount - 1).coerceAtLeast(1).toFloat()
            Slider(
                value = frameIndex.toFloat().safeClamp(0f, maxSliderVal),
                onValueChange = { v -> onScrubFrame(v.roundToInt()) },
                valueRange = 0f..maxSliderVal,
                steps = (frameCount - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = if (currentFrame.isForecast) Color(0xFFF59E0B) else Color(0xFF22C55E),
                    inactiveTrackColor = Color(0x3894A3B8)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
            )
        }
    }
}

@Composable
private fun RadarSeverityLegendRow() {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SingleLineText(
                text = "\u2744\uFE0F Snow",
                color = Color(0xFF7DD3FC),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleLineText(
                text = "Mix/Ice",
                color = Color(0xFFD8B4FE),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleLineText(
                text = "\uD83C\uDF27\uFE0F Rain",
                color = Color(0xFF4ADE80),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleLineText(
                text = "Heavy",
                color = Color(0xFFFACC15),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            SingleLineText(
                text = "\uD83C\uDF2A\uFE0F Severe",
                color = Color(0xFFF87171),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFE0F2FE), // Heavy Snow
                            Color(0xFF38BDF8), // Snow
                            Color(0xFFA855F7), // Sleet / Freezing Rain
                            Color(0xFF22C55E), // Light/Mod Rain
                            Color(0xFFEAB308), // Heavy Rain
                            Color(0xFFF97316), // Intense Storm
                            Color(0xFFEF4444), // Severe / Tornado Warn
                            Color(0xFFD946EF)  // Extreme Hail / Core
                        )
                    )
                )
        )
    }
}

private fun computeVisibleTileCoords(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    tileZoom: Int,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    padTiles: Int = 0
): List<Pair<Int, Int>> {
    val n = 1 shl tileZoom
    val scale = 2.0.pow((zoom - tileZoom).toDouble())
    val scaledTilePx = RadarProjection.TILE_SIZE_PX * scale
    val worldPx = scaledTilePx * n

    val centerPxX = RadarProjection.lonToWorldX(centerLon) * worldPx
    val centerPxY = RadarProjection.latToWorldY(centerLat) * worldPx

    val leftPx = centerPxX - viewportWidthPx / 2.0
    val rightPx = centerPxX + viewportWidthPx / 2.0
    val topPx = (centerPxY - viewportHeightPx / 2.0).coerceAtLeast(0.0)
    val bottomPx = (centerPxY + viewportHeightPx / 2.0).coerceAtMost(worldPx - 1.0)

    val minTx = floor(leftPx / scaledTilePx).toInt() - padTiles
    val maxTx = floor(rightPx / scaledTilePx).toInt() + padTiles
    val minTy = (floor(topPx / scaledTilePx).toInt() - padTiles).safeClamp(0, n - 1)
    val maxTy = (floor(bottomPx / scaledTilePx).toInt() + padTiles).safeClamp(0, n - 1)

    val centerTileX = centerPxX / scaledTilePx
    val centerTileY = centerPxY / scaledTilePx

    data class OrderedTile(val tx: Int, val ty: Int, val distSq: Double)
    val ordered = ArrayList<OrderedTile>(((maxTx - minTx + 1) * (maxTy - minTy + 1)).coerceAtLeast(4))
    for (ty in minTy..maxTy) {
        for (rawTx in minTx..maxTx) {
            val wrappedTx = ((rawTx % n) + n) % n
            val dx = (rawTx + 0.5) - centerTileX
            val dy = (ty + 0.5) - centerTileY
            ordered += OrderedTile(wrappedTx, ty, dx * dx + dy * dy)
        }
    }
    return ordered
        .sortedBy { it.distSq }
        .map { it.tx to it.ty }
        .distinct()
}

private fun DrawScope.drawCoherentLabelTileLayer(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    preferredTileZoom: Int,
    alpha: Float,
    resolveBitmap: (z: Int, x: Int, y: Int) -> Bitmap?
) {
    // Pick a single coherent zoom level across the screen so multi-tile state/city labels never split across zoom levels
    val candidateZooms = listOf(
        preferredTileZoom,
        (preferredTileZoom - 1).coerceAtLeast(3),
        (preferredTileZoom + 1).coerceAtMost(13)
    ).distinct()

    var chosenZ = preferredTileZoom
    var bestRatio = -1f
    for (cz in candidateZooms) {
        val visible = computeVisibleTileCoords(centerLat, centerLon, zoom, cz, size.width, size.height, padTiles = 0)
        if (visible.isEmpty()) continue
        val hits = visible.count { (tx, ty) ->
            val bmp = resolveBitmap(cz, tx, ty)
            bmp != null && !bmp.isRecycled
        }
        if (cz == preferredTileZoom && hits == visible.size) {
            chosenZ = cz
            break
        }
        val ratio = hits.toFloat() / visible.size.toFloat()
        if (ratio > bestRatio) {
            bestRatio = ratio
            chosenZ = cz
        }
    }

    drawSlippyTileLayer(
        centerLat = centerLat,
        centerLon = centerLon,
        zoom = zoom,
        displayTileZoom = chosenZ,
        maxNativeZoom = chosenZ,
        alpha = alpha,
        filterQuality = FilterQuality.High,
        resolveBitmap = resolveBitmap,
        fallbackParentLookup = false
    )
}

private fun DrawScope.drawSlippyTileLayer(
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    displayTileZoom: Int,
    maxNativeZoom: Int,
    alpha: Float,
    filterQuality: FilterQuality = FilterQuality.Medium,
    resolveBitmap: (z: Int, x: Int, y: Int) -> Bitmap?,
    fallbackParentLookup: Boolean
) {
    val n = 1 shl displayTileZoom
    val scale = 2.0.pow((zoom - displayTileZoom).toDouble())
    val destTilePx = RadarProjection.TILE_SIZE_PX * scale
    val worldPx = destTilePx * n

    val centerPxX = RadarProjection.lonToWorldX(centerLon) * worldPx
    val centerPxY = RadarProjection.latToWorldY(centerLat) * worldPx

    val minTx = floor((centerPxX - size.width / 2.0) / destTilePx).toInt()
    val maxTx = floor((centerPxX + size.width / 2.0) / destTilePx).toInt()
    val minTy = floor(((centerPxY - size.height / 2.0).coerceAtLeast(0.0)) / destTilePx).toInt().safeClamp(0, n - 1)
    val maxTy = floor(((centerPxY + size.height / 2.0).coerceAtMost(worldPx - 1.0)) / destTilePx).toInt().safeClamp(0, n - 1)

    for (ty in minTy..maxTy) {
        for (rawTx in minTx..maxTx) {
            val wrappedTx = ((rawTx % n) + n) % n
            val dstLeft = (size.width / 2.0 + (rawTx * destTilePx - centerPxX)).roundToInt()
            val dstTop = (size.height / 2.0 + (ty * destTilePx - centerPxY)).roundToInt()
            val dstRight = (size.width / 2.0 + ((rawTx + 1) * destTilePx - centerPxX)).roundToInt()
            val dstBottom = (size.height / 2.0 + ((ty + 1) * destTilePx - centerPxY)).roundToInt()
            val dstW = (dstRight - dstLeft).coerceAtLeast(1)
            val dstH = (dstBottom - dstTop).coerceAtLeast(1)

            // Resolve native or over-zoomed parent tile
            var lookupZ = displayTileZoom.coerceAtMost(maxNativeZoom)
            var foundBmp: Bitmap? = null
            var shift = displayTileZoom - lookupZ

            while (lookupZ >= 2) {
                shift = displayTileZoom - lookupZ
                val parentX = wrappedTx shr shift
                val parentY = ty shr shift
                val bmp = resolveBitmap(lookupZ, parentX, parentY)
                if (bmp != null && !bmp.isRecycled) {
                    foundBmp = bmp
                    break
                }
                if (!fallbackParentLookup) break
                lookupZ--
            }

            if (foundBmp != null) {
                val subDiv = 1 shl shift
                val subX = wrappedTx and (subDiv - 1)
                val subY = ty and (subDiv - 1)
                val srcW = (foundBmp.width / subDiv).coerceAtLeast(1)
                val srcH = (foundBmp.height / subDiv).coerceAtLeast(1)
                val srcLeft = (subX * srcW).safeClamp(0, (foundBmp.width - srcW).coerceAtLeast(0))
                val srcTop = (subY * srcH).safeClamp(0, (foundBmp.height - srcH).coerceAtLeast(0))

                drawImage(
                    image = foundBmp.asImageBitmap(),
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset(dstLeft, dstTop),
                    dstSize = IntSize(dstW, dstH),
                    alpha = alpha,
                    filterQuality = filterQuality
                )
            }
        }
    }
}

private fun DrawScope.drawForecastCloudAndPrecipField(
    markers: List<RegionalPrecipMarker>,
    forecastStepHours: Int,
    includeClouds: Boolean,
    includePrecip: Boolean,
    alpha: Float,
    centerLat: Double,
    centerLon: Double,
    zoom: Float
) {
    if (markers.isEmpty()) return
    val baseRadiusPx = (minOf(size.width, size.height) * 0.28f).coerceAtLeast(80f)

    for (m in markers) {
        val pos = RadarProjection.latLonToScreenOffset(
            m.point.latitude, m.point.longitude, centerLat, centerLon, zoom, size.width, size.height
        )
        if (pos.x < -baseRadiusPx || pos.x > size.width + baseRadiusPx ||
            pos.y < -baseRadiusPx || pos.y > size.height + baseRadiusPx
        ) continue

        val hourIdx = forecastStepHours.safeClamp(0, 23)
        if (includeClouds) {
            val cloudPct = m.hourlyCloudCoverPct.getOrNull(hourIdx) ?: 0
            if (cloudPct >= 20) {
                val cAlpha = ((cloudPct / 100f) * 0.42f * alpha).safeClamp(0.0f, 0.48f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF).copy(alpha = cAlpha),
                            Color(0xFFE2E8F0).copy(alpha = cAlpha * 0.45f),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = baseRadiusPx
                    ),
                    radius = baseRadiusPx,
                    center = pos
                )
            }
        }

        if (includePrecip) {
            val snowHr = m.hourlySnowInches.getOrNull(hourIdx) ?: 0.0
            val rainHr = m.hourlyRainInches.getOrNull(hourIdx) ?: 0.0
            if (snowHr >= 0.01) {
                val sAlpha = ((snowHr * 4.5).toFloat().safeClamp(0.18f, 0.62f) * alpha)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE0F2FE).copy(alpha = sAlpha),
                            Color(0xFF38BDF8).copy(alpha = sAlpha * 0.55f),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = baseRadiusPx * 0.72f
                    ),
                    radius = baseRadiusPx * 0.72f,
                    center = pos
                )
            } else if (rainHr >= 0.008) {
                val coreColor = when {
                    rainHr >= 0.25 -> Color(0xFFEF4444)
                    rainHr >= 0.10 -> Color(0xFFFACC15)
                    else -> Color(0xFF22C55E)
                }
                val rAlpha = ((rainHr * 6.0).toFloat().safeClamp(0.18f, 0.60f) * alpha)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = rAlpha),
                            Color(0xFF22C55E).copy(alpha = rAlpha * 0.45f),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = baseRadiusPx * 0.68f
                    ),
                    radius = baseRadiusPx * 0.68f,
                    center = pos
                )
            }
        }
    }
}

private fun DrawScope.drawSevereHazardPolygonsAndBadges(
    hazards: List<SevereHazardFeature>,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    pulseScale: Float,
    densityPx: Float,
    hitTargets: MutableList<ScreenHitTarget>
) {
    if (hazards.isEmpty()) return
    val placedBadgeCenters = ArrayList<Offset>()
    val minBadgeSeparationPx = 68f * densityPx

    // Draw polygons back-to-front so Tornado Warnings sit boldly on top
    for (hazard in hazards.asReversed()) {
        val (fillColor, strokeColor) = when (hazard.kind) {
            HazardPolygonKind.TORNADO_WARNING -> Color(0x52EF4444) to Color(0xFFFF2E2E)
            HazardPolygonKind.TORNADO_WATCH -> Color(0x28F59E0B) to Color(0xFFFBBF24)
            HazardPolygonKind.HURRICANE_WARNING -> Color(0x40D946EF) to Color(0xFFE879F9)
            HazardPolygonKind.HURRICANE_WATCH -> Color(0x26C084FC) to Color(0xFFC084FC)
            HazardPolygonKind.SEVERE_THUNDERSTORM_WARNING -> Color(0x3DF97316) to Color(0xFFFB923C)
            HazardPolygonKind.SEVERE_THUNDERSTORM_WATCH -> Color(0x22FACC15) to Color(0xFFFDE047)
            HazardPolygonKind.FLASH_FLOOD_WARNING -> Color(0x3810B981) to Color(0xFF34D399)
            HazardPolygonKind.WINTER_STORM_WARNING -> Color(0x3838BDF8) to Color(0xFF7DD3FC)
            HazardPolygonKind.WINTER_WEATHER_ADVISORY -> Color(0x24818CF8) to Color(0xFFA5B4FC)
            HazardPolygonKind.OTHER_SEVERE -> Color(0x26FB7185) to Color(0xFFFDA4AF)
        }

        val strokeEffect = if (hazard.kind.isWarning) {
            null
        } else {
            PathEffect.dashPathEffect(floatArrayOf(14f * densityPx, 9f * densityPx), 0f)
        }

        for (ring in hazard.rings) {
            if (ring.size < 3) continue
            val path = Path()
            var anyOnScreen = false
            ring.forEachIndexed { idx, pt ->
                val p = RadarProjection.latLonToScreenOffset(
                    pt.latitude, pt.longitude, centerLat, centerLon, zoom, size.width, size.height
                )
                if (p.x in -200f..(size.width + 200f) && p.y in -200f..(size.height + 200f)) {
                    anyOnScreen = true
                }
                if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            if (anyOnScreen) {
                drawPath(path = path, color = fillColor)
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = (if (hazard.kind == HazardPolygonKind.TORNADO_WARNING) 2.8f else 1.9f) * densityPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = strokeEffect
                    )
                )
            }
        }
    }

    // Draw prioritized storm symbol & threat/wind badges without clutter overlap
    val safeTopBadgePx = 78f * densityPx
    val safeBottomBadgePx = (size.height - 152f * densityPx).coerceAtLeast(safeTopBadgePx + 40f)
    for (hazard in hazards) {
        val cPos = RadarProjection.latLonToScreenOffset(
            hazard.centroid.latitude, hazard.centroid.longitude, centerLat, centerLon, zoom, size.width, size.height
        )
        if (cPos.x !in (24f * densityPx)..(size.width - 56f * densityPx) ||
            cPos.y !in safeTopBadgePx..safeBottomBadgePx
        ) continue

        val isTornadoWarn = hazard.kind == HazardPolygonKind.TORNADO_WARNING
        if (!isTornadoWarn && placedBadgeCenters.any {
                hypot((it.x - cPos.x).toDouble(), (it.y - cPos.y).toDouble()) < minBadgeSeparationPx
            }
        ) {
            continue
        }
        placedBadgeCenters += cPos

        if (isTornadoWarn) {
            drawCircle(
                color = Color(0x55EF4444),
                radius = 18f * densityPx * pulseScale,
                center = cPos
            )
        }

        val badgeLabel = if (zoom >= 6.3f) {
            hazard.mapCalloutSummary()
        } else {
            "${hazard.kind.symbol} ${hazard.kind.shortBadge}"
        }

        val bgArgb = when (hazard.kind) {
            HazardPolygonKind.TORNADO_WARNING -> 0xEE991B1B.toInt()
            HazardPolygonKind.TORNADO_WATCH -> 0xEE78350F.toInt()
            HazardPolygonKind.HURRICANE_WARNING, HazardPolygonKind.HURRICANE_WATCH -> 0xEE701A75.toInt()
            HazardPolygonKind.WINTER_STORM_WARNING, HazardPolygonKind.WINTER_WEATHER_ADVISORY -> 0xEE0C4A6E.toInt()
            HazardPolygonKind.FLASH_FLOOD_WARNING -> 0xEE064E3B.toInt()
            else -> 0xEE7C2D12.toInt()
        }
        val borderArgb = when (hazard.kind) {
            HazardPolygonKind.TORNADO_WARNING -> 0xFFFF4D4D.toInt()
            HazardPolygonKind.TORNADO_WATCH -> 0xFFFBBF24.toInt()
            HazardPolygonKind.HURRICANE_WARNING, HazardPolygonKind.HURRICANE_WATCH -> 0xFFF0ABFC.toInt()
            HazardPolygonKind.WINTER_STORM_WARNING, HazardPolygonKind.WINTER_WEATHER_ADVISORY -> 0xFF7DD3FC.toInt()
            HazardPolygonKind.FLASH_FLOOD_WARNING -> 0xFF6EE7B7.toInt()
            else -> 0xFFFDBA74.toInt()
        }

        val pillRadiusPx = drawCanvasPillLabel(
            center = cPos,
            text = badgeLabel,
            textSizePx = 11.2f * densityPx,
            bgColorArgb = bgArgb,
            borderColorArgb = borderArgb,
            textColorArgb = 0xFFFFFFFF.toInt(),
            densityPx = densityPx
        )
        hitTargets.add(
            0,
            ScreenHitTarget(
                center = cPos,
                radiusPx = maxOf(pillRadiusPx, 28f * densityPx),
                item = SelectedRadarOverlayItem.Hazard(hazard)
            )
        )
    }
}

private fun DrawScope.drawCycloneOverlays(
    cyclones: List<TropicalCycloneFeature>,
    windUnit: WindUnit,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    pulseScale: Float,
    densityPx: Float,
    hitTargets: MutableList<ScreenHitTarget>
) {
    for (cyclone in cyclones) {
        // Forecast error cone
        for (ring in cyclone.coneRings) {
            if (ring.size < 3) continue
            val path = Path()
            ring.forEachIndexed { idx, pt ->
                val p = RadarProjection.latLonToScreenOffset(
                    pt.latitude, pt.longitude, centerLat, centerLon, zoom, size.width, size.height
                )
                if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path = path, color = Color(0x30E879F9))
            drawPath(
                path = path,
                color = Color(0xFFE879F9),
                style = Stroke(
                    width = 1.8f * densityPx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * densityPx, 8f * densityPx), 0f)
                )
            )
        }

        val pos = RadarProjection.latLonToScreenOffset(
            cyclone.center.latitude, cyclone.center.longitude, centerLat, centerLon, zoom, size.width, size.height
        )
        if (pos.x !in -60f..(size.width + 60f) || pos.y !in -60f..(size.height + 60f)) continue

        drawCircle(
            color = Color(0x55D946EF),
            radius = 22f * densityPx * pulseScale,
            center = pos
        )

        val summary = cyclone.mapCalloutSummary(windUnit)
        val pillR = drawCanvasPillLabel(
            center = pos,
            text = summary,
            textSizePx = 11.5f * densityPx,
            bgColorArgb = 0xEE581C87.toInt(),
            borderColorArgb = 0xFFF0ABFC.toInt(),
            textColorArgb = 0xFFFFFFFF.toInt(),
            densityPx = densityPx
        )
        hitTargets.add(
            0,
            ScreenHitTarget(
                center = pos,
                radiusPx = maxOf(pillR, 30f * densityPx),
                item = SelectedRadarOverlayItem.Cyclone(cyclone)
            )
        )
    }
}

private fun DrawScope.drawPrecipAccumulationCallouts(
    markers: List<RegionalPrecipMarker>,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    densityPx: Float,
    hitTargets: MutableList<ScreenHitTarget>
) {
    if (markers.isEmpty()) return
    val placed = ArrayList<Offset>()
    val minDistPx = 76f * densityPx

    for (marker in markers) {
        val badgeText = marker.formattedBadgeText() ?: continue
        // Avoid overlapping right on top of the home location pin label unless significant rain/snow exists
        if (marker.isPrimaryLocation && !marker.hasSignificantRain && !marker.hasSignificantSnow) continue

        val rawPos = RadarProjection.latLonToScreenOffset(
            marker.point.latitude, marker.point.longitude, centerLat, centerLon, zoom, size.width, size.height
        )
        val pos = if (marker.isPrimaryLocation) {
            Offset(rawPos.x, rawPos.y + 26f * densityPx)
        } else {
            rawPos
        }

        val safeTopPrecipPx = 80f * densityPx
        val safeBottomPrecipPx = (size.height - 156f * densityPx).coerceAtLeast(safeTopPrecipPx + 40f)
        if (pos.x !in (32f * densityPx)..(size.width - 58f * densityPx) ||
            pos.y !in safeTopPrecipPx..safeBottomPrecipPx
        ) continue
        if (placed.any { hypot((it.x - pos.x).toDouble(), (it.y - pos.y).toDouble()) < minDistPx }) continue
        placed += pos

        val isSnow = marker.hasSignificantSnow
        val bgArgb = if (isSnow) 0xE60C4A6E.toInt() else 0xE6064E3B.toInt()
        val borderArgb = if (isSnow) 0xFF7DD3FC.toInt() else 0xFF4ADE80.toInt()

        val rPx = drawCanvasPillLabel(
            center = pos,
            text = badgeText,
            textSizePx = 10.8f * densityPx,
            bgColorArgb = bgArgb,
            borderColorArgb = borderArgb,
            textColorArgb = 0xFFF8FAFC.toInt(),
            densityPx = densityPx
        )
        hitTargets += ScreenHitTarget(
            center = pos,
            radiusPx = maxOf(rPx, 24f * densityPx),
            item = SelectedRadarOverlayItem.Precip(marker)
        )
    }
}

private fun DrawScope.drawActiveLocationPin(
    homeLat: Double,
    homeLon: Double,
    homeName: String,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    densityPx: Float
) {
    val pos = RadarProjection.latLonToScreenOffset(
        homeLat, homeLon, centerLat, centerLon, zoom, size.width, size.height
    )
    if (pos.x !in -40f..(size.width + 40f) || pos.y !in -40f..(size.height + 40f)) return

    drawCircle(
        color = Color(0x4438BDF8),
        radius = 14f * densityPx,
        center = pos
    )
    drawCircle(
        color = Color.White,
        radius = 6.5f * densityPx,
        center = pos
    )
    drawCircle(
        color = Color(0xFF0284C7),
        radius = 4.5f * densityPx,
        center = pos
    )

    if (zoom >= 5.2f) {
        drawCanvasPillLabel(
            center = Offset(pos.x, pos.y - 19f * densityPx),
            text = homeName,
            textSizePx = 10.5f * densityPx,
            bgColorArgb = 0xD90F172A.toInt(),
            borderColorArgb = 0xFF38BDF8.toInt(),
            textColorArgb = 0xFFFFFFFF.toInt(),
            densityPx = densityPx
        )
    }
}

private fun DrawScope.drawCanvasPillLabel(
    center: Offset,
    text: String,
    textSizePx: Float,
    bgColorArgb: Int,
    borderColorArgb: Int,
    textColorArgb: Int,
    densityPx: Float
): Float {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = textColorArgb
        textSize = textSizePx
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val textWidth = paint.measureText(text)
    val padH = 8.5f * densityPx
    val padV = 4.5f * densityPx
    val boxW = (textWidth + padH * 2f).coerceAtMost(size.width * 0.86f)
    val boxH = textSizePx + padV * 2f
    val rightMarginPx = if (center.y in (size.height * 0.25f)..(size.height * 0.68f)) {
        56f * densityPx
    } else {
        8f * densityPx
    }
    val minCenterX = boxW / 2f + 8f * densityPx
    val maxCenterX = (size.width - rightMarginPx - boxW / 2f).coerceAtLeast(minCenterX)
    val safeCenterX = center.x.safeClamp(minCenterX, maxCenterX)
    val topLeft = Offset(safeCenterX - boxW / 2f, center.y - boxH / 2f)
    val corner = CornerRadius(boxH / 2f, boxH / 2f)

    drawRoundRect(
        color = Color(bgColorArgb),
        topLeft = topLeft,
        size = Size(boxW, boxH),
        cornerRadius = corner
    )
    drawRoundRect(
        color = Color(borderColorArgb),
        topLeft = topLeft,
        size = Size(boxW, boxH),
        cornerRadius = corner,
        style = Stroke(width = 1.2f * densityPx)
    )

    val fm = paint.fontMetrics
    val baselineY = center.y - (fm.ascent + fm.descent) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, safeCenterX, baselineY, paint)
    return boxW * 0.5f
}
