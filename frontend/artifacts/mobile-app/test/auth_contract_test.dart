import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logger/logger.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/data/providers/data_providers.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';

class ContractAdapter implements HttpClientAdapter {
  final int status;
  final List<RequestOptions> requests = [];
  ContractAdapter(this.status);
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? body, Future<void>? cancel) async {
    requests.add(options);
    if (status == 200 && options.path == '/auth/me') {
      return ResponseBody.fromString(jsonEncode({'id':17,'full_name':'Driver','role':'DRIVER','verification_status':true}), 200,
        headers: {Headers.contentTypeHeader: ['application/json']});
    }
    return ResponseBody.fromString(jsonEncode(status == 200 ? {
      'token': 'test.jwt.value', 'user': {'id': 17, 'full_name': 'Driver', 'email': 'driver@example.test',
        'phone': '+251911111111', 'role': 'DRIVER', 'verification_status': true, 'is_active': true},
    } : {'message': 'Invalid credentials'}), status, headers: {Headers.contentTypeHeader: ['application/json']});
  }
  @override
  void close({bool force = false}) {}
}
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));
  test('Spring response uses token, snake_case user fields and uppercase role', () {
    final response=AuthResponse.fromJson({'token':'test.jwt.value','user':{'id':2,'full_name':'Employee','role':'SHIPPER','verification_status':1,'is_active':1}});
    expect(response.token,'test.jwt.value');expect(response.user.fullName,'Employee');expect(response.user.role,'shipper');expect(response.user.verificationStatus,true);
  });
  test('platform defaults distinguish Android, desktop and browser origin', () {
    String resolve(TargetPlatform platform,{bool web=false,String override='',bool release=false}) => resolveApiBaseUrl(override:override,web:web,platform:platform,release:release,origin:Uri.parse('https://mobile.example.test/'));
    expect(resolve(TargetPlatform.android),'http://10.0.2.2:8080/api');
    expect(resolve(TargetPlatform.windows),'http://127.0.0.1:8080/api');
    expect(resolve(TargetPlatform.iOS),'http://127.0.0.1:8080/api');
    expect(resolve(TargetPlatform.android,web:true),'https://mobile.example.test/api');
    expect(resolve(TargetPlatform.android,override:'https://api.example.test/api/'),'https://api.example.test/api');
    expect(()=>resolve(TargetPlatform.android,release:true),throwsStateError);
    expect(()=>resolve(TargetPlatform.android,override:'file:///secret'),throwsArgumentError);
  });
  test('login persists JWT and builds authenticated state; invalid credentials clear state', () async {
    final dio=Dio(BaseOptions(baseUrl:'http://127.0.0.1:8080/api',validateStatus:(s)=>true));
    dio.httpClientAdapter=ContractAdapter(200);
    final client=ApiClient(dio:dio,logger:Logger(level:Level.off),secureStorage:const FlutterSecureStorage());
    final notifier=AuthNotifier(AuthRepository(client));
    await notifier.login(identifier:'driver@example.test',password:'test');
    expect(notifier.state.isAuthenticated,true);expect(notifier.state.user?.role,'driver');expect(await client.getToken(),'test.jwt.value');
    dio.httpClientAdapter=ContractAdapter(401);
    await notifier.login(identifier:'driver@example.test',password:'wrong');
    expect(notifier.state.isAuthenticated,false);expect(await client.getToken(),isNull);expect(notifier.state.error,'Invalid credentials');
    notifier.dispose();dio.close();
  });
  test('interceptor sends a stored JWT as a Bearer token', () async {
    FlutterSecureStorage.setMockInitialValues({'auth_token': '  test.jwt.value  '});
    final adapter = ContractAdapter(200);
    final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080/api'))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));

    await dio.get('/vehicles/nearby');

    expect(adapter.requests.single.headers['Authorization'],
        'Bearer test.jwt.value');
    dio.close();
  });
  test('interceptor omits Authorization when no token is stored', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final adapter = ContractAdapter(200);
    final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080/api'))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));

    await dio.get('/vehicles/nearby');

    expect(adapter.requests.single.headers, isNot(contains('Authorization')));
    dio.close();
  });
  test('interceptor omits Authorization when the stored token is empty', () async {
    FlutterSecureStorage.setMockInitialValues({'auth_token': '   '});
    final adapter = ContractAdapter(200);
    final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080/api'))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));

    await dio.get('/vehicles/nearby');

    expect(adapter.requests.single.headers, isNot(contains('Authorization')));
    dio.close();
  });
  test('interceptor never sends the redaction placeholder as a token', () async {
    FlutterSecureStorage.setMockInitialValues({'auth_token': '******'});
    final adapter = ContractAdapter(200);
    final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080/api'))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));

    await dio.get('/vehicles/nearby');

    expect(adapter.requests.single.headers, isNot(contains('Authorization')));
    dio.close();
  });
  test('interceptor does not attach a stored token to public auth endpoints', () async {
    FlutterSecureStorage.setMockInitialValues({'auth_token': 'test.jwt.value'});
    final adapter = ContractAdapter(200);
    final dio = Dio(BaseOptions(baseUrl: 'http://127.0.0.1:8080/api'))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));

    await dio.post('/auth/login');
    await dio.post('/auth/register');

    expect(adapter.requests, hasLength(2));
    for (final request in adapter.requests) {
      expect(request.headers, isNot(contains('Authorization')));
    }
    dio.close();
  });
  test('server failure never creates an alternate mock session', () async {
    final dio=Dio(BaseOptions(baseUrl:'http://127.0.0.1:8080/api'))..httpClientAdapter=ContractAdapter(503);
    final client=ApiClient(dio:dio,logger:Logger(level:Level.off),secureStorage:const FlutterSecureStorage());
    await expectLater(AuthRepository(client).login(identifier:'driver@example.test',password:'test'),throwsA(isA<ApiException>()));
    expect(await client.getToken(),isNull);dio.close();
  });
  test('router startup barrier restores saved authentication before redirects', () async {
    final dio=Dio(BaseOptions(baseUrl:'http://127.0.0.1:8080/api'))..httpClientAdapter=ContractAdapter(200);
    final client=ApiClient(dio:dio,logger:Logger(level:Level.off),secureStorage:const FlutterSecureStorage());
    await client.saveToken('saved.jwt.value');
    final container=ProviderContainer(overrides:[authRepositoryProvider.overrideWithValue(AuthRepository(client))]);
    addTearDown(() {container.dispose();dio.close();});
    expect(container.read(authNotifierProvider).isAuthenticated,false);
    await container.read(authInitializationProvider.future);
    expect(container.read(authNotifierProvider).isAuthenticated,true);
    expect(container.read(authNotifierProvider).user?.id,17);
  });
}
