package com.pmfby.evidence.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerDao {
    @Query("SELECT * FROM cached_farmers ORDER BY farmerName")
    fun observeAll(): Flow<List<FarmerEntity>>

    @Query("SELECT * FROM cached_farmers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FarmerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(farmers: List<FarmerEntity>)

    @Query("DELETE FROM cached_farmers")
    suspend fun clear()
}

@Dao
interface SurveyCaseDao {
    @Query("SELECT * FROM cached_cases WHERE farmerId = :farmerId ORDER BY lastSyncedAtMillis DESC")
    fun observeForFarmer(farmerId: String): Flow<List<SurveyCaseEntity>>

    @Query("SELECT * FROM cached_cases WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SurveyCaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cases: List<SurveyCaseEntity>)
}

@Dao
interface OfflineEventDao {
    @Query("SELECT * FROM offline_queue ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<OfflineEventEntity>>

    @Query("SELECT * FROM offline_queue WHERE status = 'pending' OR status = 'failed' ORDER BY createdAtMillis ASC")
    suspend fun getPending(): List<OfflineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: OfflineEventEntity)

    @Update
    suspend fun update(event: OfflineEventEntity)

    @Query("DELETE FROM offline_queue WHERE idempotencyKey = :key")
    suspend fun delete(key: String)
}
