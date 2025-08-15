import 'dart:async';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:platform/platform.dart';

part 'constants.dart';

part 'filter.dart';

typedef MessageHandler(SmsMessage message);
typedef SmsSendStatusListener(SendStatus status);
typedef CallStateChangeHandler(Map<String, dynamic> callInfo);
typedef MuteStateChangeHandler(bool isMuted);

@pragma('vm:entry-point')
void _flutterSmsSetupBackgroundChannel(
    {MethodChannel backgroundChannel =
        const MethodChannel(_BACKGROUND_CHANNEL)}) async {
  WidgetsFlutterBinding.ensureInitialized();

  backgroundChannel.setMethodCallHandler((call) async {
    if (call.method == HANDLE_BACKGROUND_MESSAGE) {
      final CallbackHandle handle =
          CallbackHandle.fromRawHandle(call.arguments['handle']);
      final Function handlerFunction =
          PluginUtilities.getCallbackFromHandle(handle)!;
      try {
        await handlerFunction(SmsMessage.fromMap(
            call.arguments['message'], INCOMING_SMS_COLUMNS));
      } catch (e) {
        print('Unable to handle incoming background message.');
        print(e);
      }
      return Future<void>.value();
    }
  });

  backgroundChannel.invokeMethod<void>(BACKGROUND_SERVICE_INITIALIZED);
}

@pragma('vm:entry-point')
void _flutterCallStateSetupBackgroundChannel(
    {MethodChannel backgroundChannel =
        const MethodChannel(_BACKGROUND_CALL_CHANNEL)}) async {
  WidgetsFlutterBinding.ensureInitialized();

  backgroundChannel.setMethodCallHandler((call) async {
    if (call.method == HANDLE_BACKGROUND_CALL_STATE) {
      final CallbackHandle handle =
          CallbackHandle.fromRawHandle(call.arguments['handle']);
      final Function handlerFunction =
          PluginUtilities.getCallbackFromHandle(handle)!;
      try {
        final callInfo = Map<String, dynamic>.from(call.arguments['callInfo']);
        await handlerFunction(callInfo);
      } catch (e) {
        print('Unable to handle background call state change.');
        print(e);
      }
      return Future<void>.value();
    }
  });

  backgroundChannel.invokeMethod<void>(BACKGROUND_CALL_SERVICE_INITIALIZED);
}

/*
@pragma('vm:entry-point')
void callMain() async {
  WidgetsFlutterBinding.ensureInitialized();

  // This function will be called from Android when onShowIncomingCallUi is triggered
  print('callMain entry point called from Android');

  // You can add your custom logic here to show the incoming call UI
  // For example, you could use a global navigator key or event bus to trigger UI changes

  // Example: Show a notification or trigger navigation
  // _showIncomingCallNotification();
}
*/
///
/// A Flutter plugin to use telephony features such as
/// - Send SMS Messages
/// - Query SMS Messages
/// - Listen for incoming SMS
/// - Retrieve various network parameters
///
///
/// This plugin tries to replicate some of the functionality provided by Android's Telephony class.
///
///
class Telephony {
  final MethodChannel _foregroundChannel;
  final Platform _platform;

  late MessageHandler _onNewMessage;
  late MessageHandler _onBackgroundMessages;
  late SmsSendStatusListener _statusListener;
  CallStateChangeHandler? _onCallStateChanged;
  CallStateChangeHandler? _onBackgroundCallStateChanged;
  MuteStateChangeHandler? _onMuteStateChanged;

  ///
  /// Gets a singleton instance of the [Telephony] class.
  ///
  static Telephony get instance => _instance;

  ///
  /// Gets a singleton instance of the [Telephony] class to be used in background execution context.
  ///
  static Telephony get backgroundInstance => _backgroundInstance;

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  Telephony.private(MethodChannel methodChannel, Platform platform)
      : _foregroundChannel = methodChannel,
        _platform = platform;

  Telephony._newInstance(MethodChannel methodChannel, LocalPlatform platform)
      : _foregroundChannel = methodChannel,
        _platform = platform {
    _foregroundChannel.setMethodCallHandler(handler);
  }

  static final Telephony _instance = Telephony._newInstance(
      const MethodChannel(_FOREGROUND_CHANNEL), const LocalPlatform());
  static final Telephony _backgroundInstance = Telephony._newInstance(
      const MethodChannel(_FOREGROUND_CHANNEL), const LocalPlatform());

