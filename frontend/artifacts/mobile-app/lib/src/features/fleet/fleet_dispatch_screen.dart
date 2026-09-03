import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';
import 'fleet_dashboard_screen.dart' show fleetDashboardProvider;
import '../../utils/weight_format.dart';
import '../../utils/ethiopian_date.dart';

// ── Palette ───────────────────────────────────────────────────────────────
const _green  = Color(0xFF0F3D1A);
const _amber  = Color(0xFFF59E0B);
const _blue   = Color(0xFF2563EB);
const _red    = Color(0xFFEF4444);
const _bg     = Color(0xFFF8FBF8);
const _border = Color(0xFFE5E7EB);

// ── Screen ────────────────────────────────────────────────────────────────

class FleetDispatchScreen extends ConsumerStatefulWidget {
  const FleetDispatchScreen({super.key});

  @override
  ConsumerState<FleetDispatchScreen> createState() => _FleetDispatchScreenState();
}

class _FleetDispatchScreenState extends ConsumerState<FleetDispatchScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text('Dispatch Center / ላካ ማዕከል',
            style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            onPressed: () {
              ref.invalidate(myBidsProvider);
              ref.invalidate(bookingListProvider);
              ref.invalidate(fleetDashboardProvider);
            },
          ),
        ],
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: _amber,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white60,
          labelStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
          tabs: const [
            Tab(text: 'Available Cargo'),
            Tab(text: 'My Bids'),
            Tab(text: 'Fleet Bookings'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: const [
          _AvailableCargoTab(),
          _MyBidsTab(),
          _FleetBookingsTab(),
        ],
      ),
    );
  }
}

// ── Available Cargo tab ──────────────────────────────────────────────────

class _AvailableCargoTab extends ConsumerStatefulWidget {
  const _AvailableCargoTab();

  @override
  ConsumerState<_AvailableCargoTab> createState() => _AvailableCargoTabState();
}

class _AvailableCargoTabState extends ConsumerState<_AvailableCargoTab> {
  final _cityCtrl = TextEditingController();
  double? _radiusKm;
  List<CargoRequest>? _results;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _search());
  }

  @override
  void dispose() {
    _cityCtrl.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final city = _cityCtrl.text.trim();
      final results = await ref.read(fleetRepositoryProvider).searchCargo(
            city: city.isEmpty ? null : city,
            radiusKm: _radiusKm,
          );
      if (mounted) {
        setState(() {
          _results = results;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _SearchBar(
          cityCtrl: _cityCtrl,
          radiusKm: _radiusKm,
          onRadiusChanged: (r) {
            setState(() => _radiusKm = r);
            _search();
          },
          onSearch: _search,
        ),
        Expanded(
          child: RefreshIndicator(
            color: _amber,
            onRefresh: _search,
            child: _loading
                ? const Center(child: CircularProgressIndicator(color: _green))
                : _error != null
                    ? Center(
                        child: _ErrorHint(message: _error!, onRetry: _search))
                    : (_results == null || _results!.isEmpty)
                        ? ListView(
                            children: const [
                              _EmptyHint(
                                icon: Icons.inventory_2_outlined,
                                title: 'No available cargo',
                                subtitle:
                                    'Try a wider radius, a different city, or check back later.',
                              ),
                            ],
                          )
                        : ListView.separated(
                            padding: const EdgeInsets.all(16),
                            itemCount: _results!.length,
                            separatorBuilder: (_, __) =>
                                const SizedBox(height: 12),
                            itemBuilder: (_, i) => _CargoCard(
                              cargo: _results![i],
                              onActionComplete: _search,
                            ),
                          ),
          ),
        ),
      ],
    );
  }
}

class _SearchBar extends StatelessWidget {
  final TextEditingController cityCtrl;
  final double? radiusKm;
  final ValueChanged<double?> onRadiusChanged;
  final VoidCallback onSearch;

  const _SearchBar({
    required this.cityCtrl,
    required this.radiusKm,
    required this.onRadiusChanged,
    required this.onSearch,
  });

