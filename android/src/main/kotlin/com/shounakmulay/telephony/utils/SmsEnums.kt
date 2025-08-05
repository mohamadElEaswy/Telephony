package com.shounakmulay.telephony.utils

import android.net.Uri
import android.provider.Telephony

enum class SmsAction(private val methodName: String) {
  GET_INBOX("getAllInboxSms"),
  GET_SENT("getAllSentSms"),
  GET_DRAFT("getAllDraftSms"),
  GET_CONVERSATIONS("getAllConversations"),
  SEND_SMS("sendSms"),
  SEND_MULTIPART_SMS("sendMultipartSms"),
  SEND_SMS_INTENT("sendSmsIntent"),
  START_BACKGROUND_SERVICE("startBackgroundService"),
  DISABLE_BACKGROUND_SERVICE("disableBackgroundService"),
  BACKGROUND_SERVICE_INITIALIZED("backgroundServiceInitialized"),
  IS_SMS_CAPABLE("isSmsCapable"),
  GET_CELLULAR_DATA_STATE("getCellularDataState"),
  GET_CALL_STATE("getCallState"),
  GET_DATA_ACTIVITY("getDataActivity"),
  GET_NETWORK_OPERATOR("getNetworkOperator"),
  GET_NETWORK_OPERATOR_NAME("getNetworkOperatorName"),
  GET_DATA_NETWORK_TYPE("getDataNetworkType"),
  GET_PHONE_TYPE("getPhoneType"),
  GET_SIM_OPERATOR("getSimOperator"),
  GET_SIM_OPERATOR_NAME("getSimOperatorName"),
  GET_SIM_STATE("getSimState"),
  GET_SERVICE_STATE("getServiceState"),
  GET_SIGNAL_STRENGTH("getSignalStrength"),
  IS_NETWORK_ROAMING("isNetworkRoaming"),
  REQUEST_SMS_PERMISSIONS("requestSmsPermissions"),
  REQUEST_PHONE_PERMISSIONS("requestPhonePermissions"),
  REQUEST_PHONE_AND_SMS_PERMISSIONS("requestPhoneAndSmsPermissions"),
  OPEN_DIALER("openDialer"),
  DIAL_PHONE_NUMBER("dialPhoneNumber"),
  REQUEST_DEFAULT_DIALER("requestDefaultDialer"),
  IS_DEFAULT_DIALER("isDefaultDialer"),
  OPEN_CALL_SETTINGS("openCallSettings"),
  REGISTER_PHONE_ACCOUNT("registerPhoneAccount"),
  UNREGISTER_PHONE_ACCOUNT("unregisterPhoneAccount"),
  GET_PHONE_ACCOUNTS("getPhoneAccounts"),
  IS_PHONE_ACCOUNT_ENABLED("isPhoneAccountEnabled"),
  
  // Call management actions
  MAKE_CALL("makeCall"),
  END_CALL("endCall"),
  ANSWER_CALL("answerCall"),
  REJECT_CALL("rejectCall"),
  HOLD_CALL("holdCall"),
  UNHOLD_CALL("unholdCall"),
  MUTE_CALL("muteCall"),
  UNMUTE_CALL("unmuteCall"),
  GET_CALL_AUDIO_STATE("getCallAudioState"),
  SET_CALL_AUDIO_ROUTE("setCallAudioRoute"),
  PLAY_DTMF_TONE("playDtmfTone"),
  STOP_DTMF_TONE("stopDtmfTone"),
  
  // Call notification actions
  SHOW_INCOMING_CALL_NOTIFICATION("showIncomingCallNotification"),
  HIDE_INCOMING_CALL_NOTIFICATION("hideIncomingCallNotification"),
  SET_CALL_NOTIFICATION_CHANNEL("setCallNotificationChannel"),
  
  // Call capabilities actions
  GET_CALL_CAPABILITIES("getCallCapabilities"),
  CHECK_CALL_PERMISSION("checkCallPermission"),
  REQUEST_CALL_PERMISSION("requestCallPermission"),
  
