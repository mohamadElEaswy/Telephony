package com.shounakmulay.telephony.dialer

import android.content.Context
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.util.Log

class TelephonyConnectionService : ConnectionService() {
    
    companion object {
        private const val TAG = "TelephonyConnectionService"
    }
    
    override fun onCreateOutgoingConnection(
        phoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.d(TAG, "onCreateOutgoingConnection: phoneAccount=$phoneAccount, request=$request")
        
        if (request == null) {
            Log.w(TAG, "ConnectionRequest is null")
            return null
        }
        
        val connection = TelephonyConnection(this as Context)
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE or 
                                          Connection.CAPABILITY_HOLD or
                                          Connection.CAPABILITY_SUPPORT_HOLD)
        
        val address = request.address
        if (address != null) {
            connection.setAddress(address, 1) // PRESENTATION_ALLOWED = 1
            connection.setCallerDisplayName(address.schemeSpecificPart, 1) // PRESENTATION_ALLOWED = 1
        }
        
        return connection
    }
    
    override fun onCreateIncomingConnection(
        phoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Log.d(TAG, "onCreateIncomingConnection: phoneAccount=$phoneAccount, request=$request")
        
        if (request == null) {
            Log.w(TAG, "ConnectionRequest is null")
            return null
        }
        
        val connection = TelephonyConnection(this as Context)
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE or 
                                          Connection.CAPABILITY_HOLD or
                                          Connection.CAPABILITY_SUPPORT_HOLD)
        
        val address = request.address
        if (address != null) {
            connection.setAddress(address, 1) // PRESENTATION_ALLOWED = 1
            connection.setCallerDisplayName(address.schemeSpecificPart, 1) // PRESENTATION_ALLOWED = 1
        }
        
        return connection
    }
    
    override fun onCreateOutgoingConnectionFailed(
        phoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed: phoneAccount=$phoneAccount, request=$request")
    }
    
    override fun onCreateIncomingConnectionFailed(
        phoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateIncomingConnectionFailed: phoneAccount=$phoneAccount, request=$request")
    }
    
    override fun onConference(
        connection1: Connection?,
        connection2: Connection?
    ) {
        Log.d(TAG, "onConference: connection1=$connection1, connection2=$connection2")
    }
}