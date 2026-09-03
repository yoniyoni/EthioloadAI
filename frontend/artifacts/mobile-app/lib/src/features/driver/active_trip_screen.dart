import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:go_router/go_router.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../../services/geofence_service.dart';
import '../../services/location_service.dart';
import '../../services/navigation_launcher.dart';
import '../../utils/weight_format.dart';

// ── Amharic city name mapping for the main Ethiopian corridors ────────────
const _amharicNames = <String, String>{
  'Addis Ababa': 'አዲስ አበባ',
  'Gondar': 'ጎንደር',
  'Bahir Dar': 'ባህር ዳር',
  'Debre Tabor': 'ደብረ ታቦር',
  'Debre Markos': 'ደብረ ማርቆስ',
  'Humera': 'ሁመራ',
  'Axum': 'አክሱም',
  'Mekelle': 'መቀሌ',
  'Dessie': 'ደሴ',
  'Woldia': 'ወልዲያ',
  'Diredawa': 'ድሬዳዋ',
  'Hawassa': 'ሃዋሳ',
  'Jimma': 'ጅማ',
  'Nekemte': 'ነቀምት',
  'Gambella': 'ጋምቤላ',
  'Jijiga': 'ጅጅጋ',
  'Adama': 'አዳማ',
  'Bishoftu': 'ቢሾፍቱ',
  'Shashamane': 'ሻሸመኔ',
  'Arba Minch': 'አርባ ምንጭ',
};

String _amharic(String city) => _amharicNames[city] ?? city;

// ── Providers ─────────────────────────────────────────────────────────────

// StateNotifier so we can update stops locally after API calls
class ActiveTripNotifier extends StateNotifier<AsyncValue<Trip>> {
  final TripRepository _repo;
  final int tripId;

  ActiveTripNotifier(this._repo, this.tripId)
      : super(const AsyncValue.loading()) {
    _load();
  }

  Future<void> _load() async {
    state = const AsyncValue.loading();
    try {
      final trip = await _repo.get(tripId);
      state = AsyncValue.data(trip);
    } catch (e, s) {
      state = AsyncValue.error(e, s);
    }
  }

  Future<void> refresh() => _load();

  Future<void> arriveAtStop(int stopId) async {
    await _repo.arriveAtStop(tripId, stopId);
    await _load();
  }

  Future<void> loadAtStop(int stopId) async {
    await _repo.loadAtStop(tripId, stopId);
    await _load();
  }

  Future<void> completeStop(int stopId) async {
    await _repo.completeStop(tripId, stopId);
    await _load();
  }

  Future<TripStop> addStop({
    required int stopOrder,
    required String locationName,
    required double agreedPrice,
    int? cargoRequestId,
    String? notes,
  }) async {
    final stop = await _repo.addStop(
      tripId,
      stopOrder: stopOrder,
      locationName: locationName,
      agreedPrice: agreedPrice,
      cargoRequestId: cargoRequestId,
      notes: notes,
    );
    await _load();
    return stop;
  }

  Future<(List<ConsolidationCandidate>, double?)> getConsolidationCandidates(
          {double? radiusKm}) =>
      _repo.getConsolidationCandidates(tripId, radiusKm: radiusKm);
}

final activeTripProvider = StateNotifierProvider.autoDispose
    .family<ActiveTripNotifier, AsyncValue<Trip>, int>(
  (ref, tripId) => ActiveTripNotifier(ref.read(tripRepositoryProvider), tripId),
);

// ── Screen ────────────────────────────────────────────────────────────────

class ActiveTripScreen extends ConsumerWidget {
  final int tripId;
  const ActiveTripScreen({super.key, required this.tripId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tripAsync = ref.watch(activeTripProvider(tripId));

    return Scaffold(
      backgroundColor: const Color(0xFFF9FAFB),
      appBar: AppBar(
        backgroundColor: const Color(0xFF111827),
        foregroundColor: Colors.white,
        title: const Text('Active Trip',
            style: TextStyle(fontWeight: FontWeight.w600)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () =>
                ref.read(activeTripProvider(tripId).notifier).refresh(),
          ),
        ],
      ),
      body: tripAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Error: $e')),
        data: (trip) => _TripBody(trip: trip, tripId: tripId),
      ),
    );
  }
}

class _TripBody extends ConsumerStatefulWidget {
  final Trip trip;
  final int tripId;
  const _TripBody({required this.trip, required this.tripId});

  @override
  ConsumerState<_TripBody> createState() => _TripBodyState();
}

class _TripBodyState extends ConsumerState<_TripBody> {
  bool _completing = false;
  bool _navigating = false;
  bool _showSuccess = false;
  String? _paymentMethod;
  double _amountEarned = 0;

