import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:logger/logger.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';

class _FleetAdapter implements HttpClientAdapter {
  final List<RequestOptions> requests = [];
  final int? driverListStatus;

  _FleetAdapter({this.driverListStatus});

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);

    if (options.path == '/fleet/dashboard') {
      return _jsonResponse({
        'fleetOwnerId': 7,
        'driverCount': 1,
        'vehicleCount': 1,
        'activeBookingCount': 3,
        'drivers': [
          {
            'id': 41,
            'fullName': 'Test Driver',
            'phone': '+251911111111',
            'email': 'driver@example.test',
            'isActive': true,
            'verificationStatus': true,
          },
        ],
        'vehicles': [
          {
            'id': 52,
            'plateNumber': 'ET-12345',
            'truckType': 'flatbed',
            'vehicleCategory': 'heavy',
            'capacity': 12,
            'currentCity': 'Addis Ababa',
            'availabilityStatus': 'available',
            'assignedDriverId': 41,
          },
        ],
        'activeBookings': [],
        'page': 0,
        'pageSize': 20,
        'hasNext': false,
      });
    }

    if (options.path == '/fleet/drivers' &&
        options.method == 'GET' &&
        driverListStatus != null) {
      return _jsonResponse(
        {'message': 'Fleet access denied'},
        status: driverListStatus!,
      );
    }

    if (options.path == '/fleet/drivers' && options.method == 'GET') {
      return _jsonResponse([
        {
          'id': 41,
          'fullName': 'Test Driver',
          'phone': '+251911111111',
          'email': 'driver@example.test',
          'isActive': true,
          'verificationStatus': true,
        },
      ]);
    }

    if (options.path == '/fleet/drivers' && options.method == 'POST') {
      return _jsonResponse(
        {
          'id': 41,
          'fullName': 'Test Driver',
          'phone': '+251911111111',
          'email': 'driver@example.test',
          'isActive': false,
          'verificationStatus': false,
        },
        status: 201,
      );
    }

    if (options.path == '/fleet/vehicles' && options.method == 'GET') {
      return _jsonResponse([
        {
          'id': 52,
          'plateNumber': 'ET-12345',
          'truckType': 'flatbed',
          'vehicleCategory': 'heavy',
          'capacity': 12,
          'currentCity': 'Addis Ababa',
          'availabilityStatus': 'available',
          'assignedDriverId': 41,
        },
      ]);
    }

    if (options.path == '/vehicles/52' && options.method == 'GET') {
      return _jsonResponse({
        'id': 52,
        'latitude': 9.03,
        'longitude': 38.75,
        'lastLocationAt': '2026-10-13T12:30:00',
      });
    }

    if (options.path == '/fleet/drivers/41' && options.method == 'DELETE') {
      return ResponseBody.fromString('', 204);
    }

    return _jsonResponse({'message': 'Unexpected request'}, status: 404);
  }

  ResponseBody _jsonResponse(Object body, {int status = 200}) =>
      ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Dio dio;
  late _FleetAdapter adapter;
  late FleetRepository repository;
  late VehicleRepository vehicleRepository;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'auth_token': 'fleet-test-token'});
    adapter = _FleetAdapter();
    dio = Dio(BaseOptions(
      baseUrl: 'https://api.example.test/api',
      validateStatus: (status) => status != null && status < 500,
    ))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));
    repository = FleetRepository(ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    ));
    vehicleRepository = VehicleRepository(ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    ));
  });

  tearDown(() => dio.close());

  test('dashboard uses bounded pagination and decodes the legacy response', () async {
    final dashboard = await repository.getDashboard(page: 2, size: 20);

    expect(adapter.requests.single.path, '/fleet/dashboard');
    expect(adapter.requests.single.method, 'GET');
    expect(adapter.requests.single.queryParameters, {'page': 2, 'size': 20});
    expect(dashboard['fleetOwnerId'], 7);
    expect(dashboard['drivers'].single['fullName'], 'Test Driver');
    expect(dashboard['vehicles'].single['assignedDriverId'], 41);
    expect(dashboard['hasNext'], false);
  });

  test('driver and vehicle lists use their legacy endpoints',
      () async {
    final drivers = await repository.listDrivers();
    final vehicles = await repository.listVehicles();

    expect(
      adapter.requests.map((request) => (request.method, request.path)),
      [('GET', '/fleet/drivers'), ('GET', '/fleet/vehicles')],
    );
    expect(drivers.single, {
      'id': 41,
      'fullName': 'Test Driver',
      'phone': '+251911111111',
      'email': 'driver@example.test',
      'isActive': true,
      'verificationStatus': true,
    });
    expect(vehicles.single['plateNumber'], 'ET-12345');
    expect(vehicles.single['assignedDriverId'], 41);
  });

  test('adding a driver sends the user ID and decodes the created driver',
      () async {
    final driver = await repository.addDriver(41);
    final request = adapter.requests.single;
    final requestBody = request.data is String
        ? jsonDecode(request.data as String)
        : request.data;

    expect(request.method, 'POST');
    expect(request.path, '/fleet/drivers');
    expect(requestBody, {'driverId': 41});
    expect(driver['id'], 41);
    expect(driver['fullName'], 'Test Driver');
  });

  test('removing a driver uses the fleet unlink endpoint', () async {
    await repository.removeDriver(41);

    expect(adapter.requests.single.method, 'DELETE');
    expect(adapter.requests.single.path, '/fleet/drivers/41');
  });

  test('fleet location snapshots use the authorized vehicle detail endpoint',
      () async {
    final snapshot = await vehicleRepository.getLocationSnapshot(52);

    expect(adapter.requests.single.method, 'GET');
    expect(adapter.requests.single.path, '/vehicles/52');
    expect(snapshot['latitude'], 9.03);
    expect(snapshot['longitude'], 38.75);
    expect(snapshot['lastLocationAt'], '2026-10-13T12:30:00');
  });

  test('fleet authorization errors remain explicit', () async {
    dio.close();
    adapter = _FleetAdapter(driverListStatus: 403);
    dio = Dio(BaseOptions(
      baseUrl: 'https://api.example.test/api',
      validateStatus: (status) => status != null && status < 500,
    ))
      ..httpClientAdapter = adapter
      ..interceptors.add(ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ));
    repository = FleetRepository(ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    ));

    await expectLater(
      repository.listDrivers(),
      throwsA(
        isA<ApiException>()
            .having((error) => error.statusCode, 'statusCode', 403)
            .having((error) => error.message, 'message', 'Fleet access denied'),
      ),
    );
  });
}
