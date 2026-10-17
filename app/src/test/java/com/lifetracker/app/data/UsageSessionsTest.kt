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

    @Test
    fun appsWithNoCategoryUsedTogetherBecomeOneScreenPeriod() {
        val day = UsageDays.build(
            listOf(
                session("t", 0, 5 * min),
                session("b", 6 * min, 8 * min),
                session("t", 9 * min, 12 * min),
                session("t", 20 * min, 22 * min),
            ),
            mapOf(app("t", "Telegram"), app("b", "Brave")),
            dayStart,
            dayEnd,
        )
        assertEquals(2, day.blocks.size)
        val first = day.blocks[0]
        assertEquals("Telegram + Brave", first.label)
        assertEquals(0L, first.startMs)
        assertEquals(12 * min, first.endMs)
        assertEquals(10 * min, first.activeMs)
        assertEquals(UsageDays.MIXED_ACTIVITY_ID, first.activityId)
        assertEquals(listOf("t", "b"), first.apps.map { it.pkg })
        assertEquals(2, first.apps[0].opens)
        assertEquals("t", day.blocks[1].pkg)
        assertEquals(12 * min, day.totalMs)
    }

    @Test
    fun fiveMinutesWithoutUseEndsAPeriodButJustUnderDoesNot() {
        val apart = UsageDays.build(listOf(session("t", 0, 5 * min), session("t", 10 * min, 12 * min)), mapOf(app("t", "Telegram")), dayStart, dayEnd)
        assertEquals(2, apart.blocks.size)
        val close = UsageDays.build(listOf(session("t", 0, 5 * min), session("t", 9 * min + 59 * s, 12 * min)), mapOf(app("t", "Telegram")), dayStart, dayEnd)
        assertEquals(1, close.blocks.size)
        assertEquals(listOf(1, 1), apart.blocks.map { it.opens })
    }

    @Test
    fun appsOfTheSameCategoryJoinOneStudyPeriod() {
        val day = UsageDays.build(
            listOf(session("a", 0, 10 * min), session("c", 11 * min, 20 * min)),
            mapOf(app("a", "Anki", "Study"), app("c", "Notes", "Study")),
            dayStart,
            dayEnd,
        )
        val block = day.blocks.single()
        assertEquals("Study", block.activityId)
        assertEquals(19 * min, block.activeMs)
        assertEquals(19, day.minutesByActivity["Study"])
    }

    @Test
    fun anAppOfAnotherCategoryStartsANewPeriod() {
        val day = UsageDays.build(
            listOf(session("a", 0, 10 * min), session("g", 11 * min, 20 * min), session("a", 21 * min, 25 * min)),
            mapOf(app("a", "Anki", "Study"), app("g", "Game", "Screen")),
            dayStart,
            dayEnd,
        )
        assertEquals(listOf("Study", "Screen", "Study"), day.blocks.map { it.activityId })
        assertEquals(14, day.minutesByActivity["Study"])
        assertEquals(9, day.minutesByActivity["Screen"])
    }

    @Test
    fun aCategorisedAppAndAnUncategorisedAppAreNotMixed() {
        val day = UsageDays.build(
            listOf(session("a", 0, 10 * min), session("x", 11 * min, 14 * min)),
            mapOf(app("a", "Anki", "Study")),
            dayStart,
            dayEnd,
        )
        assertEquals(2, day.blocks.size)
        assertEquals("", day.blocks[1].activityId)
    }

    @Test
    fun aNamedCombinationJoinsItsAppsAndDecidesTheActivity() {
        val combos = listOf(UsageDays.Combo("study-pw", setOf("pw", "gpt"), "Study"))
        val day = UsageDays.build(
            listOf(session("pw", 0, 10 * min), session("gpt", 11 * min, 16 * min), session("pw", 17 * min, 30 * min)),
            mapOf(app("pw", "Physics Wallah"), app("gpt", "ChatGPT", "Screen")),
            dayStart,
            dayEnd,
            combos = combos,
        )
        val block = day.blocks.single()
        assertEquals("Study", block.activityId)
        assertEquals("study-pw", block.comboId)
        assertEquals(28, day.minutesByActivity["Study"])
    }

    @Test
    fun aCombinationNeedsAtLeastTwoOfItsAppsToApply() {
        val combos = listOf(UsageDays.Combo("study-pw", setOf("pw", "gpt"), "Study"))
        val day = UsageDays.build(listOf(session("pw", 0, 10 * min)), mapOf(app("pw", "Physics Wallah")), dayStart, dayEnd, combos = combos)
        assertEquals("", day.blocks.single().activityId)
        assertEquals("", day.blocks.single().comboId)
    }

    private fun evc(time: Long, pkg: String, type: RawType, cls: String) = RawEvent(time, pkg, type, cls)

    /** The case behind the missing time: a late "stopped" for the old screen must not end the session on the new one. */
    @Test
    fun aLateStopOfTheOldScreenDoesNotCutTheSession() {
        val out = UsageSessions.build(
            listOf(
                evc(0, "a", RawType.RESUME, "A1"),
                evc(10 * s, "a", RawType.PAUSE, "A1"),
                evc(10 * s, "a", RawType.RESUME, "A2"),
                evc(11 * s, "a", RawType.STOP, "A1"),
                evc(60 * s, "a", RawType.PAUSE, "A2"),
            ),
            100 * s,
        )
        assertEquals(listOf(Triple("a", 0L, 60 * s)), spans(out))
    }

    @Test
    fun aPauseOfAScreenThatIsNoLongerOnTopIsIgnored() {
        val out = UsageSessions.build(
            listOf(
                evc(0, "a", RawType.RESUME, "A1"),
                evc(10 * s, "a", RawType.RESUME, "A2"),
                evc(11 * s, "a", RawType.PAUSE, "A1"),
                evc(50 * s, "a", RawType.PAUSE, "A2"),
            ),
            100 * s,
        )
        assertEquals(listOf(Triple("a", 0L, 50 * s)), spans(out))
    }

    @Test
    fun aStopOfTheScreenOnTopEndsTheSessionWhenNoPauseCame() {
        val out = UsageSessions.build(listOf(evc(0, "a", RawType.RESUME, "A1"), evc(40 * s, "a", RawType.STOP, "A1")), 100 * s)
        assertEquals(listOf(Triple("a", 0L, 40 * s)), spans(out))
    }

    @Test
    fun aStopWithoutAScreenNameIsNotTrusted() {
        val out = UsageSessions.build(listOf(ev(0, "a", RawType.RESUME), ev(40 * s, "a", RawType.STOP), ev(90 * s, "a", RawType.PAUSE)), 100 * s)
        assertEquals(listOf(Triple("a", 0L, 90 * s)), spans(out))
    }

    @Test
    fun aPhoneThatDiedEndsTheSessionAtItsLastEvent() {
        val out = UsageSessions.build(
            listOf(ev(0, "a", RawType.RESUME), ev(30 * s, "a", RawType.RESUME), ev(5000 * s, "", RawType.STARTUP)),
            9000 * s,
        )
        assertEquals(listOf(Triple("a", 0L, 30 * s)), spans(out))
    }

    @Test
    fun theBlockKeepsTheStretchesOfRealUse() {
        val sessions = listOf(
            UsageSessionEntity("a|0", "a", 0, 60 * s),
            UsageSessionEntity("a|1", "a", 120 * s, 180 * s),
        )
        val day = UsageDays.build(sessions, emptyMap(), 0, 1000 * s)
        val block = day.blocks.single()
        assertEquals(listOf(0L to 60 * s, 120 * s to 180 * s), block.spans)
        assertEquals(120 * s, block.activeMs)
        assertEquals(120 * s, day.totalMs)
    }
}
