package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth

/** The three ways to look at the days of the Food tab. [key] is what is remembered on the phone. */
enum class FoodView(val key: String, val label: String) {
    WEEK("week", "Week"),
    MONTH("month", "Month"),
    TIMELINE("timeline", "Timeline");

    companion object {
        /** The view for a remembered [key]; the week for anything unknown, since that is how the tab always looked. */
        fun fromKey(key: String?): FoodView = entries.firstOrNull { it.key == key } ?: WEEK
    }
}

/** What the month grid needs, free of the screen so it can be tested. */
object MonthMath {
    /**
     * The habits ticked on each day. [order] is the picked habits in their order, [shown] the ones switched on in the month view.
     * A habit counts once a day however many times it was checked. Days with no tick are not in the map.
     */
    fun ticks(events: List<TrackerEventEntity>, order: List<String>, shown: Set<String>): Map<String, List<String>> {
        val byDay = events.filter { it.trackerId in shown }.groupBy({ it.date }, { it.trackerId })
        return byDay.mapValues { (_, ids) ->
            val seen = ids.toSet()
            order.filter { it in seen }
        }.filterValues { it.isNotEmpty() }
    }

    /** First and last day of [month] as text, for a query. */
    fun span(month: YearMonth): Pair<String, String> = month.atDay(1).toString() to month.atEndOfMonth().toString()

    /** [month] moved by [by] months. */
    fun shift(month: YearMonth, by: Int): YearMonth = month.plusMonths(by.toLong())

    /** The month that holds [date]. */
    fun of(date: LocalDate): YearMonth = YearMonth.from(date)
}

/** One line of the timeline: a meal or a habit check, both with a minute of the day. */
sealed interface TimelineEntry {
    val minute: Int

    data class Meal(val meal: MealEntity) : TimelineEntry {
        override val minute: Int get() = meal.minuteOfDay
    }

    data class Check(val event: TrackerEventEntity) : TimelineEntry {
        override val minute: Int get() = event.minuteOfDay
    }
}

object TimelineMath {
    /**
     * The meals and checks of a day in the order they happened. A check with no time ([HabitMarks.NO_TIME]) goes first, since
     * nothing says when it was. At the same minute a meal comes before a check.
     */
    fun entries(day: DiaryDay): List<TimelineEntry> {
        val all = day.meals.map<MealEntity, TimelineEntry> { TimelineEntry.Meal(it) } +
            day.events.map<TrackerEventEntity, TimelineEntry> { TimelineEntry.Check(it) }
        return all.sortedWith(compareBy<TimelineEntry>({ it.minute }, { if (it is TimelineEntry.Meal) 0 else 1 }))
    }
}
