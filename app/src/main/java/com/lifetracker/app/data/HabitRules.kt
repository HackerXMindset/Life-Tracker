package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONObject

/** What a day on the calendar looks like. */
enum class DayKind { DONE, PARTIAL, COVERED, PAUSED, MISSED, OFF, FUTURE, BEFORE, RELAPSE, CLEAN }

/** One calendar day: how it looks, and for done days how full it is (0.25 to 1). */
data class HabitCell(val kind: DayKind, val ratio: Float)

/** A stretch of days a habit was paused (a holiday). An open end means it is still going. */
data class VacationSpan(val start: LocalDate, val end: LocalDate?)

/**
 * When a habit is due, as Streak stores it. It is read from the habit's original Streak record
 * (`HabitEntity.raw`), so imported habits and this app's own habits (which keep the same record) work the same.
 */
data class HabitPlan(
    val interval: Int = DAILY,
    /** For weekly and monthly habits: how many days per week or month are needed. */
    val timesPerPeriod: Int = 1,
    /** For "some days of the week": 1 = Monday ... 7 = Sunday. */
    val weekdays: List<Int> = emptyList(),
    /** For "every X": the X. */
    val every: Int = 2,
    val unit: Int = UNIT_DAYS,
    val scheduleStart: LocalDate? = null,
    val fromLastDone: Boolean = false,
    val monthWeekday: Boolean = false,
    /** Weekdays that never count (1 = Monday ... 7 = Sunday). */
    val restDays: List<Int> = emptyList(),
    val vacations: List<VacationSpan> = emptyList(),
) {
    /** How many days one "every X" step covers. A month counts as 31 days here, as in Streak. */
    val spanDays: Int
        get() = when (unit) {
            UNIT_DAYS -> every
            UNIT_WEEKS -> every * 7
            else -> every * 31
        }

    companion object {
        const val DAILY = 0
        const val WEEKLY = 1
        const val MONTHLY = 2
        const val WEEKDAYS = 3
        const val EVERY_X_DAYS = 4

        const val UNIT_DAYS = 0
        const val UNIT_WEEKS = 1
        const val UNIT_MONTHS = 2

        /** Reads the schedule out of a Streak habit record. A record that cannot be read is treated as a daily habit. */
        fun fromRaw(raw: String): HabitPlan = try {
            val o = JSONObject(raw)
            HabitPlan(
                interval = o.optInt("interval", DAILY).coerceIn(DAILY, EVERY_X_DAYS),
                timesPerPeriod = maxOf(1, o.optInt("targetFrequency", 1)),
                weekdays = ints(o, "scheduleWeekdays"),
                every = o.optInt("scheduleEvery", 2),
                unit = o.optInt("scheduleUnit", UNIT_DAYS).coerceIn(UNIT_DAYS, UNIT_MONTHS),
                scheduleStart = o.optString("scheduleStart").takeIf { it.isNotEmpty() }?.let(::dayKey),
                fromLastDone = o.optBoolean("fromLastDone", false),
                monthWeekday = o.optBoolean("monthWeekday", false),
                restDays = ints(o, "restDays"),
                vacations = vacations(o),
            )
        } catch (e: Exception) {
            HabitPlan()
        }

        /** Streak writes days as "04-10-2026". */
        fun dayKey(value: String): LocalDate? = try {
            if (value.length != 10) null
            else LocalDate.of(value.substring(6).toInt(), value.substring(3, 5).toInt(), value.substring(0, 2).toInt())
        } catch (e: Exception) {
            null
        }

        /** Streak writes holiday days as "2026-10-04T00:00:00.000". Only the date part matters. */
        private fun isoDay(value: String): LocalDate? = try {
            LocalDate.parse(value.take(10))
        } catch (e: Exception) {
            null
        }

        private fun ints(o: JSONObject, key: String): List<Int> {
            val array = o.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { array.getInt(it) }
        }

        private fun vacations(o: JSONObject): List<VacationSpan> {
            val array = o.optJSONArray("vacations") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val v = array.optJSONObject(i) ?: return@mapNotNull null
                val start = isoDay(v.optString("start")) ?: return@mapNotNull null
                VacationSpan(start, if (v.isNull("end")) null else isoDay(v.optString("end")))
            }
        }
    }
}

/**
 * Streak's habit rules for one habit: which days it is due, which days count as done, and the streaks.
 * It follows Streak's own calculations so the numbers match. No Android in it, so it can be tested.
 *
 * [counts] holds every record of the habit by day. For a "relapse" habit (kind 1) a record means a relapse that day.
 */
