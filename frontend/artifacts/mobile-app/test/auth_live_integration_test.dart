import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/providers/data_providers.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const live = bool.fromEnvironment('LIVE_BACKEND');
  test('real API login, invalid password, restore and logout for driver and shipper', () async {
    HttpOverrides.global = null;
    FlutterSecureStorage.setMockInitialValues({});
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final api = container.read(apiClientProvider);
    final repo = AuthRepository(api);
    final stamp = DateTime.now().microsecondsSinceEpoch;
    for (final role in ['driver', 'shipper']) {
      final email = 'audit-$stamp-$role@example.test';
      final password = 'Audit-$stamp!';
      final registered = await repo.register(fullName: 'Integration $role', email: email,
        phone: '+2519${(stamp + (role == 'driver' ? 0 : 1)).toString().substring(8).padLeft(8, '0')}', password: password, role: role);
      expect(registered.user.role, role);
      await repo.logout();
      final notifier = AuthNotifier(repo);
      await notifier.login(identifier: email, password: password);
      expect(notifier.state.isAuthenticated, true, reason: notifier.state.error);
      expect(await api.getToken(), notifier.state.token);
      final restored = AuthNotifier(repo);
      await restored.checkAuthStatus();
      expect(restored.state.user?.id, registered.user.id);
      await restored.logout();
      expect(await api.getToken(), isNull);
      await notifier.login(identifier: email, password: 'invalid-password');
      expect(notifier.state.error, isNotNull);
      notifier.dispose(); restored.dispose();
    }
  }, skip: !live, timeout: const Timeout(Duration(minutes: 2)));
}
