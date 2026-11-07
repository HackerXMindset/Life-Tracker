package com.lifetracker.app.data.gymmane

import com.lifetracker.app.data.FastEntity
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.SupplementEntity
import com.lifetracker.app.data.SupplementTakenEntity
import com.lifetracker.app.data.WeightEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** What GymMane's `gymmane.json` holds that Kairos also keeps, already turned into Kairos' own rows. */
data class GymManData(
    val meals: List<MealEntity>,
    /** Millilitres drunk, by day (yyyy-mm-dd). */
    val water: Map<String, Int>,
    val supplements: List<SupplementEntity>,
    val taken: List<SupplementTakenEntity>,
    val fasts: List<FastEntity>,
    val weights: List<WeightEntity>,
) {
    val isEmpty get() = meals.isEmpty() && water.isEmpty() && supplements.isEmpty() && taken.isEmpty() && fasts.isEmpty() && weights.isEmpty()
}

/** What Kairos can give back to GymMane. Everything that came from GymMane is left out by the caller. */
data class GymManOutgoing(
    val meals: List<MealEntity>,
    val water: Map<String, Int>,
    val supplements: List<SupplementEntity>,
    val taken: List<SupplementTakenEntity>,
    val fasts: List<FastEntity>,
    val weights: List<WeightEntity>,
)

/**
 * Reads and writes the part of GymMane's backup (`gymmane.json` inside its zip) that
 * Kairos shares: the food log, water, supplements, fasts and body weight.
 *
 * Ids: things that came from GymMane are "gm-<its id>" here; things that go the other
 * way are "kx-<our id>" there, so nothing is ever sent back to where it came from.
 */
object GymManCodec {
    const val SOURCE = "gymmane"
    const val FROM_GYMMANE = "gm-"
    const val FROM_KAIROS = "kx-"
    const val JSON_ENTRY = "gymmane.json"

    private const val NOON = 12 * 60

    // ---- reading ----

    /** [knownFood] says whether a library food id such as "a:banana" exists here, so a meal keeps its photo and vitamins. */
    fun parse(text: String, knownFood: (String) -> Boolean = { false }): GymManData {
        val root = JSONObject(text)
        val nutrition = root.optJSONObject("nutrition")
        val meals = ArrayList<MealEntity>()
        val water = LinkedHashMap<String, Int>()
        val supplements = ArrayList<SupplementEntity>()
        val taken = ArrayList<SupplementTakenEntity>()
        val fasts = ArrayList<FastEntity>()

        if (nutrition != null) {
            for (e in objects(nutrition.optJSONArray("log"))) {
                val id = e.optString("id")
                if (id.isEmpty() || id.startsWith(FROM_KAIROS)) continue
                meal(e, knownFood)?.let { meals += it }
            }
            for (w in objects(nutrition.optJSONArray("water"))) {
                val at = time(w.optString("at")) ?: continue
                val ml = w.optInt("ml")
                if (ml > 0) water.merge(at.toLocalDate().toString(), ml, Int::plus)
            }
            val archived = nutrition.optJSONArray("suppArch").strings().toSet()
            for ((i, s) in objects(nutrition.optJSONArray("supps")).withIndex()) {
                val id = s.optString("id")
                if (id.isEmpty() || id.startsWith(FROM_KAIROS)) continue
                supplements += SupplementEntity(
                    id = FROM_GYMMANE + id,
                    name = s.optString("n"),
                    dose = s.optString("d"),
                    atMinute = if (s.has("at")) s.optInt("at").coerceIn(0, 1439) else -1,
                    weekdays = s.optInt("w") and 0xFE,
                    everyDays = s.optInt("e", 1).coerceAtLeast(1),
                    fromDate = s.optString("f"),
                    untilDate = s.optString("u"),
                    sortOrder = i,
                    archived = id in archived,
                )
            }
            val byId = supplements.associateBy { it.id }
            nutrition.optJSONObject("taken")?.let { t ->
                for (day in t.keys()) {
                    for (sid in t.optJSONArray(day).strings()) {
                        if (sid.startsWith(FROM_KAIROS)) continue
                        val s = byId[FROM_GYMMANE + sid] ?: continue
                        taken += SupplementTakenEntity(s.id, day, if (s.atMinute >= 0) s.atMinute else NOON)
                    }
                }
            }
            nutrition.optJSONObject("fast")?.let { f ->
                for (r in objects(f.optJSONArray("log"))) {
                    val s = time(r.optString("s")) ?: continue
                    val e = time(r.optString("e")) ?: continue
                    if (!e.isAfter(s)) continue
                    val startMs = ms(s)
                    fasts += FastEntity(FROM_GYMMANE + startMs, startMs, ms(e), r.optInt("g", 16).coerceIn(1, 72))
                }
            }
        }

        val weights = ArrayList<WeightEntity>()
        for (b in objects(root.optJSONArray("bodyweight"))) {
            val at = time(b.optString("d")) ?: continue
            val kg = b.optDouble("kg", 0.0)
            if (kg > 0 && kg.isFinite()) weights += WeightEntity(at.toLocalDate().toString(), kg)
        }
        return GymManData(meals, water, supplements, taken, fasts, weights)
    }

