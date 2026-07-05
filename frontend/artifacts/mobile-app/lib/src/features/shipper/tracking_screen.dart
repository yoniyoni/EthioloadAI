import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

const _green = Color(0xFF0F3D1A);
const _bg    = Color(0xFFF8FBF8);

/// Shipper tracking screen — polls GET /trips/{trip}/location.
/// Polling interval: 5 min (intracity) / 30 min (intercity).
/// Feature 3 enhancements: route overlay (planned=grey, traveled=green),
/// ETA from OSRM, reverse geocode "currently near" label, truck popup.
class TrackingScreen extends ConsumerStatefulWidget {
  final int freightId;
  const TrackingScreen({required this.freightId, super.key});

  @override
  ConsumerState<TrackingScreen> createState() => _TrackingScreenState();
}

class _TrackingScreenState extends ConsumerState<TrackingScreen> {
  Timer? _pollTimer;
  TripLocation? _location;
  bool _loading    = false;
  bool _firstLoad  = true;
  int? _tripId;
  Booking? _booking;
  final MapController _mapController = MapController();

  // Route overlay state
  RouteResult? _plannedRoute;
  bool _fetchingRoute = false;

  // Reverse geocode "currently near"
  String? _currentAddress;
  double? _lastGeocodedLat;
  double? _lastGeocodedLng;

