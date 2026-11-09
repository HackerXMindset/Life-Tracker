package com.lifetracker.app.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lifetracker.app.QuickPhotoActivity
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** A meal or label photo that could not be read because there was no internet, waiting to be read. */
data class QueuedPhoto(
    val id: String,
    /** True for a photo of a nutrition label, false for a photo of a meal. */
    val label: Boolean,
    val meal: String,
    val date: String,
    val minute: Int,
    val note: String,
) {
    fun toJson(): String = JSONObject()
        .put("id", id).put("label", label).put("meal", meal).put("date", date).put("minute", minute).put("note", note)
        .toString()

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(text: String): QueuedPhoto? = runCatching {
            val j = JSONObject(text)
            QueuedPhoto(
                id = MealPhotos.safe(j.getString("id")), label = j.optBoolean("label", false),
                meal = j.optString("meal", FoodLogic.LUNCH), date = j.getString("date"),
                minute = j.optInt("minute", 720), note = j.optString("note", ""),
            )
        }.getOrNull()
    }
}

/**
 * Photos taken without internet. The photo is kept on this phone; when the phone is online again a notification says it can be
 * read, and tapping it opens the check page, so nothing is ever added without you looking at it.
 */
object PhotoQueue {
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val WORK = "photo-queue"

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "photo-queue")
    private fun photoFile(context: Context, id: String) = File(dir(context), MealPhotos.safe(id) + ".jpg")
    private fun metaFile(context: Context, id: String) = File(dir(context), MealPhotos.safe(id) + ".json")

    fun add(context: Context, jpeg: ByteArray, photo: QueuedPhoto) {
        dir(context).mkdirs()
        photoFile(context, photo.id).writeBytes(jpeg)
        metaFile(context, photo.id).writeText(photo.toJson())
    }

    /** What is waiting, oldest first. Photos older than a week are dropped. */
    fun all(context: Context): List<QueuedPhoto> {
        val files = dir(context).listFiles { f -> f.extension == "json" }?.toList().orEmpty()
        val now = System.currentTimeMillis()
        return files.sortedBy { it.lastModified() }.mapNotNull { f ->
            if (now - f.lastModified() > MAX_AGE_MS) {
                val id = f.nameWithoutExtension
                remove(context, id)
                null
            } else QueuedPhoto.fromJson(runCatching { f.readText() }.getOrDefault(""))
        }
    }

    fun get(context: Context, id: String): QueuedPhoto? =
        metaFile(context, id).takeIf { it.exists() }?.let { QueuedPhoto.fromJson(runCatching { it.readText() }.getOrDefault("")) }

    fun jpeg(context: Context, id: String): ByteArray? = photoFile(context, id).takeIf { it.exists() }?.readBytes()

    fun remove(context: Context, id: String) {
        runCatching { photoFile(context, id).delete() }
        runCatching { metaFile(context, id).delete() }
    }

    /** Asks Android to run [PhotoQueueWorker] as soon as there is a connection. */
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<PhotoQueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Runs once the phone is online and says that waiting photos can be read. */
class PhotoQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PhotoQueueNotifier.refresh(applicationContext)
        return Result.success()
    }
}

object PhotoQueueNotifier {
    private const val CHANNEL = "photo-queue"
    private const val ID = 6101

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Meal photos waiting")
            .setDescription("Tells you when a meal photo taken without internet can be read")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** Shows the notification when something is waiting, and takes it away when nothing is. */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val waiting = PhotoQueue.all(context)
        if (waiting.isEmpty()) {
            runCatching { manager.cancel(ID) }
            return
        }
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val first = waiting.first()
        val open = PendingIntent.getActivity(
            context, 8,
            Intent(context, QuickPhotoActivity::class.java)
                .putExtra(com.lifetracker.app.data.QuickPhoto.EXTRA_QUEUED, first.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (waiting.size == 1) "You are online again. Tap to read it and check it." else "${waiting.size} photos are waiting. Tap to read the first and check it."
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (waiting.size == 1) "A meal photo is ready to read" else "Meal photos are ready to read")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        runCatching { manager.notify(ID, builder.build()) }
    }
}

/** What you changed in the photo reader's answers, so the next prompts can warn it about foods it mixes up. */
object FoodAiMemory {
    const val FILE = "food_ai_memory"
    private const val KEY = "corrections"
    const val MAX = 20

    /** [current] with "the reader said [aiName] but it was [finalName]" added. The same wrong name keeps only the newest correction. */
    fun merge(current: List<Pair<String, String>>, aiName: String, finalName: String): List<Pair<String, String>> {
        val wrong = aiName.trim()
        val right = finalName.trim()
        if (wrong.isEmpty() || right.isEmpty() || wrong.equals(right, ignoreCase = true)) return current
        val kept = current.filter { !it.first.equals(wrong, ignoreCase = true) }
        return (kept + (wrong to right)).takeLast(MAX)
    }

    fun encode(list: List<Pair<String, String>>): String {
        val array = org.json.JSONArray()
        for ((wrong, right) in list) array.put(org.json.JSONArray().put(wrong).put(right))
        return array.toString()
    }

    fun decode(text: String?): List<Pair<String, String>> = runCatching {
        val array = org.json.JSONArray(text ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val pair = array.optJSONArray(i) ?: return@mapNotNull null
            val wrong = pair.optString(0).trim()
            val right = pair.optString(1).trim()
            if (wrong.isEmpty() || right.isEmpty()) null else wrong to right
        }.takeLast(MAX)
    }.getOrDefault(emptyList())

    fun load(context: Context): List<Pair<String, String>> =
        decode(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))

    fun record(context: Context, aiName: String, finalName: String) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val merged = merge(decode(prefs.getString(KEY, null)), aiName, finalName)
        prefs.edit().putString(KEY, encode(merged)).apply()
    }
}
