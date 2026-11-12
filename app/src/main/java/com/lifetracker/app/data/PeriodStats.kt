package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The stretches of time the stats can show. */
enum class StatPeriod(val key: String, val label: String) {
    WEEK("week", "Week"),
    MONTH("month", "Month"),
    DAYS90("90days", "90 days"),
    ALL("all", "All time");

    companion object {
        fun fromKey(key: String?): StatPeriod = entries.firstOrNull { it.key == key } ?: WEEK
    }
}

/** From [from] to [to], both included. */
data class StatSpan(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(1)

    fun dates(): List<LocalDate> = List(days) { from.plusDays(it.toLong()) }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && !date.isAfter(to)
}

/** Which days a period covers, and how to move to the one before or after. Free of the screen, so it can be tested. */
object StatSpans {
    /**
     * The days of [period] around [anchor]: its Monday-to-Sunday week, its calendar month, the 90 days that end on [anchor] (never
     * after [today]), or everything from [earliest] up to [today].
     */
    fun span(period: StatPeriod, anchor: LocalDate, today: LocalDate, earliest: LocalDate?): StatSpan = when (period) {
        StatPeriod.WEEK -> {
            val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            StatSpan(monday, monday.plusDays(6))
        }
        StatPeriod.MONTH -> YearMonth.from(anchor).let { StatSpan(it.atDay(1), it.atEndOfMonth()) }
        StatPeriod.DAYS90 -> {
            val end = if (anchor.isAfter(today)) today else anchor
            StatSpan(end.minusDays(89), end)
        }
        StatPeriod.ALL -> StatSpan(minOf(earliest ?: today, today), today)
    }

    /** The anchor of the period [by] steps away (negative is earlier). All time does not move. */
    fun shift(period: StatPeriod, anchor: LocalDate, by: Int): LocalDate = when (period) {
        StatPeriod.WEEK -> anchor.plusWeeks(by.toLong())
        StatPeriod.MONTH -> anchor.plusMonths(by.toLong())
        StatPeriod.DAYS90 -> anchor.plusDays(90L * by)
        StatPeriod.ALL -> anchor
    }

    /** False once the period already reaches today: there is nothing later to look at. */
    fun canGoLater(period: StatPeriod, anchor: LocalDate, today: LocalDate): Boolean =
        period != StatPeriod.ALL && span(period, anchor, today, null).to.isBefore(today)

    /** False once the period starts at or before your first logged day. */
    fun canGoEarlier(period: StatPeriod, anchor: LocalDate, today: LocalDate, earliest: LocalDate?): Boolean =
        period != StatPeriod.ALL && earliest != null && span(period, anchor, today, earliest).from.isAfter(earliest)

    fun title(period: StatPeriod, span: StatSpan, today: LocalDate): String {
        val loc = Locale.getDefault()
        fun short(d: LocalDate) = d.dayOfMonth.toString() + " " + d.month.getDisplayName(TextStyle.SHORT, loc)
        return when (period) {
            StatPeriod.WEEK -> if (today in span) "This week" else short(span.from) + " to " + short(span.to)
            StatPeriod.MONTH ->
                if (today in span) "This month" else span.from.month.getDisplayName(TextStyle.FULL, loc) + " " + span.from.year
            StatPeriod.DAYS90 -> if (span.to == today) "Last 90 days" else short(span.from) + " to " + short(span.to)
            StatPeriod.ALL -> "All time"
        }
    }
}

/** Everything the stats window shows for one period, worked out from the meals and checks. No Android in it. */
object PeriodStats {
    class DayStat(val date: LocalDate, val meals: List<MealEntity>, val goal: FoodGoalEntity?) {
        val kcal: Double get() = meals.sumOf { it.kcal }
        val protein: Double get() = meals.sumOf { it.protein }
        val carbs: Double get() = meals.sumOf { it.carbs }
        val fat: Double get() = meals.sumOf { it.fat }
        val logged: Boolean get() = meals.isNotEmpty()
    }

    /** One point of the chart: a day, a week or a month, with its average calories (null when nothing was logged in it). */
    class Point(val label: String, val kcal: Double?, val from: LocalDate, val to: LocalDate)

    class HabitRow(val habitId: String, val daysDone: Int, val checks: Int, val last: LocalDate?)

    class FoodCount(val name: String, val times: Int, val last: LocalDate, val foodId: String = "")

    /** The share of calories from each macro, as 0..1 values that add up to 1. */
    class MacroShare(val carbs: Double, val protein: Double, val fat: Double)

    class Result(
        val span: StatSpan,
        val days: List<DayStat>,
        /** Days of the span up to today. */
        val elapsedDays: Int,
        val loggedDays: Int,
        val avgKcal: Double,
        val avgProtein: Double,
        val avgCarbs: Double,
        val avgFat: Double,
        val macros: MacroShare?,
        /** The calorie goal on the last day of the span, when there is one. */
        val goalKcal: Double?,
        /** Logged days with a calorie goal, and how many of them were within 10 % of it. */
        val goalDays: Int,
        val daysInBand: Int,
        val highest: DayStat?,
        val lowest: DayStat?,
        val weightFirst: Double?,
        val weightLast: Double?,
        val points: List<Point>,
        val habits: List<HabitRow>,
        val topFoods: List<FoodCount>,
    ) {
        /** Days up to today with nothing logged. They are left out of every average. */
        val emptyDays: Int get() = elapsedDays - loggedDays
        val weightChange: Double? get() = if (weightFirst != null && weightLast != null) weightLast - weightFirst else null

        /** How far the average is above (positive) or below the goal, as a share of the goal; null without a goal. */
        val avgVsGoal: Double? get() = goalKcal?.takeIf { it > 0 && loggedDays > 0 }?.let { (avgKcal - it) / it }
    }

