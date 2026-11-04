package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleGuideTest {
    @Test fun everyTriggerIsInExactlyOneGroup() {
        val grouped = RuleGuide.GROUPS.flatMap { it.kinds }
        assertEquals(RuleKinds.TRIGGERS.map { it.first }.sorted(), grouped.sorted())
        assertEquals(grouped.size, grouped.toSet().size)
    }

    @Test fun groupOfFindsTheGroupAndFallsBackToTheFirst() {
        assertEquals("places", RuleGuide.groupOf(RuleKinds.LEAVE).id)
        assertEquals("phone", RuleGuide.groupOf(RuleKinds.WIFI_ON).id)
        assertEquals(RuleGuide.GROUPS.first().id, RuleGuide.groupOf("nothing").id)
    }

    @Test fun everyTriggerAndActionHasAPlainLine() {
        RuleKinds.TRIGGERS.forEach { assertTrue(it.first, RuleGuide.triggerHint(it.first).isNotBlank()) }
        RuleKinds.ACTIONS.forEach { assertTrue(it.first, RuleGuide.actionHint(it.first).isNotBlank()) }
        RuleKinds.TRIGGERS.forEach { assertTrue(RuleGuide.triggerWords(it.first).isNotBlank()) }
    }

    @Test fun templatesUseKnownKindsAndAreNamed() {
        val triggers = RuleKinds.TRIGGERS.map { it.first }.toSet()
        val actions = RuleKinds.ACTIONS.map { it.first }.toSet()
        RuleGuide.TEMPLATES.forEach { t ->
            assertTrue(t.id, t.title.isNotBlank() && t.hint.isNotBlank())
            assertTrue(t.id, t.trigger.kind in triggers)
            t.actions.forEach { assertTrue(t.id, it.kind in actions) }
        }
        assertEquals(RuleGuide.TEMPLATES.size, RuleGuide.TEMPLATES.map { it.id }.toSet().size)
    }

    @Test fun threeStepsEachWithHelp() {
        assertEquals(listOf("When", "Only if", "Then"), RuleGuide.STEPS)
        RuleGuide.STEPS.indices.forEach { assertTrue(RuleGuide.stepHelp(it).isNotBlank()) }
    }
}
