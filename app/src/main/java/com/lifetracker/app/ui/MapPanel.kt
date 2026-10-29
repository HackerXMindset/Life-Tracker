package com.lifetracker.app.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.MapEngine
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.data.PathFrame
import com.lifetracker.app.data.PathSpeeds
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.Wobble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
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

/**
 * A map view that follows the screen's life and loads [styleUrl]. [onStyle] is called each time the style has loaded (draw your
 * layers there); [onFailed] when the map could not be loaded at all (no connection and nothing downloaded for the spot).
 * [grab] keeps a parent list from taking the finger away while you pan the map.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun MapBase(
    styleUrl: String,
    modifier: Modifier = Modifier,
    onMap: (MapLibreMap) -> Unit = {},
    onStyle: (MapLibreMap, Style) -> Unit,
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapEngine.start(context); MapView(context) }
    val styleCallback by rememberUpdatedState(onStyle)
    val mapCallback by rememberUpdatedState(onMap)
    val failCallback by rememberUpdatedState(onFailed)

    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    destroyed = true
                    mapView.onDestroy()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                val state = lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    DisposableEffect(mapView) {
        val listener = MapView.OnDidFailLoadingMapListener { failCallback() }
        mapView.addOnDidFailLoadingMapListener(listener)
        onDispose { mapView.removeOnDidFailLoadingMapListener(listener) }
    }

    LaunchedEffect(mapView, styleUrl) {
        mapView.getMapAsync { map ->
            mapCallback(map)
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style -> styleCallback(map, style) }
        }
    }

    AndroidView(
        factory = {
            mapView.setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            mapView
        },
        modifier = modifier,
    )
}

private fun hex(c: Color): String = PathSpeeds.hex(c.toArgb())

private fun dot(lat: Double, lng: Double, fill: String, stroke: String, radius: Float): Feature {
    val f = Feature.fromGeometry(Point.fromLngLat(lng, lat))
    f.addStringProperty("c", fill)
    f.addStringProperty("s", stroke)
    f.addNumberProperty("r", radius)
    return f
}

/**
 * The map of a path or a place. The path is coloured by speed (when [times] are given), [selected] is the reading you picked,
 * [marked] the readings with a note, [circle] a place's circle and [spots] single positions. Tapping the path picks the nearest
 * reading. When the map cannot be loaded, the same drawing is made on plain paper instead.
 */
@Composable
fun MapPanel(
    path: List<Pair<Double, Double>>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
    times: List<Long> = emptyList(),
    selected: Int = -1,
    marked: Set<Int> = emptySet(),
    circle: PathFrame.Circle? = null,
    spots: List<Pair<Double, Double>> = emptyList(),
    onPick: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    // With Maps switched off (Settings > Features) no map picture is fetched: the path is drawn on plain paper.
    val mapsOn = Features.isOn(context, Feature.MAPS)
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    var fitted by remember { mutableStateOf(false) }
    val pathNow by rememberUpdatedState(path)
    val pickNow by rememberUpdatedState(onPick)

    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }

    val url = styleUrl
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!mapsOn || url == null || failed) {
            PathCanvas(path = path, description = description, height = height, selected = selected, marked = marked, circle = circle, spots = spots, onPick = onPick)
            if (failed) {
                Text(
                    "The map could not load (no connection, and no downloaded map for this spot), so the path is drawn on plain paper.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
            }
        } else {
            val shape = Wobble.Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(shape)
                    .background(paper.surface2, shape)
                    .border(1.25.dp, paper.line, shape),
            ) {
                MapBase(
                    styleUrl = url,
                    modifier = Modifier.fillMaxWidth().height(height),
                    onMap = { map ->
                        map.addOnMapClickListener { tap ->
                            val pick = pickNow
                            val p = pathNow
                            if (pick == null || p.isEmpty()) {
                                false
                            } else {
                                val at = map.projection.toScreenLocation(tap)
                                var best = -1
                                var bestD = Double.MAX_VALUE
                                p.forEachIndexed { i, (la, ln) ->
                                    val s = map.projection.toScreenLocation(LatLng(la, ln))
                                    val d = Math.hypot((s.x - at.x).toDouble(), (s.y - at.y).toDouble())
                                    if (d < bestD) {
                                        bestD = d
                                        best = i
                                    }
                                }
                                if (best >= 0 && bestD <= 48.0 * context.resources.displayMetrics.density) pick(best)
                                best >= 0
                            }
                        }
                    },
                    onStyle = { map, style ->
                        addLayers(style, paper.accent, paper.accentSoft)
                        fitted = false
                        overlay = map to style
                    },
                    onFailed = { failed = true },
                )
            }
            if (times.isNotEmpty() && path.size >= 2) SpeedLegend()
        }
    }

    val ready = overlay
    LaunchedEffect(ready, path, times, circle, spots) {
        if (ready == null) return@LaunchedEffect
        val (map, style) = ready
        paintPath(style, path, times, paper.ink)
        paintCircle(style, circle)
        if (!fitted) {
            fitted = true
            fit(map, path, circle, spots, context.resources.displayMetrics.density)
        }
    }
    LaunchedEffect(ready, path, selected, marked, spots, circle) {
        if (ready == null) return@LaunchedEffect
        paintDots(ready.second, path, selected, marked, spots, paper.accent, paper.bad, paper.sun, paper.ink, paper.ink2)
    }
}

