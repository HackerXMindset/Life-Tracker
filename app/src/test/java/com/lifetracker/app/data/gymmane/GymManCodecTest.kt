package com.lifetracker.app.data.gymmane

import com.lifetracker.app.data.FastEntity
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.SupplementEntity
import com.lifetracker.app.data.SupplementTakenEntity
import com.lifetracker.app.data.WeightEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymManCodecTest {
    private val sample = """
    {
      "bodyweight": [{"d": "2026-11-05T07:10:00.000", "kg": 71.5}],
      "nutrition": {
        "log": [
          {"id": "e1", "at": "2026-11-05T08:30:00.000", "ml": "breakfast", "n": "Oats", "fd": "a:oats", "x": 2, "g": 40,
           "m": {"k": 150, "p": 5, "c": 27, "f": 3, "fi": 4, "na": 2}},
          {"id": "e2", "at": "2026-11-05T21:00:00.000", "ml": "snacks", "n": "Mystery bar", "m": {"k": 200, "p": 10}},
          {"id": "kx-mine", "at": "2026-11-05T10:00:00.000", "ml": "lunch", "n": "Sent from Kairos", "g": 100, "m": {"k": 50}}
        ],
        "water": [{"at": "2026-11-05T09:00:00.000", "ml": 250}, {"at": "2026-11-05T15:00:00.000", "ml": 500}],
        "supps": [{"id": "s1", "n": "D3", "d": "1 tab", "at": 480, "w": 42}],
        "taken": {"2026-11-05": ["s1", "kx-gone"]},
        "fast": {"h": 16, "log": [{"s": "2026-11-04T20:00:00.000", "e": "2026-11-05T12:30:00.000", "g": 16}]}
      }
    }
    """.trimIndent()

    @Test
    fun aGymManServingBecomesPer100GramsAndLogsTheWholeAmount() {
        val data = GymManCodec.parse(sample) { it == "a:oats" }
        val oats = data.meals.first { it.id == "gm-e1" }
        // 2 servings of 40 g each: 80 g eaten, 150 kcal per 40 g = 375 per 100 g.
        assertEquals(80.0, oats.grams, 1e-9)
        assertEquals(375.0, oats.kcal100, 1e-9)
        assertEquals(300.0, oats.kcal, 1e-9)
        assertEquals(12.5, oats.protein100, 1e-9)
        assertEquals(10.0, oats.fiber100, 1e-9)
        assertEquals("breakfast", oats.mealType)
        assertEquals(8 * 60 + 30, oats.minuteOfDay)
        assertEquals("a:oats", oats.foodId)
        assertEquals("gymmane", oats.source)
    }

    @Test
    fun anEntryWithNoWeightIsTakenAsOneHundredGramsOfTheTotal() {
        val bar = GymManCodec.parse(sample).meals.first { it.id == "gm-e2" }
        assertEquals(100.0, bar.grams, 1e-9)
        assertEquals(200.0, bar.kcal, 1e-9)
        assertEquals("snack", bar.mealType)
        assertEquals("", bar.foodId)
    }

    @Test
    fun anUnknownLibraryFoodKeepsNoReference() {
        val oats = GymManCodec.parse(sample) { false }.meals.first { it.id == "gm-e1" }
        assertEquals("", oats.foodId)
    }

    @Test
    fun whatKairosSentIsNeverTakenBack() {
        val data = GymManCodec.parse(sample)
        assertTrue(data.meals.none { it.id.contains("kx-") })
        assertTrue(data.taken.none { it.supplementId.contains("kx-") })
    }

    @Test
    fun waterSupplementsFastsAndWeightsAreRead() {
        val data = GymManCodec.parse(sample)
        assertEquals(mapOf("2026-11-05" to 750), data.water)
        val d3 = data.supplements.single()
        assertEquals("gm-s1", d3.id)
        assertEquals(480, d3.atMinute)
        assertEquals(42, d3.weekdays)
        assertEquals(listOf(SupplementTakenEntity("gm-s1", "2026-11-05", 480)), data.taken)
        val fast = data.fasts.single()
        assertEquals(16, fast.goalHours)
        assertEquals(16.5 * 3_600_000, (fast.endMs - fast.startMs).toDouble(), 1.0)
        assertEquals(listOf(WeightEntity("2026-11-05", 71.5)), data.weights)
    }

    @Test
    fun aBackupWithNoFoodPartReadsAsEmpty() {
        assertTrue(GymManCodec.parse("""{"sessions": []}""").isEmpty)
    }

    private fun mine() = GymManOutgoing(
        meals = listOf(
            MealEntity("m1", "2026-11-06", 13 * 60 + 5, "snack", "Apple", 150.0, 52.0, 14.0, 0.2, 0.3, "app", "", sodium100 = 1.0),
            MealEntity("gm-e1", "2026-11-05", 510, "breakfast", "Oats", 80.0, 375.0, 0.0, 0.0, 0.0, "gymmane", ""),
        ),
        water = mapOf("2026-11-05" to 1000, "2026-11-06" to 300),
        supplements = listOf(SupplementEntity("gm-s1", "D3"), SupplementEntity("k1", "Zinc", dose = "1", atMinute = 600, weekdays = 6)),
        taken = listOf(SupplementTakenEntity("k1", "2026-11-06", 600), SupplementTakenEntity("gm-s1", "2026-11-06", 480)),
        fasts = listOf(FastEntity("f1", 1_000_000_000_000L, 1_000_000_000_000L + 18 * 3_600_000L, 16)),
        weights = listOf(WeightEntity("2026-11-05", 99.0), WeightEntity("2026-11-06", 71.0)),
    )

    @Test
    fun mergeAddsOnlyWhatIsMissingAndChangesNothingThatIsThere() {
        val merged = JSONObject(GymManCodec.merge(sample, mine()))
        val nutrition = merged.getJSONObject("nutrition")
        val log = nutrition.getJSONArray("log")
        val ids = (0 until log.length()).map { log.getJSONObject(it).getString("id") }
        assertEquals(listOf("e1", "e2", "kx-mine", "kx-m1"), ids)
        val apple = log.getJSONObject(3)
        assertEquals("snacks", apple.getString("ml"))
        assertEquals(150.0, apple.getDouble("g"), 1e-9)
        assertEquals(78.0, apple.getJSONObject("m").getDouble("k"), 1e-9)
        assertEquals("2026-11-06T13:05:00.000", apple.getString("at"))

        // Water: 2026-11-05 had 750, Kairos has 1000, so a 250 sip is added; the new day gets its 300.
        val water = nutrition.getJSONArray("water")
        assertEquals(4, water.length())
        assertEquals(250, water.getJSONObject(2).getInt("ml"))
        assertEquals(300, water.getJSONObject(3).getInt("ml"))

        // Supplements: D3 already there, Zinc added under a kx- id; ticks map to their ids.
        val supps = nutrition.getJSONArray("supps")
        assertEquals(2, supps.length())
        assertEquals("kx-k1", supps.getJSONObject(1).getString("id"))
        assertEquals(600, supps.getJSONObject(1).getInt("at"))
        val taken = nutrition.getJSONObject("taken")
        assertEquals(2, taken.getJSONArray("2026-11-06").length())
        assertEquals("s1", taken.getJSONArray("2026-11-06").getString(1))
        assertEquals(2, taken.getJSONArray("2026-11-05").length())

        // Fast added once; weight added only for the day with none.
        assertEquals(2, nutrition.getJSONObject("fast").getJSONArray("log").length())
        val bw = merged.getJSONArray("bodyweight")
        assertEquals(2, bw.length())
        assertEquals(71.5, bw.getJSONObject(0).getDouble("kg"), 1e-9)
        assertEquals(71.0, bw.getJSONObject(1).getDouble("kg"), 1e-9)
    }

    @Test
    fun mergingTwiceChangesNothingTheSecondTime() {
        val once = GymManCodec.merge(sample, mine())
        val twice = GymManCodec.merge(once, mine())
        assertEquals(JSONObject(once).toString(), JSONObject(twice).toString())
    }

    @Test
    fun aSentCopyReadsBackWithoutDoublingAnything() {
        val back = GymManCodec.parse(GymManCodec.merge(sample, mine()))
        assertFalse(back.meals.any { it.name == "Apple" })
        assertEquals(750 + 250 + 300, back.water.values.sum())
    }

    @Test
    fun anEmptyOutgoingHasNothingToSend() {
        assertFalse(GymManCodec.hasAnything(GymManOutgoing(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), emptyList())))
    }

    @Test
    fun aKairosOnlyFileHoldsOurDataAndReadsBack() {
        val file = JSONObject(GymManCodec.standalone(mine()))
        val log = file.getJSONObject("nutrition").getJSONArray("log")
        assertEquals(listOf("kx-mine", "kx-m1"), (0 until log.length()).map { log.getJSONObject(it).getString("id") })
        assertEquals(2, file.getJSONArray("bodyweight").length())
        assertEquals(16, file.getJSONObject("nutrition").getJSONObject("fast").getInt("h"))
        val back = GymManCodec.parse(file.toString())
        assertFalse(back.meals.any { it.name == "Apple" })
    }
}
