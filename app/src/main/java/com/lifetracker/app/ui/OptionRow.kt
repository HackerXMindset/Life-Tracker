package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.Wobble

/** One choice in a list: a title and a line under it, outlined when it is the chosen one. */
@Composable
fun OptionRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) p.surface2 else p.surface, shape)
            .border(if (selected) 2.dp else 1.25.dp, if (selected) p.ink else p.line, shape)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = p.ink)
        if (hint.isNotBlank()) Text(hint, fontSize = 12.sp, color = p.ink2)
    }
}
