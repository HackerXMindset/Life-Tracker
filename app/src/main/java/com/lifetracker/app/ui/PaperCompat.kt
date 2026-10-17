package com.lifetracker.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperPillBox
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.ui.theme.Wobble

/*
 * Paper versions of the Material widgets the Settings pages use, with the SAME names and parameters.
 * A page that does not import the Material one of the same name (androidx.compose.material3.Button and so on)
 * gets the paper one from this package, so a page changes look without any change in what its buttons and
 * switches do. A page that still imports the Material widget keeps the old look until its own turn.
 */

/** The main button: ink-black. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PaperButtonBox(onClick, modifier, PaperButtonStyle.Dark, small = true, enabled = enabled, content = content)

/** An outlined button on paper. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PaperButtonBox(onClick, modifier, PaperButtonStyle.Ghost, small = true, enabled = enabled, content = content)

/** A quiet button (Back, Cancel, Close): drawn like the outlined one so it can be seen and hit. */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PaperButtonBox(onClick, modifier, PaperButtonStyle.Ghost, small = true, enabled = enabled, content = content)

/** A pill that can be picked. */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) = PaperPillBox(selected, onClick, modifier) {
    leadingIcon?.invoke()
    label()
}

/** The pen-drawn switch. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) = PaperToggle(checked = checked, onChange = { onCheckedChange?.invoke(it) }, modifier = modifier)

/** A pop-up on a sheet of paper. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val p = LocalPaper.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title?.let { t -> { ProvideTextStyle(TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) { t() } } },
        text = text,
        shape = Wobble.Card,
        containerColor = p.surface,
        titleContentColor = p.ink,
        textContentColor = p.ink,
    )
}

/** A text box with a pen outline. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    val p = LocalPaper.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = Wobble.Small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.ink,
            unfocusedBorderColor = p.pen,
            errorBorderColor = p.bad,
            focusedTextColor = p.ink,
            unfocusedTextColor = p.ink,
            cursorColor = p.ink,
            focusedLabelColor = p.ink2,
            unfocusedLabelColor = p.ink2,
            focusedContainerColor = p.surface,
            unfocusedContainerColor = p.surface,
        ),
    )
}
