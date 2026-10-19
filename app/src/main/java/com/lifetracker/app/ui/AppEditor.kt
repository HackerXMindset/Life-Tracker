package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.UsageAppEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperPill

/**
 * Says which category an app counts as (Study, Work, Screen and leisure...) and which icon it wears. Both are kept for
 * the app, so every time you use it, on every day, lands in that category. Used from the Timeline, the Apps page and the
 * Phone usage screen. Nothing is saved until Save; "Hide this app everywhere" acts at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppCategoryDialog(
    app: UsageAppEntity,
    types: List<ActivityTypeEntity>,
    onSave: (activityId: String, icon: String) -> Unit,
    onIgnore: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPaper.current
    var chosen by remember(app.pkg) { mutableStateOf(app.activityId) }
    var icon by remember(app.pkg) { mutableStateOf(app.icon) }
    val tint = types.firstOrNull { it.id == chosen }?.displayColor() ?: p.ink
    EditWindow(
        title = app.label,
        sub = "The category it counts as, and its icon",
        onClose = onDismiss,
        onSave = {
            if (chosen != app.activityId || icon != app.icon) onSave(chosen, icon)
            onDismiss()
        },
    ) {
        MonoLabel("Counts as")
        Text(
            "Every time you use ${app.label}, on every day, is filed under the category you pick.",
            fontSize = 12.sp,
            color = p.ink2,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperPill("None", chosen.isEmpty(), { chosen = "" })
            types.filter { !it.archived }.forEach { t ->
                PaperPill(t.name, chosen == t.id, { chosen = t.id }, dot = t.displayColor())
            }
        }
        MonoLabel("Icon")
        IconPickerPanel(selected = icon, tint = tint, onSelected = { icon = it })
        PaperButton(
            if (app.ignored) "Show this app again" else "Hide this app everywhere",
            { onIgnore(!app.ignored); onDismiss() },
            style = PaperButtonStyle.Ghost,
            small = true,
        )
    }
}