  static const _radiusOptions = <double?>[null, 10, 25, 50];

  String _radiusLabel(double? r) => r == null ? 'Any' : '${r.toInt()} km';

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: cityCtrl,
                  textInputAction: TextInputAction.search,
                  onSubmitted: (_) => onSearch(),
                  decoration: InputDecoration(
                    isDense: true,
                    hintText: 'Search by city (e.g. Gondar)',
                    prefixIcon: const Icon(Icons.location_city, size: 20),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    contentPadding:
                        const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              ElevatedButton(
                onPressed: onSearch,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _green,
                  foregroundColor: Colors.white,
                  elevation: 0,
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                ),
                child: const Icon(Icons.search, size: 18),
              ),
            ],
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 32,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _radiusOptions.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (_, i) {
                final r = _radiusOptions[i];
                final selected = r == radiusKm;
                return ChoiceChip(
                  label: Text(_radiusLabel(r),
                      style: const TextStyle(fontSize: 12)),
                  selected: selected,
                  selectedColor: _green,
                  labelStyle: TextStyle(
                    color: selected ? Colors.white : const Color(0xFF374151),
                  ),
                  backgroundColor: const Color(0xFFF3F4F6),
                  onSelected: (_) => onRadiusChanged(r),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _CargoCard extends StatelessWidget {
  final CargoRequest cargo;
  final VoidCallback onActionComplete;
  const _CargoCard({required this.cargo, required this.onActionComplete});

  @override
  Widget build(BuildContext context) {
    final urgencyColor = switch (cargo.urgencyLevel) {
      'express' => _red,
      'high'    => Colors.orange,
      _         => _green,
    };

    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => _openDetail(context),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: _border, width: 0.5),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Icon(Icons.route, size: 16, color: _green),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                '${cargo.pickupLocation} → ${cargo.destination}',
                style: const TextStyle(
                    fontSize: 14, fontWeight: FontWeight.w700, color: Color(0xFF111827)),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: urgencyColor.withAlpha(20),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(cargo.urgencyLevel,
                  style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: urgencyColor)),
            ),
          ]),
          const SizedBox(height: 8),
          Wrap(spacing: 16, runSpacing: 4, children: [
            _Detail(Icons.scale_outlined, WeightFormat.display(cargo.weight)),
            _Detail(Icons.category_outlined, cargo.materialType),
            if (cargo.distanceKm != null)
              _Detail(Icons.social_distance_outlined,
                  '${cargo.distanceKm!.toStringAsFixed(0)} km away'),
            if (cargo.budget != null)
              _Detail(Icons.payments_outlined, 'ETB ${cargo.budget!.toStringAsFixed(0)}'),
            _Detail(
              cargo.priceType == 'fixed' ? Icons.lock_outline : Icons.gavel_outlined,
              cargo.priceType == 'fixed' ? 'Fixed price' : 'Negotiable',
            ),
          ]),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () => _openDetail(context),
              icon: const Icon(Icons.visibility_outlined, size: 16),
              label: const Text('View Details'),
              style: OutlinedButton.styleFrom(
                foregroundColor: _green,
                side: const BorderSide(color: _green),
                padding: const EdgeInsets.symmetric(vertical: 10),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
          ),
        ]),
      ),
    );
  }

  void _openDetail(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => _CargoDetailSheet(
        cargo: cargo,
        onActionComplete: onActionComplete,
      ),
    );
  }
}

// ── Cargo detail sheet ───────────────────────────────────────────────────

