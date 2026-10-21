package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How serious a diary line is. */
enum class LogLevel(val code: Char) {
    Info('I'), Warn('W'), Error('E');

    companion object {
        fun of(code: Char): LogLevel = entries.firstOrNull { it.code == code } ?: Info
    }
}

/** One line of the app's diary: [ms] is the clock time, [tag] the part of the app (Places, Service, Trips...). */
data class LogLine(val ms: Long, val level: LogLevel, val tag: String, val text: String)

/**
 * The pure part of the app's diary (no Android in it, so it is tested): how a line is written to the daily text file and
 * read back, the filters on the Live logs page, and which old days the diary may drop.
 */
object LogFormat {
    /** Days of diary kept. Older days are removed from the diary only, never any tracked data. */
    const val KEEP_DAYS = 30

    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** One line of the file: time in milliseconds, level letter, tag, text, split by tabs. Line breaks in the text become a small marker. */
    fun encode(line: LogLine): String {
        val tag = line.tag.replace('\t', ' ').replace('\n', ' ').ifBlank { "App" }
        val text = line.text.replace("\r", "").replace('\t', ' ').replace("\n", " ⏎ ")
        return "${line.ms}\t${line.level.code}\t$tag\t$text"
    }

    /** The line written by [encode], or null for anything that is not one (a half-written last line, say). */
    fun decode(raw: String): LogLine? {
        val parts = raw.split('\t', limit = 4)
        if (parts.size < 4) return null
        val ms = parts[0].toLongOrNull() ?: return null
        if (parts[1].length != 1) return null
        return LogLine(ms, LogLevel.of(parts[1][0]), parts[2], parts[3])
    }

    fun clock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clockFormat.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** The day a line belongs to, in the phone's time zone. */
    fun dayOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun fileName(day: LocalDate): String = "$day.log"

    /** The day a file name stands for, or null if it is not one of the diary's files. */
    fun dayOfFile(name: String): LocalDate? =
        if (!name.endsWith(".log")) null else runCatching { LocalDate.parse(name.removeSuffix(".log")) }.getOrNull()

    /** The days that are too old to keep when today is [today]. */
    fun staleDays(days: List<LocalDate>, today: LocalDate, keep: Int = KEEP_DAYS): List<LocalDate> =
        days.filter { it.isBefore(today.minusDays(keep.toLong() - 1)) }

    /** Lines at [min] or worse, with the [tag] (null for every tag), whose time, tag or text holds [query] (ignoring capitals). */
    fun filter(lines: List<LogLine>, min: LogLevel, tag: String?, query: String): List<LogLine> {
        val q = query.trim()
        return lines.filter { l ->
            l.level.ordinal >= min.ordinal &&
                (tag == null || l.tag == tag) &&
                (q.isEmpty() || l.text.contains(q, ignoreCase = true) || l.tag.contains(q, ignoreCase = true))
        }
    }

    /** The tags present, most used first (ties alphabetically). */
    fun tags(lines: List<LogLine>): List<String> =
        lines.groupingBy { it.tag }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /** One readable line: "10:16:04 W Places  text". */
    fun readable(line: LogLine, zone: ZoneId = ZoneId.systemDefault()): String =
        "${clock(line.ms, zone)} ${line.level.code} ${line.tag}  ${line.text}"

    fun asText(lines: List<LogLine>, zone: ZoneId = ZoneId.systemDefault()): String =
        lines.joinToString("\n") { readable(it, zone) }

    /** A line for a saved file, with the date too: "2026-10-21 10:16:04 W Places  text". */
    fun fileLine(line: LogLine, zone: ZoneId = ZoneId.systemDefault()): String = "${dayOf(line.ms, zone)} ${readable(line, zone)}"

    fun asFileText(lines: List<LogLine>, zone: ZoneId = ZoneId.systemDefault()): String =
        lines.joinToString("\n", postfix = "\n") { fileLine(it, zone) }

    fun saveName(day: LocalDate?, today: LocalDate): String =
        if (day == null) "life-tracker-logs-all-$today.txt" else "life-tracker-logs-$day.txt"
}
