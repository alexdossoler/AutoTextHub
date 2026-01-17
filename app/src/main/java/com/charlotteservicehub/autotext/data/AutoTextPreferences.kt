package com.charlotteservicehub.autotext.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preferences manager for AutoText Hub settings.
 */
class AutoTextPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "autotext_prefs"
        
        // Keys
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
        private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_MISSED_CALL_MESSAGE = "missed_call_message"
        private const val KEY_FOLLOW_UP_MESSAGE = "follow_up_message"
        
        // Default values
        private const val DEFAULT_COOLDOWN_MINUTES = 5
        private const val DEFAULT_MISSED_CALL_MESSAGE = 
            "Hey! It's Charlotte Service Hub—sorry I missed your call. " +
            "You can text me here with what you need (pics welcome). Reply STOP to opt out."
        private const val DEFAULT_FOLLOW_UP_MESSAGE = 
            "Got it—thanks! Want me to send a quick estimate or schedule a visit? " +
            "I have openings tomorrow 9–11 or 2–4."
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Service enabled
    fun isServiceEnabled(): Boolean = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    
    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SERVICE_ENABLED, enabled) }
    }

    // Cooldown minutes
    fun getCooldownMinutes(): Int = prefs.getInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES)
    
    fun setCooldownMinutes(minutes: Int) {
        prefs.edit { putInt(KEY_COOLDOWN_MINUTES, minutes.coerceIn(1, 60)) }
    }

    // Blocked numbers
    fun getBlockedNumbers(): List<String> {
        val raw = prefs.getString(KEY_BLOCKED_NUMBERS, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    fun setBlockedNumbers(numbers: List<String>) {
        prefs.edit { putString(KEY_BLOCKED_NUMBERS, numbers.joinToString(",")) }
    }
    
    fun isNumberBlocked(number: String): Boolean {
        val normalized = number.replace(Regex("[^0-9+]"), "").takeLast(10)
        return getBlockedNumbers().any { blocked ->
            blocked.replace(Regex("[^0-9+]"), "").takeLast(10) == normalized
        }
    }
    
    fun addBlockedNumber(number: String) {
        val current = getBlockedNumbers().toMutableList()
        if (!current.contains(number)) {
            current.add(number)
            setBlockedNumbers(current)
        }
    }

    // Logging enabled
    fun isLoggingEnabled(): Boolean = prefs.getBoolean(KEY_LOGGING_ENABLED, true)
    
    fun setLoggingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LOGGING_ENABLED, enabled) }
    }

    // Notifications enabled
    fun areNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled) }
    }

    // Missed call message template
    fun getMissedCallMessage(): String = 
        prefs.getString(KEY_MISSED_CALL_MESSAGE, DEFAULT_MISSED_CALL_MESSAGE) ?: DEFAULT_MISSED_CALL_MESSAGE
    
    fun setMissedCallMessage(message: String) {
        prefs.edit { putString(KEY_MISSED_CALL_MESSAGE, message) }
    }

    // Follow-up message template
    fun getFollowUpMessage(): String = 
        prefs.getString(KEY_FOLLOW_UP_MESSAGE, DEFAULT_FOLLOW_UP_MESSAGE) ?: DEFAULT_FOLLOW_UP_MESSAGE
    
    fun setFollowUpMessage(message: String) {
        prefs.edit { putString(KEY_FOLLOW_UP_MESSAGE, message) }
    }

    // Reset to defaults
    fun resetToDefaults() {
        prefs.edit {
            putBoolean(KEY_SERVICE_ENABLED, true)
            putInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES)
            putString(KEY_BLOCKED_NUMBERS, "")
            putBoolean(KEY_LOGGING_ENABLED, true)
            putBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        }
    }
    
    fun resetMessagesToDefaults() {
        prefs.edit {
            putString(KEY_MISSED_CALL_MESSAGE, DEFAULT_MISSED_CALL_MESSAGE)
            putString(KEY_FOLLOW_UP_MESSAGE, DEFAULT_FOLLOW_UP_MESSAGE)
        }
    }
}
