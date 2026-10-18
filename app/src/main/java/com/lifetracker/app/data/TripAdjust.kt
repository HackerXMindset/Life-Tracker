package com.lifetracker.app.data

/** Works a trip's distance out again after something about it changed (for example the way you travelled). */
object TripAdjust {
    /** Reads the trip and its readings from the database and saves the distance and speeds if they changed. Nothing is deleted. */
    suspend fun refresh(db: AppDatabase, tripId: String, strideM: Double) {
        val trip = db.tripDao().trip(tripId) ?: return
        val fixed = PlaceCleanup.refreshTrip(trip, db.tripDao().points(tripId), strideM) ?: return
        db.tripDao().setStats(fixed.id, fixed.distanceM, fixed.medianKmh, fixed.topKmh)
    }
}
