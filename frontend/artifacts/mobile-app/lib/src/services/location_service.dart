import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../data/api/api_client.dart';
import '../data/models/models.dart';
import '../data/repositories/repositories.dart';

class LocationService {
  static const Duration globalDefaultInterval = Duration(minutes: 25);
  static const Duration globalIntracityInterval = Duration(minutes: 5);

  static Timer? _timer;
  static int _trackingGeneration = 0;
  static bool _batteryWarnShown = false;

  /// Returns true the first time an intracity tracking session starts,
  /// then false on every subsequent call. Caller should show the battery
  /// warning snackbar when this returns true.
  static bool consumeBatteryWarning() {
    if (_batteryWarnShown) return false;
    _batteryWarnShown = true;
    return true;
  }

  static Duration? globalReportingIntervalForTrips(TripPage page) {
    if (page.hasNext) return null;

    final hasIntracityOngoingTrip = page.content.any((trip) =>
        trip.tripStatus.toUpperCase() == 'ONGOING' &&
        trip.serviceType?.toUpperCase() == 'INTRACITY');
    return hasIntracityOngoingTrip
        ? globalIntracityInterval
        : globalDefaultInterval;
  }

  // Call when driver logs in or a trip starts.
  // serviceType: 'intercity' → 25-min interval (default)
  //              'intracity' → 5-min interval
  static Future<bool> startTracking(
    WidgetRef ref, {
    String serviceType = 'intercity',
    int? tripId,
  }) async {
    stopTracking();
    final generation = _trackingGeneration;
    final vehicles = ref.read(vehicleRepositoryProvider);
    final trips = ref.read(tripRepositoryProvider);
    final authorizedVehicleId = tripId == null
        ? null
        : await trips.getAuthorizedVehicleId(tripId);
    if (generation != _trackingGeneration) return false;
    final interval = tripId != null
        ? const Duration(seconds: 30)
        : serviceType == 'intracity'
            ? globalIntracityInterval
            : globalDefaultInterval;

    final position = await _requestAndGet();
    if (generation != _trackingGeneration) return false;
    if (position != null) {
      await _push(
        vehicles,
        trips,
        position,
        tripId,
        authorizedVehicleId,
      );
    }
    if (generation != _trackingGeneration) return false;
    _timer = Timer.periodic(interval, (_) async {
      final pos = await getCurrentPosition();
      if (pos != null && generation == _trackingGeneration) {
        await _push(
          vehicles,
          trips,
          pos,
          tripId,
          authorizedVehicleId,
        );
      }
    });
    return position != null;
  }

  static void stopTracking() {
    _trackingGeneration++;
    _timer?.cancel();
    _timer = null;
  }

  // Returns null if permission denied or GPS unavailable. Never throws.
  static Future<Position?> getCurrentPosition() async {
    try {
      final perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied ||
          perm == LocationPermission.deniedForever) {
        return null;
      }
      return await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.medium,
        timeLimit: const Duration(seconds: 15),
      );
    } catch (_) {
      return null;
    }
  }

  static Future<void> _push(
    VehicleRepository vehicles,
    TripRepository trips,
    Position pos,
    int? tripId,
    int? vehicleId,
  ) async {
    try {
      if (tripId == null) {
        await vehicles.reportDriverLocation(pos.latitude, pos.longitude);
      } else if (vehicleId != null) {
        await trips.reportGpsLocation(
          tripId: tripId,
          vehicleId: vehicleId,
          latitude: pos.latitude,
          longitude: pos.longitude,
          timestamp: DateTime.now().toUtc(),
          speed: pos.speed >= 0 ? pos.speed * 3.6 : null,
          accuracy: pos.accuracy,
          heading: pos.heading >= 0 ? pos.heading : null,
        );
      }
    } on ApiException catch (error) {
      debugPrint(
        'GPS location update failed (${error.statusCode}): ${error.message}',
      );
    }
  }

  static Future<Position?> _requestAndGet() async {
    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) return null;

      LocationPermission perm = await Geolocator.checkPermission();
      if (perm == LocationPermission.denied) {
        perm = await Geolocator.requestPermission();
      }
      if (perm == LocationPermission.denied ||
          perm == LocationPermission.deniedForever) {
        return null;
      }

      return await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.medium,
        timeLimit: const Duration(seconds: 15),
      );
    } catch (_) {
      return null;
    }
  }

  // Show a dialog explaining why location is needed.
  // Returns true if user tapped "Enable" and permission was granted.
  static Future<bool> showPermissionDialog(BuildContext context) async {
    final granted = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Enable Location / ቦታ ያብሩ'),
        content: const Text(
          'Location helps shippers find you and shows you the nearest cargo.\n\n'
          'ቦታዎ ጭነቶችን ለማየትና ቅርብ ስራ ለማግኘት ያስፈልጋል።',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Not now'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Enable / አብራ'),
          ),
        ],
      ),
    );
    if (granted != true) return false;

    final perm = await Geolocator.requestPermission();
    return perm == LocationPermission.always ||
        perm == LocationPermission.whileInUse;
  }
}
