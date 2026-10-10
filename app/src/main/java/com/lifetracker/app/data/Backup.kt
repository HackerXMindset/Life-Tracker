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
    val calls: List<CallEntity> = emptyList(),
    val chargeSessions: List<ChargeSessionEntity> = emptyList(),
    val stepDays: List<StepDayEntity> = emptyList(),
    val sleepNights: List<SleepNightEntity> = emptyList(),
    val places: List<PlaceEntity> = emptyList(),
    val placeVisits: List<PlaceVisitEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = entries.isEmpty() && habits.isEmpty() && completions.isEmpty() && categories.isEmpty() &&
            todoTags.isEmpty() && todos.isEmpty() && notes.isEmpty() && meals.isEmpty() && water.isEmpty() &&
            moneyItems.isEmpty() && moneyEntries.isEmpty() && usageSessions.isEmpty() && calls.isEmpty() &&
            chargeSessions.isEmpty() && stepDays.isEmpty() && sleepNights.isEmpty() && places.isEmpty() && placeVisits.isEmpty()
}

/**
 * The backup file format: plain JSON you can open in any text editor.
 *
 * `version` lets later steps add more data without breaking old backups.
 * Version 1 held only Timeline entries; version 2 adds habits, to-dos, notes
 * and the record of what was imported; version 3 adds meals, food goals and water; version 4 adds your own activities and their goals; version 5 adds money (categories, saved items and entries); version 6 adds phone usage (sessions are stored compactly as [app, start, end]); version 7 adds phone calls (each stored as [number, name, type, start, seconds]); version 8 adds charging sessions (each stored as [start, end, startLevel, endLevel, plug, source, samples, currentSamples, sumMa, sumMv, ongoing]); version 9 adds steps (each day stored as [date, steps, source]) and sleep (each night stored as [date, start, end, source]); version 10 adds places (the ones you named) and place visits (each stored as [id, place, start, end, lat, lng, source, ongoing, ignored]). A file from a newer version of the app
 * is refused rather than half-read.
 */
object BackupCodec {
    const val APP_NAME = "life-tracker"
    const val FORMAT_VERSION = 10

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
            .put("placeVisits", visitsJson(data.placeVisits))
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
                placeVisits = placeVisits(root),
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
    )

    private fun completionJson(c: CompletionEntity) = JSONObject()
        .put("habitId", c.habitId).put("date", c.date).put("count", c.count)
        .put("minuteOfDay", c.minuteOfDay ?: JSONObject.NULL)

    private fun completion(o: JSONObject) = CompletionEntity(
        habitId = o.getString("habitId"),
        date = o.getString("date"),
        count = o.getDouble("count"),
        minuteOfDay = o.intOrNull("minuteOfDay"),
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

    private fun note(o: JSONObject) = NoteEntity(
        id = o.getString("id"),
        habitId = o.getString("habitId"),
        date = o.getString("date"),
        type = o.getInt("type"),
        text = o.getString("text"),
        minutes = o.intOrNull("minutes"),
        createdAt = o.getString("createdAt"),
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
        .put("goalKind", a.goalKind).put("archived", a.archived).put("sortOrder", a.sortOrder)

    private fun activity(o: JSONObject) = ActivityTypeEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        goalMinutes = o.getInt("goalMinutes"),
        goalKind = o.getInt("goalKind"),
        archived = o.getBoolean("archived"),
        sortOrder = o.getInt("sortOrder"),
    )

    private fun moneyCategoryJson(c: MoneyCategoryEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("color", c.color).put("kind", c.kind)
        .put("sortOrder", c.sortOrder).put("archived", c.archived)

    private fun moneyCategory(o: JSONObject) = MoneyCategoryEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        color = o.getLong("color"),
        kind = o.getInt("kind"),
        sortOrder = o.getInt("sortOrder"),
        archived = o.getBoolean("archived"),
    )

    private fun moneyItemJson(i: MoneyItemEntity) = JSONObject()
        .put("id", i.id).put("name", i.name).put("kind", i.kind).put("categoryId", i.categoryId)
        .put("amount", i.amount).put("type", i.type).put("chargeDay", i.chargeDay)
        .put("startDate", i.startDate).put("endDate", i.endDate).put("lastPosted", i.lastPosted)
        .put("archived", i.archived).put("sortOrder", i.sortOrder)

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
    )

    private fun moneyEntryJson(e: MoneyEntryEntity) = JSONObject()
        .put("id", e.id).put("date", e.date).put("kind", e.kind).put("categoryId", e.categoryId)
        .put("name", e.name).put("amount", e.amount).put("note", e.note).put("itemId", e.itemId)

    private fun moneyEntry(o: JSONObject) = MoneyEntryEntity(
        id = o.getString("id"),
        date = o.getString("date"),
        kind = o.getInt("kind"),
        categoryId = o.getString("categoryId"),
        name = o.getString("name"),
        amount = o.getDouble("amount"),
        note = o.getString("note"),
        itemId = o.getString("itemId"),
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
        .put("pkg", a.pkg).put("label", a.label).put("activityId", a.activityId).put("ignored", a.ignored)

    private fun usageApp(o: JSONObject) = UsageAppEntity(
        pkg = o.getString("pkg"),
        label = o.getString("label"),
        activityId = o.getString("activityId"),
        ignored = o.getBoolean("ignored"),
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

    private fun placeJson(p: PlaceEntity) = JSONObject()
        .put("id", p.id).put("name", p.name).put("kind", p.kind).put("lat", p.lat).put("lng", p.lng)
        .put("radiusM", p.radiusM).put("archived", p.archived).put("createdMs", p.createdMs)

    private fun place(o: JSONObject) = PlaceEntity(
        id = o.getString("id"),
        name = o.getString("name"),
        kind = o.getString("kind"),
        lat = o.getDouble("lat"),
        lng = o.getDouble("lng"),
        radiusM = o.getInt("radiusM"),
        archived = o.getBoolean("archived"),
        createdMs = o.getLong("createdMs"),
    )

    // Place visits are one short list each: [id, place, start, end, lat, lng, source, ongoing, ignored].
    private fun visitsJson(list: List<PlaceVisitEntity>): JSONArray {
        val out = JSONArray()
        for (v in list) {
            out.put(
                JSONArray().put(v.id).put(v.placeId).put(v.startMs).put(v.endMs).put(v.lat).put(v.lng)
                    .put(v.source).put(if (v.ongoing) 1 else 0).put(if (v.ignored) 1 else 0),
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
            )
        }
    }
}