  ///
  /// Listens to incoming SMS.
  ///
  /// ### Requires RECEIVE_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [onNewMessage] : Called on every new message received when app is in foreground.
  /// - [onBackgroundMessage] (optional) : Called on every new message received when app is in background.
  /// - [listenInBackground] (optional) : Defaults to true. Set to false to only listen to messages in foreground. [listenInBackground] is
  /// ignored if [onBackgroundMessage] is not set.
  ///
  ///
  void listenIncomingSms(
      {required MessageHandler onNewMessage,
      MessageHandler? onBackgroundMessage,
      bool listenInBackground = true}) {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    assert(
        listenInBackground
            ? onBackgroundMessage != null
            : onBackgroundMessage == null,
        listenInBackground
            ? "`onBackgroundMessage` cannot be null when `listenInBackground` is true. Set `listenInBackground` to false if you don't need background processing."
            : "You have set `listenInBackground` to false. `onBackgroundMessage` can only be set when `listenInBackground` is true");

    _onNewMessage = onNewMessage;

    if (listenInBackground && onBackgroundMessage != null) {
      _onBackgroundMessages = onBackgroundMessage;
      final CallbackHandle backgroundSetupHandle =
          PluginUtilities.getCallbackHandle(_flutterSmsSetupBackgroundChannel)!;
      final CallbackHandle? backgroundMessageHandle =
          PluginUtilities.getCallbackHandle(_onBackgroundMessages);

      if (backgroundMessageHandle == null) {
        throw ArgumentError(
          '''Failed to setup background message handler! `onBackgroundMessage`
          should be a TOP-LEVEL OR STATIC FUNCTION and should NOT be tied to a
          class or an anonymous function.''',
        );
      }

      _foregroundChannel.invokeMethod<bool>(
        'startBackgroundService',
        <String, dynamic>{
          'setupHandle': backgroundSetupHandle.toRawHandle(),
          'backgroundHandle': backgroundMessageHandle.toRawHandle()
        },
      );
    } else {
      _foregroundChannel.invokeMethod('disableBackgroundService');
    }
  }

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  Future<dynamic> handler(MethodCall call) async {
    switch (call.method) {
      case ON_MESSAGE:
        final message = call.arguments["message"];
        return _onNewMessage(SmsMessage.fromMap(message, INCOMING_SMS_COLUMNS));
      case SMS_SENT:
        return _statusListener(SendStatus.SENT);
      case SMS_DELIVERED:
        return _statusListener(SendStatus.DELIVERED);
      case ON_CALL_STATE_CHANGED:
        if (_onCallStateChanged != null) {
          final callInfo = Map<String, dynamic>.from(call.arguments);
          return _onCallStateChanged!(callInfo);
        }
        break;
      case ON_NEW_CALL_STATE:
        if (_onCallStateChanged != null) {
          final callInfo = Map<String, dynamic>.from(call.arguments);
          return _onCallStateChanged!(callInfo);
        }
        break;
      case ON_MUTE_STATE_CHANGED:
        if (_onMuteStateChanged != null) {
          final isMuted = call.arguments["isMuted"] as bool;
          return _onMuteStateChanged!(isMuted);
        }
        break;
    }
  }

  ///
  /// Query SMS Inbox.
  ///
  /// ### Requires READ_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [columns] (optional) : List of [SmsColumn] to be returned by this query. Defaults to [ SmsColumn.ID, SmsColumn.ADDRESS, SmsColumn.BODY, SmsColumn.DATE ]
  /// - [filter] (optional) : [SmsFilter] to filter the results of this query. Works like SQL WHERE clause.
  /// - [sortOrder] (optional): List of [OrderBy]. Orders the results of this query by the provided columns and order.
  ///
  /// Returns:
  ///
  /// [Future<List<SmsMessage>>]
  Future<List<SmsMessage>> getInboxSms(
      {List<SmsColumn> columns = DEFAULT_SMS_COLUMNS,
      SmsFilter? filter,
      List<OrderBy>? sortOrder}) async {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    final args = _getArguments(columns, filter, sortOrder);

    final messages =
        await _foregroundChannel.invokeMethod<List?>(GET_ALL_INBOX_SMS, args);

    return messages
            ?.map((message) => SmsMessage.fromMap(message, columns))
            .toList(growable: false) ??
        List.empty();
  }

  ///
  /// Query SMS Outbox / Sent messages.
  ///
  /// ### Requires READ_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [columns] (optional) : List of [SmsColumn] to be returned by this query. Defaults to [ SmsColumn.ID, SmsColumn.ADDRESS, SmsColumn.BODY, SmsColumn.DATE ]
  /// - [filter] (optional) : [SmsFilter] to filter the results of this query. Works like SQL WHERE clause.
  /// - [sortOrder] (optional): List of [OrderBy]. Orders the results of this query by the provided columns and order.
  ///
  /// Returns:
  ///
  /// [Future<List<SmsMessage>>]
  Future<List<SmsMessage>> getSentSms(
      {List<SmsColumn> columns = DEFAULT_SMS_COLUMNS,
      SmsFilter? filter,
      List<OrderBy>? sortOrder}) async {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    final args = _getArguments(columns, filter, sortOrder);

    final messages =
        await _foregroundChannel.invokeMethod<List?>(GET_ALL_SENT_SMS, args);

    return messages
            ?.map((message) => SmsMessage.fromMap(message, columns))
            .toList(growable: false) ??
        List.empty();
  }

