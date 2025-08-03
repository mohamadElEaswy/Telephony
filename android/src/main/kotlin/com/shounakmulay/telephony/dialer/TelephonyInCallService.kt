package com.shounakmulay.telephony.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

class TelephonyInCallService : InCallService() {

    companion object {
        private const val TAG = "TelephonyInCallService"
        private const val NOTIFICATION_CHANNEL_ID = "incoming_calls"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCallAdded(call: Call?) {
        Log.d(TAG, "onCallAdded: call=$call")
        super.onCallAdded(call)

        call?.let { handleCallAdded(it) }
    }

    override fun onCallRemoved(call: Call?) {
        Log.d(TAG, "onCallRemoved: call=$call")
        super.onCallRemoved(call)

        call?.let { handleCallRemoved(it) }
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        Log.d(TAG, "onCallAudioStateChanged: audioState=$audioState")
        super.onCallAudioStateChanged(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        Log.d(TAG, "onBringToForeground: showDialpad=$showDialpad")
        super.onBringToForeground(showDialpad)
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        Log.d(TAG, "onCanAddCallChanged: canAddCall=$canAddCall")
        super.onCanAddCallChanged(canAddCall)
    }

    override fun onSilenceRinger() {
        Log.d(TAG, "onSilenceRinger")
        super.onSilenceRinger()
    }

    private fun handleCallAdded(call: Call) {
        when (call.state) {
            Call.STATE_RINGING -> {
                Log.d(TAG, "Incoming call detected")
                showIncomingCallNotification(call)
            }
            Call.STATE_DIALING -> {
                Log.d(TAG, "Outgoing call detected")
                // Handle outgoing call UI
            }
            Call.STATE_ACTIVE -> {
                Log.d(TAG, "Call is active")
                // Handle active call UI
            }
            Call.STATE_HOLDING -> {
                Log.d(TAG, "Call is on hold")
                // Handle call on hold UI
            }
            Call.STATE_DISCONNECTED -> {
                Log.d(TAG, "Call is disconnected")
                // Handle call disconnect
            }
        }
    }

    private fun handleCallRemoved(call: Call) {
        Log.d(TAG, "Call removed: ${call.details?.handle}")
        // Remove any notifications or UI elements for this call
        removeCallNotification()
    }

    private fun showIncomingCallNotification(call: Call) {
        createNotificationChannel()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent for full-screen incoming call UI
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            flags = Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_NEW_TASK
            // You would set your incoming call activity here
            // setClass(this@TelephonyInCallService, YourIncomingCallActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setOngoing(true)
            setPriority(Notification.PRIORITY_HIGH)
            setContentIntent(pendingIntent)
            setFullScreenIntent(pendingIntent, true)
            setSmallIcon(android.R.drawable.sym_action_call)
            setContentTitle("Incoming Call")
            setContentText(call.details?.handle?.schemeSpecificPart ?: "Unknown")

            // Add action buttons for answer/reject
            addAction(
                android.R.drawable.sym_action_call,
                "Answer",
                createAnswerPendingIntent(call)
            )
            addAction(
                android.R.drawable.sym_call_missed,
                "Reject",
                createRejectPendingIntent(call)
            )
        }.build()

        notificationManager.notify(NOTIFICATION_CHANNEL_ID, NOTIFICATION_ID, notification)
    }

    private fun removeCallNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            description = "Notifications for incoming calls"

            // Use default system ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            setSound(
                ringtoneUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )

            enableVibration(true)
            enableLights(true)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun createAnswerPendingIntent(call: Call): PendingIntent {
        val intent = Intent("ANSWER_CALL").apply {
            putExtra("call_id", call.details?.id)
        }
        return PendingIntent.getBroadcast(
            this,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createRejectPendingIntent(call: Call): PendingIntent {
        val intent = Intent("REJECT_CALL").apply {
            putExtra("call_id", call.details?.id)
        }
        return PendingIntent.getBroadcast(
            this,
            3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
} 