import 'package:another_telephony/telephony.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Phone Account Test',
      home: PhoneAccountTestPage(),
    );
  }
}

class PhoneAccountTestPage extends StatefulWidget {
  @override
  _PhoneAccountTestPageState createState() => _PhoneAccountTestPageState();
}

class _PhoneAccountTestPageState extends State<PhoneAccountTestPage> {
  final Telephony telephony = Telephony.instance;
  String _status = 'Ready to test';

  Future<void> _testPhoneAccountRegistration() async {
    try {
      setState(() {
        _status = 'Registering phone account...';
      });

      // Test the fixed registerPhoneAccount method
      bool? result = await telephony.registerPhoneAccount('test_account_id', 'Test Account');
      
      if (result == true) {
        setState(() {
          _status = 'Phone account registered successfully!';
        });
        
        // Test if the account is enabled
        bool? isEnabled = await telephony.isPhoneAccountEnabled('test_account_id');
        setState(() {
          _status += '\nAccount enabled: $isEnabled';
        });
        
        // Get all registered accounts
        List<Map<String, dynamic>>? accounts = await telephony.getPhoneAccounts;
        setState(() {
          _status += '\nRegistered accounts: ${accounts?.length ?? 0}';
        });
        
      } else {
        setState(() {
          _status = 'Failed to register phone account';
        });
      }
    } catch (e) {
      setState(() {
        _status = 'Error: $e';
      });
    }
  }

  Future<void> _testUnregisterPhoneAccount() async {
    try {
      setState(() {
        _status = 'Unregistering phone account...';
      });

      bool? result = await telephony.unregisterPhoneAccount('test_account_id');
      
      if (result == true) {
        setState(() {
          _status = 'Phone account unregistered successfully!';
        });
      } else {
        setState(() {
          _status = 'Failed to unregister phone account';
        });
      }
    } catch (e) {
      setState(() {
        _status = 'Error: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Phone Account Registration Test'),
      ),
      body: Padding(
        padding: EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Phone Account Registration Fix Test',
              style: Theme.of(context).textTheme.headlineSmall,
              textAlign: TextAlign.center,
            ),
            SizedBox(height: 20),
            Text(
              'This test verifies that the SecurityException has been fixed for phone account registration.',
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            SizedBox(height: 30),
            ElevatedButton(
              onPressed: _testPhoneAccountRegistration,
              child: Text('Register Phone Account'),
            ),
            SizedBox(height: 10),
            ElevatedButton(
              onPressed: _testUnregisterPhoneAccount,
              child: Text('Unregister Phone Account'),
            ),
            SizedBox(height: 30),
            Container(
              padding: EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                _status,
                style: TextStyle(fontFamily: 'monospace'),
              ),
            ),
            SizedBox(height: 20),
            Text(
              'Fix Details:',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            SizedBox(height: 10),
            Text(
              '1. Fixed ComponentName to use correct TelephonyConnectionService\n'
              '2. Added CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS capability\n'
              '3. Fixed TelephonyConnection constructor calls\n'
              '4. Service is properly configured with BIND_TELECOM_CONNECTION_SERVICE permission',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}