package org.walkguard.app.permissions

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.walkguard.app.guard.DeviceAdminLockReceiver

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class PermissionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val repository = PermissionRepository(context)

    @Before
    fun setUp() {
        clearSharedState()
    }

    @After
    fun tearDown() {
        clearSharedState()
    }

    @Test
    fun currentStatusReportsAccessibilityAndRuntimePermissionState() {
        enableAccessibilityService()
        grantRuntimePermission(Manifest.permission.ACTIVITY_RECOGNITION)

        val status = repository.currentStatus()

        assertTrue(status.accessibilityEnabled)
        assertTrue(status.activityRecognitionGranted)
    }

    @Test
    fun currentStatusTreatsMissingAccessibilityServiceAsDisabled() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.example/.OtherService"
        )

        assertFalse(repository.currentStatus().accessibilityEnabled)
    }

    @Test
    fun currentStatusReportsNotificationRuntimeAndAppNotificationState() {
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        setAppNotificationsEnabled(false)

        assertFalse(repository.currentStatus().notificationsGranted)

        setAppNotificationsEnabled(true)
        assertTrue(repository.currentStatus().notificationsGranted)

        denyRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(repository.currentStatus().notificationsGranted)
    }

    @Test
    fun currentStatusReportsOverlayDeviceAdminAndBatteryOptimizationState() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(repository.currentStatus().overlayGranted)

        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(repository.currentStatus().overlayGranted)

        assertFalse(repository.currentStatus().deviceAdminEnabled)
        shadowOf(devicePolicyManager()).setActiveAdmin(deviceAdminComponent())
        assertTrue(repository.currentStatus().deviceAdminEnabled)

        assertFalse(repository.currentStatus().ignoringBatteryOptimizations)
        shadowOf(powerManager()).setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(repository.currentStatus().ignoringBatteryOptimizations)
    }

    @Test
    fun observeStatusPollsPermissionChanges() = runBlocking {
        val pollingRepository = PermissionRepository(context, pollIntervalMs = 25L)
        val observed = async {
            withTimeout(500L) {
                pollingRepository.observeStatus().take(2).toList()
            }
        }

        delay(5L)
        enableAccessibilityService()

        val statuses = observed.await()

        assertFalse(statuses.first().accessibilityEnabled)
        assertTrue(statuses.last().accessibilityEnabled)
    }

    @Test
    fun intentBuildersOpenRequiredSystemScreens() {
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, repository.accessibilitySettingsIntent().action)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, repository.overlaySettingsIntent().action)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, repository.notificationSettingsIntent().action)
        assertEquals(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            repository.batteryOptimizationSettingsIntent().action
        )
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, repository.usageAccessSettingsIntent().action)

        val deviceAdminIntent = repository.deviceAdminActivationIntent()
        val component = deviceAdminIntent.getParcelableExtra(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            ComponentName::class.java
        )

        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, deviceAdminIntent.action)
        assertEquals(ComponentName(context, DeviceAdminLockReceiver::class.java), component)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            repository.accessibilitySettingsIntent().flags and Intent.FLAG_ACTIVITY_NEW_TASK
        )
        assertEquals(Uri.parse("package:${context.packageName}"), repository.overlaySettingsIntent().data)
    }

    private fun clearSharedState() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
        ShadowSettings.setCanDrawOverlays(false)
        setAppNotificationsEnabled(true)
        denyRuntimePermission(Manifest.permission.ACTIVITY_RECOGNITION)
        denyRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(powerManager()).setIgnoringBatteryOptimizations(context.packageName, false)
    }

    private fun enableAccessibilityService() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/.guard.WalkAccessibilityService"
        )
    }

    private fun grantRuntimePermission(permission: String) {
        shadowOf(context).grantPermissions(permission)
    }

    private fun denyRuntimePermission(permission: String) {
        shadowOf(context).denyPermissions(permission)
    }

    private fun setAppNotificationsEnabled(enabled: Boolean) {
        shadowOf(notificationManager()).setNotificationsEnabled(enabled)
    }

    private fun notificationManager(): NotificationManager {
        return context.getSystemService(NotificationManager::class.java)
    }

    private fun devicePolicyManager(): DevicePolicyManager {
        return context.getSystemService(DevicePolicyManager::class.java)
    }

    private fun powerManager(): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private fun deviceAdminComponent(): ComponentName {
        return ComponentName(context, DeviceAdminLockReceiver::class.java)
    }
}