class _CargoDetailSheet extends StatelessWidget {
  final CargoRequest cargo;
  final VoidCallback onActionComplete;
  const _CargoDetailSheet({required this.cargo, required this.onActionComplete});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36, height: 4,
              margin: const EdgeInsets.only(bottom: 16),
              decoration:
                  BoxDecoration(color: _border, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const Text('Cargo Details', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          const SizedBox(height: 16),
          _DetailRow(Icons.trip_origin, 'Pickup', cargo.pickupLocation),
          _DetailRow(Icons.flag_outlined, 'Destination', cargo.destination),
          _DetailRow(Icons.category_outlined, 'Material', cargo.materialType),
          _DetailRow(Icons.scale_outlined, 'Weight', WeightFormat.display(cargo.weight)),
          if (cargo.vehicleTypeNeeded != null)
            _DetailRow(Icons.local_shipping_outlined, 'Vehicle needed', cargo.vehicleTypeNeeded!),
          _DetailRow(Icons.priority_high_rounded, 'Urgency', cargo.urgencyLevel),
          if (cargo.budget != null)
            _DetailRow(Icons.payments_outlined, 'Price',
                'ETB ${cargo.budget!.toStringAsFixed(0)} (${cargo.priceType})'),
          if (cargo.distanceKm != null)
            _DetailRow(Icons.social_distance_outlined, 'Distance',
                '${cargo.distanceKm!.toStringAsFixed(1)} km'),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: () {
                Navigator.pop(context);
                if (cargo.priceType == 'fixed') {
                  _openDispatch(context);
                } else {
                  _openBid(context);
                }
              },
              icon: Icon(
                  cargo.priceType == 'fixed' ? Icons.send_rounded : Icons.gavel_outlined,
                  size: 18),
              label: Text(cargo.priceType == 'fixed' ? 'Accept & Dispatch' : 'Place Bid'),
              style: ElevatedButton.styleFrom(
                backgroundColor: _amber,
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _openDispatch(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => _DispatchSheet(cargo: cargo, onDispatched: onActionComplete),
    );
  }

  void _openBid(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => _FleetBidSheet(cargo: cargo, onSubmitted: onActionComplete),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _DetailRow(this.icon, this.label, this.value);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Row(children: [
          Icon(icon, size: 16, color: const Color(0xFF6B7280)),
          const SizedBox(width: 10),
          SizedBox(
            width: 100,
            child: Text(label,
                style: const TextStyle(fontSize: 12, color: Color(0xFF6B7280))),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          ),
        ]),
      );
}

// ── Ranked candidate selector (shared by Dispatch sheet + Bid sheet + Reassign) ──

class _CandidateList extends ConsumerStatefulWidget {
  final int cargoId;
  final ValueChanged<FleetCandidate> onSelected;
  const _CandidateList({
    required this.cargoId,
    required this.onSelected,
  });

  @override
  ConsumerState<_CandidateList> createState() => _CandidateListState();
}

class _CandidateListState extends ConsumerState<_CandidateList> {
  List<FleetCandidate>? _candidates;
  FleetCandidate? _selected;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final list =
          await ref.read(fleetRepositoryProvider).getCargoCandidates(widget.cargoId);
      if (mounted) {
        setState(() {
          _candidates = list;
          _loading = false;
          _selected ??= list.isNotEmpty ? list.first : null;
        });
        if (_selected != null) widget.onSelected(_selected!);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(child: CircularProgressIndicator(color: _green)),
      );
    }
    if (_error != null) {
      return _ErrorHint(message: _error!, onRetry: _load);
    }
    if (_candidates == null || _candidates!.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 16),
        child: Text(
          'No matching drivers/trucks in your fleet for this cargo type. '
          'Add a driver and vehicle of the right category first.',
          style: TextStyle(fontSize: 12, color: Color(0xFF6B7280)),
        ),
      );
    }

    return Column(
      children: _candidates!.map((c) {
        final selected = _selected?.vehicleId == c.vehicleId;
        return GestureDetector(
          onTap: () {
            setState(() => _selected = c);
            widget.onSelected(c);
          },
          child: Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: selected ? const Color(0xFFEFF6FF) : Colors.white,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: selected ? _blue : _border,
                width: selected ? 1.5 : 1,
              ),
            ),
            child: Row(children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${c.driverName ?? "Driver #${c.driverId}"} · ${c.plateNumber}',
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    Wrap(spacing: 8, runSpacing: 4, children: [
                      _StatusPill(
                        label: c.online ? 'Online' : 'Offline',
                        color: c.online ? const Color(0xFF16A34A) : Colors.grey,
                      ),
                      _StatusPill(
                        label: c.available ? 'Available' : 'Busy',
                        color: c.available ? const Color(0xFF16A34A) : Colors.orange,
                      ),
                      if (c.distanceKm != null)
                        _StatusPill(
                          label: '${c.distanceKm!.toStringAsFixed(0)} km away',
                          color: _blue,
                        ),
                      if (!c.available && c.headingTowardPickup == true)
                        const _StatusPill(
                          label: 'Heading this way',
                          color: _amber,
                        ),
                    ]),
                  ],
                ),
              ),
              if (selected)
                const Icon(Icons.check_circle_rounded, color: _blue, size: 20),
            ]),
          ),
        );
      }).toList(),
    );
  }
}

