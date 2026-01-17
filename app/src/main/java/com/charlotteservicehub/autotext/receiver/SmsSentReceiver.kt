package com.charlotteservicehub.autotext.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast

/**
 * Receiver for SMS sent status callbacks.
 */
class SmsSentReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "com.charlotteservicehub.autotext.SMS_SENT"
        private const val TAG = "SmsSentReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra("phone_number") ?: "Unknown"
        
        when (resultCode) {
            Activity.RESULT_OK -> {
                Log.i(TAG, "SMS sent successfully to $phoneNumber")
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                Log.e(TAG, "SMS failed: Generic failure to $phoneNumber")
                showError(context, "SMS failed to send")
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                Log.e(TAG, "SMS failed: No service to $phoneNumber")
                showError(context, "No cellular service")
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                Log.e(TAG, "SMS failed: Null PDU to $phoneNumber")
                showError(context, "SMS encoding error")
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                Log.e(TAG, "SMS failed: Radio off to $phoneNumber")
                showError(context, "Radio is off")
            }
            else -> {
                Log.e(TAG, "SMS failed: Unknown error ($resultCode) to $phoneNumber")
            }
        }
    }

    private fun showError(context: Context, message: String) {
        Toast.makeText(context, "AutoText: $message", Toast.LENGTH_SHORT).show()
    }
}
