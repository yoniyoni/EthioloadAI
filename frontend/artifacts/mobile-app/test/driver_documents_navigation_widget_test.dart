import 'package:dio/dio.dart';
import 'package:ethioload_ai/src/config/routes/app_router.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/data/providers/data_providers.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:logger/logger.dart';

class _TestAuthNotifier extends AuthNotifier {
  _TestAuthNotifier(bool isVerified)
      : super(
          AuthRepository(
            ApiClient(
              dio: Dio(),
              logger: Logger(level: Level.off),
              secureStorage: const FlutterSecureStorage(),
            ),
          ),
        ) {
    state = AuthState(
      user: User(
        id: 1,
        fullName: 'Test Driver',
        email: 'driver@example.test',
        phone: '+251900000000',
        role: 'driver',
        verificationStatus: isVerified,
      ),
      token: 'test-token',
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  Future<GoRouter> pumpDriverApp(
    WidgetTester tester, {
    required bool isVerified,
  }) async {
    late GoRouter router;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authNotifierProvider
              .overrideWith((ref) => _TestAuthNotifier(isVerified)),
          authInitializationProvider.overrideWith((ref) async {}),
          driverDocumentsProvider
              .overrideWith((ref) async => <DriverDocument>[]),
        ],
        child: Consumer(
          builder: (context, ref, _) {
            router = ref.watch(appRouterProvider);
            return MaterialApp.router(routerConfig: router);
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    return router;
  }

  testWidgets('document back button returns unverified drivers to onboarding',
      (tester) async {
    await pumpDriverApp(tester, isVerified: false);

    expect(find.text('Driver onboarding / የአሽከርካሪ ምዝገባ'), findsOneWidget);
    await tester.tap(find.text('Review or submit documents'));
    await tester.pumpAndSettle();

    expect(find.text('My Documents / ሰነዶቼ'), findsOneWidget);
    expect(find.byTooltip('Back'), findsOneWidget);
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();

    expect(find.text('Driver onboarding / የአሽከርካሪ ምዝገባ'), findsOneWidget);
    expect(find.text('Driver Dashboard'), findsNothing);
  });

  testWidgets('Android system back returns from documents to onboarding',
      (tester) async {
    await pumpDriverApp(tester, isVerified: false);

    await tester.tap(find.text('Review or submit documents'));
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.text('Driver onboarding / የአሽከርካሪ ምዝገባ'), findsOneWidget);
    expect(find.text('Driver Dashboard'), findsNothing);
  });

  testWidgets('direct document entry exposes an onboarding home fallback',
      (tester) async {
    final router = await pumpDriverApp(tester, isVerified: false);
    router.go('/driver-documents');
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Onboarding home'));
    await tester.pumpAndSettle();

    expect(find.text('Driver onboarding / የአሽከርካሪ ምዝገባ'), findsOneWidget);
    expect(find.text('Driver Dashboard'), findsNothing);
  });
}
