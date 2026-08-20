package org.walkguard.app.data.db

import kotlinx.coroutines.flow.Flow
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode
import org.walkguard.app.permissions.PermissionGate
import org.walkguard.app.permissions.PermissionStatus
import org.walkguard.app.ui.i18n.formatMissingPermissionsError

class AppPolicyRepository(
    private val appPolicyDao: AppPolicyDao,
    private val permissionStatusProvider: suspend () -> PermissionStatus,
    private val appContext: android.content.Context
) {
    fun observePolicies(): Flow<List<AppPolicyEntity>> = appPolicyDao.observePolicies()

    suspend fun getPolicy(packageName: String): AppPolicy? {
        return appPolicyDao.getPolicy(packageName)?.policy.toAppPolicyOrNull()
    }

    suspend fun setPolicy(
        packageName: String,
        label: String,
        policy: AppPolicy,
        updatedAtEpochMs: Long
    ) {
        policy.toGuardModeOrNull()?.let { mode ->
            val result = PermissionGate.canUseMode(mode, permissionStatusProvider())
            if (!result.allowed) {
                error(appContext.formatMissingPermissionsError(result.missing))
            }
        }
        appPolicyDao.upsert(
            AppPolicyEntity(
                packageName = packageName,
                label = label,
                policy = policy.name,
                updatedAtEpochMs = updatedAtEpochMs
            )
        )
    }
    suspend fun deletePolicy(packageName: String) {
        appPolicyDao.delete(packageName)
    }

    private fun AppPolicy.toGuardModeOrNull(): GuardMode? {
        return when (this) {
            AppPolicy.INHERIT,
            AppPolicy.WHITELIST -> null
            AppPolicy.MILD -> GuardMode.MILD
            AppPolicy.NORMAL -> GuardMode.NORMAL
            AppPolicy.RAGE -> GuardMode.RAGE
        }
    }

    private fun String?.toAppPolicyOrNull(): AppPolicy? {
        return this?.let { runCatching { AppPolicy.valueOf(it) }.getOrNull() }
    }
}
