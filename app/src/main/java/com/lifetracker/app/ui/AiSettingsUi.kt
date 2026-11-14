package com.lifetracker.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AiProvider
import com.lifetracker.app.data.CompareModel
import com.lifetracker.app.data.CompareSettings
import com.lifetracker.app.data.FoodAiSettings
import com.lifetracker.app.data.FoodPhotoAi
import com.lifetracker.app.data.OpenRouterAi
import com.lifetracker.app.data.PhotoAiException
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import kotlinx.coroutines.launch

/** What is typed on the AI form, for both providers, until it is saved. */
class AiDraft(context: Context) {
    var provider by mutableStateOf(FoodAiSettings.provider(context))
    private var gemKey by mutableStateOf(FoodAiSettings.key(context, AiProvider.GEMINI))
    private var gemModel by mutableStateOf(FoodAiSettings.model(context, AiProvider.GEMINI))
    private var gemStrong by mutableStateOf(FoodAiSettings.strongModel(context, AiProvider.GEMINI))
    private var orKey by mutableStateOf(FoodAiSettings.key(context, AiProvider.OPENROUTER))
    private var orModel by mutableStateOf(FoodAiSettings.model(context, AiProvider.OPENROUTER))
    private var orStrong by mutableStateOf(FoodAiSettings.strongModel(context, AiProvider.OPENROUTER))

    var key: String
        get() = if (provider == AiProvider.GEMINI) gemKey else orKey
        set(v) { if (provider == AiProvider.GEMINI) gemKey = v else orKey = v }
    var model: String
        get() = if (provider == AiProvider.GEMINI) gemModel else orModel
        set(v) { if (provider == AiProvider.GEMINI) gemModel = v else orModel = v }
    var strong: String
        get() = if (provider == AiProvider.GEMINI) gemStrong else orStrong
        set(v) { if (provider == AiProvider.GEMINI) gemStrong = v else orStrong = v }

    var compareOn by mutableStateOf(CompareSettings.on(context))
    val compareModels = mutableStateListOf<CompareModel>().apply { addAll(CompareSettings.models(context)) }

    /** The key typed for [who], whichever provider is on screen. */
    fun keyFor(who: AiProvider): String = if (who == AiProvider.GEMINI) gemKey else orKey

    /** The quick model to use: what is typed, or the usual one when the box is empty. */
    fun quickModel(): String = model.trim().ifEmpty { FoodAiSettings.defaultModel(provider) }
    fun strongModel(): String = strong.trim().ifEmpty { FoodAiSettings.defaultStrong(provider) }

    /** Keeps both providers' keys and models, and makes the one on screen the one in use. */
    fun save(context: Context) {
        FoodAiSettings.save(context, AiProvider.GEMINI, gemKey, gemModel, gemStrong)
        FoodAiSettings.save(context, AiProvider.OPENROUTER, orKey, orModel, orStrong)
        FoodAiSettings.setProvider(context, provider)
        CompareSettings.save(context, compareOn, compareModels.toList())
    }
}

/** The form: who reads the photos, the key, the two model names, and a test. Shared by the AI page and the pop-up that asks for a key. */
@Composable
fun AiForm(draft: AiDraft) {
    val p = LocalPaper.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkText by remember { mutableStateOf("") }
    var checkOk by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val gemini = draft.provider == AiProvider.GEMINI
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaperSegmented(
            options = AiProvider.entries.map { it.label },
            selected = AiProvider.entries.indexOf(draft.provider),
            onSelect = { draft.provider = AiProvider.entries[it]; checkText = "" },
        )
        Text(
            if (gemini) {
                "A free key from Google lets Kairos read food photos and words. Open Google AI Studio, sign in, choose Create API key, and paste it here."
            } else {
                "OpenRouter is one key for hundreds of models from many makers (Google, OpenAI, Anthropic, Meta and more). Make a key at openrouter.ai, " +
                    "then pick any model that can read photos. Models whose name ends in :free cost nothing but are slower and limited."
            },
            fontSize = 13.sp, color = p.ink2,
        )
        PaperButton(
            if (gemini) "Open Google AI Studio" else "Open OpenRouter keys",
            { uriHandler.openUri(if (gemini) "https://aistudio.google.com/apikey" else "https://openrouter.ai/keys") },
            style = PaperButtonStyle.Ghost, small = true,
        )
        PaperField(draft.key, { draft.key = it; checkText = "" }, "Key", singleLine = true)
        PaperField(draft.model, { draft.model = it; checkText = "" }, "Model (quick)", singleLine = true, placeholder = FoodAiSettings.defaultModel(draft.provider))
        if (!gemini) PaperButton("Choose the quick model from the list", { picking = 0 }, style = PaperButtonStyle.Ghost, small = true)
        PaperField(draft.strong, { draft.strong = it; checkText = "" }, "Model (think harder)", singleLine = true, placeholder = FoodAiSettings.defaultStrong(draft.provider))
        if (!gemini) PaperButton("Choose the think-harder model from the list", { picking = 1 }, style = PaperButtonStyle.Ghost, small = true)
        PaperButton(
            if (checking) "Testing…" else "Test the key",
            {
                scope.launch {
                    checking = true
                    val result = FoodPhotoAi.checkKey(
                        draft.provider, draft.key,
                        listOf("Quick" to draft.quickModel(), "Think harder" to draft.strongModel()),
                    )
                    checkText = result.message
                    checkOk = result.ok
                    checking = false
                }
            },
            style = PaperButtonStyle.Ghost, small = true, enabled = !checking && draft.key.isNotBlank(),
        )
        if (checkText.isNotEmpty()) Text(checkText, fontSize = 12.5.sp, color = if (checkOk) p.ink2 else p.bad)
        Text(
            if (gemini) "Google changes the free models from time to time. If a model stops working, type a newer name from the AI Studio list."
            else "\"Quick\" is tried first. \"Think harder\" is for the button of that name, and it is also the second try when the first model is busy. The test sends one short question, not a photo.",
            fontSize = 12.sp, color = p.ink2,
        )
        Text("The keys stay on this phone and are not put in backups.", fontSize = 12.sp, color = p.ink2)
    }
    picking?.let { which ->
        OpenRouterModelsDialog(
            onPick = { id -> if (which == 0) draft.model = id else draft.strong = id; checkText = ""; picking = null },
            onDismiss = { picking = null },
        )
    }
}

