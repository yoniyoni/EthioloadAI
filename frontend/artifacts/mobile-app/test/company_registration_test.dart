import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:logger/logger.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/features/fleet/company_registration_screen.dart';

class CompanyTestApi extends ApiClient {
  Map<String, dynamic> company = {
    'id': 51, 'companyName': 'Test Logistics', 'legalName': 'Test Logistics PLC',
    'tinNumber': 'TIN-123', 'businessRegistrationNumber': 'REG-123',
    'phone': '+251911223344', 'email': 'office@example.test',
    'businessAddress': 'Bole Road', 'city': 'Addis Ababa',
    'verificationStatus': 'DRAFT',
  };
  List<Map<String, dynamic>> documents = [];
  Map<String, dynamic>? lastSaved;
  int submissions = 0;
  String? submitError;
  CompanyTestApi() : super(dio: Dio(), logger: Logger(), secureStorage: const FlutterSecureStorage());

  @override
  Future<T> get<T>(String endpoint, {Map<String, dynamic>? queryParameters, T Function(dynamic)? fromJson}) async {
    final dynamic result = endpoint.endsWith('/documents') ? documents : endpoint.endsWith('/me') ? {'content': [company], 'last': true} : company;
    return result as T;
  }

  @override
  Future<T> post<T>(String endpoint, {Map<String, dynamic>? data, T Function(dynamic)? fromJson}) async {
    if (endpoint.endsWith('/submit')) {
      submissions++;
      if (submitError != null) throw ApiException(message: submitError!);
      company = {...company, 'verificationStatus': 'PENDING'};
    } else {
      lastSaved = data;
      company = {...company, ...?data};
    }
    return company as T;
  }

  @override
  Future<T> put<T>(String endpoint, {Map<String, dynamic>? data, T Function(dynamic)? fromJson}) async {
    lastSaved = data;
    company = {...company, ...?data};
    return company as T;
  }

  void completeDocuments() {
    documents = ['BUSINESS_LICENSE', 'BUSINESS_REGISTRATION', 'TIN_CERTIFICATE'].map((type) => {'id': type, 'documentType': type, 'originalName': '$type.pdf'}).toList();
  }
}

Future<GoRouter> showCompany(WidgetTester tester, CompanyTestApi api, String path) async {
  final router = GoRouter(initialLocation: path, routes: [
    GoRoute(path: '/fleet/companies', builder: (_, __) => const CompanyRegistrationScreen()),
    GoRoute(path: '/fleet/companies/:id', builder: (_, state) => CompanyRegistrationScreen(key: ValueKey(state.pathParameters['id']), companyId: state.pathParameters['id'])),
    GoRoute(path: '/fleet', builder: (_, __) => const Scaffold(body: Text('Fleet'))),
  ]);
  await tester.pumpWidget(ProviderScope(overrides: [apiClientProvider.overrideWithValue(api)], child: MaterialApp.router(routerConfig: router)));
  await tester.pumpAndSettle();
  return router;
}

void main() {
  testWidgets('owner can resume an existing company application from the list', (tester) async {
    final api = CompanyTestApi();
    final router = await showCompany(tester, api, '/fleet/companies');
    await tester.tap(find.text('Test Logistics'));
    await tester.pumpAndSettle();
    expect(router.routeInformationProvider.value.uri.path, '/fleet/companies/51');
    expect(find.text('Draft'), findsOneWidget);
  });

  testWidgets('new company saves a minimal draft and continues to documents', (tester) async {
    final api = CompanyTestApi();
    final router = await showCompany(tester, api, '/fleet/companies/new');
    await tester.enterText(find.byType(TextFormField).first, 'New Logistics');
    await tester.ensureVisible(find.text('Save and continue'));
    await tester.tap(find.text('Save and continue'));
    await tester.pumpAndSettle();
    expect(api.lastSaved?['companyName'], 'New Logistics');
    expect(api.lastSaved?['tinNumber'], isNull);
    expect(api.lastSaved?.containsKey('ownerId'), isFalse);
    expect(router.routeInformationProvider.value.uri.path, '/fleet/companies/51');
    expect(find.text('2. Company documents'), findsOneWidget);
  });

  testWidgets('incomplete documents block submission', (tester) async {
    final api = CompanyTestApi();
    await showCompany(tester, api, '/fleet/companies/51');
    await tester.ensureVisible(find.text('Submit for review'));
    final button = tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Submit for review'));
    expect(button.onPressed, isNull);
    expect(api.submissions, 0);
  });

  testWidgets('complete application submits and locks editing', (tester) async {
    final api = CompanyTestApi()..completeDocuments();
    await showCompany(tester, api, '/fleet/companies/51');
    await tester.ensureVisible(find.text('Submit for review'));
    await tester.tap(find.text('Submit for review'));
    await tester.pumpAndSettle();
    expect(api.submissions, 1);
    expect(find.text('Under review'), findsOneWidget);
    expect(find.text('Edit company details'), findsNothing);
    expect(find.text('Replace document'), findsNothing);
  });

  testWidgets('admin corrections preserve all company fields on update', (tester) async {
    final api = CompanyTestApi();
    api.company = {...api.company, 'verificationStatus': 'MORE_INFORMATION_REQUIRED', 'reviewMessage': 'Correct your address.'};
    await showCompany(tester, api, '/fleet/companies/51');
    expect(find.text('Admin feedback: Correct your address.'), findsOneWidget);
    await tester.ensureVisible(find.text('Edit company details'));
    await tester.tap(find.text('Edit company details')); await tester.pumpAndSettle();
    final address = find.byWidgetPredicate((widget) => widget is TextFormField && widget.controller?.text == 'Bole Road');
    await tester.ensureVisible(address); await tester.enterText(address, 'Updated address');
    await tester.ensureVisible(find.text('Save and continue')); await tester.tap(find.text('Save and continue')); await tester.pumpAndSettle();
    expect(api.lastSaved?['businessAddress'], 'Updated address');
    expect(api.lastSaved?['legalName'], 'Test Logistics PLC');
    expect(api.lastSaved?.length, 8);
    expect(find.text('Resubmit for review'), findsOneWidget);
  });

  testWidgets('failed submission shows the server message and allows retry', (tester) async {
    final api = CompanyTestApi()..completeDocuments();
    api.submitError = 'Company details are incomplete';
    await showCompany(tester, api, '/fleet/companies/51');
    await tester.ensureVisible(find.text('Submit for review')); await tester.tap(find.text('Submit for review')); await tester.pumpAndSettle();
    expect(find.text('Company details are incomplete'), findsOneWidget);
    expect(api.company['verificationStatus'], 'DRAFT');
  });
}
