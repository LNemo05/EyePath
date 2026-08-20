package org.walkguard.app.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GuardRuntimeStatusTracker {
    data class Status(
        val walking: Boolean = false,
        val screenOn: Boolean = false,
        val unlocked: Boolean = false
    )

    private val _statusFlow = MutableStateFlow(Status())

    val statusFlow: StateFlow<Status> = _statusFlow.asStateFlow()

    fun update(walking: Boolean, screenOn: Boolean, unlocked: Boolean) {
        _statusFlow.value = Status(
            walking = walking,
            screenOn = screenOn,
            unlocked = unlocked
        )
    }

    fun clear() {
        _statusFlow.value = Status()
    }
}
