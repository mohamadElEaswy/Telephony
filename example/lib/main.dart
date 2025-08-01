import 'package:another_telephony/telephony.dart';
import 'package:flutter/material.dart';
import 'dart:async';

@pragma('vm:entry-point')
onBackgroundMessage(SmsMessage message) {
  debugPrint("onBackgroundMessage called");
}

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _message = "";
  final telephony = Telephony.instance;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  onMessage(SmsMessage message) async {
    setState(() {
      _message = message.body ?? "Error reading message body.";
    });
  }

  onSendStatus(SendStatus status) {
    setState(() {
      _message = status == SendStatus.SENT ? "sent" : "delivered";
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    // Platform messages may fail, so we use a try/catch PlatformException.
    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.

    final bool? result = await telephony.requestPhoneAndSmsPermissions;

    if (result != null && result) {
      telephony.listenIncomingSms(
          onNewMessage: onMessage, onBackgroundMessage: onBackgroundMessage);
    }

    if (!mounted) return;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        home: Scaffold(
      appBar: AppBar(
        title: const Text('Plugin example app'),
      ),
      body: SingleChildScrollView(
        padding: EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(child: Text("Latest received SMS: $_message")),
            SizedBox(height: 20),
            ElevatedButton(
                onPressed: () async {
                  await telephony.openDialer("123413453");
                },
                child: Text('Open Dialer')),
            SizedBox(height: 10),
            ElevatedButton(
                onPressed: () async {
                  bool? isDefault = await telephony.isDefaultDialer;
                  setState(() {
                    _message = "Is Default Dialer: ${isDefault ?? 'Unknown'}";
                  });
                },
                child: Text('Check Default Dialer')),
            SizedBox(height: 10),
            ElevatedButton(
                onPressed: () async {
                  bool? success = await telephony.requestDefaultDialer;
                  setState(() {
                    _message = "Request Default Dialer: ${success ?? 'Failed'}";
                  });
                },
                child: Text('Request Default Dialer')),
            SizedBox(height: 10),
            ElevatedButton(
                onPressed: () async {
                  await telephony.openCallSettings();
                  setState(() {
                    _message = "Opened call settings";
                  });
                },
                child: Text('Open Call Settings')),
            SizedBox(height: 10),
            ElevatedButton(
                onPressed: () async {
                  bool? success = await telephony.registerPhoneAccount(
                      "test_account", "Test SIP Account");
                  setState(() {
                    _message = "Register Account: ${success ?? 'Failed'}";
                  });
                },
                child: Text('Register Phone Account')),
            SizedBox(height: 10),
            ElevatedButton(
                onPressed: () async {
                  List<Map<String, dynamic>>? accounts =
                      await telephony.getPhoneAccounts;
                  setState(() {
                    _message = "Phone Accounts: ${accounts?.length ?? 0}";
                  });
                },
                child: Text('Get Phone Accounts')),
          ],
        ),
      ),
    ));
  }
}
