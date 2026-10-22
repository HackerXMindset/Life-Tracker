package com.lifetracker.app.data

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pausing a habit (Habits H9), the way Streak does it: holidays (`vacations`, each with a start day and an end day, or
 * no end while it is still going) and weekly rest days (`restDays`, 1 = Monday ... 7 = Sunday). Both are kept in the habit's
 * own record, so they travel with it. Nothing a habit recorded is touched. No Android in it, so it can be tested.
 */
object HabitPauses {
    private data class Span(val start: LocalDate, val end: LocalDate?, val bulk: Boolean) {
        val ongoing: Boolean get() = end == null

        fun contains(day: LocalDate, today: LocalDate): Boolean =
            !day.isBefore(start) && !day.isAfter(end ?: today)
    }

    /** Streak writes a holiday day as "2026-10-04T00:00:00.000". */
    fun stamp(day: LocalDate): String = "${day}T00:00:00.000"

    private fun parse(raw: String): JSONObject = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())

    private fun day(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

    private fun read(o: JSONObject): List<Span> {
        val array = o.optJSONArray("vacations") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val v = array.optJSONObject(i) ?: return@mapNotNull null
            val start = day(v.optString("start")) ?: return@mapNotNull null
            Span(start, if (v.isNull("end")) null else day(v.optString("end")), v.optBoolean("bulk", false))
        }
    }

    private fun write(o: JSONObject, spans: List<Span>) {
        val array = JSONArray()
        spans.forEach { s ->
            val v = JSONObject().put("start", stamp(s.start))
            s.end?.let { v.put("end", stamp(it)) }
            if (s.bulk) v.put("bulk", true)
            array.put(v)
        }
        o.put("vacations", array)
    }

    /** Is the habit on a holiday that has no end yet? */
    fun isPaused(raw: String): Boolean = read(parse(raw)).any { it.ongoing }

    fun restDays(raw: String): List<Int> = HabitPlan.fromRaw(raw).restDays.sorted()

    /** Is [day] inside a holiday (not counting rest days)? */
    fun isInPause(raw: String, day: LocalDate, today: LocalDate): Boolean = read(parse(raw)).any { it.contains(day, today) }

    /** Is [day] a rest day of the week? */
    fun isRestDay(raw: String, day: LocalDate): Boolean = day.dayOfWeek.value in restDays(raw)

    /**
     * Starts a holiday today with no end ([on]), unless one is already going. Resuming ends it yesterday; a holiday that
     * began today just disappears.
     */
    fun setPaused(raw: String, on: Boolean, today: LocalDate): String {
        val o = parse(raw)
        val spans = read(o)
        val next = if (on) {
            if (spans.any { it.ongoing }) spans else spans + Span(today, null, false)
        } else {
            val yesterday = today.minusDays(1)
            spans.mapNotNull { s ->
                when {
                    !s.ongoing -> s
                    !yesterday.isBefore(s.start) -> s.copy(end = yesterday)
                    else -> null
                }
            }
        }
        write(o, next)
        return o.toString()
    }

    /**
     * Pauses one day, or brings it back. A day in the middle of a holiday splits it in two; the day at an edge shortens it.
     * A day that is only a rest day of the week is left alone (change the rest days instead).
     */
    fun toggleDay(raw: String, target: LocalDate, today: LocalDate): String {
        val o = parse(raw)
        val spans = read(o)
        val paused = isRestDay(raw, target) || spans.any { it.contains(target, today) }
        val next = if (!paused) {
            spans + Span(target, target, false)
        } else {
            val out = ArrayList<Span>()
            for (s in spans) {
                if (!s.contains(target, today)) {
                    out += s
                    continue
                }
                if (s.start.isBefore(target)) out += Span(s.start, target.minusDays(1), s.bulk)
                val end = s.end
                if (end == null) out += Span(target.plusDays(1), null, s.bulk)
                else if (end.isAfter(target)) out += Span(target.plusDays(1), end, s.bulk)
            }
            out
        }
        write(o, next)
        return o.toString()
    }

    fun setRestDays(raw: String, days: Collection<Int>): String {
        val o = parse(raw)
        o.put("restDays", JSONArray(days.filter { it in 1..7 }.distinct().sorted()))
        return o.toString()
    }
}
