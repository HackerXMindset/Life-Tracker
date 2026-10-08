package com.lifetracker.app.data

import android.content.Context
import androidx.room.withTransaction
import java.time.LocalDate

/** Adds the entries that monthly items have come due for. Safe to run as often as you like. */
object MoneyPoster {
    /** Returns how many entries were added. */
    suspend fun postDue(context: Context, today: LocalDate = LocalDate.now()): Int {
        val db = AppDatabase.get(context)
        return db.withTransaction {
            var added = 0
            for (item in db.moneyDao().allItems()) {
                val plan = Recurring.plan(item, today)
                if (plan.entries.isNotEmpty()) {
                    db.moneyDao().upsertEntries(plan.entries)
                    added += plan.entries.size
                }
                if (plan.lastPosted != item.lastPosted) {
                    db.moneyDao().upsertItem(item.copy(lastPosted = plan.lastPosted))
                }
            }
            added
        }
    }
}
