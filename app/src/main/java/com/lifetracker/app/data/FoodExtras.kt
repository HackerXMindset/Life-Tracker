package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * The rest of the Food tab (database version 35): supplements, body weight, fasting, meal prep batches and meal plans.
 * The same ideas as GymMane, kept in tables of their own. Switches and small settings live in [FoodSettings].
 */

/** A supplement you take. [weekdays] is a bit mask (bit 1 = Monday ... bit 7 = Sunday), 0 meaning every day. */
@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dose: String = "",
    /** Minute of the day to take it, or -1 for any time. */
    val atMinute: Int = -1,
    val weekdays: Int = 0,
    /** 1 = every day it is due; 2 or more = every that many days counted from [fromDate]. */
    val everyDays: Int = 1,
    /** yyyy-mm-dd or "". */
    val fromDate: String = "",
    val untilDate: String = "",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/** The day a supplement was ticked off, and when. */
@Entity(tableName = "supplement_taken", primaryKeys = ["supplementId", "date"])
data class SupplementTakenEntity(
    val supplementId: String,
    val date: String,
    val minuteOfDay: Int,
)

/** Body weight on a day, in kg (one weigh-in a day). */
@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val date: String,
    val kg: Double,
)

/** A fast that has ended. */
@Entity(tableName = "fasts")
data class FastEntity(
    @PrimaryKey val id: String,
    val startMs: Long,
    val endMs: Long,
    val goalHours: Int,
)

/** A big pot you cooked once and eat bit by bit. [items] is a [SavedMealItems] list of the ingredients. */
@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val items: String,
    val createdMs: Long,
    /** What the pot weighs once cooked, or 0 to use the raw total. */
    val cookedGrams: Double = 0.0,
    val leftGrams: Double = 0.0,
    val portions: Int = 4,
    val deletedMs: Long = 0,
)

/** A day of meals planned ahead; one list of [SavedMealItems] per meal. */
@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val breakfast: String = "",
    val lunch: String = "",
    val dinner: String = "",
    val snack: String = "",
    val createdMs: Long = 0,
    val deletedMs: Long = 0,
) {
    fun itemsOf(mealType: String): List<SavedMealItem> = SavedMealItems.decode(
        when (mealType) {
            FoodLogic.BREAKFAST -> breakfast
            FoodLogic.LUNCH -> lunch
            FoodLogic.DINNER -> dinner
            else -> snack
        },
    )

    fun withItems(mealType: String, items: List<SavedMealItem>): MealPlanEntity {
        val text = if (items.isEmpty()) "" else SavedMealItems.encode(items)
        return when (mealType) {
            FoodLogic.BREAKFAST -> copy(breakfast = text)
            FoodLogic.LUNCH -> copy(lunch = text)
            FoodLogic.DINNER -> copy(dinner = text)
            else -> copy(snack = text)
        }
    }

    val allItems: List<SavedMealItem> get() = FoodLogic.MEAL_TYPES.flatMap { itemsOf(it) }
}

/** Something you want to count by tapping: a headache, reflux, a cup of tea. It never touches calories. */
@Entity(tableName = "food_trackers")
data class FoodTrackerEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** An icon name or an emoji, as everywhere else in the app. */
    val icon: String = "",
    val color: Long = 0xFF5B5FC7L,
    val sortOrder: Int = 0,
    /** Hidden trackers are not offered for logging but keep their history. */
    val archived: Boolean = false,
)

/** One time you tapped a tracker. */
@Entity(tableName = "tracker_events")
data class TrackerEventEntity(
    @PrimaryKey val id: String,
    val trackerId: String,
    val date: String,
    val minuteOfDay: Int,
    val note: String = "",
)

