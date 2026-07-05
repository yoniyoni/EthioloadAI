import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

// ── Amharic city name mapping for the main Ethiopian corridors ────────────
const _amharicNames = <String, String>{
  'Addis Ababa':   'አዲስ አበባ',
  'Gondar':        'ጎንደር',
  'Bahir Dar':     'ባህር ዳር',
  'Debre Tabor':   'ደብረ ታቦር',
  'Debre Markos':  'ደብረ ማርቆስ',
  'Humera':        'ሁመራ',
  'Axum':          'አክሱም',
  'Mekelle':       'መቀሌ',
  'Dessie':        'ደሴ',
  'Woldia':        'ወልዲያ',
  'Diredawa':      'ድሬዳዋ',
  'Hawassa':       'ሃዋሳ',
  'Jimma':         'ጅማ',
  'Nekemte':       'ነቀምት',
  'Gambella':      'ጋምቤላ',
  'Jijiga':        'ጅጅጋ',
  'Adama':         'አዳማ',
  'Bishoftu':      'ቢሾፍቱ',
  'Shashamane':    'ሻሸመኔ',
  'Arba Minch':    'አርባ ምንጭ',
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
    String? notes,
  }) async {
    final stop = await _repo.addStop(
      tripId,
      stopOrder: stopOrder,
      locationName: locationName,
      agreedPrice: agreedPrice,
      notes: notes,
    );
    await _load();
    return stop;
  }
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
        title: const Text('Active Trip', style: TextStyle(fontWeight: FontWeight.w600)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.read(activeTripProvider(tripId).notifier).refresh(),
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
  bool _completing  = false;
  bool _navigating  = false;
  bool _showSuccess = false;
  String? _paymentMethod;
  double _amountEarned = 0;

  Future<void> _onNavigate() async {
    setState(() => _navigating = true);
    try {
      final loc = await ref
          .read(tripRepositoryProvider)
          .getLocation(widget.tripId);
      if (!mounted) return;

      if (!loc.hasDestination) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Destination coordinates not available yet.'),
        ));
        return;
      }

      final params = StringBuffer(
          '/navigate?dest_lat=${loc.destinationLat}'
          '&dest_lng=${loc.destinationLng}'
          '&dest_name=${Uri.encodeComponent(loc.destination ?? widget.trip.destination ?? '')}');
      if (loc.hasPosition) {
        params.write('&orig_lat=${loc.currentLat}&orig_lng=${loc.currentLng}');
      }

      if (mounted) context.push(params.toString());
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
      if (!mounted) return;

      final method = await showModalBottomSheet<String>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (_) => const _TripPaymentSheet(),
      );
      if (!mounted) return;

      if (method == null) {
        setState(() => _completing = false);
        return;
      }

      final price = completedTrip.bookingEstimatedPrice ?? 0.0;
      final commission =
          completedTrip.bookingCommissionFee ?? (price * 0.10);

      await ref.read(paymentRepositoryProvider).create(
            bookingId: completedTrip.bookingId,
            amount: price,
            paymentMethod: method,
          );
      if (!mounted) return;

      setState(() {
        _completing = false;
        _showSuccess = true;
        _paymentMethod = method;
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
            final idx    = entry.key;
            final stop   = entry.value;
            final isLast = idx == stops.length - 1;
            return _StopTimelineTile(
              stop: stop,
              isLast: isLast,
              tripId: widget.tripId,
            );
          }),
          const SizedBox(height: 16),
        ],

        if (widget.trip.tripStatus == 'ongoing') ...[
          // Navigate button — opens OSRM navigation to destination
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _navigating ? null : _onNavigate,
              icon: _navigating
                  ? const SizedBox(
                      width: 18, height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.navigation_rounded, size: 20),
              label: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('Navigate / መንገድ ፈልግ',
                      style: TextStyle(
                          fontSize: 14, fontWeight: FontWeight.w700)),
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
              onPressed: _completing ? null : _completeTrip,
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
                      style: TextStyle(
                          fontSize: 15, fontWeight: FontWeight.w700)),
                  Text('ጉዞ ጨርስ', style: TextStyle(fontSize: 11)),
                ],
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF16A34A),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
            ),
          ),
        ],
        const SizedBox(height: 12),
      ],
    );
  }

  Widget _buildSuccess() {
    final formatted =
        'ETB ${_amountEarned.toStringAsFixed(0).replaceAllMapped(
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
                  'Amount Earned / ያገኘህ ገቢ',
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
                      style: TextStyle(
                          fontSize: 15, fontWeight: FontWeight.w700)),
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
        ? 'ETB ${trip.totalAmount!.toStringAsFixed(0).replaceAllMapped(
            RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
            (m) => '${m[1]},')}'
        : null;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.06), blurRadius: 8)],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.local_shipping, color: Color(0xFF2563EB), size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  '${trip.startLocation ?? 'Origin'} → ${trip.destination ?? 'Destination'}',
                  style: const TextStyle(
                      fontWeight: FontWeight.w700, fontSize: 15,
                      color: Color(0xFF111827)),
                ),
              ),
              if (trip.isMultiStop)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
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
              valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF16A34A)),
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
    final stop    = widget.stop;
    final notifier = ref.read(activeTripProvider(widget.tripId).notifier);

    final dotColor = switch (stop.status) {
      'completed' => const Color(0xFF16A34A),
      'arrived'   => const Color(0xFFF59E0B),
      'loaded'    => const Color(0xFF2563EB),
      _           => const Color(0xFFD1D5DB),
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
                      '${stop.cargoMaterial}${stop.cargoWeight != null ? ' · ${stop.cargoWeight} tons' : ''}',
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
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
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
  final _formKey   = GlobalKey<FormState>();
  final _priceCtrl = TextEditingController();
  final _notesCtrl = TextEditingController();
  String? _selectedCity;
  bool _loading = false;

  static const _corridorCities = [
    'Gondar', 'Bahir Dar', 'Debre Tabor', 'Debre Markos',
    'Humera', 'Axum', 'Mekelle', 'Dessie', 'Woldia',
    'Diredawa', 'Hawassa', 'Jimma', 'Nekemte', 'Gambella',
    'Jijiga', 'Adama', 'Bishoftu', 'Shashamane', 'Arba Minch',
    'Addis Ababa',
  ];

  @override
  void dispose() {
    _priceCtrl.dispose();
    _notesCtrl.dispose();
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
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Add Stop',
                style:
                    TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text('Stop #${widget.nextOrder}',
                style: const TextStyle(
                    color: Color(0xFF6B7280), fontSize: 13)),
            const SizedBox(height: 16),
            // City picker
            DropdownButtonFormField<String>(
              initialValue: _selectedCity,
              decoration: InputDecoration(
                labelText: 'City / Location',
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8)),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12, vertical: 14),
              ),
              items: _corridorCities
                  .map((c) => DropdownMenuItem(
                      value: c,
                      child: Text('${_amharic(c)} ($c)')))
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
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8)),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12, vertical: 14),
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
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8)),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12, vertical: 14),
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
        ),
      ),
    );
  }
}

