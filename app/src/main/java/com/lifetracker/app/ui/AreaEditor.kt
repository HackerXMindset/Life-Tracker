package com.lifetracker.app.ui

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.data.PlaceShape
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperSegmented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.hypot

/**
 * Draw the area of a place freehand, like cropping a picture: drag a corner to move it, tap the map to add a corner, press and hold a
 * corner to remove it. "Move the map" lets you pan and zoom without changing the shape. [startCorners] is the shape to adjust (a
 * ring round the old circle, or the area drawn before); when it is empty the map opens at [startLat], [startLng] and you tap out the corners.
 */
@Composable
fun AreaEditorDialog(
    startCorners: List<Pair<Double, Double>>,
    startLat: Double?,
    startLng: Double?,
    onDone: (List<Pair<Double, Double>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var corners by remember { mutableStateOf(startCorners) }
    var editing by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }

    fun screen(m: MapLibreMap, c: Pair<Double, Double>): Offset =
        m.projection.toScreenLocation(LatLng(c.first, c.second)).let { Offset(it.x, it.y) }

    fun nearest(m: MapLibreMap, at: Offset, reachPx: Float): Int {
        var best = -1
        var bestD = reachPx
        corners.forEachIndexed { i, c ->
            val p = screen(m, c)
            val d = hypot(p.x - at.x, p.y - at.y)
            if (d <= bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    /** Where a new corner goes in the ring: after the corner whose edge is nearest to the tap. */
    fun insertAt(m: MapLibreMap, at: Offset): Int {
        if (corners.size < 3) return corners.size
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in corners.indices) {
            val a = screen(m, corners[i])
            val b = screen(m, corners[(i + 1) % corners.size])
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0f) 0f else (((at.x - a.x) * dx + (at.y - a.y) * dy) / len2).coerceIn(0f, 1f)
            val d = hypot(a.x + t * dx - at.x, a.y + t * dy - at.y)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best + 1
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw the area") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperSegmented(
                    options = listOf("Edit corners", "Move the map"),
                    selected = if (editing) 0 else 1,
                    onSelect = { editing = it == 0 },
                )
                val url = styleUrl
                Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    if (url != null) {
                        MapBase(
                            styleUrl = url,
                            modifier = Modifier.fillMaxWidth().height(340.dp),
                            onMap = { m ->
                                m.addOnCameraMoveListener { tick++ }
                                m.addOnCameraIdleListener { tick++ }
                            },
                            onStyle = { m, _ ->
                                map = m
                                if (corners.size >= 2) {
                                    val b = LatLngBounds.Builder()
                                    corners.forEach { b.include(LatLng(it.first, it.second)) }
                                    m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 90))
                                } else {
                                    val camera = CameraPosition.Builder()
                                    if (startLat != null && startLng != null) camera.target(LatLng(startLat, startLng)).zoom(16.0)
                                    else camera.target(LatLng(20.0, 0.0)).zoom(1.5)
                                    m.cameraPosition = camera.build()
                                }
                                tick++
                            },
                            onFailed = { failed = true },
                        )
                        val m = map
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (editing && m != null) {
                                        Modifier
                                            .pointerInput(m) {
                                                var held = -1
                                                detectDragGestures(
                                                    onDragStart = { at -> held = nearest(m, at, 44.dp.toPx()) },
                                                    onDragEnd = { held = -1 },
                                                    onDragCancel = { held = -1 },
                                                    onDrag = { change, _ ->
                                                        if (held in corners.indices) {
                                                            change.consume()
                                                            val ll = m.projection.fromScreenLocation(PointF(change.position.x, change.position.y))
                                                            corners = corners.toMutableList().also { it[held] = ll.latitude to ll.longitude }
                                                        }
                                                    },
                                                )
                                            }
                                            .pointerInput(m) {
                                                detectTapGestures(
                                                    onTap = { at ->
                                                        if (nearest(m, at, 44.dp.toPx()) < 0 && corners.size < PlaceShape.MAX_CORNERS) {
                                                            val ll = m.projection.fromScreenLocation(PointF(at.x, at.y))
                                                            corners = corners.toMutableList().also { it.add(insertAt(m, at), ll.latitude to ll.longitude) }
                                                        }
                                                    },
                                                    onLongPress = { at ->
                                                        val i = nearest(m, at, 44.dp.toPx())
                                                        if (i >= 0) corners = corners.toMutableList().also { it.removeAt(i) }
                                                    },
                                                )
                                            }
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            // Reading the tick makes the shape follow the map as it moves.
                            if (tick < 0 || m == null) return@Canvas
                            if (corners.isNotEmpty()) {
                                val pts = corners.map { screen(m, it) }
                                val path = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                    if (pts.size >= 3) close()
                                }
                                if (pts.size >= 3) drawPath(path, paper.sun.copy(alpha = 0.22f))
                                drawPath(path, paper.sun, style = Stroke(2.5.dp.toPx()))
                                if (editing) {
                                    pts.forEach {
                                        drawCircle(Color.White, 10.dp.toPx(), it)
                                        drawCircle(paper.ink, 7.dp.toPx(), it)
                                    }
                                }
                            }
                        }
                    }
                }
                val area = PlaceShape.areaM2(corners)
                Text(
                    when {
                        failed -> "The map could not load. Check the connection, or keep the circle."
                        corners.size < 3 -> "Tap the map to put down at least 3 corners."
                        else -> corners.size.toString() + " corners · about " + (if (area >= 10_000) String.format(java.util.Locale.ENGLISH, "%.1f ha", area / 10_000.0) else "${area.toInt()} m²") +
                            if (PlaceShape.valid(corners)) "" else " · too small"
                    },
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
                Text(
                    "Drag a corner to move it. Tap the map to add a corner. Press and hold a corner to remove it.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton("Remove last corner", { if (corners.isNotEmpty()) corners = corners.dropLast(1) }, style = PaperButtonStyle.Ghost, small = true)
                    PaperButton("Clear", { corners = emptyList() }, style = PaperButtonStyle.Ghost, small = true)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = PlaceShape.valid(corners), onClick = { onDone(corners) }) { Text("Use this area") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