  // ── Geofencing state (pickup / destination arrival gating) ─────────────
  StreamSubscription<Position>? _positionSub;
  bool _reachedPickup = false;
  bool _reachedDestination = false;
  double? _pickupLat;
  double? _pickupLng;
  double? _destLat;
  double? _destLng;
  double? _distanceToTargetM;
  DateTime? _lastPositionAt;
  bool _confirmingPickup = false;
  bool _gpsUnavailable = false;

  @override
  void initState() {
    super.initState();
    _initGeofencing();
  }

  @override
  void dispose() {
    _positionSub?.cancel();
    super.dispose();
  }

  Future<void> _initGeofencing() async {
    try {
      final loc =
          await ref.read(tripRepositoryProvider).getLocation(widget.tripId);
      _pickupLat = loc.pickupLat;
      _pickupLng = loc.pickupLng;
      _destLat = loc.destinationLat;
      _destLng = loc.destinationLng;
    } catch (_) {
      // best-effort — the distance readout / gating just won't show yet
    }

    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      if (mounted) setState(() => _gpsUnavailable = true);
      return;
    }

    var perm = await Geolocator.checkPermission();
    if (perm == LocationPermission.denied) {
      perm = await Geolocator.requestPermission();
    }
    if (perm == LocationPermission.denied ||
        perm == LocationPermission.deniedForever) {
      if (mounted) setState(() => _gpsUnavailable = true);
      return;
    }