  // Popup open/closed
  bool _showTruckPopup = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _bootstrap());
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<void> _bootstrap() async {
    final bookings = ref.read(bookingListProvider).valueOrNull ?? [];
    Booking? booking;
    try {
      booking = bookings.firstWhere((b) => b.cargoId == widget.freightId);
    } catch (_) {}

    if (booking == null) return;

    setState(() => _booking = booking);

    final tripId = booking.tripId;
    if (tripId == null) return;

    setState(() => _tripId = tripId);
    await _refresh();

    final serviceType = _booking?.serviceType ?? 'intercity';
    final interval    = serviceType == 'intracity'
        ? const Duration(minutes: 5)
        : const Duration(minutes: 30);
    _pollTimer = Timer.periodic(interval, (_) => _refresh());
  }

  Future<void> _refresh() async {
    if (_tripId == null) return;
    setState(() => _loading = true);
    try {
      final loc = await ref.read(tripRepositoryProvider).getLocation(_tripId!);
      if (!mounted) return;
      setState(() {
        _location  = loc;
        _firstLoad = false;
      });

      if (loc.hasPosition) {
        _mapController.move(
          LatLng(loc.currentLat!, loc.currentLng!),
          12.0,
        );
        _fetchReverseGeocode(loc.currentLat!, loc.currentLng!);
      }

      // Fetch planned route overlay once we have both current position and destination
      if (loc.hasPosition && loc.hasDestination && _plannedRoute == null) {
        _fetchPlannedRoute(loc);
      }
    } catch (_) {
      if (mounted) setState(() => _firstLoad = false);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _fetchPlannedRoute(TripLocation loc) async {
    if (_fetchingRoute) return;
    setState(() => _fetchingRoute = true);
    try {
      final route = await ref.read(routingRepositoryProvider).getRoute(
        fromLat: loc.currentLat!,
        fromLng: loc.currentLng!,
        toLat:   loc.destinationLat!,
        toLng:   loc.destinationLng!,
      );
      if (mounted) setState(() => _plannedRoute = route);
    } catch (_) {
      // Route overlay is optional — fail silently
    } finally {
      if (mounted) setState(() => _fetchingRoute = false);
    }
  }

  Future<void> _fetchReverseGeocode(double lat, double lng) async {
    // Skip if we already geocoded this exact point (within ~1km)
    if (_lastGeocodedLat != null) {
      final dist = (lat - _lastGeocodedLat!).abs() + (lng - _lastGeocodedLng!).abs();
      if (dist < 0.01) return;
    }
    try {
      final result =
          await ref.read(routingRepositoryProvider).reverseGeocode(lat, lng);
      if (mounted && result != null) {
        setState(() {
          _currentAddress  = result['short_address'] as String?;
          _lastGeocodedLat = lat;
          _lastGeocodedLng = lng;
        });
      }
    } catch (_) {}
  }

  bool get _isIntracity => _booking?.serviceType == 'intracity';

  String _lastUpdatedText() {
    final mins    = _location?.minutesSinceUpdate;
    if (mins == null) return '—';
    final offline = _isIntracity ? mins > 10 : mins > 60;
    if (offline)  return 'tracking.driver_offline'.tr();
    if (mins < 60) return 'tracking.minutes_ago'.tr(namedArgs: {'n': '$mins'});
    return 'tracking.hours_ago'.tr(namedArgs: {'n': '${(mins / 60).floor()}'});
  }

  Color _statusColor() {
    final mins = _location?.minutesSinceUpdate;
    if (mins == null) return Colors.grey;
    if (_isIntracity) {
      if (mins < 5)  return const Color(0xFF059669);
      if (mins < 10) return const Color(0xFFF59E0B);
      return Colors.red;
    } else {
      if (mins < 30)  return const Color(0xFF059669);
      if (mins < 60)  return const Color(0xFFF59E0B);
      return Colors.red;
    }
  }

  double? _etaMin() {
    final loc   = _location;
    final route = _plannedRoute;
    if (loc == null || route == null) return null;
    return route.durationMin.toDouble();
  }

  static const _defaultCenter = LatLng(9.0310, 38.7469);

  @override
  Widget build(BuildContext context) {
    final loc    = _location;
    final center = (loc != null && loc.hasPosition)
        ? LatLng(loc.currentLat!, loc.currentLng!)
        : _defaultCenter;

    // Traveled breadcrumbs (green)
    final traveledPoints = (loc?.routeData ?? []).map((p) {
      final lat = double.tryParse(p['lat']?.toString() ?? '');
      final lng = double.tryParse(p['lng']?.toString() ?? '');
      if (lat == null || lng == null) return null;
      return LatLng(lat, lng);
    }).whereType<LatLng>().toList();

    // Planned OSRM route (grey) — [lng, lat] → [lat, lng]
    final plannedPoints = (_plannedRoute?.polyline ?? [])
        .map((p) => LatLng(p[1], p[0]))
        .toList();

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        title: Text(
          'tracking.track_cargo'.tr(),
          style: const TextStyle(
              color: Colors.white, fontWeight: FontWeight.w600),
        ),
        iconTheme: const IconThemeData(color: Colors.white),
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
              icon: const Icon(Icons.refresh_rounded, color: Colors.white),
              tooltip: 'tracking.refresh_map'.tr(),
              onPressed: _refresh,
            ),
        ],
      ),
      body: Column(
        children: [
          // ── Map ──────────────────────────────────────────────────────
          Expanded(
            flex: 5,
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapController,
                  options: MapOptions(
                    initialCenter: center,
                    initialZoom:   10.0,
                    onTap:         (_, __) => setState(() => _showTruckPopup = false),
                    interactionOptions: const InteractionOptions(
                      flags: InteractiveFlag.all,
                    ),
                  ),
                  children: [
                    TileLayer(
                      urlTemplate:
                          'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.ethioloadai.app',
                    ),

                    // Planned route (grey) — full OSRM path to destination
                    if (plannedPoints.length >= 2)
                      PolylineLayer(
                        polylines: [
                          Polyline(
                            points:      plannedPoints,
                            strokeWidth: 3.0,
                            color:       Colors.grey.withValues(alpha: 0.55),
                          ),
                        ],
                      ),

                    // Traveled breadcrumbs (green solid)
                    if (traveledPoints.length >= 2)
                      PolylineLayer(
                        polylines: [
                          Polyline(
                            points:      traveledPoints,
                            strokeWidth: 3.5,
                            color:       _green.withValues(alpha: 0.80),
                          ),
                        ],
                      ),

                    // Destination pin
                    if (loc != null && loc.hasDestination)
                      MarkerLayer(markers: [
                        Marker(
                          point:  LatLng(loc.destinationLat!, loc.destinationLng!),
                          width:  36,
                          height: 36,
                          child:  _DestPin(),
                        ),
                      ]),

                    // Truck marker (tappable)
                    if (loc != null && loc.hasPosition)
                      MarkerLayer(
                        markers: [
                          Marker(
                            point:  LatLng(loc.currentLat!, loc.currentLng!),
                            width:  48,
                            height: 48,
                            child:  GestureDetector(
                              onTap: () => setState(
                                  () => _showTruckPopup = !_showTruckPopup),
                              child: _TruckMarker(),
                            ),
                          ),
                        ],
                      ),
                  ],
                ),

                // Truck popup on marker tap
                if (_showTruckPopup && loc != null && loc.hasPosition)
                  Positioned(
                    top: 12, left: 16, right: 16,
                    child: _TruckPopup(
                      location:    loc,
                      address:     _currentAddress,
                      etaMin:      _etaMin(),
                      routeDistKm: _plannedRoute?.distanceKm,
                      onClose:     () => setState(() => _showTruckPopup = false),
                    ),
                  ),

                // Route legend
                if (plannedPoints.length >= 2 || traveledPoints.length >= 2)
                  Positioned(
                    right: 10, bottom: 10,
                    child: _RouteLegend(
                      hasPlanned:  plannedPoints.length >= 2,
                      hasTraveled: traveledPoints.length >= 2,
                    ),
                  ),
              ],
            ),
          ),

          // ── Info card ────────────────────────────────────────────────
          Expanded(
            flex: 3,
            child: Container(
              width: double.infinity,
              color: Colors.white,
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
                child: _firstLoad
                    ? const Center(
                        child: Padding(
                          padding: EdgeInsets.all(24),
                          child: CircularProgressIndicator(color: _green),
                        ),
                      )
                    : _buildInfoCard(loc),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoCard(TripLocation? loc) {
    if (_tripId == null) {
      return _NoTripCard(freightId: widget.freightId);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_booking != null)
          _InfoRow(
            icon:       Icons.receipt_long_outlined,
            label:      'Booking #${_booking!.id}',
            value:      _booking!.bookingStatus.toUpperCase(),
            valueColor: _green,
          ),

        const SizedBox(height: 8),

        Builder(builder: (_) {
          final dotColor = (loc != null && loc.hasPosition)
              ? _statusColor()
              : Colors.grey;
          return Row(children: [
            Container(
              width: 10, height: 10,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: dotColor,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              (loc != null && loc.hasPosition)
                  ? 'tracking.driver_is_here'.tr()
                  : 'tracking.no_location_data'.tr(),
              style: TextStyle(
                fontWeight: FontWeight.w600,
                fontSize: 13,
                color: dotColor,
              ),
            ),
          ]);
        }),

        const SizedBox(height: 10),

        if (loc != null && loc.hasPosition) ...[
          // "Currently near" from reverse geocode
          if (_currentAddress != null)
            _InfoRow(
              icon:  Icons.place_outlined,
              label: 'tracking.currently_near'.tr(),
              value: _currentAddress!,
            )
          else
            _InfoRow(
              icon:  Icons.location_on_outlined,
              label: 'tracking.city_label'.tr(),
              value: loc.currentCity ?? '—',
            ),

          const SizedBox(height: 4),
          _InfoRow(
            icon:       Icons.access_time_outlined,
            label:      'tracking.last_updated'.tr(),
            value:      _lastUpdatedText(),
            valueColor: _statusColor(),
          ),

          // ETA row from OSRM
          if (_etaMin() != null) ...[
            const SizedBox(height: 4),
            _InfoRow(
              icon:  Icons.timer_outlined,
              label: 'tracking.eta'.tr(),
              value: '${_etaMin()!.toStringAsFixed(0)} min',
            ),
          ],

          // Destination
          if (loc.destination != null) ...[
            const SizedBox(height: 4),
            _InfoRow(
              icon:  Icons.flag_outlined,
              label: 'tracking.destination'.tr(),
              value: loc.destination!,
            ),
          ],

          const SizedBox(height: 4),
          _InfoRow(
            icon:  Icons.gps_fixed_outlined,
            label: 'GPS',
            value: '${loc.currentLat!.toStringAsFixed(4)}, '
                '${loc.currentLng!.toStringAsFixed(4)}',
          ),
        ] else ...[
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              'tracking.no_location_data'.tr(),
              style: TextStyle(fontSize: 13, color: Colors.grey[500]),
            ),
          ),
        ],

        const SizedBox(height: 16),
        Row(children: [
          const Icon(Icons.info_outline, size: 14, color: Colors.grey),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              (_isIntracity
                      ? 'tracking.updates_every_5'
                      : 'tracking.updates_every_25')
                  .tr(),
              style: TextStyle(fontSize: 11, color: Colors.grey[500]),
            ),
          ),
        ]),
      ],
    );
  }
}

