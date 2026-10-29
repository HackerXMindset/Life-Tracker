package com.lifetracker.app.ui

import android.location.Location
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
import com.lifetracker.app.data.MapAreaEntity
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.data.PathSpeeds
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.RawFixEntity
import com.lifetracker.app.ui.theme.LocalPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

private fun hexOf(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

/**
 * The live map. Your position is the map library's own location marker, moved by our readings (so there is no second GPS
 * request) and followed the way Google Maps does: smoothly, and until you move the map yourself. Around it: today's path,
 * your saved places as circles and your offline map areas. Bump [centreRequest] to follow again; [onFollowing] says whether
 * the map is following right now.
 */
@Composable
internal fun LiveMap(
    fix: RawFixEntity?,
    moving: Boolean,
    path: List<Pair<Double, Double>>,
    places: List<PlaceEntity>,
    areas: List<MapAreaEntity>,
    centreRequest: Int,
    onFollowing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val mapsOn = Features.isOn(context, AppFeature.MAPS)
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val followNow by rememberUpdatedState(onFollowing)

    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }

    val url = styleUrl
    Box(modifier = modifier.background(paper.surface2)) {
        if (!mapsOn || url == null || failed) {
            Text(
                if (!mapsOn) "Maps are switched off (Settings > Features), so there is no map picture. The status below still works."
                else if (failed) "The map could not load (no connection, and no downloaded map for this spot). The status below still works."
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
                    addLiveLayers(style, paper.accent.toArgb(), paper.ink2.toArgb())
                    val options = LocationComponentOptions.builder(context)
                        .accuracyAlpha(0.2f)
                        .accuracyColor(paper.accent.toArgb())
                        .build()
                    val lc = map.locationComponent
                    lc.activateLocationComponent(
                        LocationComponentActivationOptions.builder(context, style)
                            .useDefaultLocationEngine(false)
                            .locationComponentOptions(options)
                            .build(),
                    )
                    lc.setLocationComponentEnabled(true)
                    lc.setCameraMode(CameraMode.TRACKING)
                    lc.setRenderMode(RenderMode.NORMAL)
                    lc.zoomWhileTracking(16.0)
                    lc.addOnCameraTrackingChangedListener(
                        object : OnCameraTrackingChangedListener {
                            override fun onCameraTrackingDismissed() = followNow(false)
                            override fun onCameraTrackingChanged(currentMode: Int) = followNow(currentMode != CameraMode.NONE)
                        },
                    )
                    ready = map to style
                },
                onFailed = { failed = true },
            )
        }
    }

    LaunchedEffect(ready, path) { ready?.second?.let { paintPath(it, path) } }
    LaunchedEffect(ready, places) { ready?.second?.let { paintPlaces(it, places) } }
    LaunchedEffect(ready, areas) { ready?.second?.let { paintAreas(it, areas) } }
    LaunchedEffect(ready, fix?.ms, moving) {
        val (map, _) = ready ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        val lc = map.locationComponent
        lc.setRenderMode(if (moving && f.bearing >= 0f) RenderMode.GPS else RenderMode.NORMAL)
        val loc = Location("fused").apply {
            latitude = f.lat
            longitude = f.lng
            accuracy = f.acc
            time = f.ms
            if (f.speed >= 0f) speed = f.speed
            if (f.bearing >= 0f) bearing = f.bearing
        }
        lc.forceLocationUpdate(loc)
    }
    LaunchedEffect(ready, centreRequest) {
        val (map, _) = ready ?: return@LaunchedEffect
        if (centreRequest == 0) return@LaunchedEffect
        val lc = map.locationComponent
        lc.setCameraMode(CameraMode.TRACKING)
        lc.zoomWhileTracking(16.0)
    }
}

private fun addLiveLayers(style: Style, accent: Int, quiet: Int) {
    if (style.getSource("live-areas-src") != null) return
    style.addSource(GeoJsonSource("live-areas-src"))
    style.addLayer(FillLayer("live-areas-fill", "live-areas-src").withProperties(PropertyFactory.fillColor(hexOf(quiet)), PropertyFactory.fillOpacity(0.06f)))
    style.addLayer(
        LineLayer("live-areas-line", "live-areas-src").withProperties(
            PropertyFactory.lineColor(hexOf(quiet)),
            PropertyFactory.lineWidth(1.5f),
            PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
        ),
    )
    style.addSource(GeoJsonSource("live-places-src"))
    style.addLayer(FillLayer("live-places-fill", "live-places-src").withProperties(PropertyFactory.fillColor(hexOf(accent)), PropertyFactory.fillOpacity(0.2f)))
    style.addLayer(LineLayer("live-places-line", "live-places-src").withProperties(PropertyFactory.lineColor(hexOf(accent)), PropertyFactory.lineWidth(2f)))
    style.addSource(GeoJsonSource("live-path-src"))
    style.addLayer(
        LineLayer("live-path-line", "live-path-src").withProperties(
            PropertyFactory.lineColor(hexOf(accent)),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
}

private fun paintPath(style: Style, path: List<Pair<Double, Double>>) {
    val src = style.getSourceAs<GeoJsonSource>("live-path-src") ?: return
    if (path.size < 2) {
        src.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    } else {
        src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(path.map { Point.fromLngLat(it.second, it.first) })))
    }
}

private fun paintPlaces(style: Style, places: List<PlaceEntity>) {
    val src = style.getSourceAs<GeoJsonSource>("live-places-src") ?: return
    val features = places.filter { !it.archived }.map { p ->
        val ring = PathSpeeds.circle(p.lat, p.lng, p.radiusM.toDouble()).map { Point.fromLngLat(it.second, it.first) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun paintAreas(style: Style, areas: List<MapAreaEntity>) {
    val src = style.getSourceAs<GeoJsonSource>("live-areas-src") ?: return
    val features = areas.filter { it.state != "removed" }.map { a ->
        val ring = listOf(
            Point.fromLngLat(a.west, a.south), Point.fromLngLat(a.east, a.south),
            Point.fromLngLat(a.east, a.north), Point.fromLngLat(a.west, a.north), Point.fromLngLat(a.west, a.south),
        )
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
}