    // Seed an immediate reading via a one-shot fix rather than waiting on
    // the stream's first callback — a driver who is already stationary at
    // the pickup point (e.g. re-opening this screen after it was
    // backgrounded) may not move the 20m the stream's distanceFilter
    // requires before it emits again, which would otherwise leave the
    // "Start Trip" button stuck disabled with no further GPS events to
    // re-check it.
    try {
      final pos = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );
      _onPosition(pos);
    } catch (_) {
      // best-effort — the stream below will populate it once available
    }

    _positionSub = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 20,
      ),
    ).listen(_onPosition, onError: (_) {
      if (mounted) setState(() => _gpsUnavailable = true);
    });
  }

  void _onPosition(Position pos) {
    _lastPositionAt = DateTime.now();
    _gpsUnavailable = false;

    final targetLat = widget.trip.isPickedUp ? _destLat : _pickupLat;
    final targetLng = widget.trip.isPickedUp ? _destLng : _pickupLng;
    if (targetLat != null && targetLng != null) {
      final distance = GeofenceService.distanceToTarget(
        currentLat: pos.latitude,
        currentLng: pos.longitude,
        targetLat: targetLat,
        targetLng: targetLng,
      );
      _distanceToTargetM = distance;

      final radius = widget.trip.isPickedUp
          ? kDestinationGeofenceRadiusMeters
          : kPickupGeofenceRadiusMeters;
      if (distance <= radius) {
        if (widget.trip.isPickedUp) {
          _reachedDestination = true;
        } else {
          _reachedPickup = true;
        }
      }
    }

    if (mounted) setState(() {});
  }

  /// Manual fallback for when automatic geofence detection can't confirm
  /// arrival — e.g. the pickup/destination point is only city-level
  /// precision (no exact GPS pin was captured when the cargo was booked),
  /// GPS drift/signal loss, or permission timing. Always available so the
  /// driver is never permanently blocked by location data outside their
  /// control.
  Future<void> _confirmArrivalManually({required bool isPickup}) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(
            isPickup ? 'Confirm pickup arrival?' : 'Confirm delivery arrival?'),
        content: Text(
          isPickup
              ? "Only confirm this if you're actually at the pickup location. "
                  "Use this if GPS isn't detecting your arrival automatically."
              : "Only confirm this if you're actually at the delivery location. "
                  "Use this if GPS isn't detecting your arrival automatically.",
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Confirm'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() {
      if (isPickup) {
        _reachedPickup = true;
      } else {
        _reachedDestination = true;
      }
    });
  }

  String _secondsAgoLabel(DateTime at) {
    final secs = DateTime.now().difference(at).inSeconds;
    if (secs < 60) return '${secs}s ago';
    return '${(secs / 60).floor()}m ago';
  }

  Future<void> _confirmPickup() async {
    setState(() => _confirmingPickup = true);
    try {
      await ref.read(tripRepositoryProvider).confirmPickup(widget.tripId);
      if (!mounted) return;
      await ref.read(activeTripProvider(widget.tripId).notifier).refresh();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(e.toString()),
          backgroundColor: Colors.red.shade700,
        ));
      }
    } finally {
      if (mounted) setState(() => _confirmingPickup = false);
    }
  }

  // Launches turn-by-turn in the driver's own navigation app (Google Maps,
  // then Waze) and falls back to the in-app OSRM/MapTiler NavigationScreen
  // (pushed via `fallbackRoute`) only if neither is installed. Asserts live
  // GPS tracking is running first — the foreground service pushes location
  // independent of which app is in the foreground, so this guarantees the
  // backend keeps receiving pings while the driver is inside Maps/Waze.
  Future<void> _launchNavigation({
    required double lat,
    required double lng,
    required String label,
    required String fallbackRoute,
  }) async {
    if (!LocationService.isLiveTracking) {
      await LocationService.startTracking(ref, tripOngoing: true);
    }
    if (!mounted) return;

    final launched = await NavigationLauncher.launchExternalNavigation(
      lat: lat,
      lng: lng,
      label: label,
    );
    if (launched || !mounted) return;

    context.push(fallbackRoute);
  }

  Future<void> _onNavigateToPickup() async {
    setState(() => _navigating = true);
    try {
      final loc =
          await ref.read(tripRepositoryProvider).getLocation(widget.tripId);
      if (!mounted) return;

      if (loc.pickupLat == null || loc.pickupLng == null) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Pickup coordinates not available yet.'),
        ));
        return;
      }

      final params = StringBuffer('/navigate?dest_lat=${loc.pickupLat}'
          '&dest_lng=${loc.pickupLng}'
          '&dest_name=${Uri.encodeComponent('Pickup')}');
      if (loc.hasPosition) {
        params.write('&orig_lat=${loc.currentLat}&orig_lng=${loc.currentLng}');
      }

      await _launchNavigation(
        lat: loc.pickupLat!,
        lng: loc.pickupLng!,
        label: 'Pickup',
        fallbackRoute: params.toString(),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Could not load navigation data. Try again.'),
        ));
      }
    } finally {
      if (mounted) setState(() => _navigating = false);
    }
  }

  Future<void> _onNavigate() async {
    setState(() => _navigating = true);
    try {
      final loc =
          await ref.read(tripRepositoryProvider).getLocation(widget.tripId);
      if (!mounted) return;

      if (!loc.hasDestination) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Destination coordinates not available yet.'),
        ));
        return;
      }

      final destName = loc.destination ?? widget.trip.destination ?? '';
      final params = StringBuffer('/navigate?dest_lat=${loc.destinationLat}'
          '&dest_lng=${loc.destinationLng}'
          '&dest_name=${Uri.encodeComponent(destName)}');
      if (loc.hasPosition) {
        params.write('&orig_lat=${loc.currentLat}&orig_lng=${loc.currentLng}');
      }

      await _launchNavigation(
        lat: loc.destinationLat!,
        lng: loc.destinationLng!,
        label: destName,
        fallbackRoute: params.toString(),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Could not load navigation data. Try again.'),
        ));
      }
    } finally {
      if (mounted) setState(() => _navigating = false);
    }
  }

  Future<void> _completeTrip() async {
    setState(() => _completing = true);
    try {
      final completedTrip =
          await ref.read(tripRepositoryProvider).complete(widget.tripId);
      // Drop back to idle-interval pinging instead of going fully dark —
      // "Go to My Jobs" pops back to an already-built DriverDashboardScreen
      // instance, so its initState (the only other place tracking starts)
      // won't fire again to pick this back up.
      unawaited(LocationService.startTracking(ref));
      if (!mounted) return;

      // Payment already happened upfront (before this trip could even
      // start — see the backend's trip-start payment gate), so there's
      // nothing left to self-report here. The earnings figure below is the
      // same math the old payment-recording step used, just without
      // re-collecting a payment method that's already settled.
      final price = completedTrip.bookingEstimatedPrice ?? 0.0;
      final commission = completedTrip.bookingCommissionFee ?? (price * 0.02);

      setState(() {
        _completing = false;
        _showSuccess = true;
        _paymentMethod = 'chapa';
        _amountEarned = price - commission;
      });
    } catch (e) {
      if (mounted) {
        setState(() => _completing = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(e.toString()),
          backgroundColor: Colors.red.shade700,
        ));
      }
    }
  }

  void _showAddStopSheet(BuildContext context, int nextOrder) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _AddStopSheet(
        tripId: widget.tripId,
        nextOrder: nextOrder,
        notifier: ref.read(activeTripProvider(widget.tripId).notifier),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_showSuccess) {
      return _buildSuccess();
    }

    final stops = widget.trip.stops;
    final progress = widget.trip.totalStops > 0
        ? widget.trip.completedStops / widget.trip.totalStops
        : 0.0;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _RouteSummaryCard(trip: widget.trip, progress: progress),
        const SizedBox(height: 16),
        if (stops.isNotEmpty) ...[
          const _SectionLabel('Stop Timeline'),
          const SizedBox(height: 8),
          ...stops.asMap().entries.map((entry) {
            final idx = entry.key;
            final stop = entry.value;
            final isLast = idx == stops.length - 1;
            return _StopTimelineTile(
              stop: stop,
              isLast: isLast,
              tripId: widget.tripId,
            );
          }),
          const SizedBox(height: 16),
        ],
        if (widget.trip.tripStatus == 'ongoing' && !widget.trip.isPickedUp) ...[
          // Phase A: driver has not yet picked up the cargo — must reach the
          // pickup geofence before the trip can proceed.
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: const Color(0xFFEFF6FF),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFFBFDBFE)),
            ),
            child: Row(
              children: [
                const Icon(Icons.social_distance_rounded,
                    color: Color(0xFF1565C0), size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _reachedPickup
                            ? 'Within pickup zone'
                            : _gpsUnavailable
                                ? 'GPS unavailable — use manual confirmation below'
                                : _distanceToTargetM != null
                                    ? 'Distance to pickup: ${(_distanceToTargetM! / 1000).toStringAsFixed(1)} km'
                                    : 'Locating pickup point...',
                        style: const TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF1565C0)),
                      ),
                      if (_lastPositionAt != null)
                        Text(
                          'GPS updated ${_secondsAgoLabel(_lastPositionAt!)}',
                          style: const TextStyle(
                              fontSize: 11, color: Color(0xFF6B7280)),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _navigating ? null : _onNavigateToPickup,
              icon: _navigating
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.navigation_rounded, size: 20),
              label: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Navigate to Pickup',
                      style:
                          TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                ],
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF1565C0),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: (_confirmingPickup || !_reachedPickup)
                  ? null
                  : _confirmPickup,
              icon: _confirmingPickup
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.flag_rounded, size: 20),
              label: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Start Trip',
                      style:
                          TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                  Text('ጉዞ ጀምር', style: TextStyle(fontSize: 11)),
                ],
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF16A34A),
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFFE5E7EB),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
            ),
          ),
          if (!_reachedPickup) ...[
            const SizedBox(height: 6),
            const Text(
              'Get within range of the pickup point to enable this button.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 11, color: Color(0xFF6B7280)),
            ),
            const SizedBox(height: 8),
            Center(
              child: TextButton(
                onPressed: () => _confirmArrivalManually(isPickup: true),
                child: const Text(
                  "Already there? Confirm arrival manually",
                  style: TextStyle(
                      fontSize: 12, decoration: TextDecoration.underline),
                ),
              ),
            ),
          ],
        ],
        if (widget.trip.tripStatus == 'ongoing' && widget.trip.isPickedUp) ...[
          // Phase B: cargo picked up, en route to destination
          // Navigate button — opens OSRM navigation to destination
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _navigating ? null : _onNavigate,
              icon: _navigating
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.navigation_rounded, size: 20),
              label: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Navigate / መንገድ ፈልግ',
                      style:
                          TextStyle(fontSize: 14, fontWeight: FontWeight.w700)),
                ],
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF1565C0),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
            ),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () => _showAddStopSheet(context, stops.length + 1),
            icon: const Icon(Icons.add_location_alt_outlined),
            label: const Text('Add Another Stop'),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF2563EB),
              side: const BorderSide(color: Color(0xFF2563EB)),
              padding: const EdgeInsets.symmetric(vertical: 14),
              minimumSize: const Size(double.infinity, 48),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed:
                  (_completing || !_reachedDestination) ? null : _completeTrip,
              icon: _completing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.flag_rounded, size: 20),
              label: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Complete Trip',
                      style:
                          TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                  Text('ጉዞ ጨርስ', style: TextStyle(fontSize: 11)),
                ],
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF16A34A),
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFFE5E7EB),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
            ),
          ),
          if (!_reachedDestination) ...[
            const SizedBox(height: 6),
            Text(
              _gpsUnavailable
                  ? 'GPS unavailable — use manual confirmation below'
                  : _distanceToTargetM != null
                      ? 'Distance to destination: ${(_distanceToTargetM! / 1000).toStringAsFixed(1)} km'
                      : 'Get within range of the destination to enable this button.',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 11, color: Color(0xFF6B7280)),
            ),
            const SizedBox(height: 8),
            Center(
              child: TextButton(
                onPressed: () => _confirmArrivalManually(isPickup: false),
                child: const Text(
                  "Already there? Confirm arrival manually",
                  style: TextStyle(
                      fontSize: 12, decoration: TextDecoration.underline),
                ),
              ),
            ),
          ],
        ],
        const SizedBox(height: 12),
      ],
    );
  }

  Widget _buildSuccess() {
    final formatted = 'ETB ${_amountEarned.toStringAsFixed(0).replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
          (m) => '${m[1]},',
        )}';

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          const SizedBox(height: 48),
          Container(
            width: 96,
            height: 96,
            decoration: const BoxDecoration(
              color: Color(0xFF16A34A),
              shape: BoxShape.circle,
            ),
            child:
                const Icon(Icons.check_rounded, color: Colors.white, size: 56),
          ),
          const SizedBox(height: 24),
          const Text(
            'Trip Complete!',
            style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w800,
                color: Color(0xFF111827)),
            textAlign: TextAlign.center,
          ),
          const Text(
            'ጉዞ ተጠናቋል!',
            style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: Color(0xFF6B7280)),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 10),
          Text(
            '${widget.trip.startLocation ?? ''} → ${widget.trip.destination ?? ''}',
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 14, color: Color(0xFF9CA3AF)),
          ),
          const SizedBox(height: 28),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.07),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              children: [
                const Text(
                  "You'll Earn / የሚያገኙት ገቢ",
                  style: TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
                ),
                const SizedBox(height: 8),
                Text(
                  formatted,
                  style: const TextStyle(
                    fontSize: 40,
                    fontWeight: FontWeight.w900,
                    color: Color(0xFFF59E0B),
                    letterSpacing: -1,
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.payments_outlined,
                        size: 16, color: Color(0xFF6B7280)),
                    const SizedBox(width: 6),
                    Text(
                      'via ${_methodLabel(_paymentMethod ?? '')}',
                      style: const TextStyle(
                          fontSize: 14, color: Color(0xFF6B7280)),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: () => Navigator.of(context).pop(),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF0F3D1A),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
              child: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Go to My Jobs',
                      style:
                          TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
                  Text('ወደ ስራዎቼ', style: TextStyle(fontSize: 11)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}

// ── Route summary card ─────────────────────────────────────────────────────

class _RouteSummaryCard extends StatelessWidget {
  final Trip trip;
  final double progress;
  const _RouteSummaryCard({required this.trip, required this.progress});

  @override
  Widget build(BuildContext context) {
    final totalFormatted = trip.totalAmount != null
        ? 'ETB ${trip.totalAmount!.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},')}'
        : null;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.06), blurRadius: 8)
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.local_shipping,
                  color: Color(0xFF2563EB), size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '${trip.startLocation ?? 'Origin'} → ${trip.destination ?? 'Destination'}',
                  style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 15,
                      color: Color(0xFF111827)),
                ),
              ),
              if (trip.isMultiStop)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: const Color(0xFFEFF6FF),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Text('Multi-Stop',
                      style: TextStyle(
                          color: Color(0xFF2563EB),
                          fontSize: 11,
                          fontWeight: FontWeight.w600)),
                ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              _InfoChip(
                  icon: Icons.place_outlined,
                  label: '${trip.completedStops}/${trip.totalStops} stops'),
              const SizedBox(width: 8),
              if (totalFormatted != null)
                _InfoChip(icon: Icons.payments_outlined, label: totalFormatted),
            ],
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 8,
              backgroundColor: const Color(0xFFE5E7EB),
              valueColor:
                  const AlwaysStoppedAnimation<Color>(Color(0xFF16A34A)),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '${(progress * 100).toInt()}% complete',
            style: const TextStyle(fontSize: 12, color: Color(0xFF6B7280)),
          ),
        ],
      ),
    );
  }
}

