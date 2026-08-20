package org.walkguard.app.guard

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level assertions for GKD a11y keepalive ports on [WalkAccessibilityService].
 */
class WalkAccessibilityServiceSourceTest {
    @Test
    fun companionExposesIsRunningStateFlowAndInstance() {
        val source = walkAccessibilityServiceSource().readText()

        assertTrue(source.contains("val isRunning = MutableStateFlow(false)"))
        assertTrue(source.contains("@Volatile"))
        assertTrue(source.contains("var instance: WalkAccessibilityService?"))
        assertTrue(source.contains("isRunning.value = true"))
        assertTrue(source.contains("isRunning.value = false"))
        assertTrue(source.contains("instance = this"))
        assertTrue(source.contains("instance = null"))
    }

    @Test
    fun onServiceConnectedAttachesOverlayAndSyncsGuard() {
        val source = walkAccessibilityServiceSource().readText()
        val connectedIndex = source.indexOf("override fun onServiceConnected()")
        val destroyIndex = source.indexOf("override fun onDestroy()")

        assertTrue(connectedIndex >= 0)
        assertTrue(destroyIndex > connectedIndex)

        val connectedBody = source.substring(connectedIndex, destroyIndex)
        assertTrue(connectedBody.contains("AccessibilityOverlayKeeper.attach(this)"))
        assertTrue(connectedBody.contains("GuardRuntimeSyncer.syncGuardState"))
        assertTrue(connectedBody.contains("GuardSyncReason.AccessibilityConnected"))
    }

    @Test
    fun onDestroyDetachesOverlayAndClearsRunningState() {
        val source = walkAccessibilityServiceSource().readText()
        val destroyIndex = source.indexOf("override fun onDestroy()")
        assertTrue(destroyIndex >= 0)

        val destroyBody = source.substring(destroyIndex, destroyIndex + 400)
        assertTrue(destroyBody.contains("AccessibilityOverlayKeeper.detach(this)"))
        assertTrue(destroyBody.contains("isRunning.value = false"))
        assertTrue(destroyBody.contains("instance = null"))
    }

    @Test
    fun overlayKeeperUsesTypeAccessibilityOverlayOneByOne() {
        val source = accessibilityOverlayKeeperSource().readText()

        assertTrue(source.contains("TYPE_ACCESSIBILITY_OVERLAY"))
        assertTrue(source.contains("FLAG_NOT_TOUCHABLE"))
        assertTrue(source.contains("FLAG_NOT_FOCUSABLE"))
        assertTrue(source.contains("width = 1"))
        assertTrue(source.contains("height = 1"))
        assertTrue(source.contains("catch (_: Throwable)"))
    }

    @Test
    fun windowProbeExecutorIsInstanceScopedAndRecreatedIfShutdown() {
        val source = walkAccessibilityServiceSource().readText()

        // Must not be a permanent companion-level executor that dies after first onDestroy.
        assertFalse(
            Regex(
                """companion object[\s\S]*?private val windowProbeExecutor\s*=\s*Executors"""
            ).containsMatchIn(source)
        )
        assertTrue(source.contains("private var windowProbeExecutor"))
        assertTrue(source.contains("ensureWindowProbeExecutor"))
        assertTrue(source.contains("isShutdown"))
        assertTrue(source.contains("newWindowProbeExecutor"))
        assertTrue(source.contains("shutdownNow()"))
    }

    private fun walkAccessibilityServiceSource(): File {
        val candidates = listOf(
            File("src/main/java/org/walkguard/app/guard/WalkAccessibilityService.kt"),
            File("app/src/main/java/org/walkguard/app/guard/WalkAccessibilityService.kt")
        )
        return candidates.first { it.exists() }
    }

    private fun accessibilityOverlayKeeperSource(): File {
        val candidates = listOf(
            File("src/main/java/org/walkguard/app/guard/AccessibilityOverlayKeeper.kt"),
            File("app/src/main/java/org/walkguard/app/guard/AccessibilityOverlayKeeper.kt")
        )
        return candidates.first { it.exists() }
    }
}
