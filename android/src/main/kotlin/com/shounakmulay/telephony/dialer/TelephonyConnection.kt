package com.shounakmulay.telephony.dialer

import android.content.Context
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

class TelephonyConnection(private val context: Context? = null) : Connection() {

    companion object {
        private const val TAG = "TelephonyConnection"
    }

    override fun onCallAudioStateChanged(state: android.telecom.CallAudioState?) {
        Log.d(TAG, "onCallAudioStateChanged: state=$state")
        super.onCallAudioStateChanged(state)
    }

    override fun onDisconnect() {
        Log.d(TAG, "onDisconnect")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL, "User disconnected"))
        destroy()
    }

    override fun onHold() {
        Log.d(TAG, "onHold")
        setOnHold()
    }

    override fun onPlayDtmfTone(c: Char) {
        Log.d(TAG, "onPlayDtmfTone: c=$c")
        // Implement DTMF tone playback if needed
    }

    override fun onReject() {
        Log.d(TAG, "onReject")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED, "Call rejected"))
        destroy()
    }

    override fun onSeparate() {
        Log.d(TAG, "onSeparate")
        // Handle call separation if needed
    }

    override fun onShowIncomingCallUi() {
        Log.d(TAG, "onShowIncomingCallUi")
        super.onShowIncomingCallUi()
        
        // The InCallService will handle showing the incoming call UI
        // This method is called when the system wants to show incoming call UI
        Log.d(TAG, "System requested to show incoming call UI")
    }

    override fun onSilence() {
        Log.d(TAG, "onSilence")
        // Handle call silence if needed
    }

    override fun onStateChanged(state: Int) {
        Log.d(TAG, "onStateChanged: state=$state")
        super.onStateChanged(state)
    }

    override fun onStopDtmfTone() {
        Log.d(TAG, "onStopDtmfTone")
        // Stop DTMF tone if needed
    }

    override fun onUnhold() {
        Log.d(TAG, "onUnhold")
        setActive()
    }

    // Helper methods for managing call state
    fun setCallActive() {
        Log.d(TAG, "setCallActive")
        setActive()
    }

    fun setCallDisconnected(reason: String) {
        Log.d(TAG, "setCallDisconnected: reason=$reason")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL, reason))
        destroy()
    }

    fun setCallDialing() {
        Log.d(TAG, "setCallDialing")
        setDialing()
    }

    fun setCallRinging() {
        Log.d(TAG, "setCallRinging")
        setRinging()
    }
} 