// ── Stop timeline tile ─────────────────────────────────────────────────────

class _StopTimelineTile extends ConsumerStatefulWidget {
  final TripStop stop;
  final bool isLast;
  final int tripId;
  const _StopTimelineTile({
    required this.stop,
    required this.isLast,
    required this.tripId,
  });

  @override
  ConsumerState<_StopTimelineTile> createState() => _StopTimelineTileState();
}

class _StopTimelineTileState extends ConsumerState<_StopTimelineTile> {
  bool _busy = false;

  Future<void> _act(Future<void> Function() fn) async {
    setState(() => _busy = true);
    try {
      await fn();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final stop = widget.stop;
    final notifier = ref.read(activeTripProvider(widget.tripId).notifier);

    final dotColor = switch (stop.status) {
      'completed' => const Color(0xFF16A34A),
      'arrived' => const Color(0xFFF59E0B),
      'loaded' => const Color(0xFF2563EB),
      _ => const Color(0xFFD1D5DB),
    };

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Timeline track
          SizedBox(
            width: 32,
            child: Column(
              children: [
                Container(
                  width: 20,
                  height: 20,
                  decoration: BoxDecoration(
                    color: dotColor,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: Text(
                      '${stop.stopOrder}',
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.w700),
                    ),
                  ),
                ),
                if (!widget.isLast)
                  Expanded(
                    child: Container(
                      width: 2,
                      color: const Color(0xFFE5E7EB),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          // Stop card
          Expanded(
            child: Container(
              margin: EdgeInsets.only(bottom: widget.isLast ? 0 : 12),
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: const Color(0xFFE5E7EB)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${_amharic(stop.locationName)} (${stop.locationName})',
                          style: const TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                              color: Color(0xFF111827)),
                        ),
                      ),
                      _StatusBadge(stop.status),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    stop.agreedPriceFormatted,
                    style: const TextStyle(
                        color: Color(0xFF2563EB),
                        fontWeight: FontWeight.w700,
                        fontSize: 14),
                  ),
                  if (stop.cargoMaterial != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      '${stop.cargoMaterial}${stop.cargoWeight != null ? ' · ${WeightFormat.display(stop.cargoWeight!)}' : ''}',
                      style: const TextStyle(
                          fontSize: 12, color: Color(0xFF6B7280)),
                    ),
                  ],
                  if (stop.notes != null && stop.notes!.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(stop.notes!,
                        style: const TextStyle(
                            fontSize: 12, color: Color(0xFF9CA3AF))),
                  ],
                  const SizedBox(height: 10),
                  // Action buttons
                  if (_busy)
                    const Center(
                        child: SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2)))
                  else
                    _StopActions(
                      stop: stop,
                      onArrive: () =>
                          _act(() => notifier.arriveAtStop(stop.id)),
                      onLoad: () => _act(() => notifier.loadAtStop(stop.id)),
                      onComplete: () =>
                          _act(() => notifier.completeStop(stop.id)),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Stop action buttons ────────────────────────────────────────────────────

class _StopActions extends StatelessWidget {
  final TripStop stop;
  final VoidCallback onArrive;
  final VoidCallback onLoad;
  final VoidCallback onComplete;
  const _StopActions({
    required this.stop,
    required this.onArrive,
    required this.onLoad,
    required this.onComplete,
  });

  @override
  Widget build(BuildContext context) {
    if (stop.isCompleted) {
      return const Row(
        children: [
          Icon(Icons.check_circle, color: Color(0xFF16A34A), size: 18),
          SizedBox(width: 6),
          Text('Completed',
              style: TextStyle(
                  color: Color(0xFF16A34A),
                  fontWeight: FontWeight.w600,
                  fontSize: 13)),
        ],
      );
    }

    if (stop.isPending) {
      return SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: onArrive,
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFFF59E0B),
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(vertical: 10),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          ),
          child: const Text('Mark Arrived',
              style: TextStyle(fontWeight: FontWeight.w600)),
        ),
      );
    }

