package com.shounakmulay.telephony.dialer

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.shounakmulay.telephony.TelephonyConnectionService

class CallController(private val context: Context) {
    companion object {
        private const val TAG = "CallController"
    }
    
    private var toneGenerator: ToneGenerator? = null
    
    private val telecomManager: TelecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }
    
    fun makeCall(phoneNumber: String, accountId: String? = null): Boolean {
        return try {
            val uri = Uri.parse("tel:$phoneNumber")
            val bundle = android.os.Bundle().apply {
                if (accountId != null) {
                    putString("accountId", accountId)
                }
            }
            telecomManager.placeCall(uri, bundle)
            Log.d(TAG, "Call initiated to: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call to $phoneNumber", e)
            false
        }
    }
    
    fun endCall(): Boolean {
        return try {
            Log.d(TAG, "End call requested")
            CallBridge.endCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call", e)
            false
        }
    }
    
    fun answerCall(): Boolean {
        return try {
            Log.d(TAG, "Answer call requested")
            CallBridge.answerCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call", e)
            false
        }
    }
    
    fun rejectCall(): Boolean {
        return try {
            Log.d(TAG, "Reject call requested")
            CallBridge.rejectCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject call", e)
            false
        }
    }
    
    fun holdCall(): Boolean {
        return try {
            Log.d(TAG, "Hold call requested")
            CallBridge.holdCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hold call", e)
            false
        }
    }
    
    fun unholdCall(): Boolean {
        return try {
            Log.d(TAG, "Unhold call requested")
            CallBridge.unholdCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unhold call", e)
            false
        }
    }
    
    fun muteCall(): Boolean {
        return try {
            Log.d(TAG, "Mute call requested")
            CallBridge.muteCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute call", e)
            false
        }
    }
    
    fun unmuteCall(): Boolean {
        return try {
            Log.d(TAG, "Unmute call requested")
            CallBridge.unmuteCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unmute call", e)
            false
        }
    }
    
    fun getCallAudioState(): Map<String, Any> {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val callInfo = CallBridge.getCurrentCallInfo()
            
            // Determine current audio route
            val currentRoute = when {
                audioManager.isBluetoothScoOn -> 2 // Bluetooth
                audioManager.isSpeakerphoneOn -> 1 // Speaker
                else -> 0 // Earpiece
            }
            
            // Get available audio routes
            val availableRoutes = mutableListOf<Int>()
            availableRoutes.add(0) // Earpiece always available
            availableRoutes.add(1) // Speaker always available
            
            // Check if Bluetooth is available
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoAvailableOffCall) {
                availableRoutes.add(2) // Bluetooth
            }
            
            if (callInfo != null) {
                mapOf(
                    "hasActiveCall" to true,
                    "phoneNumber" to (callInfo["phoneNumber"] ?: ""),
                    "state" to (callInfo["state"] ?: ""),
                    "canMute" to (callInfo["canMute"] ?: false),
                    "canHold" to (callInfo["canHold"] ?: false),
                    "currentAudioRoute" to currentRoute,
                    "availableAudioRoutes" to availableRoutes,
                    "isMuted" to (callInfo["isMuted"] ?: false),
                    "isOnHold" to (callInfo["isOnHold"] ?: false)
                )
            } else {
                mapOf(
                    "hasActiveCall" to false,
                    "currentAudioRoute" to currentRoute,
                    "availableAudioRoutes" to availableRoutes
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get call audio state", e)
            mapOf("hasActiveCall" to false)
        }
    }
    
    fun setCallAudioRoute(route: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            when (route) {
                0 -> { // Earpiece
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = false
                    Log.d(TAG, "Set call audio route to earpiece")
                }
                1 -> { // Speaker
                    audioManager.isSpeakerphoneOn = true
                    audioManager.isBluetoothScoOn = false
                    Log.d(TAG, "Set call audio route to speaker")
                }
                2 -> { // Bluetooth
                    audioManager.isSpeakerphoneOn = false
                    audioManager.isBluetoothScoOn = true
                    audioManager.startBluetoothSco()
                    Log.d(TAG, "Set call audio route to bluetooth")
                }
                else -> {
                    Log.w(TAG, "Unknown audio route: $route")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set call audio route", e)
            false
        }
    }
    
    fun playDtmfTone(tone: String): Boolean {
        return try {
            // Initialize ToneGenerator if not already done
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            }
            
            // Map tone character to ToneGenerator tone
            val toneType = when (tone) {
                "0" -> ToneGenerator.TONE_DTMF_0
                "1" -> ToneGenerator.TONE_DTMF_1
                "2" -> ToneGenerator.TONE_DTMF_2
                "3" -> ToneGenerator.TONE_DTMF_3
                "4" -> ToneGenerator.TONE_DTMF_4
                "5" -> ToneGenerator.TONE_DTMF_5
                "6" -> ToneGenerator.TONE_DTMF_6
                "7" -> ToneGenerator.TONE_DTMF_7
                "8" -> ToneGenerator.TONE_DTMF_8
                "9" -> ToneGenerator.TONE_DTMF_9
                "*" -> ToneGenerator.TONE_DTMF_S
                "#" -> ToneGenerator.TONE_DTMF_P
                else -> {
                    Log.w(TAG, "Invalid DTMF tone: $tone")
                    return false
                }
            }
            
            // Play the tone for 200ms
            toneGenerator?.startTone(toneType, 200)
            Log.d(TAG, "Playing DTMF tone: $tone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play DTMF tone", e)
            false
        }
    }
    
    fun stopDtmfTone(): Boolean {
        return try {
            toneGenerator?.stopTone()
            Log.d(TAG, "Stopped DTMF tone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop DTMF tone", e)
            false
        }
    }
    
    fun cleanup() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            Log.d(TAG, "CallController cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during CallController cleanup", e)
        }
    }
    
    fun showIncomingCallNotification(phoneNumber: String, callerName: String? = null): Boolean {
        return try {
            // This would typically be handled by the InCallService
            Log.d(TAG, "Show incoming call notification for: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show incoming call notification", e)
            false
        }
    }
    
    fun hideIncomingCallNotification(): Boolean {
        return try {
            // This would typically be handled by the InCallService
            Log.d(TAG, "Hide incoming call notification")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide incoming call notification", e)
            false
        }
    }
    
    fun setCallNotificationChannel(channelId: String, channelName: String, description: String? = null): Boolean {
        return try {
            // This would typically be handled by the InCallService
            Log.d(TAG, "Set call notification channel: $channelId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set call notification channel", e)
            false
        }
    }
    
    fun getCallCapabilities(): Map<String, Any> {
        return try {
            mapOf(
                "canMakeCalls" to true,
                "canReceiveCalls" to true,
                "canHoldCalls" to true,
                "canMuteCalls" to true,
                "canPlayDtmf" to true,
                "supportedAudioRoutes" to listOf("earpiece", "speaker", "bluetooth")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get call capabilities", e)
            emptyMap()
        }
    }
    
    fun checkCallPermission(): Boolean {
        return try {
            // Check if we have the necessary permissions for call management
            context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check call permission", e)
            false
        }
    }
    
    fun requestCallPermission(): Boolean {
        return try {
            // This would typically trigger a permission request
            Log.d(TAG, "Request call permission")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request call permission", e)
            false
        }
    }
    
    /**
     * Get current call information
     */
    fun getCurrentCallInfo(): Map<String, Any>? {
        return try {
            CallBridge.getCurrentCallInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current call info", e)
            null
        }
    }
    
    /**
     * Check if there's an active call
     */
    fun hasActiveCall(): Boolean {
        return try {
            CallBridge.hasActiveCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check active call", e)
            false
        }
    }
    
    /**
     * Check if the telephony connection service is available
     */
    fun isServiceAvailable(): Boolean {
        return try {
            TelephonyConnectionService.isServiceAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service availability", e)
            false
        }
    }
}