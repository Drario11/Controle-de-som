package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VolumeGestureLogDao {
    @Query("SELECT * FROM volume_gesture_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<VolumeGestureLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VolumeGestureLog)

    @Query("DELETE FROM volume_gesture_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM volume_gesture_logs")
    fun getLogCount(): Flow<Int>
}
