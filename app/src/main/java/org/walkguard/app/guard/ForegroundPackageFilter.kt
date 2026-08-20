package org.walkguard.app.guard

import android.view.accessibility.AccessibilityEvent

data class ForegroundWindowCandidate(
    val packageName: String?,
    val isApplication: Boolean,
    val isActive: Boolean,
    val isFocused: Boolean,
    val layer: Int = 0,
    val area: Long = 0L
)

/**
 * GKD-inspired foreground package resolution.
 *
 * Accessibility event package names are untrusted clues. The "right" package is resolved from
 * the active window root first, then focused application windows. Transient OEM/system packages
 * never become the published foreground app.
 */
object ForegroundPackageFilter {
    fun shouldRefreshForeground(eventType: Int): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    fun isEventTimeAcceptable(eventTime: Long, lastEventTime: Long): Boolean {
        // GKD drops negative / out-of-order event times from noisy apps.
        if (eventTime < 0L) return false
        return eventTime >= lastEventTime
    }

    /**
     * @return package to publish, or null to keep the previous tracker value.
     */
    fun resolveRightPackage(
        eventPackage: String?,
        currentPackage: String?,
        activeRootPackage: String?,
        candidates: List<ForegroundWindowCandidate>,
        secondaryPackage: String? = null
    ): String? {
        val evAppId = normalizePackage(eventPackage)
        val currentAppId = normalizePackage(currentPackage)
        val rootAppId = normalizePackage(activeRootPackage)
            ?.takeUnless(::isIgnoredTransientPackage)
        val secondaryAppId = normalizePackage(secondaryPackage)
            ?.takeUnless(::isIgnoredTransientPackage)

        // GKD fast path: when event package already matches the trusted current package,
        // avoid thrashing. Still prefer a better active-root correction when available.
        // secondaryPackage mirrors GKD's topCpn()/Shizuku fallback (UsageStats here).
        val rightAppId = if (
            currentAppId != null &&
            currentAppId == evAppId &&
            !isIgnoredTransientPackage(currentAppId)
        ) {
            rootAppId ?: currentAppId
        } else {
            rootAppId
                ?: selectFocusedApplicationPackage(candidates)
                ?: secondaryAppId
        }

        if (rightAppId == null) return null
        if (isIgnoredTransientPackage(rightAppId)) return null
        return rightAppId
    }

    fun selectForegroundPackage(
        candidates: List<ForegroundWindowCandidate>,
        activeRootPackage: String? = null
    ): String? {
        return resolveRightPackage(
            eventPackage = null,
            currentPackage = null,
            activeRootPackage = activeRootPackage,
            candidates = candidates
        )
    }

    fun isIgnoredTransientPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        if (lower == SYSTEM_UI_PACKAGE) return true
        if (lower.contains("inputmethod")) return true
        if (lower.endsWith(".ime")) return true
        if (lower.contains("securitycenter")) return true
        if (lower.contains("permissioncontroller")) return true
        if (lower.contains("packageinstaller")) return true
        if (lower.contains("miui.notification")) return true
        if (lower == "com.miui.aod") return true
        return false
    }

    private fun selectFocusedApplicationPackage(
        candidates: List<ForegroundWindowCandidate>
    ): String? {
        return candidates
            .asSequence()
            .filter { it.isApplication && it.isFocused }
            .mapNotNull { candidate ->
                val packageName = normalizePackage(candidate.packageName) ?: return@mapNotNull null
                if (isIgnoredTransientPackage(packageName)) return@mapNotNull null
                if (candidate.area < MIN_VISIBLE_AREA_PX) return@mapNotNull null
                packageName to candidate
            }
            .maxByOrNull { (_, candidate) -> candidate.layer }
            ?.first
    }

    private fun normalizePackage(packageName: CharSequence?): String? {
        return packageName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val MIN_VISIBLE_AREA_PX = 10_000L
}
