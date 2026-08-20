package org.walkguard.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkGuardUiSourceTest {
    @Test
    fun mainActivityUsesSimpleComposeTabShellAndRuntimeRepositories() {
        val source = sourceFile("main/java/org/walkguard/app/MainActivity.kt").readText()
        val graphSource = sourceFile("main/java/org/walkguard/app/guard/GuardRuntimeGraph.kt").readText()

        assertTrue(source.contains("enum class WalkGuardTab"))
        assertTrue(source.contains("WalkGuardTab.entries"))
        assertTrue(source.contains("FloatingTextTabBar"))
        assertTrue(source.contains("WalkGuardTheme"))
        assertTrue(source.contains("WalkGuardTab.HOME"))
        assertTrue(source.contains("WalkGuardTab.APPS"))
        assertTrue(source.contains("WalkGuardTab.SETTINGS"))
        assertTrue(source.contains("WalkGuardTab.STATS"))
        assertTrue(source.contains("WalkGuardTab.PERMISSIONS"))
        assertTrue(source.contains("onNavigateToPermissions"))
        assertTrue(source.contains("selectedTab = WalkGuardTab.PERMISSIONS"))
        assertTrue(source.contains("GuardRuntimeGraph.settingsRepository"))
        assertTrue(source.contains("GuardRuntimeGraph.appPolicyRepository"))
        assertTrue(source.contains("GuardRuntimeGraph.appPolicyCatalogRepository"))
        assertTrue(source.contains("catalogRepository = catalogRepository"))
        assertTrue(source.contains("PermissionRepository"))
        assertTrue(source.contains("GuardRuntimeSync.resumePersistedGuardIfNeeded"))
        assertTrue(!source.contains("GuardSettings.Default.globalMode"))

        assertTrue(graphSource.contains("private var appPolicyCatalogRepository: AppPolicyCatalogRepository?"))
        assertTrue(graphSource.contains("fun appPolicyCatalogRepository(context: Context)"))
        assertTrue(graphSource.contains("val appContext = context.applicationContext"))
        assertTrue(graphSource.contains("buildSystemAppCatalog(appContext.packageManager, appContext.packageName)"))
        assertEquals(
            1,
            graphSource.windowed("buildSystemAppCatalog(".length)
                .count { it == "buildSystemAppCatalog(" }
        )
    }

    @Test
    fun homeScreenExposesGuardModePermissionsForegroundAndCompletePauseControls() {
        val source = sourceFile("main/java/org/walkguard/app/ui/home/HomeScreen.kt").readText()

        assertTrue(source.contains("fun HomeScreen"))
        assertTrue(source.contains("setGuardEnabled"))
        assertTrue(source.contains("setGlobalMode"))
        assertTrue(source.contains("guardModeDescription"))
        assertTrue(source.contains("setPauseUntilEpochMs"))
        assertTrue(source.contains("calculatePauseDeadline"))
        assertTrue(source.contains("home_pause_custom_minutes"))
        assertTrue(source.contains("home_pause_apply"))
        assertTrue(source.contains("ForegroundAppTracker.currentPackageFlow"))
        assertTrue(source.contains("GuardForegroundService.startSafely"))
        assertTrue(source.contains("requiredMode = settings.globalMode"))
        assertTrue(source.contains("setGuardEnabled(false)"))
        assertTrue(source.contains("GuardRuntimeStatusTracker.clear"))
        assertTrue(source.contains("readDeviceInteractiveState"))
        // Permission incomplete: switch looks OFF and open-attempt navigates to Permissions.
        assertTrue(source.contains("onNavigateToPermissions"))
        assertTrue(source.contains("settings.guardEnabled && gateResult.allowed"))
        assertTrue(source.contains("enabled && !gateResult.allowed"))
        assertTrue(source.contains("PermissionGate.canEnableGuard"))
        assertTrue(source.contains("listOf(1, 5, 15)"))
        // Real paused state must come from backend pauseUntilEpochMs, not local chip selection.
        assertTrue(source.contains("pauseUntilEpochMs"))
        assertTrue(source.contains("home_pause_active") || source.contains("home_paused"))
        assertTrue(source.contains("home_pause_resume"))
    }

    @Test
    fun appPoliciesScreenLoadsCatalogAsynchronouslyAndWritesThroughRepository() {
        val source = sourceFile("main/java/org/walkguard/app/ui/apps/AppPoliciesScreen.kt").readText()
        fun occurrenceCount(value: String) = source.windowed(value.length).count { it == value }
        val retryLabelIndex = source.indexOf("R.string.apps_retry")
        val retryButtonIndex = source.lastIndexOf("Button(", retryLabelIndex)
        val retryButtonSource = source.substring(retryButtonIndex, retryLabelIndex)

        assertTrue(source.contains("catalogRepository.load(forceRefresh = loadRequest > 0)"))
        assertTrue(source.contains("LaunchedEffect(loadRequest, catalogRepository)"))
        assertTrue(source.contains("mergeAppPolicyCatalog"))
        assertTrue(source.contains("CancellationException"))
        assertTrue(occurrenceCount("throw error") >= 2)
        assertTrue(source.contains("R.string.value_unknown"))
        assertTrue(occurrenceCount("error.message ?: unknownError") >= 2)
        assertTrue(source.contains("apps_loading"))
        assertTrue(source.contains("apps_refreshing"))
        assertTrue(source.contains("apps_refresh"))
        assertTrue(source.contains("apps_retry"))
        assertTrue(source.contains("apps_load_failed"))
        assertTrue(
            Regex("""loadError\s*==\s*null\s*&&\s*!isLoading\s*->\s*R\.string\.apps_empty_catalog""")
                .containsMatchIn(source)
        )
        assertTrue(source.contains("filterAppPolicyEntries"))
        assertTrue(source.contains("AppPolicyListFilter"))
        assertTrue(source.contains("policyFilter"))
        assertTrue(source.contains("PillChip"))
        assertTrue(source.contains("apps_filter_all"))
        assertTrue(source.contains("apps_empty_filter"))
        assertTrue(source.contains("LazyRow"))
        assertTrue(
            source.contains("policyByPackage") &&
                source.contains("policyFilter")
        )
        // Ensure filter call includes policy args, not only (catalog, searchQuery)
        assertTrue(
            Regex("""filterAppPolicyEntries\s*\([\s\S]*?policyFilter""").containsMatchIn(source)
        )
        // Bound the regression check to the call's closing parenthesis so later state cannot satisfy it.
        assertTrue(
            Regex(
                """filterAppPolicyEntries\s*\(\s*entries\s*=\s*catalog\s*,\s*query\s*=\s*searchQuery\s*,\s*policyByPackage\s*=\s*policyByPackage\s*,\s*policyFilter\s*=\s*policyFilter\s*\)"""
            ).containsMatchIn(source)
        )
        assertTrue(source.contains("apps_search_hint"))
        assertTrue(source.contains("apps_search_clear"))
        assertTrue(Regex("""enabled\s*=\s*!isLoading""").containsMatchIn(retryButtonSource))
        assertTrue(source.contains("appPolicyRepository.setPolicy"))
        assertTrue(source.contains("AppPolicy.values()"))
        // Scroll-jank A/B: list must not sit inside a shadowed AppleCard; dropdown menus stay lazy.
        assertTrue(source.contains("policyOptions"))
        assertTrue(source.contains("AppPolicy.entries"))
        assertTrue(source.contains("background(AppleCard)"))
        assertFalse(source.contains("AppleCard("))
        assertFalse(source.contains("buildAppPolicyCatalog("))
        assertFalse(source.contains("buildSystemAppCatalog("))
        assertFalse(source.contains("context.packageManager"))
        assertFalse(source.contains("AppPolicyDao"))
        assertFalse(source.contains(".upsert("))

        val dropdownSource = sourceFile("main/java/org/walkguard/app/ui/components/WalkGuardUi.kt").readText()
        assertTrue(
            Regex("""if\s*\(\s*expanded\s*\)\s*\{\s*DropdownMenu""").containsMatchIn(dropdownSource)
        )
    }

    @Test
    fun settingsScreenContainsOnlySecondaryConfiguration() {
        val source = sourceFile("main/java/org/walkguard/app/ui/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("settings_language"))
        assertTrue(source.contains("settings_exclude_from_recents"))
        assertTrue(source.contains("settings_reminder_content") || source.contains("settings_section_reminder"))
        assertTrue(source.contains("settings_reminder_scope"))
        assertTrue(source.contains("setWarningCopy"))
        assertTrue(source.contains("notificationSettingsIntent"))
        assertTrue(source.contains("settings_about"))
        assertTrue(source.contains("GitHubUpdateChecker"))
        assertTrue(source.contains("WalkGuardLinks"))
        assertTrue(source.contains("runCatching"))
        // Order: guard → general card (language / notifications / about) → reminder content (last)
        val guardIdx = source.indexOf("settings_section_guard")
        val systemIdx = source.indexOf("settings_section_system")
        // Use unambiguous resource ids so dialog strings (settings_about_*) do not win.
        val languageIdx = source.indexOf("R.string.settings_language)")
        val notificationIdx = source.indexOf("R.string.settings_notification_settings)")
        val aboutRowIdx = source.indexOf("R.string.settings_about)")
        val reminderIdx = source.indexOf("settings_reminder_content").let {
            if (it >= 0) it else source.indexOf("settings_section_reminder")
        }
        assertTrue(
            guardIdx >= 0 &&
                systemIdx > guardIdx &&
                languageIdx > systemIdx &&
                notificationIdx > languageIdx &&
                aboutRowIdx > notificationIdx &&
                reminderIdx > aboutRowIdx
        )
        assertFalse(source.contains("setGlobalMode"))
        assertFalse(source.contains("settings_global_mode"))
        assertFalse(source.contains("settings_change_mode_on_home"))
        assertFalse(source.contains("guardModeLabel"))
        assertFalse(source.contains("guardModeDescription"))
        assertFalse(source.contains("NumericSetting"))
        assertFalse(source.contains("Notification" + "Frequency"))
        assertFalse(source.contains("setRageLock" + "CooldownMs"))
        assertFalse(source.contains("setWalking" + "TimeoutMs"))
        assertFalse(source.contains("setMaxPause" + "Minutes"))
        assertFalse(source.contains("setMildNotification" + "Frequency"))
    }

    @Test
    fun statsScreenReadsDailyCountsAndTopPackagesFromRoom() {
        val source = sourceFile("main/java/org/walkguard/app/ui/stats/StatsScreen.kt").readText()
        val daoSource = sourceFile("main/java/org/walkguard/app/data/db/StatsDao.kt").readText()

        assertTrue(daoSource.contains("observeDailyStats"))
        assertTrue(daoSource.contains("observeTopAppDailyStats"))
        assertTrue(source.contains("observeDailyStats"))
        assertTrue(source.contains("observeTopAppDailyStats"))
        assertTrue(source.contains("collectAsState"))
        assertTrue(source.contains("mildCount"))
        assertTrue(source.contains("normalCount"))
        assertTrue(source.contains("rageCount"))
        // One-shot LaunchedEffect(day) snapshot is no longer acceptable for live stats.
        assertFalse(source.contains("getDailyStats(day)"))
        assertFalse(source.contains("getTopAppDailyStats(day, 10)"))
    }

    @Test
    fun permissionsScreenShowsAllStatusesAndSettingsEntrypoints() {
        val source = sourceFile("main/java/org/walkguard/app/ui/permissions/PermissionsScreen.kt").readText()

        assertTrue(source.contains("accessibilitySettingsIntent"))
        assertTrue(source.contains("notificationSettingsIntent"))
        assertTrue(source.contains("overlaySettingsIntent"))
        assertTrue(source.contains("batteryOptimizationSettingsIntent"))
        assertTrue(source.contains("deviceAdminActivationIntent"))
        assertTrue(source.contains("ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue(source.contains("R.string.permissions_stop_walking_first"))
        // Granted rows must still open system settings (revoke/adjust channels, etc.).
        assertTrue(source.contains("onClick = onOpenSettings"))
        assertFalse(source.contains("if (granted) null else onOpenSettings"))
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/$relativePath"),
            File("app/src/$relativePath")
        )
        return candidates.first { it.exists() }
    }
}
