package org.walkguard.app.permissions

data class PermissionStatus(
    val accessibilityEnabled: Boolean,
    val notificationsGranted: Boolean,
    val activityRecognitionGranted: Boolean,
    val overlayGranted: Boolean,
    val deviceAdminEnabled: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val usageStatsGranted: Boolean = false
)
