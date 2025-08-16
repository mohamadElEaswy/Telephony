# SMS Streams Product Requirements Document
## Flutter Telephony Plugin

### Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [SMS Stream Components](#sms-stream-components)
4. [Data Flow](#data-flow)
5. [Implementation Details](#implementation-details)
6. [Communication Protocol](#communication-protocol)
7. [Background Processing](#background-processing)
8. [Error Handling](#error-handling)
9. [Performance Considerations](#performance-considerations)
10. [Security & Permissions](#security--permissions)

---

## Overview

The Flutter Telephony Plugin provides a comprehensive SMS streaming system that enables real-time SMS message handling between Android's native telephony system and Flutter applications. The system supports both foreground and background SMS processing, ensuring messages are captured and processed regardless of the application's state.

### Key Features
- **Real-time SMS Reception**: Immediate processing of incoming SMS messages
- **Foreground/Background Processing**: Handles SMS when app is active or in background
- **Bidirectional Communication**: Kotlin ↔ Flutter data exchange via Method Channels
- **Message Queuing**: Ensures no SMS messages are lost during background initialization
- **Status Tracking**: Monitors SMS send/delivery status

---

## Architecture

### High-Level Architecture
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Android SMS   │───▶│   Kotlin Layer   │───▶│  Flutter Layer  │
│     System      │    │  (Native Side)   │    │   (Dart Side)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
        │                        │                        │
        │              ┌─────────▼─────────┐              │
        │              │ Method Channels   │              │
        │              │ Communication     │              │
        │              └───────────────────┘              │
        │                        │                        │
        └────────────────────────┼────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Background Isolate    │
                    │   (Background SMS)      │
                    └─────────────────────────┘
```

### Component Layers
1. **Android SMS System**: Native Android telephony framework
2. **Kotlin Native Layer**: Bridge between Android and Flutter
3. **Method Channels**: Communication protocol
4. **Flutter Dart Layer**: Application-level SMS handling
5. **Background Isolate**: Independent Dart execution context for background processing

---

## SMS Stream Components

### Kotlin Side Components

#### 1. IncomingSmsReceiver
**File**: `IncomingSmsHandler.kt`
**Purpose**: BroadcastReceiver that captures incoming SMS messages from Android system

**Key Responsibilities**:
- Receives SMS_RECEIVED broadcasts from Android system
- Groups multipart SMS messages by originating address
- Determines if app is in foreground or background
- Routes messages to appropriate processing pipeline

**Core Methods**:
```kotlin
override fun onReceive(context: Context, intent: Intent?)
private fun processIncomingSms(context: Context, smsList: List<SmsMessage>)
private fun processInBackground(context: Context, sms: HashMap<String, Any?>)
```

#### 2. IncomingSmsHandler
**File**: `IncomingSmsHandler.kt`
**Purpose**: Manages background SMS processing and Flutter isolate communication

**Key Responsibilities**:
- Initializes background Flutter isolate
- Manages message queue during isolate startup
- Executes Dart callbacks in background context
- Handles foreground/background state detection

**Core Methods**:
```kotlin
fun startBackgroundIsolate(context: Context, callbackHandle: Long)
fun executeDartCallbackInBackgroundIsolate(context: Context, message: HashMap<String, Any?>)
fun onChannelInitialized(applicationContext: Context)
fun isApplicationForeground(context: Context): Boolean
```

#### 3. SmsController
**File**: `SmsController.kt`
**Purpose**: Handles SMS operations including sending, querying, and status management

**Key Responsibilities**:
- Send SMS messages (single and multipart)
- Query SMS databases (inbox, sent, draft)
- Manage SMS status callbacks
- Handle phone operations

**Core Methods**:
```kotlin
fun sendSms(destinationAddress: String, messageBody: String, listenStatus: Boolean, subId: Int)
fun sendMultipartSms(destinationAddress: String, messageBody: String, listenStatus: Boolean, subId: Int)
fun getMessages(contentUri: ContentUri, projection: List<String>, ...): List<HashMap<String, String?>>
```

#### 4. SmsMethodCallHandler
**File**: `SmsMethodCallHandler.kt`
**Purpose**: Processes method calls from Flutter and coordinates SMS operations

**Key Responsibilities**:
- Handle Flutter method calls
- Coordinate permission requests
- Manage SMS send/delivery status broadcasts
- Route operations to appropriate controllers

#### 5. TelephonyPlugin
**File**: `TelephonyPlugin.kt`
**Purpose**: Main plugin entry point and Flutter engine integration

**Key Responsibilities**:
- Initialize plugin components
- Set up method channels
- Manage plugin lifecycle
- Coordinate between different controllers

### Flutter Side Components

#### 1. Telephony Class
**File**: `lib/telephony.dart`
**Purpose**: Main API interface for Flutter applications

**Key Responsibilities**:
- Provide public API for SMS operations
- Manage method channel communication
- Handle foreground message callbacks
- Coordinate background message setup

**Core Methods**:
```dart
void listenIncomingSms({required MessageHandler onNewMessage, MessageHandler? onBackgroundMessage, bool listenInBackground = true})
Future<void> sendSms({required String to, required String message, SmsSendStatusListener? statusListener, bool isMultipart = false, int subscriptionId = -1})
Future<List<SmsMessage>> getInboxSms({List<SmsColumn> columns = DEFAULT_SMS_COLUMNS, SmsFilter? filter, List<OrderBy>? sortOrder})
```

#### 2. Background Message Handlers
**File**: `lib/telephony.dart`
**Purpose**: Handle SMS processing in background isolate

**Key Functions**:
```dart
@pragma('vm:entry-point')
void _flutterSmsSetupBackgroundChannel({MethodChannel backgroundChannel = const MethodChannel(_BACKGROUND_CHANNEL)})
```

---

## Data Flow

### Incoming SMS Flow

#### Foreground Processing
```
1. Android SMS System
   ↓ (SMS_RECEIVED broadcast)
2. IncomingSmsReceiver.onReceive()
   ↓ (groups multipart messages)
3. IncomingSmsReceiver.processIncomingSms()
   ↓ (checks if app is foreground)
4. foregroundSmsChannel.invokeMethod(ON_MESSAGE, args)
   ↓ (Method Channel communication)
5. Flutter Telephony._foregroundChannel handler
   ↓ (processes message)
6. User-defined onNewMessage callback
```

#### Background Processing
```
1. Android SMS System
   ↓ (SMS_RECEIVED broadcast)
2. IncomingSmsReceiver.onReceive()
   ↓ (groups multipart messages)
3. IncomingSmsReceiver.processIncomingSms()
   ↓ (detects app is in background)
4. IncomingSmsReceiver.processInBackground()
   ↓ (checks if isolate is running)
5a. If isolate running:
    IncomingSmsHandler.executeDartCallbackInBackgroundIsolate()
5b. If isolate not running:
    Add to backgroundMessageQueue + startBackgroundIsolate()
   ↓ (Background Method Channel)
6. _flutterSmsSetupBackgroundChannel()
   ↓ (Background isolate processes message)
7. User-defined onBackgroundMessage callback
```

### Outgoing SMS Flow
```
1. Flutter App calls telephony.sendSms()
   ↓ (Method Channel call)
2. SmsMethodCallHandler.onMethodCall()
   ↓ (routes to SMS action)
3. SmsController.sendSms() or sendMultipartSms()
   ↓ (uses Android SmsManager)
4. Android SMS System sends message
   ↓ (if listenStatus = true)
5. BroadcastReceiver receives SMS_SENT/SMS_DELIVERED
   ↓ (Method Channel callback)
6. Flutter statusListener callback
```

### SMS Query Flow
```
1. Flutter App calls getInboxSms()/getSentSms()/getDraftSms()
   ↓ (Method Channel call)
2. SmsMethodCallHandler.onMethodCall()
   ↓ (routes to query action)
3. SmsController.getMessages()
   ↓ (queries Android SMS ContentProvider)
4. Android SMS Database
   ↓ (returns cursor with results)
5. Convert to HashMap list
   ↓ (Method Channel response)
6. Flutter converts to List<SmsMessage>
```

---

## Implementation Details

### Message Data Structure

#### Kotlin Side (HashMap)
```kotlin
val smsMap = HashMap<String, Any?>()
smsMap[MESSAGE_BODY] = messageBody
smsMap[TIMESTAMP] = timestampMillis.toString()
smsMap[ORIGINATING_ADDRESS] = originatingAddress
smsMap[STATUS] = status.toString()
smsMap[SERVICE_CENTER_ADDRESS] = serviceCenterAddress
```

#### Flutter Side (SmsMessage)
```dart
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
}
```

### Method Channel Configuration

#### Channel Names
- **Foreground**: `plugins.shounakmulay.com/foreground_sms_channel`
- **Background SMS**: `plugins.shounakmulay.com/background_sms_channel`
- **Background Call**: `plugins.shounakmulay.com/background_call_channel`

#### Key Method Names
- `onMessage`: Foreground SMS reception
- `handleBackgroundMessage`: Background SMS processing
- `sendSms`: Send single SMS
- `sendMultipartSms`: Send multipart SMS
- `getAllInboxSms`: Query inbox messages
- `smsSent`: SMS send status callback
- `smsDelivered`: SMS delivery status callback

---

## Communication Protocol

### Method Call Structure

#### Flutter to Kotlin
```dart
// Example: Send SMS
final Map<String, dynamic> args = {
  "address": to,
  "message_body": message,
  "listen_status": listenStatus,
  "sub_id": subscriptionId
};
await _foregroundChannel.invokeMethod(SEND_SMS, args);
```

#### Kotlin to Flutter
```kotlin
// Example: Incoming SMS notification
val args = HashMap<String, Any>()
args[MESSAGE] = messageMap
foregroundSmsChannel?.invokeMethod(ON_MESSAGE, args)
```

### Background Isolate Setup

#### Initialization Process
1. Flutter calls `startBackgroundService` with callback handles
2. Kotlin stores handles in SharedPreferences
3. Kotlin initializes FlutterEngine with background isolate
4. Background isolate sets up method channel handler
5. Background isolate signals initialization complete
6. Queued messages are processed

#### Callback Handle Management
```dart
final CallbackHandle backgroundSetupHandle = 
    PluginUtilities.getCallbackHandle(_flutterSmsSetupBackgroundChannel)!;
final CallbackHandle? backgroundMessageHandle = 
    PluginUtilities.getCallbackHandle(_onBackgroundMessages);
```

---

## Background Processing

### Background Isolate Lifecycle

1. **Initialization**:
   - Flutter provides callback handles
   - Kotlin creates new FlutterEngine
   - Background Dart isolate starts
   - Method channel established

2. **Message Queuing**:
   - Messages received during initialization are queued
   - Queue is processed once isolate is ready
   - Prevents message loss during startup

3. **Processing**:
   - Background isolate receives messages via method channel
   - User callback executed in background context
   - Isolate remains active for subsequent messages

### Foreground Detection
```kotlin
fun isApplicationForeground(context: Context): Boolean {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if (keyguardManager.isKeyguardLocked) {
        return false
    }
    val myPid = Process.myPid()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    // Check if process has foreground importance
    return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
```

---

## Error Handling

### Common Error Scenarios

1. **Permission Denied**:
   - SMS permissions not granted
   - Handled via permission request flow
   - Error code: `permission_denied`

2. **Background Setup Failure**:
   - Invalid callback handles
   - Background isolate initialization failure
   - Graceful fallback to foreground-only mode

3. **Method Channel Errors**:
   - Channel not initialized
   - Invalid method parameters
   - Try-catch blocks prevent crashes

4. **SMS Send Failures**:
   - Network issues
   - Invalid phone numbers
   - Status callbacks provide failure information

### Error Recovery Mechanisms

- **Graceful Degradation**: Background failures fall back to foreground processing
- **Message Queuing**: Prevents message loss during initialization
- **Retry Logic**: Automatic retry for transient failures
- **Status Callbacks**: Inform Flutter layer of operation results

---

## Performance Considerations

### Optimization Strategies

1. **Message Grouping**:
   - Multipart SMS messages grouped by originating address
   - Reduces callback frequency for long messages

2. **Background Isolate Reuse**:
   - Single isolate handles multiple background messages
   - Avoids overhead of repeated isolate creation

3. **Efficient Data Transfer**:
   - Minimal data structure for method channel communication
   - String-based message representation

4. **Memory Management**:
   - Message queue cleared after processing
   - Proper cursor management for database queries

### Resource Usage

- **Memory**: Background isolate adds ~10-20MB overhead
- **CPU**: Minimal impact during normal operation
- **Battery**: Background processing optimized for efficiency
- **Network**: No additional network usage

---

## Security & Permissions

### Required Permissions

#### SMS Operations
```xml
<uses-permission android:name="android.permission.SEND_SMS"/>
<uses-permission android:name="android.permission.RECEIVE_SMS"/>
<uses-permission android:name="android.permission.READ_SMS"/>
```

#### Phone Operations
```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
<uses-permission android:name="android.permission.CALL_PHONE"/>
```

### BroadcastReceiver Registration
```xml
<receiver android:name="com.shounakmulay.telephony.sms.IncomingSmsReceiver"
    android:permission="android.permission.BROADCAST_SMS" 
    android:exported="true">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_RECEIVED"/>
    </intent-filter>
</receiver>
```

### Security Considerations

1. **Permission Validation**: All operations check required permissions
2. **Secure Storage**: Callback handles stored in private SharedPreferences
3. **Input Validation**: Phone numbers and message content validated
4. **Background Restrictions**: Background processing respects Android limitations

---

## Conclusion

The SMS streaming system in the Flutter Telephony Plugin provides a robust, efficient, and secure solution for SMS handling in Flutter applications. The architecture ensures reliable message delivery through both foreground and background processing modes, while maintaining optimal performance and security standards.

The bidirectional communication between Kotlin and Flutter layers enables seamless integration with Android's native telephony system, providing Flutter developers with comprehensive SMS capabilities that match native Android functionality.