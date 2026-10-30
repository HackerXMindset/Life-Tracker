package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {
    private val allOn: (Feature) -> Boolean = { true }

    @Test fun everyTabIsShownWhenAllAreOn() {
        assertEquals(listOf("Timeline", "Food", "Habits", "Money", "Stats", "Settings"), FeatureRules.tabNames(allOn))
    }

    @Test fun aSwitchedOffFeatureLeavesTheTabsButTimelineAndSettingsStay() {
        val off = setOf(Feature.FOOD, Feature.MONEY)
        assertEquals(listOf("Timeline", "Habits", "Stats", "Settings"), FeatureRules.tabNames { it !in off })
        assertEquals(listOf("Timeline", "Settings"), FeatureRules.tabNames { false })
    }

    @Test fun featuresWithoutATabDoNotChangeTheTabs() {
        assertEquals(FeatureRules.tabNames(allOn), FeatureRules.tabNames { it != Feature.CALLS && it != Feature.PLACES })
    }

    @Test fun youLandOnTheTimelineWhenYourTabIsGone() {
        val visible = listOf("Timeline", "Habits", "Settings")
        assertEquals("Habits", FeatureRules.landing("Habits", visible))
        assertEquals("Timeline", FeatureRules.landing("Money", visible))
    }

    @Test fun theSummaryNamesWhatIsOff() {
        assertEquals("All on", FeatureRules.summary(allOn))
        assertEquals("Food is off", FeatureRules.summary { it != Feature.FOOD })
        assertEquals("Food, Calls are off", FeatureRules.summary { it != Feature.FOOD && it != Feature.CALLS })
    }

    @Test fun idsAreUniqueSoSavedSwitchesNeverCollide() {
        val ids = Feature.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Feature.entries.all { it.title.isNotBlank() && it.hint.isNotBlank() })
    }
}
