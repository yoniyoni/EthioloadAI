import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb, defaultTargetPlatform, TargetPlatform, kReleaseMode;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:logger/logger.dart';

// ── Base URL selection ─────────────────────────────────────────────────────
// Override at build time with: --dart-define=API_BASE_URL=http://your-server/api
// Falls back to the Spring Boot local server (port 8080 with context-path /api).
const String _kApiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

String get kBaseUrl {
  return resolveApiBaseUrl(override: _kApiBaseUrlOverride, web: kIsWeb,
    platform: defaultTargetPlatform, release: kReleaseMode, origin: Uri.base);
}

String resolveApiBaseUrl({required String override, required bool web,
  required TargetPlatform platform, required bool release, required Uri origin}) {
  if (override.isNotEmpty) {
    final uri = Uri.parse(override);
    if (!['http', 'https'].contains(uri.scheme) || uri.host.isEmpty || uri.hasQuery || uri.hasFragment || uri.userInfo.isNotEmpty) {
      throw ArgumentError('API_BASE_URL must be an HTTP(S) URL including the API context path.');
    }
    return override.replaceFirst(RegExp(r'/+$'), '');
  }
  if (web) return origin.resolve('/api').toString();
  if (release) throw StateError('Configure API_BASE_URL for a native release build.');
  return platform == TargetPlatform.android ? 'http://10.0.2.2:8080/api' : 'http://127.0.0.1:8080/api';
}

String get kWebSocketBaseUrl =>
    kBaseUrl.replaceFirst(RegExp(r'^http'), 'ws');

class ApiInterceptor extends Interceptor {
  final Logger logger;
  final FlutterSecureStorage secureStorage;

  ApiInterceptor({required this.logger, required this.secureStorage});

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    logger.i('→ ${options.method} ${options.path}');
    final token = (await secureStorage.read(key: 'auth_token'))?.trim();
    if (token != null &&
        token.isNotEmpty &&
        token != '******' &&
        !['/auth/login', '/auth/register'].contains(options.path)) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    options.headers['Accept'] = 'application/json';
    if (options.data is! FormData) {
      options.headers['Content-Type'] = 'application/json';
    }
    handler.next(options);
  }

  @override
  Future<void> onResponse(
    Response response,
    ResponseInterceptorHandler handler,
  ) async {
    logger.i('← ${response.statusCode} ${response.requestOptions.path}');
    handler.next(response);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    logger.e('✗ ${err.response?.statusCode} ${err.requestOptions.path}: ${err.message}');
    handler.next(err);
  }
}

final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: kBaseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      validateStatus: (status) => status != null && status < 500,
    ),
  );

  const secureStorage = FlutterSecureStorage();
  final logger = Logger();
  dio.interceptors.add(ApiInterceptor(logger: logger, secureStorage: secureStorage));

  // Dispose the Dio instance when the provider is disposed
  ref.onDispose(() => dio.close());

  return dio;
});

/// Thin wrapper around Dio that stores/retrieves the Spring JWT and provides
/// typed helper methods.
class ApiClient {
  final Dio dio;
  final Logger logger;
  final FlutterSecureStorage secureStorage;
  final void Function()? onSessionCleared;

  ApiClient({
    required this.dio,
    required this.logger,
    required this.secureStorage,
    this.onSessionCleared,
  });

  // ── Token helpers ─────────────────────────────────────────────────────────

  Future<void> saveToken(String token) =>
      secureStorage.write(key: 'auth_token', value: token);

  Future<String?> getToken() => secureStorage.read(key: 'auth_token');

  Future<void> deleteToken() async {
    await secureStorage.delete(key: 'auth_token');
    onSessionCleared?.call();
  }

  // ── HTTP helpers ──────────────────────────────────────────────────────────

  /// Unwraps Laravel's `{ "data": ... }` envelope when present.
  dynamic _unwrap(dynamic responseData) {
    if (responseData is Map && responseData.containsKey('data')) {
      return responseData['data'];
    }
    return responseData;
  }

