package org.walkguard.app.guard

import android.Manifest
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.walkguard.app.core.engine.GuardDecision
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.data.db.AppPolicyDao
import org.walkguard.app.data.db.StatsDao
import org.walkguard.app.data.settings.GuardSettings
import org.walkguard.app.intervention.DevicePolicyRageLockController
import org.walkguard.app.intervention.InterventionContext
import org.walkguard.app.intervention.InterventionExecutor
import org.walkguard.app.intervention.InterventionResult
import org.walkguard.app.intervention.VibrationController
import org.walkguard.app.intervention.WalkGuardNotificationManager
import org.walkguard.app.intervention.WarningOverlayController
import org.walkguard.app.ui.i18n.formatDetectionState
import org.walkguard.app.permissions.PermissionGate
import org.walkguard.app.permissions.PermissionRepository
import java.time.Instant
import java.time.ZoneId

class GuardForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val evaluationMutex = Mutex()
    private val screenStateReceiver = ScreenStateReceiver()

    private lateinit var notificationManager: WalkGuardNotificationManager
    private lateinit var coordinator: ForegroundGuardCoordinator
    private lateinit var appPolicyDao: AppPolicyDao
    private lateinit var overlayController: WarningOverlayController
    private lateinit var permissionRepository: PermissionRepository

    private var walkingDetector: StepWalkingDetector? = null
    private var evaluationJob: Job? = null
    private var screenReceiverRegistered = false

    private var currentSettings: GuardSettings = GuardSettings.Default
    private var currentForegroundPackage: String? = null
    private var currentWalking: Boolean = false
    private var currentScreenOn: Boolean = false
    private var currentUnlocked: Boolean = false
    private var foregroundStarted = false
    private var lastPostedStatus: WalkGuardNotificationManager.GuardStatus? = null

    override fun onCreate() {
        super.onCreate()
        val database = GuardRuntimeGraph.database(this)
        val settingsRepository = GuardRuntimeGraph.settingsRepository(this)
        appPolicyDao = database.appPolicyDao()
        notificationManager = WalkGuardNotificationManager(this)
        overlayController = WarningOverlayController(this)
        permissionRepository = PermissionRepository(this)

        val interventionExecutor = InterventionExecutor(
            mildNotifier = notificationManager,
            warningPresenter = overlayController,
            vibrator = VibrationController(this),
            rageLockController = DevicePolicyRageLockController(this)
        )
        coordinator = ForegroundGuardCoordinator(
            interventionRunner = AndroidGuardInterventionRunner(interventionExecutor),
            statsRecorder = AsyncGuardStatsRecorder(serviceScope, database.statsDao()),
            dismissWarningOverlay = { overlayController.dismissWarning() }
        )

        updateInteractiveState()
        updateStatusNotification()
        if (!startInForegroundSafely()) {
            // startForegroundService was used; failing to promote must stop immediately
            // to avoid system crash, but do NOT clear guardEnabled (Syncer will retry).
            GuardRuntimeStatusTracker.clear()
            stopSelf()
            return
        }
        runCatching { registerScreenReceiver() }

        serviceScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                currentSettings = settings
                if (!settings.guardEnabled) {
                    // Explicit user disable (settings flipped off) — tear down service.
                    applyPermissionDegrade()
                    stopSelf()
                    return@collectLatest
                }
                if (!canRunWithCurrentPermissions(settings)) {
                    // Keep FGS + status notification alive; pause interventions until perms return.
                    applyPermissionDegrade()
                    updateStatusNotification()
                    return@collectLatest
                }
                restartWalkingDetector()
                updateStatusNotification()
                evaluateCurrentState()
            }
        }
        serviceScope.launch {
            ForegroundAppTracker.currentPackageFlow.collectLatest { packageName ->
                currentForegroundPackage = packageName
                evaluateCurrentState()
            }
        }
        serviceScope.launch {
            permissionRepository.observeStatus().collectLatest {
                if (!currentSettings.guardEnabled) return@collectLatest
                if (!canRunWithCurrentPermissions(currentSettings)) {
                    applyPermissionDegrade()
                    updateStatusNotification()
                    return@collectLatest
                }
                restartWalkingDetector()
                updateStatusNotification()
                evaluateCurrentState()
            }
        }
        serviceScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                updateInteractiveState()
                currentWalking = walkingDetector?.currentState()?.isWalking == true
                updateStatusNotification()
                evaluateCurrentState()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            Intent.ACTION_SCREEN_ON -> coordinator.onScreenOn()
            Intent.ACTION_SCREEN_OFF -> {
                currentWalking = false
                currentForegroundPackage = null
                ForegroundAppTracker.clear()
                coordinator.onScreenOff()
            }
            Intent.ACTION_USER_PRESENT -> Unit
        }
        updateInteractiveState()
        updateStatusNotification()
        evaluateCurrentState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenReceiverRegistered = false
        }
        evaluationJob?.cancel()
        walkingDetector?.stop()
        walkingDetector = null
        overlayController.dismissWarning()
        GuardRuntimeStatusTracker.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun restartWalkingDetector() {
        if (walkingDetector != null) return

        currentWalking = false
        val sensorManager = getSystemService(SensorManager::class.java) ?: return
        val detector = StepWalkingDetector(
            sensorManager = sensorManager,
            onStateChanged = { state ->
                currentWalking = state.isWalking
                updateStatusNotification()
                evaluateCurrentState()
            }
        )
        val started = try {
            detector.start()
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (started) {
            walkingDetector = detector
        }
    }

    private fun evaluateCurrentState() {
        evaluationJob?.cancel()
        evaluationJob = serviceScope.launch {
            try {
                evaluationMutex.withLock {
                    val foregroundPackage = currentForegroundPackage
                    val appPolicy = foregroundPackage?.let { packageName ->
                        withContext(Dispatchers.IO) {
                            appPolicyDao.getPolicy(packageName)?.policy.toAppPolicyOrNull()
                        }
                    }
                    if (!canRunWithCurrentPermissions(currentSettings) ||
                        !canEvaluateWithCurrentPermissions(currentSettings, appPolicy)
                    ) {
                        // Global or app-specific permission gaps must skip this cycle only.
                        // Stopping the whole FGS drops the status notification and blocks recovery.
                        return@withLock
                    }
                    coordinator.evaluate(
                        ForegroundGuardInput(
                            settings = currentSettings,
                            foregroundPackage = foregroundPackage,
                            walking = currentWalking,
                            screenOn = currentScreenOn,
                            unlocked = currentUnlocked,
                            appPolicy = appPolicy,
                            nowEpochMs = System.currentTimeMillis()
                        )
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: RuntimeException) {
                // A transient DB/intervention failure must not kill the permanent guard loop.
            }
        }
    }

    private fun updateInteractiveState() {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        currentScreenOn = powerManager?.isInteractive == true
        currentUnlocked = currentScreenOn && keyguardManager?.isKeyguardLocked == false
    }

    private fun startInForegroundSafely(): Boolean {
        if (!hasActivityRecognitionPermission()) return false
        return try {
            val notification = notificationManager.buildStatusNotification(currentStatus())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    WalkGuardNotificationManager.NOTIFICATION_ID_GUARD_STATUS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(WalkGuardNotificationManager.NOTIFICATION_ID_GUARD_STATUS, notification)
            }
            foregroundStarted = true
            lastPostedStatus = currentStatus()
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    private fun canRunWithCurrentPermissions(settings: GuardSettings): Boolean {
        if (!settings.guardEnabled) return true

        val status = permissionRepository.currentStatus()
        return PermissionGate.canEnableGuard(status, settings.globalMode).allowed
    }

    private fun canEvaluateWithCurrentPermissions(settings: GuardSettings, appPolicy: AppPolicy?): Boolean {
        if (!settings.guardEnabled) return true

        val status = permissionRepository.currentStatus()
        return PermissionGate.canRunGuardForApp(status, settings.globalMode, appPolicy).allowed
    }

    /**
     * Pause walking detection / interventions while keeping the FGS and status notification.
     * Does NOT call stopSelf() and does NOT clear guardEnabled.
     */
    private fun applyPermissionDegrade() {
        walkingDetector?.stop()
        walkingDetector = null
        currentWalking = false
        runCatching { overlayController.dismissWarning() }
        // Keep last screen/unlock status so Home does not flash empty; walking forced false.
        GuardRuntimeStatusTracker.update(
            walking = false,
            screenOn = currentScreenOn,
            unlocked = currentUnlocked
        )
    }

    private fun updateStatusNotification() {
        GuardRuntimeStatusTracker.update(
            walking = currentWalking,
            screenOn = currentScreenOn,
            unlocked = currentUnlocked
        )
        if (!::notificationManager.isInitialized || !foregroundStarted) return

        val status = currentStatus()
        if (status == lastPostedStatus) return
        lastPostedStatus = status

        // Re-assert via startForeground so the ongoing FGS notification stays bound
        // to the service identity. Plain notify() can race on some OEMs and briefly
        // drop the shade entry while the service is still running.
        runCatching {
            val notification = notificationManager.buildStatusNotification(status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    WalkGuardNotificationManager.NOTIFICATION_ID_GUARD_STATUS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(
                    WalkGuardNotificationManager.NOTIFICATION_ID_GUARD_STATUS,
                    notification
                )
            }
        }
    }

    private fun currentStatus(): WalkGuardNotificationManager.GuardStatus {
        return WalkGuardNotificationManager.GuardStatus(
            mode = currentSettings.globalMode,
            detectionState = formatDetectionState(
                screenOn = currentScreenOn,
                unlocked = currentUnlocked,
                walking = currentWalking,
                foregroundApp = currentForegroundPackage
            )
        )
    }

    private fun String?.toAppPolicyOrNull(): AppPolicy? {
        return this?.let { runCatching { AppPolicy.valueOf(it) }.getOrNull() }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        private const val ACTION_STOP = "org.walkguard.app.action.STOP_GUARD"

        fun start(context: Context, action: String? = null) {
            startSafely(context, action)
        }

        fun startSafely(
            context: Context,
            action: String? = null,
            requiredMode: GuardMode = GuardMode.MILD,
            /**
             * When false (recovery/sync paths), only require FGS-critical permission
             * (ACTIVITY_RECOGNITION). Full gate still applies to interventions inside the service.
             * When true (explicit UI enable), require full canEnableGuard for the mode.
             */
            requireFullGate: Boolean = true
        ): Boolean {
            if (requireFullGate) {
                if (!canStartWithCurrentPermissions(context, requiredMode)) return false
            } else if (!hasActivityRecognitionForStart(context)) {
                return false
            }

            val intent = Intent(context, GuardForegroundService::class.java).apply {
                this.action = action
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: SecurityException) {
                false
            } catch (_: RuntimeException) {
                false
            }
        }

        private fun canStartWithCurrentPermissions(context: Context, requiredMode: GuardMode): Boolean {
            val appContext = context.applicationContext
            val status = PermissionRepository(appContext).currentStatus()
            return PermissionGate.canEnableGuard(status, requiredMode).allowed
        }

        private fun hasActivityRecognitionForStart(context: Context): Boolean {
            val appContext = context.applicationContext
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                appContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}

private class AndroidGuardInterventionRunner(
    private val interventionExecutor: InterventionExecutor
) : GuardInterventionRunner {
    override fun execute(decision: GuardDecision, context: InterventionContext): InterventionResult {
        return interventionExecutor.execute(decision, context)
    }
}

private class AsyncGuardStatsRecorder(
    private val scope: CoroutineScope,
    private val statsDao: StatsDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : GuardStatsRecorder {
    override fun record(mode: GuardMode, packageName: String, nowEpochMs: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val day = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId).toLocalDate().toString()
                statsDao.incrementModeCount(day, mode.name, nowEpochMs)
                statsDao.incrementAppDailyCount(day, packageName)
            } catch (_: RuntimeException) {
                // Stats are best effort; guard interventions must keep running if writes fail.
            }
        }
    }
}