    private fun meal(e: JSONObject, knownFood: (String) -> Boolean): MealEntity? {
        val at = time(e.optString("at")) ?: return null
        val m = e.optJSONObject("m") ?: JSONObject()
        val x = e.optDouble("x", 1.0).takeIf { it > 0 && it.isFinite() } ?: 1.0
        val perServingGrams = e.optDouble("g", 0.0).takeIf { it > 0 && it.isFinite() } ?: 0.0
        // GymMane keeps the numbers for one serving. With a weight, Kairos wants them per 100 g; without one the whole is taken as 100 g.
        val grams = if (perServingGrams > 0) perServingGrams * x else 100.0
        val k = if (perServingGrams > 0) 100.0 / perServingGrams else x
        fun v(key: String) = m.optDouble(key, 0.0).takeIf { it > 0 && it.isFinite() } ?: 0.0
        val aa = m.optJSONArray("aa")
        val amino = if (aa != null && aa.length() == 9) {
            (0 until 9).joinToString(",") { num(aa.optDouble(it, 0.0) * k) }
        } else ""
        val food = e.optString("fd").takeIf { FOOD_REF.matches(it) && knownFood(it) } ?: ""
        val type = when (e.optString("ml")) {
            "breakfast" -> "breakfast"
            "lunch" -> "lunch"
            "dinner" -> "dinner"
            else -> "snack"
        }
        return MealEntity(
            id = FROM_GYMMANE + e.optString("id"),
            date = at.toLocalDate().toString(),
            minuteOfDay = at.hour * 60 + at.minute,
            mealType = type,
            name = e.optString("n"),
            grams = grams,
            kcal100 = v("k") * k,
            carbs100 = v("c") * k,
            fat100 = v("f") * k,
            protein100 = v("p") * k,
            source = SOURCE,
            raw = "",
            foodId = food,
            fiber100 = v("fi") * k,
            sugar100 = v("su") * k,
            sodium100 = v("na") * k,
            sat100 = v("sa") * k,
            mono100 = v("mu") * k,
            poly100 = v("po") * k,
            animal100 = v("an") * k,
            amino = amino,
        )
    }

    // ---- writing ----