    if (stop.isArrived) {
      return Row(
        children: [
          Expanded(
            child: ElevatedButton(
              onPressed: onLoad,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2563EB),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 10),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('Cargo Loaded',
                  style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: OutlinedButton(
              onPressed: onComplete,
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFF16A34A),
                side: const BorderSide(color: Color(0xFF16A34A)),
                padding: const EdgeInsets.symmetric(vertical: 10),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: const Text('Complete Stop',
                  style: TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      );
    }

    // loaded
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: onComplete,
        style: ElevatedButton.styleFrom(
          backgroundColor: const Color(0xFF16A34A),
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(vertical: 10),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: const Text('Complete Stop',
            style: TextStyle(fontWeight: FontWeight.w600)),
      ),
    );
  }
}

// ── Add Stop bottom sheet ──────────────────────────────────────────────────

class _AddStopSheet extends StatefulWidget {
  final int tripId;
  final int nextOrder;
  final ActiveTripNotifier notifier;
  const _AddStopSheet({
    required this.tripId,
    required this.nextOrder,
    required this.notifier,
  });

  @override
  State<_AddStopSheet> createState() => _AddStopSheetState();
}

class _AddStopSheetState extends State<_AddStopSheet> {
  // 'nearby' = pick a real posted cargo request that fits remaining
  // capacity; 'custom' = the original free-text ad-hoc stop.
  String _mode = 'nearby';

