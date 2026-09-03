import 'package:geolocator/geolocator.dart';

/// Radius (meters) within which a driver is considered "arrived" at the
/// cargo pickup location. Kept as a named constant so it's easy to tune.
/// Deliberately generous (not tight GPS-accuracy-level precision) because
/// pickup/destination coordinates are frequently only city-center precision
/// when the shipper didn't drop a GPS pin — see the manual "confirm arrival"
/// fallback in active_trip_screen.dart, which exists precisely because no
/// radius here can fully compensate for that.
const double kPickupGeofenceRadiusMeters = 250.0;

/// Radius (meters) within which a driver is considered "arrived" at the
/// delivery destination. Kept separate from the pickup radius so the two
/// can be tuned independently.
const double kDestinationGeofenceRadiusMeters = 250.0;

/// Pure geofence distance/containment helpers, built on top of
/// Geolocator's haversine-based distanceBetween. No state, no I/O —
/// safe to call from any widget or service.
class GeofenceService {
  /// Distance in meters between the driver's current position and a
  /// target point (pickup or destination).
  static double distanceToTarget({
    required double currentLat,
    required double currentLng,
    required double targetLat,
    required double targetLng,
  }) {
    return Geolocator.distanceBetween(
      currentLat,
      currentLng,
      targetLat,
      targetLng,
    );
  }

  /// True once the driver is within [radiusMeters] of the target point.
  static bool isWithinRadius({
    required double currentLat,
    required double currentLng,
    required double targetLat,
    required double targetLng,
    required double radiusMeters,
  }) {
    return distanceToTarget(
          currentLat: currentLat,
          currentLng: currentLng,
          targetLat: targetLat,
          targetLng: targetLng,
        ) <=
        radiusMeters;
  }
}
