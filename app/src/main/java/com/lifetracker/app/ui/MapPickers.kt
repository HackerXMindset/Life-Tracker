package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.MapStyleStore
import com.lifetracker.app.ui.theme.LocalPaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Pick a spot on the map: move the map so the cross sits on the spot, then use it. [startLat] and [startLng] say where the map
 * opens (null for a view of the whole world).
 */
@Composable
fun MapPickDialog(
    startLat: Double?,
    startLng: Double?,
    onPicked: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick on the map") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val url = styleUrl
                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    if (url != null) {
                        MapBase(
                            styleUrl = url,
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            onStyle = { m, _ ->
                                map = m
                                val camera = CameraPosition.Builder()
                                if (startLat != null && startLng != null) {
                                    camera.target(LatLng(startLat, startLng)).zoom(15.0)
                                } else {
                                    camera.target(LatLng(20.0, 0.0)).zoom(1.5)
                                }
                                m.cameraPosition = camera.build()
                            },
                            onFailed = { failed = true },
                        )
                        Canvas(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = 14.dp.toPx()
                            drawCircle(paper.ink, r, c, style = Stroke(2.dp.toPx()))
                            drawLine(paper.ink, Offset(c.x - r - 6.dp.toPx(), c.y), Offset(c.x + r + 6.dp.toPx(), c.y), 2.dp.toPx())
                            drawLine(paper.ink, Offset(c.x, c.y - r - 6.dp.toPx()), Offset(c.x, c.y + r + 6.dp.toPx()), 2.dp.toPx())
                        }
                    }
                }
                Text(
                    if (failed) "The map could not load. Check the connection, or type the position instead."
                    else "Move and zoom the map so the cross is on the spot.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = map != null && !failed,
                onClick = {
                    val target = map?.cameraPosition?.target
                    if (target != null) onPicked(target.latitude, target.longitude)
                },
            ) { Text("Use this spot") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Frame an area to keep on the phone: move and zoom the map so the part you want fills the window, name it, choose the zoom
 * levels, and see how many tiles it is, about how big, and the free space before you download.
 */
@Composable
fun MapFrameDialog(
    start: Pair<Double, Double>?,
    average: Pair<Long, Boolean>,
    freeBytes: Long,
    limit: com.lifetracker.app.data.MapStyles.Limit?,
    onDownload: (name: String, bounds: com.lifetracker.app.data.Bounds, minZ: Int, maxZ: Int, done: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var styleUrl by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<com.lifetracker.app.data.Bounds?>(null) }
    var name by remember { mutableStateOf("") }
    var minText by remember { mutableStateOf(com.lifetracker.app.data.OfflineRules.AUTO_MIN_ZOOM.toString()) }
    var maxText by remember { mutableStateOf(com.lifetracker.app.data.OfflineRules.AUTO_MAX_ZOOM.toString()) }
    var refused by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        styleUrl = withContext(Dispatchers.IO) { MapStyleStore.current(context, AppDatabase.get(context)).url }
    }
    val minZ = minText.toIntOrNull()
    val maxZ = maxText.toIntOrNull()
    val zoomsOk = minZ != null && maxZ != null && minZ in 0..22 && maxZ in 0..22 && minZ <= maxZ
    val b = bounds
    val tiles = if (b != null && zoomsOk) com.lifetracker.app.data.TileMath.count(b, minZ!!, maxZ!!) else 0L
    val estimate = com.lifetracker.app.data.TileMath.estimateBytes(tiles, average.first)
    val problem = when {
        b == null -> "Move the map to choose an area."
        !zoomsOk -> "The zoom levels must be between 0 and 22, lowest first."
        !b.valid() -> "That part of the map cannot be downloaded. Zoom in a little."
        else -> com.lifetracker.app.data.OfflineRules.overLimit(tiles, if (average.second) estimate else 0L, limit)
            ?: com.lifetracker.app.data.OfflineRules.storageProblem(estimate, freeBytes)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download an area") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val url = styleUrl
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    if (url != null) {
                        MapBase(
                            styleUrl = url,
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            onMap = { m ->
                                m.addOnCameraIdleListener {
                                    val v = m.projection.visibleRegion.latLngBounds
                                    bounds = com.lifetracker.app.data.Bounds(
                                        v.southWest.longitude, v.southWest.latitude, v.northEast.longitude, v.northEast.latitude,
                                    )
                                }
                            },
                            onStyle = { m, _ ->
                                val camera = CameraPosition.Builder()
                                if (start != null) camera.target(LatLng(start.first, start.second)).zoom(11.0)
                                else camera.target(LatLng(20.0, 0.0)).zoom(2.0)
                                m.cameraPosition = camera.build()
                            },
                            onFailed = { failed = true },
                        )
                    }
                }
                Text(
                    if (failed) "The map could not load, so an area cannot be framed now. Check the connection."
                    else "The area is what you see in the window. Zoom in or out to change it.",
                    fontSize = 12.sp,
                    color = paper.ink2,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = minText, onValueChange = { minText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Lowest zoom") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Highest zoom") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                if (b != null && zoomsOk) {
                    Text(
                        "$tiles tiles · about " + com.lifetracker.app.data.TileMath.sizeText(estimate) +
                            (if (average.second) " (from your own areas)" else " (a first guess; the real size shows after the download)") +
                            " · " + com.lifetracker.app.data.TileMath.sizeText(freeBytes) + " free",
                        fontSize = 13.sp,
                        color = paper.ink,
                    )
                }
                (refused ?: problem)?.let { Text(it, fontSize = 12.sp, color = paper.bad) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = problem == null && b != null && minZ != null && maxZ != null,
                onClick = {
                    onDownload(name, b!!, minZ!!, maxZ!!) { err ->
                        if (err == null) onDismiss() else refused = err
                    }
                },
            ) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
