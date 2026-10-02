import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:go_router/go_router.dart';
import '../../config/map_config.dart';
import '../../data/repositories/repositories.dart';

/// Fleet-wide "live map" overview — shows every vehicle in the fleet owner's
/// fleet with its last known GPS position. Tapping a vehicle that currently
/// has an active trip lets the fleet owner drill into the existing
/// single-trip tracking screen for full route/ETA detail.
class FleetLiveMapScreen extends ConsumerStatefulWidget {
  const FleetLiveMapScreen({super.key});

  @override
  ConsumerState<FleetLiveMapScreen> createState() =>
      _FleetLiveMapScreenState();
}

class _FleetLiveMapScreenState extends ConsumerState<FleetLiveMapScreen> {
  List<Map<String, dynamic>> _vehicles = [];
  bool _loading = true;
  String? _error;
  Timer? _pollTimer;
  final MapController _mapController = MapController();

  static const _defaultCenter = LatLng(9.0320, 38.7469); // Addis Ababa

  @override
  void initState() {
    super.initState();
    _load();
    _pollTimer = Timer.periodic(const Duration(seconds: 25), (_) => _load());
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final vehicles = await ref.read(fleetRepositoryProvider).getLiveMap();
      if (!mounted) return;
      setState(() {
        _vehicles = vehicles;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'Failed to load fleet live map: $e';
        _loading = false;
      });
    }
  }

  /// Returns (markerColor, isStale) based on last_location_at recency.
  Color _markerColor(String? lastLocationAt) {
    if (lastLocationAt == null) return Colors.grey;
    final dt = DateTime.tryParse(lastLocationAt);
    if (dt == null) return Colors.grey;
    final minutes = DateTime.now().difference(dt).inMinutes;
    if (minutes < 10) return const Color(0xFF059669); // green
    if (minutes < 60) return const Color(0xFFF59E0B); // amber
    return Colors.grey;
  }

  /// Same relative-time formatting style used in fleet_dashboard_screen.dart.
  String _relativeTime(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 1) return 'just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${diff.inDays}d ago';
  }

  @override
  Widget build(BuildContext context) {
    final positioned = _vehicles.where((v) {
      final lat = v['latitude'];
      final lng = v['longitude'];
      return lat != null && lng != null;
    }).toList();

    final onlineCount = positioned.where((v) {
      return _markerColor(v['last_location_at']?.toString()) ==
          const Color(0xFF059669);
    }).length;

    final center = positioned.isNotEmpty
        ? LatLng(
            (positioned.first['latitude'] as num).toDouble(),
            (positioned.first['longitude'] as num).toDouble(),
          )
        : _defaultCenter;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Fleet Live Map'),
        backgroundColor: Colors.green,
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          if (_loading)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Center(
                child: SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Colors.white,
                  ),
                ),
              ),
            )
          else
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: 'Refresh',
              onPressed: _load,
            ),
        ],
      ),
      body: _buildBody(positioned, onlineCount, center),
    );
  }

  Widget _buildBody(
    List<Map<String, dynamic>> positioned,
    int onlineCount,
    LatLng center,
  ) {
    if (_loading && _vehicles.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null && _vehicles.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, color: Colors.red, size: 40),
              const SizedBox(height: 12),
              Text(_error!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _load,
                style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                child: const Text('Retry',
                    style: TextStyle(color: Colors.white)),
              ),
            ],
          ),
        ),
      );
    }

    if (_vehicles.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.local_shipping_outlined, size: 56, color: Colors.grey),
            SizedBox(height: 12),
            Text('No vehicles in your fleet yet',
                style: TextStyle(color: Colors.grey)),
          ],
        ),
      );
    }

    return Stack(
      children: [
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: center,
            initialZoom: 6.5,
          ),
          children: [
            TileLayer(
              urlTemplate: kMapTileUrlTemplate,
              userAgentPackageName: 'com.ethioloadai.app',
            ),
            MarkerLayer(
              markers: positioned.map((v) {
                final lat = (v['latitude'] as num).toDouble();
                final lng = (v['longitude'] as num).toDouble();
                final color = _markerColor(v['last_location_at']?.toString());
                final hasActiveTrip = v['active_trip'] != null;
                return Marker(
                  point: LatLng(lat, lng),
                  width: 46,
                  height: 46,
                  child: GestureDetector(
                    onTap: () => _showVehicleSheet(v, color),
                    child: Container(
                      decoration: BoxDecoration(
                        color: color,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: hasActiveTrip ? Colors.blue : Colors.white,
                          width: hasActiveTrip ? 3 : 2,
                        ),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.25),
                            blurRadius: 6,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: const Icon(Icons.local_shipping,
                          color: Colors.white, size: 22),
                    ),
                  ),
                );
              }).toList(),
            ),
            kMapAttribution,
          ],
        ),
        Positioned(
          top: 10,
          left: 10,
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.92),
              borderRadius: BorderRadius.circular(8),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.1),
                  blurRadius: 4,
                ),
              ],
            ),
            child: Text(
              '$onlineCount / ${positioned.length} vehicles online',
              style: const TextStyle(
                  fontSize: 12, fontWeight: FontWeight.w600),
            ),
          ),
        ),
      ],
    );
  }

  void _showVehicleSheet(Map<String, dynamic> vehicle, Color statusColor) {
    final driver = vehicle['driver'] as Map<String, dynamic>?;
    final activeTrip = vehicle['active_trip'] as Map<String, dynamic>?;
    final isVerified = vehicle['verification_status'] == true;
    final lastLocationAt = vehicle['last_location_at'] != null
        ? DateTime.tryParse(vehicle['last_location_at'].toString())
        : null;

    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: Icon(Icons.local_shipping, color: statusColor),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      vehicle['plate_number']?.toString() ?? '—',
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    Text(
                      vehicle['truck_type']?.toString() ?? '—',
                      style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                    ),
                  ],
                ),
              ),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: isVerified
                      ? Colors.green.withAlpha(20)
                      : Colors.grey.withAlpha(30),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  isVerified ? 'Verified' : 'Unverified',
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: isVerified ? Colors.green : Colors.grey[700],
                  ),
                ),
              ),
            ]),
            const SizedBox(height: 16),
            Row(children: [
              const Icon(Icons.person_outline, size: 18, color: Colors.grey),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  driver != null
                      ? '${driver['full_name'] ?? '—'} · ${driver['phone'] ?? '—'}'
                      : 'No driver assigned',
                  style: const TextStyle(fontSize: 13),
                ),
              ),
            ]),
            const SizedBox(height: 8),
            Row(children: [
              const Icon(Icons.access_time, size: 18, color: Colors.grey),
              const SizedBox(width: 8),
              Text(
                lastLocationAt != null
                    ? 'Last seen: ${_relativeTime(lastLocationAt)}'
                    : 'Last seen: no location data yet',
                style: const TextStyle(fontSize: 13),
              ),
            ]),
            if (activeTrip != null) ...[
              const SizedBox(height: 8),
              Row(children: [
                const Icon(Icons.route, size: 18, color: Colors.grey),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'On trip to ${activeTrip['destination'] ?? '—'} '
                    '(${activeTrip['booking_status'] ?? '—'})',
                    style: const TextStyle(fontSize: 13),
                  ),
                ),
              ]),
            ],
            const SizedBox(height: 20),
            if (activeTrip != null)
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: () {
                    Navigator.pop(sheetContext);
                    final tripId = activeTrip['trip_id'];
                    if (tripId != null) {
                      final serviceType = activeTrip['service_type'];
                      final query = serviceType != null
                          ? '?serviceType=${Uri.encodeComponent(serviceType.toString())}'
                          : '';
                      context.push('/fleet/trip-tracking/$tripId$query');
                    }
                  },
                  icon: const Icon(Icons.map_outlined, color: Colors.white),
                  label: const Text('View Live Trip Tracking',
                      style: TextStyle(color: Colors.white)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
