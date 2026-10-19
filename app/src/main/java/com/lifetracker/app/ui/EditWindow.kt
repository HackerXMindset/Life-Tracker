package com.lifetracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperBackground
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.Wobble

/**
 * A full-screen page for changing one thing, like the page for editing a habit in Streak: a Back button and **Save** along
 * the top, then the title and one scrolling body. It covers the whole screen (it is not a small pop-up), and the phone's
 * back button closes it. [onSave] null leaves out the Save button. Closing after a save is up to the caller, so a form that
 * finds a mistake can stay open.
 */
@Composable
fun EditWindow(
    title: String,
    sub: String,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveText: String = "Save",
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPaper.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PaperBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(Wobble.Small)
                            .clickable(role = Role.Button, onClick = onClose)
                            .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        PaperIcon(R.drawable.ic_p_back, size = 18.dp, tint = p.ink2)
                        Text("Back", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.ink2)
                    }
                    if (onSave != null) PaperButton(saveText, onSave, small = true)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = title, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 3)
                        if (sub.isNotBlank()) Text(text = sub, fontSize = 12.sp, color = p.ink2)
                    }
                    content()
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}
