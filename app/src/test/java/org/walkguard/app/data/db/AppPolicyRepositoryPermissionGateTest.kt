package org.walkguard.app.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.walkguard.app.WalkGuardTestContext
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.permissions.PermissionStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppPolicyRepositoryPermissionGateTest {
    private val allPermissions = PermissionStatus(
        accessibilityEnabled = true,
        notificationsGranted = true,
        activityRecognitionGranted = true,
        overlayGranted = true,
        deviceAdminEnabled = true,
        ignoringBatteryOptimizations = true
    )

    @Test
    fun selectingNormalPolicyFailsWithoutOverlayPermission() = runBlocking {
        val dao = FakeAppPolicyDao()
        val repository = AppPolicyRepository(
            appPolicyDao = dao,
            permissionStatusProvider = { allPermissions.copy(overlayGranted = false) },
            appContext = WalkGuardTestContext.appContext
        )

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
        assertEquals(null, dao.lastUpserted)
    }

    @Test
    fun selectingRagePolicyFailsWithoutDeviceAdmin() = runBlocking {
        val dao = FakeAppPolicyDao()
        val repository = AppPolicyRepository(
            appPolicyDao = dao,
            permissionStatusProvider = { allPermissions.copy(deviceAdminEnabled = false) },
            appContext = WalkGuardTestContext.appContext
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.setPolicy(
                    packageName = "com.example.chat",
                    label = "Chat",
                    policy = AppPolicy.RAGE,
                    updatedAtEpochMs = 456L
                )
            }
        }

        assertEquals(null, dao.lastUpserted)
        assertEquals(
            true,
            exception.message.orEmpty().contains(
                WalkGuardTestContext.appContext.getString(org.walkguard.app.R.string.missing_permission_device_admin)
            )
        )
    }

    @Test
    fun selectingWhitelistPolicyDoesNotRequireModeSpecificPermission() = runBlocking {
        val dao = FakeAppPolicyDao()
        val repository = AppPolicyRepository(
            appPolicyDao = dao,
            permissionStatusProvider = {
                allPermissions.copy(overlayGranted = false, deviceAdminEnabled = false)
            },
            appContext = WalkGuardTestContext.appContext
        )

        repository.setPolicy(
            packageName = "com.example.maps",
            label = "Maps",
            policy = AppPolicy.WHITELIST,
            updatedAtEpochMs = 789L
        )

        assertEquals("com.example.maps", dao.lastUpserted?.packageName)
        assertEquals(AppPolicy.WHITELIST.name, dao.lastUpserted?.policy)
    }

    private class FakeAppPolicyDao : AppPolicyDao {
        private val policies = MutableStateFlow<List<AppPolicyEntity>>(emptyList())
        var lastUpserted: AppPolicyEntity? = null
            private set

        override fun observePolicies(): Flow<List<AppPolicyEntity>> = policies

        override suspend fun getPolicy(packageName: String): AppPolicyEntity? {
            return policies.value.firstOrNull { it.packageName == packageName }
        }

        override suspend fun upsert(entity: AppPolicyEntity) {
            lastUpserted = entity
            policies.value = policies.value.filterNot { it.packageName == entity.packageName } + entity
        }

        override suspend fun delete(packageName: String) {
            policies.value = policies.value.filterNot { it.packageName == packageName }
        }
    }
}
