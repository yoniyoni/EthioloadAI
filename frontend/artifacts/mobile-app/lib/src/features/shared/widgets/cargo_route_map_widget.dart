import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import '../../../config/map_config.dart';
import '../../../data/models/models.dart';
import '../../../data/repositories/repositories.dart';

const _kGreen   = Color(0xFF0F3D1A);
const _kInfoBg  = Color(0xFFEAF3DE);
const _kAmber   = Color(0xFFF59E0B);

class CargoRouteMapWidget extends ConsumerStatefulWidget {
  final double pickupLat;
  final double pickupLng;
  final double destinationLat;
  final double destinationLng;
  final String pickupName;
  final String destinationName;
  final double height;

  const CargoRouteMapWidget({
    super.key,
    required this.pickupLat,
    required this.pickupLng,
    required this.destinationLat,
    required this.destinationLng,
    required this.pickupName,
    required this.destinationName,
    this.height = 220,
  });

  @override
  ConsumerState<CargoRouteMapWidget> createState() =>
      _CargoRouteMapWidgetState();
}

class _CargoRouteMapWidgetState extends ConsumerState<CargoRouteMapWidget> {
  RouteResult? _route;
  bool _loading = true;
  final _mapCtrl = MapController();

  LatLng get _pickup => LatLng(widget.pickupLat, widget.pickupLng);
  LatLng get _dest   => LatLng(widget.destinationLat, widget.destinationLng);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    try {
      final route = await ref.read(routingRepositoryProvider).getRoute(
        fromLat: widget.pickupLat,
        fromLng: widget.pickupLng,
        toLat:   widget.destinationLat,
        toLng:   widget.destinationLng,
      );
      if (!mounted) return;
      setState(() { _route = route; _loading = false; });
      WidgetsBinding.instance.addPostFrameCallback((_) => _fitBounds());
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  void _fitBounds() {
    final pts = _polyPoints;
    if (pts.length < 2) return;
    try {
      _mapCtrl.fitCamera(CameraFit.bounds(
        bounds: LatLngBounds.fromPoints(pts),
        padding: const EdgeInsets.all(40),
      ));
    } catch (_) {}
  }

  List<LatLng> get _polyPoints {
    if (_route != null && _route!.polyline.isNotEmpty) {
      return _route!.polyline
          .map((p) => LatLng(p[1], p[0])) // OSRM gives [lng, lat]
          .toList();
    }
    return [_pickup, _dest];
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: widget.height,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Stack(
              children: [
                _MapView(
                  mapCtrl:    _mapCtrl,
                  pickup:     _pickup,
                  dest:       _dest,
                  polyPoints: _polyPoints,
                  hasRoute:   _route != null && _route!.polyline.isNotEmpty,
                ),
                if (_loading)
                  Container(
                    color: Colors.white.withValues(alpha: 0.75),
                    child: const Center(
                      child: CircularProgressIndicator(
                        strokeWidth: 2, color: _kGreen,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        _buildRouteInfo(),
      ],
    );
  }

  Widget _buildRouteInfo() {
    final distKm  = _route?.distanceKm ?? _haversineEstimateKm();
    final isOsrm  = _route?.source == 'osrm';
    final durMin  = _route?.durationMin ?? 0;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: _kInfoBg,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(children: [
        const Icon(Icons.straighten_rounded, size: 16, color: _kAmber),
        const SizedBox(width: 6),
        Text(
          isOsrm
              ? '${distKm.toStringAsFixed(0)} km'
              : '~${distKm.toStringAsFixed(0)} km (estimated)',
          style: const TextStyle(
              fontWeight: FontWeight.bold, color: _kAmber, fontSize: 13),
        ),
        if (isOsrm && durMin > 0) ...[
          const SizedBox(width: 12),
          const Icon(Icons.access_time_rounded, size: 15, color: Colors.grey),
          const SizedBox(width: 4),
          Text(
            _formatDuration(durMin),
            style: const TextStyle(color: Colors.grey, fontSize: 12),
          ),
        ],
        const Spacer(),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
          decoration: BoxDecoration(
            color: isOsrm
                ? _kGreen.withValues(alpha: 0.10)
                : Colors.grey[200],
            borderRadius: BorderRadius.circular(4),
          ),
          child: Text(
            isOsrm ? 'OSRM' : 'Estimated',
            style: TextStyle(
              fontSize: 9,
              fontWeight: FontWeight.bold,
              color: isOsrm ? _kGreen : Colors.grey[600],
            ),
          ),
        ),
      ]),
    );
  }

  double _haversineEstimateKm() {
    const R = 6371.0;
    final lat1 = widget.pickupLat      * pi / 180;
    final lat2 = widget.destinationLat * pi / 180;
    final dLat = (widget.destinationLat - widget.pickupLat)       * pi / 180;
    final dLng = (widget.destinationLng - widget.pickupLng) * pi / 180;
    final a = sin(dLat / 2) * sin(dLat / 2) +
              cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2);
    return 2 * R * asin(sqrt(a)) * 1.25;
  }

  static String _formatDuration(int minutes) {
    final h = minutes ~/ 60;
    final m = minutes % 60;
    if (h == 0) return '$m min';
    if (m == 0) return '~$h hr';
    return '~$h hr $m min';
  }
}

// ── Stateless map sub-widget ───────────────────────────────────────────────

class _MapView extends StatelessWidget {
  final MapController mapCtrl;
  final LatLng pickup;
  final LatLng dest;
  final List<LatLng> polyPoints;
  final bool hasRoute;

