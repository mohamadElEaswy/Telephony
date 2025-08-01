package com.shounakmulay.telephony.dialer

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.*
import androidx.annotation.RequiresApi

class DialerController(private val context: Context) {
    
    @RequiresApi(Build.VERSION_CODES.Q)
    fun requestDefaultDialerRole(activity: Activity): Boolean {
        return try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && 
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                activity.startActivityForResult(intent, 100)
                true
            } else {
                false // Already default or not available
            }
        } catch (e: Exception) {
            false
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    fun isDefaultDialer(): Boolean {
        return try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } catch (e: Exception) {
            false
        }
    }
    
    fun openCallSettings() {
        try {
            val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to direct settings
            try {
                val intent = Intent().apply {
                    setComponent(ComponentName(
                        "com.android.server.telecom",
                        "com.android.server.telecom.settings.EnableAccountPreferenceActivity"
                    ))
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // If all else fails, open general settings
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }
}