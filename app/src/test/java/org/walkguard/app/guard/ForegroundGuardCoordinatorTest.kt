package org.walkguard.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.walkguard.app.core.engine.GuardDecision
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.data.settings.GuardSettings
import org.walkguard.app.intervention.InterventionContext
import org.walkguard.app.intervention.InterventionResult

class ForegroundGuardCoordinatorTest {
    private val interventionRunner = RecordingInterventionRunner()
    private val statsRecorder = RecordingStatsRecorder()
    private val overlayCleaner = RecordingOverlayCleaner()
    private val coordinator = ForegroundGuardCoordinator(
        interventionRunner = interventionRunner,
        statsRecorder = statsRecorder,
        dismissWarningOverlay = overlayCleaner::dismiss
    )

    @Test
    fun mildInterventionMarksCycleAndRecordsStatsOnlyAfterSuccessfulSend() {
        val input = triggerInput(
            settings = enabledSettings(globalMode = GuardMode.MILD),
            nowEpochMs = 1000L
        )

        val first = coordinator.evaluate(input)
        val second = coordinator.evaluate(input.copy(nowEpochMs = 1100L))

        assertEquals(GuardDecision.SendMildNotification, first.decision)
        assertEquals(InterventionResult.MildSent, first.interventionResult)
        assertEquals(GuardDecision.None, second.decision)
        assertEquals(1, interventionRunner.calls.size)
        assertEquals(listOf(RecordedStat(GuardMode.MILD, "com.example.app", 1000L)), statsRecorder.records)
    }

