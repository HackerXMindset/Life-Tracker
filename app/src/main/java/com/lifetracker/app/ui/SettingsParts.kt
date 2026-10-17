package com.lifetracker.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.ui.theme.Wobble
import com.lifetracker.app.ui.theme.pencilShadow

/*
 * The pieces the Settings pages are drawn from, copied from the demo (Life-Tracker-paper-6.html): the page top with a
 * Back button, list rows with an icon and a chevron, cards of switches with dashed dividers, key / value lines and the
 * tilted orange note. Pages use these instead of the older cards so that every Settings page looks the same.
 */

/** One of the demo's line icons, drawn in [tint] (ink by default). */
@Composable
fun PaperIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalPaper.current.ink) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** "‹ Back", then the big title and the quiet line under it. */
@Composable
fun SettingsTop(title: String, sub: String, onBack: () -> Unit) {
    val p = LocalPaper.current
    // Constrain the whole header to the available screen width. Without this,
    // wrap-content measurement can leave header text with an unexpectedly narrow width.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(Wobble.Small)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(start = 0.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PaperIcon(R.drawable.ic_p_back, size = 18.dp, tint = p.ink2)
            Text("Back", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.ink2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                color = p.ink,
                maxLines = 2,
            )
            Text(text = sub, modifier = Modifier.fillMaxWidth(), fontSize = 12.sp, color = p.ink2)
        }
    }
}

/** A list row: icon, a bold name with a small line under it, and a chevron. */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int?,
    title: String,
    hint: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(1.25.dp, p.line, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) PaperIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
            if (hint.isNotEmpty()) Text(hint, fontSize = 12.5.sp, color = p.ink2)
        }
        if (trailing != null) trailing()
        if (onClick != null) PaperIcon(R.drawable.ic_p_chev, size = 18.dp, tint = p.ink2)
    }
}

/** A flat paper card that holds switches and lines separated by dashed dividers. */
@Composable
fun SetCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(1.25.dp, p.line, shape)
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) { content() }
}

/** A dashed line across the top of a row, like the demo's `border-top: dashed`. */
fun Modifier.dashedTop(color: Color): Modifier = drawBehind {
    drawLine(
        color, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
}

/** A name with a small line under it and a switch at the right. [first] leaves out the dashed line above. */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String = "", first: Boolean = false) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.dashedTop(p.line))
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
            if (hint.isNotEmpty()) Text(hint, fontSize = 12.5.sp, color = p.ink2)
        }
        PaperToggle(checked = checked, onChange = onChange)
    }
}

/** A label on the left and its value on the right, with a dashed line above. */
@Composable
fun KvRow(label: String, value: String, first: Boolean = false) {
    val p = LocalPaper.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.dashedTop(p.line))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, color = p.ink2)
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = p.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** The demo's orange-tinted note, slightly tilted, with a pencil shadow. */
@Composable
fun LockNote(text: String, @DrawableRes icon: Int = R.drawable.ic_p_lock, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    val fill = Color(
        red = p.sun.red * 0.3f + p.surface.red * 0.7f,
        green = p.sun.green * 0.3f + p.surface.green * 0.7f,
        blue = p.sun.blue * 0.3f + p.surface.blue * 0.7f,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .rotate(-0.5f)
            .pencilShadow(shape, p.pencil, 2.dp, 3.dp)
            .background(fill, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperIcon(icon)
        Text(text, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
    }
}

/** A row that opens and closes to show a paragraph, like the demo's "How it works". */
@Composable
fun AccordionRow(title: String, body: String, first: Boolean = false) {
    val p = LocalPaper.current
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().then(if (first) Modifier else Modifier.dashedTop(p.line))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
            PaperIcon(if (open) R.drawable.ic_p_down else R.drawable.ic_p_chev, size = 18.dp, tint = p.ink2)
        }
        if (open) Text(body, modifier = Modifier.padding(bottom = 14.dp), fontSize = 14.sp, lineHeight = 21.sp, color = p.ink2)
    }
}
