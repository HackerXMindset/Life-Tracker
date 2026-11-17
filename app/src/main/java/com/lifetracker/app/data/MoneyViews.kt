package com.lifetracker.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The stretch of time the Money tab shows: a day, a week (Monday to Sunday), a month or a year. */
enum class Period(val label: String) { Day("Day"), Week("Week"), Month("Month"), Year("Year") }

/** The arithmetic behind the Money tab's views, kept free of Android so it can be tested. */
object MoneyViews {
    private val EN = Locale.ENGLISH

    fun start(period: Period, anchor: LocalDate): LocalDate = when (period) {
        Period.Day -> anchor
        Period.Week -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.Month -> anchor.withDayOfMonth(1)
        Period.Year -> anchor.withDayOfYear(1)
    }

    fun endExclusive(period: Period, anchor: LocalDate): LocalDate {
        val s = start(period, anchor)
        return when (period) {
            Period.Day -> s.plusDays(1)
            Period.Week -> s.plusWeeks(1)
            Period.Month -> s.plusMonths(1)
            Period.Year -> s.plusYears(1)
        }
    }

    fun shift(period: Period, anchor: LocalDate, n: Int): LocalDate = when (period) {
        Period.Day -> anchor.plusDays(n.toLong())
        Period.Week -> anchor.plusWeeks(n.toLong())
        Period.Month -> anchor.plusMonths(n.toLong())
        Period.Year -> anchor.plusYears(n.toLong())
    }

