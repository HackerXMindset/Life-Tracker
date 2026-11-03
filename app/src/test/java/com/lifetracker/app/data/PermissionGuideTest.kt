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

    @Test fun notAllowedComeFirstAndEachKeepsTheGuideOrder() {
        val allowed = setOf(Perm.Location, Perm.Notifications, Perm.Folders)
        val order = PermissionGuide.ordered { it in allowed }
        assertEquals(Perm.values().size, order.size)
        val firstOn = order.indexOfFirst { it in allowed }
        assertTrue(order.take(firstOn).none { it in allowed })
        assertTrue(order.drop(firstOn).all { it in allowed })
        assertEquals(order.filter { it !in allowed }, Perm.values().filter { it !in allowed })
        assertEquals(order.filter { it in allowed }, Perm.values().filter { it in allowed })
    }

    @Test fun everyCardSaysWhatItIsUsedForAndWhereItIs() {
        PermissionGuide.all.forEach {
            assertTrue(it.perm.name, it.usedBy.isNotBlank() && it.page.isNotBlank())
        }
    }

    @Test fun runtimePermissionsTryThePhonesOwnEditorFirstOnXiaomiThenTheNamedPage() {
        assertEquals(
            listOf(PageStep.MiuiEditor, PageStep.AppPermission, PageStep.AppDetails),
            PermissionPages.chain(Perm.Background, xiaomi = true),
        )
        assertEquals(listOf(PageStep.AppPermission, PageStep.AppDetails), PermissionPages.chain(Perm.Location, xiaomi = false))
        assertEquals(listOf(PageStep.UsageAccess, PageStep.AppDetails), PermissionPages.chain(Perm.UsageAccess, xiaomi = true))
        assertTrue(PermissionPages.chain(Perm.Folders, xiaomi = true).isEmpty())
        assertTrue(PermissionPages.androidName(Perm.Background)!!.endsWith("ACCESS_BACKGROUND_LOCATION"))
        assertTrue(PermissionPages.isXiaomi("Xiaomi") && PermissionPages.isXiaomi("POCO") && !PermissionPages.isXiaomi("samsung"))
    }
}
