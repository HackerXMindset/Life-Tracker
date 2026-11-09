package com.lifetracker.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperField

/**
 * The ways to add food that are not a search: a photo of the meal, the barcode of a pack, the nutrition table of a pack,
 * or a few words (typed or spoken). Each ends on the check page.
 */
@Composable
fun AddRoutes(onRoute: (CheckSource) -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    var said by remember { mutableStateOf("") }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) said = heard
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperButton("Take a photo of the meal", { onRoute(CheckSource.Meal) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_p_camera)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton("Scan barcode", { onRoute(CheckSource.Barcode) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
            PaperButton("Read a label", { onRoute(CheckSource.Label) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
        }
        PaperField(said, { said = it }, "Or say what you ate", placeholder = "e.g. 2 rotis, 1 katori dal, curd", singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperButton(
                "Speak",
                {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
                    try {
                        speak.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Voice typing is not available on this phone. Type it instead.", Toast.LENGTH_LONG).show()
                    }
                },
                Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true,
            )
            PaperButton(
                "Read it", { onRoute(CheckSource.Text(said.trim())) }, Modifier.weight(1f),
                small = true, enabled = said.isNotBlank(),
            )
        }
        Text("Gemini reads photos and words, and you check the result before anything is added. Barcodes come from Open Food Facts.", fontSize = 12.sp, color = p.ink2)
    }
}
