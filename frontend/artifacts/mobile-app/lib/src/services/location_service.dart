import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../data/api/api_client.dart';

class LocationService {
  static Timer? _timer;
  static bool _batteryWarnShown = false;

  /// Returns true the first time an intracity tracking session starts,
  /// then false on every subsequent call. Caller should show the battery
  /// warning snackbar when this returns true.
  static bool consumeBatteryWarning() {
    if (_batteryWarnShown) return false;
    _batteryWarnShown = true;
    return true;
  }

  // Call when driver logs in or a trip starts.
  // serviceType: 'intercity' → 25-min interval (default)
  //              'intracity' → 5-min interval
  static Future<void> startTracking(
    WidgetRef ref, {
    String serviceType = 'intercity',
  }) async {
    final interval = serviceType == 'intracity'
        ? const Duration(minutes: 5)
        : const Duration(minutes: 25);

    final position = await _requestAndGet();
    if (position != null) {
      await _push(ref, position);
    }

    _timer?.cancel();
    _timer = Timer.periodic(interval, (_) async {
      final pos = await getCurrentPosition();
      if (pos != null) {
        await _push(ref, pos);
      }
    });
  }

  static void stopTracking() {
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

  static Future<void> _push(WidgetRef ref, Position pos) async {
    try {
      await ref.read(apiClientProvider).post<void>(
        '/driver/location',
        data: {'lat': pos.latitude, 'lng': pos.longitude},
      );
    } catch (_) {
      // Silent failure — will retry on next tick
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
