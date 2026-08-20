package org.walkguard.app.guard

import org.walkguard.app.core.engine.GuardDecision
import org.walkguard.app.core.engine.GuardEngine
import org.walkguard.app.core.engine.ScreenCycleTracker
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.DetectionSnapshot
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.data.settings.GuardSettings
import org.walkguard.app.intervention.InterventionContext
import org.walkguard.app.intervention.InterventionResult

data class ForegroundGuardInput(
    val settings: GuardSettings,
    val foregroundPackage: String?,
    val walking: Boolean,
    val screenOn: Boolean,
    val unlocked: Boolean,
    val appPolicy: AppPolicy?,
    val nowEpochMs: Long
)

data class ForegroundGuardEvaluation(
    val decision: GuardDecision,
    val interventionResult: InterventionResult
)

interface GuardInterventionRunner {
    fun execute(decision: GuardDecision, context: InterventionContext): InterventionResult
}

interface GuardStatsRecorder {
    fun record(mode: GuardMode, packageName: String, nowEpochMs: Long)
}

class ForegroundGuardCoordinator(
    private val interventionRunner: GuardInterventionRunner,
    private val statsRecorder: GuardStatsRecorder,
    private val dismissWarningOverlay: () -> Unit,
    private val guardEngine: GuardEngine = GuardEngine(),
    private val screenCycleTracker: ScreenCycleTracker = ScreenCycleTracker()
) {
    private var normalInterventionHandledForScreenCycle = false
    private var rageDowngradeHandledForScreenCycle = false
    private var lastRageLockAtEpochMs: Long? = null

    fun evaluate(input: ForegroundGuardInput): ForegroundGuardEvaluation {
        val decision = guardEngine.decide(input.toGuardEngineInput())
        if (decision == GuardDecision.None) {
            return ForegroundGuardEvaluation(decision, InterventionResult.None)
        }

        val result = interventionRunner.execute(
            decision = decision,
            context = InterventionContext(
                settings = input.settings,
                onDismissForCurrentScreenCycle = {
                    normalInterventionHandledForScreenCycle = true
                    screenCycleTracker.markNormalDismissed()
                }
            )
        )
        applySuccessfulSideEffects(decision, result, input)
        return ForegroundGuardEvaluation(decision, result)
    }

    fun onScreenOn() {
        normalInterventionHandledForScreenCycle = false
        rageDowngradeHandledForScreenCycle = false
        screenCycleTracker.onScreenOn()
    }

    fun onScreenOff() {
        normalInterventionHandledForScreenCycle = false
        rageDowngradeHandledForScreenCycle = false
        screenCycleTracker.onScreenOff()
        dismissWarningOverlay()
    }

    private fun ForegroundGuardInput.toGuardEngineInput(): GuardEngine.Input {
        return GuardEngine.Input(
            guardEnabled = settings.guardEnabled,
            pauseActive = nowEpochMs < settings.pauseUntilEpochMs,
            snapshot = DetectionSnapshot(
                screenOn = screenOn,
                unlocked = unlocked,
                walking = walking,
                foregroundPackage = foregroundPackage
            ),
            appPolicy = appPolicy,
            globalMode = settings.globalMode,
            normalDismissedForScreenCycle = screenCycleTracker.normalDismissed || normalInterventionHandledForScreenCycle,
            mildAlreadySentForScreenCycle = screenCycleTracker.mildSent,
            rageCooldownElapsed = !rageDowngradeHandledForScreenCycle && isRageCooldownElapsed(nowEpochMs)
        )
    }

    private fun isRageCooldownElapsed(nowEpochMs: Long): Boolean {
        if (RAGE_LOCK_COOLDOWN_MS == 0L) return true

        val lastLockAt = lastRageLockAtEpochMs ?: return true
        return nowEpochMs - lastLockAt >= RAGE_LOCK_COOLDOWN_MS
    }

    private fun applySuccessfulSideEffects(
        decision: GuardDecision,
        result: InterventionResult,
        input: ForegroundGuardInput
    ) {
        val packageName = input.foregroundPackage ?: return
        when (result) {
            InterventionResult.MildSent -> {
                screenCycleTracker.markMildSent()
                statsRecorder.record(GuardMode.MILD, packageName, input.nowEpochMs)
            }
            InterventionResult.NormalShown,
            InterventionResult.NormalFallbackNotificationSent -> {
                normalInterventionHandledForScreenCycle = true
                statsRecorder.record(GuardMode.NORMAL, packageName, input.nowEpochMs)
            }
            InterventionResult.RageLocked -> {
                lastRageLockAtEpochMs = input.nowEpochMs
                statsRecorder.record(GuardMode.RAGE, packageName, input.nowEpochMs)
            }
            InterventionResult.RageFailedDowngraded -> {
                normalInterventionHandledForScreenCycle = true
                rageDowngradeHandledForScreenCycle = true
            }
            InterventionResult.None,
            InterventionResult.NormalFailed,
            InterventionResult.MildFailed -> Unit
        }
    }

    private companion object {
        const val RAGE_LOCK_COOLDOWN_MS = 0L
    }
}
