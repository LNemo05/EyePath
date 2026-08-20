package org.walkguard.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.walkguard.app.guard.GuardRuntimeSyncer
import org.walkguard.app.guard.GuardSyncReason

/**
 * Best-effort process-start recovery entry (GKD App.onCreate multi-entry pattern).
 * Background FGS start may still be restricted by OEM; Tile/A11y/Boot cover the rest.
 */
class WalkGuardApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching {
                GuardRuntimeSyncer.syncGuardState(this@WalkGuardApplication, GuardSyncReason.Application)
            }
        }
    }
}
