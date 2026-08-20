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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.walkguard.app.WalkGuardTestContext
import org.walkguard.app.permissions.PermissionStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsRepositoryPauseTest {
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
        val file = File(temporaryFolder.root, "walkguard_settings_pause.preferences_pb")
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

    @Test
    fun setPauseUntilEpochMsDoesNotClampFutureDeadline() = runBlocking {
        val (repository, scope) = repository()
        try {
            val requested = System.currentTimeMillis() + 24 * 60 * 60_000L

            repository.setPauseUntilEpochMs(requested)

            assertEquals(requested, repository.settings.first().pauseUntilEpochMs)
        } finally {
            scope.cancel()
        }
    }
}
