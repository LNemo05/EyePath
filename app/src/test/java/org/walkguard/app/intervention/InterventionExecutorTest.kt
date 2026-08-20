package org.walkguard.app.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.walkguard.app.core.engine.GuardDecision
import org.walkguard.app.data.settings.GuardSettings

class InterventionExecutorTest {
    private val mildNotifier = RecordingMildNotifier()
    private val warningPresenter = RecordingWarningPresenter()
    private val vibrator = RecordingVibrator()
    private val rageLockController = RecordingRageLockController()
    private val executor = InterventionExecutor(
        mildNotifier = mildNotifier,
        warningPresenter = warningPresenter,
        vibrator = vibrator,
        rageLockController = rageLockController
    )

    @Test
    fun noneDecisionDoesNothing() {
        val result = executor.execute(
            decision = GuardDecision.None,
            context = context()
        )

        assertEquals(InterventionResult.None, result)
        assertEquals(0, mildNotifier.sentCount)
        assertFalse(warningPresenter.warningShown)
        assertEquals(0, vibrator.vibrationCount)
        assertEquals(0, rageLockController.lockNowCount)
    }

    @Test
    fun mildDecisionSendsHeadsUpNotificationOnlyWithoutOverlay() {
        val result = executor.execute(
            decision = GuardDecision.SendMildNotification,
            context = context()
        )

        assertEquals(InterventionResult.MildSent, result)
        assertEquals(1, mildNotifier.sentCount)
        assertFalse(warningPresenter.warningShown)
        assertEquals(0, vibrator.vibrationCount)
        assertEquals(0, rageLockController.lockNowCount)
    }

    @Test
    fun normalDecisionShowsWarningWithSettingsCopyAndVibratesWithoutDismissing() {
        var dismissed = false
        val settings = GuardSettings.Default.copy(
            warningTitle = "Stop walking",
            warningMessage = "Look up first"
        )

        val result = executor.execute(
            decision = GuardDecision.ShowNormalWarning,
            context = context(settings = settings, onDismissForCurrentScreenCycle = { dismissed = true })
        )

        assertEquals(InterventionResult.NormalShown, result)
        assertEquals("Stop walking", warningPresenter.lastTitle)
        assertEquals("Look up first", warningPresenter.lastMessage)
        assertEquals(1, vibrator.vibrationCount)
        assertFalse(dismissed)

        warningPresenter.dismissCurrentWarning()

        assertTrue(dismissed)
    }

    @Test
    fun normalDecisionFallsBackToNotificationWhenOverlayCannotShow() {
        warningPresenter.showSucceeds = false

        val result = executor.execute(
            decision = GuardDecision.ShowNormalWarning,
            context = context()
        )

        assertEquals(InterventionResult.NormalFallbackNotificationSent, result)
        assertEquals(1, mildNotifier.sentCount)
        assertEquals(0, vibrator.vibrationCount)
    }

    @Test
    fun normalDecisionFailsSafelyWhenOverlayAndNotificationCannotShow() {
        warningPresenter.showSucceeds = false
        mildNotifier.sendSucceeds = false

        val result = executor.execute(
            decision = GuardDecision.ShowNormalWarning,
            context = context()
        )

        assertEquals(InterventionResult.NormalFailed, result)
        assertEquals(1, mildNotifier.sentCount)
        assertEquals(0, vibrator.vibrationCount)
    }

    @Test
    fun normalDecisionStillSucceedsWhenVibrationFails() {
        vibrator.throwOnVibrate = IllegalStateException("vibrator unavailable")

        val result = executor.execute(
            decision = GuardDecision.ShowNormalWarning,
            context = context()
        )

        assertEquals(InterventionResult.NormalShown, result)
        assertTrue(warningPresenter.warningShown)
        assertEquals(1, vibrator.vibrationCount)
    }

    @Test
    fun warningOverlayPresenterCanBeDismissedThroughInterfaceForLifecycleCleanup() {
        val presenter: WarningOverlayPresenter = warningPresenter

        presenter.dismissWarning()

        assertTrue(warningPresenter.dismissRequested)
    }

