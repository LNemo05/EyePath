package org.walkguard.app.guard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import org.walkguard.app.data.db.AppPolicyRepository
import org.walkguard.app.data.db.WalkGuardDatabase
import org.walkguard.app.data.settings.SettingsRepository
import org.walkguard.app.permissions.PermissionRepository
import org.walkguard.app.ui.apps.AppPolicyCatalogRepository
import org.walkguard.app.ui.apps.buildSystemAppCatalog

private val Context.walkGuardSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "guard_settings"
)

internal object GuardRuntimeGraph {
    @Volatile
    private var database: WalkGuardDatabase? = null

    @Volatile
    private var appPolicyCatalogRepository: AppPolicyCatalogRepository? = null

    fun settingsRepository(context: Context): SettingsRepository {
        val appContext = context.applicationContext
        return SettingsRepository(
            dataStore = appContext.walkGuardSettingsDataStore,
            permissionStatusProvider = { PermissionRepository(appContext).currentStatus() },
            appContext = appContext
        )
    }

    fun appPolicyRepository(context: Context): AppPolicyRepository {
        val appContext = context.applicationContext
        return AppPolicyRepository(
            appPolicyDao = database(appContext).appPolicyDao(),
            permissionStatusProvider = { PermissionRepository(appContext).currentStatus() },
            appContext = appContext
        )
    }

    fun appPolicyCatalogRepository(context: Context): AppPolicyCatalogRepository {
        val appContext = context.applicationContext
        return appPolicyCatalogRepository ?: synchronized(this) {
            appPolicyCatalogRepository ?: AppPolicyCatalogRepository(
                scanner = {
                    buildSystemAppCatalog(appContext.packageManager, appContext.packageName)
                }
            ).also { appPolicyCatalogRepository = it }
        }
    }

    fun database(context: Context): WalkGuardDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                WalkGuardDatabase::class.java,
                "walkguard.db"
            ).build().also { database = it }
        }
    }
}
