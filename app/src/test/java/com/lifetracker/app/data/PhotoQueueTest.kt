package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoQueueTest {
    @Test
    fun aQueuedPhotoSurvivesBeingWrittenAndReadBack() {
        val photo = QueuedPhoto("abc-123", label = true, meal = "dinner", date = "2026-11-09", minute = 1215, note = "Amul brand")
        assertEquals(photo, QueuedPhoto.fromJson(photo.toJson()))
    }

    @Test
    fun brokenOrIncompleteQueueFilesAreIgnored() {
        assertNull(QueuedPhoto.fromJson("not json"))
        assertNull(QueuedPhoto.fromJson("""{"id":"x"}"""))
        assertNotNull(QueuedPhoto.fromJson("""{"id":"x","date":"2026-11-09"}"""))
    }

    @Test
    fun aQueueIdCannotEscapeItsFolder() {
        assertEquals("ab", QueuedPhoto.fromJson("""{"id":"a/../b","date":"2026-11-09"}""")!!.id)
    }

    @Test
    fun aCorrectionIsRememberedOncePerWrongNameAndTheOldestGoFirst() {
        var list = emptyList<Pair<String, String>>()
        list = FoodAiMemory.merge(list, "Dal", "Rajma")
        list = FoodAiMemory.merge(list, "dal", "Chana dal") // same wrong name: newest wins
        assertEquals(listOf("dal" to "Chana dal"), list)
        assertEquals(list, FoodAiMemory.merge(list, "Roti", " roti ")) // only a different case or spaces: nothing learned
        assertEquals(list, FoodAiMemory.merge(list, "", "x"))
        repeat(30) { list = FoodAiMemory.merge(list, "wrong $it", "right $it") }
        assertEquals(FoodAiMemory.MAX, list.size)
        assertEquals("wrong 29" to "right 29", list.last())
        assertFalse(list.any { it.first == "dal" })
    }

    @Test
    fun correctionsAreKeptAsTextAndReadBack() {
        val list = listOf("dal" to "rajma", "chai" to "masala chai")
        assertEquals(list, FoodAiMemory.decode(FoodAiMemory.encode(list)))
        assertTrue(FoodAiMemory.decode("garbage").isEmpty())
        assertTrue(FoodAiMemory.decode(null).isEmpty())
        assertTrue(SettingsBackup.included(FoodAiMemory.FILE))
    }

    @Test
    fun thePromptsCarryTheCorrectionsOnlyWhenThereAreSome() {
        val fixes = listOf("dal" to "rajma")
        assertTrue(FoodPhotoAi.prompt("", FoodFields.FIXED, fixes).contains("\"dal\" was really \"rajma\""))
        assertTrue(FoodPhotoAi.textPrompt("dal rice", "", FoodFields.FIXED, fixes).contains("\"dal\" was really \"rajma\""))
        assertFalse(FoodPhotoAi.prompt("").contains("was really"))
    }

    @Test
    fun theOtherModelIsTriedWhenTheFirstRunsOut() {
        assertEquals(listOf("a", "b"), FoodPhotoAi.modelOrder("a", "b"))
        assertEquals(listOf("a"), FoodPhotoAi.modelOrder("a", " a "))
        assertEquals(listOf("b"), FoodPhotoAi.modelOrder("", "b"))
    }

    @Test
    fun onlyAMissingConnectionIsMarkedOffline() {
        assertFalse(PhotoAiException("Google did not accept the key.").offline)
        assertTrue(PhotoAiException("Could not reach Google.", offline = true).offline)
    }
}
