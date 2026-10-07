package com.lifetracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.ActivityStats
import com.lifetracker.app.data.ActivityTypeEntity

/**
 * Make a new activity ([initial] is null) or change one. For a new one the returned
 * entity has `sortOrder = -1`; the caller puts it at the end of the list.
 * [onToggleArchived] is offered only when editing; it hides the activity from the Log
 * pop-up (or brings it back) without touching anything already logged.
 */
@Composable
fun ActivityEditorDialog(
    initial: ActivityTypeEntity?,
    onSave: (ActivityTypeEntity) -> Unit,
    onDismiss: () -> Unit,
    onToggleArchived: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(initial?.color ?: ACTIVITY_PALETTE.first()) }
    // 0 = no goal, 1 = at least, 2 = at most
    var mode by remember {
        mutableIntStateOf(
            when {
                initial == null || initial.goalMinutes <= 0 -> 0
                initial.goalKind == ActivityTypeEntity.GOAL_AT_MOST -> 2
                else -> 1
            },
        )
    }
    var hours by remember { mutableStateOf(((initial?.goalMinutes ?: 0) / 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var minutes by remember { mutableStateOf(((initial?.goalMinutes ?: 0) % 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New activity" else "Edit activity") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Reading") },
                    singleLine = true,
                )

                MonoLabel("Colour")
                ACTIVITY_PALETTE.chunked(8).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowColors.forEach { c ->
                            val selected = c == color
                            Column(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(c.toInt()))
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                        else Modifier,
                                    )
                                    .clickable { color = c },
                            ) {}
                        }
                    }
                }

                MonoLabel("Daily goal")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("None") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("At least") })
                    FilterChip(selected = mode == 2, onClick = { mode = 2 }, label = { Text("At most") })
                }
                if (mode != 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter(Char::isDigit).take(2); error = null },
                            modifier = Modifier.weight(1f),
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit).take(2); error = null },
                            modifier = Modifier.weight(1f),
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                    Text(
                        text = if (mode == 1) "A bar on the Timeline fills up as you log this, until you reach the goal."
                        else "A limit, such as screen time. The bar turns red if you go over it.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                error?.let { Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

                if (onToggleArchived != null && initial != null) {
                    TextButton(onClick = onToggleArchived) {
                        Text(if (initial.archived) "Show in Log again" else "Hide from Log")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val goal = if (mode == 0) 0 else (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                when {
                    name.isBlank() -> error = "Give it a name."
                    mode != 0 && goal <= 0 -> error = "Set a goal above zero, or choose None."
                    mode != 0 && goal > 24 * 60 -> error = "A day only has 24 hours."
                    else -> onSave(
                        ActivityTypeEntity(
                            id = initial?.id ?: ActivityStats.newId(System.currentTimeMillis()),
                            name = name.trim(),
                            color = color,
                            goalMinutes = goal,
                            goalKind = if (mode == 2) ActivityTypeEntity.GOAL_AT_MOST else ActivityTypeEntity.GOAL_AT_LEAST,
                            archived = initial?.archived ?: false,
                            sortOrder = initial?.sortOrder ?: -1,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
