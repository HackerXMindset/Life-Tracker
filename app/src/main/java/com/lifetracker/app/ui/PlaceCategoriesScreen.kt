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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.PlaceCategoryEntity
import com.lifetracker.app.data.PlaceLooks
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.Wobble

/** Settings screen for the categories of place (Home, Study, Gym...): the colour of the place line on the Timeline and an icon each. */
@Composable
fun PlaceCategoriesScreen(onBack: () -> Unit, vm: PlacesViewModel = viewModel()) {
    val p = LocalPaper.current
    val categories by vm.categories.collectAsState()
    BackHandler(onBack = onBack)

    var editing by remember { mutableStateOf<PlaceCategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val active = categories.filter { !it.archived }
    val hidden = categories.filter { it.archived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsTop("Place categories", "The colour and icon of each kind of place", onBack) }
        item {
            Text(
                text = "Each place you named has one category. Its line on the Timeline takes the category's colour (a category you pick for one particular stay still wins). Add your own, or hide one you never use. Nothing is ever removed.",
                fontSize = 12.5.sp,
                color = p.ink2,
            )
        }
        items(active, key = { it.id }) { c -> CategoryRow(c, hiddenRow = false, onClick = { editing = c }) }
        item {
            PaperButtonBox(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                PaperIcon(R.drawable.ic_p_plus, size = 18.dp, tint = LocalContentColor.current)
                Text("New place category")
            }
        }
        if (hidden.isNotEmpty()) {
            item { PaperSection("Hidden") }
            items(hidden, key = { "h" + it.id }) { c -> CategoryRow(c, hiddenRow = true, onClick = { editing = c }) }
        }
    }

    if (creating) {
        PlaceCategoryEditor(initial = null, onSave = { vm.saveCategory(it); creating = false }, onDismiss = { creating = false })
    }
    editing?.let { c ->
        PlaceCategoryEditor(initial = c, onSave = { vm.saveCategory(it); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
private fun CategoryRow(c: PlaceCategoryEntity, hiddenRow: Boolean, onClick: () -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    val color = argbColor(c.color)
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
        Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
            if (c.icon.isNotEmpty()) IconGlyph(icon = c.icon, tint = Color.White, size = 20.dp)
        }
        Text(c.name, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
        PaperIcon(if (hiddenRow) R.drawable.ic_p_eyeoff else R.drawable.ic_p_edit, size = 18.dp, tint = p.ink2)
    }
}

/** Make a place category ([initial] is null) or change one: name, colour, icon, and whether to hide it. */
@Composable
fun PlaceCategoryEditor(initial: PlaceCategoryEntity?, onSave: (PlaceCategoryEntity) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(initial?.color ?: STREAK_COLORS[26]) }
    var icon by remember { mutableStateOf(initial?.icon ?: "mapPin") }
    var hide by remember { mutableStateOf(initial?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    EditWindow(
        title = if (initial == null) "New place category" else name.ifBlank { "Place category" },
        sub = "Colour and icon for places of this kind",
        onClose = onDismiss,
        onSave = {
            if (name.isBlank()) {
                error = "Give it a name."
            } else {
                onSave(
                    PlaceCategoryEntity(
                        id = initial?.id ?: PlaceLooks.newId(System.currentTimeMillis()),
                        name = name.trim(),
                        color = color,
                        icon = icon,
                        sortOrder = initial?.sortOrder ?: -1,
                        archived = hide,
                    ),
                )
            }
        },
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
            placeholder = "e.g. Friend's house",
            singleLine = true,
        )
        MonoLabel("Colour")
        ColorPickerPanel(selected = color, onSelected = { color = it })
        MonoLabel("Icon")
        IconPickerPanel(selected = icon, tint = Color(color.toInt()), onSelected = { icon = it })
        if (initial != null) ToggleRow("Hide from the list", hide, { hide = it }, hint = "Places already using it keep its colour", first = true)
        error?.let { Text(text = it, color = p.bad, fontSize = 13.sp) }
    }
}
