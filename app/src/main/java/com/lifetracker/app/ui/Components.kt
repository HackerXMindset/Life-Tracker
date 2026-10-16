package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.ui.theme.Wobble

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = LocalPaper.current.ink2,
    )
}

/**
 * The top of a page: a big title with a quiet line under it (where you are, and that the data stays on the phone).
 * [trailing] is for a small control at the right of the title.
 */
@Composable
fun ScreenHeader(
    label: String,
    badge: String,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val paper = LocalPaper.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                color = paper.ink,
                maxLines = 2,
            )
            trailing?.invoke()
        }
        Text(
            text = if (label.equals(title, ignoreCase = true)) badge else "$label  ·  $badge",
            fontSize = 12.sp,
            color = paper.ink2,
        )
    }
}

/**
 * Each detail screen has two halves: the numbers (opened from the Stats tab) and the permissions and switches
 * (opened from the Settings tab). Nothing is lost by splitting them; every control still exists.
 */
enum class Part { Data, Settings }

/** A small worn-paper note telling you a source still needs switching on in Settings. */
@Composable
fun SetupNote(text: String) {
    val paper = LocalPaper.current
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(paper.surface2, Wobble.Small)
            .border(1.25.dp, paper.line, Wobble.Small)
            .padding(14.dp),
        fontSize = 13.sp,
        color = paper.ink2,
    )
}

/** A label with a switch. The label wraps onto more lines instead of pushing the switch off the screen. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LocalPaper.current.ink)
        PaperToggle(checked = checked, onChange = onChange)
    }
}
