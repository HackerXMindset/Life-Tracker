package com.lifetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.LocationDays
import com.lifetracker.app.data.PlaceTrail
import com.lifetracker.app.data.PlacesSettings
import com.lifetracker.app.data.TimelineEditEntity
import com.lifetracker.app.data.TimelineEdits
import com.lifetracker.app.data.TripAdjust
import com.lifetracker.app.data.TripEditing
import com.lifetracker.app.data.TripEditor
import com.lifetracker.app.data.TripEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Location page shows for one day. */
class LocationDay(
    val summary: LocationDays.Summary,
    /** The day's stays and journeys in order, with what you hid left out. */
    val rows: List<PlaceTrail.Row>,
    /** Trips you hid on this day, each with a way to show it again. */
    val hiddenTrips: List<TimelineEditEntity>,
)

/**
 * The Location page: a month with a dot on every day you moved, and one day below it with its numbers, its stays and journeys.
 * It reads the same tables as the Timeline and obeys what you hid there, so the two always agree.
 */
class LocationViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val zone: ZoneId = ZoneId.systemDefault()
    private val settings = PlacesSettings(app)

    private fun <T> share(initial: T, flow: Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private val monthFlow = MutableStateFlow(YearMonth.now(zone))
    val month: StateFlow<YearMonth> = monthFlow

    private val dayFlow = MutableStateFlow(LocalDate.now(zone))
    val day: StateFlow<LocalDate> = dayFlow

    fun pickDay(d: LocalDate) {
        dayFlow.value = d
        monthFlow.value = YearMonth.from(d)
    }

    fun showMonth(m: YearMonth) {
        monthFlow.value = m
    }

    fun goToday() = pickDay(LocalDate.now(zone))

    private val edits: Flow<Map<String, TimelineEditEntity>> = db.timelineEditDao().observeAll().map { list -> list.associateBy { it.key } }

    /** Every trip that is not hidden, oldest first. */
    private val shownTrips: Flow<List<TripEntity>> =
        combine(db.tripDao().observeAllTrips(), edits) { trips, e ->
            trips.filter { !TimelineEdits.isHidden(e[TimelineEdits.key(TimelineEdits.TRIP, it.id)]) }
        }

    /** The days of the shown month on which you moved. */
    val movementDays: StateFlow<Set<LocalDate>> = share(
        emptySet(),
        combine(shownTrips, monthFlow) { trips, m ->
            val (from, to) = LocationDays.monthBounds(m, zone)
            LocationDays.movementDays(trips.filter { it.startMs in from until to }, zone)
        },
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayState: StateFlow<LocationDay?> = share(
        null,
        dayFlow.flatMapLatest { d ->
            val from = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            combine(db.placeDao().observePlaces(), db.placeDao().visitsBetween(from, to), shownTrips, edits) { places, visits, trips, e ->
                val now = System.currentTimeMillis()
                val shownVisits = visits.filter { !TimelineEdits.isHidden(e[TimelineEdits.key(TimelineEdits.VISIT, it.id)]) }
                val dayTrips = trips.filter { it.startMs in from until to }
                LocationDay(
                    summary = LocationDays.summary(dayTrips, shownVisits, from, to, now),
                    rows = PlaceTrail.build(shownVisits, dayTrips, places.associate { it.id to it.name }, now),
                    hiddenTrips = e.values.filter {
                        it.kind == TimelineEdits.TRIP && it.hidden && !TripEditing.isJoinedAway(it) && it.startMs in from until to
                    }.sortedBy { it.startMs },
                )
            }
        },
    )

    /** Shows a trip you hid again. */
    fun showTrip(edit: TimelineEditEntity) {
        viewModelScope.launch { db.timelineEditDao().upsert(edit.copy(hidden = false, updatedMs = System.currentTimeMillis())) }
    }

    /**
     * You say how you travelled. For a trip the phone recorded this is your answer and the app looks again at the trips
     * it had only guessed; for a journey with no trip it is saved as a new trip record.
     */
    fun setJourneyMode(journey: PlaceTrail.Journey, mode: String) {
        viewModelScope.launch {
            val trip = journey.trip
            if (trip != null) {
                TripAdjust.setMode(db, trip.id, mode, settings.strideM())
            } else {
                PlaceTrail.tripFor(journey, mode)?.let { db.tripDao().upsertTrip(it) }
            }
        }
    }

    /** Hides a trip (kept) or shows it again. */
    fun setTripHidden(trip: TripEntity, hidden: Boolean) {
        viewModelScope.launch { TripEditor.setHidden(db, trip, hidden) }
    }
}
