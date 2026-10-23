package com.lifetracker.app.data

/** The permissions the app asks for, in the order the Permissions page shows them. */
enum class Perm { UsageAccess, Location, Background, Activity, Calls, Notifications, ExactAlarms, Battery, HealthConnect, Folders }

/** What one permission card says. The wording lives here so it can be tested and read in one place. */
class PermText(val perm: Perm, val title: String, val why: String, val how: String)

object PermissionGuide {
    val all: List<PermText> = listOf(
        PermText(
            Perm.UsageAccess, "Usage access",
            "Android keeps a record of which app was on screen and for how long. The app copies it into its own database so the Timeline shows exact times.",
            "Tap the button, find this app in the list and switch it on. If the switch is greyed out, follow \"Allow restricted settings\" below first.",
        ),
        PermText(
            Perm.Location, "Location",
            "Needed to notice where you stop and how far you travel, for Places and trips.",
            "Tap the button and choose \"While using the app\" or \"Precise\". The next card then lets you pick \"Allow all the time\".",
        ),
        PermText(
            Perm.Background, "Location, all the time",
            "Without it Android only tells the app about places while the app is open, so stops and trips get missed.",
            "Tap the button. If Android only offers \"While using\", open this app's settings, choose Permissions, then Location, then \"Allow all the time\".",
        ),
        PermText(
            Perm.Activity, "Physical activity",
            "Lets the app notice when the phone stays still or is walking, and use the phone's step counter.",
            "Tap the button and choose Allow.",
        ),
        PermText(
            Perm.Calls, "Calls and contacts",
            "Copies your call log (who, when, how long) into the app so calls appear on the Timeline. Contacts are only used to show names instead of numbers.",
            "Tap Allow. If nothing appears, open this app's settings, choose Permissions, and allow Call logs and Contacts there.",
        ),
        PermText(
            Perm.Notifications, "Notifications",
            "The charging banner and your habit reminders are notifications. Without it the app still records charging but cannot ask what you plugged into, and no reminder can ring.",
            "Tap Allow. On older Android versions this opens the notification settings, where you switch it on.",
        ),
        PermText(
            Perm.ExactAlarms, "Exact alarms",
            "Lets habit reminders ring at the minute you chose. Android 14 and newer switch it off for new apps; without it reminders still ring, but can be a few minutes late.",
            "Tap the button and switch on \"Allow setting alarms and reminders\" for this app.",
        ),
        PermText(
            Perm.Battery, "Unrestricted battery",
            "Android stops background apps to save battery. That is the main reason places, trips and charging get missed.",
            "Tap the button and choose Allow. If it opens the wrong page, open this app's settings, then Battery, then No restrictions.",
        ),
        PermText(
            Perm.HealthConnect, "Health Connect",
            "Where Android keeps your steps and sleep. The app reads them and keeps its own copy on this phone.",
            "Tap Allow and switch on Steps, Sleep and past data. Health Connect itself can be opened with the second button.",
        ),
        PermText(
            Perm.Folders, "Streak and OpenNutriTracker folders",
            "Folders you pick so the app can read your Streak and OpenNutriTracker backups and write its own backup. The app only touches the folders you choose.",
            "Open Backups and imports and tap Choose folder next to each one.",
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

    /** "3 of 8 allowed" for the Settings row, or "All allowed". */
    fun summary(allowed: Int, total: Int): String = when {
        total <= 0 -> ""
        allowed >= total -> "All allowed"
        else -> "${total - allowed} still off"
    }
}
