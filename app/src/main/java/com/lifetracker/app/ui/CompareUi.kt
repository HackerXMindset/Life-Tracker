package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.AiProvider
import com.lifetracker.app.data.CompareModel
import com.lifetracker.app.data.CompareModels
import com.lifetracker.app.data.CompareResult
import com.lifetracker.app.data.FoodAiSettings
import com.lifetracker.app.data.OpenRouterAi
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.PaperToggle
import java.util.Locale

/** One card for each model that was asked: what it saw and the main numbers, or why it could not answer. Tap "Use this one" to carry on with it. */
@Composable
fun CompareCards(results: List<CompareResult>, onPick: (CompareResult) -> Unit) {
    val p = LocalPaper.current
    Text("What each model said", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.ink)
    val spread = CompareModels.spreadText(results)
    Text(
        (if (spread.isNotEmpty()) "$spread " else "") + "Choose the one that looks right. You can still change any number on the next page.",
        fontSize = 12.5.sp, color = p.ink2,
    )
    results.forEach { r ->
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.entry.model, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                Text(
                    r.entry.provider.label + " · " + String.format(Locale.US, "%.1f", r.millis / 1000.0) + " s",
                    fontSize = 11.5.sp, color = p.ink2,
                )
                val food = r.food
                if (food == null) {
                    Text(r.error.orEmpty(), fontSize = 13.sp, color = p.bad)
                } else {
                    Text(food.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    Text(
                        "about " + Math.round(food.servingGrams) + (if (food.drink) " ml" else " g") + (if (food.weightGuessed) " (no weight given, 100 assumed)" else ""),
                        fontSize = 12.sp, color = p.ink2,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        CompareStat("kcal", Math.round(food.kcal).toString(), Modifier.weight(1f))
                        CompareStat("protein g", Math.round(food.protein).toString(), Modifier.weight(1f))
                        CompareStat("carbs g", Math.round(food.carbs).toString(), Modifier.weight(1f))
                        CompareStat("fat g", Math.round(food.fat).toString(), Modifier.weight(1f))
                    }
                    if (food.ingredients.isNotEmpty()) Text("Seen: " + food.ingredients.joinToString(", "), fontSize = 12.sp, color = p.ink2, maxLines = 3)
                    if (food.cookingFatG > 0) Text("Oil, ghee or butter counted: " + Math.round(food.cookingFatG) + " g", fontSize = 12.sp, color = p.ink2)
                    PaperButton("Use this one", { onPick(r) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CompareStat(label: String, value: String, modifier: Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        Text(label, fontSize = 11.sp, color = p.ink2)
    }
}

/** Settings → AI: the switch for comparing and the models to ask. Off by default; when it is off nothing about reading changes. */
@Composable
fun CompareSettingsCard(draft: AiDraft) {
    val p = LocalPaper.current
    var adding by remember { mutableStateOf(false) }
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Compare models", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("Ask several models at once and choose which answer to use. Models from different providers can be mixed.", fontSize = 12.5.sp, color = p.ink2)
                }
                PaperToggle(draft.compareOn, { draft.compareOn = it })
            }
            if (draft.compareOn) {
                draft.compareModels.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(m.model, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink, maxLines = 1)
                            val noKey = draft.keyFor(m.provider).isBlank()
                            Text(
                                m.provider.label + if (noKey) " · no key yet, add it above" else "",
                                fontSize = 12.sp, color = if (noKey) p.bad else p.ink2,
                            )
                        }
                        PaperIconButton(R.drawable.ic_p_trash, "Remove", { draft.compareModels.removeAt(i) })
                    }
                }
                if (draft.compareModels.size < CompareModels.MAX) {
                    PaperButton("Add a model", { adding = true }, style = PaperButtonStyle.Ghost, small = true, icon = R.drawable.ic_p_plus)
                }
                val count = draft.compareModels.size
                Text(
                    when {
                        count < CompareModels.MIN -> "Add at least ${CompareModels.MIN} models. Until then reading works as usual with your one model."
                        else -> "Every photo, label or typed meal now makes ${CompareModels.callsText(count)}, one to each model, and you choose which answer to use. " +
                            "On paid models each call costs money; free ones are slower and limited."
                    },
                    fontSize = 12.sp, color = p.ink2,
                )
            } else {
                Text("Off: each photo or meal is read by your one model, as before.", fontSize = 12.sp, color = p.ink2)
            }
        }
    }
    if (adding) {
        AddCompareModelDialog(
            existing = draft.compareModels,
            onAdd = { draft.compareModels.add(it); adding = false },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun AddCompareModelDialog(existing: List<CompareModel>, onAdd: (CompareModel) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var provider by remember { mutableStateOf(AiProvider.OPENROUTER) }
    var model by remember { mutableStateOf("") }
    var browsing by remember { mutableStateOf(false) }
    val candidate = CompareModels.clean(provider, model)
    val duplicate = candidate != null && existing.any { it.provider == candidate.provider && it.model.equals(candidate.model, ignoreCase = true) }
    val looksWrong = provider == AiProvider.OPENROUTER && candidate != null && !OpenRouterAi.looksLikeModelId(candidate.model)
    PaperDialog(
        title = "Add a model to compare",
        onDismiss = onDismiss,
        confirmText = "Add",
        onConfirm = { candidate?.let(onAdd) },
        confirmEnabled = candidate != null && !duplicate && !looksWrong,
    ) {
        PaperSegmented(AiProvider.entries.map { it.label }, AiProvider.entries.indexOf(provider), { provider = AiProvider.entries[it]; model = "" })
        PaperField(
            model, { model = it }, "Model name", singleLine = true,
            placeholder = if (provider == AiProvider.OPENROUTER) "e.g. openai/gpt-4o-mini" else FoodAiSettings.DEFAULT_MODEL,
        )
        if (provider == AiProvider.OPENROUTER) {
            PaperButton("Choose from the list", { browsing = true }, style = PaperButtonStyle.Ghost, small = true)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperPill(FoodAiSettings.DEFAULT_MODEL, model == FoodAiSettings.DEFAULT_MODEL, { model = FoodAiSettings.DEFAULT_MODEL })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperPill(FoodAiSettings.DEFAULT_STRONG_MODEL, model == FoodAiSettings.DEFAULT_STRONG_MODEL, { model = FoodAiSettings.DEFAULT_STRONG_MODEL })
            }
        }
        when {
            duplicate -> Text("That model is already on the list.", fontSize = 12.5.sp, color = p.bad)
            looksWrong -> Text("OpenRouter names look like maker/model, for example google/gemini-2.5-flash.", fontSize = 12.5.sp, color = p.bad)
        }
    }
    if (browsing) OpenRouterModelsDialog(onPick = { model = it; browsing = false }, onDismiss = { browsing = false })
}
