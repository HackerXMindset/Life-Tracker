package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.data.ActivityTypeEntity

/** Settings screen for the kinds of activity you log: names, colours and daily goals. */
@Composable
fun ActivitiesScreen(onBack: () -> Unit, vm: ActivitiesViewModel = viewModel()) {
    val types by vm.types.collectAsState()
    BackHandler(onBack = onBack)

    var editing by remember { mutableStateOf<ActivityTypeEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val active = types.filter { !it.archived }
    val hidden = types.filter { it.archived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("Back") } }
        item { ScreenHeader(label = "Settings", badge = "On this phone", title = "Activities") }
        item {
            Text(
                text = "These are the choices in the Log pop-up. Give any activity a daily goal and it gets a bar on the Timeline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Button(onClick = { creating = true }) { Text("New activity") } }

        items(active, key = { it.id }) { type -> ActivityCard(type, onClick = { editing = type }) }

        if (hidden.isNotEmpty()) {
            item { MonoLabel("Hidden from Log") }
            items(hidden, key = { "h" + it.id }) { type -> ActivityCard(type, onClick = { editing = type }) }
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
            onToggleArchived = { vm.setArchived(type, !type.archived); editing = null },
        )
    }
}

@Composable
private fun ActivityCard(type: ActivityTypeEntity, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(type.displayColor()))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = type.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = goalText(type),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
