package com.lifetracker.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup file holds, once it has been read and checked. */
data class BackupData(
    val version: Int,
    val exportedAt: String,
    val entries: List<EntryEntity>,
    val habits: List<HabitEntity> = emptyList(),
    val completions: List<CompletionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val todoTags: List<TodoTagEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val imported: List<ImportedItemEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList(),
    val foodGoals: List<FoodGoalEntity> = emptyList(),
    val water: List<WaterEntity> = emptyList(),
    val activityTypes: List<ActivityTypeEntity> = emptyList(),
    val moneyCategories: List<MoneyCategoryEntity> = emptyList(),
    val moneyItems: List<MoneyItemEntity> = emptyList(),
    val moneyEntries: List<MoneyEntryEntity> = emptyList(),
    val usageSessions: List<UsageSessionEntity> = emptyList(),
    val usageApps: List<UsageAppEntity> = emptyList(),
    val usageCombos: List<UsageComboEntity> = emptyList(),
    val usageStates: List<UsageStateEntity> = emptyList(),
    val calls: List<CallEntity> = emptyList(),
    val chargeSessions: List<ChargeSessionEntity> = emptyList(),
    val stepDays: List<StepDayEntity> = emptyList(),
    val sleepNights: List<SleepNightEntity> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val placeVisits: List<PlaceVisitEntity> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val tripPoints: List<TripPointEntity> = emptyList(),
    val rawFixes: List<RawFixEntity> = emptyList(),
    val timelineEdits: List<TimelineEditEntity> = emptyList(),
    val placeCategories: List<PlaceCategoryEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val tripEdits: List<TripEditEntity> = emptyList(),
    val pointNotes: List<PointNoteEntity> = emptyList(),
    val trackingProfiles: List<TrackingProfileEntity> = emptyList(),
    val mapStyles: List<MapStyleEntity> = emptyList(),
    val rules: List<RuleEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = entries.isEmpty() && habits.isEmpty() && completions.isEmpty() && categories.isEmpty() &&
            todoTags.isEmpty() && todos.isEmpty() && notes.isEmpty() && meals.isEmpty() && water.isEmpty() &&
            moneyItems.isEmpty() && moneyEntries.isEmpty() && usageSessions.isEmpty() && calls.isEmpty() &&
            chargeSessions.isEmpty() && stepDays.isEmpty() && sleepNights.isEmpty() && places.isEmpty() && placeVisits.isEmpty() &&
            trips.isEmpty() && tripPoints.isEmpty() && rawFixes.isEmpty() && timelineEdits.isEmpty() && usageCombos.isEmpty() && usageStates.isEmpty() && focusSessions.isEmpty() &&
            tripEdits.isEmpty() && pointNotes.isEmpty() && trackingProfiles.isEmpty() && mapStyles.isEmpty() && rules.isEmpty()
}

/**
 * The backup file format: plain JSON you can open in any text editor.
 *
 * `version` lets later steps add more data without breaking old backups.
 * Version 1 held only Timeline entries; version 2 adds habits, to-dos, notes
 * and the record of what was imported; version 3 adds meals, food goals and water; version 4 adds your own activities and their goals; version 5 adds money (categories, saved items and entries); version 6 adds phone usage (sessions are stored compactly as [app, start, end]); version 7 adds phone calls (each stored as [number, name, type, start, seconds]); version 8 adds charging sessions (each stored as [start, end, startLevel, endLevel, plug, source, samples, currentSamples, sumMa, sumMv, ongoing]); version 9 adds steps (each day stored as [date, steps, source]) and sleep (each night stored as [date, start, end, source]); version 10 adds places (the ones you named) and place visits (each stored as [id, place, start, end, lat, lng, source, ongoing, ignored]); version 11 adds trips (each stored as [id, start, end, from, to, distance, medianKmh, topKmh, activity, mode, modeSource]) and the position readings of each trip (each stored as [trip, time, lat, lng, accuracy]); version 12 adds every position reading, stored compactly one day at a time (see rawFixesJson); version 13 adds the speed the phone measured to each reading; version 14 adds how sure the app is of each visit (a tenth value in each visit list); version 15 adds what you changed on the Timeline (each stored as [kind, id, start, end, category, hidden, changedAt, label]); version 16 adds the app combinations you named (each stored as an object with id, name, pkgs, activityId and updatedMs); version 17 adds the stretches when the phone was off or its screen stayed off (each stored as [kind, start, end]); version 18 adds the step counter to each trip reading (a sixth value, -1 when not known); version 19 adds an icon to each activity and each app (an "icon" text, left out when there is none); version 21 adds the place categories (each stored as an object with id, name, color, icon, sortOrder and archived); version 22 adds "deduct it from" to money entries and saved items ("source" 0 mine, 1 someone else, 2 not sure, and "paidBy"; left out when it is mine) and an icon to money categories; version 23 adds "deletedAt" and "clearedAt" to habits and "deletedAt" to notes (each left out when 0); version 24 adds the check times of a habit day ("marks"); version 25 adds the ticked steps of a checklist day ("steps"); version 26 adds the focus sessions (each an object with id, habitId, targetMinutes, seconds, completed, startedAtMs, counted and label); version 27 adds the Places and trips remake: more values on each position reading (direction, altitude, battery, steps, source, profile; see rawFixesJson), pause-zone settings on places (left out when off), the edits of trips ("tripEdits"), notes on readings ("pointNotes") and tracking profiles ("trackingProfiles"). A file from a newer version of the app
 * is refused rather than half-read.
 */
