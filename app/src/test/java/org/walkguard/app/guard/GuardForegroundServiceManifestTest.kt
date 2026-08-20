package org.walkguard.app.guard

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class GuardForegroundServiceManifestTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageManager = context.packageManager

    @Test
    fun foregroundServiceAndBootReceiverAreRegistered() {
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, GuardForegroundService::class.java),
            PackageManager.GET_META_DATA
        )
        val bootReceiverInfo = packageManager.getReceiverInfo(
            ComponentName(context, BootReceiver::class.java),
            PackageManager.GET_META_DATA
        )

        assertNotNull(serviceInfo)
        assertEquals(false, serviceInfo.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, serviceInfo.foregroundServiceType)
        assertNotNull(bootReceiverInfo)
        assertEquals(false, bootReceiverInfo.exported)
    }

    @Test
    fun screenStateReceiverIsDynamicOnly() {
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            packageManager.getReceiverInfo(
                ComponentName(context, ScreenStateReceiver::class.java),
                PackageManager.GET_META_DATA
            )
        }
    }

    @Test
    fun foregroundServicePermissionsAreDeclared() {
        assertPermissionGrantedInManifest(Manifest.permission.FOREGROUND_SERVICE)
        assertPermissionGrantedInManifest(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        assertPermissionGrantedInManifest(Manifest.permission.ACTIVITY_RECOGNITION)
        assertPermissionGrantedInManifest(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        assertPermissionGrantedInManifest(Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    @Test
    fun tileServiceAndPackageReplacedReceiverAreRegistered() {
        val tileInfo = packageManager.getServiceInfo(
            ComponentName(context, WalkGuardTileService::class.java),
            PackageManager.GET_META_DATA
        )
        val packageReplacedInfo = packageManager.getReceiverInfo(
            ComponentName(context, PackageReplacedReceiver::class.java),
            PackageManager.GET_META_DATA
        )

        assertNotNull(tileInfo)
        assertEquals(true, tileInfo.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tileInfo.permission)
        assertNotNull(packageReplacedInfo)
        assertEquals(false, packageReplacedInfo.exported)
    }

    private fun assertPermissionGrantedInManifest(permission: String) {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            packageManager.checkPermission(permission, context.packageName)
        )
    }
}
