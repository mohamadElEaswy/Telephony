package com.shounakmulay.telephony

import android.net.Uri
import android.os.Bundle
import android.telecom.*
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TelephonyConnectionService : ConnectionService() {
    
    companion object {
        private const val TAG = "TelephonyConnectionService"
        private var instance: TelephonyConnectionService? = null
        private var isServiceReady = false
        private var isInitialized = false
        
        fun getInstance(): TelephonyConnectionService? {
            return if (isServiceReady && isInitialized) instance else null
        }
        
        fun isServiceAvailable(): Boolean {
            return isServiceReady && isInitialized && instance != null
        }
        
        fun forceInitialize() {
            isInitialized = true
            Log.d(TAG, "Service forced to initialize")
        }
    }
    
    private var activeConnection: TelephonyConnection? = null
    private var incomingConnection: TelephonyConnection? = null
    private var outgoingConnection: TelephonyConnection? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceReady = true
        isInitialized = true
        Log.d(TAG, "TelephonyConnectionService created and ready")
        
        // Notify that service is now available
        try {
            val plugin = TelephonyPlugin.instance
            plugin?.notifyServiceReady()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to notify plugin of service ready state", e)
        }
        
        // Force initialization after a short delay to ensure proper setup
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            forceInitialize()
            Log.d(TAG, "Service fully initialized")
        }, 1000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TelephonyConnectionService destroying - cleaning up connections")
        
        try {
            // Clean up all active connections
            activeConnection?.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            incomingConnection?.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            outgoingConnection?.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            
            // Clear connection references
            removeConnection()
            
            // Update service state
            isServiceReady = false
            isInitialized = false
            instance = null
            
            Log.d(TAG, "TelephonyConnectionService destroyed and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during ConnectionService cleanup", e)
            // Still mark as destroyed even if cleanup fails
            isServiceReady = false
            isInitialized = false
            instance = null
        }
    }
    
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.d(TAG, "Creating outgoing connection for ${request?.address}")
        
        try {
            val connection = TelephonyConnection(request?.address, false)
            connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            
            // Set capabilities for outgoing calls
            connection.setConnectionCapabilities(
                Connection.CAPABILITY_HOLD or 
                Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_MUTE
            )
            
            // Set caller display name if available
            request?.extras?.getString(TelecomManager.EXTRA_CALL_SUBJECT)?.let { subject ->
                connection.setCallerDisplayName(subject, TelecomManager.PRESENTATION_ALLOWED)
            }
            
            // Set video state
            request?.videoState?.let { videoState ->
                connection.setVideoState(videoState)
            }
            
            // Store the connection
            outgoingConnection = connection
            activeConnection = connection
            
            // Notify Flutter about the new call
            notifyCallStateChanged(connection)
            
            return connection
        } catch (e: Exception) {
            Log.e(TAG, "Error creating outgoing connection", e)
            return null
        }
    }
    
    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "Failed to create outgoing connection for ${request?.address}")
        // Notify user that the call could not be placed
        // You can show a toast or send an event to Flutter
    }
    
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.d(TAG, "Creating incoming connection for ${request?.address}")
        
        try {
            val connection = TelephonyConnection(request?.address, true)
            connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            
            // Set capabilities for incoming calls
            connection.setConnectionCapabilities(
                Connection.CAPABILITY_HOLD or 
                Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_MUTE
            )
            
            // Set initial state to ringing
            connection.setRinging()
            
            // Set caller display name if available
            request?.extras?.getString(TelecomManager.EXTRA_CALL_SUBJECT)?.let { subject ->
                connection.setCallerDisplayName(subject, TelecomManager.PRESENTATION_ALLOWED)
            }
            
            // Store the connection
            incomingConnection = connection
            activeConnection = connection
            
            // Notify Flutter about the new incoming call
            notifyCallStateChanged(connection)
            
            return connection
        } catch (e: Exception) {
            Log.e(TAG, "Error creating incoming connection", e)
            return null
        }
    }
    
    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(TAG, "Failed to create incoming connection for ${request?.address}")
    }
    
    // Public methods for call management
    fun answerCall(): Boolean {
        val connection = incomingConnection ?: activeConnection
        return if (connection != null && connection.state == Connection.STATE_RINGING) {
            try {
                Log.d(TAG, "Answering incoming call")
                connection.onAnswer()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error answering call", e)
                false
            }
        } else {
            Log.w(TAG, "No active incoming call to answer. Connection state: ${connection?.state}")
            false
        }
    }
    
    fun rejectCall(): Boolean {
        val connection = incomingConnection ?: activeConnection
        return if (connection != null && connection.state == Connection.STATE_RINGING) {
            try {
                Log.d(TAG, "Rejecting incoming call")
                connection.onReject()
                incomingConnection = null
                if (activeConnection == connection) {
                    activeConnection = null
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting call", e)
                false
            }
        } else {
            Log.w(TAG, "No active incoming call to reject. Connection state: ${connection?.state}")
            false
        }
    }
    
    fun endCall(): Boolean {
        val connection = activeConnection
        return if (connection != null) {
            try {
                Log.d(TAG, "Ending active call")
                connection.onDisconnect()
                activeConnection = null
                incomingConnection = null
                outgoingConnection = null
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error ending call", e)
                false
            }
        } else {
            Log.w(TAG, "No active call to end")
            false
        }
    }
    
    fun holdCall(): Boolean {
        val connection = activeConnection
        return if (connection != null && connection.state == Connection.STATE_ACTIVE) {
            try {
                Log.d(TAG, "Holding call")
                connection.onHold()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error holding call", e)
                false
            }
        } else {
            Log.w(TAG, "No active call to hold. Connection state: ${connection?.state}")
            false
        }
    }
    
    fun unholdCall(): Boolean {
        val connection = activeConnection
        return if (connection != null && connection.state == Connection.STATE_HOLDING) {
            try {
                Log.d(TAG, "Unholding call")
                connection.onUnhold()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error unholding call", e)
                false
            }
        } else {
            Log.w(TAG, "No held call to unhold. Connection state: ${connection?.state}")
            false
        }
    }
    
    fun muteCall(): Boolean {
        val connection = activeConnection
        return if (connection != null) {
            try {
                Log.d(TAG, "Muting call")
                connection.onMute(true)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error muting call", e)
                false
            }
        } else {
            Log.w(TAG, "No active call to mute")
            false
        }
    }
    
    fun unmuteCall(): Boolean {
        val connection = activeConnection
        return if (connection != null) {
            try {
                Log.d(TAG, "Unmuting call")
                connection.onMute(false)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error unmuting call", e)
                false
            }
        } else {
            Log.w(TAG, "No active call to unmute")
            false
        }
    }
    
    fun getActiveConnection(): TelephonyConnection? {
        return activeConnection
    }
    
    fun getIncomingConnection(): TelephonyConnection? {
        return incomingConnection
    }
    
    fun getOutgoingConnection(): TelephonyConnection? {
        return outgoingConnection
    }
    
    fun hasActiveCall(): Boolean {
        return activeConnection != null
    }
    
    fun hasIncomingCall(): Boolean {
        return incomingConnection != null && incomingConnection?.state == Connection.STATE_RINGING
    }
    
    fun removeConnection() {
        activeConnection = null
        incomingConnection = null
        outgoingConnection = null
        
        Log.d(TAG, "All connections removed")
    }
    
    /**
     * Comprehensive cleanup method to reset all service state
     * Called during app teardown to ensure proper reattachment
     */
    fun cleanup() {
        try {
            Log.d(TAG, "TelephonyConnectionService cleanup started")
            
            // Disconnect and clean up all active connections
            activeConnection?.let { connection ->
                try {
                    connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                    connection.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting active connection", e)
                }
            }
            
            incomingConnection?.let { connection ->
                try {
                    connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                    connection.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting incoming connection", e)
                }
            }
            
            outgoingConnection?.let { connection ->
                try {
                    connection.setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                    connection.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting outgoing connection", e)
                }
            }
            
            // Clear all connection references
            removeConnection()
            
            // Reset service state flags
            isServiceReady = false
            isInitialized = false
            
            Log.d(TAG, "TelephonyConnectionService cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during TelephonyConnectionService cleanup", e)
        }
    }
    
    private fun notifyCallStateChanged(connection: TelephonyConnection) {
        try {
            val pluginInstance = TelephonyPlugin.instance
            if (pluginInstance != null) {
                val callInfo = mapOf<String, Any>(
                    "phoneNumber" to (connection.address?.schemeSpecificPart ?: ""),
                    "displayName" to (connection.callerDisplayName ?: ""),
                    "callType" to if (connection.isIncoming) 0 else 1, // 0 = incoming, 1 = outgoing
                    "callState" to getCallStateIndex(connection.state),
                    "startTime" to System.currentTimeMillis(),
                    "isVideoCall" to (connection.videoState != VideoProfile.STATE_AUDIO_ONLY)
                )
                
                pluginInstance.notifyCallStateChanged(callInfo)
            } else {
                Log.w(TAG, "Plugin instance is null, cannot notify call state change")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying call state change", e)
        }
    }
    
    private fun getCallStateIndex(state: Int): Int {
        return when (state) {
            Connection.STATE_INITIALIZING -> 0 // Idle
            Connection.STATE_NEW -> 0 // Idle
            Connection.STATE_RINGING -> 1 // Ringing
            Connection.STATE_DIALING -> 2 // Offhook
            Connection.STATE_ACTIVE -> 3 // Active
            Connection.STATE_HOLDING -> 4 // Holding
            Connection.STATE_DISCONNECTED -> 5 // Disconnected
            else -> 6 // Unknown
        }
    }
}

class TelephonyConnection(
    private val connectionAddress: Uri?,
    val isIncoming: Boolean
) : Connection() {
    
    override fun onAnswer() {
        Log.d("TelephonyConnection", "Call answered")
        setActive()
        notifyStateChanged()
    }
    
    override fun onAnswer(videoState: Int) {
        Log.d("TelephonyConnection", "Call answered with video")
        setVideoState(videoState)
        setActive()
        notifyStateChanged()
    }
    
    override fun onReject() {
        Log.d("TelephonyConnection", "Call rejected")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
        TelephonyConnectionService.getInstance()?.removeConnection()
        // CallBridge is notified through removeConnection()
        notifyStateChanged()
    }
    
    override fun onDisconnect() {
        Log.d("TelephonyConnection", "Call disconnected")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        TelephonyConnectionService.getInstance()?.removeConnection()
        // CallBridge is notified through removeConnection()
        notifyStateChanged()
    }
    
    override fun onHold() {
        Log.d("TelephonyConnection", "Call held")
        setOnHold()
        notifyStateChanged()
    }
    
    override fun onUnhold() {
        Log.d("TelephonyConnection", "Call unheld")
        setActive()
        notifyStateChanged()
    }
    
    fun onMute(isMuted: Boolean) {
        Log.d("TelephonyConnection", "Call mute changed, muted: $isMuted")
        // Handle mute state change
        notifyMuteStateChanged(isMuted)
    }
    
    override fun onPlayDtmfTone(c: Char) {
        Log.d("TelephonyConnection", "DTMF tone: $c")
        // Handle DTMF tone
    }
    
    override fun onStopDtmfTone() {
        Log.d("TelephonyConnection", "Stop DTMF tone")
        // Handle stop DTMF tone
    }
    
    private fun notifyStateChanged() {
        val service = TelephonyConnectionService.getInstance()
        service?.let {
            // Get plugin instance and notify
            try {
                val pluginInstance = TelephonyPlugin.instance
                if (pluginInstance != null) {
                    val callInfo = mapOf<String, Any>(
                        "phoneNumber" to (connectionAddress?.schemeSpecificPart ?: ""),
                        "displayName" to (callerDisplayName ?: ""),
                        "callType" to if (isIncoming) 0 else 1,
                        "callState" to getCallStateIndex(state),
                        "startTime" to System.currentTimeMillis(),
                        "isVideoCall" to (videoState != VideoProfile.STATE_AUDIO_ONLY)
                    )
                    
                    pluginInstance.notifyCallStateChanged(callInfo)
                } else {
                    Log.w("TelephonyConnection", "Plugin instance is null, cannot notify state change")
                }
            } catch (e: Exception) {
                Log.e("TelephonyConnection", "Error notifying state change", e)
            }
        }
    }
    
    private fun notifyMuteStateChanged(isMuted: Boolean) {
        try {
            val pluginInstance = TelephonyPlugin.instance
            if (pluginInstance != null) {
                pluginInstance.notifyMuteStateChanged(isMuted)
            } else {
                Log.w("TelephonyConnection", "Plugin instance is null, cannot notify mute state change")
            }
        } catch (e: Exception) {
            Log.e("TelephonyConnection", "Error notifying mute state change", e)
        }
    }
    
    private fun getCallStateIndex(state: Int): Int {
        return when (state) {
            STATE_INITIALIZING -> 0 // Idle
            STATE_NEW -> 0 // Idle
            STATE_RINGING -> 1 // Ringing
            STATE_DIALING -> 2 // Offhook
            STATE_ACTIVE -> 3 // Active
            STATE_HOLDING -> 4 // Holding
            STATE_DISCONNECTED -> 5 // Disconnected
            else -> 6 // Unknown
        }
    }
}