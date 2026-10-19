package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.lifetracker.app.ui.theme.LocalPaper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.UsageAppEntity
import com.lifetracker.app.ui.theme.PaperPill

/**
 * Every app the phone has seen, with the category it counts as. Tap a row to change the category or hide the app. A category
 * on an app applies to every day, past and future. Hidden apps are listed below so they can be brought back.
 */
@Composable
fun AppsScreen(onBack: () -> Unit, vm: PhoneUsageViewModel = viewModel()) {
    val apps by vm.apps.collectAsState()
    val types by vm.activityTypes.collectAsState()
    var editing by remember { mutableStateOf<UsageAppEntity?>(null) }
    BackHandler(onBack = onBack)

    val shown = apps.filter { !it.ignored }.sortedBy { it.label.lowercase() }
    val hidden = apps.filter { it.ignored }.sortedBy { it.label.lowercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SettingsTop("Apps and categories", "Which category each app counts as", onBack) }

        if (apps.isEmpty()) {
            item {
                Text(
                    "No apps yet. Once usage access is allowed in Phone and usage, every app you use appears here.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (shown.isNotEmpty()) item { PaperSection("Apps", "${shown.size} seen") }
        items(shown, key = { "a" + it.pkg }) { app ->
            val type = app.activityId.takeIf { it.isNotEmpty() }?.let { types.byId(it) }
            SettingsRow(
                icon = null,
                title = app.label,
                hint = if (type != null) "Every use counts as ${type.name}" else "No category yet",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val glyph = app.icon.ifEmpty { type?.icon.orEmpty() }
                        if (glyph.isNotEmpty()) IconGlyph(icon = glyph, tint = type?.displayColor() ?: LocalPaper.current.ink2, size = 20.dp)
                        PaperPill(type?.name ?: "Set", type != null, { editing = app })
                    }
                },
                onClick = { editing = app },
            )
        }
        if (hidden.isNotEmpty()) {
            item { PaperSection("Hidden apps", "left out of the Timeline and totals") }
            items(hidden, key = { "h" + it.pkg }) { app ->
                SettingsRow(R.drawable.ic_p_eyeoff, app.label, "Hidden everywhere · tap to bring back", onClick = { editing = app })
            }
        }
        item {
            Text(
                "Apps used together can also be named as a combination (for example Physics Wallah and ChatGPT as Study). That is under Phone and usage.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { app ->
        AppCategoryDialog(
            app = app,
            types = types,
            onSave = { activityId, icon -> vm.saveApp(app, activityId, icon) },
            onIgnore = { vm.setIgnored(app, it) },
            onDismiss = { editing = null },
        )
    }
}
