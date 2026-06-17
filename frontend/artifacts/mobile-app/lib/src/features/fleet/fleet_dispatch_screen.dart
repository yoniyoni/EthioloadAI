import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/api/api_client.dart';
import '../../data/providers/data_providers.dart';
import 'fleet_dashboard_screen.dart' show fleetDashboardProvider;

// ── Palette ───────────────────────────────────────────────────────────────
const _green  = Color(0xFF0F3D1A);
const _amber  = Color(0xFFF59E0B);
const _blue   = Color(0xFF2563EB);
const _red    = Color(0xFFEF4444);
const _bg     = Color(0xFFF8FBF8);
const _border = Color(0xFFE5E7EB);

// ── Providers ─────────────────────────────────────────────────────────────

final fleetAvailableCargoProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  final api = ref.read(apiClientProvider);
  final data = await api.get<List<dynamic>>('/fleet/available-cargo');
  return data.cast<Map<String, dynamic>>();
});

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
    _tabs = TabController(length: 2, vsync: this);
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
              ref.invalidate(fleetAvailableCargoProvider);
              ref.invalidate(bookingListProvider);
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
            Tab(text: "Fleet Bookings"),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: const [
          _AvailableCargoTab(),
          _FleetBookingsTab(),
        ],
      ),
    );
  }
}

// ── Available Cargo tab ───────────────────────────────────────────────────

class _AvailableCargoTab extends ConsumerWidget {
  const _AvailableCargoTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cargoAsync = ref.watch(fleetAvailableCargoProvider);

    return RefreshIndicator(
      color: _amber,
      onRefresh: () async => ref.invalidate(fleetAvailableCargoProvider),
      child: cargoAsync.when(
        data: (cargo) {
          if (cargo.isEmpty) {
            return const _EmptyHint(
              icon: Icons.inventory_2_outlined,
              title: 'No available cargo',
              subtitle: 'Check back later — shippers will post new loads here.',
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: cargo.length,
            separatorBuilder: (_, __) => const SizedBox(height: 12),
            itemBuilder: (_, i) => _CargoCard(cargo: cargo[i]),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator(color: _green)),
        error: (e, _) => Center(child: _ErrorHint(message: e.toString(),
            onRetry: () => ref.invalidate(fleetAvailableCargoProvider))),
      ),
    );
  }
}

class _CargoCard extends ConsumerWidget {
  final Map<String, dynamic> cargo;
  const _CargoCard({required this.cargo});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final urgency = cargo['urgency_level'] as String? ?? 'normal';
    final urgencyColor = switch (urgency) {
      'express' => _red,
      'high'    => Colors.orange,
      _         => _green,
    };

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Route header
        Row(children: [
          const Icon(Icons.route, size: 16, color: _green),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              '${cargo['pickup_location'] ?? ''} → ${cargo['destination'] ?? ''}',
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: Color(0xFF111827)),
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: urgencyColor.withAlpha(20),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(urgency,
                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: urgencyColor)),
          ),
        ]),
        const SizedBox(height: 8),
        // Details row
        Wrap(spacing: 16, runSpacing: 4, children: [
          _Detail(Icons.scale_outlined,
              '${cargo['weight'] ?? 0} tons'),
          _Detail(Icons.category_outlined,
              cargo['material_type'] ?? '—'),
          if (cargo['budget'] != null)
            _Detail(Icons.payments_outlined,
                'Budget: ETB ${cargo['budget']}'),
        ]),
        const SizedBox(height: 8),
        // Shipper info
        Row(children: [
          const Icon(Icons.person_outline, size: 13, color: Color(0xFF6B7280)),
          const SizedBox(width: 4),
          Text('${cargo['shipper_name'] ?? '—'}  ·  ${cargo['shipper_phone'] ?? ''}',
              style: const TextStyle(fontSize: 12, color: Color(0xFF6B7280))),
        ]),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: () => _showDispatchDialog(context, ref, cargo),
            icon: const Icon(Icons.send_rounded, size: 16),
            label: const Text('Dispatch to Driver'),
            style: ElevatedButton.styleFrom(
              backgroundColor: _amber,
              foregroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
              padding: const EdgeInsets.symmetric(vertical: 10),
            ),
          ),
        ),
      ]),
    );
  }

  Future<void> _showDispatchDialog(
      BuildContext context, WidgetRef ref, Map<String, dynamic> cargo) async {
    final dash = ref.read(fleetDashboardProvider).valueOrNull;
    if (dash == null) return;

    final drivers  = (dash['drivers']  as List?)?.cast<Map<String, dynamic>>() ?? [];
    final vehicles = (dash['vehicles'] as List?)?.cast<Map<String, dynamic>>() ?? [];

    if (drivers.isEmpty || vehicles.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Add at least one driver and one vehicle to your fleet first.'),
        backgroundColor: Colors.red,
      ));
      return;
    }

    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => _DispatchSheet(
        cargo: cargo,
        drivers: drivers,
        vehicles: vehicles,
        onDispatched: () {
          ref.invalidate(fleetAvailableCargoProvider);
          ref.invalidate(fleetDashboardProvider);
          ref.invalidate(bookingListProvider);
        },
      ),
    );
  }
}

// ── Dispatch bottom sheet ─────────────────────────────────────────────────

class _DispatchSheet extends ConsumerStatefulWidget {
  final Map<String, dynamic> cargo;
  final List<Map<String, dynamic>> drivers;
  final List<Map<String, dynamic>> vehicles;
  final VoidCallback onDispatched;

  const _DispatchSheet({
    required this.cargo,
    required this.drivers,
    required this.vehicles,
    required this.onDispatched,
  });

  @override
  ConsumerState<_DispatchSheet> createState() => _DispatchSheetState();
}

