package org.walkguard.app.guard

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

data class UsageForegroundEvent(
    val packageName: String,
    val timestampMs: Long
)

/**
 * GKD uses Shizuku topCpn() as a second source when accessibility root is slow/null.
 * WalkGuard mirrors that role with UsageStats recent foreground events when usage access is granted.
 */
object UsageStatsForegroundResolver {
    fun latestNonIgnoredPackage(events: List<UsageForegroundEvent>): String? {
        return events
            .asSequence()
            .sortedByDescending { it.timestampMs }
            .map { it.packageName.trim() }
            .filter { it.isNotEmpty() }
            .firstOrNull { !ForegroundPackageFilter.isIgnoredTransientPackage(it) }
    }
}

class UsageStatsRecentForegroundSource(
    private val context: Context,
    private val lookbackMs: Long = DEFAULT_LOOKBACK_MS
) {
    private val appContext = context.applicationContext

    fun isGranted(): Boolean {
        val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.packageName
                )
            }
        } catch (_: Throwable) {
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun recentForegroundPackage(): String? {
        if (!isGranted()) return null
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = end - lookbackMs
        val usageEvents = try {
            manager.queryEvents(start, end)
        } catch (_: Throwable) {
            return null
        } ?: return null

        val collected = mutableListOf<UsageForegroundEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (!isForegroundMove(event.eventType)) continue
            val packageName = event.packageName?.trim().orEmpty()
            if (packageName.isEmpty()) continue
            collected += UsageForegroundEvent(
                packageName = packageName,
                timestampMs = event.timeStamp
            )
        }
        return UsageStatsForegroundResolver.latestNonIgnoredPackage(collected)
    }

    private fun isForegroundMove(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
    }

    private companion object {
        const val DEFAULT_LOOKBACK_MS = 5_000L
    }
}