  /// Extracts the first validation error message from a 422 response.
  String _extractError(Response? response) {
    final data = response?.data;
    if (data is Map) {
      if (data.containsKey('errors')) {
        final errors = data['errors'];
        if (errors is Map && errors.isNotEmpty) {
          final first = errors.values.first;
          if (first is List && first.isNotEmpty) return first[0].toString();
          if (first != null) return first.toString();
        }
      }
      if (data.containsKey('message')) return data['message'].toString();
    }
    return 'Request failed (${response?.statusCode})';
  }

  Future<void> _clearExpiredSession(Response? response) async {
    if (response?.statusCode == 401) {
      await deleteToken();
    }
  }

  Future<T> get<T>(
    String endpoint, {
    Map<String, dynamic>? queryParameters,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await dio.get(endpoint, queryParameters: queryParameters);
      if (response.statusCode == 200) {
        final data = _unwrap(response.data);
        return fromJson != null ? fromJson(data) : data as T;
      }
      await _clearExpiredSession(response);
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await deleteToken();
      throw ApiException(message: e.response == null ? 'Unable to connect. Please try again.' : _extractError(e.response), statusCode: e.response?.statusCode);
    }
  }

  Future<T> post<T>(
    String endpoint, {
    Map<String, dynamic>? data,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await dio.post(endpoint, data: data);
      if (response.statusCode == 200 || response.statusCode == 201) {
        final body = _unwrap(response.data);
        return fromJson != null ? fromJson(body) : body as T;
      }
      await _clearExpiredSession(response);
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await deleteToken();
      throw ApiException(message: e.response == null ? 'Unable to connect. Please try again.' : _extractError(e.response), statusCode: e.response?.statusCode);
    }
  }

  Future<T> patch<T>(
    String endpoint, {
    Map<String, dynamic>? data,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await dio.patch(endpoint, data: data);
      if (response.statusCode == 200) {
        final body = _unwrap(response.data);
        return fromJson != null ? fromJson(body) : body as T;
      }
      await _clearExpiredSession(response);
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await deleteToken();
      throw ApiException(message: e.response == null ? 'Unable to connect. Please try again.' : _extractError(e.response), statusCode: e.response?.statusCode);
    }
  }

  Future<Map<String, dynamic>> upload(String endpoint, FormData data) async {
    try {
      final response = await dio.post(endpoint, data: data);
      if (response.statusCode == 200 || response.statusCode == 201) {
        return Map<String, dynamic>.from(_unwrap(response.data) as Map);
      }
      await _clearExpiredSession(response);
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      await _clearExpiredSession(e.response);
      throw ApiException(
        message: e.response == null ? 'Unable to connect. Please try again.' : _extractError(e.response),
        statusCode: e.response?.statusCode,
      );
    }
  }

  Future<T> put<T>(
    String endpoint, {
    Map<String, dynamic>? data,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await dio.put(endpoint, data: data);
      if (response.statusCode == 200 || response.statusCode == 201) {
        final body = _unwrap(response.data);
        return fromJson != null ? fromJson(body) : body as T;
      }
      await _clearExpiredSession(response);
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await deleteToken();
      throw ApiException(message: e.response == null ? 'Unable to connect. Please try again.' : _extractError(e.response), statusCode: e.response?.statusCode);
    }
  }

  Future<void> delete(String endpoint) async {
    try {
      final response = await dio.delete(endpoint);
      if (response.statusCode != 200 && response.statusCode != 204) {
        await _clearExpiredSession(response);
        throw ApiException(message: _extractError(response), statusCode: response.statusCode);
      }
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await deleteToken();
      throw ApiException(message: e.response == null ? 'Unable to connect. Please try again.' : _extractError(e.response), statusCode: e.response?.statusCode);
    }
  }
}

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  ApiException({required this.message, this.statusCode});
  @override
  String toString() => message;
}

final sessionClearedProvider = StateProvider<int>((ref) => 0);
final apiClientProvider = Provider<ApiClient>((ref) {
  final dio = ref.read(dioProvider); // read, not watch — Dio is a singleton
  return ApiClient(
    dio: dio,
    logger: Logger(),
    secureStorage: const FlutterSecureStorage(),
    onSessionCleared: () => ref.read(sessionClearedProvider.notifier).state++,
  );
});
