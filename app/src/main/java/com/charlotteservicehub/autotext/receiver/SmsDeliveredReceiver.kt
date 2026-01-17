package com.charlotteservicehub.autotext.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver for SMS delivery status callbacks.
 */
class SmsDeliveredReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_DELIVERED = "com.charlotteservicehub.autotext.SMS_DELIVERED"
        private const val TAG = "SmsDeliveredReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number") ?: "Unknown"
        
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.i(TAG, "SMS delivered to $phoneNumber")
            }
            Activity.RESULT_CANCELED -> {
                Log.w(TAG, "SMS not delivered to $phoneNumber")
            }
            else -> {
                Log.w(TAG, "SMS delivery status unknown ($resultCode) for $phoneNumber")
            }
        }
    }
}
