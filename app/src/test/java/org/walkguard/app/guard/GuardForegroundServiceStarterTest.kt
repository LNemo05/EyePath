package org.walkguard.app.guard

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.walkguard.app.core.model.GuardMode

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class GuardForegroundServiceStarterTest {
    private val baseContext = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        clearSharedPermissionState()
    }

    @After
    fun tearDown() {
        clearSharedPermissionState()
    }

    @Test
    fun startSafelyReturnsFalseWhenForegroundStartThrows() {
        grantCoreRuntimePermissions(baseContext)
        enableAccessibilityService(baseContext)
        val context = ThrowingStartContext(baseContext)

        val started = GuardForegroundService.startSafely(context, "test-action")

        assertTrue(context.startForegroundServiceCalled)
        assertFalse(started)
    }

    @Test
    fun startSafelyDoesNotStartServiceWhenCorePermissionsAreMissing() {
        grantCoreRuntimePermissions(baseContext)
        disableAccessibilityService(baseContext)
        val context = ThrowingStartContext(baseContext)

        val started = GuardForegroundService.startSafely(context, "test-action")

        assertFalse(context.startForegroundServiceCalled)
        assertFalse(started)
    }

    @Test
    fun startSafelyDoesNotStartNormalModeWhenOverlayPermissionIsMissing() {
        grantCoreRuntimePermissions(baseContext)
        enableAccessibilityService(baseContext)
        val context = ThrowingStartContext(baseContext)

        val started = GuardForegroundService.startSafely(
            context = context,
            action = "test-action",
            requiredMode = GuardMode.NORMAL
        )

        assertFalse(context.startForegroundServiceCalled)
        assertFalse(started)
    }

    @Test
    fun startSafelyDoesNotStartRageModeWhenDeviceAdminPermissionIsMissing() {
        grantCoreRuntimePermissions(baseContext)
        enableAccessibilityService(baseContext)
        val context = ThrowingStartContext(baseContext)

        val started = GuardForegroundService.startSafely(
            context = context,
            action = "test-action",
            requiredMode = GuardMode.RAGE
        )

        assertFalse(context.startForegroundServiceCalled)
        assertFalse(started)
    }

    @Test
    fun serviceSettingsCollectorUsesFixedWalkingDetectorAfterPermissionGate() {
        val source = guardForegroundServiceSource().readText()
        val gateIndex = source.indexOf("if (!canRunWithCurrentPermissions(settings))")
        val detectorIndex = source.indexOf("restartWalkingDetector()")

        assertTrue(gateIndex >= 0)
        assertTrue(detectorIndex > gateIndex)
        assertTrue(source.contains("PermissionGate.canEnableGuard(status, settings.globalMode)"))
        assertFalse(source.contains("settings.walking" + "TimeoutMs"))
    }

    @Test
    fun runningServiceObservesPermissionStatusChangesWithoutStopping() {
        val source = guardForegroundServiceSource().readText()

        assertTrue(source.contains("permissionRepository.observeStatus().collectLatest"))
        assertFalse(source.contains("stopGuardForMissingPermissions()"))
        assertFalse(source.contains("private fun stopGuardForMissingPermissions()"))
        assertTrue(source.contains("applyPermissionDegrade"))
    }

    @Test
    fun permissionGatePausesWalkingAndEvaluationWithoutStopSelf() {
        val source = guardForegroundServiceSource().readText()
        val settingsCollector = source.indexOf("settingsRepository.settings.collectLatest")
        val permCollector = source.indexOf("permissionRepository.observeStatus().collectLatest")
        assertTrue(settingsCollector >= 0)
        assertTrue(permCollector > settingsCollector)

        val degradeFn = source.indexOf("private fun applyPermissionDegrade")
        assertTrue(degradeFn >= 0)
        val afterDegrade = source.indexOf("private fun ", degradeFn + 10)
        val degradeBody = source.substring(
            degradeFn,
            if (afterDegrade > degradeFn) afterDegrade else source.length
        )
        assertFalse(degradeBody.contains("stopSelf()"))
    }

    @Test
    fun settingsCollectorStopsOnlyOnUserDisable() {
        val source = guardForegroundServiceSource().readText()
        val settingsCollector = source.indexOf("settingsRepository.settings.collectLatest")
        val permCollector = source.indexOf("permissionRepository.observeStatus().collectLatest", settingsCollector)
        assertTrue(settingsCollector >= 0)
        assertTrue(permCollector > settingsCollector)
        val collectorBody = source.substring(settingsCollector, permCollector)

        val userDisableIndex = collectorBody.indexOf("if (!settings.guardEnabled)")
        assertTrue(userDisableIndex >= 0)
        assertTrue(collectorBody.indexOf("stopSelf()", userDisableIndex) > userDisableIndex)

        // Permission missing path degrades without stopSelf.
        val permissionGateIndex = collectorBody.indexOf("if (!canRunWithCurrentPermissions(settings))")
        assertTrue(permissionGateIndex > userDisableIndex)
        val degradeIndex = collectorBody.indexOf("applyPermissionDegrade()", permissionGateIndex)
        assertTrue(degradeIndex > permissionGateIndex)
        val stopAfterPermissionGate = collectorBody.indexOf("stopSelf()", permissionGateIndex)
        assertTrue(stopAfterPermissionGate < 0)
    }

    @Test
    fun evaluationRevalidatesAppSpecificEffectiveModeBeforeIntervention() {
        val source = guardForegroundServiceSource().readText()
        val appPolicyIndex = source.indexOf("appPolicyDao.getPolicy(packageName)?.policy.toAppPolicyOrNull()")
        val globalGateIndex = source.indexOf("canRunWithCurrentPermissions(currentSettings)", appPolicyIndex)
        val appGateIndex = source.indexOf("canEvaluateWithCurrentPermissions(currentSettings, appPolicy)", appPolicyIndex)
        val evaluationIndex = source.indexOf("coordinator.evaluate(", appPolicyIndex)

        assertTrue(appPolicyIndex >= 0)
        assertTrue(globalGateIndex > appPolicyIndex)
        assertTrue(appGateIndex > appPolicyIndex)
        assertTrue(evaluationIndex > globalGateIndex && evaluationIndex > appGateIndex)
        assertTrue(source.contains("PermissionGate.canRunGuardForApp(status, settings.globalMode, appPolicy)"))
    }

    @Test
    fun evaluationSkipsCycleWithoutStoppingServiceWhenPermissionsMissing() {
        val source = guardForegroundServiceSource().readText()
        val evaluateStart = source.indexOf("private fun evaluateCurrentState()")
        val evaluateEnd = source.indexOf("private fun updateInteractiveState()", evaluateStart)
        val evaluateBody = source.substring(evaluateStart, evaluateEnd)
        val gateIndex = evaluateBody.indexOf("canRunWithCurrentPermissions(currentSettings)")
        val appGateIndex = evaluateBody.indexOf("canEvaluateWithCurrentPermissions(currentSettings, appPolicy)")
        val skipReturn = evaluateBody.indexOf("return@withLock")

        assertTrue(evaluateStart >= 0)
        assertTrue(evaluateEnd > evaluateStart)
        assertTrue(gateIndex >= 0 || appGateIndex >= 0)
        assertTrue(skipReturn >= 0)
        // Global or app-specific permission gaps must not tear down the FGS / status notification.
        assertFalse(evaluateBody.contains("stopSelf()"))
        assertFalse(evaluateBody.contains("stopGuardForMissingPermissions"))
    }

    @Test
    fun statusNotificationUpdatesAreDedupedAndReassertedViaStartForeground() {
        val source = guardForegroundServiceSource().readText()
        val updateStart = source.indexOf("private fun updateStatusNotification()")
        val updateEnd = source.indexOf("private fun currentStatus()", updateStart)
        val updateBody = source.substring(updateStart, updateEnd)

        assertTrue(source.contains("lastPostedStatus"))
        assertTrue(updateBody.contains("lastPostedStatus"))
        assertTrue(updateBody.contains("startForeground("))
        assertFalse(updateBody.contains("notificationManager.updateStatusNotification("))
    }

    @Test
    fun screenStateReceiverPreservesScreenActionForCycleResetAndUsesRecoveryStart() {
        val source = screenStateReceiverSource().readText()

        // Keepalive recovery must not swallow SCREEN_ON/OFF: those actions drive
        // ScreenCycleTracker reset so "don't remind this screen-on cycle" expires.
        assertTrue(source.contains("settings.guardEnabled"))
        assertTrue(source.contains("requireFullGate = false"))
        assertTrue(source.contains("action = action"))
        assertTrue(source.contains("requiredMode = settings.globalMode"))
        assertFalse(source.contains("GuardMode.MILD"))
        // Must not replace real screen actions with a synthetic SYNC_Screen only path.
        assertFalse(source.contains("GuardSyncReason.Screen"))
        assertFalse(source.contains("syncGuardState"))
    }

    @Test
    fun foregroundServiceResetsCycleOnScreenOnOffActions() {
        val source = guardForegroundServiceSource().readText()
        assertTrue(source.contains("Intent.ACTION_SCREEN_ON -> coordinator.onScreenOn()"))
        assertTrue(source.contains("coordinator.onScreenOff()"))
        assertTrue(source.contains("Intent.ACTION_SCREEN_OFF"))
    }

    @Test
    fun serviceStarterDoesNotReferenceApi31StartExceptionDirectly() {
        val source = guardForegroundServiceSource().readText()

        assertFalse(source.contains("ForegroundServiceStartNotAllowedException"))
    }

    @Test
    fun typedHealthForegroundStartIsOnlyUsedOnApi34AndAbove() {
        val source = guardForegroundServiceSource().readText()

        assertTrue(source.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE"))
    }

    @Test
    fun applyPermissionDegradeKeepsServiceAliveAndUpdatesTracker() {
        val source = guardForegroundServiceSource().readText()
        val degradeFn = source.indexOf("private fun applyPermissionDegrade")
        assertTrue(degradeFn >= 0)
        val afterDegrade = source.indexOf("private fun ", degradeFn + 10)
        val degradeBody = source.substring(
            degradeFn,
            if (afterDegrade > degradeFn) afterDegrade else source.length
        )

        assertTrue(degradeBody.contains("walkingDetector?.stop()"))
        assertTrue(degradeBody.contains("currentWalking = false"))
        assertTrue(degradeBody.contains("GuardRuntimeStatusTracker.update"))
        assertFalse(degradeBody.contains("stopSelf()"))
        assertFalse(source.contains("private fun stopGuardForMissingPermissions()"))
    }

    @Test
    fun screenReceiverUsesNotExportedRegistrationOnApi33Plus() {
        val source = guardForegroundServiceSource().readText()

        assertTrue(source.contains("ContextCompat.registerReceiver"))
        assertTrue(source.contains("RECEIVER_NOT_EXPORTED"))
    }

    @Test
    fun onCreateStopsWhenForegroundStartFails() {
        val source = guardForegroundServiceSource().readText()
        val onCreateIndex = source.indexOf("override fun onCreate()")
        val failIndex = source.indexOf("if (!startInForegroundSafely())", onCreateIndex)
        assertTrue(onCreateIndex >= 0)
        assertTrue(failIndex > onCreateIndex)

        val launchIndex = source.indexOf("serviceScope.launch", failIndex)
        val failBody = source.substring(
            failIndex,
            if (launchIndex > failIndex) launchIndex else failIndex + 200
        )
        assertTrue(failBody.contains("stopSelf()"))
        assertTrue(failBody.contains("return"))
    }

    @Test
    fun onStartCommandAlwaysRefreshesInteractiveStateAfterScreenBroadcasts() {
        val source = guardForegroundServiceSource().readText()
        val onStartIndex = source.indexOf("override fun onStartCommand")
        val userPresentIndex = source.indexOf("Intent.ACTION_USER_PRESENT", onStartIndex)
        val updateIndex = source.indexOf("updateInteractiveState()", userPresentIndex)

        assertTrue(onStartIndex >= 0)
        assertTrue(userPresentIndex > onStartIndex)
        assertTrue(updateIndex > userPresentIndex)
    }

    private fun clearSharedPermissionState() {
        disableAccessibilityService(baseContext)
        ShadowSettings.setCanDrawOverlays(false)
        shadowOf(baseContext).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        shadowOf(baseContext).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager()).setNotificationsEnabled(true)
    }

    private fun grantCoreRuntimePermissions(context: Application) {
        shadowOf(context).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun enableAccessibilityService(context: Context) {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/.guard.WalkAccessibilityService"
        )
    }

    private fun disableAccessibilityService(context: Context) {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
    }

    private fun notificationManager(): NotificationManager {
        return baseContext.getSystemService(NotificationManager::class.java)
    }

    private fun guardForegroundServiceSource(): File {
        val candidates = listOf(
            File("src/main/java/org/walkguard/app/guard/GuardForegroundService.kt"),
            File("app/src/main/java/org/walkguard/app/guard/GuardForegroundService.kt")
        )
        return candidates.first { it.exists() }
    }

    private fun screenStateReceiverSource(): File {
        val candidates = listOf(
            File("src/main/java/org/walkguard/app/guard/ScreenStateReceiver.kt"),
            File("app/src/main/java/org/walkguard/app/guard/ScreenStateReceiver.kt")
        )
        return candidates.first { it.exists() }
    }

    private class ThrowingStartContext(base: Context) : ContextWrapper(base) {
        var startForegroundServiceCalled = false

        override fun startForegroundService(service: Intent?): ComponentName? {
            startForegroundServiceCalled = true
            throw SecurityException("missing foreground service prerequisite")
        }
    }
}