  // ── Nearby-cargo mode state ──────────────────────────────────────────────
  bool _candidatesLoading = true;
  String? _candidatesError;
  List<ConsolidationCandidate>? _candidates;
  double? _remainingCapacity;
  ConsolidationCandidate? _selectedCandidate;
  double _radiusKm = 150; // matches the backend's own default on first load
  static const _radiusOptions = <double>[50, 100, 150, 250];
  final _candidatePriceCtrl = TextEditingController();
  final _candidateNotesCtrl = TextEditingController();

  // ── Custom-stop mode state (unchanged) ───────────────────────────────────
  final _formKey = GlobalKey<FormState>();
  final _priceCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();
  String? _selectedCity;

  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _loadCandidates();
  }

  Future<void> _loadCandidates() async {
    setState(() {
      _candidatesLoading = true;
      _candidatesError = null;
    });
    try {
      final (candidates, remaining) = await widget.notifier
          .getConsolidationCandidates(radiusKm: _radiusKm);
      if (!mounted) return;
      setState(() {
        _candidates = candidates;
        _remainingCapacity = remaining;
        _candidatesLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _candidatesError = e.toString();
        _candidatesLoading = false;
      });
    }
  }

  void _selectCandidate(ConsolidationCandidate c) {
    setState(() {
      _selectedCandidate = c;
      _candidatePriceCtrl.text =
          c.budget != null ? c.budget!.toStringAsFixed(0) : '';
    });
  }

