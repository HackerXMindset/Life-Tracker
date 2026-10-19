package com.lifetracker.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helps the phone let the app keep running. Phones stop background apps to save battery, and these
 * are the Android screens where you switch that off for this app.
 */
object KeepAlive {
    fun isExempt(context: Context): Boolean =
        runCatching {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    /** Android's own question "let this app ignore battery optimisation?". */
    fun exemptionIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(context),
    )

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /** Opens the first screen on the list that this phone has. */
    fun open(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
    }
}
