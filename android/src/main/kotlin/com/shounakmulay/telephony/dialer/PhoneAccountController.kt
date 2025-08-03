package com.shounakmulay.telephony.dialer

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.telecom.*
import com.shounakmulay.telephony.sms.SmsController

class PhoneAccountController(private val context: Context) {
    
    private val telecomManager: TelecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }
    
    fun registerPhoneAccount(
        accountId: String,
        label: String,
        capabilities: Int = PhoneAccount.CAPABILITY_CALL_PROVIDER
    ): Boolean {
        return try {
            // Use the actual ConnectionService implementation
            val componentName = ComponentName(context.packageName, "com.example.ranan.MyConnectionService")
            val phoneAccountHandle = PhoneAccountHandle(componentName, accountId)
            
            val phoneAccount = PhoneAccount.Builder(phoneAccountHandle, label)
                .setCapabilities(capabilities or PhoneAccount.CAPABILITY_CALL_SUBJECT)
                .setIcon(Icon.createWithResource(context, android.R.drawable.sym_action_call))
                .setShortDescription(label)
                .setSupportedUriSchemes(listOf("tel"))
                .build()
            
            telecomManager.registerPhoneAccount(phoneAccount)
            true
        } catch (e: Exception) {
            android.util.Log.e("PhoneAccountController", "Failed to register phone account", e)
            false
        }
    }
    
            fun unregisterPhoneAccount(accountId: String): Boolean {
        return try {
            val componentName = ComponentName(context.packageName, "com.example.ranan.MyConnectionService")
            val phoneAccountHandle = PhoneAccountHandle(componentName, accountId)
            telecomManager.unregisterPhoneAccount(phoneAccountHandle)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getRegisteredPhoneAccounts(): List<Map<String, Any>> {
        return try {
            telecomManager.selfManagedPhoneAccounts.map { handle ->
                val account = telecomManager.getPhoneAccount(handle)
                mapOf(
                    "id" to handle.id,
                    "label" to (account?.label?.toString() ?: ""),
                    "enabled" to (account?.isEnabled ?: false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun isPhoneAccountEnabled(accountId: String): Boolean {
        return try {
            val componentName = ComponentName(context.packageName, "com.example.ranan.MyConnectionService")
            val phoneAccountHandle = PhoneAccountHandle(componentName, accountId)
            val account = telecomManager.getPhoneAccount(phoneAccountHandle)
            account?.isEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }
}