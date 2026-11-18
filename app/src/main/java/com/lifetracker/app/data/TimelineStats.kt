package com.lifetracker.app.data

/** The numbers that can sit in the box above the Timeline, and how the choice is saved. No Android in here, so it can be tested. */
object TimelineStats {
    enum class Cell(val key: String, val label: String, val hint: String) {
        STEPS("steps", "Steps", "Steps walked that day"),
        PHONE("phone", "Phone", "Time on the phone"),
        STUDIED("studied", "Studied", "Logged study plus apps linked to Study"),
        SLEEP("sleep", "Sleep", "How long you slept that night"),
        CALLS("calls", "Calls", "How many calls you had"),
        FOOD("food", "Food", "Calories you logged"),
        PLACES("places", "Places", "How many places you stayed at"),
    }

    val DEFAULT = listOf(Cell.STEPS, Cell.PHONE, Cell.STUDIED)

    /** The cells in the order of [Cell], whatever order [text] lists them. Null (never chosen) gives [DEFAULT]; "" gives none. */
    fun parse(text: String?): List<Cell> {
        if (text == null) return DEFAULT
        val keys = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return Cell.entries.filter { it.key in keys }
    }

    fun encode(cells: Collection<Cell>): String = Cell.entries.filter { it in cells }.joinToString(",") { it.key }
}
