package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.Wobble

/** Settings screen for the kinds of activity you log: names and colours. */
@Composable
fun ActivitiesScreen(onBack: () -> Unit, vm: ActivitiesViewModel = viewModel()) {
    val p = LocalPaper.current
    val types by vm.types.collectAsState()
    BackHandler(onBack = onBack)

    var editing by remember { mutableStateOf<ActivityTypeEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val active = types.filter { !it.archived }
    val hidden = types.filter { it.archived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsTop("Activities", "Your own kinds of activity", onBack) }
        item {
            Text(
                text = "These are the choices in the Log pop-up. Change a name or colour, add your own, or hide one you never use. Nothing you logged is ever removed.",
                fontSize = 12.5.sp,
                color = p.ink2,
            )
        }

        items(active, key = { it.id }) { type -> ActivityRow(type, hiddenRow = false, onClick = { editing = type }) }

        item {
            PaperButtonBox(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                PaperIcon(R.drawable.ic_p_plus, size = 18.dp, tint = LocalContentColor.current)
                Text("New activity")
            }
        }

        if (hidden.isNotEmpty()) {
            item { PaperSection("Hidden from Log") }
            items(hidden, key = { "h" + it.id }) { type -> ActivityRow(type, hiddenRow = true, onClick = { editing = type }) }
        }
    }

    if (creating) {
        ActivityEditorDialog(
            initial = null,
            onSave = { vm.save(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    editing?.let { type ->
        ActivityEditorDialog(
            initial = type,
            onSave = { vm.save(it); editing = null },
            onDismiss = { editing = null },
            onToggleArchived = {},
        )
    }
}

/** One row of the list: a rounded colour square, the name, and a pencil (or a crossed-out eye when hidden). */
@Composable
private fun ActivityRow(type: ActivityTypeEntity, hiddenRow: Boolean, onClick: () -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (hiddenRow) 0.7f else 1f)
            .background(p.surface, shape)
            .border(1.25.dp, p.line, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(type.displayColor()))
        Text(
            text = type.name,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            maxLines = 1,
        )
        PaperIcon(if (hiddenRow) R.drawable.ic_p_eyeoff else R.drawable.ic_p_edit, size = 18.dp, tint = p.ink2)
    }
}
