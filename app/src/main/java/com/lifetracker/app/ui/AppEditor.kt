package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.UsageAppEntity

/**
 * Says which category an app counts as (Study, Work, Screen and leisure...). The choice is kept for the app, so every
 * time you use it, on every day, lands in that category. Used from the Timeline and from the Phone usage screen.
 */
@Composable
fun AppCategoryDialog(
    app: UsageAppEntity,
    types: List<ActivityTypeEntity>,
    onLink: (activityId: String) -> Unit,
    onIgnore: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoLabel("Counts as")
                Text(
                    "Every time you use ${app.label}, on every day, is filed under the category you pick.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = app.activityId.isEmpty(),
                        onClick = { onLink(""); onDismiss() },
                        label = { Text("None") },
                    )
                    types.filter { !it.archived }.forEach { t ->
                        FilterChip(
                            selected = app.activityId == t.id,
                            onClick = { onLink(t.id); onDismiss() },
                            label = { Text(t.name) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(t.displayColor()))
                            },
                        )
                    }
                }
                TextButton(onClick = { onIgnore(!app.ignored); onDismiss() }) {
                    Text(if (app.ignored) "Show this app again" else "Hide this app everywhere")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
