package com.lifetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetracker.app.data.AppBranding
import com.lifetracker.app.data.AppBranding.ShortcutResult
import com.lifetracker.app.ui.theme.PaperCard

/** Name and SVG selection for the custom launcher shortcut (Android keeps the original app-drawer identity). */
@Composable
fun AppIdentitySettings() {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(AppBranding.savedName(context)) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var iconRevision by remember { mutableIntStateOf(0) }

    val pickSvg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val contents = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("The selected file could not be opened.")
            }
            if (contents.isFailure) {
                message = "Could not read that file. Please choose an SVG image."
            } else {
                val error = AppBranding.saveCustomSvg(context, contents.getOrThrow())
                if (error == null) {
                    iconRevision++
                    message = "Custom SVG saved privately on this phone."
                } else {
                    message = error
                }
            }
        }
    }

    val preview = remember(iconRevision) {
        AppBranding.createIconBitmap(context, 144).asImageBitmap()
    }
    val hasCustomIcon = remember(iconRevision) { AppBranding.hasCustomIcon(context) }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text(
            text = "Custom home-screen identity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Choose a name and an SVG logo for a separate home-screen shortcut. Android keeps the original app-drawer name and icon unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = preview,
                contentDescription = "Preview of the home-screen shortcut icon",
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (hasCustomIcon) "Custom SVG selected" else "Using the current app icon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "SVG files up to 1 MB are supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            OutlinedButton(onClick = { pickSvg.launch(arrayOf("image/svg+xml")) }) {
                Text("Choose SVG")
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
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
