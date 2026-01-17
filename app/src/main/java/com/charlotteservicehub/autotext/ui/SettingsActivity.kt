package com.charlotteservicehub.autotext.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.charlotteservicehub.autotext.data.AutoTextPreferences
import com.charlotteservicehub.autotext.databinding.ActivitySettingsBinding

/**
 * Settings Activity for configuring app behavior.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: AutoTextPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        preferences = AutoTextPreferences(this)

        loadSettings()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadSettings() {
        // Load current settings
        binding.switchServiceEnabled.isChecked = preferences.isServiceEnabled()
        binding.editCooldownMinutes.setText(preferences.getCooldownMinutes().toString())
        binding.editBlockedNumbers.setText(preferences.getBlockedNumbers().joinToString("\n"))
        binding.switchLogMessages.isChecked = preferences.isLoggingEnabled()
        binding.switchShowNotifications.isChecked = preferences.areNotificationsEnabled()
    }

    private fun setupListeners() {
        // Service enabled toggle
        binding.switchServiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            preferences.setServiceEnabled(isChecked)
        }

        // Logging toggle
        binding.switchLogMessages.setOnCheckedChangeListener { _, isChecked ->
            preferences.setLoggingEnabled(isChecked)
        }

        // Notifications toggle
        binding.switchShowNotifications.setOnCheckedChangeListener { _, isChecked ->
            preferences.setNotificationsEnabled(isChecked)
        }

        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        // Reset to defaults button
        binding.btnResetDefaults.setOnClickListener {
            preferences.resetToDefaults()
            loadSettings()
            Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        try {
            // Save cooldown
            val cooldown = binding.editCooldownMinutes.text.toString().toIntOrNull() ?: 5
            preferences.setCooldownMinutes(cooldown)

            // Save blocked numbers
            val blockedNumbers = binding.editBlockedNumbers.text.toString()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            preferences.setBlockedNumbers(blockedNumbers)

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
