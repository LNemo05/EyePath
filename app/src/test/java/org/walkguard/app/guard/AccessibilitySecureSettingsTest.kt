package org.walkguard.app.guard

import android.Manifest
import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class AccessibilitySecureSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setUp() {
        WalkAccessibilityService.isRunning.value = false
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )
        shadowOf(context).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    @Test
    fun getAndPutSecureA11yServicesRoundTrip() {
        val component = AccessibilitySecureSettings.accessibilityComponent(context)
        assertTrue(AccessibilitySecureSettings.putSecureA11yServices(context, setOf(component)))

        val names = AccessibilitySecureSettings.getSecureA11yServices(context)
        assertTrue(
            names.any {
                it.flattenToString() == component.flattenToString() ||
                    it.flattenToShortString() == component.flattenToShortString()
            }
        )
    }

    @Test
    fun canWriteSecureSettingsReflectsPermission() {
        assertFalse(AccessibilitySecureSettings.canWriteSecureSettings(context))
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        assertTrue(AccessibilitySecureSettings.canWriteSecureSettings(context))
    }

    @Test
    fun fixRestartReturnsTrueWhenAlreadyRunningWithoutPermission() = runBlocking {
        WalkAccessibilityService.isRunning.value = true
        val result = AccessibilitySecureSettings.fixRestartAccessibilityIfNeeded(context)
        assertTrue(result)
    }

    @Test
    fun fixRestartReturnsFalseWithoutWriteSecureSettings() = runBlocking {
        WalkAccessibilityService.isRunning.value = false
        val result = AccessibilitySecureSettings.fixRestartAccessibilityIfNeeded(context)
        assertFalse(result)
    }

    @Test
    fun fixRestartWithPermissionTogglesComponentAndSetsAccessibilityEnabled() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        WalkAccessibilityService.isRunning.value = false

        val component = AccessibilitySecureSettings.accessibilityComponent(context)
        AccessibilitySecureSettings.putSecureA11yServices(context, setOf(component))

        // isRunning stays false in unit test (no real a11y bind) → result false after delays.
        val result = AccessibilitySecureSettings.fixRestartAccessibilityIfNeeded(context)
        assertFalse(result)

        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        assertEquals(1, enabled)

        val names = AccessibilitySecureSettings.getSecureA11yServices(context)
        assertTrue(
            names.any {
                it.packageName == component.packageName &&
                    it.className.contains("WalkAccessibilityService")
            } || names.any { it.flattenToShortString().contains("WalkAccessibilityService") }
        )
    }

    @Test
    fun accessibilityComponentMatchesPackageAndService() {
        val component = AccessibilitySecureSettings.accessibilityComponent(context)
        assertEquals(context.packageName, component.packageName)
        assertEquals(WalkAccessibilityService::class.java.name, component.className)
    }
}