object BackupCodec {
    const val APP_NAME = "life-tracker"
    const val FORMAT_VERSION = 29

    fun encode(data: BackupData): String =
        JSONObject()
            .put("app", APP_NAME)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", data.exportedAt)
            .put("entries", array(data.entries, ::entryJson))
            .put("habits", array(data.habits, ::habitJson))
            .put("completions", array(data.completions, ::completionJson))
            .put("categories", array(data.categories, ::categoryJson))
            .put("todoTags", array(data.todoTags, ::todoTagJson))
            .put("todos", array(data.todos, ::todoJson))
            .put("notes", array(data.notes, ::noteJson))
            .put("imported", array(data.imported, ::importedJson))
            .put("meals", array(data.meals, ::mealJson))
            .put("foodGoals", array(data.foodGoals, ::goalJson))
            .put("water", array(data.water, ::waterJson))
            .put("activityTypes", array(data.activityTypes, ::activityJson))
            .put("moneyCategories", array(data.moneyCategories, ::moneyCategoryJson))
            .put("moneyItems", array(data.moneyItems, ::moneyItemJson))
            .put("moneyEntries", array(data.moneyEntries, ::moneyEntryJson))
            .put("usageSessions", sessionsJson(data.usageSessions))
            .put("usageApps", array(data.usageApps, ::usageAppJson))
            .put("calls", callsJson(data.calls))
            .put("chargeSessions", chargeJson(data.chargeSessions))
            .put("stepDays", stepsJson(data.stepDays))
            .put("sleepNights", sleepJson(data.sleepNights))
            .put("places", array(data.places, ::placeJson))
            .put("placeCategories", array(data.placeCategories, ::placeCategoryJson))
            .put("placeVisits", visitsJson(data.placeVisits))
            .put("trips", tripsJson(data.trips))
            .put("tripPoints", tripPointsJson(data.tripPoints))
            .put("rawFixes", rawFixesJson(data.rawFixes))
            .put("timelineEdits", editsJson(data.timelineEdits))
            .put("usageCombos", array(data.usageCombos, ::usageComboJson))
            .put("usageStates", statesJson(data.usageStates))
            .put("focusSessions", array(data.focusSessions, ::focusJson))
            .put("tripEdits", array(data.tripEdits, ::tripEditJson))
            .put("pointNotes", array(data.pointNotes, ::pointNoteJson))
            .put("trackingProfiles", array(data.trackingProfiles, ::profileJson))
            .put("mapStyles", array(data.mapStyles, ::mapStyleJson))
            .put("rules", array(data.rules, ::ruleJson))
            .toString(2)

