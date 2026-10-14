/// mock_auth_store.dart
///
/// Local offline auth store for the EthioLoad AI mobile app.
///
/// When the Spring Boot backend is not reachable (running as a static Flutter
/// web preview, emulator with no server, etc.) registration and login fall
/// back to this in-process store. Accounts are persisted in FlutterSecureStorage
/// so they survive hot-restarts and page refreshes in web mode.
///
/// This is intentionally simple (no real hashing, no JWT) — it exists purely
/// to let the UI be exercised without a running backend.
library;

import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../models/models.dart';
import '../api/api_client.dart';

const _kMockUsersKey = 'ethioload_mock_users';

class MockAuthStore {
  final FlutterSecureStorage _storage;
  MockAuthStore(this._storage);

  // ── Persistence ─────────────────────────────────────────────────────────

  Future<List<Map<String, dynamic>>> _loadUsers() async {
    try {
      final raw = await _storage.read(key: _kMockUsersKey);
      if (raw == null) return [];
      final list = jsonDecode(raw) as List<dynamic>;
      return list.cast<Map<String, dynamic>>();
    } catch (_) {
      return [];
    }
  }

  Future<void> _saveUsers(List<Map<String, dynamic>> users) async {
    await _storage.write(key: _kMockUsersKey, value: jsonEncode(users));
  }

  // ── Token helper ─────────────────────────────────────────────────────────

  String _makeToken(int userId) {
    final ts = DateTime.now().millisecondsSinceEpoch;
    return 'mock.${base64Encode(utf8.encode('$userId'))}.$ts';
  }

  // ── Register ─────────────────────────────────────────────────────────────

  Future<AuthResponse> register({
    required String fullName,
    required String email,
    required String phone,
    required String password,
    required String role,
  }) async {
    final users = await _loadUsers();
    final normEmail = email.toLowerCase().trim();
    final normPhone = phone.trim();

    if (users.any((u) => u['email'] == normEmail)) {
      throw ApiException(message: 'An account with this email already exists.');
    }
    if (normPhone.isNotEmpty &&
        users.any((u) => u['phone'] == normPhone)) {
      throw ApiException(
          message: 'An account with this phone number already exists.');
    }

    final id = DateTime.now().millisecondsSinceEpoch;
    final newUser = {
      'id': id,
      'full_name': fullName,
      'email': normEmail,
      'phone': normPhone,
      'role': role,
      'password_hash': base64Encode(utf8.encode(password)), // demo only
      'verification_status': false,
      'is_active': true,
    };

    users.add(newUser);
    await _saveUsers(users);

    final token = _makeToken(id);
    await _storage.write(key: 'auth_token', value: token);

    return AuthResponse(
      token: token,
      user: User.fromJson(newUser),
    );
  }

  // ── Login ─────────────────────────────────────────────────────────────────

  Future<AuthResponse> login({
    required String identifier,
    required String password,
  }) async {
    final users = await _loadUsers();
    final norm = identifier.toLowerCase().trim();

    final userMap = users.firstWhere(
      (u) => u['email'] == norm || u['phone'] == norm,
      orElse: () => {},
    );

    if (userMap.isEmpty) {
      throw ApiException(
          message: 'No account found with that email or phone number.');
    }

    final stored = userMap['password_hash'] as String? ?? '';
    if (stored != base64Encode(utf8.encode(password))) {
      throw ApiException(message: 'Incorrect password.');
    }

    final id = userMap['id'] as int;
    final token = _makeToken(id);
    await _storage.write(key: 'auth_token', value: token);

    return AuthResponse(
      token: token,
      user: User.fromJson(userMap),
    );
  }

  // ── Me (restore session after cold start) ────────────────────────────────

  Future<User?> tryRestoreUser(String token) async {
    try {
      final users = await _loadUsers();
      // Extract the userId we encoded in the token: mock.<base64(id)>.<ts>
      final parts = token.split('.');
      if (parts.length < 2) return null;
      final idStr = utf8.decode(base64Decode(parts[1]));
      final id = int.tryParse(idStr);
      if (id == null) return null;
      final userMap = users.firstWhere(
        (u) => u['id'] == id,
        orElse: () => {},
      );
      if (userMap.isEmpty) return null;
      return User.fromJson(userMap);
    } catch (_) {
      return null;
    }
  }
}
