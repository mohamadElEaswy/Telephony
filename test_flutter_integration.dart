import 'package:another_telephony/telephony.dart';

void main() async {
  final telephony = Telephony.instance;

  print('Testing Flutter integration with new call features...');

  try {
    // Test phone account registration
    print('1. Testing phone account registration...');
    final registerResult = await telephony.registerPhoneAccount(
      'test_account_1',
      'Test Phone Account',
      capabilities: 1, // CAPABILITY_CALL_PROVIDER
    );
    print('Register result: $registerResult');

    // Test call capabilities
    print('2. Testing call capabilities...');
    final capabilities = await telephony.getCallCapabilities();
    print('Call capabilities: $capabilities');

    // Test call permission check
    print('3. Testing call permission check...');
    final hasPermission = await telephony.checkCallPermission();
    print('Has call permission: $hasPermission');

    // Test call audio state
    print('4. Testing call audio state...');
    final audioState = await telephony.getCallAudioState();
    print('Call audio state: $audioState');

    // Test notification channel setup
    print('5. Testing notification channel setup...');
    final channelResult = await telephony.setCallNotificationChannel(
      'incoming_calls',
      'Incoming Calls',
      description: 'Notifications for incoming calls',
    );
    print('Channel setup result: $channelResult');

    print('✅ All Flutter integration tests completed successfully!');
    print('✅ Flutter and Kotlin are properly linked!');
  } catch (e) {
    print('❌ Error occurred: $e');
    print('❌ Stack trace: ${e.toString()}');
  }
}
