package com.lifetracker.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.EntryEntity
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    date: LocalDate,
    existing: List<EntryEntity>,
    onDismiss: () -> Unit,
    onSave: (EntryEntity) -> Unit,
) {
    val context = LocalContext.current
    val isToday = date == LocalDate.now()
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }

    val defaultStart = remember {
        val lastEnd = existing.mapNotNull { it.endMinute }.maxOrNull()
        when {
            lastEnd != null -> lastEnd.coerceAtMost(1438)
            isToday -> (nowMinute - 30).coerceAtLeast(0)
            else -> 9 * 60
        }
    }
    val defaultEnd = remember {
        if (isToday && nowMinute > defaultStart) nowMinute else (defaultStart + 30).coerceAtMost(1439)
    }

    var category by remember { mutableStateOf(ActivityCategory.Study) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(defaultStart) }
    var end by remember { mutableIntStateOf(defaultEnd) }
    var moment by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(h * 60 + m) },
            initial / 60,
            initial % 60,
            false,
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Log something",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label) },
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What did you do?") },
                placeholder = { Text("e.g. Polity revision") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonoLabel("Start")
                    OutlinedButton(
                        onClick = { pickTime(start) { start = it; error = null } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(formatMinute(start), fontFamily = FontFamily.Monospace)
                    }
                }
                if (!moment) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MonoLabel("End")
                        OutlinedButton(
                            onClick = { pickTime(end) { end = it; error = null } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(formatMinute(end), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = moment, onCheckedChange = { moment = it; error = null })
                Text(
                    text = "Just a moment, no end time",
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
            )

            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(onClick = {
                    when {
                        title.isBlank() -> error = "Give it a short name."
                        !moment && end <= start -> error = "End time must be after the start time."
                        else -> onSave(
                            EntryEntity(
                                date = date.toString(),
                                startMinute = start,
                                endMinute = if (moment) null else end,
                                category = category.name,
                                title = title.trim(),
                                note = note.trim(),
                            ),
                        )
                    }
                }) { Text("Add to timeline") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