    /** Reads a backup. Throws [IllegalArgumentException] with a readable reason if it is not usable. */
    fun decode(text: String): BackupData {
        try {
            val root = JSONObject(text)
            require(root.optString("app") == APP_NAME) { "This is not a Life Tracker backup." }
            val version = root.optInt("version", -1)
            require(version in 1..FORMAT_VERSION) {
                "This backup was made by a newer version of the app. Update the app first."
            }
            return BackupData(
                version = version,
                exportedAt = root.optString("exportedAt"),
                entries = list(root, "entries", required = true, ::entry),
                habits = list(root, "habits", required = false, ::habit),
                completions = list(root, "completions", required = false, ::completion),
                categories = list(root, "categories", required = false, ::category),
                todoTags = list(root, "todoTags", required = false, ::todoTag),
                todos = list(root, "todos", required = false, ::todo),
                notes = list(root, "notes", required = false, ::note),
                imported = list(root, "imported", required = false, ::imported),
                meals = list(root, "meals", required = false, ::meal),
                foodGoals = list(root, "foodGoals", required = false, ::goal),
                water = list(root, "water", required = false, ::waterRow),
                activityTypes = list(root, "activityTypes", required = false, ::activity),
                moneyCategories = list(root, "moneyCategories", required = false, ::moneyCategory),
                moneyItems = list(root, "moneyItems", required = false, ::moneyItem),
                moneyEntries = list(root, "moneyEntries", required = false, ::moneyEntry),
                usageSessions = sessions(root),
                usageApps = list(root, "usageApps", required = false, ::usageApp),
                calls = calls(root),
                chargeSessions = charges(root),
                stepDays = stepDays(root),
                sleepNights = sleepNights(root),
                places = list(root, "places", required = false, ::place),
                placeCategories = list(root, "placeCategories", required = false, ::placeCategory),
                placeVisits = placeVisits(root),
                trips = trips(root),
                tripPoints = tripPoints(root),
                rawFixes = rawFixes(root),
                timelineEdits = edits(root),
                usageCombos = list(root, "usageCombos", required = false, ::usageCombo),
                usageStates = states(root),
                focusSessions = list(root, "focusSessions", required = false, ::focus),
                tripEdits = list(root, "tripEdits", required = false, ::tripEdit),
                pointNotes = list(root, "pointNotes", required = false, ::pointNote),
                trackingProfiles = list(root, "trackingProfiles", required = false, ::profile),
                mapStyles = list(root, "mapStyles", required = false, ::mapStyle),
                rules = list(root, "rules", required = false, ::rule),
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is damaged or is not a backup.")
        }
    }

    private fun <T> array(items: List<T>, convert: (T) -> JSONObject): JSONArray {
        val out = JSONArray()
        for (item in items) out.put(convert(item))
        return out
    }

    private fun <T> list(root: JSONObject, key: String, required: Boolean, convert: (JSONObject) -> T): List<T> {
        val array = if (required) root.getJSONArray(key) else root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { convert(array.getJSONObject(it)) }
    }

    private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun entryJson(e: EntryEntity) = JSONObject()
        .put("id", e.id).put("date", e.date).put("startMinute", e.startMinute)
        .put("endMinute", e.endMinute ?: JSONObject.NULL)
        .put("category", e.category).put("title", e.title).put("note", e.note)

    private fun entry(o: JSONObject) = EntryEntity(
        id = o.getLong("id"),
        date = o.getString("date"),
        startMinute = o.getInt("startMinute"),
        endMinute = o.intOrNull("endMinute"),
        category = o.getString("category"),
        title = o.getString("title"),
        note = o.optString("note", ""),
    )

    private fun habitJson(h: HabitEntity) = JSONObject()
        .put("id", h.id).put("name", h.name).put("icon", h.icon).put("category", h.category)
        .put("color", h.color).put("kind", h.kind).put("quantKind", h.quantKind)
        .put("unitLabel", h.unitLabel).put("target", h.target).put("step", h.step)
        .put("anyAmount", h.anyAmount).put("startMinute", h.startMinute)
        .put("durationMinutes", h.durationMinutes).put("startedOn", h.startedOn)
        .put("archived", h.archived).put("sortOrder", h.sortOrder).put("raw", h.raw)
        .also { if (h.deletedAt != 0L) it.put("deletedAt", h.deletedAt); if (h.clearedAt != 0L) it.put("clearedAt", h.clearedAt) }

    private fun habit(o: JSONObject) = HabitEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        icon = o.getString("icon"),
        category = o.getString("category"),
        color = o.getLong("color"),
        kind = o.getInt("kind"),
        quantKind = o.getInt("quantKind"),
        unitLabel = o.getString("unitLabel"),
        target = o.getDouble("target"),
        step = o.getDouble("step"),
        anyAmount = o.getBoolean("anyAmount"),
        startMinute = o.getInt("startMinute"),
        durationMinutes = o.getInt("durationMinutes"),
        startedOn = o.getString("startedOn"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
        raw = o.getString("raw"),
        deletedAt = o.optLong("deletedAt", 0L),
        clearedAt = o.optLong("clearedAt", 0L),
    )

    private fun completionJson(c: CompletionEntity) = JSONObject()
        .put("habitId", c.habitId).put("date", c.date).put("count", c.count)
        .put("minuteOfDay", c.minuteOfDay ?: JSONObject.NULL)
        .put("marks", c.marks)
        .put("steps", c.steps)

    private fun completion(o: JSONObject) = CompletionEntity(
        habitId = o.getString("habitId"),
        date = o.getString("date"),
        count = o.getDouble("count"),
        minuteOfDay = o.intOrNull("minuteOfDay"),
        marks = o.optString("marks", ""),
        steps = o.optString("steps", ""),
    )

    private fun categoryJson(c: CategoryEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("color", c.color).put("sortOrder", c.sortOrder)

    private fun category(o: JSONObject) = CategoryEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        sortOrder = o.getInt("sortOrder"),
    )

    private fun todoTagJson(t: TodoTagEntity) = JSONObject()
        .put("id", t.id).put("name", t.name).put("color", t.color).put("kind", t.kind)
        .put("sortOrder", t.sortOrder)

    private fun todoTag(o: JSONObject) = TodoTagEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        kind = o.getString("kind"),
        sortOrder = o.getInt("sortOrder"),
    )

    private fun todoJson(t: TodoEntity) = JSONObject()
        .put("id", t.id).put("text", t.text).put("done", t.done)
        .put("date", t.date ?: JSONObject.NULL).put("minutes", t.minutes ?: JSONObject.NULL)
        .put("estimate", t.estimate ?: JSONObject.NULL).put("priority", t.priority)
        .put("project", t.project).put("createdAt", t.createdAt)
        .put("doneAt", t.doneAt ?: JSONObject.NULL).put("raw", t.raw)

    private fun todo(o: JSONObject) = TodoEntity(
        id = o.getString("id"),
        text = o.getString("text"),
        done = o.getBoolean("done"),
        date = o.textOrNull("date"),
        minutes = o.intOrNull("minutes"),
        estimate = o.intOrNull("estimate"),
        priority = o.getInt("priority"),
        project = o.getString("project"),
        createdAt = o.getString("createdAt"),
        doneAt = o.textOrNull("doneAt"),
        raw = o.getString("raw"),
    )

