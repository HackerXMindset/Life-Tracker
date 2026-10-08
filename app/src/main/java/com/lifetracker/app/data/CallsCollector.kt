package com.lifetracker.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies the phone's call log into the app's own database. Needs the "Call logs" permission;
 * the "Contacts" permission is optional and only turns numbers into names.
 * Only normal phone calls are in the call log (not WhatsApp or Telegram calls).
 */
object CallsCollector {
    private const val FIRST_LOOKBACK_MS = 180L * 24 * 60 * 60 * 1000
    private const val OVERLAP_MS = 60L * 60 * 1000

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)

    fun hasAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    private fun canReadContacts(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Opens this app's own page in Android's settings, for when the permission pop-up no longer appears. */
    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Copies new calls. Returns how many were read, or null without permission. */
    suspend fun sync(context: Context, now: Long = System.currentTimeMillis()): Int? = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext null
        val dao = AppDatabase.get(context).callDao()
        val latest = dao.latestStart()
        val since = if (latest != null) latest - OVERLAP_MS else now - FIRST_LOOKBACK_MS

        val calls = try {
            read(context, since)
        } catch (e: SecurityException) {
            return@withContext null
        }
        dao.upsertCalls(calls)
        calls.size
    }

    private fun read(context: Context, since: Long): List<CallEntity> {
        val contacts = canReadContacts(context)
        val names = HashMap<String, String>()
        val out = ArrayList<CallEntity>()
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} ASC",
        ) ?: return emptyList()
        cursor.use { c ->
            val iNumber = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val iDuration = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val number = c.getString(iNumber).orEmpty()
                var name = c.getString(iName).orEmpty()
                if (name.isBlank() && contacts && number.isNotBlank() && number != "-1" && number != "-2") {
                    name = names.getOrPut(number) { lookupName(context, number) }
                }
                val start = c.getLong(iDate)
                out += CallEntity(
                    id = CallStats.idFor(start, number),
                    number = number,
                    name = name,
                    type = c.getInt(iType),
                    startMs = start,
                    durationSec = c.getInt(iDuration).coerceAtLeast(0),
                )
            }
        }
        return out
    }

    private fun lookupName(context: Context, number: String): String = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0).orEmpty() else "" }
            .orEmpty()
    } catch (e: Exception) {
        ""
    }
}