class _StatusPill extends StatelessWidget {
  final String label;
  final Color color;
  const _StatusPill({required this.label, required this.color});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        decoration: BoxDecoration(
          color: color.withAlpha(25),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(label,
            style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: color)),
      );
}

// ── Dispatch sheet (fixed-price: accept + assign in one step) ────────────

class _DispatchSheet extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  final VoidCallback onDispatched;
  const _DispatchSheet({required this.cargo, required this.onDispatched});

  @override
  ConsumerState<_DispatchSheet> createState() => _DispatchSheetState();
}

class _DispatchSheetState extends ConsumerState<_DispatchSheet> {
  FleetCandidate? _selected;
  final _priceCtrl = TextEditingController();
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    if (widget.cargo.budget != null) {
      _priceCtrl.text = widget.cargo.budget!.toStringAsFixed(0);
    }
  }

  @override
  void dispose() {
    _priceCtrl.dispose();
    super.dispose();
  }

  Future<void> _dispatch() async {
    if (_selected == null) return;
    final price = double.tryParse(_priceCtrl.text.trim());
    if (price == null || price <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Enter a valid price'),
        backgroundColor: Colors.red,
      ));
      return;
    }
    setState(() => _loading = true);
    try {
      await ref.read(fleetRepositoryProvider).createBooking(
            cargoId: widget.cargo.id,
            vehicleId: _selected!.vehicleId,
            driverId: _selected!.driverId,
            estimatedPrice: price,
          );
      if (mounted) {
        Navigator.pop(context);
        ref.invalidate(bookingListProvider);
        widget.onDispatched();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              'Dispatched to ${_selected!.driverName ?? "driver"} · ${_selected!.plateNumber}'),
          backgroundColor: _green,
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Dispatch failed: $e'),
          backgroundColor: _red,
        ));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 16, 20, 20 + padding),
      child: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(
            child: Container(
              width: 36, height: 4,
              margin: const EdgeInsets.only(bottom: 16),
              decoration: BoxDecoration(color: _border, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const Text('Dispatch Job', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(
            '${widget.cargo.pickupLocation} → ${widget.cargo.destination}',
            style: const TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
          ),
          const SizedBox(height: 16),

          const Text('Best available driver & truck',
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          _CandidateList(
            cargoId: widget.cargo.id,
            onSelected: (c) => setState(() => _selected = c),
          ),
          const SizedBox(height: 8),

          const Text('Agreed Price (ETB)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          TextField(
            controller: _priceCtrl,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              prefixIcon: const Icon(Icons.payments_outlined),
              suffixText: 'ETB',
              hintText: '0.00',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 20),

          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: (_loading || _selected == null) ? null : _dispatch,
              style: ElevatedButton.styleFrom(
                backgroundColor: _green,
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: _loading
                  ? const SizedBox(
                      width: 18, height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Confirm Dispatch',
                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
            ),
          ),
        ]),
      ),
    );
  }
}

// ── Fleet bid sheet (negotiable cargo: submit a bid) ──────────────────────

