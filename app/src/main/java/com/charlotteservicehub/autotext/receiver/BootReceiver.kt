package com.charlotteservicehub.autotext.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.provider.Settings
import android.util.Log

/**
 * Boot receiver that restarts the notification listener service
 * when the device boots up.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Device booted, checking notification listener status")
            
            // Check if notification listener is enabled
            val componentName = ComponentName(
                context,
                com.charlotteservicehub.autotext.service.MissedCallListenerService::class.java
            )
            
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            
            if (enabledListeners.contains(componentName.flattenToString())) {
                Log.d(TAG, "Notification listener is enabled, service will start automatically")
                // The NotificationListenerService should restart automatically
                // when the system detects it's enabled
            } else {
                Log.d(TAG, "Notification listener is not enabled")
            }
        }
    }
}