    private fun noteJson(n: NoteEntity) = JSONObject()
        .put("id", n.id).put("habitId", n.habitId).put("date", n.date).put("type", n.type)
        .put("text", n.text).put("minutes", n.minutes ?: JSONObject.NULL).put("createdAt", n.createdAt)
        .also { if (n.deletedAt != 0L) it.put("deletedAt", n.deletedAt) }

    private fun note(o: JSONObject) = NoteEntity(
        id = o.getString("id"),
        habitId = o.getString("habitId"),
        date = o.getString("date"),
        type = o.getInt("type"),
        text = o.getString("text"),
        minutes = o.intOrNull("minutes"),
        createdAt = o.getString("createdAt"),
        deletedAt = o.optLong("deletedAt", 0L),
    )

    private fun importedJson(i: ImportedItemEntity) = JSONObject()
        .put("source", i.source).put("sourceId", i.sourceId).put("entryId", i.entryId)

    private fun imported(o: JSONObject) = ImportedItemEntity(
        source = o.getString("source"),
        sourceId = o.getString("sourceId"),
        entryId = o.getLong("entryId"),
    )

    private fun mealJson(m: MealEntity) = JSONObject()
        .put("id", m.id).put("date", m.date).put("minuteOfDay", m.minuteOfDay).put("mealType", m.mealType)
        .put("name", m.name).put("grams", m.grams).put("kcal100", m.kcal100).put("carbs100", m.carbs100)
        .put("fat100", m.fat100).put("protein100", m.protein100).put("source", m.source).put("raw", m.raw)

    private fun meal(o: JSONObject) = MealEntity(
        id = o.getString("id"),
        date = o.getString("date"),
        minuteOfDay = o.getInt("minuteOfDay"),
        mealType = o.getString("mealType"),
        name = o.getString("name"),
        grams = o.getDouble("grams"),
        kcal100 = o.getDouble("kcal100"),
        carbs100 = o.getDouble("carbs100"),
        fat100 = o.getDouble("fat100"),
        protein100 = o.getDouble("protein100"),
        source = o.getString("source"),
        raw = o.getString("raw"),
    )

    private fun goalJson(g: FoodGoalEntity) = JSONObject()
        .put("date", g.date).put("kcal", g.kcal).put("carbs", g.carbs).put("fat", g.fat).put("protein", g.protein)

    private fun goal(o: JSONObject) = FoodGoalEntity(
        date = o.getString("date"),
        kcal = o.getDouble("kcal"),
        carbs = o.getDouble("carbs"),
        fat = o.getDouble("fat"),
        protein = o.getDouble("protein"),
    )

    private fun waterJson(w: WaterEntity) = JSONObject().put("date", w.date).put("ml", w.ml)

    private fun waterRow(o: JSONObject) = WaterEntity(date = o.getString("date"), ml = o.getInt("ml"))

    private fun activityJson(a: ActivityTypeEntity) = JSONObject()
        .put("id", a.id).put("name", a.name).put("color", a.color).put("goalMinutes", a.goalMinutes)
        .put("goalKind", a.goalKind).put("archived", a.archived).put("sortOrder", a.sortOrder).put("icon", a.icon)

    private fun activity(o: JSONObject) = ActivityTypeEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        goalMinutes = o.getInt("goalMinutes"),
        goalKind = o.getInt("goalKind"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
        icon = o.optString("icon", ""),
    )

    private fun moneyCategoryJson(c: MoneyCategoryEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("color", c.color).put("kind", c.kind)
        .put("sortOrder", c.sortOrder).put("archived", c.archived).also { if (c.icon.isNotEmpty()) it.put("icon", c.icon) }

    private fun moneyCategory(o: JSONObject) = MoneyCategoryEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        kind = o.getInt("kind"),
        sortOrder = o.getInt("sortOrder"),
        archived = o.getBoolean("archived"),
        icon = o.optString("icon", ""),
    )

    private fun moneyItemJson(i: MoneyItemEntity) = JSONObject()
        .put("id", i.id).put("name", i.name).put("kind", i.kind).put("categoryId", i.categoryId)
        .put("amount", i.amount).put("type", i.type).put("chargeDay", i.chargeDay)
        .put("startDate", i.startDate).put("endDate", i.endDate).put("lastPosted", i.lastPosted)
        .put("archived", i.archived).put("sortOrder", i.sortOrder)
        .also { if (i.source != SOURCE_MINE) it.put("source", i.source); if (i.paidBy.isNotEmpty()) it.put("paidBy", i.paidBy) }

    private fun moneyItem(o: JSONObject) = MoneyItemEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        kind = o.getInt("kind"),
        categoryId = o.getString("categoryId"),
        amount = o.getDouble("amount"),
        type = o.getInt("type"),
        chargeDay = o.getInt("chargeDay"),
        startDate = o.getString("startDate"),
        endDate = o.getString("endDate"),
        lastPosted = o.getString("lastPosted"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
        source = o.optInt("source", SOURCE_MINE),
        paidBy = o.optString("paidBy", ""),
    )