    @Test
    fun rageDecisionLocksWhenDeviceAdminIsActive() {
        rageLockController.deviceAdminActive = true

        val result = executor.execute(
            decision = GuardDecision.LockScreen,
            context = context()
        )

        assertEquals(InterventionResult.RageLocked, result)
        assertEquals(1, rageLockController.lockNowCount)
        assertFalse(warningPresenter.warningShown)
        assertEquals(0, vibrator.vibrationCount)
    }

    @Test
    fun rageDecisionFallsBackToNormalWhenDeviceAdminIsInactive() {
        rageLockController.deviceAdminActive = false

        val result = executor.execute(
            decision = GuardDecision.LockScreen,
            context = context()
        )

        assertEquals(InterventionResult.RageFailedDowngraded, result)
        assertEquals(0, rageLockController.lockNowCount)
        assertTrue(warningPresenter.warningShown)
        assertEquals(1, vibrator.vibrationCount)
    }

    @Test
    fun rageDecisionFallsBackToNormalWhenLockThrowsSecurityException() {
        rageLockController.deviceAdminActive = true
        rageLockController.throwOnLock = SecurityException("admin revoked")

        val result = executor.execute(
            decision = GuardDecision.LockScreen,
            context = context()
        )

        assertEquals(InterventionResult.RageFailedDowngraded, result)
        assertTrue(warningPresenter.warningShown)
        assertEquals(1, vibrator.vibrationCount)
    }

    @Test
    fun rageDecisionFallsBackToNormalWhenLockThrowsRuntimeException() {
        rageLockController.deviceAdminActive = true
        rageLockController.throwOnLock = IllegalStateException("lock unavailable")

        val result = executor.execute(
            decision = GuardDecision.LockScreen,
            context = context()
        )

        assertEquals(InterventionResult.RageFailedDowngraded, result)
        assertTrue(warningPresenter.warningShown)
        assertEquals(1, vibrator.vibrationCount)
    }

    @Test
    fun rageDecisionFallsBackToNormalWhenDeviceAdminCheckThrowsRuntimeException() {
        rageLockController.throwOnAdminCheck = IllegalStateException("admin check unavailable")

        val result = executor.execute(
            decision = GuardDecision.LockScreen,
            context = context()
        )

        assertEquals(InterventionResult.RageFailedDowngraded, result)
        assertTrue(warningPresenter.warningShown)
        assertEquals(1, vibrator.vibrationCount)
    }

    private fun context(
        settings: GuardSettings = GuardSettings.Default,
        onDismissForCurrentScreenCycle: () -> Unit = {}
    ) = InterventionContext(
        settings = settings,
        onDismissForCurrentScreenCycle = onDismissForCurrentScreenCycle
    )

    private class RecordingMildNotifier : MildInterventionNotifier {
        var sentCount = 0
        var sendSucceeds = true

        override fun sendMildWarning(context: InterventionContext) {
            sentCount += 1
            if (!sendSucceeds) throw IllegalStateException("notification unavailable")
        }
    }

    private class RecordingWarningPresenter : WarningOverlayPresenter {
        var lastTitle: String? = null
        var lastMessage: String? = null
        var warningShown = false
        var showSucceeds = true
        var dismissRequested = false
        private var dismissCallback: (() -> Unit)? = null

        override fun showWarning(
            title: String,
            message: String,
            onDismissForCurrentScreenCycle: () -> Unit
        ) {
            if (!showSucceeds) throw IllegalStateException("overlay unavailable")
            warningShown = true
            lastTitle = title
            lastMessage = message
            dismissCallback = onDismissForCurrentScreenCycle
        }

        override fun dismissWarning() {
            dismissRequested = true
        }

        fun dismissCurrentWarning() {
            dismissCallback?.invoke()
        }
    }

    private class RecordingVibrator : WarningVibrator {
        var vibrationCount = 0
        var throwOnVibrate: RuntimeException? = null

        override fun vibrateWarning() {
            vibrationCount += 1
            throwOnVibrate?.let { throw it }
        }
    }

    private class RecordingRageLockController : RageLockController {
        var deviceAdminActive = false
        var lockNowCount = 0
        var throwOnAdminCheck: RuntimeException? = null
        var throwOnLock: RuntimeException? = null

        override fun isDeviceAdminActive(): Boolean {
            throwOnAdminCheck?.let { throw it }
            return deviceAdminActive
        }

        override fun lockNow() {
            lockNowCount += 1
            throwOnLock?.let { throw it }
        }
    }
}
