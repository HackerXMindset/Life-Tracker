package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FoodLibraryData
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.NutrientEntity
import com.lifetracker.app.data.NutrientWatch
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSheet
import com.lifetracker.app.ui.theme.PaperToggle
import java.time.LocalDate

/** Tells you when a vitamin or mineral you picked has run low over the last week. Switched on in What to show. */
@Composable
fun NutrientWatchCard(nutrients: List<NutrientEntity>, library: FoodLibraryData?) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val tick by FoodSettings.changes.collectAsState()
    val watched = remember(tick) { FoodSettings.watchIds(context) }
    val today = LocalDate.now()
    val meals by remember(today) {
        AppDatabase.get(context).foodDao().observeMealsBetween(today.minusDays(7).toString(), today.minusDays(1).toString())
    }.collectAsState(emptyList())
    val result = remember(meals, nutrients, watched) { NutrientWatch.evaluate(meals, nutrients, watched) }
    var choosing by remember { mutableStateOf(false) }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vitamin watch", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, modifier = Modifier.weight(1f))
            PaperPill(if (watched.isEmpty()) "Choose" else "Change", false, { choosing = true })
        }
        Text("The last 7 days, not counting today.", fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 2.dp))
        when {
            watched.isEmpty() -> Text(
                "Pick the vitamins and minerals you want to keep an eye on, and this card tells you when one runs low.",
                fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
            )
            result.dataDays == 0 -> Text(
                "No food from the last 7 days has vitamin numbers yet.",
                fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
            )
            else -> Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!result.enough) {
                    Text(
                        "${result.dataDays} of the last 7 days have vitamin numbers. It needs ${NutrientWatch.MIN_DAYS} before it says anything is low.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
                result.rows.forEach { row ->
                    val n = row.nutrient
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(n.name, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(
                                amountShort(row.average) + " " + n.unit + (if (n.target > 0) " of " + amountShort(n.target) + " a day" else " a day"),
                                fontSize = 12.sp, color = if (row.low) p.bad else p.ink2,
                            )
                        }
                        row.share?.let { share ->
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
                                val f = share.toFloat().coerceIn(0f, 1f)
                                if (f > 0f) Box(Modifier.fillMaxWidth(f).height(6.dp).background(if (row.low) p.bad else p.accent))
                            }
                        }
                        if (row.low && library != null) {
                            val foods = NutrientWatch.richest(library, n.id).joinToString(", ") { it.name }
                            Text(
                                "Low on " + n.name + "." + (if (foods.isNotEmpty()) " Foods with more: $foods." else ""),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.bad,
                            )
                        }
                    }
                }
            }
        }
        if (library != null && watched.isNotEmpty()) {
            val (with, all) = NutrientWatch.coverage(library)
            Text(
                "$with of $all foods in the library have vitamin numbers. A food without them adds nothing, so a low result can be missing data.",
                fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    if (choosing) {
        PaperSheet(onDismiss = { choosing = false }) {
            SheetBody {
                SheetTitle("Vitamins to watch", "Pick the ones to keep an eye on. Their targets come from your list of vitamins and minerals.")
                nutrients.filter { !it.hidden }.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(n.name, fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
                        PaperToggle(n.id in watched, { FoodSettings.setWatch(context, n.id, it) })
                    }
                }
            }
        }
    }
}
