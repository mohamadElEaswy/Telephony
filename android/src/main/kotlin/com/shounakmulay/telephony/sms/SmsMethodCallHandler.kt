package com.shounakmulay.telephony.sms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.shounakmulay.telephony.PermissionsController
import com.shounakmulay.telephony.utils.ActionType
import com.shounakmulay.telephony.dialer.DialerController
import com.shounakmulay.telephony.dialer.PhoneAccountController
import com.shounakmulay.telephony.dialer.CallController
import com.shounakmulay.telephony.call.IncomingCallStateHandler
import com.shounakmulay.telephony.utils.Constants
import com.shounakmulay.telephony.utils.Constants.ADDRESS
import com.shounakmulay.telephony.utils.Constants.BACKGROUND_HANDLE
import com.shounakmulay.telephony.utils.Constants.CALL_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.DIALER_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.PHONE_ACCOUNT_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.ACCOUNT_ID
import com.shounakmulay.telephony.utils.Constants.ACCOUNT_LABEL
import com.shounakmulay.telephony.utils.Constants.ACCOUNT_CAPABILITIES
import com.shounakmulay.telephony.utils.Constants.DEFAULT_CONVERSATION_PROJECTION
import com.shounakmulay.telephony.utils.Constants.DEFAULT_SMS_PROJECTION
import com.shounakmulay.telephony.utils.Constants.FAILED_FETCH
import com.shounakmulay.telephony.utils.Constants.GET_STATUS_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.ILLEGAL_ARGUMENT
import com.shounakmulay.telephony.utils.Constants.LISTEN_STATUS
import com.shounakmulay.telephony.utils.Constants.SUBSCRIPTION_ID
import com.shounakmulay.telephony.utils.Constants.MESSAGE_BODY
import com.shounakmulay.telephony.utils.Constants.PERMISSION_DENIED
import com.shounakmulay.telephony.utils.Constants.PERMISSION_DENIED_MESSAGE
import com.shounakmulay.telephony.utils.Constants.PERMISSION_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.PHONE_NUMBER
import com.shounakmulay.telephony.utils.Constants.PROJECTION
import com.shounakmulay.telephony.utils.Constants.SELECTION
import com.shounakmulay.telephony.utils.Constants.SELECTION_ARGS
import com.shounakmulay.telephony.utils.Constants.SETUP_HANDLE
import com.shounakmulay.telephony.utils.Constants.SHARED_PREFERENCES_NAME
import com.shounakmulay.telephony.utils.Constants.SHARED_PREFS_DISABLE_BACKGROUND_EXE
import com.shounakmulay.telephony.utils.Constants.SMS_BACKGROUND_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.SMS_DELIVERED
import com.shounakmulay.telephony.utils.Constants.SMS_QUERY_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.SMS_SEND_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.SMS_SENT
import com.shounakmulay.telephony.utils.Constants.SORT_ORDER
import com.shounakmulay.telephony.utils.Constants.WRONG_METHOD_TYPE
import com.shounakmulay.telephony.utils.ContentUri
import com.shounakmulay.telephony.utils.SmsAction
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import com.shounakmulay.telephony.utils.Constants.CALL_MANAGEMENT_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.CALL_NOTIFICATION_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.CALL_CAPABILITIES_REQUEST_CODE
import com.shounakmulay.telephony.utils.Constants.INTENT_HANDLING_REQUEST_CODE


