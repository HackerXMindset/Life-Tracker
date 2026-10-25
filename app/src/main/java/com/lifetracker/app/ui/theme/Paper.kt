package com.lifetracker.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The paper look's building blocks (Habits and every other screen use these as each one is redrawn):
 * a card with a wobbly pen outline and a hard pencil shadow, buttons, pills, a three-way switch, a toggle,
 * a heading with an orange highlighter swipe, and tally-stroke progress bars.
 */

/** The hard, un-blurred shadow offset down and right, like a pencil line under a card. */
fun Modifier.pencilShadow(shape: Shape, color: Color, dx: Dp = 3.dp, dy: Dp = 4.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(dx.toPx(), dy.toPx()) { drawOutline(outline, color) }
}

/** A sheet of lighter paper with a wobbly pen outline and a pencil shadow. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    padding: Dp = 16.dp,
    shadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPaper.current
    val cardShape = shape ?: if (p.dark) Wobble.Card else RoundedCornerShape(20.dp)
    val base = if (shadow && p.dark) modifier.pencilShadow(cardShape, p.pencil) else modifier
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = base
            .background(p.surface, cardShape)
            .border(1.dp, p.line, cardShape)
            .clip(cardShape)
            .then(clickable)
            .padding(padding),
        content = content,
    )
}

enum class PaperButtonStyle { Dark, Ghost, Danger }

/** A button: ink-black (the main action), outlined (everything else), or red pencil (careful). */
@Composable
fun PaperButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PaperButtonStyle = PaperButtonStyle.Dark,
    small: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    PaperButtonBox(onClick, modifier, style, small, enabled) {
        val tint = LocalContentColor.current
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = tint, fontWeight = FontWeight.Bold, fontSize = if (small) 14.sp else 15.sp, maxLines = 1)
    }
}

/**
 * The button itself, with room for any content (the shared Settings pages use it through the Button, OutlinedButton and
 * TextButton in PaperCompat.kt). Text inside takes the button's ink colour and a bold face unless it says otherwise.
 */
@Composable
fun PaperButtonBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PaperButtonStyle = PaperButtonStyle.Dark,
    small: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPaper.current
    val shape = if (p.dark) Wobble.Small else RoundedCornerShape(12.dp)
    val (fill, ink, edge) = when (style) {
        PaperButtonStyle.Dark -> Triple(p.ink, p.bg, p.ink)
        PaperButtonStyle.Ghost -> Triple(p.surface, p.ink, p.pen)
        PaperButtonStyle.Danger -> Triple(p.bad, if (p.dark) p.bg else Color.White, p.bad)
    }
    Row(
        modifier = modifier
            .height(if (small) 36.dp else 46.dp)
            .then(if (p.dark) Modifier.pencilShadow(shape, p.pencil, 2.dp, 3.dp) else Modifier)
            .background(fill, shape)
            .border(1.5.dp, edge, shape)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) ink else ink.copy(alpha = 0.45f)
        CompositionLocalProvider(LocalContentColor provides tint) {
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.Bold, fontSize = if (small) 14.sp else 15.sp)) {
                content()
            }
        }
    }
}

/** A small round-cornered label that can be picked. Picked ones turn ink-black. */
@Composable
fun PaperPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
) {
    PaperPillBox(selected, onClick, modifier) {
        if (dot != null) Box(Modifier.size(9.dp).background(dot, CircleShape))
        Text(text, color = LocalContentColor.current, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

/** The pill itself, with room for any content. Picked ones turn ink-black and their text turns paper-coloured. */
@Composable
fun PaperPillBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPaper.current
    val shape = if (p.dark) Wobble.Pill else CircleShape
    val fill = if (selected) p.ink else p.surface
    val ink = if (selected) p.bg else p.ink
    Row(
        modifier = modifier
            .background(fill, shape)
            .border(1.25.dp, if (selected) p.ink else p.pen, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)) {
                content()
            }
        }
    }
}

/** Two or three choices side by side in one worn-paper strip, the picked one lifted onto lighter paper. */
@Composable
fun PaperSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    val shape = if (p.dark) Wobble.Small else RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(p.surface2, shape)
            .border(1.25.dp, p.line, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(if (on) Modifier.background(p.ink, shape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 8.dp, horizontal = 6.dp)
                    .semantics { stateDescription = if (on) "Selected" else "Not selected" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) p.bg else p.ink2, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

/** An on/off switch drawn in pen: green when on. */
@Composable
fun PaperToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val shift by animateDpAsState(if (checked) 20.dp else 0.dp, label = "toggle")
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .background(if (checked) p.ink else p.surface2, CircleShape)
            .border(1.5.dp, p.pen, CircleShape)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "On" else "Off" },
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp + shift, y = 3.dp)
                .size(24.dp)
                .background(if (checked) p.bg else p.surface, CircleShape)
                .border(1.5.dp, p.pen, CircleShape),
        )
    }
}

