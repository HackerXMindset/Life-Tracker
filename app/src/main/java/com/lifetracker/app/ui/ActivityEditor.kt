package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.lifetracker.app.ui.theme.PaperField

/**
 * Make a new activity ([initial] is null) or change one, in a full-screen window. For a new one the returned
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
    var icon by remember { mutableStateOf(initial?.icon.orEmpty()) }
    var hide by remember { mutableStateOf(initial?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (name.isBlank()) {
            error = "Give it a name."
            return
        }
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
                icon = icon,
            ),
        )
    }

    EditWindow(
        title = if (initial == null) "New activity" else name.ifBlank { "Edit activity" },
        sub = if (initial == null) "A thing you do, with its own colour and icon" else "Colour, icon and name",
        onClose = onDismiss,
        onSave = { save() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(color.toInt()).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                IconGlyph(icon = icon, tint = Color(color.toInt()), size = 26.dp, fallback = name.ifBlank { "?" })
            }
            Text(name.ifBlank { "Name" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
        }
        PaperField(
            value = name,
            onValueChange = { name = it; error = null },
            label = "Name",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "e.g. Reading",
            singleLine = true,
        )

        MonoLabel("Colour")
        ColorPickerPanel(selected = color, onSelected = { color = it })

        MonoLabel("Icon")
        IconPickerPanel(selected = icon, tint = Color(color.toInt()), onSelected = { icon = it })

        if (onToggleArchived != null && initial != null) {
            ToggleRow("Hide from Log", hide, { hide = it }, hint = "Old entries keep their colour", first = true)
        }

        error?.let { Text(text = it, color = p.bad, fontSize = 13.sp) }
    }
}
