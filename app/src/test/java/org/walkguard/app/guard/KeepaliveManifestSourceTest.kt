package org.walkguard.app.guard

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class KeepaliveManifestSourceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageManager = context.packageManager

    @Test
    fun writeSecureSettingsIsDeclaredInManifest() {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            packageManager.checkPermission(
                Manifest.permission.WRITE_SECURE_SETTINGS,
                context.packageName
            )
        )
        val manifest = androidManifestSource().readText()
        assertTrue(manifest.contains("android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(manifest.contains("tools:ignore=\"ProtectedPermissions\""))
    }

    @Test
    fun quickSettingsTileIsRegistered() {
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, WalkGuardTileService::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(serviceInfo)
        assertEquals(true, serviceInfo.exported)
        assertEquals(
            "android.permission.BIND_QUICK_SETTINGS_TILE",
            serviceInfo.permission
        )

        val source = tileServiceSource().readText()
        assertTrue(source.contains("class WalkGuardTileService"))
        assertTrue(source.contains("fixRestartAccessibilityIfNeeded"))
        assertTrue(source.contains("GuardRuntimeSyncer.syncGuardState"))
        assertTrue(source.contains("GuardSyncReason.Tile"))
        assertTrue(source.contains("setGuardEnabled"))
        assertTrue(source.contains("onStartListening"))
        assertTrue(source.contains("onClick"))
    }

    @Test
    fun packageReplacedReceiverIsRegistered() {
        val receiverInfo = packageManager.getReceiverInfo(
            ComponentName(context, PackageReplacedReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(receiverInfo)
        assertEquals(false, receiverInfo.exported)

        val source = packageReplacedSource().readText()
        assertTrue(source.contains("ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(source.contains("GuardSyncReason.PackageReplaced"))
        assertTrue(source.contains("GuardRuntimeSyncer.syncGuardState"))
        assertFalse(source.contains("setGuardEnabled(false)"))
    }

    @Test
    fun accessibilitySecureSettingsPortsGkdTiming() {
        val source = secureSettingsSource().readText()
        assertTrue(source.contains("A11Y_AWAIT_FIX_TIME = 1000L"))
        assertTrue(source.contains("A11Y_AWAIT_START_TIME = 2000L"))
        assertTrue(source.contains("fun canWriteSecureSettings"))
        assertTrue(source.contains("suspend fun fixRestartAccessibilityIfNeeded"))
        assertTrue(source.contains("getSecureA11yServices"))
        assertTrue(source.contains("putSecureA11yServices"))
        assertTrue(source.contains("ACCESSIBILITY_ENABLED"))
        assertTrue(source.contains("Mutex"))
    }

    @Test
    fun runtimeSyncerAndSyncNeverClearGuardEnabled() {
        val syncer = syncerSource().readText()
        val sync = runtimeSyncSource().readText()
        val boot = bootReceiverSource().readText()
        val home = homeScreenSource().readText()

        assertFalse(syncer.contains("setGuardEnabled(false)"))
        assertTrue(syncer.contains("fixRestartAccessibilityIfNeeded"))
        assertTrue(syncer.contains("requireFullGate = false"))

        assertFalse(sync.contains("setGuardEnabled(false)"))
        assertTrue(sync.contains("GuardRuntimeSyncer.syncGuardState"))
        assertTrue(sync.contains("GuardSyncReason.MainActivity"))

        assertFalse(boot.contains("setGuardEnabled(false)"))
        assertTrue(boot.contains("GuardRuntimeSyncer.syncGuardState"))
        assertTrue(boot.contains("GuardSyncReason.Boot"))

        // Home still uses setGuardEnabled(false) for explicit user disable only.
        assertTrue(home.contains("error_start_service"))
        val enableBlockStart = home.indexOf("if (enabled) {")
        val enableBlockEnd = home.indexOf("} else {", enableBlockStart)
        assertTrue(enableBlockStart >= 0)
        assertTrue(enableBlockEnd > enableBlockStart)
        val enableBlock = home.substring(enableBlockStart, enableBlockEnd)
        assertFalse(enableBlock.contains("setGuardEnabled(false)"))
    }

    private fun androidManifestSource(): File = sourceFile("main/AndroidManifest.xml")
    private fun tileServiceSource(): File =
        sourceFile("main/java/org/walkguard/app/guard/WalkGuardTileService.kt")
    private fun packageReplacedSource(): File =
        sourceFile("main/java/org/walkguard/app/guard/PackageReplacedReceiver.kt")
    private fun secureSettingsSource(): File =
        sourceFile("main/java/org/walkguard/app/guard/AccessibilitySecureSettings.kt")
    private fun syncerSource(): File =
        sourceFile("main/java/org/walkguard/app/guard/GuardRuntimeSyncer.kt")
    private fun runtimeSyncSource(): File =
        sourceFile("main/java/org/walkguard/app/guard/GuardRuntimeSync.kt")
    private fun bootReceiverSource(): File =
        sourceFile("main/java/org/walkguard/app/guard/BootReceiver.kt")
    private fun homeScreenSource(): File =
        sourceFile("main/java/org/walkguard/app/ui/home/HomeScreen.kt")

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/$relativePath"),
            File("app/src/$relativePath")
        )
        return candidates.first { it.exists() }
    }
}