class _FleetBidSheet extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  final VoidCallback onSubmitted;
  const _FleetBidSheet({required this.cargo, required this.onSubmitted});

  @override
  ConsumerState<_FleetBidSheet> createState() => _FleetBidSheetState();
}

class _FleetBidSheetState extends ConsumerState<_FleetBidSheet> {
  FleetCandidate? _selected;
  final _amountCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  DateTime? _availableDatetime;
  bool _submitting = false;

  bool get _isIntracity => widget.cargo.serviceType == 'intracity';

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickDatetime() async {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now().add(const Duration(days: 1)),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 60)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 8, minute: 0),
    );
    if (!mounted) return;
    setState(() {
      _availableDatetime = time == null
          ? DateTime(date.year, date.month, date.day, 8)
          : DateTime(date.year, date.month, date.day, time.hour, time.minute);
    });
  }

  Future<void> _submit() async {
    if (_selected == null) return;
    final amount = double.tryParse(_amountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Enter a valid bid amount'),
        backgroundColor: Colors.red,
      ));
      return;
    }
    if (_isIntracity && _availableDatetime == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Select your available date & time'),
        backgroundColor: Colors.red,
      ));
      return;
    }

    setState(() => _submitting = true);
    try {
      if (_isIntracity) {
        await ref.read(bidRepositoryProvider).placeWithDatetime(
              cargoId: widget.cargo.id,
              vehicleId: _selected!.vehicleId,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
              availableDatetime: _availableDatetime,
            );
      } else {
        await ref.read(bidRepositoryProvider).place(
              cargoId: widget.cargo.id,
              vehicleId: _selected!.vehicleId,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
            );
      }
      if (mounted) {
        Navigator.pop(context);
        ref.invalidate(myBidsProvider);
        widget.onSubmitted();
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Bid placed.'),
          backgroundColor: _green,
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Bid failed: $e'),
          backgroundColor: _red,
        ));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final padding = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 16, 20, 20 + padding),
      child: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(
            child: Container(
              width: 36, height: 4,
              margin: const EdgeInsets.only(bottom: 16),
              decoration: BoxDecoration(color: _border, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const Text('Place Bid', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(
            '${widget.cargo.pickupLocation} → ${widget.cargo.destination}',
            style: const TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
          ),
          const SizedBox(height: 16),

          const Text('Truck to commit', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          _CandidateList(
            cargoId: widget.cargo.id,
            onSelected: (c) => setState(() => _selected = c),
          ),
          const SizedBox(height: 8),

          const Text('Bid Amount (ETB)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          TextField(
            controller: _amountCtrl,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              prefixIcon: const Icon(Icons.payments_outlined),
              suffixText: 'ETB',
              hintText: '0.00',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 12),

          if (_isIntracity) ...[
            const Text('Available From', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _pickDatetime,
              icon: const Icon(Icons.event_outlined, size: 16),
              label: Text(_availableDatetime == null
                  ? 'Select date & time'
                  : EthiopianDate.displayWithTime(_availableDatetime!)),
              style: OutlinedButton.styleFrom(
                foregroundColor: _green,
                side: const BorderSide(color: _green),
                padding: const EdgeInsets.symmetric(vertical: 10),
                minimumSize: const Size(double.infinity, 0),
              ),
            ),
            const SizedBox(height: 12),
          ],

          const Text('Note (optional)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          TextField(
            controller: _noteCtrl,
            maxLines: 2,
            decoration: InputDecoration(
              hintText: 'Anything the shipper should know…',
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 20),

          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: (_submitting || _selected == null) ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: _amber,
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 18, height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Submit Bid',
                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
            ),
          ),
        ]),
      ),
    );
  }
}

// ── My Bids tab ───────────────────────────────────────────────────────────

class _MyBidsTab extends ConsumerWidget {
  const _MyBidsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bidsAsync = ref.watch(myBidsProvider);