/** A round-cornered square button holding one icon. */
@Composable
fun PaperIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaper.current
    val shape = if (p.dark) Wobble.Small else RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .then(if (p.dark) Modifier.pencilShadow(shape, p.pencil, 2.dp, 3.dp) else Modifier)
            .background(p.surface, shape)
            .border(1.5.dp, p.pen, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = p.ink, modifier = Modifier.size(20.dp))
    }
}

/** A heading with an orange highlighter swipe drawn behind the lower half of the words. */
@Composable
fun HighlightedHeading(text: String, modifier: Modifier = Modifier, fontSize: Float = 17f) {
    val p = LocalPaper.current
    val headingModifier = if (p.dark) {
        modifier.drawBehind {
            val h = fontSize.sp.toPx() * 0.5f
            rotate(-0.8f, pivot = center) {
                drawRoundRect(
                    color = p.sun.copy(alpha = 0.4f),
                    topLeft = Offset(0f, size.height - h - 1.dp.toPx()),
                    size = Size(size.width + 2.dp.toPx(), h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }.padding(horizontal = 4.dp)
    } else {
        modifier
    }
    Text(
        text = text,
        modifier = headingModifier,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        color = p.ink,
    )
}

/**
 * Progress as a row of slanted pen strokes, like tally marks: dark strokes are what is done, faint ones what is
 * left. [over] turns the dark strokes red pencil.
 */
@Composable
fun TallyBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalPaper.current.ink,
    over: Boolean = false,
    height: Dp = 20.dp,
) {
    val p = LocalPaper.current
    val filledColor = if (over) p.bad else color
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (p.dark) {
            val pitch = 10.dp.toPx()
            val count = TallyMath.count(size.width, pitch)
            val done = TallyMath.filled(progress, count)
            val stroke = 1.9f / 10f * pitch
            fun line(i: Int, c: Color) {
                val x = i * pitch
                drawLine(
                    color = c,
                    start = Offset(x + 0.24f * pitch, size.height * 0.93f),
                    end = Offset(x + 0.68f * pitch, size.height * 0.07f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            for (i in 0 until count) line(i, if (i < done) filledColor else p.line)
        } else {
            val fraction = progress.coerceIn(0f, 1f)
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(p.surface2, size = Size(size.width, size.height), cornerRadius = radius)
            if (fraction > 0f) {
                drawRoundRect(filledColor, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            }
        }
    }
}

/** The floating Log button: ink-black, hand-drawn corners, a hard pencil shadow. */
@Composable
fun PaperFab(text: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    val shape = if (p.dark) Wobble.Fab else RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .then(if (p.dark) Modifier.pencilShadow(shape, p.pencil, 3.dp, 4.dp) else Modifier)
            .background(p.ink, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = p.bg, modifier = Modifier.size(22.dp))
        Text(text, color = p.bg, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

/** A sheet that slides up from the bottom: lighter paper, a hand-drawn top edge and a pencil grip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPaper.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = if (p.dark) Wobble.Sheet else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = p.surface,
        contentColor = p.ink,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp).size(width = 44.dp, height = 5.dp).background(p.line, CircleShape))
        },
        content = content,
    )
}

/** A small label above a text box with a pen outline. */
@Composable
fun PaperField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val p = LocalPaper.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = p.ink2)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder != null) {
                { Text(placeholder, color = p.ink2.copy(alpha = 0.6f)) }
            } else {
                null
            },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            shape = if (p.dark) Wobble.Small else RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = p.ink,
                unfocusedBorderColor = p.pen,
                focusedTextColor = p.ink,
                unfocusedTextColor = p.ink,
                cursorColor = p.ink,
                focusedContainerColor = p.surface,
                unfocusedContainerColor = p.surface,
            ),
        )
    }
}

/** A pop-up question or form on a sheet of paper, with paper buttons. [confirmText] null leaves out the main button. */
@Composable
fun PaperDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    confirmStyle: PaperButtonStyle = PaperButtonStyle.Dark,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPaper.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = if (p.dark) Wobble.Card else RoundedCornerShape(20.dp),
        containerColor = p.surface,
        titleContentColor = p.ink,
        textContentColor = p.ink,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = {
            if (confirmText != null && onConfirm != null) PaperButton(confirmText, onConfirm, style = confirmStyle, small = true, enabled = confirmEnabled)
        },
        dismissButton = { PaperButton(dismissText, onDismiss, style = PaperButtonStyle.Ghost, small = true) },
    )
}

/** A small orange-pencil label that says where something comes from ("Shared with Streak"). */
@Composable
fun PaperChip(text: String, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    Text(
        text = text,
        modifier = modifier
            .background(p.sun.copy(alpha = if (p.dark) 0.22f else 0.14f), if (p.dark) Wobble.Pill else RoundedCornerShape(8.dp))
            .border(1.dp, p.sun, if (p.dark) Wobble.Pill else RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = p.ink,
        maxLines = 1,
    )
}