// ── Payment method bottom sheet ────────────────────────────────────────────

String _methodLabel(String method) => switch (method) {
      'cash'          => 'Cash / ጥሬ ገንዘብ',
      'telebirr'      => 'Telebirr',
      'cbe_birr'      => 'CBE',
      'awash_bank'    => 'Awash Bank',
      'dashen_bank'   => 'Dashen Bank',
      'bank_transfer' => 'Bank Transfer / ሌላ ባንክ',
      _               => method,
    };

class _TripPaymentSheet extends StatefulWidget {
  const _TripPaymentSheet();

  @override
  State<_TripPaymentSheet> createState() => _TripPaymentSheetState();
}

class _TripPaymentSheetState extends State<_TripPaymentSheet> {
  String? _selected;

  static const _options = [
    ('cash',          '💵', 'Cash / ጥሬ ገንዘብ'),
    ('telebirr',      '📱', 'Telebirr'),
    ('cbe_birr',      '🏦', 'CBE'),
    ('awash_bank',    '🏦', 'Awash Bank'),
    ('dashen_bank',   '🏦', 'Dashen Bank'),
    ('bank_transfer', '🏦', 'Other Bank / ሌላ ባንክ'),
  ];

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: const Color(0xFFE5E7EB),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          const Text(
            'How will payment be made?',
            style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: Color(0xFF111827)),
          ),
          const Text(
            'ክፍያ እንዴት ይፈጸማል?',
            style: TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
          ),
          const SizedBox(height: 16),
          ..._options.map((opt) {
            final (value, emoji, label) = opt;
            final selected = _selected == value;
            return GestureDetector(
              onTap: () => setState(() => _selected = value),
              child: Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.symmetric(
                    horizontal: 16, vertical: 14),
                decoration: BoxDecoration(
                  color: selected
                      ? const Color(0xFFEFF6FF)
                      : const Color(0xFFF9FAFB),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: selected
                        ? const Color(0xFF2563EB)
                        : const Color(0xFFE5E7EB),
                    width: selected ? 2 : 1,
                  ),
                ),
                child: Row(
                  children: [
                    Text(emoji, style: const TextStyle(fontSize: 20)),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        label,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: selected
                              ? const Color(0xFF2563EB)
                              : const Color(0xFF374151),
                        ),
                      ),
                    ),
                    if (selected)
                      const Icon(Icons.check_circle_rounded,
                          color: Color(0xFF2563EB), size: 20),
                  ],
                ),
              ),
            );
          }),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _selected == null
                  ? null
                  : () => Navigator.of(context).pop(_selected),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFFF59E0B),
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFFE5E7EB),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
              child: const Text(
                'Confirm Payment / ክፍያ አረጋግጥ',
                style:
                    TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Helper widgets ─────────────────────────────────────────────────────────

class _StatusBadge extends StatelessWidget {
  final String status;
  const _StatusBadge(this.status);

  @override
  Widget build(BuildContext context) {
    final (label, bg, fg) = switch (status) {
      'completed' => ('Completed', const Color(0xFFDCFCE7), const Color(0xFF16A34A)),
      'arrived'   => ('Arrived',   const Color(0xFFFEF3C7), const Color(0xFFF59E0B)),
      'loaded'    => ('Loaded',    const Color(0xFFEFF6FF), const Color(0xFF2563EB)),
      _           => ('Pending',   const Color(0xFFF3F4F6), const Color(0xFF6B7280)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration:
          BoxDecoration(color: bg, borderRadius: BorderRadius.circular(20)),
      child: Text(label,
          style: TextStyle(
              color: fg, fontSize: 11, fontWeight: FontWeight.w600)),
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