  ///
  /// Query SMS Drafts.
  ///
  /// ### Requires READ_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [columns] (optional) : List of [SmsColumn] to be returned by this query. Defaults to [ SmsColumn.ID, SmsColumn.ADDRESS, SmsColumn.BODY, SmsColumn.DATE ]
  /// - [filter] (optional) : [SmsFilter] to filter the results of this query. Works like SQL WHERE clause.
  /// - [sortOrder] (optional): List of [OrderBy]. Orders the results of this query by the provided columns and order.
  ///
  /// Returns:
  ///
  /// [Future<List<SmsMessage>>]
  Future<List<SmsMessage>> getDraftSms(
      {List<SmsColumn> columns = DEFAULT_SMS_COLUMNS,
      SmsFilter? filter,
      List<OrderBy>? sortOrder}) async {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    final args = _getArguments(columns, filter, sortOrder);

    final messages =
        await _foregroundChannel.invokeMethod<List?>(GET_ALL_DRAFT_SMS, args);

    return messages
            ?.map((message) => SmsMessage.fromMap(message, columns))
            .toList(growable: false) ??
        List.empty();
  }

  ///
  /// Query SMS Inbox.
  ///
  /// ### Requires READ_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [filter] (optional) : [ConversationFilter] to filter the results of this query. Works like SQL WHERE clause.
  /// - [sortOrder] (optional): List of [OrderBy]. Orders the results of this query by the provided columns and order.
  ///
  /// Returns:
  ///
  /// [Future<List<SmsConversation>>]
  Future<List<SmsConversation>> getConversations(
      {ConversationFilter? filter, List<OrderBy>? sortOrder}) async {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    final args = _getArguments(DEFAULT_CONVERSATION_COLUMNS, filter, sortOrder);

    final conversations = await _foregroundChannel.invokeMethod<List?>(
        GET_ALL_CONVERSATIONS, args);

    return conversations
            ?.map((conversation) => SmsConversation.fromMap(conversation))
            .toList(growable: false) ??
        List.empty();
  }

  Map<String, dynamic> _getArguments(List<_TelephonyColumn> columns,
      Filter? filter, List<OrderBy>? sortOrder) {
    final Map<String, dynamic> args = {};

    args["projection"] = columns.map((c) => c._name).toList();

    if (filter != null) {
      args["selection"] = filter.selection;
      args["selection_args"] = filter.selectionArgs;
    }

    if (sortOrder != null && sortOrder.isNotEmpty) {
      args["sort_order"] = sortOrder.map((o) => o._value).join(",");
    }

    return args;
  }

  ///
  /// Send an SMS directly from your application. Uses Android's SmsManager to send SMS.
  ///
  /// ### Requires SEND_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [to] : Address to send the SMS to.
  /// - [message] : Message to be sent. If message body is longer than standard SMS length limits set appropriate
  /// value for [isMultipart]
  /// - [statusListener] (optional) : Listen to the status of the sent SMS. Values can be one of [SmsStatus]
  /// - [isMultipart] (optional) : If message body is longer than standard SMS limit of 160 characters, set this flag to
  /// send the SMS in multiple parts.
  Future<void> sendSms({
    required String to,
    required String message,
    SmsSendStatusListener? statusListener,
    bool isMultipart = false,
    int subscriptionId = -1,
  }) async {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    bool listenStatus = false;
    if (statusListener != null) {
      _statusListener = statusListener;
      listenStatus = true;
    }
    final Map<String, dynamic> args = {
      "address": to,
      "message_body": message,
      "listen_status": listenStatus,
      "sub_id": subscriptionId
    };
    final String method = isMultipart ? SEND_MULTIPART_SMS : SEND_SMS;
    await _foregroundChannel.invokeMethod(method, args);
  }

  ///
  /// Open Android's default SMS application with the provided message and address.
  ///
  /// ### Requires SEND_SMS permission.
  ///
  /// Parameters:
  ///
  /// - [to] : Address to send the SMS to.
  /// - [message] : Message to be sent.
  ///
  Future<void> sendSmsByDefaultApp({
    required String to,
    required String message,
  }) async {
    final Map<String, dynamic> args = {
      "address": to,
      "message_body": message,
    };
    await _foregroundChannel.invokeMethod(SEND_SMS_INTENT, args);
  }

  ///
  /// Checks if the device has necessary features to send and receive SMS.
  ///
  /// Uses TelephonyManager class on Android.
  ///
  Future<bool?> get isSmsCapable =>
      _foregroundChannel.invokeMethod<bool>(IS_SMS_CAPABLE);

