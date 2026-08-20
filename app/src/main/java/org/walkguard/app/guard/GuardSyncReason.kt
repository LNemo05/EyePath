package org.walkguard.app.guard

/**
 * Entry points that request a guard runtime reconcile (GKD multi-entry recovery style).
 */
enum class GuardSyncReason {
    MainActivity,
    Boot,
    AccessibilityConnected,
    PackageReplaced,
    Tile,
    Application,
    Screen,
}
