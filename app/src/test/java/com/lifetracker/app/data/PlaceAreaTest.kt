package com.lifetracker.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F2: places you can draw as any shape, find, sort by distance and page through. */
class PlaceAreaTest {
    private val min = 60_000L
    private val t0 = 1_760_000_000_000L

    // An L-shaped plot about 175 m across (0.0016 deg). The corner beyond 0.0008 in both directions is cut out.
    private val l = listOf(
        28.6000 to 77.2000, 28.6000 to 77.2016, 28.6008 to 77.2016,
        28.6008 to 77.2008, 28.6016 to 77.2008, 28.6016 to 77.2000,
    )

    private fun placeFrom(corners: List<Pair<Double, Double>>, id: String = "plot", name: String = "Plot"): PlaceEntity {
        val c = PlaceShape.centre(corners)
        return PlaceEntity(id, name, "home", c.first, c.second, PlaceShape.boundingRadiusM(corners, c), false, 0L, polygon = PlaceShape.format(corners))
    }

    @After
    fun reset() = TripGuess.forTest(false)

    @Test
    fun cornersRoundTripThroughText() {
        val back = PlaceShape.parse(PlaceShape.format(l))
        assertEquals(l.size, back.size)
        assertEquals(l[2].first, back[2].first, 1e-6)
        assertEquals(1, PlaceShape.parse("nonsense;1,2").size)
        assertTrue(PlaceShape.parse("").isEmpty())
    }

    @Test
    fun insideAnLShapeButNotInItsCutOutCorner() {
        assertTrue(PlaceShape.contains(l, 28.6004, 77.2012))
        assertTrue(PlaceShape.contains(l, 28.6012, 77.2004))
        assertFalse(PlaceShape.contains(l, 28.6012, 77.2012))
        assertFalse(PlaceShape.contains(l, 28.5990, 77.2004))
    }

    @Test
    fun aDrawnAreaIsUsedByPlaceAtInsteadOfItsBoundingCircle() {
        val plot = placeFrom(l)
        // Inside the bounding circle, but in the cut-out corner: not in the place.
        assertNull(PlaceRules.placeAt(listOf(plot), 28.6012, 77.2012))
        assertEquals("plot", PlaceRules.placeAt(listOf(plot), 28.6004, 77.2012)?.id)
        // The reading's own error is forgiven: about 10 m out in the corner counts with a 12 m slack, not with none.
        assertNull(PlaceRules.placeAt(listOf(plot), 28.6009, 77.2009))
        assertNotNull(PlaceRules.placeAt(listOf(plot), 28.6009, 77.2009, 12.0))
    }

    @Test
    fun theBoundingCircleHoldsEveryCorner() {
        val c = PlaceShape.centre(l)
        val r = PlaceShape.boundingRadiusM(l, c)
        assertTrue(l.all { PlaceRules.distanceM(c.first, c.second, it.first, it.second) <= r })
        assertTrue(r in 100..150)
    }

    @Test
    fun areaAndValidity() {
        assertTrue(PlaceShape.areaM2(l) in 18_000.0..24_000.0)
        assertTrue(PlaceShape.valid(l))
        assertFalse(PlaceShape.valid(l.take(2)))
        assertFalse(PlaceShape.valid(listOf(28.6 to 77.2, 28.60001 to 77.2, 28.6 to 77.20001)))
    }

    @Test
    fun aRingMadeFromACircleStaysInsideTheCircle() {
        val ring = PlaceShape.fromCircle(28.61, 77.21, 50.0)
        assertEquals(8, ring.size)
        assertTrue(ring.all { PlaceRules.distanceM(28.61, 77.21, it.first, it.second) in 49.0..51.0 })
    }

    @Test
    fun distanceToADrawnAreaIsZeroInsideAndTheEdgeOutside() {
        val plot = placeFrom(l)
        assertEquals(0.0, PlaceShape.distanceToM(plot, 28.6004, 77.2012), 0.0)
        val out = PlaceShape.distanceToM(plot, 28.6012, 77.2012)
        assertTrue(out in 30.0..50.0)
    }

    // ---- stays use the drawn area ----

