package com.lifetracker.app.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The same chapter or episode is often noted in more than one place (Mihon's own history, AniList, MyAnimeList, Simkl).
 * This works out which are the same thing and keeps one, in the order of priority you chose. No Android in it, so it is tested.
 */
object MediaMatch {
    /** Two notes of the same chapter or episode are taken to be one if they are within this of each other. */
    const val WINDOW_MS = 12 * 60 * 60_000L

    /** A recorded entry that was added earlier, which a new event may be the same as (or a better record of). */
    class Logged(val event: MediaEvent, val replaceable: Boolean)

    /** [place] go on to be placed; [drop] are left out as duplicates (event, why); [replace] are earlier ones to remove because better data came. */
    class Plan(
        val place: List<MediaEvent>,
        val drop: List<Pair<MediaEvent, String>>,
        val replace: List<MediaEvent>,
    )

    /** Letters and numbers only, lower case, so "Solo Leveling" and "solo-leveling!" are the same title. */
    fun normalize(title: String): String = buildString {
        for (c in title.lowercase()) if (c.isLetterOrDigit()) append(c)
    }

    fun sameTitle(a: MediaEvent, b: MediaEvent): Boolean {
        if (a.ids.isNotEmpty() && b.ids.isNotEmpty() && a.ids.any { it in b.ids }) return true
        val x = normalize(a.title)
        return x.isNotEmpty() && x == normalize(b.title)
    }

    /** Do the chapter or episode numbers touch? Unknown numbers are taken to touch. */
    fun overlaps(a: MediaEvent, b: MediaEvent): Boolean {
        val af = a.numFrom; val at = a.numTo; val bf = b.numFrom; val bt = b.numTo
        if (af == null || at == null || bf == null || bt == null) return true
        return af <= bt && bf <= at
    }

    fun same(a: MediaEvent, b: MediaEvent): Boolean =
        a.service != b.service &&
            a.kind.category == b.kind.category &&
            abs(a.atMs - b.atMs) <= WINDOW_MS &&
            sameTitle(a, b) &&
            overlaps(a, b)

    /** Does [outer] cover every number [inner] has? Unknown numbers are taken to be covered. */
    fun covers(outer: MediaEvent, inner: MediaEvent): Boolean {
        val of = outer.numFrom; val ot = outer.numTo; val inf = inner.numFrom; val int = inner.numTo
        if (of == null || ot == null || inf == null || int == null) return true
        return of <= inf && ot >= int
    }

    /** Do the [parts] together cover every whole number [whole] has? */
    fun coveredBy(whole: MediaEvent, parts: List<MediaEvent>): Boolean {
        val f = whole.numFrom; val t = whole.numTo
        if (f == null || t == null || parts.any { it.numFrom == null || it.numTo == null }) return true
        var n = ceil(f).toInt()
        val last = floor(t).toInt()
        if (last - n > 200) return false
        while (n <= last) {
            if (parts.none { it.numFrom!! <= n && it.numTo!! >= n }) return false
            n++
        }
        return true
    }

    fun rank(order: List<MediaService>, service: MediaService): Int {
        val i = order.indexOf(service)
        return if (i < 0) order.size + service.ordinal else i
    }

    /** The order with anything missing added at the end, so a saved list from an older version still works. */
    fun completeOrder(saved: List<MediaService>): List<MediaService> {
        val out = saved.distinct().toMutableList()
        MediaService.DEFAULT_PRIORITY.forEach { if (it !in out) out += it }
        return out
    }

    fun plan(fresh: List<MediaEvent>, logged: List<Logged>, order: List<MediaService>): Plan {
        val drop = ArrayList<Pair<MediaEvent, String>>()

        // 1. Within this batch: group the events that are the same thing, and keep the ones from the best service of each group.
        val parent = IntArray(fresh.size) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        for (i in fresh.indices) for (j in i + 1 until fresh.size) {
            if (same(fresh[i], fresh[j])) parent[find(i)] = find(j)
        }
        val keep = ArrayList<MediaEvent>()
        for ((_, members) in fresh.indices.groupBy { find(it) }) {
            val events = members.map { fresh[it] }
            val best = events.minOf { rank(order, it.service) }
            for (e in events) {
                if (rank(order, e.service) == best) keep += e
                else {
                    val winner = events.first { rank(order, it.service) == best }
                    drop += e to "also on ${winner.service.label}"
                }
            }
        }

        // 2. Against what was added before.
        val place = ArrayList<MediaEvent>()
        val betterFor = HashMap<MediaEvent, MutableList<MediaEvent>>()
        for (e in keep) {
            var dropped = false
            for (l in logged) {
                if (!same(e, l.event)) continue
                val better = rank(order, e.service) < rank(order, l.event.service)
                if (!better && covers(l.event, e)) {
                    drop += e to "already added from ${l.event.service.label}"
                    dropped = true
                    break
                }
                if (better) {
                    if (l.replaceable) betterFor.getOrPut(l.event) { ArrayList() } += e
                    else if (covers(l.event, e)) {
                        drop += e to "already added from ${l.event.service.label} (you changed it)"
                        dropped = true
                        break
                    }
                }
            }
            if (!dropped) place += e
        }
        val replace = ArrayList<MediaEvent>()
        for ((old, parts) in betterFor) if (coveredBy(old, parts)) replace += old
        return Plan(place, drop, replace)
    }
}
