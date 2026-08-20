package org.walkguard.app.core.engine

sealed interface GuardDecision {
    data object None : GuardDecision
    data object SendMildNotification : GuardDecision
    data object ShowNormalWarning : GuardDecision
    data object LockScreen : GuardDecision
}
