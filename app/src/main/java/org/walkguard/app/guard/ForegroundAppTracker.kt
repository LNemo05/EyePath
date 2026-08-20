package org.walkguard.app.guard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ForegroundAppTracker {
    private val _currentPackageFlow = MutableStateFlow<String?>(null)

    val currentPackageFlow: StateFlow<String?> = _currentPackageFlow.asStateFlow()

    fun updateCurrentPackage(packageName: CharSequence?) {
        val normalized = packageName?.toString()?.trim().orEmpty()
        if (normalized.isNotEmpty()) {
            _currentPackageFlow.value = normalized
        }
    }

    fun clear() {
        _currentPackageFlow.value = null
    }
}
