import 'package:another_telephony/telephony.dart';

void main() async {
  final telephony = Telephony.instance;

  print('Testing phone account functionality...');

  try {
    // Test registering a phone account
    print('1. Testing phone account registration...');
    final registerResult = await telephony.registerPhoneAccount(
      'test_account_1',
      'Test Phone Account',
      capabilities: 1, // CAPABILITY_CALL_PROVIDER
    );
    print('Register result: $registerResult');

    // Test getting phone accounts
    print('2. Testing get phone accounts...');
    final accounts = await telephony.getPhoneAccounts;
    print('Phone accounts: $accounts');

    // Test checking if account is enabled
    print('3. Testing account enabled check...');
    final isEnabled = await telephony.isPhoneAccountEnabled('test_account_1');
    print('Account enabled: $isEnabled');

    // Test unregistering the account
    print('4. Testing phone account unregistration...');
    final unregisterResult =
        await telephony.unregisterPhoneAccount('test_account_1');
    print('Unregister result: $unregisterResult');

    print('✅ All phone account tests completed successfully!');
    print('✅ No NullPointerException occurred!');
  } catch (e) {
    print('❌ Error occurred: $e');
    print('❌ Stack trace: ${e.toString()}');
  }
}
