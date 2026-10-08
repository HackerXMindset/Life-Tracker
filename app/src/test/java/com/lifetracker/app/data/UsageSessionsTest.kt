package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSessionsTest {
    private val s = 1_000L
    private val min = 60_000L

    private fun ev(time: Long, pkg: String, type: RawType) = RawEvent(time, pkg, type)

    private fun spans(list: List<UsageSessionEntity>) = list.map { Triple(it.pkg, it.startMs, it.endMs) }

    @Test
    fun anAppFromResumeToPauseIsOneSession() {
        val out = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME), ev(60 * s, "a", RawType.PAUSE)), 100 * s)
        assertEquals(listOf(Triple("a", 0L, 60 * s)), spans(out))
        assertEquals("a|0", out.single().id)
    }

    @Test
    fun openingAnotherAppEndsTheFirstEvenWithoutAPause() {
        val out = UsageSessions.build(
            listOf(ev(0, "a", RawType.RESUME), ev(10 * s, "b", RawType.RESUME), ev(30 * s, "b", RawType.PAUSE)),
            100 * s,
        )
        assertEquals(listOf(Triple("a", 0L, 10 * s), Triple("b", 10 * s, 30 * s)), spans(out))
    }

    @Test
    fun theScreenTurningOffEndsTheSession() {
        val out = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME), ev(30 * s, "", RawType.SCREEN_OFF)), 999 * s)
        assertEquals(listOf(Triple("a", 0L, 30 * s)), spans(out))
    }

    @Test
    fun shutdownEndsTheSession() {
        val out = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME), ev(40 * s, "", RawType.SHUTDOWN)), 999 * s)
        assertEquals(listOf(Triple("a", 0L, 40 * s)), spans(out))
    }

    @Test
    fun aPauseFromAnotherAppOrFromNowhereIsIgnored() {
        val out = UsageSessions.build(
            listOf(ev(1 * s, "x", RawType.PAUSE), ev(5 * s, "a", RawType.RESUME), ev(10 * s, "b", RawType.PAUSE), ev(20 * s, "a", RawType.PAUSE)),
            100 * s,
        )
        assertEquals(listOf(Triple("a", 5 * s, 20 * s)), spans(out))
    }

    @Test
    fun movingBetweenScreensInsideAnAppIsOneSession() {
        val out = UsageSessions.build(
            listOf(
                ev(0, "a", RawType.RESUME), ev(10 * s, "a", RawType.PAUSE),
                ev(11 * s, "a", RawType.RESUME), ev(20 * s, "a", RawType.PAUSE),
            ),
            100 * s,
        )
        assertEquals(listOf(Triple("a", 0L, 20 * s)), spans(out))
    }

    @Test
    fun flashesUnderThreeSecondsAreDropped() {
        val out = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME), ev(1 * s, "a", RawType.PAUSE)), 100 * s)
        assertTrue(out.isEmpty())
    }

    @Test
    fun skippedAppsStillEndWhatWasOpenBeforeThem() {
        val out = UsageSessions.build(
            listOf(ev(0, "a", RawType.RESUME), ev(20 * s, "home", RawType.RESUME), ev(50 * s, "b", RawType.RESUME), ev(90 * s, "b", RawType.PAUSE)),
            100 * s,
            skip = setOf("home"),
        )
        assertEquals(listOf(Triple("a", 0L, 20 * s), Triple("b", 50 * s, 90 * s)), spans(out))
    }

    @Test
    fun anAppStillOpenIsClosedAtTheEndAndLaterExtendedWithTheSameId() {
        val first = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME)), 30 * s)
        assertEquals(listOf(Triple("a", 0L, 30 * s)), spans(first))
        // The next sync re-reads from the start of that session and sees how it ended.
        val second = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME), ev(90 * s, "a", RawType.PAUSE)), 200 * s)
        assertEquals(listOf(Triple("a", 0L, 90 * s)), spans(second))
        assertEquals(first.single().id, second.single().id)
    }

    // ---- one day's view

    private fun app(pkg: String, label: String, activity: String = "", ignored: Boolean = false) =
        pkg to UsageAppEntity(pkg, label, activity, ignored)

    private fun session(pkg: String, start: Long, end: Long) = UsageSessionEntity(UsageSessionEntity.idFor(pkg, start), pkg, start, end)

    private val dayStart = 0L
    private val dayEnd = 24 * 60 * min

    @Test
    fun closeStretchesOfOneAppBecomeOneBlock() {
        val day = UsageDays.build(
            listOf(session("a", 60 * min, 70 * min), session("a", 71 * min, 80 * min), session("b", 180 * min, 185 * min)),
            mapOf(app("a", "Anki", "Study"), app("b", "Brave")),
            dayStart,
            dayEnd,
        )
        assertEquals(2, day.blocks.size)
        val a = day.blocks.first()
        assertEquals("Anki", a.label)
        assertEquals(60 * min, a.startMs)
        assertEquals(80 * min, a.endMs)
        assertEquals(19 * min, a.activeMs)
        assertEquals(24 * min, day.totalMs)
        assertEquals(listOf("a", "b"), day.perApp.map { it.first })
        assertEquals(19, day.minutesByActivity["Study"])
    }

    @Test
    fun farApartStretchesStaySeparate() {
        val day = UsageDays.build(
            listOf(session("a", 60 * min, 70 * min), session("a", 90 * min, 100 * min)),
            mapOf(app("a", "Anki")),
            dayStart,
            dayEnd,
        )
        assertEquals(2, day.blocks.size)
    }

    @Test
    fun ignoredAppsAreLeftOutEverywhere() {
        val day = UsageDays.build(
            listOf(session("a", 0, 10 * min), session("c", 20 * min, 50 * min)),
            mapOf(app("a", "A"), app("c", "Launcher", ignored = true)),
            dayStart,
            dayEnd,
        )
        assertEquals(listOf("a"), day.blocks.map { it.pkg })
        assertEquals(10 * min, day.totalMs)
    }

    @Test
    fun aSessionAcrossMidnightIsCutToTheDay() {
        val day = UsageDays.build(
            listOf(session("a", -10 * min, 10 * min)),
            mapOf(app("a", "A", "Screen")),
            dayStart,
            dayEnd,
        )
        assertEquals(10 * min, day.totalMs)
        assertEquals(0L, day.blocks.single().startMs)
        assertEquals(10, day.minutesByActivity["Screen"])
    }

    @Test
    fun unknownAppsShowTheirPackageName() {
        val day = UsageDays.build(listOf(session("com.x", 0, 5 * min)), emptyMap(), dayStart, dayEnd)
        assertEquals("com.x", day.blocks.single().label)
        assertTrue(day.minutesByActivity.isEmpty())
    }
}
