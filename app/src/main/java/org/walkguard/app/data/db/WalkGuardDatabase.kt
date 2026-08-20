package org.walkguard.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppPolicyEntity::class,
        DailyStatsEntity::class,
        AppDailyStatsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class WalkGuardDatabase : RoomDatabase() {
    abstract fun appPolicyDao(): AppPolicyDao
    abstract fun statsDao(): StatsDao
}