// ── Truck info popup ──────────────────────────────────────────────────────

class _TruckPopup extends StatelessWidget {
  final TripLocation location;
  final String? address;
  final double? etaMin;
  final double? routeDistKm;
  final VoidCallback onClose;

  const _TruckPopup({
    required this.location,
    required this.address,
    required this.etaMin,
    required this.routeDistKm,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 4,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
        child: Row(children: [
          const Icon(Icons.local_shipping_rounded, color: _green, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  address ?? location.currentCity ?? 'tracking.driver_is_here'.tr(),
                  style: const TextStyle(
                      fontSize: 13, fontWeight: FontWeight.w600),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                if (etaMin != null || routeDistKm != null)
                  Text(
                    [
                      if (routeDistKm != null)
                        '${routeDistKm!.toStringAsFixed(0)} km',
                      if (etaMin != null)
                        '~${etaMin!.toStringAsFixed(0)} min',
                    ].join(' · '),
                    style: const TextStyle(fontSize: 11, color: Colors.grey),
                  ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 18, color: Colors.grey),
            onPressed: onClose,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ]),
      ),
    );
  }
}

// ── Route legend ──────────────────────────────────────────────────────────

class _RouteLegend extends StatelessWidget {
  final bool hasPlanned;
  final bool hasTraveled;
  const _RouteLegend({required this.hasPlanned, required this.hasTraveled});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      decoration: BoxDecoration(
        color:        Colors.white.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize:       MainAxisSize.min,
        children: [
          if (hasTraveled) _LegendRow(
            color: _green,
            dashed: false,
            label: 'tracking.traveled'.tr(),
          ),
          if (hasPlanned) _LegendRow(
            color:  Colors.grey,
            dashed: true,
            label:  'tracking.planned_route'.tr(),
          ),
        ],
      ),
    );
  }
}

