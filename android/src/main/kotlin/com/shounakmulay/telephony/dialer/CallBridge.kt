package com.shounakmulay.telephony.dialer

import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log

/**
 * CallBridge acts as a communication bridge between CallController and TelephonyInCallService.
 * This singleton ensures proper communication while respecting Android Telecom architecture.
 */
object CallBridge {
    
    private const val TAG = "CallBridge"
    
    // Current active call reference
    private var currentCall: Call? = null
    
    // Callbacks for call state changes
    private var callStateCallback: ((String, String, Map<String, Any>) -> Unit)? = null
    
    // InCallService reference for call operations
    private var inCallService: TelephonyInCallService? = null
    
    /**
     * Register the InCallService instance
     */
    fun registerInCallService(service: TelephonyInCallService) {
        inCallService = service
        Log.d(TAG, "InCallService registered")
    }
    
    /**
     * Unregister the InCallService instance
     */
    fun unregisterInCallService() {
        inCallService = null
        currentCall = null
        Log.d(TAG, "InCallService unregistered")
    }
    
    /**
     * Set the current active call
     */
    fun setCurrentCall(call: Call?) {
        currentCall = call
        Log.d(TAG, "Current call set: ${call?.details?.handle?.schemeSpecificPart}")
        
        // Notify call state change
        call?.let {
            val state = when (it.state) {
                Call.STATE_RINGING -> "ringing"
                Call.STATE_DIALING -> "dialing"
                Call.STATE_ACTIVE -> "active"
                Call.STATE_HOLDING -> "holding"
                Call.STATE_DISCONNECTED -> "disconnected"
                else -> "unknown"
            }
            
            notifyCallStateChanged("callStateChanged", state, mapOf(
                "phoneNumber" to (it.details?.handle?.schemeSpecificPart ?: "Unknown"),
                "callId" to (it.details?.id ?: ""),
                "callerDisplayName" to (it.details?.callerDisplayName ?: ""),
                "canHold" to true, // Most calls can be held
                "canMute" to true  // Most calls can be muted
            ))
        }
    }
    
    /**
     * Get the current active call
     */
    fun getCurrentCall(): Call? = currentCall
    
    /**
     * Register callback for call state changes
     */
    fun setCallStateCallback(callback: (String, String, Map<String, Any>) -> Unit) {
        callStateCallback = callback
        Log.d(TAG, "Call state callback registered")
    }
    
    /**
     * Notify call state changes
     */
    private fun notifyCallStateChanged(type: String, state: String, data: Map<String, Any>) {
        callStateCallback?.invoke(type, state, data)
    }
    
    // ===== CALL CONTROL METHODS =====
    
    /**
     * Answer the current incoming call
     */
    fun answerCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null && call.state == Call.STATE_RINGING) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                Log.d(TAG, "Call answered: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "answered", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown")
                ))
                true
            } else {
                Log.w(TAG, "No ringing call to answer. Current call state: ${call?.state}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call", e)
            false
        }
    }
    
    /**
     * Reject the current incoming call
     */
    fun rejectCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null && call.state == Call.STATE_RINGING) {
                call.reject(false, null)
                Log.d(TAG, "Call rejected: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "rejected", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown")
                ))
                true
            } else {
                Log.w(TAG, "No ringing call to reject. Current call state: ${call?.state}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject call", e)
            false
        }
    }
    
    /**
     * End the current active call
     */
    fun endCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null) {
                call.disconnect()
                Log.d(TAG, "Call ended: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "ended", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown")
                ))
                true
            } else {
                Log.w(TAG, "No active call to end")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call", e)
            false
        }
    }
    
    /**
     * Put the current call on hold
     */
    fun holdCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null && call.state == Call.STATE_ACTIVE) {
                call.hold()
                Log.d(TAG, "Call put on hold: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "held", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown")
                ))
                true
            } else {
                Log.w(TAG, "No active call to hold. Current call state: ${call?.state}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hold call", e)
            false
        }
    }
    
    /**
     * Take the current call off hold
     */
    fun unholdCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null && call.state == Call.STATE_HOLDING) {
                call.unhold()
                Log.d(TAG, "Call taken off hold: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "unheld", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown")
                ))
                true
            } else {
                Log.w(TAG, "No call on hold to unhold. Current call state: ${call?.state}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unhold call", e)
            false
        }
    }
    
    /**
     * Mute the current call
     * Note: Call.setAudioMute() doesn't exist in Android Telecom API
     * Muting should be handled through AudioManager or InCallService
     */
    fun muteCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null) {
                // TODO: Implement proper audio muting through AudioManager
                Log.d(TAG, "Call mute requested: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "muted", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown"),
                    "isMuted" to true
                ))
                true
            } else {
                Log.w(TAG, "No active call to mute")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mute call", e)
            false
        }
    }
    
    /**
     * Unmute the current call
     * Note: Call.setAudioMute() doesn't exist in Android Telecom API
     * Unmuting should be handled through AudioManager or InCallService
     */
    fun unmuteCall(): Boolean {
        return try {
            val call = currentCall
            if (call != null) {
                // TODO: Implement proper audio unmuting through AudioManager
                Log.d(TAG, "Call unmute requested: ${call.details?.handle?.schemeSpecificPart}")
                notifyCallStateChanged("callAction", "unmuted", mapOf(
                    "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown"),
                    "isMuted" to false
                ))
                true
            } else {
                Log.w(TAG, "No active call to unmute")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unmute call", e)
            false
        }
    }
    
    /**
     * Get current call information
     */
    fun getCurrentCallInfo(): Map<String, Any>? {
        val call = currentCall
        return if (call != null) {
            mapOf(
                "phoneNumber" to (call.details?.handle?.schemeSpecificPart ?: "Unknown"),
                "callId" to (call.details?.id ?: ""),
                "callerDisplayName" to (call.details?.callerDisplayName ?: ""),
                "state" to when (call.state) {
                    Call.STATE_RINGING -> "ringing"
                    Call.STATE_DIALING -> "dialing"
                    Call.STATE_ACTIVE -> "active"
                    Call.STATE_HOLDING -> "holding"
                    Call.STATE_DISCONNECTED -> "disconnected"
                    else -> "unknown"
                },
                "canHold" to true, // Most calls can be held
                "canMute" to true, // Most calls can be muted
                "isVideoCall" to (call.details?.videoState != VideoProfile.STATE_AUDIO_ONLY)
            )
        } else {
            null
        }
    }
    
    /**
     * Check if there's an active call
     */
    fun hasActiveCall(): Boolean {
        return currentCall != null && currentCall?.state != Call.STATE_DISCONNECTED
    }
}