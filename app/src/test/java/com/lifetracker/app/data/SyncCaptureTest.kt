package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCaptureTest {
    private fun table(name: String) = SyncTables.byName(name)!!

    @Test
    fun aSingleKeyIsTheColumnAsText() {
        assertEquals("CAST(NEW.`id` AS TEXT)", SyncCapture.keyExpr(table("trips"), "NEW."))
        assertEquals("CAST(`ms` AS TEXT)", SyncCapture.keyExpr(table("raw_fixes"), ""))
    }

    @Test
    fun aCompositeKeyIsJoinedWithCharacter31() {
        assertEquals("CAST(OLD.`habitId` AS TEXT) || char(31) || CAST(OLD.`date` AS TEXT)", SyncCapture.keyExpr(table("habit_completions"), "OLD."))
    }

    @Test
    fun everyCopiedTableGetsThreeTriggersAndNoOtherTableDoes() {
        SyncTables.all.forEach { t ->
            val sql = SyncCapture.createSql(t)
            assertEquals(3, sql.size)
            assertTrue(sql[0].startsWith("CREATE TRIGGER IF NOT EXISTS sync_${t.name}_ai AFTER INSERT ON `${t.name}`"))
            assertTrue(sql[1].contains("AFTER UPDATE ON `${t.name}`"))
            assertTrue(sql[2].contains("AFTER DELETE ON `${t.name}`"))
            sql.forEach { assertTrue(it, it.contains("'${t.name}'")) }
        }
        assertEquals(34, SyncTables.synced.size)
    }

    @Test
    fun theChangeLogIsNeverWatchedItself() {
        // A trigger on the change log would write to the change log for ever.
        assertFalse(SyncTables.synced.any { it.name == SyncCapture.OUTBOX })
    }

    @Test
    fun anUpdateThatMovesTheKeyAlsoNotesTheOldRow() {
        val update = SyncCapture.createSql(table("places"))[1]
        assertTrue(update.contains("SELECT 'places', CAST(OLD.`id` AS TEXT)"))
        assertTrue(update.contains("WHERE CAST(OLD.`id` AS TEXT) <> CAST(NEW.`id` AS TEXT)"))
    }

    @Test
    fun deleteNotesTheOldRowAndInsertTheNewOne() {
        assertTrue(SyncCapture.createSql(table("meals"))[2].contains("VALUES ('meals', CAST(OLD.`id` AS TEXT)"))
        assertTrue(SyncCapture.createSql(table("meals"))[0].contains("VALUES ('meals', CAST(NEW.`id` AS TEXT)"))
    }

    @Test
    fun dropRemovesTheSameThreeTriggers() {
        assertEquals(
            listOf("DROP TRIGGER IF EXISTS sync_trips_ai", "DROP TRIGGER IF EXISTS sync_trips_au", "DROP TRIGGER IF EXISTS sync_trips_ad"),
            SyncCapture.dropSql(table("trips")),
        )
    }

    @Test
    fun queueingEverythingCanBeNarrowedToAPiece() {
        val all = SyncCapture.queueAllSql(table("raw_fixes"))
        assertTrue(all.startsWith("INSERT OR REPLACE INTO sync_outbox"))
        assertTrue(all.endsWith("FROM `raw_fixes`"))
        assertTrue(SyncCapture.queueAllSql(table("raw_fixes"), "ms >= 5 AND ms < 9").endsWith("FROM `raw_fixes` WHERE ms >= 5 AND ms < 9"))
    }
}
