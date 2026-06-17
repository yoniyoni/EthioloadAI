import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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

class _TripBody extends ConsumerWidget {
  final Trip trip;
  final int tripId;
  const _TripBody({required this.trip, required this.tripId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final stops = trip.stops;
    final progress = trip.totalStops > 0
        ? trip.completedStops / trip.totalStops
        : 0.0;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── Route summary card ─────────────────────────────────────────
        _RouteSummaryCard(trip: trip, progress: progress),
        const SizedBox(height: 16),

        // ── Stop timeline ──────────────────────────────────────────────
        if (stops.isNotEmpty) ...[
          const _SectionLabel('Stop Timeline'),
          const SizedBox(height: 8),
          ...stops.asMap().entries.map((entry) {
            final idx   = entry.key;
            final stop  = entry.value;
            final isLast = idx == stops.length - 1;
            return _StopTimelineTile(
              stop: stop,
              isLast: isLast,
              tripId: tripId,
            );
          }),
          const SizedBox(height: 16),
        ],

        // ── Add another stop button ────────────────────────────────────
        if (trip.tripStatus == 'ongoing')
          OutlinedButton.icon(
            onPressed: () => _showAddStopSheet(context, ref, stops.length + 1),
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
      ],
    );
  }

  void _showAddStopSheet(BuildContext context, WidgetRef ref, int nextOrder) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (_) => _AddStopSheet(
        tripId: tripId,
        nextOrder: nextOrder,
        notifier: ref.read(activeTripProvider(tripId).notifier),
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