  // Intent handling
  GET_INITIAL_INTENT("getInitialIntent"),
  
  NO_SUCH_METHOD("noSuchMethod");

  companion object {
    fun fromMethod(method: String): SmsAction {
      for (action in values()) {
        if (action.methodName == method) {
          return action
        }
      }
      return NO_SUCH_METHOD
    }
  }

  fun toActionType(): ActionType {
    return when (this) {
      GET_INBOX,
      GET_SENT,
      GET_DRAFT,
      GET_CONVERSATIONS -> ActionType.GET_SMS
      SEND_SMS,
      SEND_MULTIPART_SMS,
      SEND_SMS_INTENT,
      NO_SUCH_METHOD -> ActionType.SEND_SMS
      START_BACKGROUND_SERVICE,
      DISABLE_BACKGROUND_SERVICE,
      BACKGROUND_SERVICE_INITIALIZED -> ActionType.BACKGROUND
      IS_SMS_CAPABLE,
      GET_CELLULAR_DATA_STATE,
      GET_CALL_STATE,
      GET_DATA_ACTIVITY,
      GET_NETWORK_OPERATOR,
      GET_NETWORK_OPERATOR_NAME,
      GET_DATA_NETWORK_TYPE,
      GET_PHONE_TYPE,
      GET_SIM_OPERATOR,
      GET_SIM_OPERATOR_NAME,
      GET_SIM_STATE,
      GET_SERVICE_STATE,
      GET_SIGNAL_STRENGTH,
      IS_NETWORK_ROAMING -> ActionType.GET
      REQUEST_SMS_PERMISSIONS,
      REQUEST_PHONE_PERMISSIONS,
      REQUEST_PHONE_AND_SMS_PERMISSIONS -> ActionType.PERMISSION
      OPEN_DIALER,
      DIAL_PHONE_NUMBER -> ActionType.CALL
      REQUEST_DEFAULT_DIALER,
      IS_DEFAULT_DIALER,
      OPEN_CALL_SETTINGS -> ActionType.DIALER
      REGISTER_PHONE_ACCOUNT,
      UNREGISTER_PHONE_ACCOUNT,
      GET_PHONE_ACCOUNTS,
      IS_PHONE_ACCOUNT_ENABLED -> ActionType.PHONE_ACCOUNT
      MAKE_CALL,
      END_CALL,
      ANSWER_CALL,
      REJECT_CALL,
      HOLD_CALL,
      UNHOLD_CALL,
      MUTE_CALL,
      UNMUTE_CALL,
      GET_CALL_AUDIO_STATE,
      SET_CALL_AUDIO_ROUTE,
      PLAY_DTMF_TONE,
      STOP_DTMF_TONE -> ActionType.CALL_MANAGEMENT
      SHOW_INCOMING_CALL_NOTIFICATION,
      HIDE_INCOMING_CALL_NOTIFICATION,
      SET_CALL_NOTIFICATION_CHANNEL -> ActionType.CALL_NOTIFICATION
      GET_CALL_CAPABILITIES,
      CHECK_CALL_PERMISSION,
      REQUEST_CALL_PERMISSION -> ActionType.CALL_CAPABILITIES
      GET_INITIAL_INTENT -> ActionType.INTENT_HANDLING
    }
  }
}

enum class ActionType {
  GET_SMS, SEND_SMS, BACKGROUND, GET, PERMISSION, CALL, DIALER, PHONE_ACCOUNT, 
  CALL_MANAGEMENT, CALL_NOTIFICATION, CALL_CAPABILITIES, INTENT_HANDLING
}

enum class ContentUri(val uri: Uri) {
  INBOX(Telephony.Sms.Inbox.CONTENT_URI),
  SENT(Telephony.Sms.Sent.CONTENT_URI),
  DRAFT(Telephony.Sms.Draft.CONTENT_URI),
  CONVERSATIONS(Telephony.Sms.Conversations.CONTENT_URI);
}