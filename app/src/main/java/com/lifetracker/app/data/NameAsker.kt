package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.lifetracker.app.MainActivity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Names a spot: saves it as a place and gives the stops there that name. Used by the notification and the Location page. */
object PlaceNaming {
    suspend fun create(context: Context, name: String, lat: Double, lng: Double, radiusM: Int = PlaceRules.DEFAULT_RADIUS): PlaceEntity {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).placeDao()
        val now = System.currentTimeMillis()
        val place = PlaceEntity("p$now", name.trim(), "other", lat, lng, radiusM, false, now)
        dao.upsertPlace(place)
        val inside = (dao.unknownOnce() + dao.openVisits().filter { it.placeId.isEmpty() })
            .filter { PlaceRules.distanceM(lat, lng, it.lat, it.lng) <= radiusM }
        if (inside.isNotEmpty()) dao.assign(inside.map { it.id }.distinct(), place.id, lat, lng)
        runCatching { PlacesTracker.syncAll(app) }
        runCatching { MapScheduler.kick(app) }
        return place
    }
}

/**
 * The optional "Where are you?" notification: when you have stayed at a spot with no name for your minimum time (and it is not
 * quiet hours or snoozed), it offers the best names as buttons, lets you type one, or lets you say "not now". Off until you switch it on.
 */
object NameAsker {
    private const val CHANNEL = "name_ask"
    private const val NOTIFICATION_ID = 4201
    const val ACTION_NAME = "com.lifetracker.app.NAME_SPOT"
    const val ACTION_SNOOZE = "com.lifetracker.app.NAME_SNOOZE"
    const val EXTRA_NAME = "name"
    const val EXTRA_LAT = "lat"
    const val EXTRA_LNG = "lng"
    const val KEY_TYPED = "typed_name"

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Where are you?")
            .setDescription("Asks for a name when you stay at a spot the app does not know")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Looks at the visits just saved and asks about the first one that qualifies. */
    suspend fun consider(context: Context, saves: List<PlaceVisitEntity>, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val settings = PlacesSettings(app)
        if (!settings.askWhere || !canNotify(app)) return
        val cfg = settings.askConfig()
        val minute = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }
        val asked = settings.askedVisits()
        for (v in saves) {
            val key = PlaceLookup.key(v.lat, v.lng)
            val stay = (if (v.ongoing) now else v.endMs) - v.startMs
            val ok = NameAskRules.shouldAsk(
                cfg, v.placeId.isEmpty(), v.ignored, v.id, stay, minute, now, asked, settings.spotAskedMs(key), settings.askSnoozeUntilMs,
            )
            if (!ok) continue
            settings.addAskedVisit(v.id)
            settings.setSpotAsked(key, now)
            CoroutineScope(Dispatchers.IO).launch { runCatching { ask(app, v, stay / 60_000L) } }
            return
        }
    }

    private suspend fun ask(context: Context, v: PlaceVisitEntity, minutes: Long) {
        val settings = PlacesSettings(context)
        val key = PlaceLookup.key(v.lat, v.lng)
        val places = AppDatabase.get(context).placeDao().allPlaces()
        var found = settings.suggestions(key)
        if (found == null) {
            found = PlaceLookup.suggestAll(v.lat, v.lng, settings.nameLanguage)
            if (found.isNotEmpty()) settings.setSuggestions(key, found)
        }
        val names = PlaceNames.combine(PlaceNames.remembered(places, v.lat, v.lng), found, null)
        post(context, v, minutes, names)
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun intent(context: Context, action: String, v: PlaceVisitEntity, name: String? = null) =
        Intent(context, NameAskReceiver::class.java).setAction(action)
            .putExtra(EXTRA_LAT, v.lat).putExtra(EXTRA_LNG, v.lng).also { if (name != null) it.putExtra(EXTRA_NAME, name) }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, v: PlaceVisitEntity, minutes: Long, names: List<PlaceNames.Suggestion>) {
        ensureChannel(context)
        val text = "You have been here about $minutes min and this spot has no name." +
            (if (names.isEmpty()) "" else " Could be: " + names.take(3).joinToString(", ") { it.label() } + ".")
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Where are you?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(60 * 60_000L)
        names.firstOrNull()?.let { best ->
            builder.addAction(
                0, "Use " + best.name.take(24),
                PendingIntent.getBroadcast(context, 1, intent(context, ACTION_NAME, v, best.name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
        val typed = RemoteInput.Builder(KEY_TYPED).setLabel("Name of this spot").build()
        builder.addAction(
            NotificationCompat.Action.Builder(
                0, "Type a name",
                PendingIntent.getBroadcast(context, 2, intent(context, ACTION_NAME, v), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE),
            ).addRemoteInput(typed).build(),
        )
        builder.addAction(
            0, "Not now",
            PendingIntent.getBroadcast(context, 3, intent(context, ACTION_SNOOZE, v), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build()) }
    }

    @SuppressLint("MissingPermission")
    fun postSaved(context: Context, text: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(text)
            .setContentIntent(open(context))
            .setAutoCancel(true)
            .setTimeoutAfter(5_000L)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}

/** Hears the buttons of the "Where are you?" notification. */
class NameAskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val lat = intent.getDoubleExtra(NameAsker.EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(NameAsker.EXTRA_LNG, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return
        when (intent.action) {
            NameAsker.ACTION_SNOOZE -> {
                val settings = PlacesSettings(app)
                settings.askSnoozeUntilMs = System.currentTimeMillis() + settings.askSnoozeMin * 60_000L
                NameAsker.cancel(app)
            }
            NameAsker.ACTION_NAME -> {
                val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NameAsker.KEY_TYPED)?.toString()
                val name = (typed ?: intent.getStringExtra(NameAsker.EXTRA_NAME)).orEmpty().trim()
                if (name.isEmpty()) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        PlaceNaming.create(app, name, lat, lng)
                        NameAsker.postSaved(app, "Saved: $name")
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