class _DispatchSheetState extends ConsumerState<_DispatchSheet> {
  late Map<String, dynamic> _selectedDriver;
  late Map<String, dynamic> _selectedVehicle;
  final _priceCtrl = TextEditingController();
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _selectedDriver  = widget.drivers.first;
    _selectedVehicle = widget.vehicles.first;
    // Pre-fill price from shipper's budget
    if (widget.cargo['budget'] != null) {
      _priceCtrl.text = widget.cargo['budget'].toString();
    }
  }

  @override
  void dispose() {
    _priceCtrl.dispose();
    super.dispose();
  }

  Future<void> _dispatch() async {
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
      await ref.read(apiClientProvider).post<Map<String, dynamic>>(
        '/fleet/bookings',
        data: {
          'cargo_id':        widget.cargo['id'] as int,
          'vehicle_id':      _selectedVehicle['id'] as int,
          'driver_id':       _selectedDriver['id'] as int,
          'estimated_price': price,
        },
      );
      if (mounted) {
        Navigator.pop(context);
        widget.onDispatched();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              'Dispatched to ${_selectedDriver['name']} · ${_selectedVehicle['plate_number']}'),
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
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        // Handle
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
          '${widget.cargo['pickup_location']} → ${widget.cargo['destination']}',
          style: const TextStyle(fontSize: 13, color: Color(0xFF6B7280)),
        ),
        const SizedBox(height: 20),

        // Driver picker
        const Text('Assign Driver', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        _StyledDropdown<Map<String, dynamic>>(
          icon: Icons.person_outline,
          value: _selectedDriver,
          items: widget.drivers,
          label: (d) => '${d['name']} · ${d['phone']}',
          onChanged: (v) => setState(() => _selectedDriver = v),
        ),
        const SizedBox(height: 14),

        // Vehicle picker
        const Text('Assign Vehicle', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
        const SizedBox(height: 8),
        _StyledDropdown<Map<String, dynamic>>(
          icon: Icons.local_shipping_outlined,
          value: _selectedVehicle,
          items: widget.vehicles,
          label: (v) => '${v['plate_number']} · ${v['truck_type']}',
          onChanged: (v) => setState(() => _selectedVehicle = v),
        ),
        const SizedBox(height: 14),

        // Price
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
            onPressed: _loading ? null : _dispatch,
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
              subtitle: 'Dispatch your first job from the Available Cargo tab.',
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
  final dynamic booking; // Booking model
  const _BookingCard({required this.booking});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = booking.bookingStatus as String;
    final statusColor = switch (status) {
      'accepted'   => _blue,
      'confirmed'  => _green,
      'completed'  => const Color(0xFF059669),
      'cancelled'  => _red,
      _            => _amber,
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
              booking.routeLabel as String,
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
            'ETB ${(booking.estimatedPrice as double).toStringAsFixed(0)}',
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: _amber),
          ),
        ]),
        // Reassign button for pending/accepted bookings
        if (status == 'pending' || status == 'accepted') ...[
          const SizedBox(height: 10),
          _ReassignButton(bookingId: booking.id as int),
        ],
      ]),
    );
  }
}

class _ReassignButton extends ConsumerStatefulWidget {
  final int bookingId;
  const _ReassignButton({required this.bookingId});

  @override
  ConsumerState<_ReassignButton> createState() => _ReassignButtonState();
}

class _ReassignButtonState extends ConsumerState<_ReassignButton> {
  bool _loading = false;

  Future<void> _reassign() async {
    final dash = ref.read(fleetDashboardProvider).valueOrNull;
    if (dash == null) return;
    final drivers = (dash['drivers'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    if (drivers.isEmpty) return;

    Map<String, dynamic>? chosen = drivers.first;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setS) => AlertDialog(
          title: const Text('Reassign Driver'),
          content: DropdownButton<Map<String, dynamic>>(
            value: chosen,
            isExpanded: true,
            items: drivers
                .map((d) => DropdownMenuItem(
                      value: d,
                      child: Text('${d['name']} · ${d['phone']}'),
                    ))
                .toList(),
            onChanged: (v) => setS(() => chosen = v),
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
      await ref.read(apiClientProvider).patch<Map<String, dynamic>>(
        '/fleet/bookings/${widget.bookingId}/dispatch',
        data: {'driver_id': chosen!['id'] as int},
      );
      if (mounted) {
        ref.invalidate(bookingListProvider);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Booking reassigned to ${chosen!['name']}'),
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
        label: const Text('Reassign Driver',
            style: TextStyle(fontSize: 12, color: _green)),
        style: OutlinedButton.styleFrom(
          side: const BorderSide(color: _green, width: 0.8),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
      );
}

// ── Shared helpers ────────────────────────────────────────────────────────

/// Outlined dropdown that uses [DropdownButton] (not DropdownButtonFormField)
/// so the controlled [value] prop stays non-deprecated.
class _StyledDropdown<T> extends StatelessWidget {
  final T value;
  final List<T> items;
  final String Function(T) label;
  final IconData icon;
  final void Function(T) onChanged;

  const _StyledDropdown({
    required this.value,
    required this.items,
    required this.label,
    required this.icon,
    required this.onChanged,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        border: Border.all(color: const Color(0xFFD1D5DB)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(children: [
        Icon(icon, size: 20, color: const Color(0xFF6B7280)),
        const SizedBox(width: 8),
        Expanded(
          child: DropdownButton<T>(
            value: value,
            isExpanded: true,
            underline: const SizedBox.shrink(),
            items: items
                .map((i) => DropdownMenuItem<T>(value: i, child: Text(label(i))))
                .toList(),
            onChanged: (v) { if (v != null) onChanged(v); },
          ),
        ),
      ]),
    );
  }
}

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
