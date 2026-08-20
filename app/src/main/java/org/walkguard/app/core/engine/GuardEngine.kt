package org.walkguard.app.core.engine

import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.DetectionSnapshot
import org.walkguard.app.core.model.GuardMode

class GuardEngine {
    data class Input(
        val guardEnabled: Boolean,
        val pauseActive: Boolean,
        val snapshot: DetectionSnapshot,
        val appPolicy: AppPolicy?,
        val globalMode: GuardMode,
        val normalDismissedForScreenCycle: Boolean,
        val mildAlreadySentForScreenCycle: Boolean,
        val rageCooldownElapsed: Boolean
    )

    fun decide(input: Input): GuardDecision {
        if (!input.guardEnabled) return GuardDecision.None
        if (input.pauseActive) return GuardDecision.None
        if (!input.snapshot.triggerSatisfied) return GuardDecision.None

        val policy = input.appPolicy ?: AppPolicy.INHERIT
        if (policy == AppPolicy.WHITELIST) return GuardDecision.None

        val mode = when (policy) {
            AppPolicy.INHERIT -> input.globalMode
            AppPolicy.MILD -> GuardMode.MILD
            AppPolicy.NORMAL -> GuardMode.NORMAL
            AppPolicy.RAGE -> GuardMode.RAGE
            AppPolicy.WHITELIST -> return GuardDecision.None
        }

        return when (mode) {
            GuardMode.MILD -> if (input.mildAlreadySentForScreenCycle) {
                GuardDecision.None
            } else {
                GuardDecision.SendMildNotification
            }
            GuardMode.NORMAL -> if (input.normalDismissedForScreenCycle) {
                GuardDecision.None
            } else {
                GuardDecision.ShowNormalWarning
            }
            GuardMode.RAGE -> if (input.rageCooldownElapsed) {
                GuardDecision.LockScreen
            } else {
                GuardDecision.None
            }
        }
    }
}
