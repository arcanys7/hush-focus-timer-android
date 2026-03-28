package com.arcanys.hush.timer

data class Session(
    val blockName: String,
    val durationMinutes: Int,
    val timestampMillis: Long,
    val notes: String?
)

