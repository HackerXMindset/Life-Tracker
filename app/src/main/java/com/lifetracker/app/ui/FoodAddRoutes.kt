package com.lifetracker.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle

/**
 * The ways to add food that are not a search, always on screen under the search box: a photo of the meal, the barcode of a
 * pack, the nutrition table of a pack. A few words (typed or spoken) go through the search box. Each ends on the check page.
 */
@Composable
fun AddRouteRow(onRoute: (CheckSource) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperButton("Photo", { onRoute(CheckSource.Meal) }, Modifier.weight(1f), small = true, icon = R.drawable.ic_p_camera)
        PaperButton("Barcode", { onRoute(CheckSource.Barcode) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
        PaperButton("Label", { onRoute(CheckSource.Label) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
    }
}

/** Opens the phone's voice typing and hands back what it heard; call the returned function to start it. */
@Composable
fun rememberSpeak(onHeard: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) onHeard(heard)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
        try {
            speak.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice typing is not available on this phone. Type it instead.", Toast.LENGTH_LONG).show()
        }
    }
}
