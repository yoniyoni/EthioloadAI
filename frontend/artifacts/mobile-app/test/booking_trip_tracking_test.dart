import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:logger/logger.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:ethioload_ai/src/features/delivery/delivery_repository.dart';

class _BookingTripAdapter implements HttpClientAdapter {
  final Map<String, dynamic> payload;
  String? requestedPath;
  String? requestedMethod;
  Map<String, dynamic>? requestedQuery;

  _BookingTripAdapter(this.payload);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requestedPath = options.path;
    requestedMethod = options.method;
    requestedQuery = options.queryParameters;
    return ResponseBody.fromString(
      jsonEncode(payload),
      200,
      headers: {Headers.contentTypeHeader: ['application/json']},
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('booking trip lookup uses the selected booking endpoint and parses exact trip identity',
      () async {
    const response = {
      'bookingId': 41,
      'freightId': 9,
      'bookingStatus': 'accepted',
      'tripId': 73,
      'tripStatus': 'ongoing',
      'tripType': 'multi_stop',
      'serviceType': 'intracity',
    };
    final adapter = _BookingTripAdapter(response);
    final dio = Dio(BaseOptions(baseUrl: 'https://api.example.test/api'))
      ..httpClientAdapter = adapter;
    final api = ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    );

    final resolved = await BookingRepository(api).getTripForBooking(41);

    expect(adapter.requestedPath, '/bookings/41/trip');
    expect(resolved.bookingId, 41);
    expect(resolved.freightId, 9);
    expect(resolved.tripId, 73);
    expect(resolved.tripStatus, 'ongoing');
    expect(resolved.tripType, 'multi_stop');
    expect(resolved.serviceType, 'intracity');
    dio.close();
  });

  test('no-trip lookup is represented without inventing a trip id', () async {
    const response = {
      'bookingId': 42,
      'freightId': 9,
      'bookingStatus': 'accepted',
      'tripId': null,
      'tripStatus': null,
      'tripType': null,
      'serviceType': 'intercity',
    };
    final dio = Dio(BaseOptions(baseUrl: 'https://api.example.test/api'))
      ..httpClientAdapter = _BookingTripAdapter(response);
    final api = ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    );

    final resolved = await BookingRepository(api).getTripForBooking(42);

    expect(resolved.tripId, isNull);
    expect(resolved.tripStatus, isNull);
    dio.close();
  });

  test('starting a trip calls the dedicated Spring start endpoint', () async {
    final adapter = _BookingTripAdapter({
      'id': 73,
      'tripStatus': 'ONGOING',
      'startTime': '2026-03-18T10:30:00',
    });
    final dio = Dio(BaseOptions(baseUrl: 'https://api.example.test/api'))
      ..httpClientAdapter = adapter;
    final api = ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    );

    final result = await DeliveryRepository(api: api, logger: Logger(level: Level.off))
        .startTrip(73);

    expect(adapter.requestedMethod, 'POST');
    expect(adapter.requestedPath, '/trips/73/start');
    expect(result['id'], 73);
    expect(result['startTime'], isNotNull);
    dio.close();
  });

  test('trip detail parsing retains cargo identity, booking identity and service type', () {
    final trip = Trip.fromJson({
      'id': 73,
      'tripStatus': 'ONGOING',
      'tripType': 'MULTI_STOP',
      'booking': {'id': 41, 'status': 'ACCEPTED'},
      'cargo': {'id': 9, 'serviceType': 'INTRACITY'},
    });

    expect(trip.id, 73);
    expect(trip.bookingId, 41);
    expect(trip.bookingStatus, 'accepted');
    expect(trip.cargoId, 9);
    expect(trip.tripStatus, 'ONGOING');
    expect(trip.tripType, 'MULTI_STOP');
    expect(trip.serviceType, 'intracity');
  });

  test('ongoing trip query is bounded and uses deterministic ordering', () async {
    final adapter = _BookingTripAdapter({
      'content': [
        {
          'id': 73,
          'tripStatus': 'ONGOING',
          'booking': {'id': 41},
          'cargo': {'id': 9, 'serviceType': 'INTRACITY'},
        },
      ],
      'number': 0,
      'size': 3,
      'last': true,
      'totalElements': 1,
    });
    final dio = Dio(BaseOptions(baseUrl: 'https://api.example.test/api'))
      ..httpClientAdapter = adapter;
    final api = ApiClient(
      dio: dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
    );

    final page = await TripRepository(api).getOngoingTripsForCurrentDriver();

    expect(adapter.requestedPath, '/trips');
    expect(adapter.requestedQuery, {
      'status': 'ONGOING',
      'page': 0,
      'size': 3,
      'sortBy': 'id',
      'sortDir': 'asc',
    });
    expect(page.content.single.serviceType, 'intracity');
    expect(page.hasNext, isFalse);
    expect(
      TripPage.fromJson({
        'content': [],
        'number': 0,
        'size': 3,
        'last': false,
        'totalElements': 4,
      }).hasNext,
      isTrue,
    );
    expect(
      TripPage.fromJson({'content': [], 'number': 0, 'size': 3}).hasNext,
      isTrue,
    );
    dio.close();
  });
}