    /** The heading for a period: "Today", "This week · 16–22 Nov", "November 2026", "2026". */
    fun title(period: Period, anchor: LocalDate, today: LocalDate): String {
        val s = start(period, anchor)
        val mmm = DateTimeFormatter.ofPattern("MMM", EN)
        return when (period) {
            Period.Day -> when (anchor) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> anchor.format(DateTimeFormatter.ofPattern("EEE d MMM", EN))
            }
            Period.Week -> {
                val e = s.plusDays(6)
                val range = if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${e.format(mmm)}" else "${s.dayOfMonth} ${s.format(mmm)} – ${e.dayOfMonth} ${e.format(mmm)}"
                if (s == start(Period.Week, today)) "This week · $range" else range
            }
            Period.Month -> s.format(DateTimeFormatter.ofPattern("MMMM yyyy", EN))
            Period.Year -> s.year.toString()
        }
    }

    /** What the period before is called in "12% more than ...". */
    fun previousWord(period: Period): String = when (period) {
        Period.Day -> "the day before"
        Period.Week -> "last week"
        Period.Month -> "last month"
        Period.Year -> "last year"
    }

    /** One bar of a chart: a day (week and month views) or a month (year view). */
    class Bucket(val start: LocalDate, val label: String, val spent: Double, val income: Double)

    private fun parse(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

    private fun spentOf(list: List<MoneyEntryEntity>): Double = list.filter(MoneyStats::countsAgainstAllowance).sumOf { it.amount }

    private fun incomeOf(list: List<MoneyEntryEntity>): Double = list.filter { it.kind == KIND_INCOME }.sumOf { it.amount }

    /** The bars for a period: 7 days, every day of the month, or 12 months. A day has none. */
    fun buckets(period: Period, anchor: LocalDate, entries: List<MoneyEntryEntity>): List<Bucket> {
        val s = start(period, anchor)
        val dated = entries.mapNotNull { e -> parse(e.date)?.let { it to e } }
        return when (period) {
            Period.Day -> emptyList()
            Period.Week, Period.Month -> {
                val days = if (period == Period.Week) 7 else s.lengthOfMonth()
                val byDay = dated.groupBy({ it.first }, { it.second })
                (0 until days).map { i ->
                    val d = s.plusDays(i.toLong())
                    val list = byDay[d].orEmpty()
                    val label = if (period == Period.Week) d.dayOfWeek.getDisplayName(TextStyle.NARROW, EN) else d.dayOfMonth.toString()
                    Bucket(d, label, spentOf(list), incomeOf(list))
                }
            }
            Period.Year -> (0 until 12).map { m ->
                val ms = s.plusMonths(m.toLong())
                val list = dated.filter { YearMonth.from(it.first) == YearMonth.from(ms) }.map { it.second }
                Bucket(ms, ms.month.getDisplayName(TextStyle.NARROW, EN), spentOf(list), incomeOf(list))
            }
        }
    }

    /** A shop or person money went to (or came from), all its entries added together. */
    class Place(val name: String, val total: Double, val count: Int, val categoryId: String)

    private fun placeKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { name.trim().lowercase() }

    /** Entries of one kind grouped by name ("Zomato", "zomato " and "ZOMATO" are one), biggest first. */
    fun places(entries: List<MoneyEntryEntity>, kind: Int): List<Place> =
        entries.filter { it.kind == kind }
            .groupBy { placeKey(it.name) }
            .map { (_, list) ->
                val name = list.groupingBy { it.name.trim() }.eachCount().maxByOrNull { it.value }!!.key
                val category = list.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }!!.key
                Place(name.ifEmpty { "No name" }, list.sumOf { it.amount }, list.size, category)
            }
            .sortedByDescending { it.total }

    /** How much more (positive) or less (negative) than before, as a fraction; null when there was nothing before to compare with. */
    fun change(current: Double, previous: Double): Double? = if (previous <= 0.0) null else (current - previous) / previous

    /** "▲ 12%", "▼ 8%" or "no change". */
    fun changeText(fraction: Double): String {
        val pct = Math.round(Math.abs(fraction) * 100)
        return if (pct == 0L) "no change" else (if (fraction > 0) "▲ " else "▼ ") + pct + "%"
    }

    /** Days of the period that have passed (today counts), so an average is not dragged down by days still to come. */
    fun elapsedDays(period: Period, anchor: LocalDate, today: LocalDate): Int {
        val s = start(period, anchor)
        val e = endExclusive(period, anchor)
        val last = if (today.isBefore(e)) today else e.minusDays(1)
        return if (last.isBefore(s)) 0 else (ChronoUnit.DAYS.between(s, last) + 1).toInt()
    }

    fun dailyAverage(spent: Double, days: Int): Double = if (days <= 0) 0.0 else spent / days

    /** 0 for nothing, then 1 to 4 for how close a day's spending is to the biggest day. */
    fun heatLevel(value: Double, max: Double): Int =
        if (value <= 0.0 || max <= 0.0) 0 else (1 + ((value / max) * 3.999).toInt()).coerceIn(1, 4)

    /** The days of a month laid out in weeks starting on Monday: empty slots (null) before the 1st. */
    fun calendarCells(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value - 1
        return List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }

    /** Where an entry came from, for a small tag; null for one you typed yourself. */
    fun sourceTag(entry: MoneyEntryEntity): String? = when {
        entry.id.startsWith("sms-") -> "SMS"
        entry.id.startsWith("gmail-") -> "Gmail"
        entry.id.startsWith("mbox-") -> "Takeout"
        entry.id.startsWith("auto|") -> "Monthly"
        else -> null
    }

    /** The day that cost most, or null when nothing was spent. */
    fun busiest(buckets: List<Bucket>): Bucket? = buckets.filter { it.spent > 0 }.maxByOrNull { it.spent }

    /** Spending per day (own money only), for a calendar. */
    fun dailySpent(entries: List<MoneyEntryEntity>): Map<LocalDate, Double> =
        entries.filter(MoneyStats::countsAgainstAllowance)
            .mapNotNull { e -> parse(e.date)?.let { it to e.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
}

/** The colour people know a shop by, for its badge. A letter on that colour is easier to find in a list than a coloured dot. */
object BrandColors {
    private val COLORS: Map<String, Long> = mapOf(
        "zomato" to 0xFFE23744, "swiggy" to 0xFFFC8019, "blinkit" to 0xFFF8CB46, "zepto" to 0xFF5A189A, "bigbasket" to 0xFF84C225,
        "dunzo" to 0xFF00D290, "dominos" to 0xFF006491, "mcdonalds" to 0xFFDA291C, "kfc" to 0xFFE4002B, "starbucks" to 0xFF00704A,
        "amazon" to 0xFFFF9900, "flipkart" to 0xFF2874F0, "myntra" to 0xFFFF3F6C, "ajio" to 0xFF2C4152, "nykaa" to 0xFFFC2779,
        "meesho" to 0xFF9F2089, "lenskart" to 0xFF00BAC6, "croma" to 0xFF00E9BF, "uber" to 0xFF000000, "ola" to 0xFFB5C71E,
        "rapido" to 0xFFFFD500, "irctc" to 0xFF1F4E9C, "makemytrip" to 0xFFE53935, "goibibo" to 0xFFEC5B24, "redbus" to 0xFFD84E55,
        "indigo" to 0xFF1D1F9E, "netflix" to 0xFFE50914, "spotify" to 0xFF1DB954, "hotstar" to 0xFF1F80E0, "airtel" to 0xFFE40000,
        "jio" to 0xFF0A2885, "bookmyshow" to 0xFFC4242B, "phonepe" to 0xFF5F259F, "paytm" to 0xFF00BAF2, "pharmeasy" to 0xFF10847E,
        "apple" to 0xFF555555, "google" to 0xFF4285F4,
    )

    /** The brand colour (ARGB) for a name that mentions a known shop, else null. */
    fun of(name: String): Long? {
        for (token in name.lowercase().split(Regex("[^a-z0-9]+"))) {
            if (token.length < 3) continue
            COLORS[token]?.let { return it }
            COLORS.entries.firstOrNull { it.key.length >= 4 && token.startsWith(it.key) }?.let { return it.value }
        }
        return null
    }
}
