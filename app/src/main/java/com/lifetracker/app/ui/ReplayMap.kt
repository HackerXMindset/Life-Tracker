package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.Feature as AppFeature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.data.PathSpeeds
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.ReplayRules
import com.lifetracker.app.ui.theme.LocalPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private fun hexOf(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

/** Lets the screen's + and - buttons zoom the map the replay shows. */
internal class ReplayCamera {
    var map: MapLibreMap? = null
    fun zoom(by: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(by), 250)
    }
}

/**
 * The map of the day replay: the path so far trailing behind a dot, and your places as circles. The dot and the trail are redrawn
 * about fifteen times a second from [clockMs]; with [follow] the camera keeps the dot in view. Bump [fitRequest] to show the
 * whole day.
 */
@Composable
internal fun ReplayMap(
    samples: List<ReplayRules.Sample>,
    clockMs: Long,
    places: List<PlaceEntity>,
    follow: Boolean,
    autoZoom: Boolean,
    fitRequest: Int,
    icon: String,
    camera: ReplayCamera,
    onUserPanned: () -> Unit,
    onUserZoomed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val paper = LocalPaper.current
    val mapsOn = Features.isOn(context, AppFeature.MAPS)
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val clock by rememberUpdatedState(clockMs)
    val following by rememberUpdatedState(follow)
    val pannedNow by rememberUpdatedState(onUserPanned)
    val zoomedNow by rememberUpdatedState(onUserZoomed)
    val auto by rememberUpdatedState(autoZoom)
    val samplesNow by rememberUpdatedState(samples)
    var touching by remember { mutableStateOf(false) }
    var zoomAtStart by remember { mutableStateOf(0.0) }
    var marker by remember { mutableStateOf<IntOffset?>(null) }

    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }

    val url = styleUrl
    Box(modifier = modifier.background(paper.surface2)) {
        if (!mapsOn || url == null || failed) {
            Text(
                if (!mapsOn) "Maps are switched off (Settings > Features), so there is no map picture. The clock and the cards still work."
                else if (failed) "The map could not load (no connection, and no downloaded map for this spot). The clock and the cards still work."
                else "Loading the map...",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                fontSize = 13.sp,
                color = paper.ink2,
            )
        } else {
            MapBase(
                styleUrl = url,
                modifier = Modifier.fillMaxSize(),
                onStyle = { map, style ->
                    addLayers(style, paper.accent.toArgb(), paper.ink.toArgb())
                    camera.map = map
                    // While a finger is on the map the camera is left alone (it would fight the finger). When the finger lifts: a changed
                    // zoom is kept (auto zoom stops until you ask for it again); a map dragged away from the dot stops the following.
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            touching = true
                            zoomAtStart = map.cameraPosition.zoom
                        }
                    }
                    map.addOnCameraIdleListener {
                        if (!touching) return@addOnCameraIdleListener
                        touching = false
                        if (kotlin.math.abs(map.cameraPosition.zoom - zoomAtStart) > 0.05) zoomedNow()
                        val here = ReplayRules.positionAt(samplesNow, clock)
                        val centre = map.cameraPosition.target
                        if (here != null && centre != null) {
                            val p = map.projection.toScreenLocation(LatLng(here.first, here.second))
                            val c = map.projection.toScreenLocation(centre)
                            val off = kotlin.math.hypot((p.x - c.x).toDouble(), (p.y - c.y).toDouble())
                            if (off > with(density) { 110.dp.toPx() }) pannedNow()
                        }
                    }
                    ready = map to style
                },
                onFailed = { failed = true },
            )
            // The picture of how you were travelling at this moment sits on the dot.
            val at = marker
            if (at != null) {
                val half = with(density) { 18.dp.roundToPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(at.x - half, at.y - half) }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(paper.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) { Text(icon, fontSize = 20.sp) }
            }
        }
    }

    LaunchedEffect(ready, places) { ready?.second?.let { paintPlaces(it, places) } }
    LaunchedEffect(ready, fitRequest) {
        val (map, _) = ready ?: return@LaunchedEffect
        if (samples.isEmpty()) return@LaunchedEffect
        val b = LatLngBounds.Builder()
        samples.forEach { b.include(LatLng(it.lat, it.lng)) }
        runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80)) }
    }
    LaunchedEffect(ready, samples) {
        val (map, style) = ready ?: return@LaunchedEffect
        var started = false
        var zoom = map.cameraPosition.zoom
        while (true) {
            val now = clock
            val parts = ReplayRules.trail(samples, now)
            paintTrail(style, parts)
            val here = ReplayRules.positionAt(samples, now)
            paintDot(style, here)
            marker = here?.let { h ->
                val p = map.projection.toScreenLocation(LatLng(h.first, h.second))
                IntOffset(p.x.toInt(), p.y.toInt())
            }
            if (here != null && following && !touching) {
                val target = LatLng(here.first, here.second)
                if (auto) {
                    // The zoom follows how fast you were really moving, glided towards so it does not jump.
                    val wanted = ReplayRules.autoZoom(ReplayRules.groundSpeedMs(samples, now))
                    zoom = if (!started) wanted else ReplayRules.easeZoom(zoom, wanted)
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(zoom).build()))
                } else {
                    zoom = map.cameraPosition.zoom
                    map.moveCamera(CameraUpdateFactory.newLatLng(target))
                }
                started = true
            } else {
                zoom = map.cameraPosition.zoom
            }
            delay(66)
        }
    }
}

private fun addLayers(style: Style, accent: Int, ink: Int) {
    if (style.getSource("replay-trail-src") != null) return
    style.addSource(GeoJsonSource("replay-places-src"))
    style.addLayer(FillLayer("replay-places-fill", "replay-places-src").withProperties(PropertyFactory.fillColor(hexOf(accent)), PropertyFactory.fillOpacity(0.2f)))
    style.addLayer(LineLayer("replay-places-line", "replay-places-src").withProperties(PropertyFactory.lineColor(hexOf(accent)), PropertyFactory.lineWidth(2f)))
    style.addSource(GeoJsonSource("replay-trail-src"))
    style.addLayer(
        LineLayer("replay-trail-line", "replay-trail-src").withProperties(
            PropertyFactory.lineColor(hexOf(accent)),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addSource(GeoJsonSource("replay-dot-src"))
    style.addLayer(
        CircleLayer("replay-dot", "replay-dot-src").withProperties(
            PropertyFactory.circleRadius(3f),
            PropertyFactory.circleColor(hexOf(accent)),
            PropertyFactory.circleStrokeColor(hexOf(ink)),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

private fun paintTrail(style: Style, parts: List<List<Pair<Double, Double>>>) {
    val src = style.getSourceAs<GeoJsonSource>("replay-trail-src") ?: return
    if (parts.isEmpty()) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    } else {
        val lines = parts.map { part -> part.map { Point.fromLngLat(it.second, it.first) } }
        src.setGeoJson(Feature.fromGeometry(MultiLineString.fromLngLats(lines)))
    }
}

private fun paintDot(style: Style, here: Pair<Double, Double>?) {
    val src = style.getSourceAs<GeoJsonSource>("replay-dot-src") ?: return
    if (here == null) src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    else src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(here.second, here.first)))
}

private fun paintPlaces(style: Style, places: List<PlaceEntity>) {
    val src = style.getSourceAs<GeoJsonSource>("replay-places-src") ?: return
    val features = places.filter { !it.archived }.map { p ->
        val ring = PathSpeeds.circle(p.lat, p.lng, p.radiusM.toDouble()).map { Point.fromLngLat(it.second, it.first) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}
