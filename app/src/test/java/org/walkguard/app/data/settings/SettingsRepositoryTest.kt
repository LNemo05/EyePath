package org.walkguard.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.walkguard.app.R
import org.walkguard.app.WalkGuardTestContext
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.permissions.PermissionStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val allPermissions = PermissionStatus(
        accessibilityEnabled = true,
        notificationsGranted = true,
        activityRecognitionGranted = true,
        overlayGranted = true,
        deviceAdminEnabled = true,
        ignoringBatteryOptimizations = true
    )

    private fun repository(): Pair<SettingsRepository, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(temporaryFolder.root, "walkguard_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return SettingsRepository(
            dataStore = dataStore,
            permissionStatusProvider = { allPermissions },
            appContext = WalkGuardTestContext.appContext
        ) to scope
    }

    @Test fun defaultSettingsMatchSpec() = runBlocking {
        val (repository, scope) = repository()
        try {
            val settings = repository.settings.first()

            assertEquals(false, settings.guardEnabled)
            assertEquals(GuardMode.RAGE, settings.globalMode)
            assertEquals(0L, settings.pauseUntilEpochMs)
            assertEquals(
                WalkGuardTestContext.appContext.getString(R.string.default_warning_title),
                settings.warningTitle
            )
            assertEquals(
                WalkGuardTestContext.appContext.getString(R.string.default_warning_message),
                settings.warningMessage
            )
            assertEquals(false, settings.excludeFromRecents)
            assertEquals(
                setOf(
                    "guardEnabled",
                    "globalMode",
                    "pauseUntilEpochMs",
                    "warningTitle",
                    "warningMessage",
                    "excludeFromRecents"
                ),
                GuardSettings::class.java.declaredFields
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .mapTo(mutableSetOf()) { it.name }
            )
        } finally {
            scope.cancel()
        }
    }

    @Test fun settersPersistUpdatedSettings() = runBlocking {
        val (repository, scope) = repository()
        try {
            repository.setGuardEnabled(true)
            repository.setGlobalMode(GuardMode.NORMAL)
            val pauseUntil = System.currentTimeMillis() + 5 * 60_000L
            repository.setPauseUntilEpochMs(pauseUntil)
            repository.setWarningCopy("Stop walking", "Look up first")
            repository.setExcludeFromRecents(true)

            val settings = repository.settings.first()
            assertEquals(true, settings.guardEnabled)
            assertEquals(GuardMode.NORMAL, settings.globalMode)
            assertEquals(pauseUntil, settings.pauseUntilEpochMs)
            assertEquals("Stop walking", settings.warningTitle)
            assertEquals("Look up first", settings.warningMessage)
            assertEquals(true, settings.excludeFromRecents)

            repository.setExcludeFromRecents(false)
            assertEquals(false, repository.settings.first().excludeFromRecents)
        } finally {
            scope.cancel()
        }
    }
}
