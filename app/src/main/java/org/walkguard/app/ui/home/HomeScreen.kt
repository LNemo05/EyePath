package org.walkguard.app.ui.home

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.walkguard.app.R
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.data.settings.GuardSettings
import org.walkguard.app.data.settings.SettingsRepository
import org.walkguard.app.guard.ForegroundAppTracker
import org.walkguard.app.guard.GuardForegroundService
import org.walkguard.app.guard.GuardRuntimeStatusTracker
import org.walkguard.app.guard.readDeviceInteractiveState
import org.walkguard.app.permissions.PermissionGate
import org.walkguard.app.permissions.PermissionRepository
import org.walkguard.app.ui.components.AppleCard
import org.walkguard.app.ui.components.AppleSecondaryButton
import org.walkguard.app.ui.components.AppleTextField
import org.walkguard.app.ui.components.CardTitle
import org.walkguard.app.ui.components.HeroCard
import org.walkguard.app.ui.components.LargeTitle
import org.walkguard.app.ui.components.ModeOptionCard
import org.walkguard.app.ui.components.PillChip
import org.walkguard.app.ui.components.ScreenContentPadding
import org.walkguard.app.ui.components.ScreenSubtitle
import org.walkguard.app.ui.components.StatusBadge
import org.walkguard.app.ui.components.StatusTile
import org.walkguard.app.ui.i18n.formatBoolean
import org.walkguard.app.ui.i18n.guardModeDescription
import org.walkguard.app.ui.i18n.guardModeLabel
import org.walkguard.app.ui.i18n.missingPermissionsSummary
import org.walkguard.app.ui.theme.AppleBlue
import org.walkguard.app.ui.theme.AppleGreen
import org.walkguard.app.ui.theme.AppleOrange
import org.walkguard.app.ui.theme.AppleRed
import org.walkguard.app.ui.theme.AppleSecondary
import org.walkguard.app.ui.theme.AppleText

