package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.ActivityStats
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperSheet

/**
 * Make a new activity ([initial] is null) or change one, in a bottom sheet. For a new one the returned
 * entity has `sortOrder = -1`; the caller puts it at the end of the list.
 * When [onToggleArchived] is given and an activity is being edited, a "Hide from Log" switch is shown;
 * it is saved with Save. Hiding only removes it from the Log pop-up, nothing already logged is touched.
 */
@Composable
fun ActivityEditorDialog(
    initial: ActivityTypeEntity?,
    onSave: (ActivityTypeEntity) -> Unit,
    onDismiss: () -> Unit,
    onToggleArchived: (() -> Unit)? = null,
) {
    val p = LocalPaper.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(initial?.color ?: ACTIVITY_PALETTE.first()) }
    var hide by remember { mutableStateOf(initial?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    PaperSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (initial == null) "New activity" else "Edit activity",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
            )
            PaperField(
                value = name,
                onValueChange = { name = it; error = null },
                label = "Name",
                modifier = Modifier.fillMaxWidth(),
                placeholder = "e.g. Reading",
                singleLine = true,
            )

            MonoLabel("Colour")
            ACTIVITY_PALETTE.chunked(8).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowColors.forEach { c ->
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c.toInt()))
                                .then(if (selected) Modifier.border(3.dp, p.ink, CircleShape) else Modifier)
                                .clickable { color = c },
                        )
                    }
                }
            }

            if (onToggleArchived != null && initial != null) {
                ToggleRow("Hide from Log", hide, { hide = it }, hint = "Old entries keep their colour", first = true)
            }

            error?.let { Text(text = it, color = p.bad, fontSize = 13.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PaperButtonBox(onClick = onDismiss, modifier = Modifier.weight(1f), style = PaperButtonStyle.Ghost) { Text("Cancel") }
                PaperButtonBox(
                    onClick = {
                        if (name.isBlank()) {
                            error = "Give it a name."
                        } else {
                            onSave(
                                ActivityTypeEntity(
                                    id = initial?.id ?: ActivityStats.newId(System.currentTimeMillis()),
                                    name = name.trim(),
                                    color = color,
                                    // Goals are no longer shown anywhere, but whatever was stored is kept as it was.
                                    goalMinutes = initial?.goalMinutes ?: 0,
                                    goalKind = initial?.goalKind ?: ActivityTypeEntity.GOAL_AT_LEAST,
                                    archived = if (onToggleArchived != null) hide else initial?.archived ?: false,
                                    sortOrder = initial?.sortOrder ?: -1,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}
