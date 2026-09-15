package com.geggioman.uggiu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crises")
data class CrisisRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long?,
    val maxBpm: Int,
    val averageBpm: Int,
    val sessionId: Int? = null
)