    @Test
    fun failedMildInterventionDoesNotMarkCycleOrRecordStats() {
        interventionRunner.nextResult = InterventionResult.MildFailed
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.MILD))

        val first = coordinator.evaluate(input)
        val second = coordinator.evaluate(input.copy(nowEpochMs = 1100L))

        assertEquals(InterventionResult.MildFailed, first.interventionResult)
        assertEquals(GuardDecision.SendMildNotification, second.decision)
        assertEquals(2, interventionRunner.calls.size)
        assertTrue(statsRecorder.records.isEmpty())
    }

    @Test
    fun normalWarningIsNotRepeatedWhileOverlayIsAlreadyShowing() {
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.NORMAL))

        val first = coordinator.evaluate(input)
        val second = coordinator.evaluate(input.copy(nowEpochMs = 1100L))

        assertEquals(GuardDecision.ShowNormalWarning, first.decision)
        assertEquals(InterventionResult.NormalShown, first.interventionResult)
        assertEquals(GuardDecision.None, second.decision)
        assertEquals(1, interventionRunner.calls.size)
        assertEquals(listOf(RecordedStat(GuardMode.NORMAL, "com.example.app", 1000L)), statsRecorder.records)
    }

    @Test
    fun normalFallbackNotificationIsNotRepeatedForCurrentScreenCycle() {
        interventionRunner.nextResult = InterventionResult.NormalFallbackNotificationSent
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.NORMAL))

        val first = coordinator.evaluate(input)
        val second = coordinator.evaluate(input.copy(nowEpochMs = 1100L))

        assertEquals(InterventionResult.NormalFallbackNotificationSent, first.interventionResult)
        assertEquals(GuardDecision.None, second.decision)
        assertEquals(1, interventionRunner.calls.size)
        assertEquals(listOf(RecordedStat(GuardMode.NORMAL, "com.example.app", 1000L)), statsRecorder.records)
    }

    @Test
    fun normalDismissCallbackSilencesCurrentScreenCycle() {
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.NORMAL))

        coordinator.evaluate(input)
        interventionRunner.calls.single().context.onDismissForCurrentScreenCycle()
        val afterDismiss = coordinator.evaluate(input.copy(nowEpochMs = 1200L))

        assertEquals(GuardDecision.None, afterDismiss.decision)
        assertEquals(1, interventionRunner.calls.size)
    }

    @Test
    fun rageCanLockAgainImmediatelyAfterSuccessfulLock() {
        val input = triggerInput(
            settings = enabledSettings(globalMode = GuardMode.RAGE),
            nowEpochMs = 1000L
        )

        val first = coordinator.evaluate(input)
        val second = coordinator.evaluate(input.copy(nowEpochMs = 1000L))

        assertEquals(InterventionResult.RageLocked, first.interventionResult)
        assertEquals(GuardDecision.LockScreen, second.decision)
        assertEquals(2, interventionRunner.calls.size)
        assertEquals(
            listOf(
                RecordedStat(GuardMode.RAGE, "com.example.app", 1000L),
                RecordedStat(GuardMode.RAGE, "com.example.app", 1000L)
            ),
            statsRecorder.records
        )
    }

    @Test
    fun rageCanLockAgainWhenWallClockMovesBackward() {
        val input = triggerInput(
            settings = enabledSettings(globalMode = GuardMode.RAGE),
            nowEpochMs = 1000L
        )

        coordinator.evaluate(input)
        val afterClockRollback = coordinator.evaluate(
            input.copy(nowEpochMs = 999L)
        )

        assertEquals(GuardDecision.LockScreen, afterClockRollback.decision)
        assertEquals(2, interventionRunner.calls.size)
    }

    @Test
    fun rageDowngradeDoesNotRecordAmbiguousStats() {
        interventionRunner.nextResult = InterventionResult.RageFailedDowngraded
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.RAGE))

        val result = coordinator.evaluate(input)

        assertEquals(InterventionResult.RageFailedDowngraded, result.interventionResult)
        assertTrue(statsRecorder.records.isEmpty())
    }

    @Test
    fun rageDowngradeIsNotRepeatedForCurrentScreenCycle() {
        interventionRunner.nextResult = InterventionResult.RageFailedDowngraded
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.RAGE))

        val first = coordinator.evaluate(input)
        val second = coordinator.evaluate(input.copy(nowEpochMs = 1100L))

        assertEquals(InterventionResult.RageFailedDowngraded, first.interventionResult)
        assertEquals(GuardDecision.None, second.decision)
        assertEquals(1, interventionRunner.calls.size)
        assertTrue(statsRecorder.records.isEmpty())
    }

    @Test
    fun screenOffResetsCycleAndDismissesOverlay() {
        val input = triggerInput(settings = enabledSettings(globalMode = GuardMode.NORMAL))
        coordinator.evaluate(input)

        coordinator.onScreenOff()
        coordinator.onScreenOn()
        val nextCycle = coordinator.evaluate(input.copy(nowEpochMs = 2000L))

        assertEquals(1, overlayCleaner.dismissCount)
        assertEquals(GuardDecision.ShowNormalWarning, nextCycle.decision)
        assertEquals(2, interventionRunner.calls.size)
    }

    @Test
    fun incompleteTriggerDoesNothing() {
        val result = coordinator.evaluate(triggerInput(walking = false))

        assertEquals(GuardDecision.None, result.decision)
        assertEquals(InterventionResult.None, result.interventionResult)
        assertTrue(interventionRunner.calls.isEmpty())
        assertTrue(statsRecorder.records.isEmpty())
    }

    private fun enabledSettings(
        globalMode: GuardMode = GuardSettings.Default.globalMode
    ): GuardSettings = GuardSettings.Default.copy(
        guardEnabled = true,
        globalMode = globalMode
    )

    private fun triggerInput(
        settings: GuardSettings = enabledSettings(),
        foregroundPackage: String? = "com.example.app",
        walking: Boolean = true,
        screenOn: Boolean = true,
        unlocked: Boolean = true,
        appPolicy: AppPolicy? = null,
        nowEpochMs: Long = 1000L
    ) = ForegroundGuardInput(
        settings = settings,
        foregroundPackage = foregroundPackage,
        walking = walking,
        screenOn = screenOn,
        unlocked = unlocked,
        appPolicy = appPolicy,
        nowEpochMs = nowEpochMs
    )

    private class RecordingInterventionRunner : GuardInterventionRunner {
        data class Call(
            val decision: GuardDecision,
            val context: InterventionContext
        )

        var nextResult: InterventionResult? = null
        val calls = mutableListOf<Call>()

        override fun execute(decision: GuardDecision, context: InterventionContext): InterventionResult {
            calls += Call(decision, context)
            return nextResult ?: when (decision) {
                GuardDecision.SendMildNotification -> InterventionResult.MildSent
                GuardDecision.ShowNormalWarning -> InterventionResult.NormalShown
                GuardDecision.LockScreen -> InterventionResult.RageLocked
                GuardDecision.None -> InterventionResult.None
            }
        }
    }

    private class RecordingStatsRecorder : GuardStatsRecorder {
        val records = mutableListOf<RecordedStat>()

        override fun record(mode: GuardMode, packageName: String, nowEpochMs: Long) {
            records += RecordedStat(mode, packageName, nowEpochMs)
        }
    }

    private data class RecordedStat(
        val mode: GuardMode,
        val packageName: String,
        val nowEpochMs: Long
    )

    private class RecordingOverlayCleaner {
        var dismissCount = 0

        fun dismiss() {
            dismissCount += 1
        }
    }
}
