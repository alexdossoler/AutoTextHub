package com.charlotteservicehub.autotext.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.charlotteservicehub.autotext.BuildConfig
import com.charlotteservicehub.autotext.R
import com.charlotteservicehub.autotext.data.AutoTextPreferences
import com.charlotteservicehub.autotext.data.MessageLog
import com.charlotteservicehub.autotext.data.MessageLogDatabase
import com.charlotteservicehub.autotext.receiver.SmsDeliveredReceiver
import com.charlotteservicehub.autotext.receiver.SmsSentReceiver
import com.charlotteservicehub.autotext.ui.MainActivity
import java.util.concurrent.ConcurrentHashMap

/**
 * Notification Listener Service that detects missed call notifications
 * and automatically sends SMS responses to the caller.
 */
class MissedCallListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MissedCallListener"
        private const val CHANNEL_ID = "autotext_foreground"
        private const val CHANNEL_ID_REPLY = "autotext_reply"
        private const val NOTIFICATION_ID = 1001
        private const val REPLY_NOTIFICATION_ID_BASE = 2000
        
        // Common dialer package names across different Android devices/ROMs
        private val DIALER_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.android.phone",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.oneplus.dialer",
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.asus.contacts",
            "com.sonymobile.android.dialer"
        )
        
        // Missed call keywords in various languages
        private val MISSED_CALL_KEYWORDS = listOf(
            "missed call",           // English
            "llamada perdida",       // Spanish
            "appel manqué",          // French
            "verpasster anruf",      // German
            "chamada perdida",       // Portuguese
            "chiamata persa",        // Italian
            "不在着信",              // Japanese
            "未接来电",              // Chinese (Simplified)
            "未接來電",              // Chinese (Traditional)
            "부재중 전화",           // Korean
            "пропущенный вызов"      // Russian
        )
        
        // Cooldown tracking to prevent duplicate SMS
        private val recentlySentNumbers = ConcurrentHashMap<String, Long>()
        private const val COOLDOWN_MS = 300_000L // 5 minutes cooldown per number
    }

    private lateinit var preferences: AutoTextPreferences
    private lateinit var messageLogDb: MessageLogDatabase

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MissedCallListenerService created")
        preferences = AutoTextPreferences(this)
        messageLogDb = MessageLogDatabase(this)
        startForegroundServiceNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MissedCallListenerService destroyed")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    /**
     * Called when a new notification is posted to the system.
     * Filters for missed call notifications and triggers auto-SMS.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        // Check if service is enabled
        if (!preferences.isServiceEnabled()) {
            Log.d(TAG, "Service is disabled in preferences")
            return
        }

        val packageName = sbn.packageName ?: return
        
        // Only process notifications from dialer apps
        if (!DIALER_PACKAGES.contains(packageName)) {
            return
        }

        // Extract notification details
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val content = "$title $text".lowercase()

        Log.d(TAG, "Dialer notification: title='$title', text='$text', pkg='$packageName'")

        // Check if this is a missed call notification
        val isMissedCall = MISSED_CALL_KEYWORDS.any { keyword ->
            content.contains(keyword.lowercase())
        }

        if (!isMissedCall) {
            Log.d(TAG, "Not a missed call notification")
            return
        }

        Log.i(TAG, "Missed call detected!")
        
        // Get the message template
        val message = preferences.getMissedCallMessage()
        
        // Check if we're in full auto mode (sideload) or assisted mode (Play)
        if (BuildConfig.FULL_AUTO_MODE && hasRequiredPermissions()) {
            // SIDELOAD: Full auto mode - read call log and send SMS automatically
            handleFullAutoMode(message)
        } else {
            // PLAY STORE: Assisted reply mode - extract number from notification and prompt user
            handleAssistedReplyMode(title, text, message)
        }
    }

    /**
     * Full auto mode (Sideload): Query call log and send SMS automatically
     */
    private fun handleFullAutoMode(message: String) {
        try {
            // Query the call log for the most recent missed call
            val number = getLastMissedNumber(windowMs = 60_000) // Last 60 seconds
            
            if (number != null) {
                // Check cooldown to prevent duplicate messages
                if (isInCooldown(number)) {
                    Log.d(TAG, "Number $number is in cooldown period, skipping")
                    return
                }
                
                // Check if number is in blocked list
                if (preferences.isNumberBlocked(number)) {
                    Log.d(TAG, "Number $number is blocked, skipping")
                    return
                }
                
                // Send the auto-text
                sendAutoText(number, message)
                
                // Update cooldown
                recentlySentNumbers[normalizeNumber(number)] = System.currentTimeMillis()
                
                // Log to database
                logMessage(number, message)
                
                Log.i(TAG, "Auto-text sent to $number")
            } else {
                Log.w(TAG, "Could not retrieve missed call number from call log")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to process missed call", t)
        }
    }

    /**
     * Assisted reply mode (Play Store): Extract number from notification and show tap-to-send
     */
    private fun handleAssistedReplyMode(title: String, text: String, message: String) {
        // Try to extract phone number from the notification text
        val phoneNumber = extractPhoneNumber(title) ?: extractPhoneNumber(text)
        
        if (phoneNumber != null) {
            // Check cooldown
            if (isInCooldown(phoneNumber)) {
                Log.d(TAG, "Number $phoneNumber is in cooldown period, skipping prompt")
                return
            }
            
            // Check if blocked
            if (preferences.isNumberBlocked(phoneNumber)) {
                Log.d(TAG, "Number $phoneNumber is blocked, skipping prompt")
                return
            }
            
            // Show notification with "Tap to Reply" action
            showReplyPromptNotification(phoneNumber, message)
            
            // Update cooldown (for the prompt, not the send)
            recentlySentNumbers[normalizeNumber(phoneNumber)] = System.currentTimeMillis()
        } else {
            Log.w(TAG, "Could not extract phone number from notification")
            // Show generic prompt to open SMS app
            showGenericReplyNotification()
        }
    }

    /**
     * Extracts a phone number from text using regex patterns.
     */
    private fun extractPhoneNumber(text: String): String? {
        // Common phone number patterns
        val patterns = listOf(
            // International format: +1 234 567 8901, +1-234-567-8901
            Regex("""\+?[1-9]\d{0,2}[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}"""),
            // US format: (234) 567-8901, 234-567-8901, 234.567.8901
            Regex("""\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}"""),
            // Simple digits: 2345678901 (10+ digits)
            Regex("""\d{10,15}""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val number = match.value.replace(Regex("[^0-9+]"), "")
                if (number.length >= 10) {
                    return number
                }
            }
        }
        return null
    }

    /**
     * Shows a notification prompting the user to tap to send the auto-reply.
     */
    private fun showReplyPromptNotification(phoneNumber: String, message: String) {
        createReplyNotificationChannel()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open SMS app with pre-filled message
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            phoneNumber.hashCode(),
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_REPLY)
            .setSmallIcon(R.drawable.ic_message_sent)
            .setContentTitle("Missed Call from $phoneNumber")
            .setContentText("Tap to send quick reply")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Tap to send: \"$message\""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_message_sent,
                "Send Reply",
                pendingIntent
            )
            .build()

        notificationManager.notify(
            REPLY_NOTIFICATION_ID_BASE + phoneNumber.hashCode(),
            notification
        )
        
        Log.i(TAG, "Showed reply prompt for $phoneNumber")
    }

    /**
     * Shows a generic notification when we can't extract the phone number.
     */
    private fun showGenericReplyNotification() {
        createReplyNotificationChannel()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Open the main SMS app
        val smsIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            type = "vnd.android-dir/mms-sms"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_REPLY)
            .setSmallIcon(R.drawable.ic_message_sent)
            .setContentTitle("Missed Call Detected")
            .setContentText("Tap to open Messages and reply")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(REPLY_NOTIFICATION_ID_BASE, notification)
    }

    /**
     * Creates the notification channel for reply prompts.
     */
    private fun createReplyNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_REPLY,
            "Missed Call Replies",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Prompts to reply to missed calls"
            enableVibration(true)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Checks if the app has the required permissions for full auto mode.
     */
    private fun hasRequiredPermissions(): Boolean {
        val hasCallLog = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasSendSms = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        return hasCallLog && hasSendSms
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: Handle notification removal if needed
    }

    /**
     * Queries the call log for the most recent missed call within the specified time window.
     */
    private fun getLastMissedNumber(windowMs: Long): String? {
        val uri = CallLog.Calls.CONTENT_URI
        val now = System.currentTimeMillis()
        
        val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(
            CallLog.Calls.MISSED_TYPE.toString(),
            (now - windowMs).toString()
        )
        val sortOrder = "${CallLog.Calls.DATE} DESC"
        
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.CACHED_NAME
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            if (cursor != null && cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                if (numberIndex >= 0) {
                    val number = cursor.getString(numberIndex)
                    Log.d(TAG, "Found missed call from: $number")
                    return number
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read call log", e)
        } finally {
            cursor?.close()
        }
        
        return null
    }

    /**
     * Sends an SMS message to the specified phone number.
     */
    private fun sendAutoText(to: String, body: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Create pending intents for sent and delivered callbacks
            val sentIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(SmsSentReceiver.ACTION_SMS_SENT).apply {
                    putExtra("phone_number", to)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(SmsDeliveredReceiver.ACTION_SMS_DELIVERED).apply {
                    putExtra("phone_number", to)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Handle long messages by splitting into parts
            val parts = smsManager.divideMessage(body)
            
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { add(sentIntent) }
                }
                val deliveredIntents = ArrayList<PendingIntent>().apply {
                    repeat(parts.size) { add(deliveredIntent) }
                }
                smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, deliveredIntents)
            } else {
                smsManager.sendTextMessage(to, null, body, sentIntent, deliveredIntent)
            }

            Log.i(TAG, "SMS sent to $to")
            
            // Show notification about sent message
            showSentNotification(to)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $to", e)
        }
    }

    /**
     * Checks if a number is within the cooldown period.
     */
    private fun isInCooldown(number: String): Boolean {
        val normalized = normalizeNumber(number)
        val lastSent = recentlySentNumbers[normalized] ?: return false
        return (System.currentTimeMillis() - lastSent) < COOLDOWN_MS
    }

    /**
     * Normalizes a phone number for comparison.
     */
    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("[^0-9+]"), "").takeLast(10)
    }

    /**
     * Logs the sent message to the database.
     */
    private fun logMessage(phoneNumber: String, message: String) {
        val log = MessageLog(
            phoneNumber = phoneNumber,
            message = message,
            timestamp = System.currentTimeMillis(),
            status = "SENT"
        )
        messageLogDb.insertLog(log)
    }

    /**
     * Shows a notification about the sent auto-text message.
     */
    private fun showSentNotification(phoneNumber: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message_sent)
            .setContentTitle("Auto-Text Sent")
            .setContentText("Message sent to $phoneNumber")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Starts the foreground service with a persistent notification.
     */
    private fun startForegroundServiceNotification() {
        val channelId = createNotificationChannel()
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoText Hub Active")
            .setContentText("Monitoring for missed calls")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Creates the notification channel for Android 8.0+.
     */
    private fun createNotificationChannel(): String {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoText Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the AutoText service running in the background"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        
        return CHANNEL_ID
    }
}
