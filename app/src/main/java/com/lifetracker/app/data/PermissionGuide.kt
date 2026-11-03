package com.lifetracker.app.data

/** The permissions the app asks for, in the order the Permissions page shows them. */
enum class Perm { UsageAccess, Location, Background, Activity, Calls, Notifications, ExactAlarms, Bluetooth, Battery, HealthConnect, Folders }

/** What one permission card says. The wording lives here so it can be tested and read in one place. */
class PermText(val perm: Perm, val title: String, val why: String, val how: String, val usedBy: String = "", val page: String = "")

/** The settings screens that can be tried, best first, for the exact page of a permission. */
enum class PageStep { MiuiEditor, AppPermission, HealthConnectApp, NotificationPage, UsageAccess, ExactAlarm, BatteryExemption, AppDetails }

/** Which Android screens to try, in order, to land on the exact page where a permission is switched. */
object PermissionPages {
    /** [xiaomi] is true on Xiaomi, Redmi and POCO phones, which have their own permission editor (it lists every permission of the app at once). */
    fun chain(perm: Perm, xiaomi: Boolean): List<PageStep> = when (perm) {
        Perm.UsageAccess -> listOf(PageStep.UsageAccess, PageStep.AppDetails)
        Perm.Notifications -> listOf(PageStep.NotificationPage, PageStep.AppDetails)
        Perm.ExactAlarms -> listOf(PageStep.ExactAlarm, PageStep.AppDetails)
        Perm.Battery -> listOf(PageStep.BatteryExemption, PageStep.AppDetails)
        Perm.HealthConnect -> listOf(PageStep.HealthConnectApp, PageStep.AppDetails)
        Perm.Folders -> emptyList()
        Perm.Location, Perm.Background, Perm.Activity, Perm.Calls, Perm.Bluetooth ->
            (if (xiaomi) listOf(PageStep.MiuiEditor) else emptyList()) + listOf(PageStep.AppPermission, PageStep.AppDetails)
    }

    /** The Android permission each runtime permission card is about, for the page that shows just that one. */
    fun androidName(perm: Perm): String? = when (perm) {
        Perm.Location -> "android.permission.ACCESS_FINE_LOCATION"
        Perm.Background -> "android.permission.ACCESS_BACKGROUND_LOCATION"
        Perm.Activity -> "android.permission.ACTIVITY_RECOGNITION"
        Perm.Calls -> "android.permission.READ_CALL_LOG"
        Perm.Bluetooth -> "android.permission.BLUETOOTH_CONNECT"
        else -> null
    }

    fun isXiaomi(manufacturer: String): Boolean = manufacturer.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }
}