    private class Run(val places: List<PlaceEntity>) {
        var state = WatchState()
        val rows = LinkedHashMap<String, PlaceVisitEntity>()
        fun feed(f: Fix) {
            val step = PlaceEngine.onFix(state, f, places, rows.values.toList(), f.timeMs)
            step.saves.forEach { rows[it.id] = it }
            state = step.state
        }
    }

    private fun fix(minute: Int, lat: Double, lng: Double) = Fix(lat, lng, 8f, t0 + minute * min)

    @Test
    fun walkingAroundInsideADrawnAreaStaysOneVisit() {
        val run = Run(listOf(placeFrom(l)))
        for (m in 0..3) run.feed(fix(m, 28.6004, 77.2004))
        // Along the long bar and up the arm of the L: about 100 m from the first spot, still inside the plot.
        for (m in 4..8) run.feed(fix(m, 28.6004, 77.2014))
        for (m in 9..12) run.feed(fix(m, 28.6014, 77.2004))
        assertEquals(1, run.rows.size)
        assertEquals("plot", run.rows.values.single().placeId)
        assertNull(run.state.trip)
    }

    @Test
    fun theCutOutCornerIsOutsideSoItEndsTheStay() {
        val run = Run(listOf(placeFrom(l)))
        for (m in 0..3) run.feed(fix(m, 28.6004, 77.2014))
        for (m in 4..5) run.feed(fix(m, 28.6014, 77.2014))
        assertNotNull(run.state.trip)
    }

    // ---- the list ----

    private fun circle(id: String, name: String, lat: Double, kind: String = "other", deleted: Long = 0L, archived: Boolean = false) =
        PlaceEntity(id, name, kind, lat, 77.2, 50, archived, 0L, deletedMs = deleted)

    private val all = listOf(
        circle("far", "Far gym", 28.9, "gym"),
        circle("near", "Home", 28.6001, "home"),
        circle("mid", "Library", 28.65, "study"),
        circle("gone", "Old shop", 28.6, "shop", deleted = 5L, archived = true),
        circle("paused", "Cousin", 28.6002, "people", archived = true),
    )

    @Test
    fun deletedPlacesAreNotListed() {
        assertEquals(listOf("far", "near", "mid", "paused"), PlaceList.matching(all, "").map { it.id })
    }

    @Test
    fun searchFindsByNameOrKind() {
        assertEquals(listOf("mid"), PlaceList.matching(all, " lib ").map { it.id })
        assertEquals(listOf("far"), PlaceList.matching(all, "GYM").map { it.id })
        assertTrue(PlaceList.matching(all, "zzz").isEmpty())
    }

    @Test
    fun closestFirstAndNotWatchedLast() {
        val rows = PlaceList.arrange(PlaceList.matching(all, ""), 28.6 to 77.2)
        assertEquals(listOf("near", "mid", "far", "paused"), rows.map { it.place.id })
        assertTrue(rows[0].distanceM!! < rows[1].distanceM!!)
    }

    @Test
    fun withoutAPositionTheyGoByName() {
        val rows = PlaceList.arrange(PlaceList.matching(all, ""), null)
        assertEquals(listOf("far", "near", "mid", "paused"), rows.map { it.place.id })
        assertNull(rows[0].distanceM)
    }

    @Test
    fun pagesOfTen() {
        val many = (1..23).map { circle("p$it", "Place $it", 28.6 + it / 1000.0) }
        val rows = PlaceList.arrange(many, 28.6 to 77.2)
        val first = PlaceList.page(rows, 0)
        assertEquals(10, first.rows.size)
        assertEquals(3, first.pageCount)
        assertEquals(23, first.total)
        assertEquals(3, PlaceList.page(rows, 2).rows.size)
        // A page past the end is the last page; an empty list is one empty page.
        assertEquals(2, PlaceList.page(rows, 9).page)
        assertEquals(1, PlaceList.page(emptyList(), 0).pageCount)
    }

    @Test
    fun distanceWords() {
        assertEquals("350 m away", PlaceList.distanceText(350.4))
        assertEquals("2.4 km away", PlaceList.distanceText(2400.0))
        assertNull(PlaceList.distanceText(null))
    }
}
