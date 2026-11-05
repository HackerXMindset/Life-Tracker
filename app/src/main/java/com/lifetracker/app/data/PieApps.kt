package com.lifetracker.app.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Apps in the pie: each app a phone period was made of gets its own slice, in a shade of its category's colour, so the pie shows
 * both what the time was (the colour) and which app it was. No Android in here, so it can be tested.
 */
object PieApps {
    /** One stretch of one app. [family] is the category it counts under and [argb] that category's colour. */
    class Stretch(val family: String, val argb: Int, val app: String, val fromMs: Long, val toMs: Long) {
        val lengthMs: Long get() = toMs - fromMs
    }

    /**
     * Cuts the real-use [spans] of one period between its [apps] (name and time on screen), in order, each app getting a share of
     * the spans as long as its share of the time. The phone does not say when inside a period each app was open, so this is an even split
     * by time, and the sums stay exactly right.
     */
    fun split(spans: List<Pair<Long, Long>>, apps: List<Pair<String, Long>>): List<Triple<String, Long, Long>> {
        val live = apps.filter { it.second > 0L }
        val total = spans.sumOf { it.second - it.first }
        if (live.size <= 1 || total <= 0L) {
            val only = live.firstOrNull()?.first ?: apps.firstOrNull()?.first ?: return emptyList()
            return spans.map { Triple(only, it.first, it.second) }
        }
        val weight = live.sumOf { it.second }.toDouble()
        // How much of the time each app gets, handing out the rounding left over to the last one.
        val share = ArrayList<Long>()
        var given = 0L
        live.forEachIndexed { i, a ->
            val ms = if (i == live.lastIndex) total - given else (total * (a.second / weight)).toLong()
            share += ms
            given += ms
        }
        val out = ArrayList<Triple<String, Long, Long>>()
        var app = 0
        var left = share[0]
        for ((from, to) in spans) {
            var at = from
            while (at < to) {
                while (left <= 0L && app < live.lastIndex) {
                    app++
                    left = share[app]
                }
                val take = minOf(to - at, if (app == live.lastIndex) to - at else left)
                if (take <= 0L) break
                out += Triple(live[app].first, at, at + take)
                at += take
                left -= take
            }
        }
        return out
    }

    /** A shade of [argb] for the app that is number [rank] (0 is the biggest) of [count] in its category: the biggest keeps the colour, the others get lighter. */
    fun shade(argb: Int, rank: Int, count: Int): Int {
        if (count <= 1 || rank <= 0) return argb
        val t = 0.55 * rank / max(1, count - 1)
        fun mix(c: Int) = (c + (255 - c) * t).roundToInt().coerceIn(0, 255)
        val r = mix((argb shr 16) and 0xFF)
        val g = mix((argb shr 8) and 0xFF)
        val b = mix(argb and 0xFF)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Each stretch with its colour: the apps of one category are ranked by the time they had in the whole day and shaded one by one. */
    fun shaded(stretches: List<Stretch>): List<Pair<Stretch, Int>> {
        val rank = HashMap<Pair<String, String>, Int>()
        val count = HashMap<String, Int>()
        stretches.groupBy { it.family }.forEach { (family, list) ->
            val apps = list.groupBy { it.app }.mapValues { (_, v) -> v.sumOf { it.lengthMs } }.entries.sortedByDescending { it.value }
            apps.forEachIndexed { i, e -> rank[family to e.key] = i }
            count[family] = apps.size
        }
        return stretches.map { it to shade(it.argb, rank.getValue(it.family to it.app), count.getValue(it.family)) }
    }
}
