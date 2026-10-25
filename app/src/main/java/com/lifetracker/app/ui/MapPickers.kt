package com.lifetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