@Dao
interface FoodExtrasDao {
    // supplements
    @Query("SELECT * FROM supplements WHERE archived = 0 ORDER BY atMinute = -1, atMinute, sortOrder, name COLLATE NOCASE")
    fun observeSupplements(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplements ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun allSupplements(): List<SupplementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplement(s: SupplementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplements(list: List<SupplementEntity>)

    @Query("DELETE FROM supplements WHERE id = :id")
    suspend fun deleteSupplement(id: String)

    @Query("DELETE FROM supplement_taken WHERE supplementId = :id")
    suspend fun deleteTakenOf(id: String)

    @Query("DELETE FROM supplements")
    suspend fun deleteAllSupplements()

    @Query("SELECT * FROM supplement_taken WHERE date = :date")
    fun observeTakenOn(date: String): Flow<List<SupplementTakenEntity>>

    @Query("SELECT * FROM supplement_taken")
    suspend fun allTaken(): List<SupplementTakenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTaken(t: SupplementTakenEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTaken(list: List<SupplementTakenEntity>)

    @Query("DELETE FROM supplement_taken WHERE supplementId = :id AND date = :date")
    suspend fun clearTaken(id: String, date: String)

    @Query("DELETE FROM supplement_taken")
    suspend fun deleteAllTaken()

    // weight
    @Query("SELECT * FROM weights ORDER BY date")
    fun observeWeights(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY date")
    suspend fun allWeights(): List<WeightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setWeight(w: WeightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeights(list: List<WeightEntity>)

    @Query("DELETE FROM weights WHERE date = :date")
    suspend fun deleteWeight(date: String)

    @Query("DELETE FROM weights")
    suspend fun deleteAllWeights()

    // fasting
    @Query("SELECT * FROM fasts ORDER BY startMs DESC")
    fun observeFasts(): Flow<List<FastEntity>>

    @Query("SELECT * FROM fasts ORDER BY startMs")
    suspend fun allFasts(): List<FastEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFast(f: FastEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFasts(list: List<FastEntity>)

    @Query("DELETE FROM fasts WHERE id = :id")
    suspend fun deleteFast(id: String)

    @Query("DELETE FROM fasts")
    suspend fun deleteAllFasts()

    // meal prep
    @Query("SELECT * FROM batches WHERE deletedMs = 0 ORDER BY leftGrams <= 0.5, createdMs DESC")
    fun observeBatches(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches ORDER BY createdMs")
    suspend fun allBatches(): List<BatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(b: BatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatches(list: List<BatchEntity>)

    @Query("UPDATE batches SET deletedMs = :now WHERE id = :id")
    suspend fun deleteBatch(id: String, now: Long)

    @Query("DELETE FROM batches")
    suspend fun deleteAllBatches()

    // meal plans
    @Query("SELECT * FROM meal_plans WHERE deletedMs = 0 ORDER BY name COLLATE NOCASE")
    fun observePlans(): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plans ORDER BY createdMs")
    suspend fun allPlans(): List<MealPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(p: MealPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlans(list: List<MealPlanEntity>)

    @Query("UPDATE meal_plans SET deletedMs = :now WHERE id = :id")
    suspend fun deletePlan(id: String, now: Long)

    @Query("DELETE FROM meal_plans")
    suspend fun deleteAllPlans()
    // custom trackers
    @Query("SELECT * FROM food_trackers ORDER BY archived, sortOrder, name COLLATE NOCASE")
    fun observeTrackers(): Flow<List<FoodTrackerEntity>>

    @Query("SELECT * FROM food_trackers ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun allTrackers(): List<FoodTrackerEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM food_trackers")
    suspend fun maxTrackerOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracker(t: FoodTrackerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackers(list: List<FoodTrackerEntity>)

    @Query("DELETE FROM food_trackers WHERE id = :id")
    suspend fun deleteTracker(id: String)

    @Query("DELETE FROM food_trackers")
    suspend fun deleteAllTrackers()

    @Query("SELECT * FROM tracker_events WHERE date BETWEEN :from AND :to ORDER BY date, minuteOfDay, id")
    fun observeEventsBetween(from: String, to: String): Flow<List<TrackerEventEntity>>

    @Query("SELECT DISTINCT date FROM tracker_events")
    fun observeEventDates(): Flow<List<String>>

    @Query("SELECT * FROM tracker_events ORDER BY date, minuteOfDay, id")
    suspend fun allEvents(): List<TrackerEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(e: TrackerEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(list: List<TrackerEventEntity>)

    @Query("DELETE FROM tracker_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM tracker_events WHERE trackerId = :trackerId")
    suspend fun deleteEventsOf(trackerId: String)

    @Query("DELETE FROM tracker_events")
    suspend fun deleteAllEvents()
}

/** The parts of the Food tab you can switch on or off. */
enum class FoodPart(val id: String, val title: String, val hint: String, val onByDefault: Boolean) {
    WATER("water", "Water", "Glasses of water and the daily goal", true),
    SHAKE("shake", "Protein shake", "One tap logs your usual shake", true),
    SUPPLEMENTS("supplements", "Supplements", "Tablets and powders, ticked off each day", true),
    EXTRAS("extras", "Fibre, sugar and sodium", "The day's totals", true),
    VITAMINS("vitamins", "Vitamins and minerals", "The day's totals against your targets", true),
    WEIGHT("weight", "Body weight", "Weigh-ins and your goal weight", true),
    WEEK("week", "This week", "Average calories and protein, and days on target", true),
    PREP("prep", "Meal prep", "Log a whole pot once, eat it bit by bit", true),
    PLANS("plans", "Meal plans", "A day of meals to log with one tap", true),
    QUALITY("quality", "Protein quality", "Animal and plant protein, and the essential amino acids", false),
    FATS("fats", "Types of fat", "Saturated, mono- and polyunsaturated fat", false),
    WATCH("watch", "Vitamin watch", "Tells you when a vitamin or mineral you picked ran low over the last week", false),
    FASTING("fasting", "Fasting", "A timer for intermittent fasting", false),
    TRACKERS("trackers", "Custom trackers", "Count anything with one tap, such as a headache or a cup of tea, and the Diary timeline", false),
}

/** The two stacks of parts on the Food tab and how a saved order is applied to them. */
object FoodPartOrder {
    /** Above the "Details" heading, in the order they come in until you move them. */
    val TOP = listOf(FoodPart.FASTING, FoodPart.WATER, FoodPart.SHAKE, FoodPart.SUPPLEMENTS, FoodPart.TRACKERS, FoodPart.PREP, FoodPart.PLANS)

    /** Under the "Details" heading. */
    val DETAILS = listOf(FoodPart.EXTRAS, FoodPart.VITAMINS, FoodPart.WATCH, FoodPart.QUALITY, FoodPart.FATS, FoodPart.WEEK, FoodPart.WEIGHT)

    /** [parts] in the order of [saved] (ids); a part that is not in [saved] keeps its place among the others at the end. */
    fun sorted(saved: List<String>, parts: List<FoodPart>): List<FoodPart> {
        val rank = saved.withIndex().associate { it.value to it.index }
        return parts.withIndex().sortedWith(compareBy({ rank[it.value.id] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
    }

    /** [saved] after [part] moves [delta] places inside [parts] (-1 up, 1 down); unchanged when it is already at that end. */
    fun move(saved: List<String>, parts: List<FoodPart>, part: FoodPart, delta: Int): List<String> {
        val list = sorted(saved, parts).toMutableList()
        val i = list.indexOf(part)
        val j = i + delta
        if (i < 0 || j !in list.indices) return saved
        val other = list[j]
        list[j] = list[i]
        list[i] = other
        return saved.filter { id -> parts.none { it.id == id } } + list.map { it.id }
    }
}

/** Choices for the Food tab, kept on the phone and saved in the backup with every other setting. */
object FoodSettings {
    private const val FILE = "food_tracking"
    private val tick = MutableStateFlow(0)

    /** Goes up each time something changes, so an open screen can look again. */
    val changes: StateFlow<Int> = tick

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun changed() {
        tick.value = tick.value + 1
    }

    fun isOn(context: Context, part: FoodPart): Boolean = prefs(context).getBoolean("on_" + part.id, part.onByDefault)

    fun setOn(context: Context, part: FoodPart, on: Boolean) {
        prefs(context).edit { putBoolean("on_" + part.id, on) }
        changed()
    }

    /** The order you put the parts in, as part ids (parts you never moved are not in it). */
    fun order(context: Context): List<String> = (prefs(context).getString("order", "") ?: "").split(',').filter { it.isNotBlank() }

    /** Moves [part] one place up (-1) or down (1) inside its stack [parts]. */
    fun move(context: Context, parts: List<FoodPart>, part: FoodPart, delta: Int) {
        val next = FoodPartOrder.move(order(context), parts, part, delta)
        prefs(context).edit { putString("order", next.joinToString(",")) }
        changed()
    }

    /** Calories burned on a day, typed in by hand; they are added to that day's budget. */
    fun burnedKcal(context: Context, date: String): Int = prefs(context).getInt("burned_$date", 0)

    fun setBurnedKcal(context: Context, date: String, kcal: Int) {
        prefs(context).edit { if (kcal <= 0) remove("burned_$date") else putInt("burned_$date", kcal.coerceAtMost(20_000)) }
        changed()
    }

    /** The nutrients the vitamin watch keeps an eye on (ids from the nutrient list). */
    fun watchIds(context: Context): Set<String> = prefs(context).getStringSet("watch_ids", emptySet()) ?: emptySet()

    fun setWatch(context: Context, id: String, on: Boolean) {
        val all = watchIds(context).toMutableSet()
        if (on) all += id else all -= id
        prefs(context).edit { putStringSet("watch_ids", all) }
        changed()
    }

    /** Lets the screens look again after something changed outside them (a reminder button, say). */
    fun touch() = changed()

    /** Meal reminders: off until you switch them on; each meal has its own time and can be left out. */
    fun mealReminders(context: Context): Boolean = prefs(context).getBoolean("meal_reminders", false)

    fun setMealReminders(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("meal_reminders", on) }
        changed()
    }

    fun mealRemindOn(context: Context, meal: String): Boolean = prefs(context).getBoolean("remind_on_$meal", true)

    fun setMealRemindOn(context: Context, meal: String, on: Boolean) {
        prefs(context).edit { putBoolean("remind_on_$meal", on) }
        changed()
    }

    fun mealMinute(context: Context, meal: String): Int = prefs(context).getInt("remind_at_$meal", FoodReminderRules.defaultMinute(meal))

    fun setMealMinute(context: Context, meal: String, minute: Int) {
        prefs(context).edit { putInt("remind_at_$meal", minute.coerceIn(0, 1439)) }
        changed()
    }

    /** Water reminders: off until you switch them on; they ring every [WaterWindow.interval] minutes between [WaterWindow.start] and [WaterWindow.end]. */
    class WaterWindow(val start: Int, val end: Int, val interval: Int)

    fun waterReminders(context: Context): Boolean = prefs(context).getBoolean("water_reminders", false)

    fun setWaterReminders(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("water_reminders", on) }
        changed()
    }

    fun waterWindow(context: Context): WaterWindow {
        val p = prefs(context)
        val start = p.getInt("water_from", 8 * 60).coerceIn(0, 1379)
        val end = p.getInt("water_to", 22 * 60).coerceIn(start + 60, 1439)
        return WaterWindow(start, end, p.getInt("water_every", 120).coerceIn(30, 360))
    }

    fun setWaterWindow(context: Context, start: Int, end: Int, interval: Int) {
        prefs(context).edit { putInt("water_from", start.coerceIn(0, 1379)); putInt("water_to", end.coerceIn(60, 1439)); putInt("water_every", interval.coerceIn(30, 360)) }
        changed()
    }

    /** Whether the calories burned on a day also come from Health Connect, or from your steps when it has none (off until you switch it on). */
    fun autoBurned(context: Context): Boolean = prefs(context).getBoolean("auto_burned", false)

    fun setAutoBurned(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("auto_burned", on) }
        changed()
    }

    /** Active calories Health Connect reported for a day, or 0. */
    fun healthKcal(context: Context, date: String): Int = prefs(context).getInt("burned_hc_$date", 0)

    fun setHealthKcal(context: Context, date: String, kcal: Int) {
        prefs(context).edit { if (kcal <= 0) remove("burned_hc_$date") else putInt("burned_hc_$date", kcal.coerceAtMost(20_000)) }
        changed()
    }

    /** The calories burned that count on [date]: what you typed, else (when switched on) Health Connect's number, else an estimate from [steps]. */
    fun burnedFor(context: Context, date: String, steps: Int, kg: Double): BurnedMath.Burned {
        val auto = autoBurned(context)
        return BurnedMath.pick(burnedKcal(context, date), if (auto) healthKcal(context, date) else 0, if (auto) steps else 0, kg)
    }

    fun waterGoalMl(context: Context): Int = prefs(context).getInt("water_goal", FoodStats.DEFAULT_WATER_GOAL_ML)
    fun glassMl(context: Context): Int = prefs(context).getInt("glass_ml", 250)

    fun setWater(context: Context, goalMl: Int, glassMl: Int) {
        prefs(context).edit { putInt("water_goal", goalMl.coerceIn(500, 10_000)); putInt("glass_ml", glassMl.coerceIn(50, 2_000)) }
        changed()
    }

    /** Whether a supplement with a time rings a reminder at that time (off until you switch it on). */
    fun suppReminders(context: Context): Boolean = prefs(context).getBoolean("supp_reminders", false)

    fun setSuppReminders(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("supp_reminders", on) }
        changed()
    }

    /** The foods you marked as shakes ([FoodRef] texts), and the one the shaker button logs. */
    fun shakeRefs(context: Context): Set<String> = prefs(context).getStringSet("shake_refs", emptySet()) ?: emptySet()
    fun shakeRef(context: Context): String = prefs(context).getString("shake_ref", "") ?: ""

    fun setShake(context: Context, ref: String) {
        val all = shakeRefs(context).toMutableSet()
        if (ref.isNotEmpty()) all += ref
        prefs(context).edit { putStringSet("shake_refs", all); putString("shake_ref", ref) }
        changed()
    }

    fun removeShake(context: Context, ref: String) {
        val all = shakeRefs(context).toMutableSet().also { it.remove(ref) }
        val current = shakeRef(context).let { if (it == ref) all.firstOrNull() ?: "" else it }
        prefs(context).edit { putStringSet("shake_refs", all); putString("shake_ref", current) }
        changed()
    }

    fun weightGoalKg(context: Context): Double = prefs(context).getFloat("weight_goal", 0f).toDouble()

    fun setWeightGoal(context: Context, kg: Double) {
        prefs(context).edit { putFloat("weight_goal", kg.coerceAtLeast(0.0).toFloat()) }
        changed()
    }

    /** When the fast that is running started (epoch milliseconds), or 0 when none is running. */
    fun fastStartMs(context: Context): Long = prefs(context).getLong("fast_start", 0L)
    fun fastGoalHours(context: Context): Int = prefs(context).getInt("fast_goal", 16)

    fun setFast(context: Context, startMs: Long, goalHours: Int) {
        prefs(context).edit { putLong("fast_start", startMs); putInt("fast_goal", goalHours.coerceIn(1, 72)) }
        changed()
    }
}

// ---- the rules behind them, free of Android so they can be tested ----

object SupplementRules {
    const val MON_BIT = 1

    /** The bit of [weekday] (1 = Monday ... 7 = Sunday) in [SupplementEntity.weekdays]. */
    fun bit(weekday: Int): Int = 1 shl weekday

    fun dueOn(s: SupplementEntity, day: LocalDate): Boolean {
        if (s.archived) return false
        val from = s.fromDate.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val until = s.untilDate.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (from != null && day.isBefore(from)) return false
        if (until != null && day.isAfter(until)) return false
        if (s.everyDays > 1 && from != null) return java.time.temporal.ChronoUnit.DAYS.between(from, day) % s.everyDays == 0L
        return s.weekdays == 0 || s.weekdays and bit(day.dayOfWeek.value) != 0
    }

    private val NAMES = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun summary(s: SupplementEntity): String = when {
        s.everyDays > 1 -> "Every ${s.everyDays} days"
        s.weekdays == 0 || s.weekdays == (1..7).sumOf { bit(it) } -> "Every day"
        else -> (1..7).filter { s.weekdays and bit(it) != 0 }.joinToString(", ") { NAMES[it] }
    }
}

object FastMath {
    /** How far a running fast is, as a share of the goal (above 1 once the goal is passed). */
    fun progress(startMs: Long, nowMs: Long, goalHours: Int): Double =
        if (startMs <= 0 || goalHours <= 0) 0.0 else (nowMs - startMs).coerceAtLeast(0) / (goalHours * 3_600_000.0)

    fun reached(f: FastEntity): Boolean = f.endMs - f.startMs >= f.goalHours * 3_600_000L

    /** How many of the latest fasts in a row reached their goal. */
    fun streak(fasts: List<FastEntity>): Int {
        var n = 0
        for (f in fasts.sortedByDescending { it.startMs }) {
            if (reached(f)) n++ else break
        }
        return n
    }

    data class Week(val count: Int, val reached: Int, val avgMinutes: Long)

    /** The fasts that ended in the 7 days before [nowMs]. */
    fun week(fasts: List<FastEntity>, nowMs: Long): Week? {
        val recent = fasts.filter { it.endMs in (nowMs - 7 * 86_400_000L)..nowMs }
        if (recent.isEmpty()) return null
        return Week(recent.size, recent.count { reached(it) }, recent.sumOf { (it.endMs - it.startMs) / 60_000 } / recent.size)
    }

    /** "13:05" for a length of time. */
    fun clock(ms: Long): String {
        val minutes = (ms.coerceAtLeast(0) / 60_000)
        return "%d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60)
    }
}

object WeightMath {
    /** Change per week over the last [days] days of weigh-ins (needs two weigh-ins at least 3 days apart), or null. */
    fun kgPerWeek(weights: List<WeightEntity>, today: LocalDate, days: Int = 28): Double? {
        val from = today.minusDays(days.toLong())
        val pts = weights.mapNotNull { w -> runCatching { LocalDate.parse(w.date) }.getOrNull()?.let { it to w.kg } }
            .filter { !it.first.isBefore(from) && !it.first.isAfter(today) }.sortedBy { it.first }
        if (pts.size < 2) return null
        val span = java.time.temporal.ChronoUnit.DAYS.between(pts.first().first, pts.last().first)
        if (span < 3) return null
        return (pts.last().second - pts.first().second) / (span / 7.0)
    }

    /** "2.4 kg to lose", "1 kg to gain" or "Goal reached" for [latest] against [goal]; null without a goal. */
    fun toGoal(latest: Double, goal: Double): String? {
        if (goal <= 0 || latest <= 0) return null
        val gap = goal - latest
        if (Math.abs(gap) < 0.05) return "Goal reached"
        val shown = (Math.round(Math.abs(gap) * 10) / 10.0).toString().removeSuffix(".0")
        return if (gap < 0) "$shown kg to lose" else "$shown kg to gain"
    }
}

object BatchMath {
    private fun sum(items: List<SavedMealItem>, pick: (SavedMealItem) -> Double) = items.sumOf { pick(it) * it.grams / 100 }

    fun rawGrams(items: List<SavedMealItem>): Double = items.sumOf { it.grams }

    fun totalGrams(b: BatchEntity, items: List<SavedMealItem> = SavedMealItems.decode(b.items)): Double =
        if (b.cookedGrams > 0) b.cookedGrams else rawGrams(items)

    /** The batch as one food: the numbers of all ingredients spread over what the pot weighs. One portion is a share of the pot. */
    fun asFood(b: BatchEntity): Loggable {
        val items = SavedMealItems.decode(b.items)
        val total = totalGrams(b, items).takeIf { it > 0 } ?: 100.0
        val k = 100.0 / total
        val micro = LinkedHashMap<String, Double>()
        for (i in items) for ((id, v) in NutrientValues.decode(i.extra)) micro[id] = (micro[id] ?: 0.0) + v * i.grams / 100 * k
        return Loggable(
            ref = "", name = b.name,
            per100 = Per100(
                kcal = sum(items) { it.kcal100 } * k, protein = sum(items) { it.protein100 } * k,
                carbs = sum(items) { it.carbs100 } * k, fat = sum(items) { it.fat100 } * k,
                fiber = sum(items) { it.fiber100 } * k, sugar = sum(items) { it.sugar100 } * k,
                sodiumMg = sum(items) { it.sodium100 } * k, animalProtein = sum(items) { it.animal100 } * k,
                sat = sum(items) { it.sat100 } * k, mono = sum(items) { it.mono100 } * k, poly = sum(items) { it.poly100 } * k,
            ),
            micro = micro, liquid = false, portion = PortionKind.Serving,
            portionGrams = total / b.portions.coerceAtLeast(1), cat = null, cookedYield = 0.0, photoAsset = null,
        )
    }

    /**
     * The batch as a food of your own, so it turns up in search and quick picks like any other: numbers per 100 g of what the pot
     * weighs, one "serving" being a share of the pot ([BatchEntity.portions]). Also returns its vitamin numbers, by nutrient id.
     */
    fun asOwnFood(b: BatchEntity, id: String = FoodLogic.newFoodId(), nowMs: Long = System.currentTimeMillis()): Pair<FoodEntity, Map<String, Double>> {
        val f = asFood(b)
        val per = f.per100
        val entity = FoodEntity(
            id = id, name = b.name, kcal100 = per.kcal, protein100 = per.protein, carbs100 = per.carbs, fat100 = per.fat,
            fiber100 = per.fiber, sugar100 = per.sugar, sodium100 = per.sodiumMg, sat100 = per.sat, mono100 = per.mono, poly100 = per.poly,
            animal100 = per.animalProtein, portionKind = PortionKind.Serving.key, portionGrams = f.portionGrams, createdMs = nowMs,
        )
        return entity to f.micro
    }

    fun kcalTotal(b: BatchEntity): Double = SavedMealItems.decode(b.items).sumOf { it.kcal }

    fun finished(b: BatchEntity): Boolean = b.leftGrams <= 0.5

    fun leftShare(b: BatchEntity): Double {
        val total = totalGrams(b)
        return if (total <= 0) 0.0 else (b.leftGrams / total).coerceIn(0.0, 1.0)
    }

    fun newId(): String = "b:" + UUID.randomUUID().toString().take(12)
}

object PlanMath {
    fun newId(): String = "p:" + UUID.randomUUID().toString().take(12)

    fun kcal(p: MealPlanEntity): Double = p.allItems.sumOf { it.kcal }

    /** The meals a plan adds to a day: only for meal types that have nothing yet ("log the empty meals"). */
    fun mealsToAdd(
        plan: MealPlanEntity, date: String, existing: List<MealEntity>, minuteFor: (String) -> Int, newId: () -> String,
    ): List<MealEntity> = FoodLogic.MEAL_TYPES
        .filter { type -> existing.none { it.mealType == type } }
        .flatMap { type -> plan.itemsOf(type).map { it.asMeal(newId(), date, minuteFor(type), type) } }
}

object WeekMath {
    data class Summary(
        val loggedDays: Int,
        val avgKcal: Double,
        val avgProtein: Double,
        val daysOnTarget: Int,
    )

    /**
     * Averages over the days that have food, and how many of them are on target: calories within 10 % of the goal and at least
     * 90 % of the protein goal (the same rule GymMane uses). Without a goal no day counts as on target.
     */
    fun summary(meals: List<MealEntity>, goalFor: (String) -> FoodGoalEntity?): Summary {
        val byDay = meals.groupBy { it.date }
        if (byDay.isEmpty()) return Summary(0, 0.0, 0.0, 0)
        var onTarget = 0
        for ((day, list) in byDay) {
            val goal = goalFor(day) ?: continue
            if (goal.kcal <= 0) continue
            val kcal = list.sumOf { it.kcal }
            val protein = list.sumOf { it.protein }
            val kcalOk = Math.abs(kcal - goal.kcal) <= goal.kcal * 0.10
            val proteinOk = goal.protein <= 0 || protein >= goal.protein * 0.9
            if (kcalOk && proteinOk) onTarget++
        }
        return Summary(
            loggedDays = byDay.size,
            avgKcal = meals.sumOf { it.kcal } / byDay.size,
            avgProtein = meals.sumOf { it.protein } / byDay.size,
            daysOnTarget = onTarget,
        )
    }
}

/** What is in the day's protein and fat, and how the essential amino acids compare with what an adult needs. */
object QualityMath {
    /** Essential amino acids in the order of [Per100.amino]. */
    val AMINO_NAMES = listOf("Histidine", "Isoleucine", "Leucine", "Lysine", "Methionine + cysteine", "Phenylalanine + tyrosine", "Threonine", "Tryptophan", "Valine")

    /** Milligrams per kilogram of body weight per day that the WHO lists for adults (2007), in the same order. */
    val NEED_MG_PER_KG = listOf(10.0, 20.0, 39.0, 30.0, 15.0, 25.0, 15.0, 4.0, 26.0)

    /** Grams of each essential amino acid eaten (all zeros when no food had the numbers). */
    fun aminoEaten(meals: List<MealEntity>): List<Double> {
        val out = DoubleArray(9)
        for (m in meals) {
            val values = m.amino.split(',').mapNotNull { it.toDoubleOrNull() }
            if (values.size != 9) continue
            for (i in 0 until 9) out[i] += values[i] * m.grams / 100
        }
        return out.toList()
    }

    /** The share of the daily need for each amino acid ([eatenGrams] against [bodyKg]); 1.0 means the need is met. */
    fun aminoShares(eatenGrams: List<Double>, bodyKg: Double): List<Double> =
        eatenGrams.mapIndexed { i, g -> g * 1000 / (NEED_MG_PER_KG[i] * bodyKg.coerceAtLeast(30.0)) }

    data class Fats(val sat: Double, val mono: Double, val poly: Double)

    fun fats(meals: List<MealEntity>) = Fats(
        sat = meals.sumOf { it.sat100 * it.grams / 100 },
        mono = meals.sumOf { it.mono100 * it.grams / 100 },
        poly = meals.sumOf { it.poly100 * it.grams / 100 },
    )

    /** Share of the day's protein that comes from animals (0 to 1), or null when no protein was logged. */
    fun animalShare(meals: List<MealEntity>): Double? {
        val protein = meals.sumOf { it.protein }
        if (protein <= 0) return null
        return (meals.sumOf { it.animal100 * it.grams / 100 } / protein).coerceIn(0.0, 1.0)
    }
}

object TrackerMath {
    fun newId(): String = "t:" + UUID.randomUUID().toString().take(12)
    fun newEventId(): String = "e:" + UUID.randomUUID().toString().take(12)

    /** One tracker's taps on a day: how many, and when. */
    data class Chip(val trackerId: String, val name: String, val icon: String, val color: Long, val minutes: List<Int>) {
        val count: Int get() = minutes.size
        val text: String get() = if (count <= 1) name else "$name ×$count"
    }

    /** The taps of a day grouped by tracker, in the order of the trackers. Taps of a tracker that no longer exists are left out. */
    fun chips(events: List<TrackerEventEntity>, trackers: List<FoodTrackerEntity>): List<Chip> =
        trackers.mapNotNull { t ->
            val mine = events.filter { it.trackerId == t.id }.map { it.minuteOfDay }.sorted()
            if (mine.isEmpty()) null else Chip(t.id, t.name, t.icon, t.color, mine)
        }

    /** How many times [trackerId] was tapped on each of [days]. */
    fun counts(events: List<TrackerEventEntity>, days: List<LocalDate>, trackerId: String): List<Int> =
        days.map { d -> events.count { it.trackerId == trackerId && it.date == d.toString() } }
}

/** One day of the Diary timeline. */
data class DiaryDay(val date: LocalDate, val meals: List<MealEntity>, val events: List<TrackerEventEntity>) {
    val kcal: Double get() = meals.sumOf { it.kcal }
    val hasEvents: Boolean get() = events.isNotEmpty()
}

object DiaryMath {
    /** The days from newest to oldest that have food or taps; days with neither are left out. */
    fun days(meals: List<MealEntity>, events: List<TrackerEventEntity>): List<DiaryDay> {
        val mealsBy = meals.groupBy { it.date }
        val eventsBy = events.groupBy { it.date }
        return (mealsBy.keys + eventsBy.keys).mapNotNull { key -> runCatching { LocalDate.parse(key) }.getOrNull() }
            .distinct().sortedDescending()
            .map { d ->
                DiaryDay(
                    date = d,
                    meals = mealsBy[d.toString()].orEmpty().sortedBy { it.minuteOfDay },
                    events = eventsBy[d.toString()].orEmpty().sortedBy { it.minuteOfDay },
                )
            }
    }
}
