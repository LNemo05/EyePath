package org.walkguard.app.guard

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object GuardRuntimeSync {
    /**
     * DataStore may keep [guardEnabled] true while the foreground service is not running
     * (process kill, failed FGS start, permission blip). Resume on each cold start via Syncer.
     *
     * Never persists guardEnabled=false on transient faults — recovery entries retry later.
     */
    fun resumePersistedGuardIfNeeded(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            GuardRuntimeSyncer.syncGuardState(
                context = activity,
                reason = GuardSyncReason.MainActivity
            )
        }
    }
}