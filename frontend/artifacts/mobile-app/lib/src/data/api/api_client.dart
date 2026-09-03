import 'dart:async';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:logger/logger.dart';

// Dio's own connect/receive/send timeouts (set below on BaseOptions) don't
// reliably fire on every device against a server that accepts a TCP
// connection but never sends any response — observed in the field: a hung
// splash-screen auth check and a login request that both sat well past
// their configured 30s receiveTimeout. _TimeoutHttpClientAdapter enforces
// an independent, guaranteed backstop (Dart's own event-loop timer, not
// the HTTP client's internal bookkeeping) at the single point every
// request — however it's issued, dio.get() or ApiClient's wrappers —
// actually goes out, so no individual call site needs to opt in.
const Duration _kRequestTimeout = Duration(seconds: 20);

class _TimeoutHttpClientAdapter implements HttpClientAdapter {
  final HttpClientAdapter _inner;
  _TimeoutHttpClientAdapter(this._inner);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    return _inner.fetch(options, requestStream, cancelFuture).timeout(
      _kRequestTimeout,
      onTimeout: () => throw DioException(
        requestOptions: options,
        type: DioExceptionType.receiveTimeout,
        message:
            'Request timed out after ${_kRequestTimeout.inSeconds}s — no response from server.',
      ),
    );
  }

  @override
  void close({bool force = false}) => _inner.close(force: force);
}

// ── Base URL selection ─────────────────────────────────────────────────────
// Precedence: runtime override (set in-app, Profile → Server URL) > build-time
// --dart-define=API_BASE_URL=http://your-server/api > default Herd local
// server. The runtime override exists so a locally-hosted dev/test server's
// address (e.g. a LAN IP that changes with DHCP) can be updated from inside
// the already-installed app instead of requiring a full rebuild every time —
// this was previously the single most common cause of "the app stopped
// working" during device testing against a local backend.
const String _kApiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

class ServerConfig {
  static const _storageKey = 'server_base_url_override';
  static String? _runtimeOverride;

  static String? get runtimeOverride => _runtimeOverride;

  /// Call once at app startup, before runApp(), so kBaseUrl already
  /// reflects the saved override by the time anything makes its first
  /// request. Times out and falls through to defaults on failure — a
  /// hung read here must never block app startup.
  static Future<void> load(FlutterSecureStorage storage) async {
    try {
      final saved = await storage
          .read(key: _storageKey)
          .timeout(const Duration(seconds: 3), onTimeout: () => null);
      if (saved != null && saved.isNotEmpty) _runtimeOverride = saved;
    } catch (_) {
      // Keep defaults.
    }
  }

  /// Persists [url] (or clears the override if null/empty) and applies it
  /// immediately to this process — callers still need to tell the user to
  /// restart the app, since the already-constructed Dio instance's
  /// baseUrl was fixed at creation time and won't pick this up mid-session.
  static Future<void> save(FlutterSecureStorage storage, String? url) async {
    final trimmed = url?.trim();
    _runtimeOverride = (trimmed == null || trimmed.isEmpty) ? null : trimmed;
    try {
      if (_runtimeOverride == null) {
        await storage
            .delete(key: _storageKey)
            .timeout(const Duration(seconds: 5), onTimeout: () {});
      } else {
        await storage
            .write(key: _storageKey, value: _runtimeOverride)
            .timeout(const Duration(seconds: 5), onTimeout: () {});
      }
    } catch (_) {
      // Runtime override above is already applied for this session even
      // if persisting it failed — worst case it doesn't survive a restart.
    }
  }
}

String get kBaseUrl {
  final runtime = ServerConfig.runtimeOverride;
  if (runtime != null && runtime.isNotEmpty) return runtime;
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
    // This runs before every single request, including the network-level
    // timeout backstop on the adapter (_TimeoutHttpClientAdapter) — a hung
    // secure-storage read here would block every API call indefinitely
    // with that backstop never even coming into play. flutter_secure_storage
    // reads are known to hang on some real Android devices/OS versions
    // (Keystore-backed, unlike the emulator's software implementation).
    // Timing out and proceeding without a token is the safe failure mode:
    // the request just goes out unauthenticated instead of never going out.
    final token = await secureStorage
        .read(key: 'auth_token')
        .timeout(const Duration(seconds: 5), onTimeout: () => null);
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

  dio.httpClientAdapter = _TimeoutHttpClientAdapter(dio.httpClientAdapter);

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
  // Same rationale as the interceptor's read above: flutter_secure_storage
  // calls are known to hang on some real Android devices. Timing out and
  // proceeding is always the safer failure mode here than blocking forever
  // — worst case a save/delete silently doesn't persist (the in-memory
  // AuthState is set from the API response regardless, not by re-reading
  // storage, so login/logout still work within the session either way).

  Future<void> saveToken(String token) => secureStorage
      .write(key: 'auth_token', value: token)
      .timeout(const Duration(seconds: 5), onTimeout: () {});

  Future<String?> getToken() => secureStorage
      .read(key: 'auth_token')
      .timeout(const Duration(seconds: 5), onTimeout: () => null);

  Future<void> deleteToken() => secureStorage
      .delete(key: 'auth_token')
      .timeout(const Duration(seconds: 5), onTimeout: () {});

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
