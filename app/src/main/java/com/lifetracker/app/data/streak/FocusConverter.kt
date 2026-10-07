package com.lifetracker.app.data.streak

import java.time.LocalDate

/** One Timeline block made from (part of) a Streak focus session. */
data class FocusPiece(
    val sourceId: String,
    val date: LocalDate,
    val startMinute: Int,
    val endMinute: Int,
)

/** Turns Streak focus sessions into Timeline entries. */
object FocusConverter {
    private const val LAST_MINUTE = 1439

    /**
     * Splits a session at midnight, because a Timeline entry belongs to one day.
     * Sessions under a minute are ignored (Streak does the same). The first piece
     * keeps the session's id; later pieces get "-2", "-3"... so each is imported once.
     */
    fun pieces(session: StreakFocus): List<FocusPiece> {
        if (session.seconds < 60) return emptyList()
        var remaining = (session.seconds + 30) / 60
        var cursor = session.startedAt.withSecond(0).withNano(0)
        val pieces = ArrayList<FocusPiece>()
        var index = 1
        while (remaining > 0 && index <= 10) {
            val start = cursor.hour * 60 + cursor.minute
            val room = 24 * 60 - start
            val end = if (remaining <= room) start + remaining else 24 * 60
            val clipped = end.coerceAtMost(LAST_MINUTE)
            if (clipped > start) {
                pieces += FocusPiece(
                    sourceId = if (index == 1) session.id else "${session.id}-$index",
                    date = cursor.toLocalDate(),
                    startMinute = start,
                    endMinute = clipped,
                )
            }
            if (remaining <= room) break
            remaining -= room
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay()
            index++
        }
        return pieces
    }

    /** Maps a Streak habit category to one of this app's Timeline categories (by its stored key). */
    fun timelineCategory(streakCategory: String?): String {
        val c = streakCategory.orEmpty().lowercase()
        return when {
            "fitness" in c || "exercise" in c || "workout" in c -> "Exercise"
            "health" in c || "mindful" in c -> "Health"
            "financ" in c -> "Routine"
            else -> "Study"
        }
    }
}
