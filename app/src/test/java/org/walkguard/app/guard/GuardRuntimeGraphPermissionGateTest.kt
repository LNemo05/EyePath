package org.walkguard.app.guard

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.walkguard.app.core.model.AppPolicy

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class GuardRuntimeGraphPermissionGateTest {
    @Test
    fun settingsRepositoryFromRuntimeGraphRejectsGuardEnableWhenPermissionsAreMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = GuardRuntimeGraph.settingsRepository(context)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.setGuardEnabled(true) }
        }
        assertFalse(repository.settings.first().guardEnabled)
    }

    @Test
    fun appPolicyRepositoryFromRuntimeGraphRejectsNormalPolicyWhenOverlayIsMissing() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val repository = GuardRuntimeGraph.appPolicyRepository(context)

            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    repository.setPolicy(
                        packageName = "com.example.reader",
                        label = "Reader",
                        policy = AppPolicy.NORMAL,
                        updatedAtEpochMs = 123L
                    )
                }
            }
        }
    }
}
