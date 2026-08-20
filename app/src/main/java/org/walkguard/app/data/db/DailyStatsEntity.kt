package org.walkguard.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey
    val day: String,
    @ColumnInfo(name = "mild_count")
    val mildCount: Int,
    @ColumnInfo(name = "normal_count")
    val normalCount: Int,
    @ColumnInfo(name = "rage_count")
    val rageCount: Int,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
