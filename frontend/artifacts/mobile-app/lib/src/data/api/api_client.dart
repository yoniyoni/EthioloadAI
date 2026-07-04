import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:logger/logger.dart';

// ── Base URL selection ─────────────────────────────────────────────────────
// Override at build time with: --dart-define=API_BASE_URL=http://your-server/api
// Falls back to the Herd local server when no override is provided.
const String _kApiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://backend.test/api';
  return 'http://backend.test/api';
}

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
    final token = await secureStorage.read(key: 'auth_token');
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    options.headers['Accept'] = 'application/json';
    options.headers['Content-Type'] = 'application/json';
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

/// Thin wrapper around Dio that stores / retrieves the Sanctum token and
/// provides typed helper methods.
class ApiClient {
  final Dio dio;
  final Logger logger;
  final FlutterSecureStorage secureStorage;

  ApiClient({
    required this.dio,
    required this.logger,
    required this.secureStorage,
  });

  // ── Token helpers ─────────────────────────────────────────────────────────

  Future<void> saveToken(String token) =>
      secureStorage.write(key: 'auth_token', value: token);

  Future<String?> getToken() => secureStorage.read(key: 'auth_token');

  Future<void> deleteToken() => secureStorage.delete(key: 'auth_token');

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
        final errors = data['errors'] as Map;
        final first = errors.values.first;
        if (first is List && first.isNotEmpty) return first[0].toString();
      }
      if (data.containsKey('message')) return data['message'].toString();
    }
    return 'Request failed (${response?.statusCode})';
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
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      throw ApiException(message: e.message ?? 'Network error', statusCode: e.response?.statusCode);
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
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      throw ApiException(message: e.message ?? 'Network error', statusCode: e.response?.statusCode);
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
      throw ApiException(message: _extractError(response), statusCode: response.statusCode);
    } on DioException catch (e) {
      throw ApiException(message: e.message ?? 'Network error', statusCode: e.response?.statusCode);
    }
  }

  Future<void> delete(String endpoint) async {
    try {
      final response = await dio.delete(endpoint);
      if (response.statusCode != 200 && response.statusCode != 204) {
        throw ApiException(message: _extractError(response), statusCode: response.statusCode);
      }
    } on DioException catch (e) {
      throw ApiException(message: e.message ?? 'Network error', statusCode: e.response?.statusCode);
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

final apiClientProvider = Provider<ApiClient>((ref) {
  final dio = ref.read(dioProvider); // read, not watch — Dio is a singleton
  return ApiClient(
    dio: dio,
    logger: Logger(),
    secureStorage: const FlutterSecureStorage(),
  );
});
