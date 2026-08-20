package org.walkguard.app.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.walkguard.app.WalkGuardTestContext
import org.walkguard.app.R
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PermissionGateTest {
    @Test
    fun guardEnableRequiresAccessibilityNotificationsAndActivityRecognition() {
        val status = permissionStatus(
            accessibilityEnabled = false,
            notificationsGranted = false,
            activityRecognitionGranted = false,
            overlayGranted = true,
            deviceAdminEnabled = true
        )

        val result = PermissionGate.canEnableGuard(status, GuardMode.MILD)

        assertFalse(result.allowed)
        assertEquals(
            setOf(
                MissingPermission.ACCESSIBILITY,
                MissingPermission.NOTIFICATIONS,
                MissingPermission.ACTIVITY_RECOGNITION
            ),
            result.missing.toSet()
        )
    }

    @Test
    fun normalModeRequiresOverlayPermission() {
        val result = PermissionGate.canUseMode(
            GuardMode.NORMAL,
            permissionStatus(overlayGranted = false)
        )

        assertFalse(result.allowed)
        assertEquals(listOf(MissingPermission.OVERLAY), result.missing)
    }

    @Test
    fun rageModeRequiresDeviceAdmin() {
        val result = PermissionGate.canUseMode(
            GuardMode.RAGE,
            permissionStatus(deviceAdminEnabled = false)
        )

        assertFalse(result.allowed)
        assertEquals(listOf(MissingPermission.DEVICE_ADMIN), result.missing)
    }

    @Test
    fun mildModeRequiresNoModeSpecificPermission() {
        val result = PermissionGate.canUseMode(
            GuardMode.MILD,
            permissionStatus(overlayGranted = false, deviceAdminEnabled = false)
        )

        assertTrue(result.allowed)
        assertEquals(emptyList<MissingPermission>(), result.missing)
    }

    @Test
    fun enforceCanEnableGuardThrowsWhenRequiredPermissionIsMissing() {
        val exception = assertThrows(IllegalStateException::class.java) {
            PermissionGate.enforceCanEnableGuard(
                permissionStatus(activityRecognitionGranted = false),
                GuardMode.MILD,
                WalkGuardTestContext.appContext
            )
        }

        assertTrue(
            exception.message.orEmpty().contains(
                WalkGuardTestContext.appContext.getString(R.string.missing_permission_activity_recognition)
            )
        )
    }

    @Test
    fun appSpecificNormalRequiresOverlayEvenWhenGlobalMildIsAllowed() {
        val result = PermissionGate.canRunGuardForApp(
            status = permissionStatus(overlayGranted = false),
            globalMode = GuardMode.MILD,
            appPolicy = AppPolicy.NORMAL
        )

        assertFalse(result.allowed)
        assertEquals(listOf(MissingPermission.OVERLAY), result.missing)
    }

    @Test
    fun appSpecificRageRequiresDeviceAdminEvenWhenGlobalMildIsAllowed() {
        val result = PermissionGate.canRunGuardForApp(
            status = permissionStatus(deviceAdminEnabled = false),
            globalMode = GuardMode.MILD,
            appPolicy = AppPolicy.RAGE
        )

        assertFalse(result.allowed)
        assertEquals(listOf(MissingPermission.DEVICE_ADMIN), result.missing)
    }

    @Test
    fun whitelistedAppDoesNotRequireModeSpecificPermission() {
        val result = PermissionGate.canRunGuardForApp(
            status = permissionStatus(overlayGranted = false, deviceAdminEnabled = false),
            globalMode = GuardMode.RAGE,
            appPolicy = AppPolicy.WHITELIST
        )

        assertTrue(result.allowed)
        assertEquals(emptyList<MissingPermission>(), result.missing)
    }

    private fun permissionStatus(
        accessibilityEnabled: Boolean = true,
        notificationsGranted: Boolean = true,
        activityRecognitionGranted: Boolean = true,
        overlayGranted: Boolean = true,
        deviceAdminEnabled: Boolean = true,
        ignoringBatteryOptimizations: Boolean = true
    ) = PermissionStatus(
        accessibilityEnabled = accessibilityEnabled,
        notificationsGranted = notificationsGranted,
        activityRecognitionGranted = activityRecognitionGranted,
        overlayGranted = overlayGranted,
        deviceAdminEnabled = deviceAdminEnabled,
        ignoringBatteryOptimizations = ignoringBatteryOptimizations
    )
}
