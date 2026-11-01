package com.lifetracker.app.data

/**
 * Every table in the app, and whether it is copied to the second phone.
 *
 * A test reads the database source and fails when a table is added or removed here or there, so a new table can never
 * be forgotten: it has to be sorted into "synced" or "not synced" on purpose.
 */
object SyncTables {
    /** One table: its name in the database, the columns that make a row unique, and whether it is synced. */
    class Table(val name: String, val keys: List<String>, val synced: Boolean, val note: String)

    /** Joins the parts of a composite key into one text. The separator cannot appear in any of our ids. */
    const val KEY_SEPARATOR = '\u001F'

    fun rowKey(parts: List<String>): String = parts.joinToString(KEY_SEPARATOR.toString())

    fun keyParts(rowKey: String): List<String> = rowKey.split(KEY_SEPARATOR)

    private fun t(name: String, vararg keys: String, note: String = "") = Table(name, keys.toList(), true, note)
    private fun no(name: String, vararg keys: String, note: String) = Table(name, keys.toList(), false, note)

    /**
     * In the order a second phone should apply a first copy: small things you set up first (places, habits, categories),
     * then what happened, then the big tables last, so the screens have something to show early.
     */
    val all: List<Table> = listOf(
        t("categories", "id"),
        t("activity_types", "id"),
        t("place_categories", "id"),
        t("places", "id"),
        t("tracking_profiles", "id"),
        t("map_styles", "id", note = "Your own map styles, so the second phone shows the same map."),
        t("habits", "id"),
        t("todo_tags", "id"),
        t("todos", "id"),
        t("notes", "id"),
        t("money_categories", "id"),
        t("money_items", "id"),
        t("food_goals", "date"),
        t("usage_apps", "pkg"),
        t("usage_combos", "id"),
        t("entries", "id", note = "Timeline entries; ids are numbers made on the main phone and are kept as they are."),
        t("habit_completions", "habitId", "date"),
        t("meals", "id"),
        t("water_log", "date"),
        t("money_entries", "id"),
        t("focus_sessions", "id"),
        t("calls", "id"),
        t("charge_sessions", "id"),
        t("step_days", "date"),
        t("sleep_nights", "date"),
        t("usage_sessions", "id"),
        t("usage_states", "id", note = "Stretches when the phone was off or shut down."),
        t("place_visits", "id"),
        t("trips", "id"),
        t("trip_edits", "id"),
        t("timeline_edits", "kind", "refId"),
        t("point_notes", "ms"),
        t("trip_points", "tripId", "ms"),
        t("raw_fixes", "ms", note = "Every GPS reading; sent in compressed batches, by day."),
        no("imported_items", "source", "sourceId", note = "Bookkeeping so a Streak or OpenNutriTracker import never doubles; the second phone never imports."),
        no("map_areas", "id", note = "Offline map areas live as files on one phone, so they cannot be copied as rows."),
    )

    val synced: List<Table> get() = all.filter { it.synced }

    fun byName(name: String): Table? = all.firstOrNull { it.name == name }

    /** The tables that must be sent when everything is sent again, in order. */
    val syncedNames: List<String> get() = synced.map { it.name }
}