class _LegendRow extends StatelessWidget {
  final Color color;
  final bool dashed;
  final String label;
  const _LegendRow({
    required this.color,
    required this.dashed,
    required this.label,
  });

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          if (dashed)
            Row(children: List.generate(3, (_) => Container(
              width: 5, height: 2,
              margin: const EdgeInsets.only(right: 2),
              color: color,
            )))
          else
            Container(width: 18, height: 2, color: color),
          const SizedBox(width: 6),
          Text(label, style: const TextStyle(fontSize: 10, color: Colors.black87)),
        ]),
      );
}

// ── Markers ───────────────────────────────────────────────────────────────

class _TruckMarker extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color:  _green,
        shape:  BoxShape.circle,
        border: Border.all(color: Colors.white, width: 2),
        boxShadow: [
          BoxShadow(
            color:      Colors.black.withValues(alpha: 0.25),
            blurRadius: 6,
            offset:     const Offset(0, 2),
          ),
        ],
      ),
      child: const Icon(Icons.local_shipping_rounded,
          color: Colors.white, size: 22),
    );
  }
}

class _DestPin extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color:  const Color(0xFFF59E0B),
          shape:  BoxShape.circle,
          border: Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color:      Colors.black.withValues(alpha: 0.2),
              blurRadius: 5,
            ),
          ],
        ),
        child: const Icon(Icons.flag_rounded, color: Colors.white, size: 18),
      );
}

// ── Info row helper ───────────────────────────────────────────────────────

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;

  const _InfoRow({
    required this.icon,
    required this.label,
    required this.value,
    this.valueColor,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 15, color: Colors.grey[400]),
        const SizedBox(width: 8),
        Text('$label: ',
            style: TextStyle(fontSize: 12, color: Colors.grey[500])),
        Expanded(
          child: Text(
            value,
            style: TextStyle(
              fontSize:   12,
              fontWeight: FontWeight.w500,
              color:      valueColor ?? const Color(0xFF111827),
            ),
          ),
        ),
      ],
    );
  }
}

// ── No trip card ──────────────────────────────────────────────────────────

class _NoTripCard extends StatelessWidget {
  final int freightId;
  const _NoTripCard({required this.freightId});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: Text(
          'tracking.no_trip'.tr(),
          style: TextStyle(color: Colors.grey[500]),
        ),
      ),
    );
  }
}
