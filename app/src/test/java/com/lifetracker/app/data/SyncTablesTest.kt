package com.lifetracker.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTablesTest {
    private class Found(val name: String, val keys: List<String>)

    /** Reads the real entity declarations, so the list in SyncTables is checked against the database itself. */
    private fun entitiesInSource(): List<Found> {
        val dir = listOf(File("src/main/java/com/lifetracker/app/data"), File("app/src/main/java/com/lifetracker/app/data")).first { it.isDirectory }
        val entity = Regex("""@Entity\((.*?)\)\s*\n\s*(?:data )?class (\w+)\((.*?)\n\)""", RegexOption.DOT_MATCHES_ALL)
        val out = mutableListOf<Found>()
        dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            entity.findAll(file.readText()).forEach { m ->
                val (args, _, body) = m.destructured
                val name = Regex("""tableName\s*=\s*"(\w+)"""").find(args)!!.groupValues[1]
                val composite = Regex("""primaryKeys\s*=\s*\[(.*?)]""").find(args)
                val keys = if (composite != null) {
                    Regex(""""(\w+)"""").findAll(composite.groupValues[1]).map { it.groupValues[1] }.toList()
                } else {
                    listOf(Regex("""@PrimaryKey(?:\([^)]*\))?\s*val (\w+)""").find(body)!!.groupValues[1])
                }
                out += Found(name, keys)
            }
        }
        return out
    }

    @Test
    fun everyTableInTheDatabaseIsSortedIntoSyncedOrNot() {
        val found = entitiesInSource()
        assertTrue("found only ${found.size}", found.size >= 30)
        assertEquals(found.map { it.name }.sorted(), SyncTables.all.map { it.name }.sorted())
        assertEquals("a table is listed twice", SyncTables.all.size, SyncTables.all.map { it.name }.toSet().size)
    }

    @Test
    fun theKeysMatchTheRealPrimaryKeys() {
        entitiesInSource().forEach { f ->
            val t = SyncTables.byName(f.name)
            assertNotNull(f.name, t)
            assertEquals("keys of ${f.name}", f.keys, t!!.keys)
        }
    }

    @Test
    fun theDatabaseListHasTheSameNumberOfTablesAsTheEntitiesDeclared() {
        val dir = listOf(File("src/main/java/com/lifetracker/app/data/Database.kt"), File("app/src/main/java/com/lifetracker/app/data/Database.kt")).first { it.isFile }
        val listed = Regex("""(\w+Entity)::class""").findAll(dir.readText().substringAfter("entities = [").substringBefore("],")).count()
        assertEquals(listed, SyncTables.all.size)
    }

    @Test
    fun onlyBookkeepingAndPerPhoneFilesAreLeftOut() {
        assertEquals(listOf("imported_items", "map_areas", "sync_outbox"), SyncTables.all.filter { !it.synced }.map { it.name }.sorted())
        assertTrue(SyncTables.byName("raw_fixes")!!.synced)
        assertEquals("raw_fixes", SyncTables.syncedNames.last())
        assertEquals("trip_points", SyncTables.syncedNames[SyncTables.syncedNames.size - 2])
        assertFalse("imported_items" in SyncTables.syncedNames)
    }

    @Test
    fun compositeKeysJoinAndSplitBack() {
        val key = SyncTables.rowKey(listOf("h|1", "2026-11-01"))
        assertEquals(listOf("h|1", "2026-11-01"), SyncTables.keyParts(key))
        assertEquals("abc", SyncTables.rowKey(listOf("abc")))
        assertEquals(listOf("abc"), SyncTables.keyParts("abc"))
    }
}
