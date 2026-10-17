package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.UsageComboEntity

/**
 * Names a combination of apps: when these apps are used together, the whole stretch counts as the category you pick.
 * It applies to every day, past and future, because the Timeline works periods out each time a day is drawn.
 * [apps] are the choices (package, name); [existing] is set when you are changing one you already named.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComboDialog(
    existing: UsageComboEntity?,
    apps: List<Pair<String, String>>,
    initialPkgs: Set<String>,
    initialName: String,
    initialActivityId: String,
    note: String?,
    types: List<ActivityTypeEntity>,
    onSave: (UsageComboEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var picked by remember { mutableStateOf(initialPkgs) }
    var activityId by remember { mutableStateOf(initialActivityId) }
    val canSave = name.isNotBlank() && picked.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Name this combination" else "Change this combination") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "When these apps are used together, on any day, the whole stretch counts as the category you pick. Nothing already saved is changed.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (note != null) Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                MonoLabel("Apps (pick at least two)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.forEach { (pkg, label) ->
                        FilterChip(
                            selected = pkg in picked,
                            onClick = { picked = if (pkg in picked) picked - pkg else picked + pkg },
                            label = { Text(label) },
                        )
                    }
                }
                MonoLabel("Counts as")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activityId.isEmpty(),
                        onClick = { activityId = "" },
                        label = { Text("None") },
                    )
                    types.filter { !it.archived || it.id == activityId }.forEach { t ->
                        FilterChip(
                            selected = activityId == t.id,
                            onClick = { activityId = t.id },
                            label = { Text(t.name) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(t.displayColor()))
                            },
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) { Text("Stop counting these together") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        UsageComboEntity(
                            id = existing?.id ?: ("c" + System.currentTimeMillis()),
                            name = name.trim(),
                            pkgs = UsageComboEntity.joinPkgs(picked),
                            activityId = activityId,
                            updatedMs = System.currentTimeMillis(),
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
