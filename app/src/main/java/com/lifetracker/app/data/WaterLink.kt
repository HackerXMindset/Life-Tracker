package com.lifetracker.app.data

import android.app.Application
import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How many millilitres one unit of a water habit is. Only units that mean a volume can be linked. */
object WaterUnits {
    fun mlPer(unit: String): Double? = when (unit.trim().lowercase()) {
        "ml", "millilitre", "millilitres", "milliliter", "milliliters" -> 1.0
        "l", "litre", "litres", "liter", "liters" -> 1000.0
        "glass", "glasses", "cup", "cups" -> 250.0
        "oz", "fl oz", "ounce", "ounces" -> 29.57
        else -> null
    }

    fun toMl(count: Double, unit: String): Int? = mlPer(unit)?.let { (count * it).roundToInt() }

    fun fromMl(ml: Int, unit: String): Double? = mlPer(unit)?.let { HabitTicks.round(ml / it) }
}

/**
 * Keeps the water of the Food tab (millilitres a day) and a counted water habit of the Habits tab the same, whichever you change.
 * Pure: it only plans which side gets which value.
 */
object WaterSync {
    /** What to write on each side, and the agreed state to remember for next time. */
    data class Plan(val water: Map<String, Int>, val habit: Map<String, Int>, val agreed: Map<String, Int>)

    /**
     * [prev] is what both sides held last time (null the first time). A side that changed since then wins; when both changed, or the
     * first time, the larger one wins so nothing you drank is lost. Differences of 1 ml or less are rounding and are ignored.
     */
    fun step(prev: Map<String, Int>?, water: Map<String, Int>, habit: Map<String, Int>): Plan {
        val writeWater = HashMap<String, Int>()
        val writeHabit = HashMap<String, Int>()
        val agreed = HashMap<String, Int>()
        for (d in water.keys + habit.keys) {
            val w = water[d] ?: 0
            val h = habit[d] ?: 0
            if (abs(w - h) <= 1) {
                if (w > 0 || h > 0) agreed[d] = w
                continue
            }
            val p = prev?.get(d)
            val wChanged = p == null || abs(w - p) > 1
            val hChanged = p == null || abs(h - p) > 1
            val value = when {
                wChanged && !hChanged -> w
                hChanged && !wChanged -> h
                else -> maxOf(w, h)
            }
            if (abs(w - value) > 1) writeWater[d] = value
            if (abs(h - value) > 1) writeHabit[d] = value
            if (value > 0) agreed[d] = value
        }
        return Plan(writeWater, writeHabit, agreed)
    }
}

/** Runs the water link while the app's process is alive. Started once from the application class. */
object WaterLink {
    private var started = false

    /** The habit to mirror: the saved one, or one named Water whose unit is a volume, or null. */
    fun pick(habits: List<HabitEntity>, savedId: String): HabitEntity? {
        habits.firstOrNull { it.id == savedId && it.kind == HabitStats.KIND_COUNTED && WaterUnits.mlPer(it.unitLabel) != null }?.let { return it }
        return habits.firstOrNull {
            it.kind == HabitStats.KIND_COUNTED && !it.archived && TrackerMigration.fold(it.name) == "water" && WaterUnits.mlPer(it.unitLabel) != null
        }
    }

    fun start(app: Application) {
        if (started) return
        started = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = AppDatabase.get(app)
        scope.launch {
            var prev: Map<String, Int>? = null
            var lastId = ""
            FoodSettings.changes.collectLatest {
                if (!FoodSettings.waterLinked(app)) { prev = null; return@collectLatest }
                val habit = ensureHabit(app, db)
                if (habit == null) return@collectLatest
                if (habit.id != lastId) { prev = null; lastId = habit.id }
                val unit = habit.unitLabel
                combine(db.foodDao().observeAllWater(), db.habitDao().observeCompletions()) { waters, comps -> waters to comps }
                    .collect { (waters, comps) ->
                        val w = waters.associate { it.date to it.ml }
                        val h = comps.filter { it.habitId == habit.id }.associate { it.date to (WaterUnits.toMl(it.count, unit) ?: 0) }
                        val plan = WaterSync.step(prev, w, h)
                        if (plan.water.isNotEmpty()) {
                            db.foodDao().upsertWater(plan.water.map { (d, ml) -> WaterEntity(d, ml) })
                        }
                        if (plan.habit.isNotEmpty()) {
                            val today = LocalDate.now().toString()
                            val now = LocalDateTime.now().let { it.hour * 60 + it.minute }
                            for ((d, ml) in plan.habit) {
                                val old = comps.firstOrNull { it.habitId == habit.id && it.date == d }
                                val next = HabitTicks.withTotal(old, habit.id, d, WaterUnits.fromMl(ml, unit) ?: 0.0, if (d == today) now else null)
                                if (next == null) db.habitDao().deleteCompletion(habit.id, d) else db.habitDao().upsertCompletion(next)
                            }
                            HabitSyncState(app).stamp(habit.id)
                        }
                        prev = plan.agreed
                    }
            }
        }
    }

    /** The habit to link, made if there is none. If the saved one was deleted, the link is switched off instead of making another. */
    private suspend fun ensureHabit(context: Context, db: AppDatabase): HabitEntity? {
        val dao = db.habitDao()
        val all = dao.allHabits()
        val live = all.filter { it.deletedAt == 0L }
        val saved = FoodSettings.waterHabit(context)
        pick(live, saved)?.let {
            if (it.id != saved) FoodSettings.setWaterHabit(context, it.id)
            return it
        }
        if (saved.isNotEmpty() && all.any { it.id == saved }) {
            FoodSettings.setWaterLinked(context, false)
            return null
        }
        val glass = FoodSettings.glassMl(context).toDouble()
        val form = HabitForm(
            name = "Water", icon = "glassWater", color = 0xFF2E86DEL, kind = HabitStats.KIND_COUNTED,
            target = FoodSettings.waterGoalMl(context).toDouble(), unitLabel = "ml", step = glass, anyAmount = false,
        )
        val id = HabitEditor.newId()
        val habit = HabitEditor.build(form, null, id, dao.maxHabitOrder() + 1, LocalDate.now(), LocalDateTime.now())
        dao.upsertHabit(habit)
        HabitSyncState(context).stamp(id)
        FoodSettings.setWaterHabit(context, id)
        return habit
    }
}