  Future<void> _submitCandidate() async {
    final selected = _selectedCandidate;
    if (selected == null) return;
    final price = double.tryParse(_candidatePriceCtrl.text.replaceAll(',', ''));
    if (price == null || price <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter a valid agreed price')),
      );
      return;
    }
    setState(() => _loading = true);
    try {
      await widget.notifier.addStop(
        stopOrder: widget.nextOrder,
        locationName: selected.pickupLocation,
        agreedPrice: price,
        cargoRequestId: selected.id,
        notes: _candidateNotesCtrl.text.isNotEmpty
            ? _candidateNotesCtrl.text
            : null,
      );
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  static const _corridorCities = [
    'Gondar',
    'Bahir Dar',
    'Debre Tabor',
    'Debre Markos',
    'Humera',
    'Axum',
    'Mekelle',
    'Dessie',
    'Woldia',
    'Diredawa',
    'Hawassa',
    'Jimma',
    'Nekemte',
    'Gambella',
    'Jijiga',
    'Adama',
    'Bishoftu',
    'Shashamane',
    'Arba Minch',
    'Addis Ababa',
  ];

  @override
  void dispose() {
    _priceCtrl.dispose();
    _notesCtrl.dispose();
    _candidatePriceCtrl.dispose();
    _candidateNotesCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      await widget.notifier.addStop(
        stopOrder: widget.nextOrder,
        locationName: _selectedCity!,
        agreedPrice: double.parse(_priceCtrl.text.replaceAll(',', '')),
        notes: _notesCtrl.text.isNotEmpty ? _notesCtrl.text : null,
      );
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Add Stop',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text('Stop #${widget.nextOrder}',
              style: const TextStyle(color: Color(0xFF6B7280), fontSize: 13)),
          const SizedBox(height: 16),
          // Mode toggle
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: const Color(0xFFF3F4F6),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(children: [
              Expanded(child: _ModeTab(
                label: 'Nearby Cargo',
                selected: _mode == 'nearby',
                onTap: () => setState(() => _mode = 'nearby'),
              )),
              Expanded(child: _ModeTab(
                label: 'Custom Stop',
                selected: _mode == 'custom',
                onTap: () => setState(() => _mode = 'custom'),
              )),
            ]),
          ),
          const SizedBox(height: 16),
          if (_mode == 'nearby') _buildNearbyPanel() else _buildCustomPanel(),
        ],
      ),
    );
  }

  Widget _buildNearbyPanel() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_remainingCapacity != null)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: const Color(0xFFEFF6FF),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFFBFDBFE)),
            ),
            child: Text(
              '${WeightFormat.display(_remainingCapacity!)} of space left on your truck',
              style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF1D4ED8)),
            ),
          ),
        const Text('Search radius',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Color(0xFF6B7280))),
        const SizedBox(height: 6),
        SizedBox(
          height: 32,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: _radiusOptions.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (_, i) {
              final r = _radiusOptions[i];
              final selected = r == _radiusKm;
              return ChoiceChip(
                label: Text('${r.toInt()} km', style: const TextStyle(fontSize: 12)),
                selected: selected,
                selectedColor: const Color(0xFF2563EB),
                labelStyle: TextStyle(
                  color: selected ? Colors.white : const Color(0xFF374151),
                ),
                backgroundColor: const Color(0xFFF3F4F6),
                onSelected: (_) {
                  setState(() {
                    _radiusKm = r;
                    _selectedCandidate = null;
                  });
                  _loadCandidates();
                },
              );
            },
          ),
        ),
        const SizedBox(height: 12),
        if (_candidatesLoading)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(child: CircularProgressIndicator()),
          )
        else if (_candidatesError != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Column(children: [
              Text(_candidatesError!,
                  style: const TextStyle(color: Colors.red, fontSize: 12)),
              const SizedBox(height: 8),
              OutlinedButton(
                  onPressed: _loadCandidates, child: const Text('Retry')),
            ]),
          )
        else if (_candidates == null || _candidates!.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Text(
              'No nearby cargo fits your remaining capacity right now. '
              'Use "Custom Stop" to add a manual stop instead.',
              style: TextStyle(fontSize: 12, color: Color(0xFF6B7280)),
            ),
          )
        else ...[
          ..._candidates!.map((c) => _CandidateCard(
                candidate: c,
                selected: _selectedCandidate?.id == c.id,
                onTap: () => _selectCandidate(c),
              )),
          if (_selectedCandidate != null) ...[
            const SizedBox(height: 8),
            TextField(
              controller: _candidatePriceCtrl,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: 'Agreed Price (ETB)',
                prefixText: 'ETB ',
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _candidateNotesCtrl,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: 'Notes (optional)',
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _loading ? null : _submitCandidate,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2563EB),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                ),
                child: _loading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('Add Stop',
                        style: TextStyle(
                            fontWeight: FontWeight.w600, fontSize: 15)),
              ),
            ),
          ],
        ],
      ],
    );
  }

  Widget _buildCustomPanel() {
    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // City picker
          DropdownButtonFormField<String>(
            initialValue: _selectedCity,
            decoration: InputDecoration(
              labelText: 'City / Location',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
            ),
            items: _corridorCities
                .map((c) => DropdownMenuItem(
                    value: c, child: Text('${_amharic(c)} ($c)')))
                .toList(),
            onChanged: (v) => setState(() => _selectedCity = v),
            validator: (v) => v == null ? 'Select a city' : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _priceCtrl,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              labelText: 'Agreed Price (ETB)',
              prefixText: 'ETB ',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
            ),
            validator: (v) {
              if (v == null || v.isEmpty) return 'Enter a price';
              final n = double.tryParse(v.replaceAll(',', ''));
              if (n == null || n <= 0) return 'Enter a valid price';
              return null;
            },
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _notesCtrl,
            maxLines: 2,
            decoration: InputDecoration(
              labelText: 'Notes (optional)',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
            ),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _loading ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2563EB),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
              child: _loading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Text('Add Stop',
                      style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
            ),
          ),
        ],
      ),
    );
  }
}

