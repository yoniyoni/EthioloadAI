import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:logger/logger.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';

class _NearbyVehiclesAdapter implements HttpClientAdapter {
  final List<RequestOptions> requests = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final payload = options.path == '/freight/16'
        ? {
            'id': 16,
            'userId': 7,
            'pickupLocation': 'Addis Ababa',
            'destination': 'Adama',
            'materialType': 'grain',
            'weightTons': 2,
            'urgencyLevel': 'normal',
            'status': 'posted',
            'pickupLatitude': 8.98,
            'pickupLongitude': 38.75,
          }
        : [
            {
              'id': 23,
              'userId': 88,
              'truckType': 'flatbed',
              'vehicleCategory': 'HEAVY',
              'plateNumber': 'ET-12345',
              'capacity': 12,
              'currentCity': 'Addis Ababa',
              'latitude': 8.99,
              'longitude': 38.76,
              'availabilityStatus': 'available',
              'approvalStatus': 'APPROVED',
              'rating': 4.5,
            },
          ];
    return ResponseBody.fromString(
      jsonEncode(payload),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json']
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Dio dio;
  late _NearbyVehiclesAdapter adapter;
  late CargoRepository repository;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'auth_token': 'contract-test-token'});
    adapter = _NearbyVehiclesAdapter();
    dio = Dio(BaseOptions(baseUrl: 'https://api.example.test/api'))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));
    repository = CargoRepository(ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    ));
  });

  tearDown(() => dio.close());

  test('nearby search uses the authenticated Spring vehicle contract',
      () async {
    final vehicles = await repository.nearbyVehiclesByLocation(8.98, 38.75);

    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.method, 'GET');
    expect(adapter.requests.single.path, '/vehicles/nearby');
    expect(adapter.requests.single.queryParameters, {
      'latitude': 8.98,
      'longitude': 38.75,
      'radius': 10.0,
    });
    expect(adapter.requests.single.headers['Authorization'],
        'Bearer contract-test-token');
    expect(vehicles, hasLength(1));
    expect(vehicles.single.id, 23);
    expect(vehicles.single.userId, 88);
    expect(vehicles.single.truckType, 'flatbed');
    expect(vehicles.single.vehicleCategory, 'HEAVY');
    expect(vehicles.single.plateNumber, 'ET-12345');
    expect(vehicles.single.latitude, 8.99);
    expect(vehicles.single.longitude, 38.76);
    expect(vehicles.single.availabilityStatus, 'available');
    expect(vehicles.single.approvalStatus, 'APPROVED');
    expect(vehicles.single.rating, 4.5);
  });

  test('cargo lookup uses pickup coordinates before nearby search', () async {
    final vehicles = await repository.nearbyVehicles(16);

    expect(adapter.requests.map((request) => request.path),
        ['/freight/16', '/vehicles/nearby']);
    expect(adapter.requests.first.method, 'GET');
    expect(adapter.requests.first.headers['Authorization'],
        'Bearer contract-test-token');
    expect(adapter.requests.last.queryParameters, {
      'latitude': 8.98,
      'longitude': 38.75,
      'radius': 10.0,
    });
    expect(vehicles.single.id, 23);
  });
}