    return RefreshIndicator(
      color: _amber,
      onRefresh: () async => ref.invalidate(myBidsProvider),
      child: bidsAsync.when(
        data: (bids) {
          if (bids.isEmpty) {
            return ListView(
              children: const [
                _EmptyHint(
                  icon: Icons.gavel_outlined,
                  title: 'No bids yet',
                  subtitle: 'Bids you place on negotiable cargo will appear here.',
                ),
              ],
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: bids.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (_, i) => _BidCard(bid: bids[i]),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator(color: _green)),
        error: (e, _) => Center(
            child: _ErrorHint(message: e.toString(), onRetry: () => ref.invalidate(myBidsProvider))),
      ),
    );
  }
}

class _BidCard extends StatelessWidget {
  final Bid bid;
  const _BidCard({required this.bid});

  @override
  Widget build(BuildContext context) {
    final statusColor = switch (bid.status) {
      'accepted'  => const Color(0xFF16A34A),
      'countered' => _amber,
      'rejected'  => _red,
      'expired'   => Colors.grey,
      _           => _blue,
    };

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.route, size: 15, color: _green),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              '${bid.cargoPickup ?? "—"} → ${bid.cargoDestination ?? "—"}',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: statusColor.withAlpha(20),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(bid.status,
                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: statusColor)),
          ),
        ]),
        const SizedBox(height: 6),
        Row(children: [
          Text('ETB ${bid.amount.toStringAsFixed(0)}',
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: _amber)),
          if (bid.plateNumber != null) ...[
            const SizedBox(width: 10),
            const Icon(Icons.local_shipping_outlined, size: 12, color: Color(0xFF6B7280)),
            const SizedBox(width: 3),
            Text(bid.plateNumber!,
                style: const TextStyle(fontSize: 11, color: Color(0xFF6B7280))),
          ],
        ]),
        if (bid.status == 'countered' && bid.counterAmount != null) ...[
          const SizedBox(height: 6),
          Text(
            'Countered at ETB ${bid.counterAmount!.toStringAsFixed(0)} by ${bid.counterBy ?? "shipper"}',
            style: const TextStyle(fontSize: 12, color: _amber),
          ),
        ],
      ]),
    );
  }
}

// ── Fleet Bookings tab ────────────────────────────────────────────────────

class _FleetBookingsTab extends ConsumerWidget {
  const _FleetBookingsTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bookingsAsync = ref.watch(bookingListProvider);

    return RefreshIndicator(
      color: _amber,
      onRefresh: () async => ref.invalidate(bookingListProvider),
      child: bookingsAsync.when(
        data: (bookings) {
          if (bookings.isEmpty) {
            return const _EmptyHint(
              icon: Icons.receipt_long_outlined,
              title: 'No bookings yet',
              subtitle: 'Dispatch or win a bid from the other tabs to see it here.',
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: bookings.length,
            separatorBuilder: (_, __) => const SizedBox(height: 10),
            itemBuilder: (_, i) => _BookingCard(booking: bookings[i]),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator(color: _green)),
        error: (e, _) => Center(
          child: _ErrorHint(
              message: e.toString(), onRetry: () => ref.invalidate(bookingListProvider))),
      ),
    );
  }
}

class _BookingCard extends ConsumerWidget {
  final Booking booking;
  const _BookingCard({required this.booking});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = booking.bookingStatus;
    final statusColor = switch (status) {
      'accepted'   => _blue,
      'confirmed'  => _green,
      'completed'  => const Color(0xFF059669),
      'cancelled'  => _red,
      _            => _amber,
    };
    final isOngoingTrip = booking.isTripOngoing;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          const Icon(Icons.route, size: 15, color: _green),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              booking.routeLabel,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: statusColor.withAlpha(20),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(status,
                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: statusColor)),
          ),
        ]),
        const SizedBox(height: 6),
        Row(children: [
          const Icon(Icons.person_outline, size: 13, color: Color(0xFF6B7280)),
          const SizedBox(width: 4),
          Text(booking.driverName ?? 'Driver #${booking.driverId}',
              style: const TextStyle(fontSize: 12, color: Color(0xFF6B7280))),
          const Spacer(),
          Text(
            'ETB ${booking.estimatedPrice.toStringAsFixed(0)}',
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: _amber),
          ),
        ]),
        if (status == 'pending' || status == 'accepted' || isOngoingTrip) ...[
          const SizedBox(height: 10),
          Row(children: [
            if (status == 'pending' || status == 'accepted') ...[
              _ReassignButton(bookingId: booking.id, cargoId: booking.cargoId),
              const SizedBox(width: 8),
            ],
            if (isOngoingTrip)
              OutlinedButton.icon(
                onPressed: () => context.push('/tracking/${booking.cargoId}'),
                icon: const Icon(Icons.my_location_rounded, size: 15, color: _blue),
                label: const Text('Track',
                    style: TextStyle(fontSize: 12, color: _blue)),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: _blue, width: 0.8),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
          ]),
        ],
      ]),
    );
  }
}

