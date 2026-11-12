package com.lifetracker.app.data

import java.time.LocalDate

/**
 * "What came before": which foods were eaten in the hours before a habit you watch (a headache, reflux, a bad stomach). Each food
 * is compared with how often it is eaten at any time, so a food you eat all day does not look suspicious just for being common.
 * This finds patterns to try changing, not causes. Free of the screen so it can be tested.
 */
object FoodPatterns {
    /** The hours before a check that are looked at. */
    val WINDOWS = listOf(3, 6, 12, 24, 48)

    /** Checks closer together than this are one episode (a headache ticked three times in an afternoon is one). */
    const val MERGE_MINUTES = 120

    /** At least this many episodes with food logged before them, and a food in at least this many of them, to say anything. */
    const val MIN_EPISODES = 3
    const val MIN_TIMES = 2

    /** The times of day used to sample "any time" for comparison, in minutes: every three hours. */
    private val CONTROL_MINUTES = listOf(180, 360, 540, 720, 900, 1080, 1260, 1439)

    class Row(
        val name: String,
        /** In how many episodes it was eaten within the window. */
        val before: Int,
        /** [before] as a share of the episodes with food logged. */
        val share: Double,
        /** How often it is eaten within a window at other times, same measure. */
        val baseline: Double,
        /** The typical time from the food to the check, in hours. */
        val medianGapHours: Double,
    )

    class Result(
        /** Checks that had a time, in the span. */
        val checks: Int,
        /** Episodes after merging close checks. */
        val episodes: Int,
        /** Episodes with at least one food logged in the window. Only these are counted. */
        val withFood: Int,
        val rows: List<Row>,
        val enough: Boolean,
    )

    /** Minutes since the first day of the calendar, for ordering and distances. */
    fun stamp(date: String, minute: Int): Long = LocalDate.parse(date).toEpochDay() * 1440 + minute

    /** The checks with a time as stamps, close ones merged into the first of them, in order. */
    fun episodes(checks: List<TrackerEventEntity>): List<Long> {
        val stamps = checks.filter { it.minuteOfDay >= 0 }.map { stamp(it.date, it.minuteOfDay) }.sorted()
        val out = ArrayList<Long>()
        for (s in stamps) if (out.isEmpty() || s - out.last() > MERGE_MINUTES) out.add(s)
        return out
    }

    private fun fold(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")

    /** True when a sorted [times] array has a value in (from, to]. */
    private fun anyIn(times: LongArray, from: Long, to: Long): Boolean {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= from) lo = mid + 1 else hi = mid
        }
        return lo < times.size && times[lo] <= to
    }

    /** The latest value in (from, to], or null. */
    private fun latestIn(times: LongArray, from: Long, to: Long): Long? {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= to) lo = mid + 1 else hi = mid
        }
        val i = lo - 1
        return if (i >= 0 && times[i] > from) times[i] else null
    }

    fun build(meals: List<MealEntity>, checks: List<TrackerEventEntity>, windowHours: Int, from: LocalDate, to: LocalDate): Result {
        val span = windowHours * 60L
        val fromS = from.toString()
        val toS = to.toString()
        val inSpanChecks = checks.filter { it.date in fromS..toS && it.minuteOfDay >= 0 }
        val episodes = episodes(inSpanChecks)

        val byFood = HashMap<String, MutableList<Long>>()
        val names = HashMap<String, String>()
        val allStamps = ArrayList<Long>()
        // meals from a window before the span started count too, so the first checks have something before them
        val lead = from.minusDays(windowHours / 24L + 1).toString()
        for (m in meals) {
            if (m.date < lead || m.date > toS) continue
            val s = stamp(m.date, m.minuteOfDay)
            val key = fold(m.name)
            byFood.getOrPut(key) { ArrayList() }.add(s)
            names.putIfAbsent(key, m.name.trim())
            allStamps.add(s)
        }
        val foods = byFood.mapValues { (_, v) -> v.sorted().toLongArray() }
        val every = allStamps.sorted().toLongArray()

        val before = HashMap<String, Int>()
        val gaps = HashMap<String, MutableList<Long>>()
        var withFood = 0
        for (t in episodes) {
            if (!anyIn(every, t - span, t)) continue
            withFood++
            for ((key, times) in foods) {
                val last = latestIn(times, t - span, t) ?: continue
                before.merge(key, 1, Int::plus)
                gaps.getOrPut(key) { ArrayList() }.add(t - last)
            }
        }
        val enough = withFood >= MIN_EPISODES
        if (!enough) return Result(inSpanChecks.size, episodes.size, withFood, emptyList(), false)

        // How often each food is in a window at other times: windows ending every three hours of every day of the span.
        var controls = 0
        val candidates = before.filterValues { it >= MIN_TIMES }.keys
        val seen = candidates.associateWith { 0 }.toMutableMap()
        var day = from
        while (!day.isAfter(to)) {
            val base = day.toEpochDay() * 1440
            for (m in CONTROL_MINUTES) {
                val t = base + m
                if (!anyIn(every, t - span, t)) continue
                controls++
                for (key in candidates) if (anyIn(foods.getValue(key), t - span, t)) seen[key] = seen.getValue(key) + 1
            }
            day = day.plusDays(1)
        }

        val rows = candidates.map { key ->
            val share = before.getValue(key).toDouble() / withFood
            val baseline = if (controls == 0) 0.0 else seen.getValue(key).toDouble() / controls
            val g = gaps.getValue(key).sorted()
            Row(names.getValue(key), before.getValue(key), share, baseline, g[g.size / 2] / 60.0)
        }.filter { it.share > it.baseline }
            .sortedWith(compareBy({ -(it.share - it.baseline) }, { -it.before }, { it.name.lowercase() }))
            .take(8)
        return Result(inSpanChecks.size, episodes.size, withFood, rows, true)
    }
}