@Composable
fun HomeScreen(
    settings: GuardSettings,
    settingsRepository: SettingsRepository,
    permissionRepository: PermissionRepository,
    context: Context,
    onNavigateToPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val foregroundPackage = ForegroundAppTracker.currentPackageFlow.collectAsState(initial = null).value
    val runtimeStatus = GuardRuntimeStatusTracker.statusFlow.collectAsState(
        initial = GuardRuntimeStatusTracker.Status()
    ).value
    val permissionStatus = permissionRepository.observeStatus().collectAsState(
        initial = permissionRepository.currentStatus()
    ).value
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var customPauseMinutes by remember { mutableStateOf("") }
    var selectedPauseMinutes by remember { mutableStateOf<Int?>(null) }
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val gateResult = PermissionGate.canEnableGuard(permissionStatus, settings.globalMode)
    val unknownLabel = stringResource(R.string.value_unknown)
    val invalidPauseMessage = stringResource(R.string.home_pause_invalid)
    val tooLargePauseMessage = stringResource(R.string.home_pause_too_large)
    var deviceInteractive by remember {
        mutableStateOf(context.readDeviceInteractiveState())
    }
    val isPaused = nowEpochMs < settings.pauseUntilEpochMs
    val remainingPauseMinutes = if (isPaused) {
        ((settings.pauseUntilEpochMs - nowEpochMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    } else {
        0
    }
    DisposableEffect(settings.guardEnabled, settings.pauseUntilEpochMs) {
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                nowEpochMs = System.currentTimeMillis()
                if (settings.guardEnabled) {
                    deviceInteractive = context.readDeviceInteractiveState()
                }
                handler.postDelayed(this, 1_000L)
            }
        }
        nowEpochMs = System.currentTimeMillis()
        if (settings.guardEnabled) {
            deviceInteractive = context.readDeviceInteractiveState()
        }
        handler.post(tick)
        onDispose { handler.removeCallbacks(tick) }
    }
    val serviceReporting = runtimeStatus.screenOn || runtimeStatus.unlocked || runtimeStatus.walking
    val heroDescription = buildString {
        append(guardModeLabel(settings.globalMode))
        append(" · ")
        if (isPaused) {
            append(stringResource(R.string.home_pause_active, remainingPauseMinutes))
            append(" · ")
        }
        if (gateResult.allowed) {
            append(stringResource(R.string.home_permission_ready))
        } else {
            append(
                stringResource(
                    R.string.home_permission_missing,
                    missingPermissionsSummary(gateResult.missing)
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LargeTitle(stringResource(R.string.app_name))
        ScreenSubtitle(stringResource(R.string.home_tagline))

        HeroCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = stringResource(R.string.home_guard_title).uppercase(),
                        color = AppleBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )
                    // Visual ON only when permissions allow full enable; matches switchChecked below.
                    val guardLooksOn = settings.guardEnabled && gateResult.allowed
                    Text(
                        text = stringResource(
                            if (guardLooksOn) R.string.home_guard_enabled else R.string.home_guard_disabled
                        ),
                        color = AppleText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = heroDescription,
                        color = AppleSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                // Permission-incomplete: always show OFF. Turning on routes to Permissions tab
                // instead of leaving a disabled/degraded "on" state.
                val switchChecked = settings.guardEnabled && gateResult.allowed
                Switch(
                    checked = switchChecked,
                    enabled = true,
                    onCheckedChange = { enabled ->
                        if (enabled && !gateResult.allowed) {
                            onNavigateToPermissions()
                            return@Switch
                        }
                        scope.launch {
                            errorMessage = runCatching {
                                if (enabled) {
                                    settingsRepository.setGuardEnabled(true)
                                    val started = GuardForegroundService.startSafely(
                                        context = context,
                                        requiredMode = settings.globalMode
                                    )
                                    if (!started) {
                                        // Keep guardEnabled; recovery entries (Tile/Boot/Syncer) will retry.
                                        error(context.getString(R.string.error_start_service))
                                    }
                                } else {
                                    settingsRepository.setGuardEnabled(false)
                                    context.stopService(Intent(context, GuardForegroundService::class.java))
                                    GuardRuntimeStatusTracker.clear()
                                }
                            }.exceptionOrNull()?.message
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                        checkedTrackColor = AppleGreen,
                        uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                        uncheckedTrackColor = org.walkguard.app.ui.theme.AppleFillStrong
                    )
                )
            }
            errorMessage?.let {
                Text(
                    text = stringResource(R.string.home_last_action_failed, it),
                    color = AppleRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        AppleCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardTitle(stringResource(R.string.home_realtime_status))
                StatusBadge(
                    text = stringResource(
                        if (settings.guardEnabled && serviceReporting) {
                            R.string.home_service_online
                        } else if (settings.guardEnabled) {
                            R.string.home_service_offline
                        } else {
                            R.string.home_guard_disabled
                        }
                    ),
                    ok = settings.guardEnabled && serviceReporting
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (settings.guardEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusTile(
                            label = stringResource(R.string.home_status_walking),
                            value = formatBoolean(runtimeStatus.walking),
                            valueColor = if (runtimeStatus.walking) AppleOrange else AppleGreen,
                            modifier = Modifier.weight(1f)
                        )
                        StatusTile(
                            label = stringResource(R.string.home_status_screen),
                            value = formatBoolean(deviceInteractive.screenOn),
                            valueColor = if (deviceInteractive.screenOn) AppleGreen else AppleSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusTile(
                            label = stringResource(R.string.home_status_unlocked),
                            value = formatBoolean(deviceInteractive.unlocked),
                            valueColor = if (deviceInteractive.unlocked) AppleGreen else AppleSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatusTile(
                            label = stringResource(R.string.home_status_foreground),
                            value = foregroundPackage ?: unknownLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (gateResult.allowed && !serviceReporting) {
                        Text(
                            text = stringResource(R.string.home_service_not_reporting),
                            color = AppleRed,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_runtime_unavailable),
                        color = AppleSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(
                            R.string.home_foreground_app,
                            foregroundPackage ?: unknownLabel
                        ),
                        color = AppleSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        AppleCard {
            CardTitle(
                text = stringResource(R.string.settings_global_mode),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuardMode.entries.forEach { mode ->
                    ModeOptionCard(
                        title = guardModeLabel(mode),
                        description = guardModeDescription(mode),
                        selected = settings.globalMode == mode,
                        onClick = {
                            scope.launch {
                                errorMessage = runCatching {
                                    settingsRepository.setGlobalMode(mode)
                                }.exceptionOrNull()?.message
                            }
                        }
                    )
                }
            }
        }

        AppleCard {
            CardTitle(
                text = stringResource(R.string.home_pause_title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isPaused) {
                    Text(
                        text = stringResource(R.string.home_pause_active, remainingPauseMinutes),
                        color = AppleOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    AppleSecondaryButton(
                        text = stringResource(R.string.home_pause_resume),
                        onClick = {
                            selectedPauseMinutes = null
                            scope.launch {
                                errorMessage = runCatching {
                                    // Resume immediately by clearing future pause deadline.
                                    settingsRepository.setPauseUntilEpochMs(System.currentTimeMillis())
                                }.exceptionOrNull()?.message
                            }
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15).forEach { minutes ->
                        PillChip(
                            text = stringResource(R.string.home_pause_minutes, minutes),
                            selected = selectedPauseMinutes == minutes,
                            onClick = {
                                selectedPauseMinutes = minutes
                                scope.launch {
                                    val pauseUntil = System.currentTimeMillis() + minutes * 60_000L
                                    errorMessage = runCatching {
                                        settingsRepository.setPauseUntilEpochMs(pauseUntil)
                                    }.exceptionOrNull()?.message
                                }
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppleTextField(
                        value = customPauseMinutes,
                        onValueChange = {
                            customPauseMinutes = it
                            selectedPauseMinutes = null
                        },
                        placeholder = stringResource(R.string.home_pause_custom_minutes),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    AppleSecondaryButton(
                        text = stringResource(R.string.home_pause_apply),
                        onClick = {
                            when (val result = calculatePauseDeadline(customPauseMinutes, System.currentTimeMillis())) {
                                is PauseDeadlineResult.Valid -> scope.launch {
                                    errorMessage = runCatching {
                                        settingsRepository.setPauseUntilEpochMs(result.deadlineEpochMs)
                                    }.exceptionOrNull()?.message
                                }
                                PauseDeadlineResult.Invalid -> errorMessage = invalidPauseMessage
                                PauseDeadlineResult.TooLarge -> errorMessage = tooLargePauseMessage
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.home_permission_setup_hint),
            color = AppleSecondary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}
