package com.arcanys.hush.timer

/**
 * Simple in-memory block/category model.
 *
 * Color is stored as ARGB int for easy storage and conversion to Compose [androidx.compose.ui.graphics.Color].
 */
data class Block(
    val name: String,
    val colorArgb: Int,
    val emoji: String
)

