package com.lifetracker.app.data

/**
 * The beginner's way into rules: triggers sorted into a few plain groups, and ready-made starting points.
 * No Android in this file, so it can be tested.
 */
object RuleGuide {
    /** A group of triggers, shown as one choice before the exact trigger is picked. */
    class Group(val id: String, val title: String, val hint: String, val kinds: List<String>)

    val GROUPS: List<Group> = listOf(
        Group("places", "A place", "Arriving, leaving or staying", listOf(RuleKinds.ARRIVE, RuleKinds.LEAVE, RuleKinds.STAY)),
        Group("time", "Time", "A time of day, or a button you press", listOf(RuleKinds.TIME, RuleKinds.BUTTON)),
        Group(
            "phone", "The phone", "Charging, Wi-Fi, Bluetooth, apps, calls",
            listOf(
                RuleKinds.CHARGE_ON, RuleKinds.CHARGE_OFF, RuleKinds.WIFI_ON, RuleKinds.WIFI_OFF, RuleKinds.BT_ON, RuleKinds.BT_OFF,
                RuleKinds.APP_OPEN, RuleKinds.APP_USED, RuleKinds.CALL,
            ),
        ),
        Group("moving", "Moving", "Trips and speed", listOf(RuleKinds.TRIP_START, RuleKinds.TRIP_END, RuleKinds.SPEED_OVER, RuleKinds.SPEED_UNDER)),
        Group("body", "My body", "Steps, waking up, falling asleep", listOf(RuleKinds.STEPS, RuleKinds.WAKE, RuleKinds.SLEEP)),
        Group("habits", "A habit", "When a habit is ticked", listOf(RuleKinds.HABIT_TICKED)),
    )

    /** The group a trigger sits in (the first one if the kind is not known). */
    fun groupOf(kind: String): Group = GROUPS.firstOrNull { kind in it.kinds } ?: GROUPS.first()

    /** The words for a trigger inside its group, where the group already says half of it. */
    fun triggerWords(kind: String): String = if (kind == RuleKinds.BUTTON) "I tap this rule's button" else RuleKinds.triggerLabel(kind)

    /** A short line that says what a trigger is for, so nobody has to guess. */
    fun triggerHint(kind: String): String = when (kind) {
        RuleKinds.ARRIVE -> "The moment the app notices you have got to a place"
        RuleKinds.LEAVE -> "The moment the app notices you have gone from a place"
        RuleKinds.STAY -> "When you have been at a place for a while"
        RuleKinds.CHARGE_ON -> "When you plug the charger in"
        RuleKinds.CHARGE_OFF -> "When you pull the charger out"
        RuleKinds.TRIP_END -> "When you get somewhere and a trip is saved"
        RuleKinds.TRIP_START -> "When you set off on a trip"
        RuleKinds.TIME -> "At a time you choose, every day"
        RuleKinds.BUTTON -> "Runs only when you tap it, as a button at the top of the page"
        RuleKinds.SPEED_OVER -> "When you go faster than a speed you choose"
        RuleKinds.SPEED_UNDER -> "When you slow down below a speed you choose, on a trip"
        RuleKinds.HABIT_TICKED -> "When you tick a habit"
        RuleKinds.WIFI_ON -> "When the phone joins a Wi-Fi network"
        RuleKinds.WIFI_OFF -> "When the phone leaves a Wi-Fi network"
        RuleKinds.BT_ON -> "When the phone connects to a Bluetooth device"
        RuleKinds.BT_OFF -> "When the phone disconnects from a Bluetooth device"
        RuleKinds.APP_OPEN -> "When you open an app"
        RuleKinds.APP_USED -> "When you have used an app for long enough in a day"
        RuleKinds.CALL -> "When a call comes in, goes out or is missed"
        RuleKinds.STEPS -> "When your steps for the day reach a number"
        RuleKinds.WAKE -> "When the app finds you woke up"
        RuleKinds.SLEEP -> "When the app finds you fell asleep"
        else -> ""
    }

    /** A short line that says what an action does. */
    fun actionHint(kind: String): String = when (kind) {
        RuleKinds.TICK_HABIT -> "Marks one of your habits as done"
        RuleKinds.ENTRY -> "Writes a line on your Timeline"
        RuleKinds.NOTIFY -> "Shows a notification on the phone"
        RuleKinds.START_FOCUS -> "Starts a focus timer"
        RuleKinds.MONEY -> "Writes down some money spent or earned"
        RuleKinds.NOTE -> "Adds a note to a habit"
        RuleKinds.RUN_RULE -> "Does what another rule of yours does"
        else -> ""
    }

    /** A ready-made start for a new rule. It is only a starting point: every part can be changed. */
    class Template(
        val id: String,
        val title: String,
        val hint: String,
        val trigger: RulePart,
        val actions: List<RulePart>,
    )

    val TEMPLATES: List<Template> = listOf(
        Template(
            "arrive", "Tell me when I get somewhere", "A notification the moment you arrive at a place",
            RulePart(RuleKinds.ARRIVE),
            listOf(RulePart(RuleKinds.NOTIFY).with("title", "Welcome to {place}")),
        ),
        Template(
            "leave", "Remind me when I leave somewhere", "A notification when you go, for \"did I take everything?\"",
            RulePart(RuleKinds.LEAVE),
            listOf(RulePart(RuleKinds.NOTIFY).with("title", "Leaving {place}").with("text", "Have you got everything?")),
        ),
        Template(
            "habit_place", "Tick a habit when I get somewhere", "For example the gym habit, when you reach the gym",
            RulePart(RuleKinds.ARRIVE),
            listOf(RulePart(RuleKinds.TICK_HABIT)),
        ),
        Template(
            "daily", "Remind me at a time every day", "A notification at the time you choose",
            RulePart(RuleKinds.TIME).with("at", (20 * 60).toString()),
            listOf(RulePart(RuleKinds.NOTIFY).with("title", "Time to check in")),
        ),
        Template(
            "charge", "Do something when I plug in", "For example a Timeline entry when charging starts",
            RulePart(RuleKinds.CHARGE_ON),
            listOf(RulePart(RuleKinds.ENTRY).with("title", "Charging")),
        ),
        Template(
            "button", "A button I tap myself", "For things the phone cannot notice, like \"I took my vitamins\"",
            RulePart(RuleKinds.BUTTON),
            emptyList(),
        ),
    )

    /** The three steps of the editor, in order. */
    val STEPS: List<String> = listOf("When", "Only if", "Then")

    /** What each step asks, in one plain sentence. */
    fun stepHelp(step: Int): String = when (step) {
        0 -> "What should set this rule off?"
        1 -> "Optional. Add a limit if it should not run every time. Skip it if it should always run."
        else -> "What should the phone do? Then give the rule a name."
    }
}
