package com.charlotteservicehub.autotext.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.charlotteservicehub.autotext.data.AutoTextPreferences
import com.charlotteservicehub.autotext.databinding.ActivityMessageTemplatesBinding

/**
 * Activity for managing message templates.
 */
class MessageTemplatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageTemplatesBinding
    private lateinit var preferences: AutoTextPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageTemplatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Message Templates"

        preferences = AutoTextPreferences(this)

        loadTemplate()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadTemplate() {
        binding.editMissedCallMessage.setText(preferences.getMissedCallMessage())
        binding.editFollowUpMessage.setText(preferences.getFollowUpMessage())
        updateCharCount()
    }

    private fun setupListeners() {
        // Character count updates
        binding.editMissedCallMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateCharCount()
            }
        })

        // Save button
        binding.btnSaveTemplate.setOnClickListener {
            saveTemplate()
        }

        // Reset to default button
        binding.btnResetTemplate.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Templates")
                .setMessage("Reset all message templates to defaults?")
                .setPositiveButton("Reset") { _, _ ->
                    preferences.resetMessagesToDefaults()
                    loadTemplate()
                    Toast.makeText(this, "Templates reset to defaults", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Preview button
        binding.btnPreviewMessage.setOnClickListener {
            showPreview()
        }

        // Template suggestions
        binding.chipTemplate1.setOnClickListener {
            binding.editMissedCallMessage.setText(
                "Hey! Sorry I missed your call. Text me here with what you need and I'll get back to you ASAP. Reply STOP to opt out."
            )
        }

        binding.chipTemplate2.setOnClickListener {
            binding.editMissedCallMessage.setText(
                "Thanks for calling! I'm currently unavailable. Please leave your name and reason for calling, and I'll respond shortly. Reply STOP to unsubscribe."
            )
        }

        binding.chipTemplate3.setOnClickListener {
            binding.editMissedCallMessage.setText(
                "Hi! This is Charlotte Service Hub. Sorry I missed you—text me your request (photos welcome!) and I'll reply soon. Reply STOP to opt out."
            )
        }
    }

    private fun updateCharCount() {
        val length = binding.editMissedCallMessage.text?.length ?: 0
        val segments = (length / 160) + 1
        binding.txtCharCount.text = "$length characters ($segments SMS segment${if (segments > 1) "s" else ""})"
        
        // Warn if message is too long
        if (length > 480) {
            binding.txtCharCount.setTextColor(getColor(com.charlotteservicehub.autotext.R.color.status_error))
        } else if (length > 320) {
            binding.txtCharCount.setTextColor(getColor(com.charlotteservicehub.autotext.R.color.status_warning))
        } else {
            binding.txtCharCount.setTextColor(getColor(com.charlotteservicehub.autotext.R.color.text_secondary))
        }
    }

    private fun saveTemplate() {
        val missedCallMessage = binding.editMissedCallMessage.text.toString().trim()
        val followUpMessage = binding.editFollowUpMessage.text.toString().trim()

        if (missedCallMessage.isEmpty()) {
            Toast.makeText(this, "Missed call message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Check for opt-out compliance
        if (!missedCallMessage.contains("STOP", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Compliance Warning")
                .setMessage(
                    "Your message doesn't include an opt-out option (e.g., 'Reply STOP to opt out').\n\n" +
                    "This is recommended for US SMS compliance. Continue anyway?"
                )
                .setPositiveButton("Save Anyway") { _, _ ->
                    doSaveTemplate(missedCallMessage, followUpMessage)
                }
                .setNegativeButton("Edit Message", null)
                .show()
        } else {
            doSaveTemplate(missedCallMessage, followUpMessage)
        }
    }

    private fun doSaveTemplate(missedCallMessage: String, followUpMessage: String) {
        preferences.setMissedCallMessage(missedCallMessage)
        preferences.setFollowUpMessage(followUpMessage)
        Toast.makeText(this, "Templates saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showPreview() {
        val message = binding.editMissedCallMessage.text.toString()
        AlertDialog.Builder(this)
            .setTitle("Message Preview")
            .setMessage("This is how your message will appear:\n\n$message")
            .setPositiveButton("OK", null)
            .show()
    }
}
