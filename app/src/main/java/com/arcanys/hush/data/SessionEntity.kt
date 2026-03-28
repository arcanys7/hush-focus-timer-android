package com.arcanys.hush.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val blockName: String,
    val duration: Int,
    val timestamp: Long,
    val notes: String?
)