  const _MapView({
    required this.mapCtrl,
    required this.pickup,
    required this.dest,
    required this.polyPoints,
    required this.hasRoute,
  });

  @override
  Widget build(BuildContext context) {
    return FlutterMap(
      mapController: mapCtrl,
      options: MapOptions(
        initialCameraFit: CameraFit.bounds(
          bounds: LatLngBounds.fromPoints([pickup, dest]),
          padding: const EdgeInsets.all(60),
        ),
        interactionOptions: const InteractionOptions(
          flags: InteractiveFlag.none,
        ),
      ),
      children: [
        TileLayer(
          urlTemplate: kMapTileUrlTemplate,
          userAgentPackageName: 'com.ethioloadai.app',
        ),
        if (hasRoute)
          PolylineLayer(polylines: [
            Polyline(
              points:      polyPoints,
              color:       _kGreen,
              strokeWidth: 4.0,
              strokeCap:   StrokeCap.round,
            ),
          ]),
        CircleLayer(circles: [
          CircleMarker(
            point:            pickup,
            radius:           10,
            color:            Colors.blue,
            borderColor:      Colors.white,
            borderStrokeWidth: 2,
          ),
        ]),
        MarkerLayer(markers: [
          Marker(
            point:  dest,
            width:  36,
            height: 36,
            child:  const Icon(
              Icons.location_on_rounded,
              color: Colors.red,
              size: 36,
            ),
          ),
        ]),
        kMapAttribution,
      ],
    );
  }
}

// ── Error placeholder (no coordinates available) ──────────────────────────

class RouteUnavailablePlaceholder extends StatelessWidget {
  final double height;
  const RouteUnavailablePlaceholder({super.key, this.height = 220});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: Colors.grey[100],
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[300]!),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.map_outlined, size: 36, color: Colors.grey[400]),
          const SizedBox(height: 10),
          Text(
            'Route preview unavailable',
            style: TextStyle(
                fontSize: 13,
                color: Colors.grey[500],
                fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 4),
          Text(
            'የመንገድ ቅድመ እይታ አልተገኘም',
            style: TextStyle(fontSize: 12, color: Colors.grey[400]),
          ),
        ],
      ),
    );
  }
}
