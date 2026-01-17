package com.charlotteservicehub.autotext.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.charlotteservicehub.autotext.R
import com.charlotteservicehub.autotext.data.AutoTextPreferences
import com.charlotteservicehub.autotext.data.MessageLogDatabase
import com.charlotteservicehub.autotext.databinding.ActivityMainBinding
import com.charlotteservicehub.autotext.service.MissedCallListenerService

/**
 * Main Activity - Dashboard for the AutoText Hub app.
 * Handles permission requests and displays service status.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AutoTextPreferences
    private lateinit var messageLogDb: MessageLogDatabase

    // Required runtime permissions
    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.SEND_SMS
    ).apply {
        // Add POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            updateUI()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "AutoText Hub"

        preferences = AutoTextPreferences(this)
        messageLogDb = MessageLogDatabase(this)

        setupUI()
        checkInitialPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_templates -> {
                startActivity(Intent(this, MessageTemplatesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        // Service toggle switch
        binding.switchServiceEnabled.setOnCheckedChangeListener { _, isChecked ->
            preferences.setServiceEnabled(isChecked)
            updateServiceStatus()
        }

        // Grant permissions button
        binding.btnGrantPermissions.setOnClickListener {
            requestAllPermissions()
        }

        // Enable notification access button
        binding.btnEnableNotificationAccess.setOnClickListener {
            openNotificationAccessSettings()
        }

        // Disable battery optimization button
        binding.btnDisableBatteryOptimization.setOnClickListener {
            requestDisableBatteryOptimization()
        }

        // View message log button
        binding.btnViewLog.setOnClickListener {
            showMessageLog()
        }

        // Edit message template button
        binding.btnEditTemplate.setOnClickListener {
            startActivity(Intent(this, MessageTemplatesActivity::class.java))
        }

        // Test SMS button (for debugging)
        binding.btnTestSms.setOnClickListener {
            showTestSmsDialog()
        }
    }

    private fun checkInitialPermissions() {
        if (!hasAllPermissions()) {
            // Show explanation dialog before requesting
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage(
                    "AutoText Hub needs the following permissions to work:\n\n" +
                    "• READ_CALL_LOG - To get missed caller's number\n" +
                    "• READ_PHONE_STATE - To detect call state\n" +
                    "• SEND_SMS - To send auto-reply messages\n" +
                    "• NOTIFICATIONS - To show service status\n\n" +
                    "Tap 'Grant' to continue."
                )
                .setPositiveButton("Grant") { _, _ -> requestAllPermissions() }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun requestAllPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "Some permissions were denied. AutoText Hub needs these permissions to function. " +
                "Please grant them in Settings."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, MissedCallListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(componentName.flattenToString())
    }

    private fun openNotificationAccessSettings() {
        AlertDialog.Builder(this)
            .setTitle("Enable Notification Access")
            .setMessage(
                "AutoText Hub needs notification access to detect missed calls.\n\n" +
                "In the next screen, find 'AutoText Hub' and enable it."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestDisableBatteryOptimization() {
        if (!isBatteryOptimizationDisabled()) {
            AlertDialog.Builder(this)
                .setTitle("Disable Battery Optimization")
                .setMessage(
                    "To ensure AutoText Hub keeps running in the background, " +
                    "please disable battery optimization for this app."
                )
                .setPositiveButton("Disable") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            Toast.makeText(this, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        // Update permission status
        val permissionsGranted = hasAllPermissions()
        binding.statusPermissions.text = if (permissionsGranted) "✓ Granted" else "✗ Required"
        binding.statusPermissions.setTextColor(
            ContextCompat.getColor(this, if (permissionsGranted) R.color.status_ok else R.color.status_error)
        )
        binding.btnGrantPermissions.isEnabled = !permissionsGranted

        // Update notification listener status
        val notificationEnabled = isNotificationListenerEnabled()
        binding.statusNotificationAccess.text = if (notificationEnabled) "✓ Enabled" else "✗ Disabled"
        binding.statusNotificationAccess.setTextColor(
            ContextCompat.getColor(this, if (notificationEnabled) R.color.status_ok else R.color.status_error)
        )
        binding.btnEnableNotificationAccess.isEnabled = !notificationEnabled

        // Update battery optimization status
        val batteryOptDisabled = isBatteryOptimizationDisabled()
        binding.statusBatteryOptimization.text = if (batteryOptDisabled) "✓ Disabled" else "✗ Enabled"
        binding.statusBatteryOptimization.setTextColor(
            ContextCompat.getColor(this, if (batteryOptDisabled) R.color.status_ok else R.color.status_warning)
        )
        binding.btnDisableBatteryOptimization.isEnabled = !batteryOptDisabled

        // Update service status
        updateServiceStatus()

        // Update message count
        val messageCount = messageLogDb.getMessageCount()
        binding.txtMessageCount.text = "Messages sent: $messageCount"

        // Update current template preview
        val currentTemplate = preferences.getMissedCallMessage()
        binding.txtCurrentTemplate.text = if (currentTemplate.length > 100) {
            currentTemplate.take(100) + "..."
        } else {
            currentTemplate
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = preferences.isServiceEnabled()
        val isReady = hasAllPermissions() && isNotificationListenerEnabled()

        binding.switchServiceEnabled.isChecked = isEnabled

        val statusText = when {
            !isReady -> "Setup Required"
            isEnabled -> "Active - Monitoring"
            else -> "Disabled"
        }

        binding.txtServiceStatus.text = statusText
        binding.txtServiceStatus.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    !isReady -> R.color.status_warning
                    isEnabled -> R.color.status_ok
                    else -> R.color.status_error
                }
            )
        )

        binding.switchServiceEnabled.isEnabled = isReady
    }

    private fun showMessageLog() {
        val logs = messageLogDb.getRecentLogs(20)
        
        if (logs.isEmpty()) {
            Toast.makeText(this, "No messages sent yet", Toast.LENGTH_SHORT).show()
            return
        }

        val logText = logs.joinToString("\n\n") { log ->
            val date = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(log.timestamp))
            "📱 ${log.phoneNumber}\n📅 $date\n📊 ${log.status}"
        }

        AlertDialog.Builder(this)
            .setTitle("Recent Messages")
            .setMessage(logText)
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear Log") { _, _ ->
                messageLogDb.clearLogs()
                Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .show()
    }

    private fun showTestSmsDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "Enter phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        AlertDialog.Builder(this)
            .setTitle("Test SMS")
            .setMessage("Send a test message to verify the service is working.")
            .setView(editText)
            .setPositiveButton("Send") { _, _ ->
                val number = editText.text.toString().trim()
                if (number.isNotEmpty()) {
                    sendTestSms(number)
                } else {
                    Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTestSms(phoneNumber: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }

            val testMessage = "[TEST] This is a test message from AutoText Hub. If you received this, the service is working correctly."
            smsManager.sendTextMessage(phoneNumber, null, testMessage, null, null)
            
            Toast.makeText(this, "Test message sent to $phoneNumber", Toast.LENGTH_LONG).show()
            
            // Log the test message
            messageLogDb.insertLog(
                com.charlotteservicehub.autotext.data.MessageLog(
                    phoneNumber = phoneNumber,
                    message = testMessage,
                    timestamp = System.currentTimeMillis(),
                    status = "TEST"
                )
            )
            updateUI()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send test: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