    private fun moneyEntryJson(e: MoneyEntryEntity) = JSONObject()
        .put("id", e.id).put("date", e.date).put("kind", e.kind).put("categoryId", e.categoryId)
        .put("name", e.name).put("amount", e.amount).put("note", e.note).put("itemId", e.itemId)
        .also { if (e.source != SOURCE_MINE) it.put("source", e.source); if (e.paidBy.isNotEmpty()) it.put("paidBy", e.paidBy) }

    private fun moneyEntry(o: JSONObject) = MoneyEntryEntity(
        id = o.getString("id"),
        date = o.getString("date"),
        kind = o.getInt("kind"),
        categoryId = o.getString("categoryId"),
        name = o.getString("name"),
        amount = o.getDouble("amount"),
        note = o.getString("note"),
        itemId = o.getString("itemId"),
        source = o.optInt("source", SOURCE_MINE),
        paidBy = o.optString("paidBy", ""),
    )

    // Sessions are the biggest part of a backup, so each is one short list: [app, start, end].
    private fun sessionsJson(list: List<UsageSessionEntity>): JSONArray {
        val out = JSONArray()
        for (s in list) out.put(JSONArray().put(s.pkg).put(s.startMs).put(s.endMs))
        return out
    }

    private fun sessions(root: JSONObject): List<UsageSessionEntity> {
        val array = root.optJSONArray("usageSessions") ?: return emptyList()
        return (0 until array.length()).map {
            val row = array.getJSONArray(it)
            val pkg = row.getString(0)
            val start = row.getLong(1)
            UsageSessionEntity(UsageSessionEntity.idFor(pkg, start), pkg, start, row.getLong(2))
        }
    }

    private fun usageAppJson(a: UsageAppEntity) = JSONObject()
        .put("pkg", a.pkg).put("label", a.label).put("activityId", a.activityId).put("ignored", a.ignored).put("icon", a.icon)

    private fun usageApp(o: JSONObject) = UsageAppEntity(
        pkg = o.getString("pkg"),
        label = o.getString("label"),
        activityId = o.getString("activityId"),
        ignored = o.getBoolean("ignored"),
        icon = o.optString("icon", ""),
    )

    // Off periods are one short list each: [kind, start, end].
    private fun statesJson(list: List<UsageStateEntity>): JSONArray {
        val out = JSONArray()
        for (s in list) out.put(JSONArray().put(s.kind).put(s.startMs).put(s.endMs))
        return out
    }

