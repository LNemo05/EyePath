package org.walkguard.app.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.DetectionSnapshot
import org.walkguard.app.core.model.GuardMode

class GuardEngineTest {
    private val engine = GuardEngine()

    private fun input(
        guardEnabled: Boolean = true,
        pauseActive: Boolean = false,
        snapshot: DetectionSnapshot = DetectionSnapshot(
            screenOn = true,
            unlocked = true,
            walking = true,
            foregroundPackage = "com.example.app"
        ),
        appPolicy: AppPolicy? = null,
        globalMode: GuardMode = GuardMode.RAGE,
        normalDismissedForScreenCycle: Boolean = false,
        mildAlreadySentForScreenCycle: Boolean = false,
        rageCooldownElapsed: Boolean = true
    ) = GuardEngine.Input(
        guardEnabled = guardEnabled,
        pauseActive = pauseActive,
        snapshot = snapshot,
        appPolicy = appPolicy,
        globalMode = globalMode,
        normalDismissedForScreenCycle = normalDismissedForScreenCycle,
        mildAlreadySentForScreenCycle = mildAlreadySentForScreenCycle,
        rageCooldownElapsed = rageCooldownElapsed
    )

    @Test fun guardOffDoesNothing() {
        assertEquals(GuardDecision.None, engine.decide(input(guardEnabled = false)))
    }

    @Test fun pauseDoesNothing() {
        assertEquals(GuardDecision.None, engine.decide(input(pauseActive = true)))
    }

    @Test fun incompleteTriggerDoesNothing() {
        val snapshot = DetectionSnapshot(screenOn = true, unlocked = true, walking = false, foregroundPackage = "com.example.app")
        assertEquals(GuardDecision.None, engine.decide(input(snapshot = snapshot)))
    }

    @Test fun whitelistDoesNothing() {
        assertEquals(GuardDecision.None, engine.decide(input(appPolicy = AppPolicy.WHITELIST)))
    }

    @Test fun appPolicyOverridesGlobalMode() {
        assertEquals(GuardDecision.ShowNormalWarning, engine.decide(input(appPolicy = AppPolicy.NORMAL, globalMode = GuardMode.RAGE)))
    }

    @Test fun inheritedPolicyUsesGlobalMode() {
        assertEquals(GuardDecision.LockScreen, engine.decide(input(appPolicy = AppPolicy.INHERIT, globalMode = GuardMode.RAGE)))
    }

    @Test fun normalDismissedIsSilentForCurrentScreenCycle() {
        assertEquals(GuardDecision.None, engine.decide(input(appPolicy = AppPolicy.NORMAL, normalDismissedForScreenCycle = true)))
    }

    @Test fun mildAlreadySentIsSilentForCurrentScreenCycle() {
        assertEquals(GuardDecision.None, engine.decide(input(appPolicy = AppPolicy.MILD, mildAlreadySentForScreenCycle = true)))
    }

    @Test fun rageCooldownBlocksLock() {
        assertEquals(GuardDecision.None, engine.decide(input(appPolicy = AppPolicy.RAGE, rageCooldownElapsed = false)))
    }
}
