package com.lifetracker.app.data

/** Wording and arithmetic for calls. Kept free of Android so it can be tested. */
object CallStats {
    const val INCOMING = 1
    const val OUTGOING = 2
    const val MISSED = 3
    const val VOICEMAIL = 4
    const val DECLINED = 5
    const val BLOCKED = 6
    const val ELSEWHERE = 7

    fun idFor(startMs: Long, number: String) = "$startMs|$number"

    /** Who the call was with: the contact's name, else the number, else "Unknown number". */
    fun who(call: CallEntity): String = when {
        call.name.isNotBlank() -> call.name.trim()
        call.number.isNotBlank() && call.number != "-1" && call.number != "-2" -> call.number.trim()
        else -> "Unknown number"
    }

    /** True if you actually spoke to someone. */
    fun connected(call: CallEntity): Boolean =
        call.durationSec > 0 && call.type in listOf(INCOMING, OUTGOING, ELSEWHERE)

    /** One line for the Timeline, such as "Call with Mom" or "Missed call from 98765 43210". */
    fun title(call: CallEntity): String {
        val who = who(call)
        return when (call.type) {
            MISSED -> "Missed call from $who"
            DECLINED -> "Declined call from $who"
            BLOCKED -> "Blocked call from $who"
            VOICEMAIL -> "Voicemail from $who"
            OUTGOING -> if (call.durationSec > 0) "Call with $who" else "Called $who, no answer"
            ELSEWHERE -> "Call from $who, answered on another device"
            else -> "Call with $who"
        }
    }

    /** The second line: direction and length, such as "Incoming · 12m 05s". */
    fun detail(call: CallEntity): String {
        val direction = when (call.type) {
            OUTGOING -> "Outgoing"
            MISSED, DECLINED, BLOCKED, VOICEMAIL -> "Incoming"
            else -> "Incoming"
        }
        return if (connected(call)) "$direction · ${durationText(call.durationSec)}" else direction
    }

    /** "45s", "12m", "12m 05s" or "1h 05m". */
    fun durationText(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> {
                val m = s / 60
                val rem = s % 60
                if (rem == 0) "${m}m" else "${m}m ${rem.toString().padStart(2, '0')}s"
            }
            else -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0) "${h}h" else "${h}h ${m.toString().padStart(2, '0')}m"
            }
        }
    }

    data class Totals(val calls: Int, val talkSec: Int, val missed: Int)

    fun totals(calls: List<CallEntity>) = Totals(
        calls = calls.size,
        talkSec = calls.filter { connected(it) }.sumOf { it.durationSec },
        missed = calls.count { it.type == MISSED },
    )

    /** One person (or number) and what you did with them over a period. */
    data class Person(val key: String, val name: String, val calls: Int, val talkSec: Int, val lastMs: Long)

    /** People you talked to most first. Calls with the same number are one person, even if the saved name changed. */
    fun perPerson(calls: List<CallEntity>): List<Person> =
        calls.groupBy { c -> c.number.takeIf { it.isNotBlank() && it != "-1" && it != "-2" } ?: who(c) }
            .map { (key, list) ->
                val latest = list.maxByOrNull { it.startMs }!!
                Person(
                    key = key,
                    name = who(latest),
                    calls = list.size,
                    talkSec = list.filter { connected(it) }.sumOf { it.durationSec },
                    lastMs = latest.startMs,
                )
            }
            .sortedWith(compareByDescending<Person> { it.talkSec }.thenByDescending { it.calls }.thenByDescending { it.lastMs })
}
