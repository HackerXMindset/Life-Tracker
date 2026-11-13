package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.MyPortion
import com.lifetracker.app.data.MyPortions
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill

/** One line being edited: the weight is kept as typed until it is saved. */
private class PortionDraft(name: String, grams: String) {
    var name by mutableStateOf(name)
    var grams by mutableStateOf(grams)
}

/** Your own weights for a roti, a katori, a spoon. They are told to the photo and word reader, so "2 roti" means your roti. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    BackHandler(onBack = onBack)
    val rows = remember {
        mutableStateListOf<PortionDraft>().also { l ->
            MyPortions.load(context).forEach { l.add(PortionDraft(it.name, it.grams.let { g -> if (g == Math.rint(g)) g.toLong().toString() else g.toString() })) }
        }
    }
    var saved by remember { mutableStateOf(false) }

    fun touch() { saved = false }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("My portions", "What a roti, a katori or a spoon weighs in your kitchen", onBack) }
        item {
            Text(
                "Weigh one on a kitchen scale and write it here. When you take a photo or type \"2 roti, 1 katori dal\", the reader uses your weights instead of the usual ones, so the totals match what you really ate.",
                fontSize = 13.sp, color = p.ink2,
            )
        }
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (rows.isEmpty()) Text("Nothing yet. Pick one below, or add your own.", fontSize = 13.sp, color = p.ink2)
                    rows.forEachIndexed { i, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            PaperField(row.name, { row.name = it; touch() }, "One of", Modifier.weight(1f), placeholder = "e.g. roti", singleLine = true)
                            PaperField(row.grams, { row.grams = it; touch() }, "Grams", Modifier.weight(0.6f), singleLine = true, keyboardOptions = DecimalKeys)
                            PaperIconButton(R.drawable.ic_p_trash, "Remove", { rows.removeAt(i); touch() })
                        }
                    }
                    PaperButton("Add a line", { rows.add(PortionDraft("", "")); touch() }, style = PaperButtonStyle.Ghost, small = true, icon = R.drawable.ic_p_plus, enabled = rows.size < MyPortions.MAX)
                }
            }
        }
        val missing = MyPortions.SUGGESTED.filter { s -> rows.none { it.name.trim().equals(s.name, ignoreCase = true) } }
        if (missing.isNotEmpty()) {
            item {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start from", fontSize = 12.sp, color = p.ink2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        missing.forEach { s ->
                            PaperPill(s.name + " · " + s.grams.toLong() + " g", false, { rows.add(PortionDraft(s.name, s.grams.toLong().toString())); touch() })
                        }
                    }
                }
            }
        }
        item {
            val parsed = rows.map { MyPortions.clean(it.name, it.grams.trim().replace(',', '.').toDoubleOrNull()) }
            val half = rows.indices.filter { parsed[it] == null && (rows[it].name.isNotBlank() || rows[it].grams.isNotBlank()) }
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (half.isNotEmpty()) Text("A line needs a name and a weight between 0 and 5000 g. Lines that do not are left out.", fontSize = 12.5.sp, color = p.bad)
                PaperButton(
                    if (saved) "Saved" else "Save",
                    {
                        MyPortions.save(context, parsed.filterNotNull())
                        saved = true
                    },
                    Modifier.fillMaxWidth(), enabled = !saved,
                )
            }
        }
    }
}
