package com.lifetracker.app.data

/** Works a trip's distance out again after something about it changed (for example the way you travelled). */
object TripAdjust {
    /** Reads the trip and its readings from the database and saves the distance and speeds if they changed. Nothing is deleted. */
    suspend fun refresh(db: AppDatabase, tripId: String, strideM: Double) {
        val trip = db.tripDao().trip(tripId) ?: return
        val fixed = PlaceCleanup.refreshTrip(trip, db.tripDao().points(tripId), strideM) ?: return
        db.tripDao().setStats(fixed.id, fixed.distanceM, fixed.medianKmh, fixed.topKmh)
    }

    /**
     * Saves your answer to "How did you travel?" for a trip, works its distance out again for that way of travelling, then looks
     * again at the trips the app had only guessed (what you just said teaches the guess).
     */
    suspend fun setMode(db: AppDatabase, tripId: String, mode: String, strideM: Double) {
        val dao = db.tripDao()
        dao.setMode(tripId, mode, "you")
        refresh(db, tripId, strideM)
        val learned = dao.corrected().map { TripModes.learned(it) }
        for (t in dao.guessed()) {
            val stats = TripStats(t.distanceM, t.endMs - t.startMs, t.medianKmh, t.topKmh, TripRules.MIN_FARTHEST_M)
            val guess = TripModes.guess(stats, TripModes.parseActivity(t.activity), t.fromPlaceId, t.toPlaceId, learned)
            if (guess != t.mode) {
                dao.setMode(t.id, guess, "auto")
                refresh(db, t.id, strideM)
            }
        }
    }
}