    /**
     * Returns [text] (an existing `gymmane.json`) with whatever [out] holds and the file lacks added to it.
     * Nothing already in the file is changed or removed.
     */
    fun merge(text: String, out: GymManOutgoing): String {
        val root = JSONObject(text)
        val nutrition = root.optJSONObject("nutrition") ?: JSONObject().also { root.put("nutrition", it) }

        // Food log
        val log = nutrition.optJSONArray("log") ?: JSONArray().also { nutrition.put("log", it) }
        val have = objects(log).map { it.optString("id") }.toHashSet()
        for (m in out.meals) {
            val id = FROM_KAIROS + m.id
            if (id in have || m.id.startsWith(FROM_GYMMANE)) continue
            log.put(entry(id, m))
        }

        // Water: add one sip for what the file is short of, per day
        val water = nutrition.optJSONArray("water") ?: JSONArray().also { nutrition.put("water", it) }
        val drunk = HashMap<String, Int>()
        for (w in objects(water)) {
            val at = time(w.optString("at")) ?: continue
            drunk.merge(at.toLocalDate().toString(), w.optInt("ml"), Int::plus)
        }
        for ((day, ml) in out.water) {
            val short = ml - (drunk[day] ?: 0)
            if (short > 0) water.put(JSONObject().put("at", "${day}T12:00:00.000").put("ml", short.coerceAtMost(5000)))
        }

        // Supplements and what was taken
        val supps = nutrition.optJSONArray("supps") ?: JSONArray().also { nutrition.put("supps", it) }
        val haveSupp = objects(supps).map { it.optString("id") }.toHashSet()
        val theirId = HashMap<String, String>()
        for (s in out.supplements) {
            val id = theirSupplementId(s.id)
            theirId[s.id] = id
            if (id in haveSupp) continue
            supps.put(
                JSONObject().put("id", id).put("n", s.name).apply {
                    if (s.dose.isNotEmpty()) put("d", s.dose)
                    if (s.atMinute >= 0) put("at", s.atMinute)
                    if (s.weekdays != 0) put("w", s.weekdays and 0xFE)
                    if (s.everyDays > 1) put("e", s.everyDays)
                    if (s.fromDate.isNotEmpty()) put("f", s.fromDate)
                    if (s.untilDate.isNotEmpty()) put("u", s.untilDate)
                },
            )
            haveSupp += id
        }
        val takenJson = nutrition.optJSONObject("taken") ?: JSONObject().also { nutrition.put("taken", it) }
        for (t in out.taken) {
            val id = theirId[t.supplementId] ?: continue
            val list = takenJson.optJSONArray(t.date) ?: JSONArray().also { takenJson.put(t.date, it) }
            if (list.strings().none { it == id }) list.put(id)
        }

        // Fasts
        val fast = nutrition.optJSONObject("fast") ?: JSONObject().also { nutrition.put("fast", it) }
        val fastLog = fast.optJSONArray("log") ?: JSONArray().also { fast.put("log", it) }
        val haveStarts = objects(fastLog).mapNotNull { time(it.optString("s"))?.let(::ms) }
        for (f in out.fasts) {
            if (f.id.startsWith(FROM_GYMMANE)) continue
            if (haveStarts.any { kotlin.math.abs(it - f.startMs) < 60_000 }) continue
            fastLog.put(
                JSONObject().put("s", iso(f.startMs)).put("e", iso(f.endMs)).put("g", f.goalHours),
            )
        }
        if (!fast.has("h")) fast.put("h", 16)

        // Body weight: a day that has a weight there already is left as it is
        val bw = root.optJSONArray("bodyweight") ?: JSONArray().also { root.put("bodyweight", it) }
        val haveDays = objects(bw).mapNotNull { time(it.optString("d"))?.toLocalDate()?.toString() }.toHashSet()
        for (w in out.weights) {
            if (w.date in haveDays) continue
            bw.put(JSONObject().put("d", "${w.date}T07:00:00.000").put("kg", w.kg))
        }
        return root.toString(2)
    }

    /** Whether [out] holds anything at all, so an empty send can be skipped. */
    fun hasAnything(out: GymManOutgoing) =
        out.meals.isNotEmpty() || out.water.isNotEmpty() || out.supplements.isNotEmpty() ||
            out.taken.isNotEmpty() || out.fasts.isNotEmpty() || out.weights.isNotEmpty()

    fun theirSupplementId(ours: String) =
        if (ours.startsWith(FROM_GYMMANE)) ours.removePrefix(FROM_GYMMANE) else FROM_KAIROS + ours

    private fun entry(id: String, m: MealEntity): JSONObject {
        // GymMane keeps the numbers for one serving; here one serving is the whole portion.
        val f = m.grams / 100.0
        val per = JSONObject()
        fun put(key: String, per100: Double) {
            val v = per100 * f
            if (v > 0 && v.isFinite()) per.put(key, round(v))
        }
        put("k", m.kcal100); put("p", m.protein100); put("c", m.carbs100); put("f", m.fat100)
        put("fi", m.fiber100); put("su", m.sugar100); put("na", m.sodium100)
        put("an", m.animal100); put("sa", m.sat100); put("mu", m.mono100); put("po", m.poly100)
        val aa = m.amino.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if (aa.size == 9 && m.protein100 > 0) {
            per.put("ap", round(m.protein100 * f))
            per.put("aa", JSONArray(aa.map { round(it * f) }))
        }
        val at = "${m.date}T%02d:%02d:00.000".format(m.minuteOfDay / 60, m.minuteOfDay % 60)
        return JSONObject()
            .put("id", id)
            .put("at", at)
            .put("ml", if (m.mealType == "snack") "snacks" else m.mealType)
            .put("n", m.name)
            .apply { if (m.grams > 0) put("g", round(m.grams)) }
            .put("m", per)
    }

    // ---- helpers ----

    private val FOOD_REF = Regex("^[a-z]:.+")

    private fun objects(a: JSONArray?): List<JSONObject> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

    /** GymMane writes local times with no zone; one that ends in Z or an offset is moved to this phone's zone. */
    fun time(s: String): LocalDateTime? {
        if (s.isEmpty()) return null
        return try {
            if (s.endsWith("Z") || Regex(".*[+-]\\d\\d:\\d\\d$").matches(s)) {
                OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            } else if (s.length == 10) {
                LocalDate.parse(s).atStartOfDay()
            } else {
                LocalDateTime.parse(s)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun iso(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().let {
            if (it.length == 16) "$it:00.000" else it
        }

    private fun round(v: Double) = Math.round(v * 1000.0) / 1000.0

    private fun num(v: Double) = round(v).toString()
}
