package com.lifetracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.lifetracker.app.ui.theme.BackgroundMode
import com.lifetracker.app.ui.theme.BackgroundSettings
import com.lifetracker.app.ui.theme.BackgroundStyle
import com.lifetracker.app.ui.theme.LocalBackgroundStyle
import com.lifetracker.app.ui.theme.LocalPaper
import java.util.Locale

private val backgroundSwatches = listOf(
    0xFFFFFCF7.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFF0E5D3.toInt(),
    0xFFDDE8F6.toInt(),
    0xFFDDEFE3.toInt(),
    0xFFF0E3F5.toInt(),
    0xFF30343B.toInt(),
    0xFF000000.toInt(),
)

@Composable
fun BackgroundCustomizer() {
    val context = LocalContext.current
    val style = LocalBackgroundStyle.current
    val dark = LocalPaper.current.dark
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val permissionTaken = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (permissionTaken) {
                val previous = style.photoUri?.let(Uri::parse)
                BackgroundSettings.set(context, style.copy(mode = BackgroundMode.Photo, photoUri = uri.toString()))
                if (previous != null && previous != uri) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            previous,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                message = "Background photo saved on this device."
            } else {
                message = "Android did not grant lasting access to that image. Please choose it again."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PaperSection("Background", "Choose what appears behind every screen")
        Text(
            "The background changes independently of cards, buttons and controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BackgroundMode.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { mode ->
                    BackgroundModeOption(
                        mode = mode,
                        selected = style.mode == mode,
                        style = style,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            BackgroundSettings.set(context, style.copy(mode = mode))
                            message = null
                        },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        when (style.mode) {
            BackgroundMode.Solid -> {
                ArgbSetting(
                    title = if (dark) "Dark background colour" else "Light background colour",
                    colorArgb = style.solidArgb(dark),
                    swatches = backgroundSwatches,
                    onColorSelected = { selected ->
                        BackgroundSettings.set(
                            context,
                            if (dark) style.copy(darkSolidArgb = selected)
                            else style.copy(lightSolidArgb = selected),
                        )
                    },
                )
            }

            BackgroundMode.Gradient -> {
                ArgbSetting(
                    title = "Gradient start",
                    colorArgb = style.gradientStartArgb(dark),
                    swatches = backgroundSwatches,
                    onColorSelected = { selected ->
                        BackgroundSettings.set(
                            context,
                            if (dark) style.copy(darkGradientStartArgb = selected)
                            else style.copy(lightGradientStartArgb = selected),
                        )
                    },
                )
                ArgbSetting(
                    title = "Gradient end",
                    colorArgb = style.gradientEndArgb(dark),
                    swatches = backgroundSwatches,
                    onColorSelected = { selected ->
                        BackgroundSettings.set(
                            context,
                            if (dark) style.copy(darkGradientEndArgb = selected)
                            else style.copy(lightGradientEndArgb = selected),
                        )
                    },
                )
            }

            BackgroundMode.Dots -> {
                ArgbSetting(
                    title = "Base colour",
                    colorArgb = style.dotsBackgroundArgb(dark),
                    swatches = backgroundSwatches,
                    onColorSelected = { selected ->
                        BackgroundSettings.set(
                            context,
                            if (dark) style.copy(darkDotsBackgroundArgb = selected)
                            else style.copy(lightDotsBackgroundArgb = selected),
                        )
                    },
                )
                ArgbSetting(
                    title = "Dot colour (supports alpha, e.g. #667B7B7B)",
                    colorArgb = style.dotsArgb(dark),
                    swatches = listOf(0x667B7B7B, 0x668F8FA0, 0x669E7855, 0x66FFFFFF, 0xFF333333.toInt()),
                    onColorSelected = { selected ->
                        BackgroundSettings.set(
                            context,
                            if (dark) style.copy(darkDotsArgb = selected)
                            else style.copy(lightDotsArgb = selected),
                        )
                    },
                )
            }

            BackgroundMode.Oled -> {
                Text(
                    if (dark) "Pure black for dark mode." else "Pure white for light mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BackgroundMode.Photo -> {
                Text(
                    if (style.photoUri == null) {
                        "Choose a photo to use it as the full-screen background."
                    } else {
                        "Your selected photo is used behind every screen. A theme-matched overlay keeps content readable."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickPhoto.launch(arrayOf("image/*")) }) {
                        Text(if (style.photoUri == null) "Choose photo" else "Change photo")
                    }
                    if (style.photoUri != null) {
                        OutlinedButton(
                            onClick = {
                                val previous = style.photoUri?.let(Uri::parse)
                                BackgroundSettings.set(
                                    context,
                                    style.copy(mode = BackgroundMode.Solid, photoUri = null),
                                )
                                if (previous != null) {
                                    runCatching {
                                        context.contentResolver.releasePersistableUriPermission(
                                            previous,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                        )
                                    }
                                }
                                message = "Photo removed. Solid background selected."
                            },
                        ) { Text("Remove") }
                    }
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackgroundModeOption(
    mode: BackgroundMode,
    selected: Boolean,
    style: BackgroundStyle,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(previewBrush(mode, style, dark)),
        ) {
            if (mode == BackgroundMode.Dots) {
                Canvas(Modifier.fillMaxSize()) {
                    val gap = 11.dp.toPx()
                    val radius = 1.dp.toPx()
                    val dot = Color(style.dotsArgb(dark))
                    var y = gap / 2f
                    while (y < size.height) {
                        var x = gap / 2f
                        while (x < size.width) {
                            drawCircle(dot, radius, Offset(x, y))
                            x += gap
                        }
                        y += gap
                    }
                }
            }
            if (mode == BackgroundMode.Photo) {
                Text(
                    text = if (style.photoUri == null) "Choose photo" else "Photo selected",
                    modifier = Modifier.align(Alignment.Center),
                    color = if (dark) Color.White else Color(0xFF202124),
                    fontSize = 11.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(mode.title, style = MaterialTheme.typography.labelLarge)
            if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun previewBrush(mode: BackgroundMode, style: BackgroundStyle, dark: Boolean): Brush =
    when (mode) {
        BackgroundMode.Solid -> Brush.linearGradient(
            listOf(Color(style.solidArgb(dark)), Color(style.solidArgb(dark))),
        )
        BackgroundMode.Gradient -> Brush.linearGradient(
            listOf(Color(style.gradientStartArgb(dark)), Color(style.gradientEndArgb(dark))),
        )
        BackgroundMode.Dots -> Brush.linearGradient(
            listOf(Color(style.dotsBackgroundArgb(dark)), Color(style.dotsBackgroundArgb(dark))),
        )
        BackgroundMode.Oled -> Brush.linearGradient(
            listOf(if (dark) Color.Black else Color.White, if (dark) Color.Black else Color.White),
        )
        BackgroundMode.Photo -> Brush.linearGradient(
            listOf(Color(0xFFB9C9D5), Color(0xFFE9DFD0)),
        )
    }

@Composable
private fun ArgbSetting(
    title: String,
    colorArgb: Int,
    swatches: List<Int>,
    onColorSelected: (Int) -> Unit,
) {
    var hex by remember(colorArgb) { mutableStateOf(colorArgb.toHex()) }
    val parsed = parseArgb(hex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable {
                            hex = swatch.toHex()
                            onColorSelected(swatch)
                        },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = it.take(9) },
                modifier = Modifier.weight(1f),
                label = { Text("Hex colour") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Button(
                onClick = { parsed?.let(onColorSelected) },
                enabled = parsed != null,
            ) { Text("Apply") }
        }
    }
}

private fun Int.toHex(): String {
    val alpha = (this ushr 24) and 0xFF
    val rgb = this and 0xFFFFFF
    return if (alpha == 0xFF) "#%06X".format(Locale.ROOT, rgb)
    else "#%08X".format(Locale.ROOT, this)
}

private fun parseArgb(value: String): Int? {
    val digits = value.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (digits.any { it.digitToIntOrNull(16) == null }) return null
    return runCatching {
        when (digits.length) {
            6 -> (0xFF000000L or digits.toLong(16)).toInt()
            8 -> digits.toLong(16).toInt()
            else -> null
        }
    }.getOrNull()
}
