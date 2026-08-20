package org.walkguard.app.permissions

import android.content.Context
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.ui.i18n.formatMissingPermissionsError

enum class MissingPermission {
    ACCESSIBILITY,
    NOTIFICATIONS,
    ACTIVITY_RECOGNITION,
    OVERLAY,
    DEVICE_ADMIN
}

data class PermissionGateResult(
    val allowed: Boolean,
    val missing: List<MissingPermission>
)

object PermissionGate {
    fun canEnableGuard(status: PermissionStatus, mode: GuardMode): PermissionGateResult {
        return resultFor(requiredGuardPermissions(status) + requiredModePermissions(status, mode))
    }

    fun canUseMode(mode: GuardMode, status: PermissionStatus): PermissionGateResult {
        return resultFor(requiredModePermissions(status, mode))
    }

    fun canRunGuardForApp(
        status: PermissionStatus,
        globalMode: GuardMode,
        appPolicy: AppPolicy?
    ): PermissionGateResult {
        val policy = appPolicy ?: AppPolicy.INHERIT
        val mode = when (policy) {
            AppPolicy.INHERIT -> globalMode
            AppPolicy.WHITELIST -> null
            AppPolicy.MILD -> GuardMode.MILD
            AppPolicy.NORMAL -> GuardMode.NORMAL
            AppPolicy.RAGE -> GuardMode.RAGE
        }
        return resultFor(requiredGuardPermissions(status) + modeSpecificMissingPermissions(status, mode))
    }

    fun enforceCanEnableGuard(status: PermissionStatus, mode: GuardMode, context: Context) {
        val result = canEnableGuard(status, mode)
        if (!result.allowed) {
            error(context.formatMissingPermissionsError(result.missing))
        }
    }

    private fun requiredGuardPermissions(status: PermissionStatus): List<MissingPermission> = buildList {
        if (!status.accessibilityEnabled) add(MissingPermission.ACCESSIBILITY)
        if (!status.notificationsGranted) add(MissingPermission.NOTIFICATIONS)
        if (!status.activityRecognitionGranted) add(MissingPermission.ACTIVITY_RECOGNITION)
    }

    private fun requiredModePermissions(status: PermissionStatus, mode: GuardMode): List<MissingPermission> {
        return modeSpecificMissingPermissions(status, mode)
    }

    private fun modeSpecificMissingPermissions(status: PermissionStatus, mode: GuardMode?): List<MissingPermission> = buildList {
        when (mode) {
            null,
            GuardMode.MILD -> Unit
            GuardMode.NORMAL -> if (!status.overlayGranted) add(MissingPermission.OVERLAY)
            GuardMode.RAGE -> if (!status.deviceAdminEnabled) add(MissingPermission.DEVICE_ADMIN)
        }
    }

    private fun resultFor(missing: List<MissingPermission>): PermissionGateResult {
        return PermissionGateResult(
            allowed = missing.isEmpty(),
            missing = missing.distinct()
        )
    }
}