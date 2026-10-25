package com.lifetracker.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.lifetracker.app.ui.theme.AppTheme
import com.lifetracker.app.ui.theme.BackgroundMode
import com.lifetracker.app.ui.theme.BackgroundSettings
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.ThemeModes

private val BackgroundColorPresets = listOf(
    0xFFFFFCF7.toInt(),
    0xFFF1DFC0.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFE0E0E0.toInt(),
    0xFF1F1A14.toInt(),
    0xFF000000.toInt(),
    0xFFFFB4A8.toInt(),
    0xFFD8C8FF.toInt(),
)

/** Appearance controls for global background styles; colour values are saved separately per theme. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundCustomizer() {
    val context = LocalContext.current
    val preferences = BackgroundSettings.preferences
    val dark = ThemeModes.isDark(AppTheme.mode, isSystemInDarkTheme())
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val choosePhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val permissionSaved = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            BackgroundSettings.setPhotoUri(context, uri.toString())
            BackgroundSettings.setMode(context, BackgroundMode.PHOTO)
            message = if (permissionSaved) {
                "Photo selected. It will remain available after restarting the app."
            } else {
                "Photo selected, but Android did not allow permanent access. Choose it again if it disappears after a restart."
            }
        }
    }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text(
            "Background",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Change the screen behind your cards and controls. Your card and button styling stays the same.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BackgroundMode.entries.forEach { mode ->
                FilterChip(
                    selected = preferences.mode == mode,
                    onClick = {
                        BackgroundSettings.setMode(context, mode)
                        message = null
                    },
                    label = { Text(mode.label) },
                )
            }
        }

        BackgroundPreview(mode = preferences.mode, dark = dark, preferencesPhotoSelected = preferences.photoUri != null)

        when (preferences.mode) {
            BackgroundMode.SOLID -> ColorEditor(
                label = "Background colour",
                color = preferences.solid(dark),
                onColor = { BackgroundSettings.setSolidColor(context, dark, it) },
            )
            BackgroundMode.GRADIENT -> {
                ColorEditor(
                    label = "Gradient start",
                    color = preferences.gradientStart(dark),
                    onColor = { BackgroundSettings.setGradientStart(context, dark, it) },
                )
                ColorEditor(
                    label = "Gradient end",
                    color = preferences.gradientEnd(dark),
                    onColor = { BackgroundSettings.setGradientEnd(context, dark, it) },
                )
            }
            BackgroundMode.DOTS -> {
                ColorEditor(
                    label = "Base colour",
                    color = preferences.dotsBackground(dark),
                    onColor = { BackgroundSettings.setDotsBackground(context, dark, it) },
                )
                ColorEditor(
                    label = "Dot colour",
                    color = preferences.dotsColor(dark),
                    onColor = { BackgroundSettings.setDotsColor(context, dark, it) },
                )
            }
            BackgroundMode.OLED -> Text(
                "Uses pure black in dark theme and pure white in light theme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BackgroundMode.PHOTO -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { choosePhoto.launch(arrayOf("image/*")) }) {
                        Text(if (preferences.photoUri == null) "Choose photo" else "Change photo")
                    }
                    if (preferences.photoUri != null) {
                        OutlinedButton(onClick = {
                            BackgroundSettings.setPhotoUri(context, null)
                            BackgroundSettings.setMode(context, BackgroundMode.SOLID)
                            message = "Custom photo removed."
                        }) {
                            Text("Remove")
                        }
                    }
                }
                Text(
                    "Photos are kept on this device. A subtle light or dark wash helps keep the interface readable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "Light and dark themes keep separate colour choices. Switch the theme above to customise the other one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackgroundPreview(
    mode: BackgroundMode,
    dark: Boolean,
    preferencesPhotoSelected: Boolean,
) {
    val preferences = BackgroundSettings.preferences
    val brush = when (mode) {
        BackgroundMode.GRADIENT -> Brush.linearGradient(
            listOf(Color(preferences.gradientStart(dark)), Color(preferences.gradientEnd(dark))),
        )
        BackgroundMode.DOTS -> Brush.linearGradient(
            listOf(Color(preferences.dotsBackground(dark)), Color(preferences.dotsBackground(dark))),
        )
        else -> null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush ?: when (mode) {
                    BackgroundMode.SOLID -> Brush.linearGradient(listOf(Color(preferences.solid(dark)), Color(preferences.solid(dark))))
                    BackgroundMode.OLED -> Brush.linearGradient(listOf(if (dark) Color.Black else Color.White, if (dark) Color.Black else Color.White))
                    BackgroundMode.PHOTO -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
                    BackgroundMode.DOTS -> Brush.linearGradient(listOf(Color(preferences.dotsBackground(dark)), Color(preferences.dotsBackground(dark))))
                    BackgroundMode.GRADIENT -> Brush.linearGradient(listOf(Color(preferences.gradientStart(dark)), Color(preferences.gradientEnd(dark))))
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            BackgroundMode.DOTS -> Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val spacing = 18.dp.toPx()
                val radius = 1.5.dp.toPx()
                var x = spacing / 2f
                while (x < size.width) {
                    var y = spacing / 2f
                    while (y < size.height) {
                        drawCircle(Color(preferences.dotsColor(dark)), radius, androidx.compose.ui.geometry.Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
            }
            BackgroundMode.PHOTO -> Text(
                if (preferencesPhotoSelected) "Custom photo selected" else "Choose a photo to preview it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            else -> Unit
        }
    }
}

@Composable
private fun ColorEditor(
    label: String,
    color: Int,
    onColor: (Int) -> Unit,
) {
    var input by rememberSaveable(label, color) { mutableStateOf(BackgroundSettings.colorToHex(color)) }
    val parsed = BackgroundSettings.parseHexColor(input)

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("HEX colour") },
                singleLine = true,
                isError = input.isNotBlank() && parsed == null,
            )
            TextButton(onClick = { parsed?.let(onColor) }, enabled = parsed != null) {
                Text("Apply")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackgroundColorPresets.forEach { preset ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(preset))
                        .border(
                            width = if (preset == color) 2.dp else 1.dp,
                            color = if (preset == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable {
                            input = BackgroundSettings.colorToHex(preset)
                            onColor(preset)
                        },
                )
            }
        }
        if (parsed == null && input.isNotBlank()) {
            Text(
                "Enter #RGB, #RRGGBB or #AARRGGBB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
