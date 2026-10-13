package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEditsTest {
    private val min = 60_000L

    @Test
    fun withoutAnEditTheOriginalTimesStand() {
        assertEquals(100L to 200L, TimelineEdits.times(100, 200, null))
    }

    @Test
    fun anEditChangesOnlyWhatYouChanged() {
        val onlyStart = TimelineEdits.edit(TimelineEdits.CALL, "c", startMs = 150, now = 1)
        assertEquals(150L to 200L, TimelineEdits.times(100, 200, onlyStart))
        val onlyEnd = TimelineEdits.edit(TimelineEdits.CALL, "c", endMs = 300, now = 1)
        assertEquals(100L to 300L, TimelineEdits.times(100, 200, onlyEnd))
    }

    @Test
    fun anEndBeforeTheStartIsNeverShownBackwards() {
        val e = TimelineEdits.edit(TimelineEdits.VISIT, "v", startMs = 500, endMs = 400, now = 1)
        assertEquals(500L to 500L, TimelineEdits.times(100, 200, e))
    }

    @Test
    fun hiddenAndCategoryAreReadFromTheEdit() {
        assertFalse(TimelineEdits.isHidden(null))
        assertTrue(TimelineEdits.isHidden(TimelineEdits.edit(TimelineEdits.TRIP, "t", hidden = true, now = 1)))
        assertNull(TimelineEdits.category(TimelineEdits.edit(TimelineEdits.TRIP, "t", now = 1)))
        assertEquals("Study", TimelineEdits.category(TimelineEdits.edit(TimelineEdits.TRIP, "t", activityId = "Study", now = 1)))
    }

    @Test
    fun clockTimesBecomeTimesOnTheDayAndAnEarlierEndMeansTheNextMorning() {
        val dayStart = 1_000_000L * min
        assertEquals(dayStart + 600 * min to dayStart + 660 * min, TimelineEdits.pickTimes(dayStart, 600, 660))
        val night = TimelineEdits.pickTimes(dayStart, 23 * 60, 6 * 60)
        assertEquals(dayStart + 23 * 60 * min, night.first)
        assertEquals(dayStart + (24 + 6) * 60 * min, night.second)
    }

    @Test
    fun minuteOfTheDayRoundTrips() {
        val dayStart = 5_000L * min
        assertEquals(615, TimelineEdits.minuteOf(dayStart + 615 * min, dayStart))
        assertEquals(30, TimelineEdits.minuteOf(dayStart + (24 * 60 + 30) * min, dayStart))
    }

    @Test
    fun appBlocksAreKeyedByAppAndStart() {
        assertEquals("com.a@123", TimelineEdits.appRef("com.a", 123))
        assertEquals("app|com.a@123", TimelineEdits.key(TimelineEdits.APP, TimelineEdits.appRef("com.a", 123)))
    }
}
