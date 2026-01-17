package com.charlotteservicehub.autotext.data

/**
 * Data class representing a sent message log entry.
 */
data class MessageLog(
    val id: Long = 0,
    val phoneNumber: String,
    val message: String,
    val timestamp: Long,
    val status: String // SENT, DELIVERED, FAILED, TEST
)
