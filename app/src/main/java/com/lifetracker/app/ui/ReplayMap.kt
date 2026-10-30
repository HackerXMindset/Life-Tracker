package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
    fitRequest: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val mapsOn = Features.isOn(context, AppFeature.MAPS)
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val clock by rememberUpdatedState(clockMs)
    val following by rememberUpdatedState(follow)

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
                    ready = map to style
                },
                onFailed = { failed = true },
            )
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
        while (true) {
            val now = clock
            val parts = ReplayRules.trail(samples, now)
            paintTrail(style, parts)
            val here = ReplayRules.positionAt(samples, now)
            paintDot(style, here)
            if (here != null && following) {
                val target = LatLng(here.first, here.second)
                if (!started) {
                    started = true
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(target).zoom(14.0).build()))
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLng(target))
                }
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
            PropertyFactory.circleRadius(8f),
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