class _ModeTab extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  const _ModeTab({required this.label, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 9),
        decoration: BoxDecoration(
          color: selected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
          boxShadow: selected
              ? [BoxShadow(color: Colors.black.withValues(alpha: 0.08), blurRadius: 3)]
              : null,
        ),
        child: Text(
          label,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: selected ? const Color(0xFF111827) : const Color(0xFF6B7280),
          ),
        ),
      ),
    );
  }
}

class _CandidateCard extends StatelessWidget {
  final ConsolidationCandidate candidate;
  final bool selected;
  final VoidCallback onTap;
  const _CandidateCard({required this.candidate, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: selected ? const Color(0xFFEFF6FF) : Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected ? const Color(0xFF2563EB) : const Color(0xFFE5E7EB),
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(
              child: Text(
                '${candidate.pickupLocation} → ${candidate.destination}',
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
              ),
            ),
            if (selected)
              const Icon(Icons.check_circle_rounded, color: Color(0xFF2563EB), size: 18),
          ]),
          const SizedBox(height: 6),
          Wrap(spacing: 8, runSpacing: 4, children: [
            _MiniBadge(WeightFormat.display(candidate.weight)),
            _MiniBadge(candidate.materialType),
            if (candidate.distanceKm != null)
              _MiniBadge('${candidate.distanceKm!.toStringAsFixed(0)} km away'),
            if (candidate.budget != null)
              _MiniBadge('ETB ${candidate.budget!.toStringAsFixed(0)}'),
          ]),
          const SizedBox(height: 4),
          Text(candidate.shipperName,
              style: const TextStyle(fontSize: 11, color: Color(0xFF6B7280))),
        ]),
      ),
    );
  }
}

class _MiniBadge extends StatelessWidget {
  final String label;
  const _MiniBadge(this.label);

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        decoration: BoxDecoration(
          color: const Color(0xFFF3F4F6),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(label,
            style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: Color(0xFF374151))),
      );
}

// ── Payment method bottom sheet ────────────────────────────────────────────

String _methodLabel(String method) => switch (method) {
      'chapa' => 'Chapa',
      'cash' => 'Cash / ጥሬ ገንዘብ',
      'telebirr' => 'Telebirr',
      'cbe_birr' => 'CBE',
      'awash_bank' => 'Awash Bank',
      'dashen_bank' => 'Dashen Bank',
      'bank_transfer' => 'Bank Transfer / ሌላ ባንክ',
      _ => method,
    };

// ── Helper widgets ─────────────────────────────────────────────────────────

class _StatusBadge extends StatelessWidget {
  final String status;
  const _StatusBadge(this.status);

  @override
  Widget build(BuildContext context) {
    final (label, bg, fg) = switch (status) {
      'completed' => (
          'Completed',
          const Color(0xFFDCFCE7),
          const Color(0xFF16A34A)
        ),
      'arrived' => (
          'Arrived',
          const Color(0xFFFEF3C7),
          const Color(0xFFF59E0B)
        ),
      'loaded' => ('Loaded', const Color(0xFFEFF6FF), const Color(0xFF2563EB)),
      _ => ('Pending', const Color(0xFFF3F4F6), const Color(0xFF6B7280)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration:
          BoxDecoration(color: bg, borderRadius: BorderRadius.circular(20)),
      child: Text(label,
          style:
              TextStyle(color: fg, fontSize: 11, fontWeight: FontWeight.w600)),
    );
  }
}

class _InfoChip extends StatelessWidget {
  final IconData icon;
  final String label;
  const _InfoChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: const Color(0xFFEFF6FF),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: const Color(0xFF2563EB)),
          const SizedBox(width: 4),
          Text(label,
              style: const TextStyle(
                  color: Color(0xFF2563EB),
                  fontSize: 12,
                  fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Text(text,
        style: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w600,
            color: Color(0xFF6B7280),
            letterSpacing: 0.5));
  }
}