object PermissionGuide {
    val all: List<PermText> = listOf(
        PermText(
            Perm.UsageAccess, "Usage access",
            "Android keeps a record of which app was on screen and for how long. The app copies it into its own database so the Timeline shows exact times.",
            "Tap the button, find this app in the list and switch it on. If the switch is greyed out, follow \"Allow restricted settings\" below first.",
            "Phone usage on the Timeline · Habits that follow an app · Rules (app opened or used)",
            "Special access > Usage access > this app",
        ),
        PermText(
            Perm.Location, "Location",
            "Needed to notice where you stop and how far you travel, for Places and trips.",
            "Tap the button and choose \"While using the app\" or \"Precise\". The next card then lets you pick \"Allow all the time\".",
            "Places and trips · the Live map · Place goals · Rules (arrive, leave)",
            "App settings > Permissions > Location",
        ),
        PermText(
            Perm.Background, "Location, all the time",
            "Without it Android only tells the app about places while the app is open, so stops and trips get missed.",
            "Tap the button. If Android only offers \"While using\", open this app's settings, choose Permissions, then Location, then \"Allow all the time\".",
            "Places and trips while the app is closed · Rules (arrive, leave)",
            "App settings > Permissions > Location > Allow all the time",
        ),
        PermText(
            Perm.Activity, "Physical activity",
            "Lets the app notice when the phone stays still or is walking, and use the phone's step counter.",
            "Tap the button and choose Allow.",
            "Steps · walking inside a place · trip detection",
            "App settings > Permissions > Physical activity",
        ),
        PermText(
            Perm.Calls, "Calls and contacts",
            "Copies your call log (who, when, how long) into the app so calls appear on the Timeline. Contacts are only used to show names instead of numbers.",
            "Tap Allow. If nothing appears, open this app's settings, choose Permissions, and allow Call logs and Contacts there.",
            "Calls on the Timeline · Rules (call ended)",
            "App settings > Permissions > Call logs and Contacts",
        ),
        PermText(
            Perm.Notifications, "Notifications",
            "The charging banner and your habit reminders are notifications. Without it the app still records charging but cannot ask what you plugged into, and no reminder can ring.",
            "Tap Allow. On older Android versions this opens the notification settings, where you switch it on.",
            "Habit reminders · the charging banner (if you switch it on) · Rules (notify)",
            "App settings > Notifications",
        ),
        PermText(
            Perm.ExactAlarms, "Exact alarms",
            "Lets habit reminders ring at the minute you chose. Android 14 and newer switch it off for new apps; without it reminders still ring, but can be a few minutes late.",
            "Tap the button and switch on \"Allow setting alarms and reminders\" for this app.",
            "Habit reminders · the focus timer",
            "Special access > Alarms and reminders > this app",
        ),
        PermText(
            Perm.Bluetooth, "Bluetooth devices (Nearby devices)",
            "Lets a tracking profile switch on while a device you pick (your car, for example) is connected. Only the names and addresses of paired devices are read, and only on this phone.",
            "Tap the button and choose Allow. Android calls this \"Nearby devices\".",
            "Tracking profiles (car connected) · Rules (Bluetooth)",
            "App settings > Permissions > Nearby devices",
        ),
        PermText(
            Perm.Battery, "Unrestricted battery",
            "Android stops background apps to save battery. That is the main reason places, trips and charging get missed.",
            "Tap the button and choose Allow. If it opens the wrong page, open this app's settings, then Battery, then No restrictions.",
            "Places, trips, charging and reminders staying alive in the background",
            "App settings > Battery > No restrictions",
        ),
        PermText(
            Perm.HealthConnect, "Health Connect",
            "Where Android keeps your steps and sleep. The app reads them and keeps its own copy on this phone.",
            "Tap Allow and switch on Steps, Sleep and past data. Health Connect itself can be opened with the second button.",
            "Steps and sleep",
            "Health Connect > App permissions > this app",
        ),
        PermText(
            Perm.Folders, "Streak and OpenNutriTracker folders",
            "Folders you pick so the app can read your Streak and OpenNutriTracker backups and write its own backup. The app only touches the folders you choose.",
            "Open Backups and imports and tap Choose folder next to each one.",
            "Backups · Streak and OpenNutriTracker imports",
            "Settings > Backups and imports",
        ),
    )

    fun text(perm: Perm): PermText = all.first { it.perm == perm }

    /** The extra guide for usage access: Android blocks it for apps that were not installed from a store until you allow it. */
    fun restrictedTitle(sdk: Int): String = "Allow restricted settings"

    fun restrictedSteps(sdk: Int): List<String> {
        val where = if (sdk >= 37) {
            "Open Android Settings > Apps > this app. Tap the three dots at the top right."
        } else {
            "Open Android Settings > Apps > this app. Scroll to the very bottom of the page, below everything else."
        }
        return listOf(
            where,
            "Choose \"Allow restricted settings\" and confirm with your PIN or fingerprint.",
            "Come back here and tap the usage access button again. The switch now works.",
        )
    }

    /** The ones that are not allowed come first, then the allowed ones, each keeping the order of the guide. */
    fun ordered(allowed: (Perm) -> Boolean): List<Perm> {
        val all = Perm.values().toList()
        return all.filter { !allowed(it) } + all.filter { allowed(it) }
    }

    /** "3 of 8 allowed" for the Settings row, or "All allowed". */
    fun summary(allowed: Int, total: Int): String = when {
        total <= 0 -> ""
        allowed >= total -> "All allowed"
        else -> "${total - allowed} still off"
    }
}
