package org.walkguard.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "app_daily_stats",
    primaryKeys = ["day", "package_name"]
)
data class AppDailyStatsEntity(
    val day: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "intervention_count")
    val interventionCount: Int
)
