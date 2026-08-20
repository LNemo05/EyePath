package org.walkguard.app.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCycleTrackerTest {
    @Test fun flagsResetWhenScreenTurnsOn() {
        val tracker = ScreenCycleTracker()
        tracker.markNormalDismissed()
        tracker.markMildSent()
        assertTrue(tracker.normalDismissed)
        assertTrue(tracker.mildSent)

        tracker.onScreenOn()

        assertFalse(tracker.normalDismissed)
        assertFalse(tracker.mildSent)
    }

    @Test fun screenOffAlsoClearsCurrentCycleState() {
        val tracker = ScreenCycleTracker()
        tracker.markNormalDismissed()
        tracker.markMildSent()

        tracker.onScreenOff()

        assertFalse(tracker.normalDismissed)
        assertFalse(tracker.mildSent)
    }
}
