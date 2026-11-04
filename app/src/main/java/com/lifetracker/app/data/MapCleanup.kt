package com.lifetracker.app.data

import android.content.Context

/** Keeps the downloaded maps under the limit by deleting the ones that are used least (see [MapLimits]). */
object MapCleanup {
    class State(val candidates: List<MapLimits.Candidate>, val usedBytes: Long)

    /** The downloaded areas as the limit rules see them, and how much they take together. */
    suspend fun state(context: Context, db: AppDatabase): State {
        val settings = MapSettings(context)
        val styleId = MapStyleStore.current(context, db).id
        val active = db.placeDao().activePlaces().map { it.id }.toSet()
        val done = db.mapDao().areas().filter { it.state == AreaState.DONE }
        val candidates = done.map {
            MapLimits.Candidate(it.id, it.bytes, MapLimits.rank(it, styleId, active), MapLimits.lastUsed(it, settings.usedMs(it.id)))
        }
        return State(candidates, done.sumOf { it.bytes })
    }

    /** Deletes the tiles of these areas (the rows stay). Returns how many areas were deleted. */
    suspend fun delete(context: Context, db: AppDatabase, ids: List<String>): Int {
        val byId = db.mapDao().areas().associateBy { it.id }
        var n = 0
        for (id in ids) {
            val area = byId[id] ?: continue
            MapDownloads.removeTiles(context, db, area)
            n++
        }
        if (n > 0) AppLog.i("Map", "Deleted the maps of $n area(s) to stay under the limit")
        return n
    }

    /** Deletes the least used maps while the maps on the phone pass the limit. Returns how many areas were deleted. */
    suspend fun enforce(context: Context, db: AppDatabase): Int {
        val settings = MapSettings(context)
        if (settings.limitBytes <= 0L || !settings.autoDelete) return 0
        val st = state(context, db)
        val plan = MapLimits.plan(st.candidates, st.usedBytes, 0L, settings.limitBytes, autoDelete = true)
        if (!plan.ok) {
            AppLog.w("Map", "Over the map limit: ${plan.problem}")
            return 0
        }
        return delete(context, db, plan.delete)
    }

    /** True when the limit is full and nothing may be deleted, so no new map should even be asked for. */
    suspend fun full(context: Context, db: AppDatabase): Boolean {
        val settings = MapSettings(context)
        if (settings.limitBytes <= 0L || settings.autoDelete) return false
        return state(context, db).usedBytes >= settings.limitBytes
    }
}
