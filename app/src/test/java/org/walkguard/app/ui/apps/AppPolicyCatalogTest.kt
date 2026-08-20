package org.walkguard.app.ui.apps

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.data.db.AppPolicyEntity

class AppPolicyCatalogTest {

    @Test
    fun filterMatchesLabelCaseInsensitive() {
        val entries = listOf(
            AppPolicyEntry(label = "WeChat", packageName = "com.tencent.mm"),
            AppPolicyEntry(label = "WalkGuard", packageName = "org.walkguard.app")
        )
        val filtered = filterAppPolicyEntries(entries, "wechat")
        assertEquals(1, filtered.size)
        assertEquals("com.tencent.mm", filtered.single().packageName)
    }

    @Test
    fun filterMatchesPackageName() {
        val entries = listOf(
            AppPolicyEntry(label = "Demo", packageName = "com.example.alpha")
        )
        val filtered = filterAppPolicyEntries(entries, "example.alpha")
        assertEquals(1, filtered.size)
    }

    @Test
    fun emptyQueryReturnsAll() {
        val entries = listOf(
            AppPolicyEntry(label = "A", packageName = "a"),
            AppPolicyEntry(label = "B", packageName = "b")
        )
        assertEquals(entries, filterAppPolicyEntries(entries, "   "))
    }

    @Test
    fun noMatchReturnsEmpty() {
        val entries = listOf(AppPolicyEntry(label = "A", packageName = "a"))
        assertTrue(filterAppPolicyEntries(entries, "zzz").isEmpty())
    }

    @Test
    fun repositoryCachesSuccessfulInitialScan() = runBlocking {
        var scanCount = 0
        val expected = listOf(AppPolicyEntry(label = "Alpha", packageName = "alpha"))
        val repository = AppPolicyCatalogRepository(
            scanner = {
                scanCount += 1
                expected
            },
            dispatcher = Dispatchers.Unconfined
        )

        assertEquals(expected, repository.load())
        assertEquals(expected, repository.load())
        assertEquals(1, scanCount)
    }

    @Test
    fun forceRefreshRescansAndReplacesCache() = runBlocking {
        var scanCount = 0
        val repository = AppPolicyCatalogRepository(
            scanner = {
                scanCount += 1
                listOf(AppPolicyEntry(label = "Scan $scanCount", packageName = "app"))
            },
            dispatcher = Dispatchers.Unconfined
        )

        assertEquals("Scan 1", repository.load().single().label)
        assertEquals("Scan 2", repository.load(forceRefresh = true).single().label)
        assertEquals("Scan 2", repository.load().single().label)
        assertEquals(2, scanCount)
    }

