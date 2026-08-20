package org.walkguard.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.walkguard.app.R
import org.walkguard.app.data.settings.GuardSettings
import org.walkguard.app.data.settings.SettingsRepository
import org.walkguard.app.permissions.PermissionRepository
import org.walkguard.app.ui.components.ApplePrimaryButton
import org.walkguard.app.ui.components.AppleTextField
import org.walkguard.app.ui.components.Chevron
import org.walkguard.app.ui.components.FooterNote
import org.walkguard.app.ui.components.GroupedList
import org.walkguard.app.ui.components.LargeTitle
import org.walkguard.app.ui.components.ListRow
import org.walkguard.app.ui.components.PolicyDropdown
import org.walkguard.app.ui.components.ScreenContentPadding
import org.walkguard.app.ui.components.SectionLabel
import org.walkguard.app.ui.i18n.WalkGuardAppLanguage
import org.walkguard.app.ui.i18n.applyWalkGuardAppLanguage
import org.walkguard.app.ui.i18n.labelRes
import org.walkguard.app.ui.theme.AppleFillStrong
import org.walkguard.app.ui.theme.AppleGreen
import org.walkguard.app.ui.theme.AppleRed
import org.walkguard.app.ui.theme.AppleSecondary
import org.walkguard.app.ui.theme.AppleText
import org.walkguard.app.update.GitHubUpdateChecker
import org.walkguard.app.update.UpdateCheckResult
import org.walkguard.app.update.WalkGuardLinks