    fun build(
        span: StatSpan,
        today: LocalDate,
        meals: List<MealEntity>,
        goalFor: (String) -> FoodGoalEntity?,
        weights: List<WeightEntity>,
        checks: List<TrackerEventEntity>,
        habitOrder: List<String>,
    ): Result {
        val byDay = meals.filter { it.date >= span.from.toString() && it.date <= span.to.toString() }.groupBy { it.date }
        val days = span.dates().map { d -> DayStat(d, byDay[d.toString()].orEmpty(), goalFor(d.toString())) }
        val logged = days.filter { it.logged }
        val n = logged.size
        val avg = { f: (DayStat) -> Double -> if (n == 0) 0.0 else logged.sumOf(f) / n }

        val carbsK = logged.sumOf { it.carbs } * 4
        val proteinK = logged.sumOf { it.protein } * 4
        val fatK = logged.sumOf { it.fat } * 9
        val macroTotal = carbsK + proteinK + fatK
        val macros = if (macroTotal > 0) MacroShare(carbsK / macroTotal, proteinK / macroTotal, fatK / macroTotal) else null

        val withGoal = logged.filter { (it.goal?.kcal ?: 0.0) > 0 }
        val inBand = withGoal.count { Math.abs(it.kcal - it.goal!!.kcal) <= it.goal.kcal * 0.10 }

        val range = days.map { it.date.toString() }.toSet()
        val weighed = weights.filter { it.date in range }.sortedBy { it.date }

        val inSpan = checks.filter { it.date in range }
        val habitRows = habitOrder.map { id ->
            val mine = inSpan.filter { it.trackerId == id }
            HabitRow(id, mine.map { it.date }.distinct().size, mine.size, mine.maxOfOrNull { it.date }?.let { LocalDate.parse(it) })
        }

        val foods = meals.filter { it.date in range }
            .groupBy { it.name.trim().lowercase() }
            .map { (_, list) -> FoodCount(list.first().name.trim(), list.size, list.maxOf { LocalDate.parse(it.date) }, list.firstOrNull { it.foodId.isNotBlank() }?.foodId.orEmpty()) }
            .sortedWith(compareBy({ -it.times }, { it.name.lowercase() }))
            .take(6)

        return Result(
            span = span,
            days = days,
            elapsedDays = days.count { !it.date.isAfter(today) },
            loggedDays = n,
            avgKcal = avg { it.kcal },
            avgProtein = avg { it.protein },
            avgCarbs = avg { it.carbs },
            avgFat = avg { it.fat },
            macros = macros,
            goalKcal = goalFor(span.to.toString())?.kcal?.takeIf { it > 0 },
            goalDays = withGoal.size,
            daysInBand = inBand,
            highest = if (n >= 2) logged.maxByOrNull { it.kcal } else null,
            lowest = if (n >= 2) logged.minByOrNull { it.kcal } else null,
            weightFirst = weighed.firstOrNull()?.takeIf { weighed.size >= 2 }?.kg,
            weightLast = weighed.lastOrNull()?.takeIf { weighed.size >= 2 }?.kg,
            points = points(span, days, today),
            habits = habitRows,
            topFoods = foods,
        )
    }

    /**
     * The chart: one point a day for up to 31 days, one a week up to about four months, otherwise one a month. A point is the
     * average of the days with food in it, so a day with nothing logged is a gap, never a zero.
     */
    fun points(span: StatSpan, days: List<DayStat>, today: LocalDate): List<Point> {
        val loc = Locale.getDefault()
        val groups: List<Triple<LocalDate, LocalDate, List<DayStat>>> = when {
            span.days <= 31 -> days.map { Triple(it.date, it.date, listOf(it)) }
            span.days <= 125 -> {
                val out = ArrayList<Triple<LocalDate, LocalDate, List<DayStat>>>()
                var start = span.from.minusDays((span.from.dayOfWeek.value - 1).toLong())
                while (!start.isAfter(span.to)) {
                    val end = start.plusDays(6)
                    out.add(Triple(start, end, days.filter { it.date in StatSpan(start, end) }))
                    start = start.plusDays(7)
                }
                out
            }
            else -> {
                val out = ArrayList<Triple<LocalDate, LocalDate, List<DayStat>>>()
                var month = YearMonth.from(span.from)
                while (!month.isAfter(YearMonth.from(span.to))) {
                    out.add(Triple(month.atDay(1), month.atEndOfMonth(), days.filter { YearMonth.from(it.date) == month }))
                    month = month.plusMonths(1)
                }
                out
            }
        }
        val step = Math.ceil(groups.size / 6.0).toInt().coerceAtLeast(1)
        return groups.mapIndexed { i, (from, to, list) ->
            val logged = list.filter { it.logged && !it.date.isAfter(today) }
            val label = when {
                groups.size <= 7 -> from.dayOfWeek.getDisplayName(TextStyle.NARROW, loc)
                i % step != 0 -> ""
                span.days <= 31 -> from.dayOfMonth.toString()
                span.days <= 125 -> from.dayOfMonth.toString() + "/" + from.monthValue
                else -> from.month.getDisplayName(TextStyle.SHORT, loc)
            }
            Point(label, if (logged.isEmpty()) null else logged.sumOf { it.kcal } / logged.size, from, to)
        }
    }
}
