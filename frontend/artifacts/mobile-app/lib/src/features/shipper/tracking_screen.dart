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

/// Shipper tracking screen — polls GET /trips/{trip}/location every 30 minutes.
/// Driver GPS pushing is handled by LocationService (background, 25-min timer).
class TrackingScreen extends ConsumerStatefulWidget {
  final int freightId; // cargo ID — used to find the booking/trip
  const TrackingScreen({required this.freightId, super.key});

  @override
  ConsumerState<TrackingScreen> createState() => _TrackingScreenState();
}

class _TrackingScreenState extends ConsumerState<TrackingScreen> {
  Timer? _pollTimer;
  TripLocation? _location;
  bool _loading = false;
  bool _firstLoad = true;
  int? _tripId;
  Booking? _booking;
  final MapController _mapController = MapController();

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
    _pollTimer = Timer.periodic(const Duration(minutes: 30), (_) => _refresh());
  }

  Future<void> _refresh() async {
    if (_tripId == null) return;
    setState(() => _loading = true);
    try {
      final loc = await ref.read(tripRepositoryProvider).getLocation(_tripId!);
      if (mounted) {
        setState(() {
          _location = loc;
          _firstLoad = false;
        });
        if (loc.hasPosition) {
          _mapController.move(
            LatLng(loc.currentLat!, loc.currentLng!),
            12.0,
          );
        }
      }
    } catch (_) {
      if (mounted) setState(() => _firstLoad = false);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _lastUpdatedText() {
    final mins = _location?.minutesSinceUpdate;
    if (mins == null) return '—';
    if (mins < 60) {
      return 'tracking.minutes_ago'.tr(namedArgs: {'n': '$mins'});
    }
    return 'tracking.hours_ago'
        .tr(namedArgs: {'n': '${(mins / 60).floor()}'});
  }

  // Ethiopia center (Addis Ababa) used as default when no location data yet
  static const _defaultCenter = LatLng(9.0310, 38.7469);

  @override
  Widget build(BuildContext context) {
    final loc = _location;
    final center = (loc != null && loc.hasPosition)
        ? LatLng(loc.currentLat!, loc.currentLng!)
        : _defaultCenter;

    final routePoints = (loc?.routeData ?? []).map((p) {
      final lat = double.tryParse(p['lat']?.toString() ?? '');
      final lng = double.tryParse(p['lng']?.toString() ?? '');
      if (lat == null || lng == null) return null;
      return LatLng(lat, lng);
    }).whereType<LatLng>().toList();

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
            child: FlutterMap(
              mapController: _mapController,
              options: MapOptions(
                initialCenter: center,
                initialZoom: 10.0,
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
                if (routePoints.length >= 2)
                  PolylineLayer(
                    polylines: [
                      Polyline(
                        points: routePoints,
                        strokeWidth: 3.5,
                        color: _green.withValues(alpha: 0.75),
                      ),
                    ],
                  ),
                if (loc != null && loc.hasPosition)
                  MarkerLayer(
                    markers: [
                      Marker(
                        point: LatLng(loc.currentLat!, loc.currentLng!),
                        width: 48,
                        height: 48,
                        child: _TruckMarker(),
                      ),
                    ],
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
        // Booking reference
        if (_booking != null)
          _InfoRow(
            icon: Icons.receipt_long_outlined,
            label: 'Booking #${_booking!.id}',
            value: _booking!.bookingStatus.toUpperCase(),
            valueColor: _green,
          ),

        const SizedBox(height: 8),

        // Driver location status
        Row(children: [
          Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: (loc != null && loc.hasPosition) ? _green : Colors.grey,
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
              color: (loc != null && loc.hasPosition)
                  ? _green
                  : Colors.grey[600],
            ),
          ),
        ]),

        const SizedBox(height: 10),

        if (loc != null && loc.hasPosition) ...[
          _InfoRow(
            icon: Icons.location_on_outlined,
            label: 'tracking.city_label'.tr(),
            value: loc.currentCity ?? '—',
          ),
          const SizedBox(height: 4),
          _InfoRow(
            icon: Icons.access_time_outlined,
            label: 'tracking.last_updated'.tr(),
            value: _lastUpdatedText(),
          ),
          const SizedBox(height: 4),
          _InfoRow(
            icon: Icons.gps_fixed_outlined,
            label: 'GPS',
            value:
                '${loc.currentLat!.toStringAsFixed(4)}, '
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

        // Polling note
        const SizedBox(height: 16),
        Row(children: [
          const Icon(Icons.info_outline, size: 14, color: Colors.grey),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              'Location updates every 25 min · Tap refresh for latest',
              style: TextStyle(fontSize: 11, color: Colors.grey[500]),
            ),
          ),
        ]),
      ],
    );
  }
}

// ── Truck map marker ──────────────────────────────────────────────────────

class _TruckMarker extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: _green,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 2),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.25),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: const Icon(Icons.local_shipping_rounded,
          color: Colors.white, size: 22),
    );
  }
}

// ── No-trip placeholder ───────────────────────────────────────────────────

class _NoTripCard extends StatelessWidget {
  final int freightId;
  const _NoTripCard({required this.freightId});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const SizedBox(height: 8),
        const Icon(Icons.location_off_outlined,
            size: 40, color: Colors.grey),
        const SizedBox(height: 12),
        Text(
          'tracking.no_trip'.tr(),
          style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              color: Colors.grey),
        ),
        const SizedBox(height: 4),
        Text(
          'Freight #$freightId',
          style: const TextStyle(fontSize: 12, color: Colors.grey),
        ),
      ],
    );
  }
}

// ── Info row ──────────────────────────────────────────────────────────────

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
      children: [
        Icon(icon, size: 16, color: Colors.grey[500]),
        const SizedBox(width: 8),
        Text(
          label,
          style: TextStyle(fontSize: 13, color: Colors.grey[600]),
        ),
        const Spacer(),
        Text(
          value,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: valueColor ?? const Color(0xFF111827),
          ),
        ),
      ],
    );
  }
}