  ///
  /// Returns a constant indicating the current data connection state (cellular).
  ///
  /// Returns:
  ///
  /// [Future<DataState>]
  Future<DataState> get cellularDataState async {
    final int? dataState =
        await _foregroundChannel.invokeMethod<int>(GET_CELLULAR_DATA_STATE);
    if (dataState == null || dataState == -1) {
      return DataState.UNKNOWN;
    } else {
      return DataState.values[dataState];
    }
  }

  ///
  /// Returns a constant that represents the current state of all phone calls.
  ///
  /// Returns:
  ///
  /// [Future<CallState>]
  Future<CallState> get callState async {
    final int? state =
        await _foregroundChannel.invokeMethod<int>(GET_CALL_STATE);
    if (state != null) {
      return CallState.values[state];
    } else {
      return CallState.UNKNOWN;
    }
  }

  ///
  /// Returns a constant that represents the current state of all phone calls.
  ///
  /// Returns:
  ///
  /// [Future<CallState>]
  Future<DataActivity> get dataActivity async {
    final int? activity =
        await _foregroundChannel.invokeMethod<int>(GET_DATA_ACTIVITY);
    if (activity != null) {
      return DataActivity.values[activity];
    } else {
      return DataActivity.UNKNOWN;
    }
  }

  ///
  /// Returns the numeric name (MCC+MNC) of current registered operator.
  ///
  /// Availability: Only when user is registered to a network.
  ///
  /// Result may be unreliable on CDMA networks (use phoneType to determine if on a CDMA network).
  ///
  Future<String?> get networkOperator =>
      _foregroundChannel.invokeMethod<String>(GET_NETWORK_OPERATOR);

  ///
  /// Returns the alphabetic name of current registered operator.
  ///
  /// Availability: Only when user is registered to a network.
  ///
  /// Result may be unreliable on CDMA networks (use phoneType to determine if on a CDMA network).
  ///
  Future<String?> get networkOperatorName =>
      _foregroundChannel.invokeMethod<String>(GET_NETWORK_OPERATOR_NAME);

  ///
  /// Returns a constant indicating the radio technology (network type) currently in use on the device for data transmission.
  ///
  /// ### Requires READ_PHONE_STATE permission.
  ///
  Future<NetworkType> get dataNetworkType async {
    final int? type =
        await _foregroundChannel.invokeMethod<int>(GET_DATA_NETWORK_TYPE);
    if (type != null) {
      return NetworkType.values[type];
    } else {
      return NetworkType.UNKNOWN;
    }
  }

  ///
  /// Returns a constant indicating the device phone type. This indicates the type of radio used to transmit voice calls.
  ///
  Future<PhoneType> get phoneType async {
    final int? type =
        await _foregroundChannel.invokeMethod<int>(GET_PHONE_TYPE);
    if (type != null) {
      return PhoneType.values[type];
    } else {
      return PhoneType.UNKNOWN;
    }
  }

  ///
  /// Returns the MCC+MNC (mobile country code + mobile network code) of the provider of the SIM. 5 or 6 decimal digits.
  ///
  /// Availability: SimState must be SIM\_STATE\_READY
  Future<String?> get simOperator =>
      _foregroundChannel.invokeMethod<String>(GET_SIM_OPERATOR);

  ///
  /// Returns the Service Provider Name (SPN).
  ///
  /// Availability: SimState must be SIM_STATE_READY
  Future<String?> get simOperatorName =>
      _foregroundChannel.invokeMethod<String>(GET_SIM_OPERATOR_NAME);

  ///
  /// Returns a constant indicating the state of the default SIM card.
  ///
  /// Returns:
  ///
  /// [Future<SimState>]
  Future<SimState> get simState async {
    final int? state =
        await _foregroundChannel.invokeMethod<int>(GET_SIM_STATE);
    if (state != null) {
      return SimState.values[state];
    } else {
      return SimState.UNKNOWN;
    }
  }

  ///
  /// Returns true if the device is considered roaming on the current network, for GSM purposes.
  ///
  /// Availability: Only when user registered to a network.
  Future<bool?> get isNetworkRoaming =>
      _foregroundChannel.invokeMethod<bool>(IS_NETWORK_ROAMING);

  ///
  /// Returns a List of SignalStrength or an empty List if there are no valid measurements.
  ///
  /// ### Requires Android build version 29 --> Android Q
  ///
  /// Returns:
  ///
  /// [Future<List<SignalStrength>>]
  Future<List<SignalStrength>> get signalStrengths async {
    final List<dynamic>? strengths =
        await _foregroundChannel.invokeMethod(GET_SIGNAL_STRENGTH);
    return (strengths ?? [])
        .map((s) => SignalStrength.values[s])
        .toList(growable: false);
  }

