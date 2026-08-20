package org.walkguard.app.core.engine

class ScreenCycleTracker {
    var normalDismissed: Boolean = false
        private set

    var mildSent: Boolean = false
        private set

    fun onScreenOn() = reset()

    fun onScreenOff() = reset()

    fun markNormalDismissed() {
        normalDismissed = true
    }

    fun markMildSent() {
        mildSent = true
    }

    private fun reset() {
        normalDismissed = false
        mildSent = false
    }
}
