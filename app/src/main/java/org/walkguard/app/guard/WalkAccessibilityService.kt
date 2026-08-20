package org.walkguard.app.guard

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground package detection modeled after GKD:
 * - event.packageName is only a clue
 * - right package comes from safe active window root (timeout-protected)
 * - focused application windows are fallback
 * - out-of-order event times and transient packages are ignored
 *
 * Keepalive ports from GKD A11yService:
 * - companion [isRunning] / [instance]
 * - 1×1 TYPE_ACCESSIBILITY_OVERLAY via [AccessibilityOverlayKeeper]
 * - instance-scoped window probe executor (recreated if shut down)
 */
class WalkAccessibilityService : AccessibilityService() {
    private var lastEventTime = 0L
    private val cachedRootPackage = AtomicReference<Pair<Long, String?>?>(null)
    private val usageStatsSource by lazy { UsageStatsRecentForegroundSource(this) }

    /**
     * Instance-scoped executor so [onDestroy] shutdown does not permanently kill a companion
     * executor shared across service recreations (GKD lifecycle fix).
     */
    @Volatile
    private var windowProbeExecutor: ExecutorService = newWindowProbeExecutor()

    override fun onCreate() {
        super.onCreate()
        markRunning()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        markRunning()
        AccessibilityOverlayKeeper.attach(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                GuardRuntimeSyncer.syncGuardState(
                    context = this@WalkAccessibilityService,
                    reason = GuardSyncReason.AccessibilityConnected,
                )
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accessibilityEvent = event ?: return
        if (!ForegroundPackageFilter.shouldRefreshForeground(accessibilityEvent.eventType)) {
            return
        }
        if (!ForegroundPackageFilter.isEventTimeAcceptable(
                eventTime = accessibilityEvent.eventTime,
                lastEventTime = lastEventTime
            )
        ) {
            return
        }
        lastEventTime = accessibilityEvent.eventTime

        val eventPackage = accessibilityEvent.packageName?.toString()
        val currentPackage = ForegroundAppTracker.currentPackageFlow.value
        val activeRootPackage = safeActiveWindowPackage()
        val candidates = collectWindowCandidates()
        // Only pay for UsageStats when a11y primary sources are empty (GKD topCpn fallback style).
        val secondaryPackage = if (
            activeRootPackage.isNullOrBlank() &&
            candidates.none {
                it.isApplication &&
                    it.isFocused &&
                    !ForegroundPackageFilter.isIgnoredTransientPackage(it.packageName.orEmpty())
            }
        ) {
            usageStatsSource.recentForegroundPackage()
        } else {
            null
        }

        val rightPackage = ForegroundPackageFilter.resolveRightPackage(
            eventPackage = eventPackage,
            currentPackage = currentPackage,
            activeRootPackage = activeRootPackage,
            candidates = candidates,
            secondaryPackage = secondaryPackage
        )
        ForegroundAppTracker.updateCurrentPackage(rightPackage)
    }

    override fun onInterrupt() {
        ForegroundAppTracker.clear()
    }

    override fun onDestroy() {
        AccessibilityOverlayKeeper.detach(this)
        shutdownWindowProbeExecutor()
        ForegroundAppTracker.clear()
        isRunning.value = false
        instance = null
        super.onDestroy()
    }

    /**
     * GKD wraps rootInActiveWindow with try/catch and short timeouts because some apps block
     * for hundreds of milliseconds and stall the event pipeline.
     */
    private fun safeActiveWindowPackage(): String? {
        val now = System.currentTimeMillis()
        cachedRootPackage.get()?.let { (at, packageName) ->
            if (now - at <= ROOT_PACKAGE_CACHE_MS) return packageName
        }

        val packageName = try {
            ensureWindowProbeExecutor().submit<String?> {
                val root = try {
                    rootInActiveWindow
                } catch (_: Throwable) {
                    null
                }
                readPackageName(root)
            }.get(ROOT_PACKAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            null
        }

        cachedRootPackage.set(now to packageName)
        return packageName
    }

    private fun collectWindowCandidates(): List<ForegroundWindowCandidate> {
        val windowList = try {
            windows
        } catch (_: Throwable) {
            null
        }.orEmpty()

        return windowList.mapNotNull { window ->
            try {
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val packageName = readPackageName(window.root)
                ForegroundWindowCandidate(
                    packageName = packageName,
                    isApplication = window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                    isActive = window.isActive,
                    isFocused = window.isFocused,
                    layer = window.layer,
                    area = bounds.width().toLong().coerceAtLeast(0L) *
                        bounds.height().toLong().coerceAtLeast(0L)
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun readPackageName(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        return try {
            root.packageName?.toString()
        } catch (_: Throwable) {
            null
        } finally {
            try {
                @Suppress("DEPRECATION")
                root.recycle()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun markRunning() {
        instance = this
        isRunning.value = true
    }

    private fun ensureWindowProbeExecutor(): ExecutorService {
        val current = windowProbeExecutor
        if (!current.isShutdown) return current
        val recreated = newWindowProbeExecutor()
        windowProbeExecutor = recreated
        return recreated
    }

    private fun shutdownWindowProbeExecutor() {
        runCatching { windowProbeExecutor.shutdownNow() }
    }

    companion object {
        private const val ROOT_PACKAGE_TIMEOUT_MS = 100L
        private const val ROOT_PACKAGE_CACHE_MS = 30L

        val isRunning = MutableStateFlow(false)

        @Volatile
        var instance: WalkAccessibilityService? = null
            private set

        private fun newWindowProbeExecutor(): ExecutorService {
            return Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "walkguard-a11y-window").apply { isDaemon = true }
            }
        }
    }
}