  ///
  /// Returns current voice service state.
  ///
  /// ### Requires Android build version 26 --> Android O
  /// ### Requires permissions ACCESS_COARSE_LOCATION and READ_PHONE_STATE
  ///
  /// Returns:
  ///
  /// [Future<ServiceState>]
  Future<ServiceState> get serviceState async {
    final int? state =
        await _foregroundChannel.invokeMethod<int>(GET_SERVICE_STATE);
    if (state != null) {
      return ServiceState.values[state];
    } else {
      return ServiceState.UNKNOWN;
    }
  }

  ///
  /// Request the user for all the sms permissions listed in the app's AndroidManifest.xml
  ///
  Future<bool?> get requestSmsPermissions =>
      _foregroundChannel.invokeMethod<bool>(REQUEST_SMS_PERMISSION);

  ///
  /// Request the user for all the phone permissions listed in the app's AndroidManifest.xml
  ///
  Future<bool?> get requestPhonePermissions =>
      _foregroundChannel.invokeMethod<bool>(REQUEST_PHONE_PERMISSION);

  ///
  /// Request the user for all the phone and sms permissions listed in the app's AndroidManifest.xml
  ///
  Future<bool?> get requestPhoneAndSmsPermissions =>
      _foregroundChannel.invokeMethod<bool>(REQUEST_PHONE_AND_SMS_PERMISSION);

  ///
  /// Opens the default dialer with the given phone number.
  ///
  Future<void> openDialer(String phoneNumber) async {
    assert(phoneNumber.isNotEmpty, "phoneNumber cannot be empty");
    final Map<String, dynamic> args = {"phoneNumber": phoneNumber};
    await _foregroundChannel.invokeMethod(OPEN_DIALER, args);
  }

  ///
  /// Starts a phone all with the given phone number.
  ///
  /// ### Requires permission CALL_PHONE
  ///
  Future<void> dialPhoneNumber(String phoneNumber) async {
    assert(phoneNumber.isNotEmpty, "phoneNumber cannot be null or empty");
    final Map<String, dynamic> args = {"phoneNumber": phoneNumber};
    await _foregroundChannel.invokeMethod(DIAL_PHONE_NUMBER, args);
  }

  ///
  /// Request to set this app as the default dialer app.
  ///
  /// ### Requires Android API 29+ (Android Q)
  ///
  /// Returns true if the request was successfully initiated, false otherwise.
  ///
  Future<bool?> get  requestDefaultDialer =>
      _foregroundChannel.invokeMethod<bool>(REQUEST_DEFAULT_DIALER);

  ///
  /// Check if this app is currently the default dialer app.
  ///
  /// ### Requires Android API 29+ (Android Q)
  ///
  /// Returns true if this app is the default dialer, false otherwise.
  ///
  Future<bool?> get isDefaultDialer =>
      _foregroundChannel.invokeMethod<bool>(IS_DEFAULT_DIALER);

  ///
  /// Open the call settings where users can manage phone accounts and calling preferences.
  ///
  Future<void> openCallSettings() async {
    await _foregroundChannel.invokeMethod(OPEN_CALL_SETTINGS);
  }

  ///
  /// Register a new phone account with the system.
  ///
  /// Parameters:
  ///
  /// - [accountId] : Unique identifier for the phone account
  /// - [label] : Human-readable label for the account
  /// - [capabilities] (optional) : Account capabilities (defaults to CAPABILITY_CALL_PROVIDER)
  ///
  /// Returns true if the account was successfully registered, false otherwise.
  ///
  Future<bool?> registerPhoneAccount(String accountId, String label,
      {int capabilities = 0}) async {
    assert(accountId.isNotEmpty, "accountId cannot be empty");
    assert(label.isNotEmpty, "label cannot be empty");
    final Map<String, dynamic> args = {
      "accountId": accountId,
      "label": label,
      "capabilities": capabilities,
    };
    return _foregroundChannel.invokeMethod<bool>(REGISTER_PHONE_ACCOUNT, args);
  }

  ///
  /// Unregister a phone account from the system.
  ///
  /// Parameters:
  ///
  /// - [accountId] : Unique identifier of the phone account to unregister
  ///
  /// Returns true if the account was successfully unregistered, false otherwise.
  ///
  Future<bool?> unregisterPhoneAccount(String accountId) async {
    assert(accountId.isNotEmpty, "accountId cannot be empty");
    final Map<String, dynamic> args = {"accountId": accountId};
    return _foregroundChannel.invokeMethod<bool>(
        UNREGISTER_PHONE_ACCOUNT, args);
  }

