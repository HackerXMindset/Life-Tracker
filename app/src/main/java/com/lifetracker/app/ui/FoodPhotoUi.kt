package com.lifetracker.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import com.lifetracker.app.data.AiProvider
import com.lifetracker.app.data.CompareSettings
import com.lifetracker.app.data.FoodAiSettings
import com.lifetracker.app.data.FoodPhotoAi
import com.lifetracker.app.data.PhotoAiException
import com.lifetracker.app.data.PhotoFood
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.Wobble
import java.io.File
import kotlinx.coroutines.launch

/**
 * "Fill from a photo" at the top of the new-food form: take or choose a photo, Google's Gemini reads it, and the form is filled in
 * for you to check. Needs your own free Gemini key (asked for once). Only the photo and your note are sent, nothing else.
 */
@Composable
fun PhotoFill(onFilled: (PhotoFood) -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()

    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var showKey by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val animOn = rememberReadingAnimation()
    val phase = rememberReadPhase(busy, failed)

    fun run(strong: Boolean) {
        val bytes = jpeg ?: return
        busy = true
        failed = false
        message = if (strong) "Asking the stronger model…" else "Reading the photo…"
        scope.launch {
            try {
                val food = FoodPhotoAi.analyze(context, bytes, note, strong)
                onFilled(food)
                message = "Filled in from the photo, for about " + Math.round(food.servingGrams) + (if (food.drink) " ml" else " g") + "." +
                    (if (food.weightGuessed) " It gave no weight, so 100 is assumed." else "") +
                    (if (food.ingredients.isNotEmpty()) " Seen: " + food.ingredients.joinToString(", ") + "." else "") +
                    " It is a guess, so check the numbers."
            } catch (e: PhotoAiException) {
                message = e.message.orEmpty()
                failed = true
            } catch (e: Exception) {
                message = "Something went wrong: " + (e.message ?: e.javaClass.simpleName)
                failed = true
            }
            busy = false
        }
    }

    fun photoReady(uri: Uri?) {
        if (uri == null) return
        busy = true
        failed = false
        message = "Preparing the photo…"
        scope.launch {
            try {
                jpeg = FoodPhotoAi.prepare(context, uri)
            } catch (e: Exception) {
                message = e.message ?: "Could not read the photo."
                failed = true
                busy = false
                return@launch
            }
            run(false)
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photoReady(photoUri) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> photoReady(uri) }

    fun startTake() {
        val dir = File(context.cacheDir, "food-photos").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".photos", File(dir, "photo.jpg"))
        photoUri = uri
        take.launch(uri)
    }

    fun startPick() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    /** Asks for the key and shows the one-time notice first, then does [action]. */
    fun guarded(action: () -> Unit) {
        when {
            FoodAiSettings.key(context).isEmpty() -> { pending = action; showKey = true }
            !FoodAiSettings.noticeSeen(context) -> { pending = action; showNotice = true }
            else -> action()
        }
    }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("Fill from a photo")
            PaperField(note, { note = it }, "What is it? (optional)", placeholder = "e.g. 2 rotis, 1 katori dal", singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Camera", { guarded { startTake() } }, Modifier.weight(1f), enabled = !busy)
                PaperButton("Gallery", { guarded { startPick() } }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, enabled = !busy)
            }
            if (animOn && phase != ReadPhase.Idle) {
                ReadingStage(phase, true, Modifier.fillMaxWidth().height(84.dp)) {
                    Box(Modifier.matchParentSize().background(p.surface2, Wobble.Card))
                }
            }
            if (message.isNotEmpty() && !(animOn && busy)) Text(message, fontSize = 12.5.sp, color = if (failed) p.bad else p.ink2)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (jpeg != null && !busy) {
                    PaperButton("Think harder", { guarded { run(true) } }, style = PaperButtonStyle.Ghost, small = true)
                }
                Text(
                    "AI settings", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = p.ink2,
                    modifier = Modifier.padding(start = 8.dp).clickable(role = Role.Button) { pending = null; showKey = true }.padding(vertical = 8.dp),
                )
            }
        }
    }

    if (showKey) {
        AiKeyDialog(
            onSaved = {
                showKey = false
                val action = pending
                if (action != null && !FoodAiSettings.noticeSeen(context)) showNotice = true
                else { pending = null; action?.invoke() }
            },
            onDismiss = { showKey = false; pending = null },
        )
    }
    if (showNotice) {
        PhotoNoticeDialog(
            onContinue = {
                showNotice = false
                val action = pending
                pending = null
                action?.invoke()
            },
            onDismiss = { showNotice = false; pending = null },
        )
    }
}

/** The one-time notice that a photo goes to the AI provider you chose (or to every provider on the compare list). [onContinue] runs after it is accepted. */
@Composable
fun PhotoNoticeDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val used = CompareSettings.providersUsed(context).toList()
    PaperDialog(
        title = "Your photo or words go to " + used.joinToString(" and ") { it.company },
        onDismiss = onDismiss,
        confirmText = "Continue",
        onConfirm = {
            CompareSettings.markNoticeSeen(context)
            onContinue()
        },
    ) {
        val parts = used.map { provider ->
            if (provider == AiProvider.GEMINI) {
                "Google's Gemini gets the photo (or the words you typed or said) and your note. On Google's free plan they may be used to improve Google's products and be seen by reviewers."
            } else {
                "OpenRouter gets the photo (or the words) and your note and passes them to the model you chose and its maker. Each model has its own privacy rules, and free models may keep what they are sent."
            }
        }
        Text(
            parts.joinToString(" ") + " Do not photograph anything private. Nothing else from Kairos is sent. A barcode is looked up on Open Food Facts instead, which sees only the number.",
            fontSize = 13.sp, color = p.ink2,
        )
    }
}
