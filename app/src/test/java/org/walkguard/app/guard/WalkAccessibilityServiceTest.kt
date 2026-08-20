package org.walkguard.app.guard

import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildService
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class WalkAccessibilityServiceTest {
    @Test
    fun interruptClearsForegroundPackage() {
        ForegroundAppTracker.updateCurrentPackage("com.example.app")
        val service = buildService(WalkAccessibilityService::class.java).get()

        service.onInterrupt()

        assertNull(ForegroundAppTracker.currentPackageFlow.value)
    }
}
