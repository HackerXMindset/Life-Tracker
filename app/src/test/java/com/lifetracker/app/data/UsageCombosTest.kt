package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCombosTest {
    private val min = 60_000L

    private fun example(name: String) = UsageCombos.EXAMPLES.first { it.name == name }

    private fun type(id: String, name: String, archived: Boolean = false) = ActivityTypeEntity(id, name, 0xFF000000, 0, 0, archived, 0)

    @Test
    fun physicsWallahAndChatGptAreStudy() {
        val found = UsageCombos.matchExample(example("Study"), listOf("a" to "PW", "b" to "ChatGPT", "c" to "Clock"))
        assertEquals(setOf("a", "b"), found)
        val long = UsageCombos.matchExample(example("Study"), listOf("a" to "Physics Wallah", "b" to "ChatGPT"))
        assertEquals(setOf("a", "b"), long)
    }

    @Test
    fun claudeAndBraveAreBuildingAndGitHubJoinsWhenPresent() {
        val two = UsageCombos.matchExample(example("Building"), listOf("a" to "Claude", "b" to "Brave"))
        assertEquals(setOf("a", "b"), two)
        val three = UsageCombos.matchExample(example("Building"), listOf("a" to "Claude", "b" to "Brave", "c" to "GitHub"))
        assertEquals(setOf("a", "b", "c"), three)
    }

    @Test
    fun anExampleNeedsEveryRequiredPart() {
        assertNull(UsageCombos.matchExample(example("Study"), listOf("a" to "ChatGPT", "b" to "Clock")))
        assertNull(UsageCombos.matchExample(example("Building"), listOf("a" to "Claude")))
    }

    @Test
    fun aWordIsNotFoundInsideAnotherWord() {
        assertTrue(UsageCombos.keywordMatches("word", "x", "Microsoft Word"))
        assertTrue(!UsageCombos.keywordMatches("word", "x", "Wordle"))
        assertTrue(!UsageCombos.keywordMatches("ola", "x", "Pinterest Olaf"))
        assertTrue(UsageCombos.keywordMatches("com.openai.chatgpt", "com.openai.chatgpt", "Anything"))
    }

    @Test
    fun aSharedWordCannotBlockAMatch() {
        // "Amazon" is a shopping app and "Amazon Pay" is a payment app.
        val found = UsageCombos.matchExample(example("Shopping"), listOf("p" to "Amazon Pay", "s" to "Amazon"))
        assertEquals(setOf("p", "s"), found)
    }

    @Test
    fun chattingNeedsTwoChatApps() {
        assertNull(UsageCombos.matchExample(example("Chatting"), listOf("a" to "WhatsApp", "b" to "Clock")))
        assertNotNull(UsageCombos.matchExample(example("Chatting"), listOf("a" to "WhatsApp", "b" to "Telegram")))
    }

    @Test
    fun theSuggestedActivityIsYoursByName() {
        val types = listOf(type("s1", "study"), type("w", "Work"))
        assertEquals("s1", UsageCombos.activityFor(example("Study"), types))
        assertEquals("w", UsageCombos.activityFor(example("Building"), types))
        assertEquals("", UsageCombos.activityFor(example("Travel"), types))
        assertEquals("", UsageCombos.activityFor(example("Study"), listOf(type("s1", "Study", archived = true))))
    }

    private fun dayWith(vararg groups: List<Pair<String, String>>): UsageDays.Day {
        var t = 0L
        val blocks = groups.map { apps ->
            val uses = apps.map { (pkg, label) -> UsageDays.AppUse(pkg, label, "", 10 * min, 1) }
            val b = UsageDays.Block(apps.first().first, uses.joinToString(" + ") { it.label }, "Screen", t, t + 20 * min, 20 * min, apps = uses)
            t += 60 * min
            b
        }
        return UsageDays.Day(blocks, 0L, emptyList(), emptyMap())
    }

    @Test
    fun aBuiltInExampleIsSuggestedAtOnce() {
        val day = dayWith(listOf("pw" to "Physics Wallah", "gpt" to "ChatGPT"))
        val s = UsageCombos.suggest(listOf(day), emptyList(), listOf(type("st", "Study")), emptySet())
        assertEquals(1, s.size)
        assertEquals("Study", s[0].name)
        assertEquals("st", s[0].activityId)
        assertEquals(setOf("pw", "gpt"), s[0].pkgs)
    }

    @Test
    fun theSameAppsThreeTimesOverTwoDaysAreSuggested() {
        val together = listOf("a" to "Alpha", "b" to "Beta")
        val d1 = dayWith(together, together)
        val d2 = dayWith(together)
        val s = UsageCombos.suggest(listOf(d1, d2), emptyList(), emptyList(), emptySet())
        assertEquals(1, s.size)
        assertEquals("Alpha + Beta", s[0].name)
        assertEquals(3, s[0].times)
        assertEquals(2, s[0].days)
    }

    @Test
    fun threeTimesOnOneDayIsNotEnough() {
        val together = listOf("a" to "Alpha", "b" to "Beta")
        val s = UsageCombos.suggest(listOf(dayWith(together, together, together)), emptyList(), emptyList(), emptySet())
        assertTrue(s.isEmpty())
    }

    @Test
    fun namedAndDismissedSetsAreLeftOut() {
        val day = dayWith(listOf("pw" to "Physics Wallah", "gpt" to "ChatGPT"))
        val named = listOf(UsageComboEntity("c1", "Study", "gpt,pw", "st", 0))
        assertTrue(UsageCombos.suggest(listOf(day), named, emptyList(), emptySet()).isEmpty())
        assertTrue(UsageCombos.suggest(listOf(day), emptyList(), emptyList(), setOf(UsageCombos.keyOf(listOf("pw", "gpt")))).isEmpty())
    }

    @Test
    fun anAppUsedOnTheSideIsNotPartOfTheSet() {
        val uses = listOf(
            UsageDays.AppUse("a", "Alpha", "", 10 * min, 1),
            UsageDays.AppUse("b", "Beta", "", 10 * min, 1),
            UsageDays.AppUse("c", "Gamma", "", 1 * min, 1),
        )
        val block = UsageDays.Block("a", "x", "Screen", 0, 21 * min, 21 * min, apps = uses)
        assertEquals(listOf("a", "b"), UsageCombos.mainApps(block).map { it.pkg })
    }
}
