package org.walkguard.app.ui.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.data.db.AppPolicyEntity

data class AppPolicyEntry(
    val label: String,
    val packageName: String
)

class AppPolicyCatalogRepository(
    private val scanner: suspend () -> List<AppPolicyEntry>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private var cache: List<AppPolicyEntry>? = null
    private var inFlight: ScanFlight? = null

    suspend fun load(forceRefresh: Boolean = false): List<AppPolicyEntry> {
        while (true) {
            var isOwner = false
            val flight = mutex.withLock {
                val cached = cache
                if (!forceRefresh && cached != null) return cached

                inFlight ?: ScanFlight(
                    result = CompletableDeferred()
                ).also {
                    inFlight = it
                    isOwner = true
                }
            }

            if (isOwner) return runScan(flight)
            try {
                return flight.result.await().getOrThrow()
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private suspend fun runScan(flight: ScanFlight): List<AppPolicyEntry> = try {
        val scanned = withContext(dispatcher) { scanner() }
        finishScan(flight, Result.success(scanned), updateCache = true)
    } catch (error: CancellationException) {
        finishScan(flight, Result.failure(error), updateCache = false)
    } catch (error: Exception) {
        finishScan(flight, Result.failure(error), updateCache = false)
    } catch (error: Throwable) {
        finishScan(flight, Result.failure(error), updateCache = false)
    }

    private suspend fun finishScan(
        flight: ScanFlight,
        result: Result<List<AppPolicyEntry>>,
        updateCache: Boolean
    ): List<AppPolicyEntry> {
        withContext(NonCancellable) {
            mutex.withLock {
                if (updateCache) cache = result.getOrThrow()
                if (inFlight === flight) inFlight = null
                flight.result.complete(result)
            }
        }
        return result.getOrThrow()
    }

    private data class ScanFlight(
        val result: CompletableDeferred<Result<List<AppPolicyEntry>>>
    )
}

sealed class AppPolicyListFilter {
    data object All : AppPolicyListFilter()
    data class ByPolicy(val policy: AppPolicy) : AppPolicyListFilter()
}

fun effectiveAppPolicy(
    packageName: String,
    policyByPackage: Map<String, AppPolicyEntity>
): AppPolicy {
    return policyByPackage[packageName]?.policy
        ?.let { runCatching { AppPolicy.valueOf(it) }.getOrNull() }
        ?: AppPolicy.INHERIT
}

fun filterAppPolicyEntries(
    entries: List<AppPolicyEntry>,
    query: String,
    policyByPackage: Map<String, AppPolicyEntity> = emptyMap(),
    policyFilter: AppPolicyListFilter = AppPolicyListFilter.All
): List<AppPolicyEntry> {
    val trimmed = query.trim()
    val needle = trimmed.lowercase()
    return entries.filter { entry ->
        val matchesQuery = trimmed.isEmpty() ||
            entry.label.lowercase().contains(needle) ||
            entry.packageName.lowercase().contains(needle)
        if (!matchesQuery) return@filter false

        when (policyFilter) {
            AppPolicyListFilter.All -> true
            is AppPolicyListFilter.ByPolicy ->
                effectiveAppPolicy(entry.packageName, policyByPackage) == policyFilter.policy
        }
    }
}

fun buildAppPolicyCatalog(
    packageManager: PackageManager,
    selfPackageName: String,
    savedPolicies: List<AppPolicyEntity>
): List<AppPolicyEntry> = mergeAppPolicyCatalog(
    systemEntries = buildSystemAppCatalog(packageManager, selfPackageName),
    selfPackageName = selfPackageName,
    savedPolicies = savedPolicies
)

fun buildSystemAppCatalog(
    packageManager: PackageManager,
    selfPackageName: String
): List<AppPolicyEntry> {
    val byPackage = linkedMapOf<String, AppPolicyEntry>()

    for (resolveInfo in queryLauncherResolveInfos(packageManager)) {
        val packageName = resolveInfo.activityInfo?.packageName ?: continue
        if (packageName == selfPackageName) continue
        val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
            .ifBlank { packageName }
        byPackage.putIfAbsent(
            packageName,
            AppPolicyEntry(label = label, packageName = packageName)
        )
    }

    for (applicationInfo in queryInstalledApplications(packageManager)) {
        val packageName = applicationInfo.packageName
        if (packageName == selfPackageName) continue
        if (byPackage.containsKey(packageName)) continue
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: continue
        if (launchIntent.action != Intent.ACTION_MAIN) continue
        val label = applicationInfo.loadLabel(packageManager)?.toString().orEmpty()
            .ifBlank { packageName }
        byPackage[packageName] = AppPolicyEntry(label = label, packageName = packageName)
    }

    return sortAppPolicyEntries(byPackage.values)
}

fun mergeAppPolicyCatalog(
    systemEntries: List<AppPolicyEntry>,
    selfPackageName: String,
    savedPolicies: List<AppPolicyEntity>
): List<AppPolicyEntry> {
    val byPackage = linkedMapOf<String, AppPolicyEntry>()

    for (entry in systemEntries) {
        if (entry.packageName == selfPackageName) continue
        byPackage.putIfAbsent(entry.packageName, entry)
    }

    for (policy in savedPolicies) {
        val packageName = policy.packageName
        if (packageName == selfPackageName || byPackage.containsKey(packageName)) continue
        byPackage[packageName] = AppPolicyEntry(
            label = policy.label.ifBlank { packageName },
            packageName = packageName
        )
    }

    return sortAppPolicyEntries(byPackage.values)
}

private fun sortAppPolicyEntries(entries: Collection<AppPolicyEntry>): List<AppPolicyEntry> =
    entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

private fun queryLauncherResolveInfos(packageManager: PackageManager) = runCatching {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
}.getOrElse {
    @Suppress("DEPRECATION")
    packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        0
    )
}

private fun queryInstalledApplications(packageManager: PackageManager): List<ApplicationInfo> =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }
    }.getOrDefault(emptyList())
