package org.walkguard.app

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.walkguard.app.R
import org.walkguard.app.data.settings.GuardSettings
import org.walkguard.app.guard.GuardRuntimeGraph
import org.walkguard.app.guard.GuardRuntimeSync
import org.walkguard.app.permissions.PermissionRepository
import org.walkguard.app.ui.apps.AppPoliciesScreen
import org.walkguard.app.ui.components.FloatingTabItem
import org.walkguard.app.ui.components.FloatingTextTabBar
import org.walkguard.app.ui.home.HomeScreen
import org.walkguard.app.ui.permissions.PermissionsScreen
import org.walkguard.app.ui.settings.SettingsScreen
import org.walkguard.app.ui.stats.StatsScreen
import org.walkguard.app.ui.theme.AppleBg
import org.walkguard.app.ui.theme.WalkGuardTheme

enum class WalkGuardTab(@StringRes val labelRes: Int) {
    HOME(R.string.tab_home),
    APPS(R.string.tab_apps),
    SETTINGS(R.string.tab_settings),
    STATS(R.string.tab_stats),
    PERMISSIONS(R.string.tab_permissions)
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsRepository = GuardRuntimeGraph.settingsRepository(this)
        val appPolicyRepository = GuardRuntimeGraph.appPolicyRepository(this)
        val catalogRepository = GuardRuntimeGraph.appPolicyCatalogRepository(this)
        val database = GuardRuntimeGraph.database(this)
        val permissionRepository = PermissionRepository(this)
        GuardRuntimeSync.resumePersistedGuardIfNeeded(this)

        // GKD-style: observe excludeFromRecents and apply to all AppTasks.
        lifecycleScope.launch {
            settingsRepository.settings
                .map { it.excludeFromRecents }
                .distinctUntilChanged()
                .collect { exclude ->
                    applyExcludeFromRecents(exclude)
                }
        }

        setContent {
            val settings = settingsRepository.settings.collectAsState(
                initial = GuardSettings.Default
            ).value
            var selectedTab by remember { mutableStateOf(WalkGuardTab.HOME) }

            WalkGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppleBg
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        when (selectedTab) {
                            WalkGuardTab.HOME -> HomeScreen(
                                settings = settings,
                                settingsRepository = settingsRepository,
                                permissionRepository = permissionRepository,
                                context = this@MainActivity,
                                onNavigateToPermissions = {
                                    selectedTab = WalkGuardTab.PERMISSIONS
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            WalkGuardTab.APPS -> AppPoliciesScreen(
                                context = this@MainActivity,
                                appPolicyRepository = appPolicyRepository,
                                catalogRepository = catalogRepository,
                                modifier = Modifier.fillMaxSize()
                            )
                            WalkGuardTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                settingsRepository = settingsRepository,
                                permissionRepository = permissionRepository,
                                modifier = Modifier.fillMaxSize()
                            )
                            WalkGuardTab.STATS -> StatsScreen(
                                statsDao = database.statsDao(),
                                modifier = Modifier.fillMaxSize()
                            )
                            WalkGuardTab.PERMISSIONS -> PermissionsScreen(
                                context = this@MainActivity,
                                permissionRepository = permissionRepository,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        FloatingTextTabBar(
                            tabs = WalkGuardTab.entries.map { tab ->
                                FloatingTabItem(
                                    key = tab,
                                    label = stringResource(tab.labelRes)
                                )
                            },
                            selectedKey = selectedTab,
                            onTabSelected = { key ->
                                selectedTab = key as WalkGuardTab
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    private fun applyExcludeFromRecents(exclude: Boolean) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        activityManager.appTasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(exclude) }
        }
    }
}
