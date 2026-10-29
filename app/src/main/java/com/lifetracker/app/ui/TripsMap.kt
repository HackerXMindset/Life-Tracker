package com.lifetracker.app.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.Wobble
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** One trip on the Trips map: its id, its colour (0xRRGGBB) and its path. */
class TripLine(val id: String, val color: Int, val path: List<Pair<Double, Double>>)

private fun hexOf(rgb: Int) = String.format("#%06X", rgb and 0xFFFFFF)

/**
 * Many trips on one map, each in its own colour. The [selectedId] trip is drawn thick and on top, the others fainter, and it
 * gets a start and an end dot. Tapping a path calls [onPick] with that trip (tapping empty map calls it with null).
 * When the map cannot load, the same trips are drawn on plain paper.
 */
@Composable
fun TripsMap(
    lines: List<TripLine>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    val mapsOn = Features.isOn(context, Feature.MAPS)
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }
    val pickNow by rememberUpdatedState(onPick)
    val density = context.resources.displayMetrics.density

    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }

    val url = styleUrl
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!mapsOn || url == null || failed) {
            PlainTrips(lines, selectedId, description, height)
            if (failed) {
                Text(
                    "The map could not load (no connection, and no downloaded map for this spot), so the trips are drawn on plain paper.",
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
                            val at = map.projection.toScreenLocation(tap)
                            val r = 22f * density
                            val hit = map.queryRenderedFeatures(RectF(at.x - r, at.y - r, at.x + r, at.y + r), "trips-line")
                                .firstOrNull()?.getStringProperty("id")
                            pickNow(hit)
                            hit != null
                        }
                    },
                    onStyle = { map, style ->
                        addTripLayers(style)
                        ready = map to style
                    },
                    onFailed = { failed = true },
                )
            }
        }
    }

    val fitKey = lines.map { it.id }
    LaunchedEffect(ready, lines, selectedId) {
        val (_, style) = ready ?: return@LaunchedEffect
        paintTrips(style, lines, selectedId, paper.accent.toArgbRgb(), paper.bad.toArgbRgb())
    }
    LaunchedEffect(ready, fitKey) {
        val (map, _) = ready ?: return@LaunchedEffect
        fitAll(map, lines, density)
    }
}

private fun Color.toArgbRgb(): Int = toArgb() and 0xFFFFFF

private fun addTripLayers(style: Style) {
    if (style.getSource("trips-src") != null) return
    style.addSource(GeoJsonSource("trips-src"))
    style.addLayer(
        LineLayer("trips-line", "trips-src").withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("c"))),
            PropertyFactory.lineWidth(Expression.get("w")),
            PropertyFactory.lineOpacity(Expression.get("o")),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addSource(GeoJsonSource("ends-src"))
    style.addLayer(
        CircleLayer("ends", "ends-src").withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        ),
    )
}

private fun paintTrips(style: Style, lines: List<TripLine>, selectedId: String?, startRgb: Int, endRgb: Int) {
    val src = style.getSourceAs<GeoJsonSource>("trips-src") ?: return
    val ends = style.getSourceAs<GeoJsonSource>("ends-src") ?: return
    val features = ArrayList<Feature>()
    // The picked trip goes last so it is drawn on top.
    (lines.filter { it.id != selectedId } + lines.filter { it.id == selectedId }).forEach { l ->
        if (l.path.size < 2) return@forEach
        val line = LineString.fromLngLats(l.path.map { Point.fromLngLat(it.second, it.first) })
        val f = Feature.fromGeometry(line)
        val picked = l.id == selectedId
        f.addStringProperty("id", l.id)
        f.addStringProperty("c", hexOf(l.color))
        f.addNumberProperty("w", if (picked) 7f else 4f)
        f.addNumberProperty("o", if (selectedId == null || picked) 0.95f else 0.45f)
        features += f
    }
    src.setGeoJson(FeatureCollection.fromFeatures(features))
    val dots = ArrayList<Feature>()
    lines.firstOrNull { it.id == selectedId && it.path.size >= 2 }?.let { l ->
        fun dot(p: Pair<Double, Double>, rgb: Int) = Feature.fromGeometry(Point.fromLngLat(p.second, p.first)).also { it.addStringProperty("c", hexOf(rgb)) }
        dots += dot(l.path.first(), startRgb)
        dots += dot(l.path.last(), endRgb)
    }
    ends.setGeoJson(FeatureCollection.fromFeatures(dots))
}

private fun fitAll(map: MapLibreMap, lines: List<TripLine>, density: Float) {
    val all = lines.flatMap { l -> l.path.map { LatLng(it.first, it.second) } }
    if (all.isEmpty()) return
    val spread = all.maxOf { it.latitude } - all.minOf { it.latitude } + all.maxOf { it.longitude } - all.minOf { it.longitude }
    if (all.size < 2 || spread < 1e-6) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(all.first(), 15.0))
        return
    }
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(all).build(), (36 * density).toInt()))
}

/** The trips drawn on plain paper, for when the map cannot be loaded. */
@Composable
private fun PlainTrips(lines: List<TripLine>, selectedId: String?, description: String, height: Dp) {
    val paper = LocalPaper.current
    val shape = Wobble.Card
    val pts = lines.flatMap { it.path }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(paper.surface2, shape)
            .border(1.25.dp, paper.line, shape)
            .semantics { contentDescription = description },
    ) {
        if (pts.size < 2) return@Canvas
        val minLat = pts.minOf { it.first }
        val maxLat = pts.maxOf { it.first }
        val minLng = pts.minOf { it.second }
        val maxLng = pts.maxOf { it.second }
        val k = cos(Math.toRadians((minLat + maxLat) / 2.0)).coerceAtLeast(0.01)
        val w = ((maxLng - minLng) * k).coerceAtLeast(1e-9)
        val h = (maxLat - minLat).coerceAtLeast(1e-9)
        val pad = 24.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h).toFloat()
        val offX = (size.width - (w * scale).toFloat()) / 2f
        val offY = (size.height - (h * scale).toFloat()) / 2f
        fun at(p: Pair<Double, Double>) = Offset(offX + ((p.second - minLng) * k * scale).toFloat(), offY + ((maxLat - p.first) * scale).toFloat())
        (lines.filter { it.id != selectedId } + lines.filter { it.id == selectedId }).forEach { l ->
            if (l.path.size < 2) return@forEach
            val path = Path()
            l.path.forEachIndexed { i, p -> at(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
            val picked = l.id == selectedId
            drawPath(
                path,
                Color(0xFF000000.toInt() or l.color).copy(alpha = if (selectedId == null || picked) 0.95f else 0.45f),
                style = Stroke(width = (if (picked) 5.dp else 3.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
