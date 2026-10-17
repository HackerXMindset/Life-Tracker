package com.lifetracker.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import com.lifetracker.app.data.AppBranding
import com.lifetracker.app.data.AppBranding.ShortcutResult
import com.lifetracker.app.ui.theme.PaperCard
import kotlin.math.max

/** Lets the user choose any image supported by Android, crop/position it in-app, and make a matching launcher shortcut. */
@Composable
fun AppIdentitySettings(onDesignSaved: () -> Unit = {}) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(AppBranding.savedName(context)) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var iconRevision by remember { mutableIntStateOf(0) }
    var pendingImage by remember { mutableStateOf<Bitmap?>(null) }
    var cropOpen by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching { AppBranding.decodeImage(context, uri) }
            result.onSuccess { bitmap ->
                pendingImage = bitmap
                cropOpen = true
                message = "Move the image and adjust the zoom, then use the crop."
            }.onFailure { error ->
                message = error.message ?: "The image could not be opened. Choose another image."
            }
        }
    }

    val preview = remember(iconRevision) { AppBranding.createIconBitmap(context, 144).asImageBitmap() }
    val hasCustomIcon = remember(iconRevision) { AppBranding.hasCustomIcon(context) }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text(
            text = "Custom home-screen identity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Choose a name and any supported image for a separate home-screen shortcut. The installed app-drawer name and icon stay unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Image(bitmap = preview, contentDescription = "Preview of the home-screen shortcut icon",
                    modifier = Modifier.size(56.dp), contentScale = ContentScale.Fit)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (hasCustomIcon) "Custom icon selected" else "Using the current app icon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Photos, PNG, JPEG, WebP and SVG are supported when Android can decode them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Launcher appearance can vary, including icon shape and label truncation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                message = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Home-screen name") },
            supportingText = { Text("Long names may be shortened by your launcher.") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                Text("Choose image")
            }
            OutlinedButton(
                onClick = {
                    AppBranding.clearCustomIcon(context)
                    iconRevision++
                    message = "Using the original app icon again. Update the shortcut to apply it."
                },
                enabled = hasCustomIcon,
            ) {
                Text("Reset logo")
            }
        }
        Button(
            onClick = {
                AppBranding.saveName(context, name)
                message = when (AppBranding.createOrUpdateShortcut(context, name)) {
                    ShortcutResult.PinRequested ->
                        "Approve the pin request in your launcher to add the shortcut."
                    ShortcutResult.Updated ->
                        "Custom home-screen shortcut updated. Your launcher may take a moment to refresh."
                    ShortcutResult.Unsupported ->
                        "This launcher does not support creating or updating pinned shortcuts."
                    ShortcutResult.UpdateFailed ->
                        "The launcher could not update the existing shortcut. Try creating it again."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save name and create / update shortcut")
        }
        OutlinedButton(
            onClick = {
                runCatching {
                    AppBranding.saveDesign(context, name).also {
                        AppBranding.saveName(context, name)
                    }
                }.onSuccess { design ->
                    message = "Saved \"${design.name}\". Your saved designs are listed below."
                    onDesignSaved()
                }.onFailure { error ->
                    message = error.message ?: "This design could not be saved."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save current design")
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (cropOpen) {
        pendingImage?.let { source ->
            CropImageDialog(
                source = source,
                onDismiss = {
                    cropOpen = false
                    pendingImage = null
                },
                onUse = { cropped ->
                    val saved = runCatching { AppBranding.saveCustomIcon(context, cropped) }
                    cropped.recycle()
                    cropOpen = false
                    pendingImage = null
                    if (saved.isSuccess) {
                        iconRevision++
                        message = "Cropped image saved privately on this phone. Save name and create / update shortcut to apply it."
                    } else {
                        message = saved.exceptionOrNull()?.message ?: "The cropped image could not be saved."
                    }
                },
            )
        }
    }
}

@Composable
private fun CropImageDialog(
    source: Bitmap,
    onDismiss: () -> Unit,
    onUse: (Bitmap) -> Unit,
) {
    val viewportDp = 260.dp
    val viewportPx = with(LocalDensity.current) { viewportDp.toPx() }
    val fittedWidthPx = source.width * max(viewportPx / source.width, viewportPx / source.height)
    val fittedHeightPx = source.height * max(viewportPx / source.width, viewportPx / source.height)

    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offsetX by remember(source) { mutableFloatStateOf(0f) }
    var offsetY by remember(source) { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Crop image", style = MaterialTheme.typography.titleLarge)
                var fitMode by remember(source) { mutableIntStateOf(1) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = fitMode == 0, onClick = { fitMode = 0 }, label = { Text("Fit whole image") })
                    FilterChip(selected = fitMode == 1, onClick = { fitMode = 1 }, label = { Text("Fill square") })
                }
                Text(
                    "Drag the image to position it. Use the slider to zoom in or out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(viewportDp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = "Image crop preview",
                        contentScale = if (fitMode == 0) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX
                                translationY = offsetY
                            }
                            .pointerInput(source, zoom, viewportPx) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val widthMax = ((fittedWidthPx * zoom - viewportPx) / 2f).coerceAtLeast(0f)
                                    val heightMax = ((fittedHeightPx * zoom - viewportPx) / 2f).coerceAtLeast(0f)
                                    offsetX = (offsetX + dragAmount.x).coerceIn(-widthMax, widthMax)
                                    offsetY = (offsetY + dragAmount.y).coerceIn(-heightMax, heightMax)
                                }
                            },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Zoom", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Zoom: " + String.format(java.util.Locale.US, "%.1f", zoom) + "×",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Slider(
                    value = zoom,
                    onValueChange = { value ->
                        zoom = value
                        offsetX = offsetX.coerceIn(
                            -((fittedWidthPx * value - viewportPx) / 2f).coerceAtLeast(0f),
                            ((fittedWidthPx * value - viewportPx) / 2f).coerceAtLeast(0f),
                        )
                        offsetY = offsetY.coerceIn(
                            -((fittedHeightPx * value - viewportPx) / 2f).coerceAtLeast(0f),
                            ((fittedHeightPx * value - viewportPx) / 2f).coerceAtLeast(0f),
                        )
                    },
                    valueRange = 1f..4f,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Center") }
                    Button(
                        onClick = {
                            onUse(if (fitMode == 0) AppBranding.fitBitmap(source) else AppBranding.cropBitmap(source, zoom, offsetX, offsetY, viewportPx))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Use crop") }
                }
                Spacer(Modifier.size(2.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
