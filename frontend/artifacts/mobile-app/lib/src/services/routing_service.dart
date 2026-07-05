import 'package:dio/dio.dart';
import '../data/api/api_client.dart';
import '../data/models/models.dart';

/// Client for the Laravel routing proxy (OSRM + Nominatim).
/// All mapping API calls go through Laravel — never directly from Flutter.
class RoutingService {
  static final Dio _dio = ApiClient.dio;

  /// GET /routing/route — returns OSRM route or haversine fallback.
  static Future<RouteResult?> getRoute({
    required double fromLat,
    required double fromLng,
    required double toLat,
    required double toLng,
  }) async {
    try {
      final resp = await _dio.get('/routing/route', queryParameters: {
        'from_lat': fromLat,
        'from_lng': fromLng,
        'to_lat':   toLat,
        'to_lng':   toLng,
      });
      if (resp.statusCode == 200 && resp.data is Map) {
        return RouteResult.fromJson(Map<String, dynamic>.from(resp.data as Map));
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  /// GET /routing/search?q= — returns Nominatim place results.
  static Future<List<PlaceResult>> searchPlace(String query) async {
    if (query.trim().isEmpty) return [];
    try {
      final resp = await _dio.get('/routing/search', queryParameters: {'q': query.trim()});
      if (resp.statusCode == 200 && resp.data is Map) {
        final raw = (resp.data as Map)['places'] as List? ?? [];
        return raw
            .whereType<Map>()
            .map((p) => PlaceResult.fromJson(Map<String, dynamic>.from(p)))
            .toList();
      }
      return [];
    } catch (_) {
      return [];
    }
  }

  /// GET /routing/reverse?lat=&lng= — returns Nominatim reverse geocode.
  static Future<Map<String, dynamic>?> reverseGeocode(double lat, double lng) async {
    try {
      final resp = await _dio.get('/routing/reverse', queryParameters: {'lat': lat, 'lng': lng});
      if (resp.statusCode == 200 && resp.data is Map) {
        return Map<String, dynamic>.from(resp.data as Map);
      }
      return null;
    } catch (_) {
      return null;
    }
  }
}
