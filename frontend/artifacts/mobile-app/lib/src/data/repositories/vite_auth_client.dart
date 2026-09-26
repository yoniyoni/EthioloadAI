/// vite_auth_client.dart
///
/// Talks to the Vite dev-server shared auth endpoints (port 5173):
///   POST /mailer/auth/register
///   POST /mailer/auth/login
///   GET  /mailer/auth/me
///
/// Used when Spring Boot (port 8080) is unavailable so any account registered
/// in a browser is also accessible from the Flutter web preview.

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show kIsWeb, debugPrint;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../models/models.dart';
import '../api/api_client.dart';

/// Base URL of the Vite server.
/// In web mode the Flutter app is served from the same origin (port 5173),
/// so relative paths work. On native we target the host machine directly.
String get _viteBase {
  if (kIsWeb) return ''; // same origin
  return 'http://127.0.0.1:5173';
}

class ViteAuthClient {
  final FlutterSecureStorage _storage;
  late final Dio _dio;

  ViteAuthClient(this._storage) {
    _dio = Dio(BaseOptions(
      connectTimeout: const Duration(seconds: 6),
      receiveTimeout: const Duration(seconds: 8),
      validateStatus: (_) => true, // handle status codes manually
    ));
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /// Map a Vite user object to the shape User.fromJson() expects.
  Map<String, dynamic> _normalizeUser(Map<String, dynamic> u) => {
        'id': u['id'],
        'full_name': u['name'] ?? u['fullName'] ?? '',
        'email': u['email'] ?? '',
        'phone': u['phone'] ?? '',
        'role': u['role'] ?? 'shipper',
        'verification_status': u['isVerified'] == true,
        'is_active': u['isActive'] != false,
      };

  Future<AuthResponse> _post(String path, Map<String, dynamic> body) async {
    debugPrint('[ViteAuth] POST $_viteBase$path');
    final res = await _dio.post<Map<String, dynamic>>(
      '$_viteBase$path',
      data: body,
      options: Options(contentType: 'application/json'),
    );
    final data = res.data ?? {};
    if (res.statusCode != 200 && res.statusCode != 201) {
      throw ApiException(
        message: data['message']?.toString() ?? 'Vite auth error ${res.statusCode}',
        statusCode: res.statusCode,
      );
    }
    final token = data['token'] as String;
    await _storage.write(key: 'auth_token', value: token);
    return AuthResponse(
      token: token,
      user: User.fromJson(_normalizeUser(data['user'] as Map<String, dynamic>)),
    );
  }

  // ── Public API ────────────────────────────────────────────────────────────

  Future<AuthResponse> register({
    required String fullName,
    required String email,
    required String phone,
    required String password,
    required String role,
  }) =>
      _post('/mailer/auth/register', {
        'fullName': fullName,
        'email': email,
        'phone': phone,
        'password': password,
        'role': role,
      });

  Future<AuthResponse> login({
    required String identifier,
    required String password,
  }) =>
      _post('/mailer/auth/login', {
        'identifier': identifier,
        'password': password,
      });

  Future<User?> me(String token) async {
    try {
      final res = await _dio.get<Map<String, dynamic>>(
        '$_viteBase/mailer/auth/me',
        options: Options(headers: {'Authorization': 'Bearer $token'}),
      );
      if (res.statusCode != 200 || res.data == null) return null;
      return User.fromJson(_normalizeUser(res.data!));
    } catch (_) {
      return null;
    }
  }

  /// Returns true when the Vite server is reachable.
  Future<bool> isAvailable() async {
    try {
      final res = await _dio.get<dynamic>(
        '$_viteBase/mailer/auth/me',
        options: Options(
          sendTimeout: const Duration(seconds: 3),
          receiveTimeout: const Duration(seconds: 3),
          validateStatus: (_) => true,
        ),
      );
      // Any HTTP response means the server is up (401 = up but no token)
      return (res.statusCode ?? 0) > 0;
    } catch (_) {
      return false;
    }
  }
}