private fun addLayers(style: Style, accent: Color, accentSoft: Color) {
    if (style.getSource("circle-src") != null) return
    style.addSource(GeoJsonSource("circle-src"))
    style.addLayer(
        FillLayer("circle-fill", "circle-src").withProperties(
            PropertyFactory.fillColor(hex(accentSoft)),
            PropertyFactory.fillOpacity(0.45f),
        ),
    )
    style.addLayer(
        LineLayer("circle-line", "circle-src").withProperties(
            PropertyFactory.lineColor(hex(accent)),
            PropertyFactory.lineWidth(2f),
        ),
    )
    style.addSource(GeoJsonSource("path-src"))
    style.addLayer(
        LineLayer("path-line", "path-src").withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("c"))),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addSource(GeoJsonSource("dots-src"))
    style.addLayer(
        CircleLayer("dots", "dots-src").withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
            PropertyFactory.circleRadius(Expression.get("r")),
            PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get("s"))),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

private fun paintPath(style: Style, path: List<Pair<Double, Double>>, times: List<Long>, ink: Color) {
    val source = style.getSourceAs<GeoJsonSource>("path-src") ?: return
    val features = ArrayList<Feature>()
    if (path.size >= 2) {
        val speeds = times.size == path.size
        val segments = PathSpeeds.segments(path.map { it.first }, path.map { it.second }, if (speeds) times else emptyList())
        segments.forEach { s ->
            val line = LineString.fromLngLats(listOf(Point.fromLngLat(s.fromLng, s.fromLat), Point.fromLngLat(s.toLng, s.toLat)))
            val f = Feature.fromGeometry(line)
            f.addStringProperty("c", if (speeds) PathSpeeds.hex(PathSpeeds.band(s.kmh).rgb) else hex(ink))
            features += f
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun paintCircle(style: Style, circle: PathFrame.Circle?) {
    val source = style.getSourceAs<GeoJsonSource>("circle-src") ?: return
    if (circle == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        return
    }
    val ring = PathSpeeds.circle(circle.lat, circle.lng, circle.radiusM).map { Point.fromLngLat(it.second, it.first) }
    source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))))
}

private fun paintDots(
    style: Style,
    path: List<Pair<Double, Double>>,
    selected: Int,
    marked: Set<Int>,
    spots: List<Pair<Double, Double>>,
    accent: Color,
    bad: Color,
    sun: Color,
    ink: Color,
    ink2: Color,
) {
    val source = style.getSourceAs<GeoJsonSource>("dots-src") ?: return
    val out = ArrayList<Feature>()
    spots.forEach { (la, ln) -> out += dot(la, ln, hex(ink2), "#FFFFFF", 5f) }
    if (path.isNotEmpty()) {
        out += dot(path.first().first, path.first().second, hex(accent), "#FFFFFF", 7f)
        if (path.size >= 2) out += dot(path.last().first, path.last().second, hex(bad), "#FFFFFF", 7f)
    }
    marked.forEach { i -> path.getOrNull(i)?.let { out += dot(it.first, it.second, hex(sun), hex(ink), 6f) } }
    path.getOrNull(selected)?.let { out += dot(it.first, it.second, hex(sun), hex(ink), 10f) }
    source.setGeoJson(FeatureCollection.fromFeatures(out))
}

private fun fit(map: MapLibreMap, path: List<Pair<Double, Double>>, circle: PathFrame.Circle?, spots: List<Pair<Double, Double>>, density: Float) {
    val all = ArrayList<LatLng>()
    path.forEach { all += LatLng(it.first, it.second) }
    spots.forEach { all += LatLng(it.first, it.second) }
    if (circle != null) PathSpeeds.circle(circle.lat, circle.lng, circle.radiusM, 8).forEach { all += LatLng(it.first, it.second) }
    if (all.isEmpty()) return
    val spread = all.maxOf { it.latitude } - all.minOf { it.latitude } + all.maxOf { it.longitude } - all.minOf { it.longitude }
    if (all.size < 2 || spread < 1e-6) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(all.first(), 15.0))
        return
    }
    val bounds = LatLngBounds.Builder().includes(all).build()
    val pad = (36 * density).toInt()
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, pad))
}

/** What the colours of the path mean. */
@Composable
private fun SpeedLegend() {
    val paper = LocalPaper.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PathSpeeds.bands.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).background(Color(0xFF000000.toInt() or b.rgb), CircleShape))
                Text(b.label, fontSize = 11.sp, color = paper.ink2)
            }
        }
    }
}
