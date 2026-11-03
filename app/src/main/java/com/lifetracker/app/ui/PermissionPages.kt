package com.lifetracker.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.lifetracker.app.data.Perm
import com.lifetracker.app.data.PageStep
import com.lifetracker.app.data.PermissionPages

/**
 * Opens the exact Android page where [perm] is switched for this app, trying the best page for this phone first and the app's
 * general settings last. Returns whether some page opened. Android has no public link to the page of one runtime permission, so on
 * Xiaomi, Redmi and POCO phones the phone's own permission editor is tried first; elsewhere the permission page is asked for by name.
 */
internal fun openPermissionPage(context: Context, perm: Perm): Boolean {
    val pkg = context.packageName
    val packageUri = Uri.parse("package:$pkg")
    fun intentFor(step: PageStep): List<Intent> = when (step) {
        PageStep.MiuiEditor -> listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", pkg),
            Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
        )
        PageStep.AppPermission -> PermissionPages.androidName(perm)?.let { name ->
            listOf(
                Intent("android.intent.action.MANAGE_APP_PERMISSION")
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
                    .putExtra("android.intent.extra.PERMISSION_NAME", name)
                    .putExtra(Intent.EXTRA_USER, Process.myUserHandle()),
            )
        }.orEmpty()
        PageStep.HealthConnectApp -> listOf(
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, pkg),
            Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
        )
        PageStep.NotificationPage -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        )
        PageStep.UsageAccess -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        PageStep.ExactAlarm ->
            if (Build.VERSION.SDK_INT >= 31) listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)) else emptyList()
        PageStep.BatteryExemption -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        PageStep.AppDetails -> listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }
    for (step in PermissionPages.chain(perm, PermissionPages.isXiaomi(Build.MANUFACTURER))) {
        for (intent in intentFor(step)) {
            val opened = try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            } catch (e: RuntimeException) {
                false
            }
            if (opened) return true
        }
    }
    return false
}
