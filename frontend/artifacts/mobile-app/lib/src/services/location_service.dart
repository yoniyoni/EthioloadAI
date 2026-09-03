import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import '../data/api/api_client.dart';

class LocationService {
  static Timer? _timer;
  static StreamSubscription<Position>? _positionStream;
  static bool _batteryWarnShown = false;

  /// True once live (trip-ongoing) tracking has started — i.e. the
  /// Android foreground service / iOS background-location stream is
  /// active and will keep pushing positions to the backend regardless of
  /// which app is currently in the foreground.
  static bool get isLiveTracking => _positionStream != null;

  /// Returns true the first time a live-tracking session starts,
  /// then false on every subsequent call. Caller should show the battery
  /// warning snackbar when this returns true.
  static bool consumeBatteryWarning() {
    if (_batteryWarnShown) return false;
    _batteryWarnShown = true;
    return true;
  }

  // Call when driver logs in, or a trip starts/ends.
  // tripOngoing: true  → live tracking for the shipper's tracking screen —
  //              pushes on ~150m of movement, or at least every 45s while
  //              stationary (loading, traffic).
  //              false → idle "available" driver, only feeding the nearby-
  //              trucks preview map — infrequent interval push:
  //              serviceType 'intercity' → 25-min (default), 'intracity' → 5-min.
  // Returns true if an initial GPS fix was obtained AND successfully pushed
  // to the server on this call — callers that need to confirm the location
  // actually reached the backend (e.g. to refresh a "location not set"
  // banner) should check this instead of assuming success.
  static Future<bool> startTracking(
    WidgetRef ref, {
    String serviceType = 'intercity',
    bool tripOngoing = false,
  }) async {
    stopTracking();

    var pushed = false;
    final position = await _requestAndGet();
    if (position != null) {
      pushed = await _push(ref, position);
    }

    if (tripOngoing) {
      await _startLiveTracking(ref);
      return pushed;
    }

    final interval = serviceType == 'intracity'
        ? const Duration(minutes: 5)
        : const Duration(minutes: 25);
    _timer = Timer.periodic(interval, (_) async {
      final pos = await getCurrentPosition();
      if (pos != null) {
        await _push(ref, pos);
      }
    });
    return pushed;
  }

  static Future<void> _startLiveTracking(WidgetRef ref) async {
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return;

    LocationPermission perm = await Geolocator.checkPermission();
    if (perm == LocationPermission.denied) {
      perm = await Geolocator.requestPermission();
    }
    if (perm == LocationPermission.denied ||
        perm == LocationPermission.deniedForever) {
      return;
    }

    final LocationSettings settings;
    if (defaultTargetPlatform == TargetPlatform.android) {
      // Android 13+ blocks the foreground-service notification (silently —
      // the service still runs, just invisibly) unless this is granted.
      await Permission.notification.request();

      // Foreground service notification keeps location updates flowing
      // while the driver's phone is locked or the app is backgrounded.
      settings = AndroidSettings(
        accuracy: LocationAccuracy.medium,
        distanceFilter: 150,
        intervalDuration: const Duration(seconds: 15),
        foregroundNotificationConfig: const ForegroundNotificationConfig(
          notificationTitle: 'EthioLoad — trip tracking is active',
          notificationText:
              'Your location is being shared with the shipper for this trip.',
          notificationChannelName: 'Trip tracking',
          enableWakeLock: true,
        ),
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      settings = AppleSettings(
        accuracy: LocationAccuracy.medium,
        distanceFilter: 150,
        activityType: ActivityType.automotiveNavigation,
        allowBackgroundLocationUpdates: true,
        showBackgroundLocationIndicator: true,
      );
    } else {
      settings = const LocationSettings(
        accuracy: LocationAccuracy.medium,
        distanceFilter: 150,
      );
    }

    _positionStream = Geolocator.getPositionStream(locationSettings: settings)
        .listen((pos) => _push(ref, pos));

    // Fallback for when the truck is stationary — the distance-filtered
    // stream above won't fire on its own in that case.
    _timer = Timer.periodic(const Duration(seconds: 45), (_) async {
      final pos = await getCurrentPosition();
      if (pos != null) {
        await _push(ref, pos);
      }
    });
  }

  static void stopTracking() {
    _timer?.cancel();
    _timer = null;
    _positionStream?.cancel();
    _positionStream = null;
  }

  // Returns null if permission denied or GPS unavailable. Never throws.
  static Future<Position?> getCurrentPosition() async {
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

  /// Pushes a driver-chosen point (e.g. a manually selected city) as their
  /// current location, via the same endpoint the GPS path uses. Lets the
  /// driver override their tracked position on demand — the next automatic
  /// GPS push (if tracking is running) will naturally supersede it, same as
  /// picking a new city again would.
  static Future<bool> pushManualLocation(
    WidgetRef ref, {
    required double lat,
    required double lng,
  }) async {
    try {
      await ref.read(apiClientProvider).post<void>(
        '/driver/location',
        data: {'lat': lat, 'lng': lng},
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> _push(WidgetRef ref, Position pos) async {
    try {
      await ref.read(apiClientProvider).post<void>(
        '/driver/location',
        data: {'lat': pos.latitude, 'lng': pos.longitude},
      );
      return true;
    } catch (_) {
      // Silent failure — will retry on next tick
      return false;
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
