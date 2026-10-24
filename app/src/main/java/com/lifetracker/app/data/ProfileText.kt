package com.lifetracker.app.data

/** The words for tracking profiles: what each condition is called and how a whole rule reads back as a sentence. */
object ProfileText {
    fun conditionLabel(id: String): String = when (id) {
        ProfileCondition.CHARGING -> "Charging"
        ProfileCondition.CAR_MODE -> "Car mode"
        ProfileCondition.BLUETOOTH -> "A Bluetooth device"
        ProfileCondition.WIFI -> "Any Wi-Fi"
        ProfileCondition.WIFI_NAMED -> "A named Wi-Fi"
        ProfileCondition.SPEED_ABOVE -> "Faster than a speed"
        ProfileCondition.SPEED_BELOW -> "Slower than a speed"
        ProfileCondition.STATIONARY -> "Still"
        else -> "Unknown"
    }

    /** A Bluetooth device is stored as "address|name" so the name can be shown; a bare address (no bar) works too. */
    fun packDevice(address: String, name: String): String = if (name.isBlank()) address else "$address|$name"

    fun deviceAddress(text: String): String = text.substringBefore('|').trim()

    fun deviceName(text: String): String = text.substringAfter('|', "").trim()

    private fun deviceShown(text: String): String = deviceName(text).ifEmpty { deviceAddress(text) }

    /** "5 s", "2 min", "1 min 30 s". */
    fun seconds(sec: Int): String = when {
        sec < 60 -> "$sec s"
        sec % 60 == 0 -> "${sec / 60} min"
        else -> "${sec / 60} min ${sec % 60} s"
    }

    private fun speed(kmh: Double): String =
        if (kmh == Math.floor(kmh)) kmh.toInt().toString() else String.format(java.util.Locale.ENGLISH, "%.1f", kmh)

    private fun condition(p: TrackingProfileEntity): String = when (p.condition) {
        ProfileCondition.CHARGING -> "charging"
        ProfileCondition.CAR_MODE -> "the phone is in car mode"
        ProfileCondition.BLUETOOTH -> if (p.conditionText.isBlank()) "a Bluetooth device is connected" else "connected to ${deviceShown(p.conditionText)}"
        ProfileCondition.WIFI -> "connected to Wi-Fi"
        ProfileCondition.WIFI_NAMED -> if (p.conditionText.isBlank()) "connected to a named Wi-Fi" else "connected to the Wi-Fi \"${p.conditionText.trim()}\""
        ProfileCondition.SPEED_ABOVE -> "moving faster than ${speed(p.conditionNumber)} km/h"
        ProfileCondition.SPEED_BELOW -> "moving slower than ${speed(p.conditionNumber)} km/h"
        ProfileCondition.STATIONARY -> "the phone has been still"
        else -> "something unknown happens"
    }

    private fun accuracy(mode: String): String = when (mode) {
        AccuracyMode.HIGH -> ", at high accuracy"
        AccuracyMode.BALANCED -> ", at balanced accuracy"
        AccuracyMode.LOW_POWER -> ", at low power"
        else -> ""
    }

    /** The whole rule as a sentence, for the list and for the top of the editor. */
    fun describe(p: TrackingProfileEntity): String {
        val movement = if (p.minMoveM <= 0 || p.condition == ProfileCondition.STATIONARY) "keeping every reading"
        else "keeping a reading only when it moved at least ${p.minMoveM} m"
        val first = "When ${condition(p)}, record every ${seconds(p.intervalSec)}, $movement${accuracy(p.accuracyMode)}."
        val delays = buildList {
            if (p.activateDelaySec > 0) add("switches on after ${seconds(p.activateDelaySec)}")
            if (p.deactivateDelaySec > 0) add("stays on for ${seconds(p.deactivateDelaySec)} after it ends")
        }
        val second = if (delays.isEmpty()) "" else " It " + delays.joinToString(" and ") + "."
        val off = if (p.enabled) "" else " (Switched off.)"
        return first + second + off
    }
}
