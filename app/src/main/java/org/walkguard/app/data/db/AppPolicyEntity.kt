package org.walkguard.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_policies")
data class AppPolicyEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    val label: String,
    val policy: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
