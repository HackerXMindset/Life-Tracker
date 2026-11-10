package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifetracker.app.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** When the meal and water reminders ring, and when they stay quiet. */
object FoodReminderRules {
    val MEALS = listOf(FoodLogic.BREAKFAST, FoodLogic.LUNCH, FoodLogic.SNACK, FoodLogic.DINNER)

    fun defaultMinute(meal: String): Int = when (meal) {
        FoodLogic.BREAKFAST -> 8 * 60 + 30
        FoodLogic.LUNCH -> 13 * 60
        FoodLogic.SNACK -> 17 * 60
        else -> 20 * 60
    }

    private fun at(day: LocalDate, minute: Int): LocalDateTime = LocalDateTime.of(day, LocalTime.of(minute / 60, minute % 60))

    /** The next time after [now] that [minute] of the day comes round. */
    fun nextDaily(minute: Int, now: LocalDateTime): LocalDateTime {
        val today = at(now.toLocalDate(), minute.coerceIn(0, 1439))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** The minutes of the day water rings: from [start] every [interval] minutes up to and including [end]. */
    fun waterSlots(start: Int, end: Int, interval: Int): List<Int> {
        val step = interval.coerceAtLeast(30)
        return generateSequence(start.coerceIn(0, 1439)) { it + step }.takeWhile { it <= end.coerceIn(0, 1439) }.toList()
    }

    /** The next water ring after [now]: a later slot today, else the first slot tomorrow. */
    fun nextWater(now: LocalDateTime, start: Int, end: Int, interval: Int): LocalDateTime {
        val slots = waterSlots(start, end, interval).ifEmpty { listOf(start.coerceIn(0, 1439)) }
        slots.forEach { m -> at(now.toLocalDate(), m).let { if (it.isAfter(now)) return it } }
        return at(now.toLocalDate().plusDays(1), slots.first())
    }

    /** A meal reminder stays quiet when anything is already logged for that meal today. */
    fun mealLogged(meals: List<MealEntity>, date: String, meal: String): Boolean = meals.any { it.date == date && it.mealType == meal }

    /**
     * Water is on track when what you drank is at least the share of the goal that the waking window has used up so far
     * (half the window gone means half the goal), or the goal is already met.
     */
    fun waterOnTrack(drunkMl: Int, goalMl: Int, nowMinute: Int, start: Int, end: Int): Boolean {
        if (goalMl <= 0 || drunkMl >= goalMl) return true
        val span = (end - start).coerceAtLeast(1)
        val used = ((nowMinute - start).toDouble() / span).coerceIn(0.0, 1.0)
        return drunkMl >= goalMl * used
    }
}

/** Meal and water reminders: one alarm per meal and one for water, each always set for its next moment only. Off until switched on. */
object FoodReminderScheduler {
    private const val SCHEME = "lifetracker-foodrem"
    const val ACTION_RING = "com.lifetracker.app.FOOD_REMINDER_RING"
    const val ACTION_DRINK = "com.lifetracker.app.FOOD_REMINDER_DRINK"
    const val EXTRA_ID = "reminder"
    private const val WATER = "water"
    private fun mealId(meal: String) = "meal-$meal"

    private fun pending(context: Context, action: String, kind: String, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, FoodReminderReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("$SCHEME://$kind/${Uri.encode(id)}"))
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun drinkIntent(context: Context): PendingIntent = pending(context, ACTION_DRINK, "drink", WATER)

    private fun cancelAlarm(context: Context, id: String) {
        val p = pending(context, ACTION_RING, "ring", id)
        runCatching { context.getSystemService(android.app.AlarmManager::class.java).cancel(p) }
        p.cancel()
    }

    private fun mealsOn(app: Context) = Features.isOn(app, Feature.FOOD) && FoodSettings.mealReminders(app)
    private fun waterOn(app: Context) = Features.isOn(app, Feature.FOOD) && FoodSettings.waterReminders(app)

