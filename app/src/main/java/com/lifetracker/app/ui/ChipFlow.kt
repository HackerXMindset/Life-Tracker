package com.lifetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperPillBox

/**
 * A row of choices that wraps onto more lines instead of scrolling sideways, so everything is in one place.
 * When there is more than [maxLines] lines of it, the rest hides behind "+N more" and "Show less" brings it back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    spacing: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        maxLines = if (expanded) Int.MAX_VALUE else maxLines,
        overflow = FlowRowOverflow.expandOrCollapseIndicator(
            expandIndicator = {
                MoreItemsPill(
                    totalItemCount = totalItemCount,
                    shownItemCount = { shownItemCount },
                    onClick = { expanded = true },
                )
            },
            collapseIndicator = { PaperPill("Show less", false, { expanded = false }) },
            minRowsToShowCollapse = maxLines + 1,
        ),
        content = { content() },
    )
}

/**
 * Keeps the "+N more" label, but reads shownItemCount only while drawing. FlowRow calculates that
 * value during layout, after it has already composed the overflow indicator.
 */
@Composable
private fun MoreItemsPill(
    totalItemCount: Int,
    shownItemCount: () -> Int,
    onClick: () -> Unit,
) {
    PaperPillBox(selected = false, onClick = onClick) {
        val textMeasurer = rememberTextMeasurer()
        val textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current)

        Text(
            text = "+$totalItemCount more",
            color = Color.Transparent,
            maxLines = 1,
            modifier = Modifier.drawWithContent {
                drawContent()

                val remainingItems = (totalItemCount - shownItemCount()).coerceAtLeast(0)
                val textLayout = textMeasurer.measure(
                    text = "+$remainingItems more",
                    style = textStyle,
                    maxLines = 1,
                )
                drawText(textLayout)
            },
        )
    }
}
