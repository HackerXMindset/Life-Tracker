package com.lifetracker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionGuideTest {
    @Test fun everyPermissionHasOneCardInOrder() {
        assertEquals(Perm.values().toList(), PermissionGuide.all.map { it.perm })
        PermissionGuide.all.forEach {
            assertTrue(it.title.isNotBlank() && it.why.isNotBlank() && it.how.isNotBlank())
        }
    }

    @Test fun restrictedGuideDependsOnAndroidVersion() {
        assertTrue(PermissionGuide.restrictedSteps(37).first().contains("three dots"))
        assertTrue(PermissionGuide.restrictedSteps(35).first().contains("very bottom"))
        assertEquals(3, PermissionGuide.restrictedSteps(35).size)
    }

    @Test fun summaryWords() {
        assertEquals("All allowed", PermissionGuide.summary(10, 10))
        assertEquals("3 still off", PermissionGuide.summary(7, 10))
        assertEquals("", PermissionGuide.summary(0, 0))
    }
}
