import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:geolocator_platform_interface/geolocator_platform_interface.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/services/location_service.dart';

class FakeGeolocatorPlatform extends GeolocatorPlatform {
  LocationPermission permission;
  int requestPermissionCallCount = 0;

  FakeGeolocatorPlatform({this.permission = LocationPermission.denied});

  @override
  Future<LocationPermission> checkPermission() async => permission;

  @override
  Future<LocationPermission> requestPermission() async {
    requestPermissionCallCount++;
    return permission = LocationPermission.denied;
  }

  @override
  Future<bool> isLocationServiceEnabled() async => true;

  @override
  Future<Position> getCurrentPosition({
    LocationAccuracy desiredAccuracy = LocationAccuracy.best,
    bool forceAndroidLocationManager = false,
    Duration? timeLimit,
    LocationSettings? locationSettings,
  }) async {
    throw StateError(
        'getCurrentPosition should not be called when permission is denied');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Trip activeTrip(int id, String serviceType) => Trip(
        id: id,
        bookingId: id + 100,
        tripStatus: 'ONGOING',
        serviceType: serviceType,
      );

  TripPage tripPage(List<Trip> trips, {bool hasNext = false}) => TripPage(
        content: trips,
        number: 0,
        size: 3,
        hasNext: hasNext,
        totalElements: trips.length,
      );

  test('no ongoing trips retain the 25-minute global cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(tripPage(const [])),
      const Duration(minutes: 25),
    );
  });

  test('intercity trips retain the 25-minute global cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(
          tripPage([activeTrip(1, 'INTERCITY')])),
      const Duration(minutes: 25),
    );
  });

  test('intracity trips use the 5-minute global cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(
          tripPage([activeTrip(1, 'INTRACITY')])),
      const Duration(minutes: 5),
    );
  });

  test('multiple intercity trips use the 25-minute global cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(tripPage([
        activeTrip(1, 'INTERCITY'),
        activeTrip(2, 'INTERCITY'),
        activeTrip(3, 'INTERCITY'),
      ])),
      const Duration(minutes: 25),
    );
  });

  test('mixed service types use the shortest required global cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(tripPage([
        activeTrip(1, 'INTERCITY'),
        activeTrip(2, 'INTRACITY'),
      ])),
      const Duration(minutes: 5),
    );
  });

  test('multiple intracity trips use the 5-minute global cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(tripPage([
        activeTrip(1, 'INTRACITY'),
        activeTrip(2, 'INTRACITY'),
        activeTrip(3, 'INTRACITY'),
      ])),
      const Duration(minutes: 5),
    );
  });

  test('incomplete active-trip page does not calculate a cadence', () {
    expect(
      LocationService.globalReportingIntervalForTrips(
          tripPage([activeTrip(1, 'INTRACITY')], hasNext: true)),
      isNull,
    );
  });

  test('does not request permission before returning null when location access is denied',
      () async {
    final fakePlatform = FakeGeolocatorPlatform();
    GeolocatorPlatform.instance = fakePlatform;

    final position = await LocationService.getCurrentPosition();

    expect(position, isNull);
    expect(fakePlatform.requestPermissionCallCount, 0);
  });
}
