package org.walkguard.app.ui.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import org.walkguard.app.R
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.permissions.MissingPermission

@Composable
@ReadOnlyComposable
fun guardModeLabel(mode: GuardMode): String = stringResource(mode.labelRes())

@Composable
@ReadOnlyComposable
fun guardModeDescription(mode: GuardMode): String = stringResource(mode.descriptionRes())

@Composable
@ReadOnlyComposable
fun appPolicyLabel(policy: AppPolicy): String = stringResource(policy.labelRes())

@Composable
@ReadOnlyComposable
fun missingPermissionLabel(permission: MissingPermission): String = stringResource(permission.labelRes())

@Composable
@ReadOnlyComposable
fun missingPermissionsSummary(missing: List<MissingPermission>): String {
    val parts = mutableListOf<String>()
    for (permission in missing) {
        parts.add(missingPermissionLabel(permission))
    }
    return parts.joinToString(", ")
}

@StringRes
fun GuardMode.labelRes(): Int = when (this) {
    GuardMode.MILD -> R.string.guard_mode_mild
    GuardMode.NORMAL -> R.string.guard_mode_normal
    GuardMode.RAGE -> R.string.guard_mode_rage
}

@StringRes
fun GuardMode.descriptionRes(): Int = when (this) {
    GuardMode.MILD -> R.string.guard_mode_mild_desc
    GuardMode.NORMAL -> R.string.guard_mode_normal_desc
    GuardMode.RAGE -> R.string.guard_mode_rage_desc
}

@StringRes
fun AppPolicy.labelRes(): Int = when (this) {
    AppPolicy.INHERIT -> R.string.app_policy_inherit
    AppPolicy.WHITELIST -> R.string.app_policy_whitelist
    AppPolicy.MILD -> R.string.app_policy_mild
    AppPolicy.NORMAL -> R.string.app_policy_normal
    AppPolicy.RAGE -> R.string.app_policy_rage
}


fun MissingPermission.labelRes(): Int = when (this) {
    MissingPermission.ACCESSIBILITY -> R.string.missing_permission_accessibility
    MissingPermission.NOTIFICATIONS -> R.string.missing_permission_notifications
    MissingPermission.ACTIVITY_RECOGNITION -> R.string.missing_permission_activity_recognition
    MissingPermission.OVERLAY -> R.string.missing_permission_overlay
    MissingPermission.DEVICE_ADMIN -> R.string.missing_permission_device_admin
}

fun GuardMode.localizedLabel(context: Context): String = context.getString(labelRes())

fun AppPolicy.localizedLabel(context: Context): String = context.getString(labelRes())

fun MissingPermission.localizedLabel(context: Context): String = context.getString(labelRes())

fun List<MissingPermission>.localizedSummary(context: Context): String =
    joinToString { it.localizedLabel(context) }

fun Context.formatMissingPermissionsError(missing: List<MissingPermission>): String =
    getString(R.string.error_missing_permissions, missing.localizedSummary(this))

fun Context.defaultWarningTitle(): String = getString(R.string.default_warning_title)

fun Context.defaultWarningMessage(): String = getString(R.string.default_warning_message)

fun Context.formatDetectionState(
    screenOn: Boolean,
    unlocked: Boolean,
    walking: Boolean,
    foregroundApp: String?
): String = getString(
    R.string.detection_state_format,
    getString(if (screenOn) R.string.value_true else R.string.value_false),
    getString(if (unlocked) R.string.value_true else R.string.value_false),
    getString(if (walking) R.string.value_true else R.string.value_false),
    foregroundApp?.takeIf { it.isNotBlank() } ?: getString(R.string.value_unknown)
)

fun Context.formatGuardStatusNotificationText(mode: GuardMode, detectionState: String): String =
    getString(R.string.notification_status_content, mode.localizedLabel(this), detectionState)

@Composable
@ReadOnlyComposable
fun formatBoolean(value: Boolean): String =
    stringResource(if (value) R.string.value_true else R.string.value_false)