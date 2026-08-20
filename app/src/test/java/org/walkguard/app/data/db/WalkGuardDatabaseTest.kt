package org.walkguard.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.walkguard.app.core.model.AppPolicy
import org.walkguard.app.core.model.GuardMode

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class WalkGuardDatabaseTest {
    private lateinit var database: WalkGuardDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WalkGuardDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun appPolicyCanBeInsertedAndReadByPackageName() = runBlocking {
        val entity = AppPolicyEntity(
            packageName = "com.example.app",
            label = "Example",
            policy = AppPolicy.RAGE.name,
            updatedAtEpochMs = 1L
        )

        database.appPolicyDao().upsert(entity)

        assertEquals(entity, database.appPolicyDao().getPolicy("com.example.app"))
    }

    @Test fun dailyModeCountCanBeIncremented() = runBlocking {
        database.statsDao().incrementModeCount(
            day = "2026-07-05",
            modeName = GuardMode.RAGE.name,
            updatedAtEpochMs = 10L
        )

        val dailyStats = database.statsDao().getDailyStats("2026-07-05")

        assertNotNull(dailyStats)
        assertEquals(0, dailyStats!!.mildCount)
        assertEquals(0, dailyStats.normalCount)
        assertEquals(1, dailyStats.rageCount)
        assertEquals(10L, dailyStats.updatedAtEpochMs)
    }

    @Test fun appDailyStatsCanBeIncrementedForSamePackage() = runBlocking {
        database.statsDao().incrementAppDailyCount("2026-07-05", "com.example.app")
        database.statsDao().incrementAppDailyCount("2026-07-05", "com.example.app")

        val appStats = database.statsDao().getAppDailyStats("2026-07-05", "com.example.app")

        assertNotNull(appStats)
        assertEquals(2, appStats!!.interventionCount)
    }
}