    @Test
    fun failedRefreshThrowsAndRetainsPreviousCache() = runBlocking {
        var shouldFail = false
        var scanCount = 0
        val expected = listOf(AppPolicyEntry(label = "Cached", packageName = "cached"))
        val repository = AppPolicyCatalogRepository(
            scanner = {
                scanCount += 1
                if (shouldFail) error("refresh failed")
                expected
            },
            dispatcher = Dispatchers.Unconfined
        )

        assertEquals(expected, repository.load())
        shouldFail = true
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.load(forceRefresh = true) }
        }
        assertEquals("refresh failed", error.message)
        assertEquals(expected, repository.load())
        assertEquals(2, scanCount)
    }

    @Test
    fun concurrentFailedRefreshSharesFailureAndRetainsPreviousCache() = runBlocking {
        val scanCount = AtomicInteger()
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val expected = listOf(AppPolicyEntry(label = "Cached", packageName = "cached"))
        val refreshFailure = IllegalStateException("refresh failed")
        val repository = AppPolicyCatalogRepository(
            scanner = {
                when (scanCount.incrementAndGet()) {
                    1 -> expected
                    else -> {
                        refreshStarted.complete(Unit)
                        releaseRefresh.await()
                        throw refreshFailure
                    }
                }
            },
            dispatcher = Dispatchers.Default
        )
        assertEquals(expected, repository.load())

        val failures = supervisorScope {
            val owner = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.load(forceRefresh = true) }
            }
            refreshStarted.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.load(forceRefresh = true) }
            }
            releaseRefresh.complete(Unit)
            listOf(owner.await(), waiter.await())
        }

        assertEquals("refresh failed", failures[0].exceptionOrNull()?.message)
        assertSame(failures[0].exceptionOrNull(), failures[1].exceptionOrNull())
        assertEquals(expected, repository.load())
        assertEquals(2, scanCount.get())
    }

    @Test
    fun initialScanFailureIsPropagated() {
        val repository = AppPolicyCatalogRepository(
            scanner = { error("initial failed") },
            dispatcher = Dispatchers.Unconfined
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.load() }
        }
        assertEquals("initial failed", error.message)
    }

    @Test
    fun concurrentInitialLoadsShareOneScan() = runBlocking {
        val scanCount = AtomicInteger()
        val scanStarted = CompletableDeferred<Unit>()
        val releaseScan = CompletableDeferred<Unit>()
        val expected = listOf(AppPolicyEntry(label = "Shared", packageName = "shared"))
        val repository = AppPolicyCatalogRepository(
            scanner = {
                scanCount.incrementAndGet()
                scanStarted.complete(Unit)
                releaseScan.await()
                expected
            },
            dispatcher = Dispatchers.Default
        )

        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
        scanStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
        releaseScan.complete(Unit)

        assertEquals(listOf(expected, expected), listOf(first.await(), second.await()))
        assertEquals(1, scanCount.get())
    }

    @Test
    fun concurrentInitialLoadFailuresShareOneScanAndFailure() = runBlocking {
        val scanCount = AtomicInteger()
        val scanStarted = CompletableDeferred<Unit>()
        val releaseScan = CompletableDeferred<Unit>()
        val failures = supervisorScope {
            val repository = AppPolicyCatalogRepository(
                scanner = {
                    val attempt = scanCount.incrementAndGet()
                    scanStarted.complete(Unit)
                    releaseScan.await()
                    error("initial failed $attempt")
                },
                dispatcher = Dispatchers.Default
            )

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.load() }
            }
            scanStarted.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.load() }
            }
            releaseScan.complete(Unit)
            listOf(first.await(), second.await())
        }

        assertEquals(1, scanCount.get())
        assertEquals("initial failed 1", failures[0].exceptionOrNull()?.message)
        assertSame(failures[0].exceptionOrNull(), failures[1].exceptionOrNull())
    }

    @Test
    fun concurrentAssertionErrorsShareThrowableAndAllowRetry() = runBlocking {
        val scanCount = AtomicInteger()
        val scanStarted = CompletableDeferred<Unit>()
        val releaseScan = CompletableDeferred<Unit>()
        val failure = AssertionError("scanner assertion failed")
        val expected = listOf(AppPolicyEntry(label = "Recovered", packageName = "recovered"))
        val repository = AppPolicyCatalogRepository(
            scanner = {
                val attempt = scanCount.incrementAndGet()
                if (attempt == 1) {
                    scanStarted.complete(Unit)
                    releaseScan.await()
                    throw failure
                }
                expected
            },
            dispatcher = Dispatchers.Default
        )

        val failures = supervisorScope {
            val owner = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.load() }
            }
            scanStarted.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { repository.load() }
            }
            releaseScan.complete(Unit)
            withTimeout(5_000) { listOf(owner.await(), waiter.await()) }
        }

        assertEquals(1, scanCount.get())
        assertSame(failures[0].exceptionOrNull(), failures[1].exceptionOrNull())
        assertEquals(expected, withTimeout(5_000) { repository.load() })
        assertEquals(2, scanCount.get())
    }

    @Test
    fun cancellingOwnerAllowsActiveWaiterToRetryAndCacheResult() = runBlocking {
        val scanCount = AtomicInteger()
        val scanStarted = CompletableDeferred<Unit>()
        val scannerCancelled = CompletableDeferred<Unit>()
        val expected = listOf(AppPolicyEntry(label = "Recovered", packageName = "recovered"))
        val repository = AppPolicyCatalogRepository(
            scanner = {
                val attempt = scanCount.incrementAndGet()
                if (attempt == 1) {
                    scanStarted.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                    } finally {
                        scannerCancelled.complete(Unit)
                    }
                }
                expected
            },
            dispatcher = Dispatchers.Default
        )

        val (ownerCancellation, waiterResult) = supervisorScope {
            val owner = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
            scanStarted.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
            owner.cancel()
            withTimeout(5_000) {
                scannerCancelled.await()
                val cancellation = try {
                    owner.await()
                    null
                } catch (error: CancellationException) {
                    error
                }
                cancellation to waiter.await()
            }
        }

        assertTrue(ownerCancellation is CancellationException)
        assertEquals(expected, waiterResult)
        assertEquals(2, scanCount.get())
        assertEquals(expected, withTimeout(5_000) { repository.load() })
        assertEquals(2, scanCount.get())
    }

    @Test
    fun cancellingWaiterDoesNotCancelOwnerOrRetryScan() = runBlocking {
        val scanCount = AtomicInteger()
        val scanStarted = CompletableDeferred<Unit>()
        val releaseScan = CompletableDeferred<Unit>()
        val expected = listOf(AppPolicyEntry(label = "Shared", packageName = "shared"))
        val repository = AppPolicyCatalogRepository(
            scanner = {
                scanCount.incrementAndGet()
                scanStarted.complete(Unit)
                releaseScan.await()
                expected
            },
            dispatcher = Dispatchers.Default
        )

        val (waiterCancellation, ownerResult) = supervisorScope {
            val owner = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
            scanStarted.await()
            val waiter = async(start = CoroutineStart.UNDISPATCHED) { repository.load() }
            waiter.cancel()
            withTimeout(5_000) {
                val cancellation = try {
                    waiter.await()
                    null
                } catch (error: CancellationException) {
                    error
                }
                releaseScan.complete(Unit)
                cancellation to owner.await()
            }
        }

        assertTrue(waiterCancellation is CancellationException)
        assertEquals(expected, ownerResult)
        assertEquals(1, scanCount.get())
        assertEquals(expected, withTimeout(5_000) { repository.load() })
        assertEquals(1, scanCount.get())
    }

    @Test
    fun scannerRunsOnInjectedDispatcher() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "app-catalog-scanner")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val repository = AppPolicyCatalogRepository(
                scanner = {
                    listOf(
                        AppPolicyEntry(
                            label = Thread.currentThread().name,
                            packageName = "thread"
                        )
                    )
                },
                dispatcher = dispatcher
            )

            val result = runBlocking { repository.load() }

            assertTrue(result.single().label.startsWith("app-catalog-scanner"))
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun mergeKeepsSystemEntriesAndAddsSavedOnlyPolicies() {
        val systemEntries = listOf(
            AppPolicyEntry(label = "System Label", packageName = "existing"),
            AppPolicyEntry(label = "Ignored Duplicate", packageName = "existing"),
            AppPolicyEntry(label = "WalkGuard", packageName = "self")
        )
        val savedPolicies = listOf(
            policy(packageName = "existing", label = "Saved Label"),
            policy(packageName = "saved", label = "Saved Only"),
            policy(packageName = "blank", label = ""),
            policy(packageName = "self", label = "Saved Self")
        )

        val merged = mergeAppPolicyCatalog(
            systemEntries = systemEntries,
            selfPackageName = "self",
            savedPolicies = savedPolicies
        )

        assertEquals(
            listOf(
                AppPolicyEntry(label = "blank", packageName = "blank"),
                AppPolicyEntry(label = "Saved Only", packageName = "saved"),
                AppPolicyEntry(label = "System Label", packageName = "existing")
            ),
            merged
        )
    }

    @Test
    fun effectivePolicyDefaultsToInheritWhenMissing() {
        assertEquals(
            AppPolicy.INHERIT,
            effectiveAppPolicy("missing", emptyMap())
        )
    }

    @Test
    fun effectivePolicyReadsSavedEnumName() {
        val map = mapOf(
            "com.a" to AppPolicyEntity(
                packageName = "com.a",
                label = "A",
                policy = AppPolicy.WHITELIST.name,
                updatedAtEpochMs = 1L
            )
        )
        assertEquals(AppPolicy.WHITELIST, effectiveAppPolicy("com.a", map))
    }

    @Test
    fun effectivePolicyFallsBackToInheritForCorruptString() {
        val map = mapOf(
            "com.a" to AppPolicyEntity(
                packageName = "com.a",
                label = "A",
                policy = "not_a_policy",
                updatedAtEpochMs = 1L
            )
        )
        assertEquals(AppPolicy.INHERIT, effectiveAppPolicy("com.a", map))
    }

    @Test
    fun policyFilterAllKeepsSearchOnlyBehavior() {
        val entries = listOf(
            AppPolicyEntry(label = "WeChat", packageName = "com.tencent.mm"),
            AppPolicyEntry(label = "Maps", packageName = "com.maps")
        )
        val filtered = filterAppPolicyEntries(
            entries = entries,
            query = "wechat",
            policyByPackage = emptyMap(),
            policyFilter = AppPolicyListFilter.All
        )
        assertEquals(listOf(entries[0]), filtered)
    }

    @Test
    fun policyFilterWhitelistKeepsOnlyWhitelistApps() {
        val entries = listOf(
            AppPolicyEntry(label = "A", packageName = "a"),
            AppPolicyEntry(label = "B", packageName = "b"),
            AppPolicyEntry(label = "C", packageName = "c")
        )
        val policyByPackage = mapOf(
            "a" to AppPolicyEntity("a", "A", AppPolicy.WHITELIST.name, 1L),
            "b" to AppPolicyEntity("b", "B", AppPolicy.RAGE.name, 1L)
            // c missing => INHERIT
        )
        val filtered = filterAppPolicyEntries(
            entries = entries,
            query = "",
            policyByPackage = policyByPackage,
            policyFilter = AppPolicyListFilter.ByPolicy(AppPolicy.WHITELIST)
        )
        assertEquals(listOf(entries[0]), filtered)
    }

    @Test
    fun policyFilterInheritIncludesMissingAndSavedInherit() {
        val entries = listOf(
            AppPolicyEntry(label = "A", packageName = "a"),
            AppPolicyEntry(label = "B", packageName = "b"),
            AppPolicyEntry(label = "C", packageName = "c")
        )
        val policyByPackage = mapOf(
            "a" to AppPolicyEntity("a", "A", AppPolicy.INHERIT.name, 1L),
            "b" to AppPolicyEntity("b", "B", AppPolicy.MILD.name, 1L)
            // c missing
        )
        val filtered = filterAppPolicyEntries(
            entries = entries,
            query = "",
            policyByPackage = policyByPackage,
            policyFilter = AppPolicyListFilter.ByPolicy(AppPolicy.INHERIT)
        )
        assertEquals(listOf(entries[0], entries[2]), filtered)
    }

    @Test
    fun policyFilterAndSearchAreCombinedWithAnd() {
        val entries = listOf(
            AppPolicyEntry(label = "WeChat", packageName = "com.tencent.mm"),
            AppPolicyEntry(label = "Work WeChat", packageName = "com.tencent.wework"),
            AppPolicyEntry(label = "Maps", packageName = "com.maps")
        )
        val policyByPackage = mapOf(
            "com.tencent.mm" to AppPolicyEntity(
                "com.tencent.mm", "WeChat", AppPolicy.WHITELIST.name, 1L
            ),
            "com.tencent.wework" to AppPolicyEntity(
                "com.tencent.wework", "Work WeChat", AppPolicy.NORMAL.name, 1L
            ),
            "com.maps" to AppPolicyEntity(
                "com.maps", "Maps", AppPolicy.WHITELIST.name, 1L
            )
        )
        val filtered = filterAppPolicyEntries(
            entries = entries,
            query = "wechat",
            policyByPackage = policyByPackage,
            policyFilter = AppPolicyListFilter.ByPolicy(AppPolicy.WHITELIST)
        )
        assertEquals(listOf(entries[0]), filtered)
    }

    @Test
    fun twoArgFilterOverloadStillWorks() {
        val entries = listOf(AppPolicyEntry(label = "A", packageName = "a"))
        assertEquals(entries, filterAppPolicyEntries(entries, ""))
        assertTrue(filterAppPolicyEntries(entries, "zzz").isEmpty())
    }

    private fun policy(packageName: String, label: String) = AppPolicyEntity(
        packageName = packageName,
        label = label,
        policy = "default",
        updatedAtEpochMs = 0L
    )
}
