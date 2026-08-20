package org.walkguard.app.guard

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors GKD-style resolution:
 * event package is a clue; right package comes from active root / focused app windows.
 */
class ForegroundPackageFilterTest {
    @Test
    fun onlyWindowStateChangedTriggersRefresh() {
        assertTrue(
            ForegroundPackageFilter.shouldRefreshForeground(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            )
        )
        assertFalse(
            ForegroundPackageFilter.shouldRefreshForeground(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            )
        )
        assertFalse(
            ForegroundPackageFilter.shouldRefreshForeground(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            )
        )
    }

    @Test
    fun olderEventTimeIsRejected() {
        assertTrue(ForegroundPackageFilter.isEventTimeAcceptable(100L, lastEventTime = 50L))
        assertFalse(ForegroundPackageFilter.isEventTimeAcceptable(40L, lastEventTime = 50L))
        assertTrue(ForegroundPackageFilter.isEventTimeAcceptable(50L, lastEventTime = 50L))
    }

    @Test
    fun securityCenterEventDoesNotOverrideWeChatWhenRootIsWeChat() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.miui.securitycenter",
            currentPackage = "com.tencent.mm",
            activeRootPackage = "com.tencent.mm",
            candidates = emptyList()
        )
        assertEquals("com.tencent.mm", right)
    }

    @Test
    fun securityCenterRootIsIgnoredAndKeepsPreviousByReturningNull() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.miui.securitycenter",
            currentPackage = "com.tencent.mm",
            activeRootPackage = "com.miui.securitycenter",
            candidates = listOf(
                candidate("com.miui.securitycenter", isFocused = true, area = 0L)
            )
        )
        assertNull(right)
    }

    @Test
    fun systemUiEventUsesRootWhenRootIsRealApp() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.android.systemui",
            currentPackage = "com.tencent.mm",
            activeRootPackage = "com.tencent.mm",
            candidates = emptyList()
        )
        assertEquals("com.tencent.mm", right)
    }

    @Test
    fun matchingEventAndCurrentUsesFastPathUnlessRootCorrects() {
        assertEquals(
            "com.tencent.mm",
            ForegroundPackageFilter.resolveRightPackage(
                eventPackage = "com.tencent.mm",
                currentPackage = "com.tencent.mm",
                activeRootPackage = null,
                candidates = emptyList()
            )
        )
        assertEquals(
            "com.ss.android.ugc.aweme",
            ForegroundPackageFilter.resolveRightPackage(
                eventPackage = "com.tencent.mm",
                currentPackage = "com.tencent.mm",
                activeRootPackage = "com.ss.android.ugc.aweme",
                candidates = emptyList()
            )
        )
    }

    @Test
    fun appSwitchUsesActiveRootEvenIfEventPackageDiffers() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.tencent.mm",
            currentPackage = "com.android.chrome",
            activeRootPackage = "com.tencent.mm",
            candidates = emptyList()
        )
        assertEquals("com.tencent.mm", right)
    }

    @Test
    fun fallsBackToFocusedApplicationWindowWhenRootMissing() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.miui.securitycenter",
            currentPackage = null,
            activeRootPackage = null,
            candidates = listOf(
                candidate("com.example.ghost", isActive = true, isFocused = false, area = 50_000L),
                candidate("com.tencent.mm", isActive = true, isFocused = true, area = 900_000L, layer = 4)
            )
        )
        assertEquals("com.tencent.mm", right)
    }

    @Test
    fun merelyActiveGhostWithoutRootIsRejected() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.example.ghost",
            currentPackage = "com.tencent.mm",
            activeRootPackage = null,
            candidates = listOf(
                candidate("com.example.ghost", isActive = true, isFocused = false, area = 50_000L)
            )
        )
        assertNull(right)
    }

    @Test
    fun secondaryUsageStatsFillsInWhenRootAndFocusedMissing() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.miui.securitycenter",
            currentPackage = null,
            activeRootPackage = null,
            candidates = emptyList(),
            secondaryPackage = "com.tencent.mm"
        )
        assertEquals("com.tencent.mm", right)
    }

    @Test
    fun secondaryUsageStatsDoesNotOverrideTrustedRoot() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.miui.securitycenter",
            currentPackage = "com.android.chrome",
            activeRootPackage = "com.android.chrome",
            candidates = emptyList(),
            secondaryPackage = "com.tencent.mm"
        )
        assertEquals("com.android.chrome", right)
    }

    @Test
    fun secondaryIgnoredPackageIsRejected() {
        val right = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = "com.miui.securitycenter",
            currentPackage = null,
            activeRootPackage = null,
            candidates = emptyList(),
            secondaryPackage = "com.android.systemui"
        )
        assertNull(right)
    }

    @Test
    fun ignoredPackagesIncludeSystemUiImeAndSecurityCenter() {
        assertTrue(ForegroundPackageFilter.isIgnoredTransientPackage("com.android.systemui"))
        assertTrue(
            ForegroundPackageFilter.isIgnoredTransientPackage(
                "com.google.android.inputmethod.latin"
            )
        )
        assertTrue(ForegroundPackageFilter.isIgnoredTransientPackage("com.miui.securitycenter"))
        assertFalse(ForegroundPackageFilter.isIgnoredTransientPackage("com.tencent.mm"))
    }

    private fun candidate(
        packageName: String,
        isApplication: Boolean = true,
        isActive: Boolean = true,
        isFocused: Boolean,
        layer: Int = 0,
        area: Long = 1_000_000L
    ) = ForegroundWindowCandidate(
        packageName = packageName,
        isApplication = isApplication,
        isActive = isActive,
        isFocused = isFocused,
        layer = layer,
        area = area
    )
}
