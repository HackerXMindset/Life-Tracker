package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.DateSpan
import com.lifetracker.app.data.RangePresets
import com.lifetracker.app.data.TimelineEdits
import com.lifetracker.app.data.TripEntity
import com.lifetracker.app.data.TripsRange
import com.lifetracker.app.data.TripsSummary
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** One trip as the Trips view draws it: the trip, its colour on the map and its path. */
class TripsItem(
    val trip: TripEntity,
    val color: Int,
    val path: List<Pair<Double, Double>>,
    val fromName: String,
    val toName: String,
)

/** Everything the Trips view shows for one stretch of days. */
class TripsState(
    val window: DateSpan,
    val title: String,
    val items: List<TripsItem>,
    val days: List<Pair<LocalDate, List<TripsItem>>>,
    val summary: TripsSummary,
    /** True when the stretch ends today or later, so "next" has nothing to show. */
    val atEnd: Boolean,
)

/**
 * The Trips view: whole trips for a day, a week, a month, a year or two dates you pick. Hidden trips and the ones a join took
 * away are left out, the same as on the Timeline. Nothing here changes any trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val spanFlow = MutableStateFlow(TripsRange.Span.WEEK)
    private val anchorFlow = MutableStateFlow(LocalDate.now())
    private val customFlow = MutableStateFlow(RangePresets.span("30", LocalDate.now()))

    val span: StateFlow<TripsRange.Span> = spanFlow
    val custom: StateFlow<DateSpan> = customFlow

    fun setSpan(s: TripsRange.Span) {
        spanFlow.value = s
    }

    fun step(direction: Int) {
        anchorFlow.value = TripsRange.shift(spanFlow.value, anchorFlow.value, direction)
    }

    fun setCustom(span: DateSpan) {
        customFlow.value = span
    }

    private val windowFlow = combine(spanFlow, anchorFlow, customFlow) { s, a, c -> s to TripsRange.window(s, a, c) }

    val state: StateFlow<TripsState?> = windowFlow.flatMapLatest { (s, w) ->
        val (from, to) = TripsRange.bounds(w, zone)
        // A long stretch draws thinner, so a year of trips still moves smoothly on the map.
        val thinM = when {
            w.dayCount > 120 -> 60.0
            w.dayCount > 31 -> 30.0
            w.dayCount > 7 -> 12.0
            else -> 0.0
        }
        combine(
            db.tripDao().observeAllTrips(),
            db.tripDao().observePointsBetween(from, to),
            db.timelineEditDao().observeAll(),
            db.placeDao().observePlaces(),
        ) { trips, points, edits, places ->
            val hidden = edits.filter { it.kind == TimelineEdits.TRIP && TimelineEdits.isHidden(it) }.map { it.refId }.toSet()
            val names = places.associate { it.id to it.name }
            val shown = TripsRange.inRange(trips, from, to, hidden)
            val sorted = TripsRange.prepare(points)
            val items = shown.mapIndexed { i, t ->
                val own = TripsRange.pointsOf(t, sorted).map { it.lat to it.lng }
                TripsItem(t, TripsRange.colorOf(i), TripsRange.thin(own, thinM), names[t.fromPlaceId].orEmpty(), names[t.toPlaceId].orEmpty())
            }
            val byId = items.associateBy { it.trip.id }
            val days = TripsRange.byDay(shown, zone).map { (d, list) -> d to list.mapNotNull { byId[it.id] } }
            val today = LocalDate.now()
            TripsState(w, TripsRange.title(s, w, today), items, days, TripsRange.summary(shown), w.to >= today)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
