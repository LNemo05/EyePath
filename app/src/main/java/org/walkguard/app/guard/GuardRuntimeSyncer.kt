package org.walkguard.app.guard

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Idempotent multi-entry reconciler for guard keepalive.
 *
 * When [org.walkguard.app.data.settings.GuardSettings.guardEnabled] is true, starts the FGS in
 * recovery mode (`requireFullGate = false`) so only FGS-critical permissions are required.
 * Never clears `guardEnabled` on transient start failure.
 */
object GuardRuntimeSyncer {
    private val mutex = Mutex()

    suspend fun syncGuardState(context: Context, reason: GuardSyncReason) {
        mutex.withLock {
            val app = context.applicationContext
            val settingsRepo = GuardRuntimeGraph.settingsRepository(app)
            val settings = settingsRepo.settings.first()
            if (!settings.guardEnabled) return

            // Optional: a11y repair when WRITE_SECURE_SETTINGS granted and instance dead.
            runCatching {
                AccessibilitySecureSettings.fixRestartAccessibilityIfNeeded(app)
            }

            GuardForegroundService.startSafely(
                context = app,
                action = "org.walkguard.app.action.SYNC_${reason.name}",
                requiredMode = settings.globalMode,
                requireFullGate = false,
            )
        }
    }
}
