package com.example.uggiu.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSession): Long

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Delete
    suspend fun deleteSession(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Int): WorkoutSession?

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    // Crisis Records
    @Query("SELECT * FROM crises ORDER BY startTime DESC")
    fun getAllCrises(): Flow<List<CrisisRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrisis(crisis: CrisisRecord): Long

    @Update
    suspend fun updateCrisis(crisis: CrisisRecord)

    @Delete
    suspend fun deleteCrisis(crisis: CrisisRecord)

    // Device Aliases
    @Query("SELECT * FROM device_aliases")
    fun getAllAliases(): Flow<List<DeviceAlias>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(deviceAlias: DeviceAlias)

    @Query("SELECT * FROM device_aliases WHERE address = :address LIMIT 1")
    suspend fun getAliasForAddress(address: String): DeviceAlias?
}
