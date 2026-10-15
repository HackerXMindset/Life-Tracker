package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.Wobble
import com.lifetracker.app.ui.theme.pencilShadow

/** The floating paper menu bar: a lighter sheet with a pen outline, the chosen tab inked in black. */
@Composable
fun PaperNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(66.dp)
            .pencilShadow(Wobble.Card, p.pencil)
            .background(p.surface, Wobble.Card)
            .border(1.5.dp, p.pen, Wobble.Card)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(Wobble.Small)
                    .then(if (on) Modifier.background(p.ink, Wobble.Small) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (on) p.bg else p.ink2
                Icon(painterResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                // Six tabs share the width, so the names stay on one line and a little smaller.
                Text(
                    text = tab.title,
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
