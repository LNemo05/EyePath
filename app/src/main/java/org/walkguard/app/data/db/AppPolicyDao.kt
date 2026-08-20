package org.walkguard.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPolicyDao {
    @Query("SELECT * FROM app_policies ORDER BY label COLLATE NOCASE")
    fun observePolicies(): Flow<List<AppPolicyEntity>>

    @Query("SELECT * FROM app_policies WHERE package_name = :packageName")
    suspend fun getPolicy(packageName: String): AppPolicyEntity?

    @Upsert
    suspend fun upsert(entity: AppPolicyEntity)

    @Query("DELETE FROM app_policies WHERE package_name = :packageName")
    suspend fun delete(packageName: String)
}
