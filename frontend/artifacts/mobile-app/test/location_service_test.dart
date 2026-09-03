import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator/geolocator.dart';
import 'package:geolocator_platform_interface/geolocator_platform_interface.dart';
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

  test('requests permission before failing when location access is denied',
      () async {
    final fakePlatform = FakeGeolocatorPlatform();
    GeolocatorPlatform.instance = fakePlatform;

    final position = await LocationService.getCurrentPosition();

    expect(position, isNull);
    expect(fakePlatform.requestPermissionCallCount, 1);
  });
}
