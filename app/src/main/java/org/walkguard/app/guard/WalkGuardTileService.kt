package org.walkguard.app.guard

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.walkguard.app.MainActivity
import org.walkguard.app.permissions.PermissionGate
import org.walkguard.app.permissions.PermissionRepository

/**
 * GKD-style QS tile (BaseTileService + GkdTileService, no Shizuku/automator).
 *
 * - Active when user intent is on (`guardEnabled`) or a11y instance is connected.
 * - onStartListening: throttled a11y repair + [GuardRuntimeSyncer.syncGuardState]
 * - onClick: toggle guard; open MainActivity when enabling without full permission gate.
 */
class WalkGuardTileService : TileService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private val listeningFlow = MutableStateFlow(false)
    private val guardEnabledFlow = MutableStateFlow(false)

    private val activeFlow = combine(
        guardEnabledFlow,
        WalkAccessibilityService.isRunning
    ) { guardEnabled, a11yRunning ->
        guardEnabled || a11yRunning
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private var tileCollectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        tileCollectJob = scope.launch {
            combine(activeFlow, listeningFlow) { active, listening ->
                active to listening
            }.collect { (active, listening) ->
                if (listening) {
                    qsTile?.let { tile ->
                        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        tile.updateTile()
                    }
                }
            }
        }
        scope.launch {
            GuardRuntimeGraph.settingsRepository(this@WalkGuardTileService).settings.collect { settings ->
                guardEnabledFlow.value = settings.guardEnabled
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        listeningFlow.value = true
        val t = System.currentTimeMillis()
        if (t - lastA11yFixTime > 3_000L) {
            lastA11yFixTime = t
            scope.launch(Dispatchers.IO) {
                runCatching {
                    AccessibilitySecureSettings.fixRestartAccessibilityIfNeeded(this@WalkGuardTileService)
                }
                GuardRuntimeSyncer.syncGuardState(this@WalkGuardTileService, GuardSyncReason.Tile)
            }
        } else {
            scope.launch(Dispatchers.IO) {
                GuardRuntimeSyncer.syncGuardState(this@WalkGuardTileService, GuardSyncReason.Tile)
            }
        }
        refreshGuardEnabled()
    }

    override fun onStopListening() {
        listeningFlow.value = false
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch(Dispatchers.IO) {
            val app = applicationContext
            val settingsRepo = GuardRuntimeGraph.settingsRepository(app)
            val settings = settingsRepo.settings.first()
            val status = PermissionRepository(app).currentStatus()
            val gate = PermissionGate.canEnableGuard(status, settings.globalMode)

            if (settings.guardEnabled) {
                settingsRepo.setGuardEnabled(false)
                app.stopService(Intent(app, GuardForegroundService::class.java))
                GuardRuntimeStatusTracker.clear()
            } else {
                if (!gate.allowed) {
                    openMainActivity()
                    return@launch
                }
                runCatching { settingsRepo.setGuardEnabled(true) }
                if (AccessibilitySecureSettings.canWriteSecureSettings(app)) {
                    runCatching {
                        AccessibilitySecureSettings.fixRestartAccessibilityIfNeeded(app)
                    }
                }
                GuardRuntimeSyncer.syncGuardState(app, GuardSyncReason.Tile)
            }
            refreshGuardEnabled()
        }
    }

    override fun onDestroy() {
        tileCollectJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun refreshGuardEnabled() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                guardEnabledFlow.value =
                    GuardRuntimeGraph.settingsRepository(this@WalkGuardTileService).settings.first().guardEnabled
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        @Volatile
        private var lastA11yFixTime = 0L
    }
}
