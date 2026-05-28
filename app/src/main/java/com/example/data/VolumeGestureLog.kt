package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "volume_gesture_logs")
data class VolumeGestureLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val direction: String, // "Aumento" or "Diminuição"
    val changeAmount: Int, // difference in percentage or steps
    val startVolume: Int,
    val finalVolume: Int
)
