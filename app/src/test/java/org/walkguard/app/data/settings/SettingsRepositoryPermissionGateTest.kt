package org.walkguard.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
class SettingsRepositoryPermissionGateTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun repository(
        permissionStatus: PermissionStatus
    ): Pair<SettingsRepository, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(temporaryFolder.root, "walkguard_settings_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return SettingsRepository(
            dataStore = dataStore,
            permissionStatusProvider = { permissionStatus },
            appContext = WalkGuardTestContext.appContext
        ) to scope
    }

    @Test fun enablingGuardFailsWhenCorePermissionsAreMissing() = runBlocking {
        val (repository, scope) = repository(permissionStatus(activityRecognitionGranted = false))
        try {
            val exception = assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.setGuardEnabled(true) }
            }

            assertFalse(repository.settings.first().guardEnabled)
            assertEquals(
                true,
                exception.message.orEmpty().contains(
                    WalkGuardTestContext.appContext.getString(R.string.missing_permission_activity_recognition)
                )
            )
        } finally {
            scope.cancel()
        }
    }

    @Test fun selectingNormalModeFailsWithoutOverlayPermission() = runBlocking {
        val (repository, scope) = repository(permissionStatus(overlayGranted = false))
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.setGlobalMode(GuardMode.NORMAL) }
            }

            assertEquals(GuardMode.RAGE, repository.settings.first().globalMode)
        } finally {
            scope.cancel()
        }
    }

    @Test fun selectingRageModeFailsWithoutDeviceAdmin() = runBlocking {
        val (repository, scope) = repository(permissionStatus(deviceAdminEnabled = false))
        try {
            repository.setGlobalMode(GuardMode.MILD)

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.setGlobalMode(GuardMode.RAGE) }
            }

            assertEquals(GuardMode.MILD, repository.settings.first().globalMode)
        } finally {
            scope.cancel()
        }
    }

    @Test fun repositoryRequiresPermissionProviderForMutatingPermissionGatedSettings() {
        val source = settingsRepositorySource().readText()

        assertFalse(source.contains("PermissionStatus)? = null"))
        assertFalse(source.contains("permissionStatusProvider?.invoke()"))
    }

    private fun settingsRepositorySource(): File {
        val candidates = listOf(
            File("src/main/java/org/walkguard/app/data/settings/SettingsRepository.kt"),
            File("app/src/main/java/org/walkguard/app/data/settings/SettingsRepository.kt")
        )
        return candidates.first { it.exists() }
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
