package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEditingTest {
    private val t0 = 1_760_000_000_000L
    private val min = 60_000L
    private val stride = 0.70

    private fun at(minute: Int) = t0 + minute * min

    private fun point(tripId: String, minute: Int, lat: Double, steps: Long = -1L) =
        TripPointEntity(tripId, at(minute), lat, 77.2, 8f, steps)

    /** The way to work: five readings, a minute apart, going north (about 440 m). */
    private val a = TripEntity("a", at(0), at(4), "home", "work", 440, 6, 7, "WALKING=240000", "walk", "you")
    private val aPoints = (0..4).map { point("a", it, 28.600 + it * 0.001) }

    /** After 15 minutes at work: five more readings (the ride on). */
    private val b = TripEntity("b", at(20), at(24), "work", "gym", 440, 25, 30, "IN_VEHICLE=200000", "vehicle", "auto")
    private val bPoints = (20..24).map { point("b", it, 28.610 + (it - 20) * 0.001) }

    /** The readings taken while you were at work. */
    private val between = (5..19).map { RawFixEntity(at(it), 28.604 + (it % 2) * 0.00001, 77.2, 8f) }

    private fun visit(id: String, place: String, from: Int, to: Int, ongoing: Boolean = false) =
        PlaceVisitEntity(id, place, at(from), at(to), 28.604, 77.2, "gps", ongoing, false)

    private val before = visit("v-before", "home", -30, 0)
    private val atWork = visit("v-work", "work", 5, 20)
    private val after = visit("v-after", "gym", 25, 60)
    private val visits = listOf(before, atWork, after)

    private fun merged(edits: Map<String, TimelineEditEntity> = emptyMap()) =
        TripEditing.merge(a, b, aPoints, bPoints, between, visits, edits, stride, t0 + 90 * min, "m1")!!

    // ---- merge

    @Test
    fun aMergeKeepsTheEarlierTripsRowAndAnswerAndTakesTheLaterEnd() {
        val change = merged()
        val joined = change.trips.single()
        assertEquals("a", joined.id)
        assertEquals(a.startMs, joined.startMs)
        assertEquals(b.endMs, joined.endMs)
        assertEquals("home", joined.fromPlaceId)
        assertEquals("gym", joined.toPlaceId)
        // How you travelled was your answer on the first trip: it stays.
        assertEquals("walk", joined.mode)
        assertEquals("you", joined.modeSource)
        assertEquals("IN_VEHICLE=200000,WALKING=240000", joined.activity)
    }

    @Test
    fun aMergeBringsAlongBothSetsOfReadingsAndTheOnesBetween() {
        val change = merged()
        assertEquals(5 + 15 + 5, change.points.size)
        assertTrue(change.points.all { it.tripId == "a" })
        assertEquals(change.points.map { it.ms }, change.points.map { it.ms }.sorted())
    }

    @Test
    fun theDistanceAndSpeedsAreWorkedOutAgainFromTheReadings() {
        val joined = merged().trips.single()
        // 4 steps of about 111 m going up, about 670 m to the next trip's first reading, 4 more steps: well over each trip alone.
        assertTrue("distance ${joined.distanceM}", joined.distanceM > a.distanceM + b.distanceM)
        assertTrue(joined.topKmh > 0)
    }

    @Test
    fun aMergeHidesTheLaterTripAndTheStaysBetweenAndNothingElse() {
        val change = merged()
        val byKey = change.timelineEdits.associateBy { it.key }
        val trip = byKey[TimelineEdits.key(TimelineEdits.TRIP, "b")]!!
        assertTrue(trip.hidden)
        assertEquals(TripEditing.JOINED_TRIP_LABEL, trip.label)
        assertEquals(b.startMs, trip.startMs)
        val stay = byKey[TimelineEdits.key(TimelineEdits.VISIT, "v-work")]!!
        assertTrue(stay.hidden)
        assertEquals(TripEditing.JOINED_STAY_LABEL, stay.label)
        // The stays before the first trip and after the second are not touched.
        assertNull(byKey[TimelineEdits.key(TimelineEdits.VISIT, "v-before")])
        assertNull(byKey[TimelineEdits.key(TimelineEdits.VISIT, "v-after")])
        assertEquals(2, change.timelineEdits.size)
    }

    @Test
    fun aStayStillGoingIsNeverHidden() {
        val going = visit("v-now", "work", 6, 19, ongoing = true)
        val change = TripEditing.merge(a, b, aPoints, bPoints, between, listOf(going), emptyMap(), stride, t0, "m1")!!
        assertEquals(1, change.timelineEdits.size)
    }

    @Test
    fun theMergeIsWrittenDownByItsBoundaryTimes() {
        val journal = merged().journal!!
        assertEquals(TripEditKind.MERGE, journal.kind)
        assertEquals(a.endMs, journal.atMs)
        assertEquals(b.startMs, journal.otherMs)
        assertEquals(0L, journal.undoneMs)
        assertNull(merged().undoneEditId)
    }

    @Test
    fun tripsThatDoNotFollowEachOtherAreNotMerged() {
        assertNull(TripEditing.merge(b, a, bPoints, aPoints, between, visits, emptyMap(), stride, t0, "m"))
        assertNull(TripEditing.merge(a, a, aPoints, aPoints, between, visits, emptyMap(), stride, t0, "m"))
    }

    @Test
    fun hidingKeepsWhatYouAlreadyChangedOnTheLaterTrip() {
        val old = TimelineEdits.edit(TimelineEdits.TRIP, "b", startMs = at(19), endMs = at(25), activityId = "commute", now = 1L, label = "Trip")
        val change = merged(mapOf(old.key to old))
        val saved = change.timelineEdits.first { it.refId == "b" }
        assertEquals(at(19), saved.startMs)
        assertEquals(at(25), saved.endMs)
        assertEquals("commute", saved.activityId)
        assertTrue(saved.hidden)
    }

    // ---- undo a merge

    @Test
    fun undoingAMergeGivesBackTheEarlierTripAsItWas() {
        val change = merged()
        val joined = change.trips.single()
        val edits = change.timelineEdits.associateBy { it.key }
        val pair = TripEditing.joinedPair(change.journal!!, listOf(joined, b, ) )
        assertNotNull(pair)
        val undo = TripEditing.unmerge(change.journal!!, joined, b, change.points, visits, edits, stride, t0 + 120 * min)
        val back = undo.trips.single()
        assertEquals("a", back.id)
        assertEquals(a.endMs, back.endMs)
        assertEquals("work", back.toPlaceId)
        assertEquals(a.activity, back.activity)
        assertEquals("walk", back.mode)
        assertEquals("you", back.modeSource)
        assertEquals(a.distanceM.toDouble(), back.distanceM.toDouble(), 40.0)
        assertEquals("m1", undo.undoneEditId)
        assertNull(undo.journal)
    }

    @Test
    fun undoingAMergeShowsTheLaterTripAndTheJoinedStayAgain() {
        val change = merged()
        val edits = change.timelineEdits.associateBy { it.key }
        val undo = TripEditing.unmerge(change.journal!!, change.trips.single(), b, change.points, visits, edits, stride, t0 + 120 * min)
        assertEquals(2, undo.timelineEdits.size)
        assertTrue(undo.timelineEdits.none { it.hidden })
    }

    @Test
    fun aStayYouHidAloneStaysHiddenWhenTheMergeIsUndone() {
        val byHand = TimelineEdits.edit(TimelineEdits.VISIT, "v-work", startMs = atWork.startMs, hidden = true, now = 1L, label = "Work")
        val change = merged(mapOf(byHand.key to byHand))
        // The merge hid it too, with its own reason, so it is now "joined away"; a stay hidden before and not by the merge is left alone.
        val onlyByHand = mapOf(byHand.key to byHand, TimelineEdits.key(TimelineEdits.TRIP, "b") to change.timelineEdits.first { it.refId == "b" })
        val undo = TripEditing.unmerge(change.journal!!, change.trips.single(), b, change.points, visits, onlyByHand, stride, t0 + 120 * min)
        assertTrue(undo.timelineEdits.none { it.kind == TimelineEdits.VISIT })
    }

    @Test
    fun aJoinIsFoundAgainByTheTimesAtItsBoundary() {
        val change = merged()
        val joined = change.trips.single()
        val found = TripEditing.joinedPair(change.journal!!, listOf(joined, b))!!
        assertEquals("a", found.first.id)
        assertEquals("b", found.second.id)
        // A trip that was cut or extended since no longer matches, so the pair is not found.
        assertNull(TripEditing.joinedPair(change.journal!!, listOf(joined.copy(endMs = joined.endMs + min), b)))
        assertNull(TripEditing.joinedPair(change.journal!!, listOf(joined)))
    }

    @Test
    fun isJoinedAwayTellsJoinedFromHiddenByHand() {
        val joined = TimelineEdits.edit(TimelineEdits.TRIP, "b", hidden = true, now = 1L, label = TripEditing.JOINED_TRIP_LABEL)
        val byHand = TimelineEdits.edit(TimelineEdits.TRIP, "b", hidden = true, now = 1L, label = "Trip")
        assertTrue(TripEditing.isJoinedAway(joined))
        assertFalse(TripEditing.isJoinedAway(byHand))
        assertFalse(TripEditing.isJoinedAway(joined.copy(hidden = false)))
        assertFalse(TripEditing.isJoinedAway(null))
    }

    // ---- split

    private val ride = TripEntity("r", at(0), at(9), "home", "gym", 1000, 6, 7, "WALKING=540000", "walk", "you")
    private val ridePoints = (0..9).map { point("r", it, 28.600 + it * 0.001, steps = 1000L + it * 150) }

    @Test
    fun aSplitKeepsTheFirstPartUnderTheOldRowAndAnswer() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        val first = change.trips.first { it.id == "r" }
        assertEquals(ride.startMs, first.startMs)
        assertEquals(at(5), first.endMs)
        assertEquals("home", first.fromPlaceId)
        assertEquals("", first.toPlaceId)
        assertEquals("walk", first.mode)
        assertEquals("you", first.modeSource)
        // The phone's walking and driving times cannot be cut by the clock, so they stay with the first part.
        assertEquals(ride.activity, first.activity)
    }

    @Test
    fun aSplitMakesTheSecondPartANewTripWithItsOwnGuess() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        val second = change.trips.first { it.id == "r2" }
        assertEquals(at(5), second.startMs)
        assertEquals(ride.endMs, second.endMs)
        assertEquals("", second.fromPlaceId)
        assertEquals("gym", second.toPlaceId)
        assertEquals("auto", second.modeSource)
        assertEquals("", second.activity)
        assertEquals(2, change.trips.size)
    }

    @Test
    fun theSecondPartGetsCopiesOfItsReadingsAndTheCutReadingIsInBoth() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        assertEquals(5, change.points.size)
        assertTrue(change.points.all { it.tripId == "r2" })
        assertEquals(at(5), change.points.first().ms)
        assertEquals(at(9), change.points.last().ms)
        // Each part's own readings are the ones between its start and end, so the cut reading is on both paths.
        assertEquals(6, TripEditing.pointsOf(change.trips.first { it.id == "r" }, ridePoints).size)
    }

    @Test
    fun theTwoPartsAddUpToAboutTheWholeTrip() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        val sum = change.trips.sumOf { it.distanceM }
        val whole = TripEditing.withStats(ride, ridePoints, stride).distanceM
        assertEquals(whole.toDouble(), sum.toDouble(), 5.0)
        assertTrue(change.trips.all { it.distanceM > 0 })
    }

    @Test
    fun aSplitNeedsTwoReadingsOnEachSideAndARealReading() {
        assertNull(TripEditing.split(ride, ridePoints, at(1), "r2", stride, t0, "s"))
        assertNotNull(TripEditing.split(ride, ridePoints, at(2), "r2", stride, t0, "s"))
        assertNull(TripEditing.split(ride, ridePoints, at(9), "r2", stride, t0, "s"))
        assertNull(TripEditing.split(ride, ridePoints, at(5) + 1000L, "r2", stride, t0, "s"))
    }

    @Test
    fun theSplitIsWrittenDownByTheTimeOfTheCut() {
        val journal = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!.journal!!
        assertEquals(TripEditKind.SPLIT, journal.kind)
        assertEquals(at(5), journal.atMs)
        assertEquals("s1", journal.id)
    }

    @Test
    fun aSplitIsFoundAgainAndCanBeUndone() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        val (first, second) = TripEditing.splitPair(change.journal!!, change.trips)!!
        assertEquals("r", first.id)
        assertEquals("r2", second.id)
        val undo = TripEditing.unsplit(change.journal!!, first, second, ridePoints, change.points, emptyMap(), stride, t0 + 60 * min)
        val back = undo.trips.single()
        assertEquals("r", back.id)
        assertEquals(ride.endMs, back.endMs)
        assertEquals("gym", back.toPlaceId)
        assertEquals("walk", back.mode)
        assertEquals(TripEditing.withStats(ride, ridePoints, stride).distanceM.toDouble(), back.distanceM.toDouble(), 5.0)
        val hide = undo.timelineEdits.single()
        assertEquals("r2", hide.refId)
        assertTrue(hide.hidden)
        assertEquals("s1", undo.undoneEditId)
    }

    @Test
    fun aSplitIsNotFoundWhenTheTripsNoLongerMeetAtTheCut() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        val moved = change.trips.map { if (it.id == "r") it.copy(endMs = at(6)) else it }
        assertNull(TripEditing.splitPair(change.journal!!, moved))
    }

    @Test
    fun aMergeCanJoinTheTwoPartsOfASplitAgain() {
        val change = TripEditing.split(ride, ridePoints, at(5), "r2", stride, t0 + 30 * min, "s1")!!
        val first = change.trips.first { it.id == "r" }
        val second = change.trips.first { it.id == "r2" }
        val again = TripEditing.merge(first, second, ridePoints, change.points, emptyList(), emptyList(), emptyMap(), stride, t0 + 40 * min, "m2")!!
        assertEquals(ride.endMs, again.trips.single().endMs)
        assertEquals(TripEditing.withStats(ride, ridePoints, stride).distanceM.toDouble(), again.trips.single().distanceM.toDouble(), 5.0)
    }

    // ---- numbers

    @Test
    fun aTripWithFewerThanTwoReadingsHasNoDistance() {
        val one = TripEditing.withStats(ride.copy(distanceM = 999), ridePoints.take(1), stride)
        assertEquals(0, one.distanceM)
        assertEquals(0, one.topKmh)
    }

    @Test
    fun theReadingsOfATripAreOnlyThoseBetweenItsStartAndEnd() {
        val trip = ride.copy(endMs = at(4))
        assertEquals(5, TripEditing.pointsOf(trip, ridePoints + ridePoints).size)
    }

    @Test
    fun walkingAndDrivingTimesAddUpAndTakeAwayTheSameWay() {
        assertEquals("IN_VEHICLE=200,WALKING=300", TripEditing.addActivity("WALKING=100", "IN_VEHICLE=200,WALKING=200"))
        assertEquals("WALKING=100", TripEditing.subtractActivity("IN_VEHICLE=200,WALKING=300", "IN_VEHICLE=200,WALKING=200"))
        assertEquals("", TripEditing.subtractActivity("WALKING=100", "WALKING=500"))
        assertEquals("WALKING=100", TripEditing.addActivity("WALKING=100", ""))
    }

    // ---- which edit can be undone

    private fun edit(id: String, kind: String, at: Int, other: Int = 0, created: Long) =
        TripEditEntity(id, kind, at(at), if (other == 0) 0L else at(other), created)

    @Test
    fun aNewerChangeOnTopOfAnEditHasToBeUndoneFirst() {
        val merge = edit("m", TripEditKind.MERGE, 4, 20, created = 100L)
        val split = edit("s", TripEditKind.SPLIT, 12, created = 200L)
        val region = at(0)..at(24)
        assertEquals("s", TripEditing.blockedBy(merge, listOf(merge, split), region)?.id)
        // The newer one is the one that can be undone.
        assertNull(TripEditing.blockedBy(split, listOf(merge, split), region))
    }

    @Test
    fun aNewerChangeSomewhereElseDoesNotBlock() {
        val merge = edit("m", TripEditKind.MERGE, 4, 20, created = 100L)
        val far = edit("s", TripEditKind.SPLIT, 300, created = 200L)
        assertNull(TripEditing.blockedBy(merge, listOf(merge, far), at(0)..at(24)))
    }

    @Test
    fun aChangeThatWasUndoneDoesNotBlock() {
        val merge = edit("m", TripEditKind.MERGE, 4, 20, created = 100L)
        val undone = edit("s", TripEditKind.SPLIT, 12, created = 200L).copy(undoneMs = 300L)
        assertNull(TripEditing.blockedBy(merge, listOf(merge, undone), at(0)..at(24)))
    }

    @Test
    fun aNewerMergeOfTheWholeTripWithItsNeighbourBlocksToo() {
        val first = edit("m1", TripEditKind.MERGE, 4, 20, created = 100L)
        // The next trip starts at minute 40, so the second merge's boundary is at the end of the first (minute 24) and at 40.
        val second = edit("m2", TripEditKind.MERGE, 24, 40, created = 200L)
        assertEquals("m2", TripEditing.blockedBy(first, listOf(first, second), at(0)..at(24))?.id)
    }

    // ---- the fixes found in the C6 review

    @Test
    fun aGuessedModeIsGuessedAgainForTheWholeJoinedTrip() {
        val walkGuess = a.copy(activity = "WALKING=60000", mode = "walk", modeSource = "auto")
        val drive = b.copy(activity = "IN_VEHICLE=900000")
        val joined = TripEditing.merge(walkGuess, drive, aPoints, bPoints, between, visits, emptyMap(), stride, t0 + 90 * min, "m1")!!.trips.single()
        assertEquals("vehicle", joined.mode)
        assertEquals("auto", joined.modeSource)
    }

    @Test
    fun aTripYouHidAloneIsNotRelabelledByAMerge() {
        val byHand = TimelineEdits.edit(TimelineEdits.TRIP, "b", startMs = b.startMs, endMs = -1L, activityId = "", hidden = true, now = t0, label = "Trip")
        val change = merged(mapOf(byHand.key to byHand))
        assertNull(change.timelineEdits.firstOrNull { it.key == byHand.key })
    }

    @Test
    fun undoingAMergeDoesNotShowATripYouHidAlone() {
        val change = merged()
        val byHand = TimelineEdits.edit(TimelineEdits.TRIP, "b", startMs = b.startMs, endMs = -1L, activityId = "", hidden = true, now = t0, label = "Trip")
        val undo = TripEditing.unmerge(change.journal!!, change.trips.single(), b, change.points, visits, mapOf(byHand.key to byHand), stride, t0 + 120 * min)
        assertNull(undo.timelineEdits.firstOrNull { it.key == byHand.key })
    }

    @Test
    fun undoingAMergeFindsTheEndPlaceFromTheStayThere() {
        val change = merged()
        val laterWithoutPlace = b.copy(fromPlaceId = "")
        val undo = TripEditing.unmerge(change.journal!!, change.trips.single(), laterWithoutPlace, change.points, visits, emptyMap(), stride, t0 + 120 * min)
        assertEquals("work", undo.trips.single().toPlaceId)
    }

    @Test
    fun theSecondPartOfASplitIsNotMistakenForATripAMergeHid() {
        val cut = at(2)
        val edit = TripEditEntity("s1", TripEditKind.SPLIT, cut, 0L, t0)
        val first = TripEntity("a", at(0), cut, "", "", 0, 0, 0, "", "walk", "auto")
        val hiddenByMerge = TripEntity("hb", cut, at(4), "", "", 0, 0, 0, "", "walk", "auto")
        val secondPart = TripEntity("c", cut, at(4), "", "", 0, 0, 0, "", "walk", "auto")
        val trips = listOf(first, hiddenByMerge, secondPart)
        assertEquals("hb", TripEditing.splitPair(edit, trips)!!.second.id)
        assertEquals("c", TripEditing.splitPair(edit, trips, setOf("hb"))!!.second.id)
        // When only the hidden one matches, it is still used (better than nothing).
        assertEquals("hb", TripEditing.splitPair(edit, listOf(first, hiddenByMerge), setOf("hb"))!!.second.id)
    }
}
