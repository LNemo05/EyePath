package org.walkguard.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.permissions.PermissionGate
import org.walkguard.app.permissions.PermissionStatus
import org.walkguard.app.ui.i18n.defaultWarningMessage
import org.walkguard.app.ui.i18n.defaultWarningTitle
import org.walkguard.app.ui.i18n.formatMissingPermissionsError

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val permissionStatusProvider: suspend () -> PermissionStatus,
    private val appContext: android.content.Context
) {
    val settings: Flow<GuardSettings> = dataStore.data.map { preferences ->
        GuardSettings(
            guardEnabled = preferences[Keys.GUARD_ENABLED] ?: GuardSettings.Default.guardEnabled,
            globalMode = preferences[Keys.GLOBAL_MODE].toEnumOrDefault(GuardSettings.Default.globalMode),
            pauseUntilEpochMs = preferences[Keys.PAUSE_UNTIL_EPOCH_MS] ?: GuardSettings.Default.pauseUntilEpochMs,
            warningTitle = preferences[Keys.WARNING_TITLE]?.takeIf { it.isNotBlank() }
                ?: appContext.defaultWarningTitle(),
            warningMessage = preferences[Keys.WARNING_MESSAGE]?.takeIf { it.isNotBlank() }
                ?: appContext.defaultWarningMessage(),
            excludeFromRecents = preferences[Keys.EXCLUDE_FROM_RECENTS]
                ?: GuardSettings.Default.excludeFromRecents
        )
    }

    suspend fun setGuardEnabled(enabled: Boolean) {
        if (enabled) {
            val currentMode = settings.first().globalMode
            PermissionGate.enforceCanEnableGuard(permissionStatusProvider(), currentMode, appContext)
        }
        set(Keys.GUARD_ENABLED, enabled)
    }

    suspend fun setGlobalMode(mode: GuardMode) {
        val result = PermissionGate.canUseMode(mode, permissionStatusProvider())
        if (!result.allowed) {
            error(appContext.formatMissingPermissionsError(result.missing))
        }
        set(Keys.GLOBAL_MODE, mode.name)
    }

    suspend fun setPauseUntilEpochMs(epochMs: Long) {
        val now = System.currentTimeMillis()
        set(Keys.PAUSE_UNTIL_EPOCH_MS, if (epochMs <= now) now else epochMs)
    }

    suspend fun setWarningCopy(title: String, message: String) {
        dataStore.edit { preferences ->
            preferences[Keys.WARNING_TITLE] = title
            preferences[Keys.WARNING_MESSAGE] = message
        }
    }

    suspend fun setExcludeFromRecents(enabled: Boolean) {
        set(Keys.EXCLUDE_FROM_RECENTS, enabled)
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
        return this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }

    private object Keys {
        val GUARD_ENABLED = booleanPreferencesKey("guard_enabled")
        val GLOBAL_MODE = stringPreferencesKey("global_mode")
        val PAUSE_UNTIL_EPOCH_MS = longPreferencesKey("pause_until_epoch_ms")
        val WARNING_TITLE = stringPreferencesKey("warning_title")
        val WARNING_MESSAGE = stringPreferencesKey("warning_message")
        val EXCLUDE_FROM_RECENTS = booleanPreferencesKey("exclude_from_recents")
    }
}
