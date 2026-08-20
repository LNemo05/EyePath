package org.walkguard.app.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.walkguard.app.R
import org.walkguard.app.permissions.PermissionRepository
import org.walkguard.app.ui.components.Chevron
import org.walkguard.app.ui.components.GroupedList
import org.walkguard.app.ui.components.LargeTitle
import org.walkguard.app.ui.components.ListRow
import org.walkguard.app.ui.components.ScreenContentPadding
import org.walkguard.app.ui.components.ScreenSubtitle
import org.walkguard.app.ui.components.SecondaryValue
import org.walkguard.app.ui.components.StatusBadge
import org.walkguard.app.ui.theme.AppleBlue
import org.walkguard.app.ui.theme.AppleSecondary

@Composable
fun PermissionsScreen(
    context: Context,
    permissionRepository: PermissionRepository,
    modifier: Modifier = Modifier
) {
    val status = permissionRepository.observeStatus().collectAsState(
        initial = permissionRepository.currentStatus()
    ).value
    val hostContext = LocalContext.current

    fun launchSettingsIntent(intent: Intent, preferActivity: Boolean = false) {
        val activity = hostContext as? Activity
        if (preferActivity && activity != null) {
            activity.startActivity(intent)
        } else {
            hostContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenContentPadding)
    ) {
        LargeTitle(stringResource(R.string.permissions_title))
        ScreenSubtitle(stringResource(R.string.permissions_stop_walking_first))

        GroupedList {
            PermissionListRow(
                title = stringResource(R.string.permission_accessibility),
                subtitle = stringResource(R.string.permission_accessibility_hint),
                granted = status.accessibilityEnabled,
                showDivider = false,
                onOpenSettings = { launchSettingsIntent(permissionRepository.accessibilitySettingsIntent()) }
            )
            PermissionListRow(
                title = stringResource(R.string.permission_notifications),
                subtitle = stringResource(R.string.permission_notifications_hint),
                granted = status.notificationsGranted,
                showDivider = true,
                onOpenSettings = { launchSettingsIntent(permissionRepository.notificationSettingsIntent()) }
            )
            PermissionListRow(
                title = stringResource(R.string.permission_activity_recognition),
                subtitle = stringResource(R.string.permission_activity_recognition_hint),
                granted = status.activityRecognitionGranted,
                showDivider = true,
                detail = Manifest.permission.ACTIVITY_RECOGNITION,
                onOpenSettings = {
                    launchSettingsIntent(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionListRow(
                    title = stringResource(R.string.permission_runtime_notifications),
                    subtitle = stringResource(R.string.permission_notifications_hint),
                    granted = status.notificationsGranted,
                    showDivider = true,
                    detail = Manifest.permission.POST_NOTIFICATIONS,
                    onOpenSettings = {
                        launchSettingsIntent(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
            }
            PermissionListRow(
                title = stringResource(R.string.permission_overlay),
                subtitle = stringResource(R.string.permission_overlay_hint),
                granted = status.overlayGranted,
                showDivider = true,
                onOpenSettings = { launchSettingsIntent(permissionRepository.overlaySettingsIntent()) }
            )
            PermissionListRow(
                title = stringResource(R.string.permission_device_admin),
                subtitle = stringResource(R.string.permission_device_admin_detail),
                granted = status.deviceAdminEnabled,
                showDivider = true,
                onOpenSettings = {
                    launchSettingsIntent(
                        permissionRepository.deviceAdminActivationIntent(),
                        preferActivity = true
                    )
                }
            )
            PermissionListRow(
                title = stringResource(R.string.permission_battery),
                subtitle = stringResource(R.string.permission_battery_hint),
                granted = status.ignoringBatteryOptimizations,
                showDivider = true,
                onOpenSettings = { launchSettingsIntent(permissionRepository.batteryOptimizationSettingsIntent()) }
            )
            PermissionListRow(
                title = stringResource(R.string.permission_usage_stats),
                subtitle = stringResource(R.string.permission_usage_stats_detail),
                granted = status.usageStatsGranted,
                showDivider = true,
                onOpenSettings = { launchSettingsIntent(permissionRepository.usageAccessSettingsIntent()) }
            )
        }

        Text(
            text = stringResource(R.string.permissions_guard_may_stop),
            color = AppleSecondary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun PermissionListRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    showDivider: Boolean,
    onOpenSettings: () -> Unit,
    detail: String? = null
) {
    ListRow(
        title = title,
        subtitle = buildString {
            append(subtitle)
            if (!detail.isNullOrBlank()) {
                append('\n')
                append(detail)
            }
        },
        showDivider = showDivider,
        // Always keep settings entrypoints clickable so users can revoke/adjust after grant.
        onClick = onOpenSettings,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    text = stringResource(
                        if (granted) R.string.permission_granted else R.string.permission_missing
                    ),
                    ok = granted
                )
                if (!granted) {
                    SecondaryValue(
                        text = stringResource(R.string.action_go_to_settings),
                        color = AppleBlue,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Chevron(modifier = Modifier.padding(start = 4.dp))
            }
        }
    )
}
