package org.walkguard.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Never clear guardEnabled on boot start failure; Syncer is best-effort.
                GuardRuntimeSyncer.syncGuardState(
                    context = context,
                    reason = GuardSyncReason.Boot
                )
            } catch (_: RuntimeException) {
                // OEM/background-start restrictions are handled by leaving recovery for later entries.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
