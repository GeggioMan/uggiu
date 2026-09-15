package com.geggioman.uggiu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    // Comma-separated list of heart rate values (e.g. "72,75,78,82")
    val heartRateHistoryString: String,
) {
    // Utility to get history as a List of Ints
    fun getHeartRateHistory(): List<Int> {
        if (heartRateHistoryString.isEmpty()) return emptyList()
        return heartRateHistoryString.split(",").mapNotNull { it.toIntOrNull() }
    }
}
