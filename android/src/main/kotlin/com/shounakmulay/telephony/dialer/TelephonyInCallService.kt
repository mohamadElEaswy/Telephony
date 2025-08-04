package com.shounakmulay.telephony.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import androidx.annotation.RequiresApi

class TelephonyInCallService : InCallService() {

    companion object {
        private const val TAG = "TelephonyInCallService"
        private const val NOTIFICATION_CHANNEL_ID = "incoming_calls"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_ANSWER_CALL = "ANSWER_CALL"
        private const val ACTION_REJECT_CALL = "REJECT_CALL"
    }

    private lateinit var callActionReceiver: BroadcastReceiver
    private var currentCall: Call? = null

    override fun onCreate() {
        super.onCreate()
        registerCallActionReceiver()
        CallBridge.registerInCallService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(callActionReceiver)
        CallBridge.unregisterInCallService()
    }

    private fun registerCallActionReceiver() {
        callActionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast received: ${intent?.action}")
                val callId = intent?.getStringExtra("call_id")
                val phoneNumber = intent?.getStringExtra("phone_number")
                
                when (intent?.action) {
                    ACTION_ANSWER_CALL -> {
                        Log.d(TAG, "Answer call action received for call: $callId, phone: $phoneNumber")
                        val success = CallBridge.answerCall()
                        if (success) {
                            removeCallNotification()
                        }
                        Log.d(TAG, "Answer call result: $success")
                    }
                    ACTION_REJECT_CALL -> {
                        Log.d(TAG, "Reject call action received for call: $callId, phone: $phoneNumber")
                        val success = CallBridge.rejectCall()
                        if (success) {
                            removeCallNotification()
                        }
                        Log.d(TAG, "Reject call result: $success")
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_ANSWER_CALL)
            addAction(ACTION_REJECT_CALL)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callActionReceiver, filter)
        }
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
        currentCall = call
        CallBridge.setCurrentCall(call)
        
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
                removeCallNotification()
                // Handle active call UI
            }
            Call.STATE_HOLDING -> {
                Log.d(TAG, "Call is on hold")
                // Handle call on hold UI
            }
            Call.STATE_DISCONNECTED -> {
                Log.d(TAG, "Call is disconnected")
                removeCallNotification()
                // Handle call disconnect
            }
        }
        
        // Register callback for call state changes
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                CallBridge.setCurrentCall(call)
                
                when (state) {
                    Call.STATE_ACTIVE -> removeCallNotification()
                    Call.STATE_DISCONNECTED -> removeCallNotification()
                }
            }
        })
    }

    private fun handleCallRemoved(call: Call) {
        Log.d(TAG, "Call removed: ${call.details?.handle}")
        // Remove any notifications or UI elements for this call
        removeCallNotification()
        currentCall = null
        CallBridge.setCurrentCall(null)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showIncomingCallNotification(call: Call) {
        try {
            createNotificationChannel()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
            val callerName = call.details?.callerDisplayName ?: phoneNumber

            Log.d(TAG, "Creating notification for call from: $phoneNumber ($callerName)")

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
                setCategory(Notification.CATEGORY_CALL)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setPriority(Notification.PRIORITY_MAX)
                setContentIntent(pendingIntent)
                setFullScreenIntent(pendingIntent, true)
                setSmallIcon(android.R.drawable.sym_action_call)
                setContentTitle(callerName)
                setContentText("Incoming call")
                setSubText(phoneNumber)
                setShowWhen(true)
                setWhen(System.currentTimeMillis())
                setAutoCancel(false)

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

            Log.d(TAG, "Displaying notification with ID: $NOTIFICATION_ID")
            notificationManager.notify(NOTIFICATION_ID, notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show incoming call notification", e)
        }
    }

    private fun removeCallNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
        val intent = Intent(ACTION_ANSWER_CALL).apply {
            setPackage(packageName) // Explicit package name for security and reliability
            putExtra("call_id", call.details?.id)
            putExtra("phone_number", call.details?.handle?.schemeSpecificPart)
        }
        return PendingIntent.getBroadcast(
            this,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createRejectPendingIntent(call: Call): PendingIntent {
        val intent = Intent(ACTION_REJECT_CALL).apply {
            setPackage(packageName) // Explicit package name for security and reliability
            putExtra("call_id", call.details?.id)
            putExtra("phone_number", call.details?.handle?.schemeSpecificPart)
        }
        return PendingIntent.getBroadcast(
            this,
            3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
} 