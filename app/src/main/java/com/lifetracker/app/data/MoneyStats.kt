package com.lifetracker.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Money arithmetic, kept free of Android so it can be tested. */
object MoneyStats {
    data class Totals(val income: Double, val spent: Double) {
        val net: Double get() = income - spent
    }

    fun totals(entries: List<MoneyEntryEntity>): Totals = Totals(
        income = entries.filter { it.kind == KIND_INCOME }.sumOf { it.amount },
        spent = entries.filter { it.kind == KIND_EXPENSE }.sumOf { it.amount },
    )

    /** Total per category for one kind, biggest first. */
    fun byCategory(entries: List<MoneyEntryEntity>, kind: Int): List<Pair<String, Double>> =
        entries.filter { it.kind == kind }
            .groupBy { it.categoryId }
            .map { (id, list) -> id to list.sumOf { it.amount } }
            .sortedByDescending { it.second }

    fun newId(): String = UUID.randomUUID().toString()

    /** The first day of [month] and of the month after it, as ISO dates, for a "between" lookup. */
    fun range(month: YearMonth): Pair<String, String> =
        month.atDay(1).toString() to month.plusMonths(1).atDay(1).toString()

    /**
     * An amount with the currency symbol: "₹1,25,000" for rupees (Indian digit grouping),
     * "$125,000" otherwise. Whole amounts show no decimals, others show two.
     */
    fun format(value: Double, symbol: String): String {
        val cents = Math.round(Math.abs(value) * 100)
        val whole = (cents / 100).toString()
        val frac = (cents % 100).toInt()
        val grouped = if (symbol == "₹") groupIndian(whole) else groupWestern(whole)
        val body = if (frac == 0) grouped else grouped + "." + frac.toString().padStart(2, '0')
        return (if (value < 0 && cents > 0) "-" else "") + symbol + body
    }

    private fun groupWestern(digits: String): String =
        digits.reversed().chunked(3).joinToString(",").reversed()

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
    }

    /** "5th", "22nd" and so on. */
    fun ordinal(day: Int): String {
        val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
}

/** Works out which monthly items are due to be added as entries. */
object Recurring {
    /** What to add, and the new `lastPosted` to store with the item. */
    data class Plan(val entries: List<MoneyEntryEntity>, val lastPosted: String)

    private const val MAX_MONTHS = 120

    private fun parse(text: String): LocalDate? =
        if (text.isBlank()) null else runCatching { LocalDate.parse(text) }.getOrNull()

    /**
     * Every month from the start (or from the month after the last one added) up to
     * this month, whose charge day has arrived, gets one entry. A charge day the month
     * does not have (the 31st in April) falls on the month's last day. Nothing is added
     * after the end date, before the start date, for a stopped (hidden) item, or in the
     * future. Each entry has a fixed id, so running this twice never doubles anything.
     */
    fun plan(item: MoneyItemEntity, today: LocalDate): Plan {
        val unchanged = Plan(emptyList(), item.lastPosted)
        if (item.type != MoneyItemEntity.MONTHLY || item.archived || item.chargeDay !in 1..31) return unchanged
        val start = parse(item.startDate) ?: return unchanged
        val end = parse(item.endDate)

        val lastMonth = YearMonth.from(today)
        var month = if (item.lastPosted.isNotBlank()) {
            runCatching { YearMonth.parse(item.lastPosted).plusMonths(1) }.getOrNull() ?: return unchanged
        } else {
            YearMonth.from(start)
        }
        if (month.plusMonths(MAX_MONTHS.toLong()).isBefore(lastMonth)) {
            month = lastMonth.minusMonths(MAX_MONTHS - 1L)
        }

        val out = ArrayList<MoneyEntryEntity>()
        var lastPosted = item.lastPosted
        while (!month.isAfter(lastMonth)) {
            val date = month.atDay(minOf(item.chargeDay, month.lengthOfMonth()))
            if (date.isAfter(today)) break
            if (end != null && date.isAfter(end)) break
            if (!date.isBefore(start)) {
                out += MoneyEntryEntity(
                    id = "auto|${item.id}|$month",
                    date = date.toString(),
                    kind = item.kind,
                    categoryId = item.categoryId,
                    name = item.name,
                    amount = item.amount,
                    note = "",
                    itemId = item.id,
                )
            }
            lastPosted = month.toString()
            month = month.plusMonths(1)
        }
        return Plan(out, lastPosted)
    }
}
