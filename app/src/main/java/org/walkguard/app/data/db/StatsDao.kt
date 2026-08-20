package org.walkguard.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.walkguard.app.core.model.GuardMode

@Dao
interface StatsDao {
    @Query("SELECT * FROM daily_stats WHERE day = :day")
    suspend fun getDailyStats(day: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats WHERE day = :day")
    fun observeDailyStats(day: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM app_daily_stats WHERE day = :day AND package_name = :packageName")
    suspend fun getAppDailyStats(day: String, packageName: String): AppDailyStatsEntity?

    @Query("SELECT * FROM app_daily_stats WHERE day = :day ORDER BY intervention_count DESC LIMIT :limit")
    suspend fun getTopAppDailyStats(day: String, limit: Int): List<AppDailyStatsEntity>

    @Query("SELECT * FROM app_daily_stats WHERE day = :day ORDER BY intervention_count DESC LIMIT :limit")
    fun observeTopAppDailyStats(day: String, limit: Int): Flow<List<AppDailyStatsEntity>>

    @Query("""
        INSERT OR IGNORE INTO daily_stats(day, mild_count, normal_count, rage_count, updated_at_epoch_ms)
        VALUES(:day, 0, 0, 0, :updatedAtEpochMs)
    """)
    suspend fun ensureDailyStats(day: String, updatedAtEpochMs: Long)

    @Query("UPDATE daily_stats SET mild_count = mild_count + 1, updated_at_epoch_ms = :updatedAtEpochMs WHERE day = :day")
    suspend fun incrementMildCount(day: String, updatedAtEpochMs: Long)

    @Query("UPDATE daily_stats SET normal_count = normal_count + 1, updated_at_epoch_ms = :updatedAtEpochMs WHERE day = :day")
    suspend fun incrementNormalCount(day: String, updatedAtEpochMs: Long)

    @Query("UPDATE daily_stats SET rage_count = rage_count + 1, updated_at_epoch_ms = :updatedAtEpochMs WHERE day = :day")
    suspend fun incrementRageCount(day: String, updatedAtEpochMs: Long)

    @Transaction
    suspend fun incrementModeCount(day: String, modeName: String, updatedAtEpochMs: Long) {
        ensureDailyStats(day, updatedAtEpochMs)
        when (GuardMode.valueOf(modeName)) {
            GuardMode.MILD -> incrementMildCount(day, updatedAtEpochMs)
            GuardMode.NORMAL -> incrementNormalCount(day, updatedAtEpochMs)
            GuardMode.RAGE -> incrementRageCount(day, updatedAtEpochMs)
        }
    }

    @Query("""
        INSERT OR IGNORE INTO app_daily_stats(day, package_name, intervention_count)
        VALUES(:day, :packageName, 0)
    """)
    suspend fun ensureAppDailyStats(day: String, packageName: String)

    @Query("""
        UPDATE app_daily_stats
        SET intervention_count = intervention_count + 1
        WHERE day = :day AND package_name = :packageName
    """)
    suspend fun incrementExistingAppDailyCount(day: String, packageName: String)

    @Transaction
    suspend fun incrementAppDailyCount(day: String, packageName: String) {
        ensureAppDailyStats(day, packageName)
        incrementExistingAppDailyCount(day, packageName)
    }
}