@Composable
fun SettingsScreen(
    settings: GuardSettings,
    settingsRepository: SettingsRepository,
    permissionRepository: PermissionRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var warningTitle by remember(settings.warningTitle) { mutableStateOf(settings.warningTitle) }
    var warningMessage by remember(settings.warningMessage) { mutableStateOf(settings.warningMessage) }
    var showExcludeFromRecentsConfirm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        errorMessage = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.exceptionOrNull()?.let {
            context.getString(R.string.settings_open_link_failed)
        }
    }

    if (showExcludeFromRecentsConfirm) {
        AlertDialog(
            onDismissRequest = { showExcludeFromRecentsConfirm = false },
            title = { Text(stringResource(R.string.settings_exclude_from_recents)) },
            text = { Text(stringResource(R.string.settings_exclude_from_recents_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExcludeFromRecentsConfirm = false
                        scope.launch {
                            errorMessage = runCatching {
                                settingsRepository.setExcludeFromRecents(true)
                            }.exceptionOrNull()?.message
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_exclude_from_recents_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExcludeFromRecentsConfirm = false }) {
                    Text(stringResource(R.string.settings_exclude_from_recents_cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        val versionName = remember {
            runCatching { GitHubUpdateChecker.installedVersion(context).first }
                .getOrDefault("0")
        }
        AlertDialog(
            onDismissRequest = {
                if (!updateBusy) {
                    showAboutDialog = false
                    updateStatus = null
                }
            },
            title = { Text(stringResource(R.string.settings_about_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_about_version,
                            versionName
                        ),
                        color = AppleSecondary,
                        fontSize = 13.sp
                    )
                    TextButton(
                        onClick = { openUrl(WalkGuardLinks.profileUrl) },
                        enabled = !updateBusy
                    ) {
                        Text(stringResource(R.string.settings_about_github_profile))
                    }
                    TextButton(
                        onClick = { openUrl(WalkGuardLinks.repoUrl) },
                        enabled = !updateBusy
                    ) {
                        Text(stringResource(R.string.settings_about_github_project))
                    }
                    updateStatus?.let { status ->
                        Text(
                            text = status,
                            color = AppleText,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (updateBusy) return@TextButton
                        updateBusy = true
                        updateStatus = context.getString(R.string.settings_about_checking_update)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                GitHubUpdateChecker.checkForUpdate(context)
                            }
                            updateBusy = false
                            when (result) {
                                is UpdateCheckResult.UpToDate -> {
                                    updateStatus = context.getString(
                                        R.string.settings_about_up_to_date,
                                        result.currentVersionName
                                    )
                                }
                                is UpdateCheckResult.UpdateAvailable -> {
                                    updateStatus = context.getString(
                                        R.string.settings_about_update_available,
                                        result.remoteVersionName,
                                        result.currentVersionName
                                    )
                                    openUrl(result.releasePageUrl)
                                }
                                is UpdateCheckResult.Failed -> {
                                    updateStatus = context.getString(
                                        R.string.settings_about_update_failed,
                                        result.message
                                    )
                                    openUrl(result.fallbackUrl)
                                }
                                is UpdateCheckResult.NotConfigured -> {
                                    updateStatus = context.getString(
                                        R.string.settings_about_update_not_configured
                                    )
                                }
                            }
                        }
                    },
                    enabled = !updateBusy
                ) {
                    Text(
                        if (updateBusy) {
                            stringResource(R.string.settings_about_checking_update)
                        } else {
                            stringResource(R.string.settings_about_check_update)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAboutDialog = false
                        updateStatus = null
                    },
                    enabled = !updateBusy
                ) {
                    Text(stringResource(R.string.settings_about_close))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenContentPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        LargeTitle(stringResource(R.string.settings_title))

        errorMessage?.let {
            Text(
                text = stringResource(R.string.settings_last_save_failed, it),
                color = AppleRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }

        val activity = context as? Activity
        // Product order: guard → general (language / notifications / about) → reminder content.
        SectionLabel(stringResource(R.string.settings_section_guard))
        GroupedList {
            ListRow(
                title = stringResource(R.string.settings_exclude_from_recents),
                subtitle = stringResource(R.string.settings_exclude_from_recents_subtitle),
                trailing = {
                    Switch(
                        checked = settings.excludeFromRecents,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // Match GKD: confirm before enabling (lock-in-recents may require visible card).
                                showExcludeFromRecentsConfirm = true
                            } else {
                                scope.launch {
                                    errorMessage = runCatching {
                                        settingsRepository.setExcludeFromRecents(false)
                                    }.exceptionOrNull()?.message
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = AppleFillStrong
                        )
                    )
                }
            )
        }
        FooterNote(stringResource(R.string.settings_exclude_from_recents_confirm))

        SectionLabel(stringResource(R.string.settings_section_system))
        GroupedList {
            ListRow(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_options_hint),
                trailing = {
                    PolicyDropdown(
                        selected = WalkGuardAppLanguage.current(),
                        options = WalkGuardAppLanguage.entries,
                        labelFor = { stringResource(it.labelRes()) },
                        onSelected = { language ->
                            if (language != WalkGuardAppLanguage.current()) {
                                applyWalkGuardAppLanguage(language)
                                activity?.recreate()
                            }
                        }
                    )
                }
            )
            ListRow(
                title = stringResource(R.string.settings_notification_settings),
                onClick = {
                    errorMessage = runCatching {
                        context.startActivity(permissionRepository.notificationSettingsIntent())
                    }.exceptionOrNull()?.let {
                        context.getString(R.string.settings_notification_settings_failed)
                    }
                },
                trailing = { Chevron() }
            )
            ListRow(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_subtitle),
                onClick = {
                    updateStatus = null
                    showAboutDialog = true
                },
                trailing = { Chevron() }
            )
        }

        SectionLabel(stringResource(R.string.settings_reminder_content))
        GroupedList {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_warning_title),
                    color = AppleText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                AppleTextField(
                    value = warningTitle,
                    onValueChange = { warningTitle = it },
                    singleLine = true
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_warning_message),
                    color = AppleText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.settings_reminder_scope),
                    color = AppleSecondary,
                    fontSize = 12.5.sp
                )
                AppleTextField(
                    value = warningMessage,
                    onValueChange = { warningMessage = it },
                    singleLine = false,
                    minLines = 3
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                ApplePrimaryButton(
                    text = stringResource(R.string.settings_save_warning_copy),
                    onClick = {
                        scope.launch {
                            errorMessage = runCatching {
                                settingsRepository.setWarningCopy(warningTitle, warningMessage)
                            }.exceptionOrNull()?.message
                        }
                    }
                )
            }
        }
    }
}
