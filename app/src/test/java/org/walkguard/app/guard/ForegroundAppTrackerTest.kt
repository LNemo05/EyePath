package org.walkguard.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppTrackerTest {
    @Test
    fun nonBlankPackageNameIsPublished() {
        ForegroundAppTracker.clear()

        ForegroundAppTracker.updateCurrentPackage("com.example.app")

        assertEquals("com.example.app", ForegroundAppTracker.currentPackageFlow.value)
    }

    @Test
    fun blankPackageNameDoesNotOverwriteCurrentPackage() {
        ForegroundAppTracker.clear()
        ForegroundAppTracker.updateCurrentPackage("com.example.app")

        ForegroundAppTracker.updateCurrentPackage("   ")

        assertEquals("com.example.app", ForegroundAppTracker.currentPackageFlow.value)
    }

    @Test
    fun nullPackageNameDoesNotOverwriteCurrentPackage() {
        ForegroundAppTracker.clear()
        ForegroundAppTracker.updateCurrentPackage("com.example.app")

        ForegroundAppTracker.updateCurrentPackage(null)

        assertEquals("com.example.app", ForegroundAppTracker.currentPackageFlow.value)
    }

    @Test
    fun clearRemovesCurrentPackage() {
        ForegroundAppTracker.updateCurrentPackage("com.example.app")

        ForegroundAppTracker.clear()

        assertNull(ForegroundAppTracker.currentPackageFlow.value)
    }
}
