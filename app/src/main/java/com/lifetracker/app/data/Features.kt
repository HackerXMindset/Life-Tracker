package com.lifetracker.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The parts of the app you can switch off. Off means it disappears from the tabs, Settings, the Timeline and Stats, and
 * nothing new is collected for it until you switch it on again. What was collected before is kept.
 */
enum class Feature(val id: String, val title: String, val hint: String, val tabName: String? = null) {
    FOOD("food", "Food", "Meals and calories, and the Food tab", "Food"),
    HABITS("habits", "Habits", "Habits, their reminders, the focus timer and the Habits tab", "Habits"),
    MONEY("money", "Money", "Spending and income, monthly items and the Money tab", "Money"),
    STATS("stats", "Stats", "The Stats tab", "Stats"),
    STEPS_SLEEP("steps", "Steps and sleep", "Steps, sleep and Health Connect"),
    PHONE_USAGE("phone", "Phone usage", "How long you use each app"),
    CALLS("calls", "Calls", "Your calls, copied from the call log"),
    CHARGING("charging", "Charging", "When and how your phone charges"),
    PLACES("places", "Places and trips", "Your position, stays, trips and the live map"),
    MAPS("maps", "Maps", "Map pictures, offline areas and their downloads"),
    RULES("rules", "Rules", "Things that happen by themselves when something else happens"),
}

/** The small rules around the switches, free of Android so they can be tested. */
object FeatureRules {
    /** The tabs to show, in order: Timeline and Settings are always there. */
    fun tabNames(isOn: (Feature) -> Boolean): List<String> =
        listOf("Timeline") + Feature.entries.filter { it.tabName != null && isOn(it) }.map { it.tabName!! } + "Settings"

    /** [current] if it is still showing, otherwise the Timeline. */
    fun landing(current: String, visible: List<String>): String = if (current in visible) current else "Timeline"

    /** The ids of the features that are off, for the line under the Features row. */
    fun offTitles(isOn: (Feature) -> Boolean): List<String> = Feature.entries.filter { !isOn(it) }.map { it.title }

    /** "All on", "Food is off" or "Food, Money are off". */
    fun summary(isOn: (Feature) -> Boolean): String {
        val off = offTitles(isOn)
        return when (off.size) {
            0 -> "All on"
            1 -> off[0] + " is off"
            else -> off.joinToString(", ") + " are off"
        }
    }
}

/** Where the switches are kept. Everything starts on. */
object Features {
    private const val FILE = "features"
    private val tick = MutableStateFlow(0)

    /** Goes up each time a switch changes, so a screen can look again. */
    val changes: StateFlow<Int> = tick

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOn(context: Context, feature: Feature): Boolean = !prefs(context).getBoolean("off_" + feature.id, false)

    fun set(context: Context, feature: Feature, on: Boolean) {
        val app = context.applicationContext
        if (isOn(app, feature) == on) return
        prefs(app).edit { putBoolean("off_" + feature.id, !on) }
        tick.value = tick.value + 1
        AppLog.i("Features", feature.title + (if (on) " switched on" else " switched off: nothing new is collected for it until it is switched on"))
        CoroutineScope(Dispatchers.IO).launch { runCatching { applyEffects(app, feature, on) } }
    }

    /** Starts or stops the background work that belongs to [feature]. Work that only runs now and then checks the switch itself. */
    private suspend fun applyEffects(context: Context, feature: Feature, on: Boolean) {
        when (feature) {
            Feature.PLACES -> {
                if (on) {
                    if (PlacesSettings(context).enabled) {
                        PlacesWorker.schedule(context)
                        PlacesTracker.syncAll(context)
                        PlacesTracker.resumeIfNeeded(context)
                    }
                } else {
                    TrackService.stop(context)
                    PlaceWatchService.stop(context)
                    PlacesWorker.cancel(context)
                    PlacesTracker.syncAll(context)
                }
            }
            Feature.CHARGING -> if (on) {
                if (ChargeSettings(context).track) ChargeWorker.schedule(context)
            } else {
                ChargeWorker.cancel(context)
            }
            Feature.HABITS -> if (on) HabitReminderScheduler.rearmAll(context)
            Feature.RULES -> RulesWorker.refresh(context)
            else -> Unit
        }
    }
}
