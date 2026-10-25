package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetracker.app.ui.theme.AppTheme
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.ThemeMode
import com.lifetracker.app.data.AppBranding

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var savedDesignRevision by remember { mutableIntStateOf(0) }
    var activeIdentityRevision by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsTop(
                title = "Appearance",
                sub = "Theme, app name and home-screen icon",
                onBack = onBack,
            )
        }
        item { PaperSection("Theme", "how Life Tracker looks") }
        item {
            PaperSegmented(
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.entries.indexOf(AppTheme.mode),
                onSelect = { AppTheme.set(context, ThemeMode.entries[it]) },
            )
        }
        item { BackgroundCustomizer() }

        item {
            key(activeIdentityRevision) {
                AppIdentitySettings(onDesignSaved = { savedDesignRevision++ })
            }
        }
        item {
            SavedAppDesignsSettings(
                revision = savedDesignRevision,
                onDesignsChanged = { savedDesignRevision++ },
                onDesignApplied = { activeIdentityRevision++ },
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedAppDesignsSettings(
    revision: Int,
    onDesignsChanged: () -> Unit,
    onDesignApplied: () -> Unit,
) {
    val context = LocalContext.current
    val designs = remember(revision) { AppBranding.savedDesigns(context) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<AppBranding.SavedDesign?>(null) }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Text(
            "Saved app designs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Keep different app names and icons here, then switch between them whenever you like.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (designs.isEmpty()) {
            Text(
                "No saved designs yet. Use “Save current design” above to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                designs.forEach { design ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = remember(revision, design.id) {
                            AppBranding.savedDesignIcon(context, design.id)?.asImageBitmap()
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = "${design.name} app icon",
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                design.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        runCatching {
                                            AppBranding.applySavedDesign(context, design.id)
                                        }.onSuccess { active ->
                                            message = when (AppBranding.createOrUpdateShortcut(context, active.name)) {
                                                AppBranding.ShortcutResult.PinRequested ->
                                                    "\"${active.name}\" selected. Approve the launcher request to add its shortcut."
                                                AppBranding.ShortcutResult.Updated ->
                                                    "\"${active.name}\" applied and the home-screen shortcut was updated."
                                                AppBranding.ShortcutResult.Unsupported ->
                                                    "\"${active.name}\" applied here, but this launcher cannot pin shortcuts."
                                                AppBranding.ShortcutResult.UpdateFailed ->
                                                    "\"${active.name}\" applied here, but the launcher could not update the shortcut."
                                            }
                                            onDesignApplied()
                                        }.onFailure { error ->
                                            message = error.message ?: "This design could not be applied."
                                        }
                                    },
                                ) { Text("Apply") }
                                OutlinedButton(
                                    onClick = {
                                        message = when (AppBranding.createOrUpdateSavedDesignShortcut(context, design.id)) {
                                            AppBranding.ShortcutResult.PinRequested ->
                                                "\" ${design.name}\" shortcut requested. Approve the launcher prompt if it appears."
                                            AppBranding.ShortcutResult.Updated ->
                                                "\" ${design.name}\" home-screen shortcut updated."
                                            AppBranding.ShortcutResult.Unsupported ->
                                                "This launcher does not support pinned shortcuts."
                                            AppBranding.ShortcutResult.UpdateFailed ->
                                                "Could not create or update this design's shortcut. Try again."
                                        }
                                    },
                                ) { Text("Add shortcut") }
                                TextButton(onClick = { deleteTarget = design }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    deleteTarget?.let { design ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete saved design?") },
            text = { Text("\"${design.name}\" will be removed from this list. Your active identity will not change; a shortcut already added may remain on your home screen.") },
            confirmButton = {
                TextButton(onClick = {
                    val removed = AppBranding.deleteSavedDesign(context, design.id)
                    deleteTarget = null
                    message = if (removed) "Saved design deleted." else "That saved design was already removed."
                    onDesignsChanged()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}
