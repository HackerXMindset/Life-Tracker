package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Looking back (Step 14): what you did and how much you studied in a month or a year, and around each life event. Works on minutes
 * per activity for each day, which the screen collects (your Timeline entries plus phone use linked to an activity). No Android here,
 * so it can be tested.
 */
object LifeLook {
    const val STUDY_ID = "Study"

    /** Minutes for each activity on each day. A day that is missing, or has only 0 minutes, counts as "no data". */
    typealias PerDay = Map<LocalDate, Map<String, Int>>

    class Summary(
        val trackedDays: Int,
        val totalMin: Int,
        val studyMin: Int,
        /** Activities by minutes, biggest first. */
        val top: List<Pair<String, Int>>,
        /** The day with the most study, or null when there was none. */
        val bestStudyDay: Pair<LocalDate, Int>?,
    ) {
        val studyPerTrackedDay: Int get() = if (trackedDays == 0) 0 else studyMin / trackedDays
        val totalPerTrackedDay: Int get() = if (trackedDays == 0) 0 else totalMin / trackedDays
    }

    private fun dayTotal(m: Map<String, Int>) = m.values.sum()

    /** The summary of the days from [from] to [to], both included. */
    fun summary(perDay: PerDay, from: LocalDate, to: LocalDate): Summary {
        var tracked = 0
        var total = 0
        var study = 0
        val byActivity = HashMap<String, Int>()
        var best: Pair<LocalDate, Int>? = null
        var d = from
        while (!d.isAfter(to)) {
            val m = perDay[d].orEmpty()
            val t = dayTotal(m)
            if (t > 0) {
                tracked++
                total += t
                m.forEach { (k, v) -> if (v > 0) byActivity[k] = (byActivity[k] ?: 0) + v }
                val s = m[STUDY_ID] ?: 0
                study += s
                if (s > 0 && (best == null || s > best.second)) best = d to s
            }
            d = d.plusDays(1)
        }
        val top = byActivity.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }
        return Summary(tracked, total, study, top, best)
    }

    fun month(perDay: PerDay, ym: YearMonth): Summary = summary(perDay, ym.atDay(1), ym.atEndOfMonth())

    /** Twelve summaries, January first. */
    fun year(perDay: PerDay, year: Int): List<Summary> = (1..12).map { month(perDay, YearMonth.of(year, it)) }

    /** Study minutes on each day of a month, in order (0 for a day with none), for a small chart. */
    fun studyByDay(perDay: PerDay, ym: YearMonth): List<Int> = (1..ym.lengthOfMonth()).map { perDay[ym.atDay(it)]?.get(STUDY_ID) ?: 0 }

    /** Before and after one event: [days] days on each side (the day itself is in neither). */
    class Around(val before: Summary, val after: Summary, val daysAfterKnown: Int, val future: Boolean) {
        /** Change in study per tracked day, or null when either side has no tracked day. */
        val studyChange: Int? get() = if (before.trackedDays == 0 || after.trackedDays == 0) null else after.studyPerTrackedDay - before.studyPerTrackedDay
        val totalChange: Int? get() = if (before.trackedDays == 0 || after.trackedDays == 0) null else after.totalPerTrackedDay - before.totalPerTrackedDay
    }

    /** [today] cuts the "after" side, so days that have not happened yet are not counted as empty. */
    fun around(perDay: PerDay, eventDate: LocalDate, today: LocalDate, days: Int = 30): Around {
        val before = summary(perDay, eventDate.minusDays(days.toLong()), eventDate.minusDays(1))
        val afterEnd = minOf(eventDate.plusDays(days.toLong()), today)
        val after = if (afterEnd.isAfter(eventDate)) summary(perDay, eventDate.plusDays(1), afterEnd) else summary(perDay, eventDate.plusDays(1), eventDate)
        val known = if (afterEnd.isAfter(eventDate)) (afterEnd.toEpochDay() - eventDate.toEpochDay()).toInt() else 0
        return Around(before, after, known, future = eventDate.isAfter(today))
    }

    /** The events that fall inside a month, oldest first. */
    fun eventsIn(events: List<LifeEventEntity>, ym: YearMonth): List<LifeEventEntity> =
        events.filter { runCatching { YearMonth.from(LocalDate.parse(it.date)) == ym }.getOrDefault(false) }.sortedBy { it.date }

    /** The events of a year by month number (1 to 12). */
    fun eventsByMonth(events: List<LifeEventEntity>, year: Int): Map<Int, List<LifeEventEntity>> =
        (1..12).associateWith { eventsIn(events, YearMonth.of(year, it)) }

    /** The date typed as 2026-11-02, or null. */
    fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** "2 h 5 min", "45 min", "0 min". */
    fun durationText(min: Int): String {
        val m = Math.abs(min)
        val text = if (m >= 60) (m / 60).toString() + " h" + (if (m % 60 != 0) " " + (m % 60) + " min" else "") else "$m min"
        return text
    }

    /** "+45 min", "-1 h 10 min", "no change". */
    fun changeText(min: Int): String = when {
        min == 0 -> "no change"
        min > 0 -> "+" + durationText(min)
        else -> "-" + durationText(min)
    }

    /** Days since the event ("day 12"), or null before it. */
    fun dayCount(eventDate: LocalDate, today: LocalDate): Int? =
        (today.toEpochDay() - eventDate.toEpochDay()).toInt().takeIf { it >= 0 }?.plus(1)
}