  ///
  /// Get a list of all registered phone accounts for this app.
  ///
  /// Returns a list of maps containing account information (id, label, enabled).
  ///
  Future<dynamic> get getPhoneAccounts async {
    final dynamic accounts = await _foregroundChannel
        .invokeMethod<List<dynamic>>(GET_PHONE_ACCOUNTS);
    return accounts;
  }

  ///
  /// Check if a specific phone account is enabled.
  ///
  /// Parameters:
  ///
  /// - [accountId] : Unique identifier of the phone account to check
  ///
  /// Returns true if the account is enabled, false otherwise.
  ///
  Future<bool?> isPhoneAccountEnabled(String accountId) async {
    assert(accountId.isNotEmpty, "accountId cannot be empty");
    final Map<String, dynamic> args = {"accountId": accountId};
    return _foregroundChannel.invokeMethod<bool>(
        IS_PHONE_ACCOUNT_ENABLED, args);
  }

  ///
  /// Make a phone call using the registered phone account.
  ///
  /// Parameters:
  ///
  /// - [phoneNumber] : The phone number to call
  /// - [accountId] (optional) : The phone account to use for the call
  ///
  /// Returns true if the call was initiated successfully, false otherwise.
  ///
  Future<bool?> makeCall(String phoneNumber, {String? accountId}) async {
    assert(phoneNumber.isNotEmpty, "phoneNumber cannot be empty");
    final Map<String, dynamic> args = {
      "phoneNumber": phoneNumber,
      if (accountId != null) "accountId": accountId,
    };
    return _foregroundChannel.invokeMethod<bool>(MAKE_CALL, args);
  }

  ///
  /// End the current active call.
  ///
  /// Returns true if the call was ended successfully, false otherwise.
  ///
  Future<bool?> endCall() async {
    return _foregroundChannel.invokeMethod<bool>(END_CALL);
  }

  ///
  /// Answer an incoming call.
  ///
  /// Returns true if the call was answered successfully, false otherwise.
  ///
  Future<bool?> answerCall() async {
    return _foregroundChannel.invokeMethod<bool>(ANSWER_CALL);
  }

  ///
  /// Reject an incoming call.
  ///
  /// Returns true if the call was rejected successfully, false otherwise.
  ///
  Future<bool?> rejectCall() async {
    return _foregroundChannel.invokeMethod<bool>(REJECT_CALL);
  }

  ///
  /// Put the current call on hold.
  ///
  /// Returns true if the call was put on hold successfully, false otherwise.
  ///
  Future<bool?> holdCall() async {
    return _foregroundChannel.invokeMethod<bool>(HOLD_CALL);
  }

  ///
  /// Take the current call off hold.
  ///
  /// Returns true if the call was taken off hold successfully, false otherwise.
  ///
  Future<bool?> unholdCall() async {
    return _foregroundChannel.invokeMethod<bool>(UNHOLD_CALL);
  }

  ///
  /// Mute the current call.
  ///
  /// Returns true if the call was muted successfully, false otherwise.
  ///
  Future<bool?> muteCall() async {
    return _foregroundChannel.invokeMethod<bool>(MUTE_CALL);
  }

  ///
  /// Unmute the current call.
  ///
  /// Returns true if the call was unmuted successfully, false otherwise.
  ///
  Future<bool?> unmuteCall() async {
    return _foregroundChannel.invokeMethod<bool>(UNMUTE_CALL);
  }

  ///
  /// Get the current call audio state.
  ///
  /// Returns a map containing audio state information.
  ///
  Future<dynamic> getCallAudioState() async {
    return _foregroundChannel.invokeMethod(GET_CALL_AUDIO_STATE);
  }

  ///
  /// Set the call audio route (speaker, earpiece, bluetooth, etc.).
  ///
  /// Parameters:
  ///
  /// - [route] : The audio route to set (0=earpiece, 1=speaker, 2=bluetooth, etc.)
  ///
  /// Returns true if the audio route was set successfully, false otherwise.
  ///
  Future<bool?> setCallAudioRoute(int route) async {
    final Map<String, dynamic> args = {"route": route};
    return _foregroundChannel.invokeMethod<bool>(SET_CALL_AUDIO_ROUTE, args);
  }

  ///
  /// Play a DTMF tone during a call.
  ///
  /// Parameters:
  ///
  /// - [tone] : The DTMF tone to play (0-9, *, #, A-D)
  ///
  /// Returns true if the tone was played successfully, false otherwise.
  ///
  Future<bool?> playDtmfTone(String tone) async {
    assert(tone.isNotEmpty, "tone cannot be empty");
    final Map<String, dynamic> args = {"tone": tone};
    return _foregroundChannel.invokeMethod<bool>(PLAY_DTMF_TONE, args);
  }

  ///
  /// Stop playing DTMF tones.
  ///
  /// Returns true if the tone was stopped successfully, false otherwise.
  ///
  Future<bool?> stopDtmfTone() async {
    return _foregroundChannel.invokeMethod<bool>(STOP_DTMF_TONE);
  }

