package com.lifetracker.app.data.ont

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OntImportTest {
    // Shaped like a real OpenNutriTracker export, with invented content.
    private val intake = """
    [
      {"id":"i1","unit":"serving","amount":100.0,"type":"breakfast",
       "meal":{"code":"c1","name":"Chole kulche ","mealQuantity":"100","mealUnit":"g/ml","servingQuantity":100.0,
               "nutriments":{"energyKcal100":650.0,"carbohydrates100":100.0,"fat100":18.0,"proteins100":21.0,"sugars100":null}},
       "dateTime":"2026-10-04T12:32:34.017"},
      {"id":"i2","unit":"serving","amount":400.0,"type":"dinner",
       "meal":{"code":"c2","name":"Roti (1 medium)","nutriments":{"energyKcal100":106.0,"carbohydrates100":null,"fat100":null,"proteins100":null}},
       "dateTime":"2026-10-04T21:10:00.000"},
      {"id":"i3","unit":"g","amount":100.0,"type":"brunch",
       "meal":{"code":"c3","name":"Paneer Paratha","nutriments":{"energyKcal100":320.0,"carbohydrates100":40.0,"fat100":14.0,"proteins100":9.0}},
       "dateTime":"2026-10-05T19:45:00.000"},
      {"id":"bad","unit":"g","amount":1.0,"type":"lunch"}
    ]
    """.trimIndent()

    private val trackedDay = """
    [
      {"day":"2026-10-04T12:32:34.017","calorieGoal":2672.88,"carbsGoal":399.474,"fatGoal":73.97,"proteinGoal":99.87,"caloriesTracked":2519.0},
      {"day":"2026-10-06T00:00:00.000Z","calorieGoal":2609.82,"carbsGoal":391.47,"fatGoal":72.49,"proteinGoal":97.86,"caloriesTracked":1243.0}
    ]
    """.trimIndent()

    private val activity = """
    [
      {"id":"a1","duration":0.0,"burnedKcal":0.0,"date":"2026-10-04T16:58:50.257",
       "physicalActivityDBO":{"code":"99999","specificActivity":"custom","description":"user-entered kcal","mets":0.0,"tags":[],"type":"conditioningExercise"},
       "userKcal":0.0},
      {"id":"a2","duration":7.5,"burnedKcal":32.8125,"date":"2026-10-04T21:48:34.312",
       "physicalActivityDBO":{"code":"17160","specificActivity":"walking","description":"for pleasure","mets":3.5,"tags":[],"type":"sport"},
       "userKcal":null},
      {"id":"a3","duration":0.0,"burnedKcal":0.0,"date":"2026-10-05T08:00:00.000",
       "physicalActivityDBO":{"code":"99999","specificActivity":"stool","description":"user-entered kcal","mets":0.0,"tags":[],"type":"conditioningExercise"},
       "userKcal":0.0}
    ]
    """.trimIndent()

    private val data = OntParser.parse(intake, trackedDay, activity)

    @Test
    fun readsMealsAndSkipsBrokenRecords() {
        assertEquals(listOf("i1", "i2", "i3"), data.intakes.map { it.id })
        assertEquals("Chole kulche", data.intakes[0].name)
        assertEquals(650.0, data.intakes[0].kcal100, 0.0)
        // Missing nutrient values count as zero.
        assertEquals(0.0, data.intakes[1].carbs100, 0.0)
        // An unknown meal type falls back to snack.
        assertEquals("snack", data.intakes[2].type)
    }

    @Test
    fun amountIsAlwaysGramsWhateverTheUnitSays() {
        val meals = data.intakes.map(OntConverter::meal)
        assertEquals(650.0, meals[0].kcal, 1e-9)
        assertEquals(424.0, meals[1].kcal, 1e-9) // 400 "serving" of 106 kcal per 100
        assertEquals("2026-10-04", meals[0].date)
        assertEquals(12 * 60 + 32, meals[0].minuteOfDay)
        assertEquals("ont", meals[0].source)
    }

    @Test
    fun goalsKeepOnlyTheDay() {
        assertEquals(LocalDate.of(2026, 10, 4), data.goals[0].date)
        assertEquals(LocalDate.of(2026, 10, 6), data.goals[1].date)
        assertEquals(2609.82, data.goals[1].kcal, 1e-9)
    }

    @Test
    fun activitiesBecomeTimelineEntries() {
        val specs = data.activities.map(OntConverter::activity)

        val custom = specs[0]
        assertEquals("Custom activity", custom.title)
        assertEquals("Health", custom.category)
        assertNull(custom.endMinute)

        val walk = specs[1]
        assertEquals("Walking", walk.title)
        assertEquals("Exercise", walk.category)
        assertEquals(21 * 60 + 48, walk.startMinute)
        assertEquals(21 * 60 + 48 + 8, walk.endMinute) // 7.5 minutes rounds to 8
        assertTrue(walk.note.contains("33 kcal"))

        val stool = specs[2]
        assertEquals("Stool", stool.title) // a custom activity keeps the name you gave it
        assertEquals("Health", stool.category)
        assertEquals(LocalDate.of(2026, 10, 5), stool.date)
    }

    @Test
    fun missingFilesAreFineButNothingAtAllIsRefused() {
        val onlyMeals = OntParser.parse(intake, null, null)
        assertEquals(3, onlyMeals.intakes.size)
        assertTrue(onlyMeals.goals.isEmpty())
        try {
            OntParser.parse(null, null, null)
            fail("should refuse")
        } catch (_: IllegalArgumentException) {
        }
        try {
            OntParser.parse("not json", null, null)
            fail("should refuse")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun anEmptyExportHasNothingToImport() {
        assertTrue(OntParser.parse("[]", "[]", "[]").isEmpty)
    }
}
