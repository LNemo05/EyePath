package org.walkguard.app.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Delivers screen lifecycle to [GuardForegroundService] while also acting as a recovery entry.
 *
 * Critical: preserve the original [Intent.ACTION_SCREEN_ON] / [Intent.ACTION_SCREEN_OFF] action.
 * The service uses those actions to reset [org.walkguard.app.core.engine.ScreenCycleTracker]
 * so "don't remind again this screen-on cycle" only lasts for the current cycle.
 *
 * Using a synthetic SYNC_Screen action alone would leave dismiss/mild flags stuck forever.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = GuardRuntimeGraph.settingsRepository(context).settings.first()
                if (!settings.guardEnabled) return@launch

                GuardForegroundService.startSafely(
                    context = context,
                    action = action,
                    requiredMode = settings.globalMode,
                    requireFullGate = false,
                )
            } catch (_: RuntimeException) {
                // Screen broadcasts should never crash if permission state changed externally.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
