import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Call handler for managing incoming call UI
class CallHandler {
  static const MethodChannel _channel =
      MethodChannel('plugins.shounakmulay.com/foreground_sms_channel');

  static void initialize() {
    // Set up method call handler to listen for incoming call UI events
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  static Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'showIncomingCallUI':
        _handleIncomingCallUI(call.arguments);
        break;
      default:
        print('Unknown method: ${call.method}');
    }
  }

  static void _handleIncomingCallUI(dynamic arguments) {
    // Extract call data
    final Map<String, dynamic> callData = Map<String, dynamic>.from(arguments);
    final String phoneNumber = callData['phoneNumber'] ?? 'Unknown';
    final String callId = callData['callId'] ?? '';
    final String callerDisplayName =
        callData['callerDisplayName'] ?? phoneNumber;
    final int timestamp = callData['timestamp'] ?? 0;

    print('Incoming call UI triggered for: $phoneNumber');

    // Navigate to your incoming call screen
    _navigateToIncomingCallScreen(
      phoneNumber: phoneNumber,
      callId: callId,
      callerName: callerDisplayName,
      timestamp: timestamp,
    );
  }

  static void _navigateToIncomingCallScreen({
    required String phoneNumber,
    required String callId,
    required String callerName,
    required int timestamp,
  }) {
    // Use a global navigator key or event bus to trigger navigation
    // This is just an example - you'll need to implement this based on your app's architecture

    // Example using a global navigator key:
    // if (navigatorKey.currentState != null) {
    //   navigatorKey.currentState!.pushNamed(
    //     '/incoming-call',
    //     arguments: {
    //       'phoneNumber': phoneNumber,
    //       'callId': callId,
    //       'callerName': callerName,
    //       'timestamp': timestamp,
    //     },
    //   );
    // }

    print('Should navigate to incoming call screen for: $phoneNumber');
  }
}

/// Global navigator key for accessing navigation from anywhere
final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

/// Incoming call screen widget
class IncomingCallScreen extends StatelessWidget {
  final String phoneNumber;
  final String callId;
  final String callerName;

  const IncomingCallScreen({
    Key? key,
    required this.phoneNumber,
    required this.callId,
    required this.callerName,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black87,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Caller avatar
            CircleAvatar(
              radius: 80,
              backgroundColor: Colors.blue,
              child: Text(
                callerName.isNotEmpty ? callerName[0].toUpperCase() : '?',
                style: TextStyle(fontSize: 48, color: Colors.white),
              ),
            ),
            SizedBox(height: 24),

            // Caller name
            Text(
              callerName,
              style: TextStyle(
                color: Colors.white,
                fontSize: 28,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 8),

            // Phone number
            Text(
              phoneNumber,
              style: TextStyle(
                color: Colors.white70,
                fontSize: 18,
              ),
            ),
            SizedBox(height: 48),

            // Action buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                // Reject button
                FloatingActionButton(
                  onPressed: () => _rejectCall(context),
                  backgroundColor: Colors.red,
                  child: Icon(Icons.call_end, color: Colors.white),
                ),

                // Answer button
                FloatingActionButton(
                  onPressed: () => _answerCall(context),
                  backgroundColor: Colors.green,
                  child: Icon(Icons.call, color: Colors.white),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _answerCall(BuildContext context) {
    // You can use the existing telephony plugin methods to answer
    // Or implement your own call management
    Navigator.pop(context);
    print('Call answered for: $phoneNumber');
  }

  void _rejectCall(BuildContext context) {
    // You can use the existing telephony plugin methods to reject
    // Or implement your own call management
    Navigator.pop(context);
    print('Call rejected for: $phoneNumber');
  }
}