class HabitRules(
    private val kind: Int,
    private val target: Double,
    private val anyAmount: Boolean,
    private val plan: HabitPlan,
    private val counts: Map<LocalDate, Double>,
    createdOn: LocalDate,
    private val today: LocalDate,
    private val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** A tracking-only habit: no streak, no strength, no missed days; every check just counts. Never for an Avoid habit. */
    val tracking: Boolean = false,
    /** The times of the checks of each day (minutes of the day, in order), for days that have them. */
    private val times: Map<LocalDate, List<Int>> = emptyMap(),
) {
    private val relapse: Boolean get() = kind == HabitStats.KIND_RELAPSE

    /** The first day the habit counts from: when it was made, or its earliest record if that is older. */
    val startedAt: LocalDate = counts.keys.minOrNull()?.let { if (it.isBefore(createdOn)) it else createdOn } ?: createdOn

    /** Days that reached the goal, sorted, as day numbers. Used by "every X days from the last time I did it". */
    private val doneDays: LongArray by lazy {
        counts.keys.filter { isCompleted(it) }.map { it.toEpochDay() }.sorted().toLongArray()
    }

    /** Whether one day's amount reaches the goal. */
    fun reaches(count: Double): Boolean =
        if (tracking) count > 0
        else if (kind == HabitStats.KIND_COUNTED && (anyAmount || target <= 0)) count > 0 else count >= target - 1e-9

    /** What was recorded on a day: the amount, or 0 if nothing. For a "relapse" habit a record means a relapse. */
    fun amountOn(day: LocalDate): Double = counts[day] ?: 0.0

    /** Did you do the habit on this day? For a "relapse" habit, a day with no relapse. */
    fun isCompleted(day: LocalDate): Boolean {
        if (day.isAfter(today)) return false
        val amount = counts[day]
        if (relapse) return !day.isBefore(startedAt) && amount == null
        return amount != null && reaches(amount)
    }

    /** The times of the checks on a day, in order. */
    fun timesOn(day: LocalDate): List<Int> = times[day].orEmpty()

    /** The day of the latest check and its last time, or null if there is none. */
    fun lastCheck(): Pair<LocalDate, Int?>? {
        val day = counts.filter { it.value > 0 && !it.key.isAfter(today) }.keys.maxOrNull() ?: return null
        return day to timesOn(day).lastOrNull()
    }

    /** The amounts of every day from [from] to [to], added up. */
    fun totalIn(from: LocalDate, to: LocalDate): Double =
        counts.filter { !it.key.isBefore(from) && !it.key.isAfter(to) }.values.sum()

    fun weekTotal(): Double = totalIn(HabitCalendar.startOfWeek(today, weekStart), today)

    fun monthTotal(): Double = totalIn(today.withDayOfMonth(1), today)

    /** Is the habit due on this day, by its schedule? */
    fun isScheduled(day: LocalDate): Boolean = when (plan.interval) {
        HabitPlan.WEEKDAYS -> day.dayOfWeek.value in plan.weekdays
        HabitPlan.EVERY_X_DAYS -> isEveryXDue(day)
        else -> true
    }

    private fun isEveryXDue(day: LocalDate): Boolean {
        if (plan.every <= 0) return false
        val start = plan.scheduleStart ?: startedAt
        if (day.isBefore(start)) return false
        if (plan.fromLastDone) return dueSinceLastDone(day, start)
        if (plan.unit == HabitPlan.UNIT_MONTHS) {
            val months = (day.year - start.year) * 12 + day.monthValue - start.monthValue
            if (months < 0 || months % plan.every != 0) return false
            if (plan.monthWeekday) return sameWeekdaySlot(day, start)
            val last = day.lengthOfMonth()
            return day.dayOfMonth == minOf(start.dayOfMonth, last)
        }
        return (day.toEpochDay() - start.toEpochDay()) % plan.spanDays == 0L
    }

    /** "The second Tuesday of every month": the same weekday in the same week of the month as the start. */
    private fun sameWeekdaySlot(day: LocalDate, start: LocalDate): Boolean {
        if (day.dayOfWeek != start.dayOfWeek) return false
        val nth = (start.dayOfMonth - 1) / 7
        if (nth < 4) return (day.dayOfMonth - 1) / 7 == nth
        return day.dayOfMonth + 7 > day.lengthOfMonth()
    }

    private fun dueSinceLastDone(day: LocalDate, start: LocalDate): Boolean {
        val last = lastDoneBefore(day.toEpochDay()) ?: return true
        if (last < start.toEpochDay()) return true
        return !day.isBefore(nextDueAfter(LocalDate.ofEpochDay(last)))
    }

    private fun lastDoneBefore(epochDay: Long): Long? {
        var low = 0
        var high = doneDays.size
        while (low < high) {
            val mid = (low + high) / 2
            if (doneDays[mid] < epochDay) low = mid + 1 else high = mid
        }
        return if (low == 0) null else doneDays[low - 1]
    }

    private fun nextDueAfter(done: LocalDate): LocalDate {
        if (plan.unit != HabitPlan.UNIT_MONTHS) return done.plusDays(plan.spanDays.toLong())
        val month = done.withDayOfMonth(1).plusMonths(plan.every.toLong())
        return month.withDayOfMonth(minOf(done.dayOfMonth, month.lengthOfMonth()))
    }

    /** A weekly rest day or a holiday: the day neither helps nor breaks anything. */
    fun isPaused(day: LocalDate): Boolean =
        day.dayOfWeek.value in plan.restDays ||
            plan.vacations.any { !day.isBefore(it.start) && !day.isAfter(it.end ?: today) }

    /** Not due, or paused. */
    fun isOffDay(day: LocalDate): Boolean = !isScheduled(day) || isPaused(day)

    /** A paused day with nothing recorded: it is skipped when counting streaks. */
    fun isNeutral(day: LocalDate): Boolean = !day.isBefore(startedAt) && isPaused(day) && counts[day] == null

    /** Done on the day, or an "every X days" habit done early enough that the day does not need it. */
    fun isSatisfied(day: LocalDate): Boolean = isCompleted(day) || isDoneAhead(day)

    /** The day is not done, but an earlier day inside the same "every X" step was, so nothing is owed. */
    fun isDoneAhead(day: LocalDate): Boolean = !isCompleted(day) && doneAheadOf(day)

    private fun doneAheadOf(day: LocalDate): Boolean {
        val span = plan.spanDays
        if (plan.interval != HabitPlan.EVERY_X_DAYS || span <= 1 || plan.fromLastDone) return false
        var cursor = day
        for (ignored in 1 until span) {
            cursor = cursor.minusDays(1)
            if (cursor.isBefore(startedAt)) return false
            if (isCompleted(cursor)) return true
            if (isScheduled(cursor)) return false
        }
        return false
    }

    private fun periodOf(day: LocalDate): Long =
        if (plan.interval == HabitPlan.WEEKLY) startOfWeek(day).toEpochDay() else day.year * 12L + day.monthValue

    private fun startOfWeek(day: LocalDate): LocalDate {
        val back = (day.dayOfWeek.value - weekStart.value + 7) % 7
        return day.minusDays(back.toLong())
    }

    private val periodDone: Map<Long, Int> by lazy {
        if (plan.interval != HabitPlan.WEEKLY && plan.interval != HabitPlan.MONTHLY) emptyMap()
        else counts.keys.filter { isCompleted(it) }.groupingBy { periodOf(it) }.eachCount()
    }

    /**
     * A day with nothing owed because the week or month already reached its target, or because an "every X days"
     * habit was done recently enough. Such a day is shown as "covered", not as a miss.
     */
    fun isCovered(day: LocalDate): Boolean {
        if (day.isAfter(today) || day.isBefore(startedAt)) return false
        if (plan.interval == HabitPlan.WEEKLY || plan.interval == HabitPlan.MONTHLY) {
            if (isPaused(day) || isCompleted(day)) return false
            return (periodDone[periodOf(day)] ?: 0) >= plan.timesPerPeriod
        }
        val span = plan.spanDays
        if (plan.interval != HabitPlan.EVERY_X_DAYS || span <= 1) return false
        if (isScheduled(day)) return isDoneAhead(day)
        var cursor = day
        for (ignored in 1 until span) {
            cursor = cursor.minusDays(1)
            if (cursor.isBefore(startedAt)) return false
            if (isCompleted(cursor)) return true
            if (isScheduled(cursor)) return false
        }
        return false
    }

    /** Can a reason for missing this day be given? Only a past, due day that was not done and not paused. */
    fun canMiss(day: LocalDate): Boolean =
        !tracking && day.isBefore(today) && !day.isBefore(startedAt) && !isCompleted(day) && !isNeutral(day)

    /** True when there is nothing left to do today: not due, done, or covered. A "relapse" habit is never "done for now". */
    fun isDoneForNow(): Boolean = tracking || !relapse && (isOffDay(today) || isCompleted(today) || isCovered(today))

    /** Days that reached the goal. */
    fun totalCompletions(): Int = if (relapse) 0 else counts.values.count { reaches(it) }

    /** Days without a relapse since the habit started (only meaningful for a "relapse" habit). */
    fun cleanDays(): Int {
        if (!relapse) return 0
        val span = (today.toEpochDay() - startedAt.toEpochDay() + 1).toInt()
        if (span <= 0) return 0
        val relapses = counts.keys.count { !it.isBefore(startedAt) && !it.isAfter(today) }
        return span - relapses
    }

    private fun countIn(from: LocalDate, to: LocalDate): Int {
        var n = 0
        var d = from
        while (!d.isAfter(to)) {
            if (isCompleted(d)) n++
            d = d.plusDays(1)
        }
        return n
    }

    fun currentStreak(): Int {
        if (relapse) return dailyRun()
        if (counts.isEmpty()) return 0
        return when (plan.interval) {
            HabitPlan.WEEKLY -> periodStreakNow(
                startOfPeriod = { startOfWeek(it) },
                endOfPeriod = { it.plusDays(6) },
                previous = { it.minusDays(7) },
            )
            HabitPlan.MONTHLY -> periodStreakNow(
                startOfPeriod = { it.withDayOfMonth(1) },
                endOfPeriod = { it.plusMonths(1).minusDays(1) },
                previous = { it.minusMonths(1) },
            )
            HabitPlan.WEEKDAYS, HabitPlan.EVERY_X_DAYS -> daySpecificCurrent()
            else -> dailyRun()
        }
    }

    /** Counts back from today over completed days, skipping paused ones, until a day was missed. */
    private fun dailyRun(): Int {
        var cursor = today
        if (!relapse && !isCompleted(cursor) && !isNeutral(cursor)) {
            // Today is not over, so a streak survives a day not done yet, but not two.
            cursor = cursor.minusDays(1)
            if (!isCompleted(cursor) && !isNeutral(cursor)) return 0
        }
        var streak = 0
        while (!cursor.isBefore(startedAt)) {
            if (isNeutral(cursor)) {
                // skipped
            } else if (isCompleted(cursor)) {
                streak++
            } else {
                break
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private inline fun periodStreakNow(
        startOfPeriod: (LocalDate) -> LocalDate,
        endOfPeriod: (LocalDate) -> LocalDate,
        previous: (LocalDate) -> LocalDate,
    ): Int {
        var first = startOfPeriod(today)
        var streak = 0
        if (countIn(first, endOfPeriod(first)) >= plan.timesPerPeriod) streak++
        first = previous(first)
        while (countIn(first, endOfPeriod(first)) >= plan.timesPerPeriod) {
            streak++
            first = previous(first)
        }
        return streak
    }

    private fun daySpecificCurrent(): Int {
        var cursor = today
        var streak = 0
        while (!cursor.isBefore(startedAt)) {
            if (isNeutral(cursor) || !isScheduled(cursor)) {
                cursor = cursor.minusDays(1)
                continue
            }
            if (isSatisfied(cursor)) {
                streak++
            } else if (cursor != today) {
                break
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(): Int {
        if (relapse) return dailyBest()
        if (counts.isEmpty()) return 0
        if (plan.interval == HabitPlan.DAILY) return dailyBest()
        val first = counts.keys.minOrNull()!!
        val last = counts.keys.maxOrNull()!!
        return when (plan.interval) {
            HabitPlan.WEEKLY -> periodBest(
                from = startOfWeek(first),
                until = startOfWeek(last).plusDays(6),
                endOfPeriod = { it.plusDays(6) },
                next = { it.plusDays(7) },
            )
            HabitPlan.MONTHLY -> periodBest(
                from = first.withDayOfMonth(1),
                until = last.plusMonths(1).withDayOfMonth(1).minusDays(1),
                endOfPeriod = { it.plusMonths(1).minusDays(1) },
                next = { it.plusMonths(1) },
            )
            else -> daySpecificBest()
        }
    }

    private fun dailyBest(): Int {
        var best = 0
        var run = 0
        var day = startedAt
        while (!day.isAfter(today)) {
            val amount = counts[day]
            val paused = amount == null && isPaused(day)
            if (!paused) {
                val good = if (relapse) amount == null else amount != null && reaches(amount)
                if (good) {
                    run++
                    if (run > best) best = run
                } else {
                    run = 0
                }
            }
            day = day.plusDays(1)
        }
        return best
    }

    private inline fun periodBest(
        from: LocalDate,
        until: LocalDate,
        endOfPeriod: (LocalDate) -> LocalDate,
        next: (LocalDate) -> LocalDate,
    ): Int {
        var start = from
        var best = 0
        var run = 0
        while (!start.isAfter(until)) {
            if (countIn(start, endOfPeriod(start)) >= plan.timesPerPeriod) run++ else run = 0
            if (run > best) best = run
            start = next(start)
        }
        return best
    }

    private fun daySpecificBest(): Int {
        var best = 0
        var run = 0
        var day = startedAt
        while (!day.isAfter(today)) {
            if (!(isNeutral(day) || !isScheduled(day))) {
                if (isSatisfied(day)) {
                    run++
                    if (run > best) best = run
                } else {
                    run = 0
                }
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** How one day looks on the calendar, the way Streak colours it. */
    fun cell(day: LocalDate): HabitCell {
        val before = day.isBefore(startedAt)
        val future = day.isAfter(today)
        if (tracking) {
            return when {
                before -> HabitCell(DayKind.BEFORE, 0f)
                future -> HabitCell(DayKind.FUTURE, 0f)
                (counts[day] ?: 0.0) > 0 -> HabitCell(DayKind.DONE, 1f)
                else -> HabitCell(DayKind.OFF, 0f)
            }
        }
        if (!before && !future && isNeutral(day)) return HabitCell(DayKind.PAUSED, 0f)
        if (relapse) {
            return when {
                before -> HabitCell(DayKind.BEFORE, 0f)
                future -> HabitCell(DayKind.FUTURE, 0f)
                counts[day] != null -> HabitCell(DayKind.RELAPSE, 1f)
                else -> HabitCell(DayKind.CLEAN, 1f)
            }
        }
        if (!before && isCovered(day)) return HabitCell(DayKind.COVERED, 0f)
        val amount = if (future) 0.0 else counts[day] ?: 0.0
        if (amount <= 0) {
            return when {
                before -> HabitCell(DayKind.BEFORE, 0f)
                future || isOffDay(day) -> HabitCell(DayKind.OFF, 0f)
                else -> HabitCell(DayKind.MISSED, 0f)
            }
        }
        val ratio = if (target <= 0) 1.0 else (amount / target).coerceIn(0.25, 1.0)
        return HabitCell(if (reaches(amount)) DayKind.DONE else DayKind.PARTIAL, ratio.toFloat())
    }

    /** How full one day is, 0 to 1. Used for the strength bar. */
    private fun dayValue(day: LocalDate): Double {
        if (day.isBefore(startedAt) || day.isAfter(today)) return 0.0
        if (relapse) return if (counts[day] != null) 0.0 else 1.0
        val amount = counts[day] ?: 0.0
        if (target <= 0) return if (amount > 0) 1.0 else 0.0
        return (amount / target).coerceIn(0.0, 1.0)
    }

    /**
     * How steady the habit has been lately, 0 to 1: the last 90 days, with recent days counting more (the weight
     * halves every 12 days). Days off, paused or covered days are left out. Streak shows it as a bar and a percent.
     */
    fun strength(): Double {
        if (tracking || counts.isEmpty() && !relapse) return 0.0
        var score = 0.0
        var norm = 0.0
        for (i in 0 until 90) {
            val day = today.minusDays(i.toLong())
            if (day.isBefore(startedAt) || !isScheduled(day) || isNeutral(day) || isCovered(day)) continue
            val weight = Math.pow(0.5, i / 12.0)
            norm += weight
            score += weight * dayValue(day)
        }
        return if (norm == 0.0) 0.0 else (score / norm).coerceIn(0.0, 1.0)
    }

    /** The strength as a whole percent, as shown on the card. */
    fun consistency(): Int = Math.round(strength() * 100).toInt()

    /** The numbers shown on a habit's card. */
    fun streaks(): Streaks = Streaks(
        current = currentStreak(),
        best = longestStreak(),
        totalDays = if (relapse) counts.keys.count { !it.isAfter(today) } else totalCompletions(),
    )

    companion object {
        /** Rules for a stored habit, with its records by day. */
        fun of(
            habit: HabitEntity,
            counts: Map<LocalDate, Double>,
            today: LocalDate,
            times: Map<LocalDate, List<Int>> = emptyMap(),
        ): HabitRules =
            HabitRules(
                kind = habit.kind,
                target = habit.target,
                anyAmount = habit.anyAmount,
                plan = HabitPlan.fromRaw(habit.raw),
                counts = counts,
                createdOn = runCatching { LocalDate.parse(habit.startedOn) }.getOrDefault(today),
                today = today,
                tracking = isTracking(habit),
                times = times,
            )

        /** Whether the habit is tracking-only (Streak's `tracking` switch). An Avoid habit never is. */
        fun isTracking(habit: HabitEntity): Boolean =
            habit.kind != HabitStats.KIND_RELAPSE && runCatching { JSONObject(habit.raw).optBoolean("tracking", false) }.getOrDefault(false)
    }
}