class SmsMethodCallHandler(
    private val context: Context,
    private val smsController: SmsController,
    private val permissionsController: PermissionsController,
    private val dialerController: DialerController,
    private val phoneAccountController: PhoneAccountController,
    private val callController: CallController
) : PluginRegistry.RequestPermissionsResultListener,
    MethodChannel.MethodCallHandler,
    BroadcastReceiver() {

  private lateinit var result: MethodChannel.Result
  private var isResultSubmitted = false
  private lateinit var action: SmsAction
  private lateinit var foregroundChannel: MethodChannel
  private lateinit var activity: Activity

  private var projection: List<String>? = null
  private var selection: String? = null
  private var selectionArgs: List<String>? = null
  private var sortOrder: String? = null

  private lateinit var messageBody: String
  private lateinit var address: String
  private var subId: Int = -1
  private var listenStatus: Boolean = false

  private var setupHandle: Long = -1
  private var backgroundHandle: Long = -1

  private lateinit var phoneNumber: String

  private var requestCode: Int = -1

  private lateinit var accountId: String
  private lateinit var accountLabel: String
  private var accountCapabilities: Int = -1

  // Call management variables
  private var callPhoneNumber: String = ""
  private var callAccountId: String? = null
  private var callRoute: Int = 0
  private var callTone: String = ""
  private var callChannelId: String = ""
  private var callChannelName: String = ""
  private var callDescription: String? = null
  private var callCallerName: String? = null

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    this.result = result
    this.isResultSubmitted = false

    action = SmsAction.fromMethod(call.method)

    if (action == SmsAction.NO_SUCH_METHOD) {
      result.notImplemented()
      return
    }

    // Extract call-related arguments
    callPhoneNumber = call.argument<String>("phoneNumber") ?: ""
    callAccountId = call.argument<String>("accountId")
    callRoute = call.argument<Int>("route") ?: 0
    callTone = call.argument<String>("tone") ?: ""
    callChannelId = call.argument<String>("channelId") ?: ""
    callChannelName = call.argument<String>("channelName") ?: ""
    callDescription = call.argument<String>("description")
    callCallerName = call.argument<String>("callerName")

    when (action.toActionType()) {
      ActionType.GET_SMS -> {
        projection = call.argument(PROJECTION)
        selection = call.argument(SELECTION)
        selectionArgs = call.argument(SELECTION_ARGS)
        sortOrder = call.argument(SORT_ORDER)

        handleMethod(action, SMS_QUERY_REQUEST_CODE)
      }
      ActionType.SEND_SMS -> {
        if (call.hasArgument(MESSAGE_BODY)
            && call.hasArgument(ADDRESS)) {
          val messageBody = call.argument<String>(MESSAGE_BODY)
          val address = call.argument<String>(ADDRESS)
          var subId: Int? = call.argument<Int>(SUBSCRIPTION_ID)
          if (messageBody.isNullOrBlank() || address.isNullOrBlank()) {
            result.error(ILLEGAL_ARGUMENT, Constants.MESSAGE_OR_ADDRESS_CANNOT_BE_NULL, null)
            return
          }

          this.messageBody = messageBody
          this.address = address
          if(subId != null){
            this.subId = subId
          }

          listenStatus = call.argument(LISTEN_STATUS) ?: false
        }
        handleMethod(action, SMS_SEND_REQUEST_CODE)
      }
      ActionType.BACKGROUND -> {
        if (call.hasArgument(SETUP_HANDLE)
            && call.hasArgument(BACKGROUND_HANDLE)) {
          val setupHandle = call.argument<Long>(SETUP_HANDLE)
          val backgroundHandle = call.argument<Long>(BACKGROUND_HANDLE)
          if (setupHandle == null || backgroundHandle == null) {
            result.error(ILLEGAL_ARGUMENT, "Setup handle or background handle missing", null)
            return
          }

          this.setupHandle = setupHandle
          this.backgroundHandle = backgroundHandle
        }
        handleMethod(action, SMS_BACKGROUND_REQUEST_CODE)
      }
      ActionType.GET -> handleMethod(action, GET_STATUS_REQUEST_CODE)
      ActionType.PERMISSION -> handleMethod(action, PERMISSION_REQUEST_CODE)
      ActionType.CALL -> {
        if (call.hasArgument(PHONE_NUMBER)) {
          val phoneNumber = call.argument<String>(PHONE_NUMBER)

          if (!phoneNumber.isNullOrBlank()) {
            this.phoneNumber = phoneNumber
          }

          handleMethod(action, CALL_REQUEST_CODE)
        }
      }
      ActionType.DIALER -> handleMethod(action, DIALER_REQUEST_CODE)
      ActionType.PHONE_ACCOUNT -> {
        if (call.hasArgument(ACCOUNT_ID)) {
          val accountId = call.argument<String>(ACCOUNT_ID)
          val accountLabel = call.argument<String>(ACCOUNT_LABEL) ?: ""
          val accountCapabilities = call.argument<Int>(ACCOUNT_CAPABILITIES) ?: 0

          if (!accountId.isNullOrBlank()) {
            this.accountId = accountId
            this.accountLabel = accountLabel
            this.accountCapabilities = accountCapabilities
          }
        }
        handleMethod(action, PHONE_ACCOUNT_REQUEST_CODE)
      }
      ActionType.CALL_MANAGEMENT -> handleMethod(action, CALL_MANAGEMENT_REQUEST_CODE)
      ActionType.CALL_NOTIFICATION -> handleMethod(action, CALL_NOTIFICATION_REQUEST_CODE)
      ActionType.CALL_CAPABILITIES -> handleMethod(action, CALL_CAPABILITIES_REQUEST_CODE)
      ActionType.INTENT_HANDLING -> handleMethod(action, INTENT_HANDLING_REQUEST_CODE)
    }
  }

  /**
   * Called by [handleMethod] after checking the permissions.
   *
   * #####
   *
   * If permission was not previously granted, [handleMethod] will request the user for permission
   *
   * Once user grants the permission this method will be executed.
   *
   * #####
   */
  private fun execute(smsAction: SmsAction) {
    try {
      when (smsAction.toActionType()) {
        ActionType.GET_SMS -> handleGetSmsActions(smsAction)
        ActionType.SEND_SMS -> handleSendSmsActions(smsAction)
        ActionType.BACKGROUND -> handleBackgroundActions(smsAction)
        ActionType.GET -> handleGetActions(smsAction)
        ActionType.PERMISSION -> safeResultSuccess(true)
        ActionType.CALL -> handleCallActions(smsAction)
        ActionType.DIALER -> handleDialerActions(smsAction)
        ActionType.PHONE_ACCOUNT -> handlePhoneAccountActions(smsAction)
        ActionType.CALL_MANAGEMENT -> handleCallManagementActions(smsAction)
        ActionType.CALL_NOTIFICATION -> handleCallNotificationActions(smsAction)
        ActionType.CALL_CAPABILITIES -> handleCallCapabilitiesActions(smsAction)
        ActionType.INTENT_HANDLING -> handleIntentActions(smsAction)
      }
    } catch (e: IllegalArgumentException) {
      safeResultError(ILLEGAL_ARGUMENT, WRONG_METHOD_TYPE, null)
    } catch (e: RuntimeException) {
      safeResultError(FAILED_FETCH, e.message, null)
    }
  }

  private fun handleGetSmsActions(smsAction: SmsAction) {
    if (projection == null) {
      projection = if (smsAction == SmsAction.GET_CONVERSATIONS) DEFAULT_CONVERSATION_PROJECTION else DEFAULT_SMS_PROJECTION
    }
    val contentUri = when (smsAction) {
      SmsAction.GET_INBOX -> ContentUri.INBOX
      SmsAction.GET_SENT -> ContentUri.SENT
      SmsAction.GET_DRAFT -> ContentUri.DRAFT
      SmsAction.GET_CONVERSATIONS -> ContentUri.CONVERSATIONS
      else -> throw IllegalArgumentException()
    }
    val messages = smsController.getMessages(contentUri, projection!!, selection, selectionArgs, sortOrder)
    safeResultSuccess(messages)
  }

  private fun handleSendSmsActions(smsAction: SmsAction) {
    if (listenStatus) {
      val intentFilter = IntentFilter().apply {
        addAction(Constants.ACTION_SMS_SENT)
        addAction(Constants.ACTION_SMS_DELIVERED)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.applicationContext.registerReceiver(this, intentFilter, RECEIVER_EXPORTED)
      }else {
        context.applicationContext.registerReceiver(this, intentFilter)
      }
    }
    when (smsAction) {
      SmsAction.SEND_SMS -> smsController.sendSms(address, messageBody, listenStatus, subId)
      SmsAction.SEND_MULTIPART_SMS -> smsController.sendMultipartSms(address, messageBody, listenStatus, subId)
      SmsAction.SEND_SMS_INTENT -> smsController.sendSmsIntent(address, messageBody)
      else -> throw IllegalArgumentException()
    }
    safeResultSuccess(null)
  }

  private fun handleBackgroundActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.START_BACKGROUND_SERVICE -> {
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putBoolean(SHARED_PREFS_DISABLE_BACKGROUND_EXE, false).apply()
        IncomingSmsHandler.setBackgroundSetupHandle(context, setupHandle)
        IncomingSmsHandler.setBackgroundMessageHandle(context, backgroundHandle)
      }
      SmsAction.BACKGROUND_SERVICE_INITIALIZED -> {
        IncomingSmsHandler.onChannelInitialized(context.applicationContext)
      }
      SmsAction.DISABLE_BACKGROUND_SERVICE -> {
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putBoolean(SHARED_PREFS_DISABLE_BACKGROUND_EXE, true).apply()
      }
      SmsAction.SETUP_CALL_STATE_BACKGROUND -> {
        IncomingCallStateHandler.setBackgroundSetupHandle(context, setupHandle)
        IncomingCallStateHandler.setBackgroundCallStateHandle(context, backgroundHandle)
      }
      else -> throw IllegalArgumentException()
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleGetActions(smsAction: SmsAction) {
    smsController.apply {
      val value: Any = when (smsAction) {
        SmsAction.IS_SMS_CAPABLE -> isSmsCapable()
        SmsAction.GET_CELLULAR_DATA_STATE -> getCellularDataState()
        SmsAction.GET_CALL_STATE -> getCallState()
        SmsAction.GET_DATA_ACTIVITY -> getDataActivity()
        SmsAction.GET_NETWORK_OPERATOR -> getNetworkOperator()
        SmsAction.GET_NETWORK_OPERATOR_NAME -> getNetworkOperatorName()
        SmsAction.GET_DATA_NETWORK_TYPE -> getDataNetworkType()
        SmsAction.GET_PHONE_TYPE -> getPhoneType()
        SmsAction.GET_SIM_OPERATOR -> getSimOperator()
        SmsAction.GET_SIM_OPERATOR_NAME -> getSimOperatorName()
        SmsAction.GET_SIM_STATE -> getSimState()
        SmsAction.IS_NETWORK_ROAMING -> isNetworkRoaming()
        SmsAction.GET_SIGNAL_STRENGTH -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSignalStrength()
                ?: safeResultError("SERVICE_STATE_NULL", "Error getting service state", null)

          } else {
            safeResultError("INCORRECT_SDK_VERSION", "getServiceState() can only be called on Android Q and above", null)
          }
        }
        SmsAction.GET_SERVICE_STATE -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getServiceState()
                ?: safeResultError("SERVICE_STATE_NULL", "Error getting service state", null)
          } else {
            safeResultError("INCORRECT_SDK_VERSION", "getServiceState() can only be called on Android O and above", null)
          }
        }
        else -> throw IllegalArgumentException()
      }
      safeResultSuccess(value)
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleCallActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.OPEN_DIALER -> smsController.openDialer(phoneNumber)
      SmsAction.DIAL_PHONE_NUMBER -> smsController.dialPhoneNumber(phoneNumber)
      else -> throw IllegalArgumentException()
    }
  }


  /**
   * Calls the [execute] method after checking if the necessary permissions are granted.
   *
   * If not granted then it will request the permission from the user.
   */
  private fun handleMethod(smsAction: SmsAction, requestCode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkOrRequestPermission(smsAction, requestCode)) {
      execute(smsAction)
    }
  }

  /**
   * Check and request if necessary for all the SMS permissions listed in the manifest
   */
  @RequiresApi(Build.VERSION_CODES.M)
  fun checkOrRequestPermission(smsAction: SmsAction, requestCode: Int): Boolean {
    this.action = smsAction
    this.requestCode = requestCode
    when (smsAction) {
      SmsAction.GET_INBOX,
      SmsAction.GET_SENT,
      SmsAction.GET_DRAFT,
      SmsAction.GET_CONVERSATIONS,
      SmsAction.SEND_SMS,
      SmsAction.SEND_MULTIPART_SMS,
      SmsAction.SEND_SMS_INTENT,
      SmsAction.START_BACKGROUND_SERVICE,
      SmsAction.BACKGROUND_SERVICE_INITIALIZED,
      SmsAction.DISABLE_BACKGROUND_SERVICE,
      SmsAction.SETUP_CALL_STATE_BACKGROUND,
      SmsAction.REQUEST_SMS_PERMISSIONS -> {
        val permissions = permissionsController.getSmsPermissions()
        return checkOrRequestPermission(permissions, requestCode)
      }
      SmsAction.GET_DATA_NETWORK_TYPE,
      SmsAction.OPEN_DIALER,
      SmsAction.DIAL_PHONE_NUMBER,
      SmsAction.REQUEST_PHONE_PERMISSIONS -> {
        val permissions = permissionsController.getPhonePermissions()
        return checkOrRequestPermission(permissions, requestCode)
      }
      SmsAction.GET_SERVICE_STATE -> {
        val permissions = permissionsController.getServiceStatePermissions()
        return checkOrRequestPermission(permissions, requestCode)
      }
      SmsAction.REQUEST_PHONE_AND_SMS_PERMISSIONS -> {
        val permissions = listOf(permissionsController.getSmsPermissions(), permissionsController.getPhonePermissions()).flatten()
        return checkOrRequestPermission(permissions, requestCode)
      }
      SmsAction.IS_SMS_CAPABLE,
      SmsAction.GET_CELLULAR_DATA_STATE,
      SmsAction.GET_CALL_STATE,
      SmsAction.GET_DATA_ACTIVITY,
      SmsAction.GET_NETWORK_OPERATOR,
      SmsAction.GET_NETWORK_OPERATOR_NAME,
      SmsAction.GET_PHONE_TYPE,
      SmsAction.GET_SIM_OPERATOR,
      SmsAction.GET_SIM_OPERATOR_NAME,
      SmsAction.GET_SIM_STATE,
      SmsAction.IS_NETWORK_ROAMING,
      SmsAction.GET_SIGNAL_STRENGTH,
      SmsAction.REQUEST_DEFAULT_DIALER,
      SmsAction.IS_DEFAULT_DIALER,
      SmsAction.OPEN_CALL_SETTINGS,
      SmsAction.REGISTER_PHONE_ACCOUNT,
      SmsAction.UNREGISTER_PHONE_ACCOUNT,
      SmsAction.GET_PHONE_ACCOUNTS,
      SmsAction.IS_PHONE_ACCOUNT_ENABLED,
      SmsAction.MAKE_CALL,
      SmsAction.END_CALL,
      SmsAction.ANSWER_CALL,
      SmsAction.REJECT_CALL,
      SmsAction.HOLD_CALL,
      SmsAction.UNHOLD_CALL,
      SmsAction.MUTE_CALL,
      SmsAction.UNMUTE_CALL,
      SmsAction.GET_CALL_AUDIO_STATE,
      SmsAction.SET_CALL_AUDIO_ROUTE,
      SmsAction.PLAY_DTMF_TONE,
      SmsAction.STOP_DTMF_TONE,
      SmsAction.SHOW_INCOMING_CALL_NOTIFICATION,
      SmsAction.HIDE_INCOMING_CALL_NOTIFICATION,
      SmsAction.SET_CALL_NOTIFICATION_CHANNEL,
      SmsAction.GET_CALL_CAPABILITIES,
      SmsAction.CHECK_CALL_PERMISSION,
      SmsAction.REQUEST_CALL_PERMISSION,
      SmsAction.IS_SERVICE_AVAILABLE,
      SmsAction.GET_INITIAL_INTENT,
      SmsAction.NO_SUCH_METHOD -> return true
    }
    return true
  }

  fun setActivity(activity: Activity) {
    this.activity = activity
  }

  private fun handleDialerActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.REQUEST_DEFAULT_DIALER -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val success = dialerController.requestDefaultDialerRole(activity)
          safeResultSuccess(success)
        } else {
          safeResultError("NOT_SUPPORTED", "Requires Android API 29+", null)
        }
      }
      SmsAction.IS_DEFAULT_DIALER -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val isDefault = dialerController.isDefaultDialer()
          safeResultSuccess(isDefault)
        } else {
          safeResultError("NOT_SUPPORTED", "Requires Android API 29+", null)
        }
      }
      SmsAction.OPEN_CALL_SETTINGS -> {
        dialerController.openCallSettings()
        safeResultSuccess(true)
      }
      else -> throw IllegalArgumentException()
    }
  }

  private fun handlePhoneAccountActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.REGISTER_PHONE_ACCOUNT -> {
        val success = phoneAccountController.registerPhoneAccount(accountId, accountLabel, accountCapabilities)
        safeResultSuccess(success)
      }
      SmsAction.UNREGISTER_PHONE_ACCOUNT -> {
        val success = phoneAccountController.unregisterPhoneAccount(accountId)
        safeResultSuccess(success)
      }
      SmsAction.GET_PHONE_ACCOUNTS -> {
        val accounts = phoneAccountController.getRegisteredPhoneAccounts()
        safeResultSuccess(accounts)
      }
      SmsAction.IS_PHONE_ACCOUNT_ENABLED -> {
        val isEnabled = phoneAccountController.isPhoneAccountEnabled(accountId)
        safeResultSuccess(isEnabled)
      }
      else -> throw IllegalArgumentException()
    }
  }

  private fun handleCallManagementActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.MAKE_CALL -> {
        val success = callController.makeCall(callPhoneNumber, callAccountId)
        safeResultSuccess(success)
      }
      SmsAction.END_CALL -> {
        val success = callController.endCall()
        safeResultSuccess(success)
      }
      SmsAction.ANSWER_CALL -> {
        val success = callController.answerCall()
        safeResultSuccess(success)
      }
      SmsAction.REJECT_CALL -> {
        val success = callController.rejectCall()
        safeResultSuccess(success)
      }
      SmsAction.HOLD_CALL -> {
        val success = callController.holdCall()
        safeResultSuccess(success)
      }
      SmsAction.UNHOLD_CALL -> {
        val success = callController.unholdCall()
        safeResultSuccess(success)
      }
      SmsAction.MUTE_CALL -> {
        val success = callController.muteCall()
        safeResultSuccess(success)
      }
      SmsAction.UNMUTE_CALL -> {
        val success = callController.unmuteCall()
        safeResultSuccess(success)
      }
      SmsAction.GET_CALL_AUDIO_STATE -> {
        val audioState = callController.getCallAudioState()
        safeResultSuccess(audioState)
      }
      SmsAction.SET_CALL_AUDIO_ROUTE -> {
        val success = callController.setCallAudioRoute(callRoute)
        safeResultSuccess(success)
      }
      SmsAction.PLAY_DTMF_TONE -> {
        val success = callController.playDtmfTone(callTone)
        safeResultSuccess(success)
      }
      SmsAction.STOP_DTMF_TONE -> {
        val success = callController.stopDtmfTone()
        safeResultSuccess(success)
      }
      SmsAction.IS_SERVICE_AVAILABLE -> {
        val isAvailable = callController.isServiceAvailable()
        safeResultSuccess(isAvailable)
      }
      else -> throw IllegalArgumentException()
    }
  }

  private fun handleCallNotificationActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.SHOW_INCOMING_CALL_NOTIFICATION -> {
        val success = callController.showIncomingCallNotification(callPhoneNumber, callCallerName)
        safeResultSuccess(success)
      }
      SmsAction.HIDE_INCOMING_CALL_NOTIFICATION -> {
        val success = callController.hideIncomingCallNotification()
        safeResultSuccess(success)
      }
      SmsAction.SET_CALL_NOTIFICATION_CHANNEL -> {
        val success = callController.setCallNotificationChannel(callChannelId, callChannelName, callDescription)
        safeResultSuccess(success)
      }
      else -> throw IllegalArgumentException()
    }
  }

  private fun handleCallCapabilitiesActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.GET_CALL_CAPABILITIES -> {
        val capabilities = callController.getCallCapabilities()
        safeResultSuccess(capabilities)
      }
      SmsAction.CHECK_CALL_PERMISSION -> {
        val hasPermission = callController.checkCallPermission()
        safeResultSuccess(hasPermission)
      }
      SmsAction.REQUEST_CALL_PERMISSION -> {
        val success = callController.requestCallPermission()
        safeResultSuccess(success)
      }
      else -> throw IllegalArgumentException()
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private fun checkOrRequestPermission(permissions: List<String>, requestCode: Int): Boolean {
    permissionsController.apply {
      
      if (!::activity.isInitialized) {
        return hasRequiredPermissions(permissions)
      }
      
      if (!hasRequiredPermissions(permissions)) {
        requestPermissions(activity, permissions, requestCode)
        return false
      }
      return true
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {

    permissionsController.isRequestingPermission = false

    val deniedPermissions = mutableListOf<String>()
    if (requestCode != this.requestCode && !this::action.isInitialized) {
      return false
    }

    val allPermissionGranted = grantResults.foldIndexed(true) { i, acc, result ->
      if (result == PackageManager.PERMISSION_DENIED) {
        permissions.let { deniedPermissions.add(it[i]) }
      }
      return@foldIndexed acc && result == PackageManager.PERMISSION_GRANTED
    }

    return if (allPermissionGranted) {
      execute(action)
      true
    } else {
      onPermissionDenied(deniedPermissions)
      false
    }
  }

  private fun safeResultSuccess(value: Any?) {
    if (!isResultSubmitted) {
      result.success(value)
      isResultSubmitted = true
    }
  }

  private fun safeResultError(errorCode: String, errorMessage: String?, errorDetails: Any?) {
    if (!isResultSubmitted) {
      result.error(errorCode, errorMessage, errorDetails)
      isResultSubmitted = true
    }
  }

  private fun onPermissionDenied(deniedPermissions: List<String>) {
    safeResultError(PERMISSION_DENIED, PERMISSION_DENIED_MESSAGE, deniedPermissions)
  }

  private fun handleIntentActions(smsAction: SmsAction) {
    when (smsAction) {
      SmsAction.GET_INITIAL_INTENT -> {
        val intentData = getInitialIntentData()
        safeResultSuccess(intentData)
      }
      else -> throw IllegalArgumentException()
    }
  }

  private fun getInitialIntentData(): Map<String, Any>? {
    return try {
      if (::activity.isInitialized) {
        val intent = activity.intent
        if (intent != null && intent.hasExtra("call_action")) {
          // Extract call data from intent
          mapOf(
            "call_action" to (intent.getStringExtra("call_action") ?: ""),
            "phone_number" to (intent.getStringExtra("phone_number") ?: ""),
            "call_id" to (intent.getStringExtra("call_id") ?: ""),
            "timestamp" to intent.getLongExtra("timestamp", 0L),
            "route" to (intent.getStringExtra("route") ?: "")
          )
        } else {
          null
        }
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  fun setForegroundChannel(channel: MethodChannel) {
    foregroundChannel = channel
  }

  override fun onReceive(ctx: Context?, intent: Intent?) {
    if (intent != null) {
      when (intent.action) {
        Constants.ACTION_SMS_SENT -> {
          try {
            if (::foregroundChannel.isInitialized) {
              foregroundChannel.invokeMethod(SMS_SENT, null)
            }
          } catch (e: Exception) {
            Log.e("SmsMethodCallHandler", "Error invoking SMS_SENT", e)
          }
        }
        Constants.ACTION_SMS_DELIVERED -> {
          try {
            if (::foregroundChannel.isInitialized) {
              foregroundChannel.invokeMethod(SMS_DELIVERED, null)
            }
            ctx?.unregisterReceiver(this)
          } catch (e: Exception) {
            Log.e("SmsMethodCallHandler", "Error invoking SMS_DELIVERED", e)
            ctx?.unregisterReceiver(this)
          }
        }
      }
    }
  }
}