  ///
  /// Show an incoming call notification.
  ///
  /// Parameters:
  ///
  /// - [phoneNumber] : The phone number of the incoming call
  /// - [callerName] (optional) : The name of the caller
  ///
  /// Returns true if the notification was shown successfully, false otherwise.
  ///
  Future<bool?> showIncomingCallNotification(String phoneNumber,
      {String? callerName}) async {
    assert(phoneNumber.isNotEmpty, "phoneNumber cannot be empty");
    final Map<String, dynamic> args = {
      "phoneNumber": phoneNumber,
      if (callerName != null) "callerName": callerName,
    };
    return _foregroundChannel.invokeMethod<bool>(
        SHOW_INCOMING_CALL_NOTIFICATION, args);
  }

  ///
  /// Hide the incoming call notification.
  ///
  /// Returns true if the notification was hidden successfully, false otherwise.
  ///
  Future<bool?> hideIncomingCallNotification() async {
    return _foregroundChannel
        .invokeMethod<bool>(HIDE_INCOMING_CALL_NOTIFICATION);
  }

  ///
  /// Set up the call notification channel.
  ///
  /// Parameters:
  ///
  /// - [channelId] : The notification channel ID
  /// - [channelName] : The notification channel name
  /// - [description] (optional) : The notification channel description
  ///
  /// Returns true if the channel was set up successfully, false otherwise.
  ///
  Future<bool?> setCallNotificationChannel(String channelId, String channelName,
      {String? description}) async {
    assert(channelId.isNotEmpty, "channelId cannot be empty");
    assert(channelName.isNotEmpty, "channelName cannot be empty");
    final Map<String, dynamic> args = {
      "channelId": channelId,
      "channelName": channelName,
      if (description != null) "description": description,
    };
    return _foregroundChannel.invokeMethod<bool>(
        SET_CALL_NOTIFICATION_CHANNEL, args);
  }

  ///
  /// Get the call capabilities for the current device.
  ///
  /// Returns a map containing call capability information.
  ///
  Future<dynamic> getCallCapabilities() async {
    return _foregroundChannel.invokeMethod(GET_CALL_CAPABILITIES);
  }

  ///
  /// Check if the app has call permissions.
  ///
  /// Returns true if the app has call permissions, false otherwise.
  ///
  Future<bool?> checkCallPermission() async {
    return _foregroundChannel.invokeMethod<bool>(CHECK_CALL_PERMISSION);
  }

  ///
  /// Request call permissions from the user.
  ///
  /// Returns true if permissions were granted, false otherwise.
  ///
  Future<bool?> requestCallPermission() async {
    return _foregroundChannel.invokeMethod<bool>(REQUEST_CALL_PERMISSION);
  }

  ///
  /// Check if the telephony connection service is available.
  ///
  /// Returns true if the service is available, false otherwise.
  ///
  Future<bool?> isServiceAvailable() async {
    return _foregroundChannel.invokeMethod<bool>(IS_SERVICE_AVAILABLE);
  }

  ///
  /// Listen to call state changes.
  ///
  /// Parameters:
  ///
  /// - [onNewCallState] : Called on every call state change when app is in foreground.
  /// - [onBackgroundCallState] (optional) : Called on every call state change when app is in background.
  /// - [listenInBackground] (optional) : Defaults to true. Set to false to only listen to call state changes in foreground. [listenInBackground] is
  /// ignored if [onBackgroundCallState] is not set.
  ///
  void listenCallStateChanges({
    required CallStateChangeHandler onNewCallState,
    CallStateChangeHandler? onBackgroundCallState,
    bool listenInBackground = true,
  }) {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    assert(
        listenInBackground
            ? onBackgroundCallState != null
            : onBackgroundCallState == null,
        listenInBackground
            ? "`onBackgroundCallState` cannot be null when `listenInBackground` is true. Set `listenInBackground` to false if you don't need background processing."
            : "You have set `listenInBackground` to false. `onBackgroundCallState` can only be set when `listenInBackground` is true");

    _onCallStateChanged = onNewCallState;

    if (listenInBackground && onBackgroundCallState != null) {
      _onBackgroundCallStateChanged = onBackgroundCallState;
      final CallbackHandle backgroundSetupHandle =
          PluginUtilities.getCallbackHandle(_flutterCallStateSetupBackgroundChannel)!;
      final CallbackHandle backgroundMessageHandle =
          PluginUtilities.getCallbackHandle(_onBackgroundCallStateChanged!)!;

      _foregroundChannel.invokeMethod("setupCallStateBackground", <String, dynamic>{
        "setupHandle": backgroundSetupHandle.toRawHandle(),
        "backgroundHandle": backgroundMessageHandle.toRawHandle(),
        "listenInBackground": listenInBackground
      });
    }
  }

