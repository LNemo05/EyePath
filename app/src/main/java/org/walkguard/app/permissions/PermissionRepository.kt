package org.walkguard.app.permissions

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.walkguard.app.R
import org.walkguard.app.guard.DeviceAdminLockReceiver
import org.walkguard.app.guard.UsageStatsRecentForegroundSource
import org.walkguard.app.guard.WalkAccessibilityService

class PermissionRepository(
    private val context: Context,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    private val appContext = context.applicationContext
    private val usageStatsSource by lazy { UsageStatsRecentForegroundSource(appContext) }

    fun observeStatus(): Flow<PermissionStatus> = flow {
        while (true) {
            emit(currentStatus())
            delay(pollIntervalMs)
        }
    }

    fun currentStatus(): PermissionStatus {
        return PermissionStatus(
            accessibilityEnabled = isAccessibilityServiceEnabled(),
            notificationsGranted = areNotificationsGranted(),
            activityRecognitionGranted = isActivityRecognitionGranted(),
            overlayGranted = Settings.canDrawOverlays(appContext),
            deviceAdminEnabled = isDeviceAdminEnabled(),
            ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
            usageStatsGranted = usageStatsSource.isGranted()
        )
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).asNewTask()

    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
    }.asNewTask()

    fun overlaySettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${appContext.packageName}")
    ).asNewTask()

    fun batteryOptimizationSettingsIntent(): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${appContext.packageName}")
    ).asNewTask()

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).asNewTask()

    fun deviceAdminActivationIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                appContext.getString(R.string.device_admin_activation_explanation)
            )
        }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(appContext, WalkAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabledServices.split(':').any { service ->
            val component = ComponentName.unflattenFromString(service) ?: return@any false
            component == expected ||
                component.packageName == expected.packageName && component.className == expected.className
        }
    }

    private fun areNotificationsGranted(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return notificationsEnabled && runtimeGranted
    }

    private fun isActivityRecognitionGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            appContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching { devicePolicyManager.isAdminActive(deviceAdminComponent()) }.getOrDefault(false)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { powerManager.isIgnoringBatteryOptimizations(appContext.packageName) }.getOrDefault(false)
    }

    private fun deviceAdminComponent(): ComponentName {
        return ComponentName(appContext, DeviceAdminLockReceiver::class.java)
    }

    private fun Intent.asNewTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 1000L
    }
}
