package org.walkguard.app.guard

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * GKD-style Secure Settings helpers for accessibility repair (no Shizuku).
 *
 * Port of `App.getSecureA11yServices` / `putSecureA11yServices` and
 * `GkdTileService.fixA11yService` timing (remove → delay 1s → add → delay 2s).
 */
object AccessibilitySecureSettings {
    private const val ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ':'
    private const val A11Y_AWAIT_FIX_TIME = 1000L
    private const val A11Y_AWAIT_START_TIME = 2000L

    private val modifyA11yMutex = Mutex()

    fun canWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun accessibilityComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, WalkAccessibilityService::class.java.name)
    }

    fun getSecureA11yServices(context: Context): MutableSet<ComponentName> {
        val value = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (value.isNullOrEmpty()) return mutableSetOf()
        return value.split(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR)
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .toHashSet()
    }

    fun putSecureA11yServices(context: Context, services: Set<ComponentName>): Boolean {
        return Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.joinToString(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR.toString()) {
                it.flattenToShortString()
            }
        )
    }

    fun putSecureInt(context: Context, name: String, value: Int): Boolean {
        return Settings.Secure.putInt(context.contentResolver, name, value)
    }

    /**
     * If accessibility is not running and WRITE_SECURE_SETTINGS is granted, restart the service
     * by toggling its component in ENABLED_ACCESSIBILITY_SERVICES (GKD fixA11yService timing).
     *
     * @return true when accessibility is running after the attempt (or was already running)
     */
    suspend fun fixRestartAccessibilityIfNeeded(context: Context): Boolean {
        if (WalkAccessibilityService.isRunning.value) return true
        if (!canWriteSecureSettings(context)) return false

        return modifyA11yMutex.withLock {
            if (WalkAccessibilityService.isRunning.value) return@withLock true

            val app = context.applicationContext
            val component = accessibilityComponent(app)
            val names = getSecureA11yServices(app)
            putSecureInt(app, Settings.Secure.ACCESSIBILITY_ENABLED, 1)

            // Listed but dead → remove first so the system will restart on re-add.
            if (names.contains(component) || names.any { it.flattenToString() == component.flattenToString() || it.flattenToShortString() == component.flattenToShortString() }) {
                names.removeAll { it.flattenToString() == component.flattenToString() || it.flattenToShortString() == component.flattenToShortString() }
                putSecureA11yServices(app, names)
                delay(A11Y_AWAIT_FIX_TIME)
            }

            names.add(component)
            putSecureA11yServices(app, names)
            delay(A11Y_AWAIT_START_TIME)
            WalkAccessibilityService.isRunning.value
        }
    }
}
