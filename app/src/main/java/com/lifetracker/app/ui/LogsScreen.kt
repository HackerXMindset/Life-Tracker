package com.lifetracker.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppLog
import com.lifetracker.app.data.LogFormat
import com.lifetracker.app.data.LogLevel
import com.lifetracker.app.data.LogLine
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The most lines drawn at once; Copy and Share carry the same lines. */
private const val SHOWN_MAX = 1500

private val warnInk = Color(0xFFC98A00)

/**
 * Settings, last line: the app's diary, live. Newest at the bottom, following the end while you watch. A day picker,
 * filters by seriousness and part of the app, a search box, and Copy / Share for what is on screen.
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val paper = LocalPaper.current
    val today = remember { LocalDate.now() }
    var day by remember { mutableStateOf(today) }
    var level by remember { mutableStateOf(LogLevel.Info) }
    var tag by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }
    var lines by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var days by remember { mutableStateOf(listOf(today)) }
    var askClear by remember { mutableStateOf(false) }
    var sizeText by remember { mutableStateOf("") }
    val revision by AppLog.revision.collectAsState()
    val listState = rememberLazyListState()

    // Today reloads a moment after each new line; an earlier day is read once.
    LaunchedEffect(day, if (day == today) revision else 0) {
        if (day == today) delay(300)
        val loaded = withContext(Dispatchers.IO) { AppLog.read(day) }
        lines = if (loaded.isEmpty() && day == today) AppLog.recentLines() else loaded
        days = (listOf(today) + withContext(Dispatchers.IO) { AppLog.days() }).distinct()
        sizeText = sizeLabel(withContext(Dispatchers.IO) { AppLog.sizeBytes() })
    }

    val tags = remember(lines) { LogFormat.tags(lines) }
    val picked = tag?.takeIf { it in tags }
    val filtered = remember(lines, level, picked, query) { LogFormat.filter(lines, level, picked, query) }
    val shown = if (filtered.size > SHOWN_MAX) filtered.takeLast(SHOWN_MAX) else filtered

    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(shown.size, follow, day) {
        if (follow && shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsTop(title = "Live logs", sub = "What the app did in the background, as it happens", onBack = onBack)

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEach { d ->
                PaperPill(dayName(d, today), day == d, { day = d; tag = null; follow = true })
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PaperPill("All", level == LogLevel.Info, { level = LogLevel.Info })
            PaperPill("Warnings", level == LogLevel.Warn, { level = LogLevel.Warn })
            PaperPill("Errors", level == LogLevel.Error, { level = LogLevel.Error })
        }
        if (tags.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PaperPill("Every part", picked == null, { tag = null })
                tags.forEach { t -> PaperPill(t, picked == t, { tag = t }) }
            }
        }
        PaperField(
            value = query,
            onValueChange = { query = it },
            label = "Search",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        PaperCard(modifier = Modifier.fillMaxWidth().weight(1f), padding = 8.dp, shadow = false) {
            if (shown.isEmpty()) {
                Text(
                    text = if (lines.isEmpty()) "Nothing written for this day yet." else "No line matches these filters.",
                    fontSize = 13.sp,
                    color = paper.ink2,
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(shown, key = { i, l -> "$i-${l.ms}" }) { _, l ->
                        Text(
                            text = LogFormat.readable(l),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = when (l.level) {
                                LogLevel.Info -> paper.ink
                                LogLevel.Warn -> warnInk
                                LogLevel.Error -> paper.bad
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Text(
            text = "${filtered.size} of ${lines.size} lines" + (if (filtered.size > SHOWN_MAX) ", the last $SHOWN_MAX shown" else "") +
                " · diary uses $sizeText · days older than ${LogFormat.KEEP_DAYS} are removed",
            fontSize = 11.sp,
            color = paper.ink2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PaperButton(if (follow) "Following" else "Follow end", { follow = true }, small = true, style = if (follow) PaperButtonStyle.Dark else PaperButtonStyle.Ghost)
            PaperButton("Copy", { copyText(context, LogFormat.asText(shown)) }, small = true, style = PaperButtonStyle.Ghost, enabled = shown.isNotEmpty())
            PaperButton("Share", { shareText(context, LogFormat.asText(shown)) }, small = true, style = PaperButtonStyle.Ghost, enabled = shown.isNotEmpty())
            PaperButton("Clear logs", { askClear = true }, small = true, style = PaperButtonStyle.Danger)
        }
    }

    if (askClear) {
        PaperDialog(
            title = "Clear all logs?",
            onDismiss = { askClear = false },
            confirmText = "Clear logs",
            confirmStyle = PaperButtonStyle.Danger,
            onConfirm = {
                AppLog.clear()
                lines = emptyList()
                askClear = false
            },
        ) {
            Text(
                "This removes the diary of what the app did, for every day. Your tracked data, habits, entries and backups are not touched.",
                fontSize = 14.sp,
                color = paper.ink,
            )
        }
    }
}

private fun dayName(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("d MMM"))
}

private fun sizeLabel(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
}

private fun copyText(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Live logs", text))
    }
}

private fun shareText(context: Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
