import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:logger/logger.dart';

class _TrackingAdapter implements HttpClientAdapter {
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final response = options.path == '/v1/tracking/trips/73/vehicle'
        ? {'tripId': 73, 'authorizedVehicleId': 52}
        : {
            'success': true,
            'tripId': 73,
            'vehicleId': 52,
          };
    return ResponseBody.fromString(
      jsonEncode(response),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Dio dio;
  late _TrackingAdapter adapter;
  late TripRepository repository;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'auth_token': 'tracking-token'});
    adapter = _TrackingAdapter();
    dio = Dio(BaseOptions(
      baseUrl: 'https://api.example.test/api',
      validateStatus: (status) => status != null && status < 500,
    ))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));
    repository = TripRepository(ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    ));
  });

  tearDown(() => dio.close());

  test('resolves the server-authorized vehicle for a trip', () async {
    final vehicleId = await repository.getAuthorizedVehicleId(73);

    expect(vehicleId, 52);
    expect(adapter.requests.single.method, 'GET');
    expect(adapter.requests.single.path, '/v1/tracking/trips/73/vehicle');
  });

  test('reports GPS using the Spring tracking path and required contract', () async {
    await repository.reportGpsLocation(
      tripId: 73,
      vehicleId: 52,
      latitude: 9.03,
      longitude: 38.75,
      timestamp: DateTime.utc(2026, 10, 13, 12, 30, 45, 123),
      speed: 36,
      accuracy: 8,
      heading: 180,
    );

    final request = adapter.requests.single;
    final rawBody = request.data;
    final body = rawBody is String
        ? jsonDecode(rawBody) as Map<String, dynamic>
        : Map<String, dynamic>.from(rawBody as Map);
    expect(request.method, 'POST');
    expect(request.path, '/v1/tracking/trips/73/location');
    expect(body, {
      'vehicleId': 52,
      'latitude': 9.03,
      'longitude': 38.75,
      'timestamp': '2026-10-13T12:30:45Z',
      'speed': 36,
      'accuracy': 8,
      'heading': 180,
    });
  });
}
