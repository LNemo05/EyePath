package org.walkguard.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GKD-style recovery after APK update: when the user previously left guard enabled,
 * re-sync FGS + optional a11y repair without clearing [guardEnabled].
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GuardRuntimeSyncer.syncGuardState(
                    context = context,
                    reason = GuardSyncReason.PackageReplaced
                )
            } catch (_: RuntimeException) {
                // Package-replaced recovery must never crash the process.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