class _ReassignButton extends ConsumerStatefulWidget {
  final int bookingId;
  final int cargoId;
  const _ReassignButton({required this.bookingId, required this.cargoId});

  @override
  ConsumerState<_ReassignButton> createState() => _ReassignButtonState();
}

class _ReassignButtonState extends ConsumerState<_ReassignButton> {
  bool _loading = false;

  Future<void> _reassign() async {
    FleetCandidate? chosen;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setS) => AlertDialog(
          title: const Text('Reassign Driver'),
          content: SizedBox(
            width: double.maxFinite,
            child: SingleChildScrollView(
              child: _CandidateList(
                cargoId: widget.cargoId,
                onSelected: (c) => setS(() => chosen = c),
              ),
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            ElevatedButton(
              onPressed: () => Navigator.pop(ctx, true),
              style: ElevatedButton.styleFrom(backgroundColor: _green),
              child: const Text('Reassign', style: TextStyle(color: Colors.white)),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true || chosen == null) return;

    setState(() => _loading = true);
    try {
      await ref.read(fleetRepositoryProvider).dispatchBooking(
            widget.bookingId,
            driverId: chosen!.driverId,
          );
      if (mounted) {
        ref.invalidate(bookingListProvider);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Booking reassigned to ${chosen!.driverName ?? "driver"}'),
          backgroundColor: _green,
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Reassign failed: $e'),
          backgroundColor: _red,
        ));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => OutlinedButton.icon(
        onPressed: _loading ? null : _reassign,
        icon: _loading
            ? const SizedBox(
                width: 14, height: 14,
                child: CircularProgressIndicator(strokeWidth: 2, color: _green))
            : const Icon(Icons.swap_horiz, size: 15, color: _green),
        label: const Text('Reassign',
            style: TextStyle(fontSize: 12, color: _green)),
        style: OutlinedButton.styleFrom(
          side: const BorderSide(color: _green, width: 0.8),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
      );
}

// ── Shared helpers ────────────────────────────────────────────────────────

class _Detail extends StatelessWidget {
  final IconData icon;
  final String text;
  const _Detail(this.icon, this.text);

  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 13, color: const Color(0xFF6B7280)),
        const SizedBox(width: 4),
        Text(text, style: const TextStyle(fontSize: 12, color: Color(0xFF6B7280))),
      ]);
}

class _EmptyHint extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  const _EmptyHint({required this.icon, required this.title, required this.subtitle});

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(icon, size: 56, color: Colors.grey[300]),
            const SizedBox(height: 12),
            Text(title,
                style: const TextStyle(
                    fontSize: 15, fontWeight: FontWeight.w600, color: Colors.grey)),
            const SizedBox(height: 6),
            Text(subtitle,
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: Colors.grey[400])),
          ]),
        ),
      );
}

class _ErrorHint extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _ErrorHint({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          const Icon(Icons.cloud_off_outlined, size: 48, color: Colors.grey),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.grey, fontSize: 13)),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh, size: 16),
            label: const Text('Retry'),
          ),
        ]),
      );
}
