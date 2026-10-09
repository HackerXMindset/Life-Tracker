package com.lifetracker.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.DateSpan
import com.lifetracker.app.data.RangePresets
import java.time.LocalDate

/** Which days a screen is showing, and whether that came from a quick choice ([preset]) or from dates you picked ([RangePresets.CUSTOM]). */
data class RangeChoice(val preset: String, val span: DateSpan) {
    companion object {
        fun preset(id: String, today: LocalDate = LocalDate.now()) = RangeChoice(id, RangePresets.span(id, today))
        fun custom(span: DateSpan) = RangeChoice(RangePresets.CUSTOM, span)
    }
}

/**
 * Quick choices (Today, Last 7 days, All time, ...), then From and To dates you can set to anything,
 * and arrows to step to the day (or stretch) before or after. Pick the same date twice for one day.
 */
@Composable
fun RangePicker(choice: RangeChoice, onChange: (RangeChoice) -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()

    fun pick(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RangePresets.list.forEach { p ->
                FilterChip(
                    selected = choice.preset == p.id,
                    onClick = { onChange(RangeChoice.preset(p.id, today)) },
                    label = { Text(p.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onChange(RangeChoice.custom(choice.span.shifted(-1))) }) { Text("<") }
            OutlinedButton(
                onClick = { pick(choice.span.from) { onChange(RangeChoice.custom(choice.span.withFrom(it))) } },
            ) { Text("From " + choice.span.from.format(SHORT)) }
            OutlinedButton(
                onClick = { pick(choice.span.to) { onChange(RangeChoice.custom(choice.span.withTo(it))) } },
            ) { Text("To " + choice.span.to.format(SHORT)) }
            TextButton(onClick = { onChange(RangeChoice.custom(choice.span.shifted(1))) }) { Text(">") }
        }
        Text(
            text = choice.span.label(today) + if (choice.span.isSingleDay) "" else " · ${choice.span.dayCount} days",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SHORT: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("d MMM yy", java.util.Locale.ENGLISH)
