package org.walkguard.app.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageStatsForegroundResolverTest {
    @Test
    fun picksLatestNonIgnoredPackage() {
        val selected = UsageStatsForegroundResolver.latestNonIgnoredPackage(
            listOf(
                UsageForegroundEvent("com.tencent.mm", 1_000L),
                UsageForegroundEvent("com.android.systemui", 2_000L),
                UsageForegroundEvent("com.miui.securitycenter", 3_000L)
            )
        )
        assertEquals("com.tencent.mm", selected)
    }

    @Test
    fun prefersNewestRealAppOverOlderOnes() {
        val selected = UsageStatsForegroundResolver.latestNonIgnoredPackage(
            listOf(
                UsageForegroundEvent("com.android.chrome", 1_000L),
                UsageForegroundEvent("com.tencent.mm", 4_000L),
                UsageForegroundEvent("com.android.systemui", 5_000L)
            )
        )
        assertEquals("com.tencent.mm", selected)
    }

    @Test
    fun returnsNullWhenOnlyIgnoredPackagesExist() {
        val selected = UsageStatsForegroundResolver.latestNonIgnoredPackage(
            listOf(
                UsageForegroundEvent("com.android.systemui", 1_000L),
                UsageForegroundEvent("com.miui.securitycenter", 2_000L)
            )
        )
        assertNull(selected)
    }
}