/** The models OpenRouter offers, searchable. Tap one to use it. */
@Composable
fun OpenRouterModelsDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    var models by remember { mutableStateOf<List<OpenRouterAi.ModelInfo>?>(null) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var photosOnly by remember { mutableStateOf(true) }
    var freeOnly by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        models = null
        error = ""
        try {
            models = OpenRouterAi.fetchModels()
        } catch (e: PhotoAiException) {
            error = e.message.orEmpty()
        } catch (e: Exception) {
            error = "Could not load the list. Try again."
        }
    }
    PaperDialog(title = "OpenRouter models", onDismiss = onDismiss, dismissText = "Close") {
        PaperField(query, { query = it }, "Search", singleLine = true, placeholder = "e.g. gemini, claude, gpt, llama")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperPill("Reads photos", photosOnly, { photosOnly = !photosOnly })
            PaperPill("Free", freeOnly, { freeOnly = !freeOnly })
        }
        val all = models
        when {
            error.isNotEmpty() -> {
                Text(error, fontSize = 13.sp, color = p.bad)
                PaperButton("Try again", { attempt++ }, style = PaperButtonStyle.Ghost, small = true)
            }
            all == null -> Text("Loading the list…", fontSize = 13.sp, color = p.ink2)
            else -> {
                val shown = remember(all, query, photosOnly, freeOnly) { OpenRouterAi.filter(all, query, photosOnly, freeOnly) }
                Text(shown.size.toString() + " models" + (if (shown.size > 100) ", the first 100 shown. Search to narrow it." else ""), fontSize = 12.sp, color = p.ink2)
                LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown.take(100), key = { it.id }) { m ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(m.id) }.padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                            Text(
                                m.id + (m.priceText().takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "") + (if (m.readsPhotos) "" else " · text only"),
                                fontSize = 12.sp, color = p.ink2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The pop-up that asks for a key the first time a photo or words are read. [onSaved] runs once a key is saved. */
@Composable
fun AiKeyDialog(onSaved: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val draft = remember { AiDraft(context) }
    PaperDialog(
        title = "Set up the AI reader",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            if (draft.key.isNotBlank()) {
                draft.save(context)
                onSaved()
            }
        },
    ) {
        Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { AiForm(draft) }
    }
}

/** Settings → AI: who reads food photos and words, with which key and models. */
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    BackHandler(onBack = onBack)
    val draft = remember { AiDraft(context) }
    var saved by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("AI", "Who reads your food photos and words", onBack) }
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiForm(draft)
                }
            }
        }
        item { CompareSettingsCard(draft) }
        item {
            Text(
                "Only used when you press Camera, Gallery, Label or type a meal for the AI to read. What is sent is the photo or the words and your note, plus your own portion sizes and a few of your past corrections. " +
                    "Nothing else leaves the phone. Barcodes go to Open Food Facts, which sees only the number.",
                fontSize = 12.sp, color = p.ink2,
            )
        }
        item {
            PaperButton(
                if (saved) "Saved" else "Save",
                { draft.save(context); saved = true },
                Modifier.fillMaxWidth(), enabled = !saved || draft.key.isNotBlank(),
            )
        }
    }
}

/** Lines that say a number looks wrong. Nothing is blocked; they only ask you to look again. */
@Composable
fun SanityNotes(notes: List<String>) {
    if (notes.isEmpty()) return
    val p = LocalPaper.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notes.forEach { Text("Look again: $it", fontSize = 12.5.sp, color = p.bad) }
    }
}
