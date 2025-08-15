package com.shounakmulay.telephony

import android.content.Context
import androidx.annotation.NonNull
import com.shounakmulay.telephony.sms.IncomingSmsHandler
import com.shounakmulay.telephony.utils.Constants.CHANNEL_SMS
import com.shounakmulay.telephony.sms.IncomingSmsReceiver
import com.shounakmulay.telephony.sms.SmsController
import com.shounakmulay.telephony.sms.SmsMethodCallHandler
import com.shounakmulay.telephony.dialer.DialerController
import com.shounakmulay.telephony.dialer.PhoneAccountController
import com.shounakmulay.telephony.dialer.CallController
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*


class TelephonyPlugin : FlutterPlugin, ActivityAware {

  companion object {
    var instance: TelephonyPlugin? = null
      private set
  }

  private lateinit var smsChannel: MethodChannel

  private lateinit var smsMethodCallHandler: SmsMethodCallHandler

  private lateinit var smsController: SmsController

  private lateinit var binaryMessenger: BinaryMessenger

  private lateinit var permissionsController: PermissionsController

  private lateinit var dialerController: DialerController

  private lateinit var phoneAccountController: PhoneAccountController
  private lateinit var callController: CallController

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    instance = this
    if (!this::binaryMessenger.isInitialized) {
      binaryMessenger = flutterPluginBinding.binaryMessenger
    }

    setupPlugin(flutterPluginBinding.applicationContext, binaryMessenger)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    tearDownPlugin()
    instance = null
  }

  override fun onDetachedFromActivity() {
    tearDownPlugin()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    IncomingSmsReceiver.foregroundSmsChannel = smsChannel
    smsMethodCallHandler.setActivity(binding.activity)
    binding.addRequestPermissionsResultListener(smsMethodCallHandler)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  private fun setupPlugin(context: Context, messenger: BinaryMessenger) {
    smsController = SmsController(context)
    permissionsController = PermissionsController(context)
    dialerController = DialerController(context)
    phoneAccountController = PhoneAccountController(context)
    callController = CallController(context)
    smsMethodCallHandler = SmsMethodCallHandler(context, smsController, permissionsController, dialerController, phoneAccountController, callController)

    smsChannel = MethodChannel(messenger, CHANNEL_SMS)
    smsChannel.setMethodCallHandler(smsMethodCallHandler)
    smsMethodCallHandler.setForegroundChannel(smsChannel)
  }

  private fun tearDownPlugin() {
    IncomingSmsReceiver.foregroundSmsChannel = null
    smsChannel.setMethodCallHandler(null)
  }

  fun notifyServiceReady() {
    // Notify Flutter that the telephony service is ready
    try {
      smsChannel.invokeMethod("onServiceReady", null)
    } catch (e: Exception) {
      android.util.Log.w("TelephonyPlugin", "Failed to notify service ready", e)
    }
  }

  fun notifyCallStateChanged(callInfo: Map<String, Any>) {
    // Notify Flutter about call state changes
    try {
      smsChannel.invokeMethod("onCallStateChanged", callInfo)
      
      // Also trigger background call state handler if configured
      context?.let {
        com.shounakmulay.telephony.call.IncomingCallStateHandler.handleCallStateChange(
          it, callInfo
        )
      }
    } catch (e: Exception) {
      android.util.Log.w("TelephonyPlugin", "Failed to notify call state change", e)
    }
  }

  fun notifyMuteStateChanged(isMuted: Boolean) {
    // Notify Flutter about mute state changes
    try {
      val muteInfo = mapOf("isMuted" to isMuted)
      smsChannel.invokeMethod("onMuteStateChanged", muteInfo)
    } catch (e: Exception) {
      android.util.Log.w("TelephonyPlugin", "Failed to notify mute state change", e)
    }
  }

}
