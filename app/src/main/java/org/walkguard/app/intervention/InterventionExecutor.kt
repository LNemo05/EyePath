package org.walkguard.app.intervention

import org.walkguard.app.core.engine.GuardDecision
import org.walkguard.app.data.settings.GuardSettings

data class InterventionContext(
    val settings: GuardSettings,
    val onDismissForCurrentScreenCycle: () -> Unit
)

sealed interface InterventionResult {
    data object None : InterventionResult
    data object NormalShown : InterventionResult
    data object NormalFallbackNotificationSent : InterventionResult
    data object NormalFailed : InterventionResult
    data object MildSent : InterventionResult
    data object MildFailed : InterventionResult
    data object RageLocked : InterventionResult
    data object RageFailedDowngraded : InterventionResult
}

interface MildInterventionNotifier {
    fun sendMildWarning(context: InterventionContext)
}

interface WarningOverlayPresenter {
    fun showWarning(
        title: String,
        message: String,
        onDismissForCurrentScreenCycle: () -> Unit
    )

    fun dismissWarning()
}

interface WarningVibrator {
    fun vibrateWarning()
}

interface RageLockController {
    fun isDeviceAdminActive(): Boolean
    fun lockNow()
}

class InterventionExecutor(
    private val mildNotifier: MildInterventionNotifier,
    private val warningPresenter: WarningOverlayPresenter,
    private val vibrator: WarningVibrator,
    private val rageLockController: RageLockController
) {
    fun execute(decision: GuardDecision, context: InterventionContext): InterventionResult {
        return when (decision) {
            GuardDecision.None -> InterventionResult.None
            GuardDecision.SendMildNotification -> executeMild(context)
            GuardDecision.ShowNormalWarning -> runNormalIntervention(context)
            GuardDecision.LockScreen -> executeRage(context)
        }
    }

    private fun executeMild(context: InterventionContext): InterventionResult {
        return try {
            mildNotifier.sendMildWarning(context)
            InterventionResult.MildSent
        } catch (_: SecurityException) {
            InterventionResult.MildFailed
        } catch (_: RuntimeException) {
            InterventionResult.MildFailed
        }
    }

    private fun executeRage(context: InterventionContext): InterventionResult {
        return try {
            if (!rageLockController.isDeviceAdminActive()) {
                runNormalIntervention(context)
                return InterventionResult.RageFailedDowngraded
            }

            rageLockController.lockNow()
            InterventionResult.RageLocked
        } catch (_: SecurityException) {
            runNormalIntervention(context)
            InterventionResult.RageFailedDowngraded
        } catch (_: RuntimeException) {
            runNormalIntervention(context)
            InterventionResult.RageFailedDowngraded
        }
    }

    private fun runNormalIntervention(context: InterventionContext): InterventionResult {
        return try {
            warningPresenter.showWarning(
                title = context.settings.warningTitle,
                message = context.settings.warningMessage,
                onDismissForCurrentScreenCycle = context.onDismissForCurrentScreenCycle
            )
            tryVibrate()
            InterventionResult.NormalShown
        } catch (_: SecurityException) {
            sendNormalFallbackNotification(context)
        } catch (_: RuntimeException) {
            sendNormalFallbackNotification(context)
        }
    }

    private fun tryVibrate() {
        try {
            vibrator.vibrateWarning()
        } catch (_: RuntimeException) {
            // Overlay visibility is the primary normal-mode intervention; vibration must not crash it.
        }
    }

    private fun sendNormalFallbackNotification(context: InterventionContext): InterventionResult {
        return try {
            mildNotifier.sendMildWarning(context)
            InterventionResult.NormalFallbackNotificationSent
        } catch (_: SecurityException) {
            InterventionResult.NormalFailed
        } catch (_: RuntimeException) {
            InterventionResult.NormalFailed
        }
    }
}
