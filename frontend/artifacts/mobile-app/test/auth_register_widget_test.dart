import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/features/auth/auth_screens.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:logger/logger.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RegistrationAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode({'message': 'Synthetic widget-test response'}),
      422,
      headers: {Headers.contentTypeHeader: ['application/json']},
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await EasyLocalization.ensureInitialized();
  });
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  testWidgets('Register button submits the validated form to Spring auth API',
      (tester) async {
    final adapter = _RegistrationAdapter();
    final submittedPassword =
        'Widget-only-${DateTime.now().microsecondsSinceEpoch}';
    final dio = Dio(BaseOptions(
      baseUrl: 'http://127.0.0.1:8080/api',
      validateStatus: (status) => status != null && status < 500,
    ))
      ..httpClientAdapter = adapter;
    final api = ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    );
    final router = GoRouter(
      initialLocation: '/register',
      routes: [
        GoRoute(
          path: '/register',
          builder: (_, __) => const RegisterScreen(),
        ),
      ],
    );

    await tester.pumpWidget(
      EasyLocalization(
        supportedLocales: const [Locale('en', 'US'), Locale('am', 'ET')],
        path: 'assets/languages',
        fallbackLocale: const Locale('en', 'US'),
        child: ProviderScope(
          overrides: [apiClientProvider.overrideWithValue(api)],
          child: Builder(
            builder: (context) => MaterialApp.router(
              localizationsDelegates: context.localizationDelegates,
              supportedLocales: context.supportedLocales,
              locale: context.locale,
              routerConfig: router,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final fields = find.byType(TextField);
    expect(fields, findsNWidgets(4));
    await tester.enterText(fields.at(0), 'Synthetic Widget User');
    await tester.enterText(fields.at(1), 'widget-user@example.test');
    await tester.enterText(fields.at(2), '+251911234567');
    await tester.enterText(fields.at(3), submittedPassword);

    final registerButton = find.widgetWithText(
      ElevatedButton,
      'Create Account'.tr(),
    );
    await tester.ensureVisible(registerButton);
    await tester.tap(registerButton);
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(1));
    final request = adapter.requests.single;
    expect(request.method, 'POST');
    expect(request.path, '/auth/register');
    final payload = request.data is String
        ? jsonDecode(request.data as String) as Map<String, dynamic>
        : Map<String, dynamic>.from(request.data as Map);
    expect(payload, {
      'fullName': 'Synthetic Widget User',
      'email': 'widget-user@example.test',
      'phone': '+251911234567',
      'password': submittedPassword,
      'role': 'shipper',
    });

    router.dispose();
    dio.close(force: true);
  });
}
