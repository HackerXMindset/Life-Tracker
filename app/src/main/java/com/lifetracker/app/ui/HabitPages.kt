package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.HabitEntity
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle

/**
 * A whole screen for the Habits tab (Habits H11), like Streak's pages: a Back button and the title on top, the content
 * in the middle (it scrolls itself), and, if [confirmText] is given, the main button and a second button pinned at the
 * bottom. The phone's Back button closes it too.
 */
@Composable
fun HabitPage(
    title: String,
    onBack: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    val paper = LocalPaper.current
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaperButton("Back", onBack, style = PaperButtonStyle.Ghost, small = true)
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = paper.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        if (confirmText != null && onConfirm != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperButton(dismissText, onBack, modifier = Modifier.weight(1f), style = PaperButtonStyle.Ghost)
                PaperButton(confirmText, onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Every habit you see, with Up and Down buttons. The new order is saved at once and reaches Streak. */
@Composable
fun HabitReorderPage(habits: List<HabitEntity>, onMove: (habitId: String, delta: Int) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val paper = LocalPaper.current
    Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaperButton("Back", onBack, style = PaperButtonStyle.Ghost, small = true)
            Text("Reorder", modifier = Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = paper.ink)
        }
        Text(
            text = "Use Up and Down to put your habits in the order you want. It is saved straight away.",
            modifier = Modifier.padding(vertical = 10.dp),
            fontSize = 12.sp,
            color = paper.ink2,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(habits, key = { _, h -> h.id }) { index, habit ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = habit.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = paper.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PaperButton("Up", { onMove(habit.id, -1) }, style = PaperButtonStyle.Ghost, small = true, enabled = index > 0)
                    PaperButton("Down", { onMove(habit.id, 1) }, style = PaperButtonStyle.Ghost, small = true, enabled = index < habits.lastIndex)
                }
            }
        }
    }
}
