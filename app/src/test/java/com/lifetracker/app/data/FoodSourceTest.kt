package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSourceTest {
    private fun meal(id: String, source: String, foodId: String = "") =
        MealEntity(id, "2026-11-10", 780, "lunch", "Dal", 150.0, 100.0, 10.0, 2.0, 6.0, source, "", foodId = foodId)

    @Test
    fun aTagRemembersWhetherYouChangedTheNumbers() {
        assertEquals("photo", FoodSource.tag(FoodSource.PHOTO, false))
        assertEquals("photo:edited", FoodSource.tag(FoodSource.PHOTO, true))
        assertEquals("app", FoodSource.tag(FoodSource.APP, true))
        assertEquals("photo", FoodSource.kind("photo:edited"))
        assertTrue(FoodSource.edited("label:edited"))
        assertFalse(FoodSource.edited("label"))
    }

    @Test
    fun onlyAnUntouchedPhotoOrWordsGuessIsAGuess() {
        assertTrue(FoodSource.isGuess("photo"))
        assertTrue(FoodSource.isGuess("text"))
        assertFalse(FoodSource.isGuess("photo:edited"))
        assertFalse(FoodSource.isGuess("label"))
        assertFalse(FoodSource.isGuess("app"))
    }

    @Test
    fun labelsSayWhereTheNumbersCameFrom() {
        assertEquals("Photo guess by the AI", FoodSource.label("photo"))
        assertEquals("Photo guess, numbers changed by you", FoodSource.label("photo:edited"))
        assertEquals("AI guess from your words", FoodSource.label("text"))
        assertEquals("Read from the pack label", FoodSource.label("label"))
        assertEquals("Open Food Facts", FoodSource.label("barcode"))
        assertEquals("Imported from GymMane", FoodSource.label("gymmane"))
        assertEquals("Built-in food table", FoodSource.label("app", "a:banana"))
        assertEquals("One of your own foods", FoodSource.label("app", "m:123"))
        assertEquals("Typed by you", FoodSource.label("app", ""))
        assertEquals("Typed by you", FoodSource.label("something old", ""))
    }

    @Test
    fun aCopyKeepsAPhotoOrLabelTagButNotAnImport() {
        assertEquals("photo:edited", FoodSource.forCopy("photo:edited"))
        assertEquals("barcode", FoodSource.forCopy("barcode"))
        assertEquals("app", FoodSource.forCopy("gymmane"))
        assertEquals("app", FoodSource.forCopy("ont"))
    }

    @Test
    fun sourcesAreCountedMostCommonFirst() {
        val meals = listOf(meal("1", "photo"), meal("2", "app"), meal("3", "photo"), meal("4", "label"), meal("5", "app", "a:dal"))
        assertEquals(
            listOf("Photo guess by the AI" to 2, "Built-in food table" to 1, "Read from the pack label" to 1, "Typed by you" to 1),
            FoodSource.counts(meals),
        )
    }

    @Test
    fun aChosenModelIsKeptInTheTag() {
        assertEquals("photo@google/gemini-2.5-flash", FoodSource.tag(FoodSource.PHOTO, false, "google/gemini-2.5-flash"))
        assertEquals("text@meta/x:free:edited", FoodSource.tag(FoodSource.TEXT, true, " meta/x:free "))
        assertEquals("photo:edited", FoodSource.tag(FoodSource.PHOTO, true, " "))
        assertEquals("app", FoodSource.tag(FoodSource.APP, true, "a/b"))
        assertEquals("photo", FoodSource.kind("photo@google/gemini-2.5-flash:edited"))
        assertEquals("text", FoodSource.kind("text@meta/x:free"))
        assertEquals("meta/x:free", FoodSource.model("text@meta/x:free:edited"))
        assertEquals("a/b", FoodSource.model("label@a/b"))
        assertEquals(null, FoodSource.model("photo:edited"))
        assertTrue(FoodSource.edited("text@meta/x:free:edited"))
        assertFalse(FoodSource.edited("text@meta/x:free"))
        assertTrue(FoodSource.isGuess("photo@a/b"))
        assertFalse(FoodSource.isGuess("photo@a/b:edited"))
    }

    @Test
    fun theLabelNamesTheChosenModelAndACopyKeepsIt() {
        assertEquals("Photo guess by the AI (a/b)", FoodSource.label("photo@a/b"))
        assertEquals("Photo guess, numbers changed by you (a/b)", FoodSource.label("photo@a/b:edited"))
        assertEquals("Read from the pack label (a/b)", FoodSource.label("label@a/b"))
        assertEquals("photo@a/b", FoodSource.forCopy("photo@a/b"))
    }
}
