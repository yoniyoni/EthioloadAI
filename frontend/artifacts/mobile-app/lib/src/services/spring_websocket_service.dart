import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../data/api/api_client.dart';

/// Native WebSocket client for Spring's JSON protocol.
class SpringWebSocketService {
  final FlutterSecureStorage _storage;
  final _messages = StreamController<Map<String, dynamic>>.broadcast();

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _subscription;
  Timer? _reconnectTimer;
  Timer? _pingTimer;
  String? _endpoint;
  String? _topic;
  bool _disposed = false;
  bool _connected = false;

  SpringWebSocketService({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  Stream<Map<String, dynamic>> get messages => _messages.stream;
  bool get isConnected => _connected;

  Future<void> connect({
    required String endpoint,
    required String topic,
  }) async {
    _endpoint = endpoint;
    _topic = topic;
    _reconnectTimer?.cancel();
    await _open();
  }

  Future<void> _open() async {
    if (_disposed || _endpoint == null || _topic == null) return;
    await _closeChannel();
    final token = await _storage.read(key: 'auth_token');
    if (token == null || token.isEmpty || _disposed) return;

    final uri = Uri.parse('${kWebSocketBaseUrl}${_endpoint!}').replace(
      queryParameters: {'token': token},
    );
    final channel = WebSocketChannel.connect(uri);
    _channel = channel;
    _connected = true;
    _subscription = channel.stream.listen(
      _handleMessage,
      onError: (_, __) => _scheduleReconnect(),
      onDone: _scheduleReconnect,
      cancelOnError: false,
    );
    _send({'type': 'SUBSCRIBE', 'data': {'topic': _topic, 'filter': null}});
    _pingTimer?.cancel();
    _pingTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _send({'type': 'PING', 'data': {}});
    });
  }

  void _handleMessage(dynamic raw) {
    if (raw is! String) return;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) {
        _messages.add(Map<String, dynamic>.from(decoded));
      }
    } catch (_) {
      // Ignore malformed frames; REST remains the authoritative fallback.
    }
  }

  void _send(Map<String, dynamic> message) {
    if (_channel != null && _connected) {
      _channel!.sink.add(jsonEncode(message));
    }
  }

  void _scheduleReconnect() {
    if (_disposed || _reconnectTimer?.isActive == true) return;
    _connected = false;
    _pingTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 3), _open);
  }

  Future<void> _closeChannel() async {
    _connected = false;
    _pingTimer?.cancel();
    await _subscription?.cancel();
    _subscription = null;
    await _channel?.sink.close();
    _channel = null;
  }

  Future<void> dispose() async {
    _disposed = true;
    _reconnectTimer?.cancel();
    await _closeChannel();
    await _messages.close();
  }
}