    /** Sets every alarm again from the settings as they are now, and takes away those that are switched off. */
    fun rearmAll(context: Context) {
        val app = context.applicationContext
        val now = LocalDateTime.now()
        for (meal in FoodReminderRules.MEALS) {
            val id = mealId(meal)
            if (mealsOn(app) && FoodSettings.mealRemindOn(app, meal)) {
                HabitReminderScheduler.setAlarm(app, FoodReminderRules.nextDaily(FoodSettings.mealMinute(app, meal), now), pending(app, ACTION_RING, "ring", id))
            } else {
                cancelAlarm(app, id)
            }
        }
        if (waterOn(app)) {
            val w = FoodSettings.waterWindow(app)
            HabitReminderScheduler.setAlarm(app, FoodReminderRules.nextWater(now, w.start, w.end, w.interval), pending(app, ACTION_RING, "ring", WATER))
        } else {
            cancelAlarm(app, WATER)
        }
        if (!mealsOn(app) && !waterOn(app)) FoodReminderNotifier.cancelAll(app)
    }

    /** An alarm went off: say it unless that meal is logged or water is on track, then set the next one. */
    suspend fun ring(context: Context, id: String) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).foodDao()
        val today = LocalDate.now()
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        if (id == WATER) {
            if (waterOn(app)) {
                val w = FoodSettings.waterWindow(app)
                val drunk = dao.water(today.toString())?.ml ?: 0
                val goal = FoodSettings.waterGoalMl(app)
                if (!FoodReminderRules.waterOnTrack(drunk, goal, nowMinute, w.start, w.end)) FoodReminderNotifier.postWater(app, drunk, goal)
            }
        } else if (id.startsWith("meal-")) {
            val meal = id.removePrefix("meal-")
            if (mealsOn(app) && FoodSettings.mealRemindOn(app, meal) && !FoodReminderRules.mealLogged(dao.mealsOnceOn(today.toString()), today.toString(), meal)) {
                FoodReminderNotifier.postMeal(app, meal)
            }
        }
        rearmAll(app)
    }

    /** The "+ glass" button on the water banner. */
    suspend fun drink(context: Context) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).foodDao()
        val day = LocalDate.now().toString()
        val current = dao.water(day)?.ml ?: 0
        dao.setWater(WaterEntity(day, current + FoodSettings.glassMl(app)))
        FoodSettings.touch()
        FoodReminderNotifier.cancel(app, WATER)
    }
}

/** The banners of the meal and water reminders. */
object FoodReminderNotifier {
    private const val CHANNEL = "food-reminders"
    private const val ID = 6003

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Meal and water reminders")
            .setDescription("A nudge at meal times and through the day, only when the meal is not logged or water is behind")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    private fun post(context: Context, tag: String, title: String, text: String, drink: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open(context))
            .setAutoCancel(true)
        if (drink) builder.addAction(0, "Drank a glass", FoodReminderScheduler.drinkIntent(context))
        runCatching { NotificationManagerCompat.from(context).notify(tag, ID, builder.build()) }
    }

    fun postMeal(context: Context, meal: String) =
        post(context, "meal-$meal", meal.replaceFirstChar { it.uppercase() } + " time", "Nothing is logged for it yet. Tap to add it.", false)

    fun postWater(context: Context, drunkMl: Int, goalMl: Int) =
        post(context, "water", "Time for water", "$drunkMl of $goalMl ml so far today.", true)

    fun cancel(context: Context, tag: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(tag, ID) }
    }

    fun cancelAll(context: Context) {
        runCatching {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.activeNotifications.filter { it.id == ID }.forEach { manager.cancel(it.tag, it.id) }
        }
    }
}

/** Hears the alarms and the glass button. */
class FoodReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Features.isOn(context, Feature.FOOD)) return
        val action = intent.action ?: return
        val id = intent.getStringExtra(FoodReminderScheduler.EXTRA_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    FoodReminderScheduler.ACTION_RING -> FoodReminderScheduler.ring(app, id)
                    FoodReminderScheduler.ACTION_DRINK -> FoodReminderScheduler.drink(app)
                }
            } catch (e: Exception) {
                AppLog.e("Food", "Food reminder action $action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