    private fun states(root: JSONObject): List<UsageStateEntity> {
        val array = root.optJSONArray("usageStates") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            val kind = r.getString(0)
            val start = r.getLong(1)
            UsageStateEntity(UsageStateEntity.idFor(kind, start), kind, start, r.getLong(2))
        }
    }

    private fun usageComboJson(c: UsageComboEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("pkgs", c.pkgs).put("activityId", c.activityId).put("updatedMs", c.updatedMs)

    private fun usageCombo(o: JSONObject) = UsageComboEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        pkgs = o.getString("pkgs"),
        activityId = o.getString("activityId"),
        updatedMs = o.optLong("updatedMs", 0L),
    )

    // Calls are one short list each: [number, name, type, start, seconds].
    private fun callsJson(list: List<CallEntity>): JSONArray {
        val out = JSONArray()
        for (c in list) out.put(JSONArray().put(c.number).put(c.name).put(c.type).put(c.startMs).put(c.durationSec))
        return out
    }

    private fun calls(root: JSONObject): List<CallEntity> {
        val array = root.optJSONArray("calls") ?: return emptyList()
        return (0 until array.length()).map {
            val row = array.getJSONArray(it)
            val number = row.getString(0)
            val start = row.getLong(3)
            CallEntity(CallStats.idFor(start, number), number, row.getString(1), row.getInt(2), start, row.getInt(4))
        }
    }

    // Charging sessions are one short list each: [start, end, startLevel, endLevel, plug, source, samples, currentSamples, sumMa, sumMv, ongoing].
    private fun chargeJson(list: List<ChargeSessionEntity>): JSONArray {
        val out = JSONArray()
        for (c in list) {
            out.put(
                JSONArray().put(c.startMs).put(c.endMs).put(c.startLevel).put(c.endLevel).put(c.plugType).put(c.source)
                    .put(c.samples).put(c.currentSamples).put(c.sumMa).put(c.sumMv).put(if (c.ongoing) 1 else 0),
            )
        }
        return out
    }

    private fun charges(root: JSONObject): List<ChargeSessionEntity> {
        val array = root.optJSONArray("chargeSessions") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            ChargeSessionEntity(
                id = r.getLong(0).toString(),
                startMs = r.getLong(0),
                endMs = r.getLong(1),
                startLevel = r.getInt(2),
                endLevel = r.getInt(3),
                plugType = r.getInt(4),
                source = r.getString(5),
                samples = r.getInt(6),
                currentSamples = r.getInt(7),
                sumMa = r.getLong(8),
                sumMv = r.getLong(9),
                ongoing = r.getInt(10) == 1,
            )
        }
    }

    // Steps are one short list per day: [date, steps, source].
    private fun stepsJson(list: List<StepDayEntity>): JSONArray {
        val out = JSONArray()
        for (d in list) out.put(JSONArray().put(d.date).put(d.steps).put(d.source))
        return out
    }

    private fun stepDays(root: JSONObject): List<StepDayEntity> {
        val array = root.optJSONArray("stepDays") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            StepDayEntity(r.getString(0), r.getInt(1), r.getString(2))
        }
    }

    // Sleep is one short list per night: [date, start, end, source].
    private fun sleepJson(list: List<SleepNightEntity>): JSONArray {
        val out = JSONArray()
        for (n in list) out.put(JSONArray().put(n.date).put(n.startMs).put(n.endMs).put(n.source))
        return out
    }

    private fun sleepNights(root: JSONObject): List<SleepNightEntity> {
        val array = root.optJSONArray("sleepNights") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            SleepNightEntity(r.getString(0), r.getLong(1), r.getLong(2), r.getString(3))
        }
    }

    private fun focusJson(f: FocusSessionEntity) = JSONObject()
        .put("id", f.id).put("habitId", f.habitId).put("targetMinutes", f.targetMinutes).put("seconds", f.seconds)
        .put("completed", f.completed).put("startedAtMs", f.startedAtMs).put("counted", f.counted).put("label", f.label)

    private fun focus(o: JSONObject) = FocusSessionEntity(
        id = o.getString("id"),
        habitId = o.optString("habitId", ""),
        targetMinutes = o.optInt("targetMinutes", 0),
        seconds = o.getInt("seconds"),
        completed = o.optBoolean("completed", false),
        startedAtMs = o.getLong("startedAtMs"),
        counted = o.optBoolean("counted", false),
        label = o.optString("label", ""),
    )

    private fun placeCategoryJson(c: PlaceCategoryEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("color", c.color).put("icon", c.icon)
        .put("sortOrder", c.sortOrder).put("archived", c.archived)

    private fun placeCategory(o: JSONObject) = PlaceCategoryEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        icon = o.optString("icon", ""),
        sortOrder = o.getInt("sortOrder"),
        archived = o.getBoolean("archived"),
    )

    private fun placeJson(p: PlaceEntity) = JSONObject()
        .put("id", p.id).put("name", p.name).put("kind", p.kind).put("lat", p.lat).put("lng", p.lng)
        .put("radiusM", p.radiusM).put("archived", p.archived).put("createdMs", p.createdMs)
        .also { o ->
            // The pause-zone settings are left out while they are all off (version 27).
            if (p.pauseHere) o.put("pauseHere", true)
            if (p.pauseWifi.isNotEmpty()) o.put("pauseWifi", p.pauseWifi)
            if (p.pauseMotionless) o.put("pauseMotionless", true)
            if (p.heartbeatMin != 0) o.put("heartbeatMin", p.heartbeatMin)
            if (p.entryDelaySec != -1) o.put("entryDelaySec", p.entryDelaySec)
        }

    private fun place(o: JSONObject) = PlaceEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        kind = o.getString("kind"),
        lat = o.getDouble("lat"),
        lng = o.getDouble("lng"),
        radiusM = o.getInt("radiusM"),
        archived = o.getBoolean("archived"),
        createdMs = o.getLong("createdMs"),
        pauseHere = o.optBoolean("pauseHere", false),
        pauseWifi = o.optString("pauseWifi", ""),
        pauseMotionless = o.optBoolean("pauseMotionless", false),
        heartbeatMin = o.optInt("heartbeatMin", 0),
        entryDelaySec = o.optInt("entryDelaySec", -1),
    )

    private fun tripEditJson(e: TripEditEntity) = JSONObject()
        .put("id", e.id).put("kind", e.kind).put("atMs", e.atMs).put("otherMs", e.otherMs).put("createdMs", e.createdMs)
        .put("undoneMs", e.undoneMs)

    private fun tripEdit(o: JSONObject) = TripEditEntity(
        id = o.getString("id"),
        kind = o.getString("kind"),
        atMs = o.getLong("atMs"),
        otherMs = o.optLong("otherMs", 0L),
        createdMs = o.getLong("createdMs"),
        undoneMs = o.optLong("undoneMs", 0L),
    )

    private fun pointNoteJson(n: PointNoteEntity) = JSONObject().put("ms", n.ms).put("note", n.note).put("updatedMs", n.updatedMs)

    private fun pointNote(o: JSONObject) = PointNoteEntity(
        ms = o.getLong("ms"),
        note = o.getString("note"),
        updatedMs = o.optLong("updatedMs", 0L),
    )

    // Version 29 adds the rules (the line of what ran is not kept in a backup).
    private fun ruleJson(r: RuleEntity) = JSONObject()
        .put("id", r.id).put("name", r.name).put("enabled", r.enabled).put("trigger", r.triggerJson)
        .put("conditions", r.conditionsJson).put("actions", r.actionsJson).put("cooldownSec", r.cooldownSec)
        .put("createdMs", r.createdMs).put("lastRunMs", r.lastRunMs).put("runs", r.runs).put("sortOrder", r.sortOrder)

    private fun rule(o: JSONObject) = RuleEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        enabled = o.optBoolean("enabled", true),
        triggerJson = o.getString("trigger"),
        conditionsJson = o.optString("conditions", "[]"),
        actionsJson = o.optString("actions", "[]"),
        cooldownSec = o.optInt("cooldownSec", Rule.DEFAULT_COOLDOWN_SEC),
        createdMs = o.optLong("createdMs", 0L),
        lastRunMs = o.optLong("lastRunMs", 0L),
        runs = o.optInt("runs", 0),
        sortOrder = o.optInt("sortOrder", 0),
    )

    private fun mapStyleJson(m: MapStyleEntity) = JSONObject()
        .put("id", m.id).put("name", m.name).put("address", m.address).put("key", m.key)
        .put("hidden", m.hidden).put("createdMs", m.createdMs)

    private fun mapStyle(o: JSONObject) = MapStyleEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        address = o.getString("address"),
        key = o.optString("key", ""),
        hidden = o.optBoolean("hidden", false),
        createdMs = o.getLong("createdMs"),
    )

    private fun profileJson(p: TrackingProfileEntity) = JSONObject()
        .put("id", p.id).put("name", p.name).put("condition", p.condition).put("conditionText", p.conditionText)
        .put("conditionNumber", p.conditionNumber).put("intervalSec", p.intervalSec).put("minMoveM", p.minMoveM)
        .put("accuracyMode", p.accuracyMode).put("priority", p.priority).put("activateDelaySec", p.activateDelaySec)
        .put("deactivateDelaySec", p.deactivateDelaySec).put("enabled", p.enabled).put("deletedAt", p.deletedAt)
        .put("createdMs", p.createdMs)

    private fun profile(o: JSONObject) = TrackingProfileEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        condition = o.getString("condition"),
        conditionText = o.optString("conditionText", ""),
        conditionNumber = o.optDouble("conditionNumber", 0.0),
        intervalSec = o.getInt("intervalSec"),
        minMoveM = o.optInt("minMoveM", 0),
        accuracyMode = o.optString("accuracyMode", ""),
        priority = o.optInt("priority", 0),
        activateDelaySec = o.optInt("activateDelaySec", 0),
        deactivateDelaySec = o.optInt("deactivateDelaySec", 0),
        enabled = o.optBoolean("enabled", true),
        deletedAt = o.optLong("deletedAt", 0L),
        createdMs = o.getLong("createdMs"),
    )

    // Place visits are one short list each: [id, place, start, end, lat, lng, source, ongoing, ignored, confidence].
    // A list from before version 14 has nine values and no confidence.
    private fun visitsJson(list: List<PlaceVisitEntity>): JSONArray {
        val out = JSONArray()
        for (v in list) {
            out.put(
                JSONArray().put(v.id).put(v.placeId).put(v.startMs).put(v.endMs).put(v.lat).put(v.lng)
                    .put(v.source).put(if (v.ongoing) 1 else 0).put(if (v.ignored) 1 else 0).put(v.confidence),
            )
        }
        return out
    }

    private fun placeVisits(root: JSONObject): List<PlaceVisitEntity> {
        val array = root.optJSONArray("placeVisits") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            PlaceVisitEntity(
                id = r.getString(0),
                placeId = r.getString(1),
                startMs = r.getLong(2),
                endMs = r.getLong(3),
                lat = r.getDouble(4),
                lng = r.getDouble(5),
                source = r.getString(6),
                ongoing = r.getInt(7) == 1,
                ignored = r.getInt(8) == 1,
                confidence = if (r.length() > 9) r.getInt(9) else -1,
            )
        }
    }

    // Timeline edits are one short list each: [kind, id, start, end, category, hidden, changedAt, label].
    private fun editsJson(list: List<TimelineEditEntity>): JSONArray {
        val out = JSONArray()
        for (e in list) {
            out.put(JSONArray().put(e.kind).put(e.refId).put(e.startMs).put(e.endMs).put(e.activityId).put(if (e.hidden) 1 else 0).put(e.updatedMs).put(e.label))
        }
        return out
    }

    private fun edits(root: JSONObject): List<TimelineEditEntity> {
        val array = root.optJSONArray("timelineEdits") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            TimelineEditEntity(
                kind = r.getString(0),
                refId = r.getString(1),
                startMs = r.getLong(2),
                endMs = r.getLong(3),
                activityId = r.getString(4),
                hidden = r.getInt(5) == 1,
                updatedMs = r.getLong(6),
                label = r.optString(7, ""),
            )
        }
    }

    // Trips are one short list each: [id, start, end, from, to, distance, medianKmh, topKmh, activity, mode, modeSource].
    private fun tripsJson(list: List<TripEntity>): JSONArray {
        val out = JSONArray()
        for (t in list) {
            out.put(
                JSONArray().put(t.id).put(t.startMs).put(t.endMs).put(t.fromPlaceId).put(t.toPlaceId).put(t.distanceM)
                    .put(t.medianKmh).put(t.topKmh).put(t.activity).put(t.mode).put(t.modeSource),
            )
        }
        return out
    }

    private fun trips(root: JSONObject): List<TripEntity> {
        val array = root.optJSONArray("trips") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            TripEntity(
                id = r.getString(0),
                startMs = r.getLong(1),
                endMs = r.getLong(2),
                fromPlaceId = r.getString(3),
                toPlaceId = r.getString(4),
                distanceM = r.getInt(5),
                medianKmh = r.getInt(6),
                topKmh = r.getInt(7),
                activity = r.getString(8),
                mode = r.getString(9),
                modeSource = r.getString(10),
            )
        }
    }

    // Trip readings are one short list each: [trip, time, lat, lng, accuracy].
    private fun tripPointsJson(list: List<TripPointEntity>): JSONArray {
        val out = JSONArray()
        for (p in list) out.put(JSONArray().put(p.tripId).put(p.ms).put(p.lat).put(p.lng).put(p.acc.toDouble()).put(p.steps))
        return out
    }

    private fun tripPoints(root: JSONObject): List<TripPointEntity> {
        val array = root.optJSONArray("tripPoints") ?: return emptyList()
        return (0 until array.length()).map {
            val r = array.getJSONArray(it)
            TripPointEntity(r.getString(0), r.getLong(1), r.getDouble(2), r.getDouble(3), r.getDouble(4).toFloat(), r.optLong(5, -1L))
        }
    }

    private const val DAY_MS = 86_400_000L

    /**
     * Position readings are many, so each day is one object with one short text: a reading is
     * "milliseconds after the previous one, latitude, longitude, accuracy, speed", the first one counted from the start of the
     * day and each next latitude and longitude as a change, with degrees in millionths (about 11 cm), the accuracy
     * in whole metres and the speed in centimetres per second (-1 when the phone did not say), separated by ";".
     * A version 12 file has no speed (four values) and is still read. From version 27 a reading that has any of the new values
     * (direction, altitude, battery, steps, source, profile) has eleven values: the five above, then the direction in whole degrees
     * (-1 for none), the altitude in decimetres (empty for none), the battery percent (-1), the step counter (-1), the source and
     * the profile id (commas and semicolons in those two are replaced by underscores). A reading with none of them keeps five values.
     */
    private fun rawFixesJson(list: List<RawFixEntity>): JSONArray {
        val out = JSONArray()
        for ((day, fixes) in list.sortedBy { it.ms }.groupBy { Math.floorDiv(it.ms, DAY_MS) }) {
            val sb = StringBuilder()
            var ms = day * DAY_MS
            var lat = 0L
            var lng = 0L
            for (f in fixes) {
                val la = Math.round(f.lat * 1_000_000.0)
                val lo = Math.round(f.lng * 1_000_000.0)
                if (sb.isNotEmpty()) sb.append(';')
                sb.append(f.ms - ms).append(',').append(la - lat).append(',').append(lo - lng).append(',').append(Math.round(f.acc))
                    .append(',').append(if (f.speed < 0f) -1L else Math.round(f.speed * 100.0))
                if (hasExtras(f)) {
                    sb.append(',').append(if (f.bearing < 0f) -1L else Math.round(f.bearing.toDouble()))
                        .append(',').append(f.altitude?.let { Math.round(it * 10.0).toString() } ?: "")
                        .append(',').append(f.battery).append(',').append(f.steps)
                        .append(',').append(f.source.replace(',', '_').replace(';', '_'))
                        .append(',').append(f.profileId.replace(',', '_').replace(';', '_'))
                }
                ms = f.ms
                lat = la
                lng = lo
            }
            out.put(JSONObject().put("day", day).put("rows", sb.toString()))
        }
        return out
    }

    private fun rawFixes(root: JSONObject): List<RawFixEntity> {
        val array = root.optJSONArray("rawFixes") ?: return emptyList()
        val out = ArrayList<RawFixEntity>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            var ms = o.getLong("day") * DAY_MS
            var lat = 0L
            var lng = 0L
            val rows = o.getString("rows")
            if (rows.isEmpty()) continue
            try {
                for (row in rows.split(';')) {
                    val p = row.split(',')
                    if (p.size != 4 && p.size != 5 && p.size != 11) throw JSONException("bad reading")
                    ms += p[0].toLong()
                    lat += p[1].toLong()
                    lng += p[2].toLong()
                    val speed = if (p.size >= 5 && p[4].toLong() >= 0L) p[4].toLong() / 100f else -1f
                    if (p.size == 11) {
                        out.add(
                            RawFixEntity(
                                ms, lat / 1_000_000.0, lng / 1_000_000.0, p[3].toFloat(), speed,
                                bearing = p[5].toLong().let { if (it < 0L) -1f else it.toFloat() },
                                altitude = p[6].takeIf { it.isNotEmpty() }?.let { it.toLong() / 10.0 },
                                battery = p[7].toInt(),
                                steps = p[8].toLong(),
                                source = p[9],
                                profileId = p[10],
                            ),
                        )
                    } else {
                        out.add(RawFixEntity(ms, lat / 1_000_000.0, lng / 1_000_000.0, p[3].toFloat(), speed))
                    }
                }
            } catch (e: NumberFormatException) {
                throw JSONException("bad reading")
            }
        }
        return out
    }

    private fun hasExtras(f: RawFixEntity) =
        f.bearing >= 0f || f.altitude != null || f.battery != -1 || f.steps != -1L || f.source.isNotEmpty() || f.profileId.isNotEmpty()
}