  ///
  /// Listen to mute state changes.
  ///
  /// Parameters:
  ///
  /// - [onMuteStateChanged] : Called when call mute state changes
  ///
  void listenMuteStateChanges({
    required MuteStateChangeHandler onMuteStateChanged,
  }) {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    _onMuteStateChanged = onMuteStateChanged;
  }

  ///
  /// Stop listening to call state changes.
  ///
  void stopListeningCallStateChanges() {
    _onCallStateChanged = null;
  }

  ///
  /// Stop listening to mute state changes.
  ///
  void stopListeningMuteStateChanges() {
    _onMuteStateChanged = null;
  }
}

///
/// Represents a message returned by one of the query functions such as
/// [getInboxSms], [getSentSms], [getDraftSms]
class SmsMessage {
  int? id;
  String? address;
  String? body;
  int? date;
  int? dateSent;
  bool? read;
  bool? seen;
  String? subject;
  int? subscriptionId;
  int? threadId;
  SmsType? type;
  SmsStatus? status;
  String? serviceCenterAddress;

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  SmsMessage.fromMap(Map rawMessage, List<SmsColumn> columns) {
    final message = Map.castFrom<dynamic, dynamic, String, dynamic>(rawMessage);
    for (var column in columns) {
      // debugPrint('Column is ${column._columnName}');
      final value = message[column._columnName];
      switch (column._columnName) {
        case _SmsProjections.ID:
          this.id = int.tryParse(value);
          break;
        case _SmsProjections.ORIGINATING_ADDRESS:
        case _SmsProjections.ADDRESS:
          this.address = value;
          break;
        case _SmsProjections.MESSAGE_BODY:
        case _SmsProjections.BODY:
          this.body = value;
          break;
        case _SmsProjections.DATE:
        case _SmsProjections.TIMESTAMP:
          this.date = int.tryParse(value);
          break;
        case _SmsProjections.DATE_SENT:
          this.dateSent = int.tryParse(value);
          break;
        case _SmsProjections.READ:
          this.read = int.tryParse(value) == 0 ? false : true;
          break;
        case _SmsProjections.SEEN:
          this.seen = int.tryParse(value) == 0 ? false : true;
          break;
        case _SmsProjections.STATUS:
          switch (int.tryParse(value)) {
            case 0:
              this.status = SmsStatus.STATUS_COMPLETE;
              break;
            case 32:
              this.status = SmsStatus.STATUS_PENDING;
              break;
            case 64:
              this.status = SmsStatus.STATUS_FAILED;
              break;
            case -1:
            default:
              this.status = SmsStatus.STATUS_NONE;
              break;
          }
          break;
        case _SmsProjections.SUBJECT:
          this.subject = value;
          break;
        case _SmsProjections.SUBSCRIPTION_ID:
          this.subscriptionId = int.tryParse(value);
          break;
        case _SmsProjections.THREAD_ID:
          this.threadId = int.tryParse(value);
          break;
        case _SmsProjections.TYPE:
          var smsTypeIndex = int.tryParse(value);
          this.type =
              smsTypeIndex != null ? SmsType.values[smsTypeIndex] : null;
          break;
        case _SmsProjections.SERVICE_CENTER_ADDRESS:
          this.serviceCenterAddress = value;
          break;
      }
    }
  }

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  bool equals(SmsMessage other) {
    return this.id == other.id &&
        this.address == other.address &&
        this.body == other.body &&
        this.date == other.date &&
        this.dateSent == other.dateSent &&
        this.read == other.read &&
        this.seen == other.seen &&
        this.subject == other.subject &&
        this.subscriptionId == other.subscriptionId &&
        this.threadId == other.threadId &&
        this.type == other.type &&
        this.status == other.status;
  }
}

///
/// Represents a conversation returned by the query conversation functions
/// [getConversations]
class SmsConversation {
  String? snippet;
  int? threadId;
  int? messageCount;

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  SmsConversation.fromMap(Map rawConversation) {
    final conversation =
        Map.castFrom<dynamic, dynamic, String, dynamic>(rawConversation);
    for (var column in DEFAULT_CONVERSATION_COLUMNS) {
      final String? value = conversation[column._columnName];
      switch (column._columnName) {
        case _ConversationProjections.SNIPPET:
          this.snippet = value;
          break;
        case _ConversationProjections.THREAD_ID:
          this.threadId = int.tryParse(value!);
          break;
        case _ConversationProjections.MSG_COUNT:
          this.messageCount = int.tryParse(value!);
          break;
      }
    }
  }

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  bool equals(SmsConversation other) {
    return this.threadId == other.threadId &&
        this.snippet == other.snippet &&
        this.messageCount == other.messageCount;
  }
}
