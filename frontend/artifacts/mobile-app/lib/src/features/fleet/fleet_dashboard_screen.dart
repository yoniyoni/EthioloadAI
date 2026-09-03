import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/api/api_client.dart';
import '../../data/repositories/repositories.dart';
import '../../utils/weight_format.dart';

// ── Fleet dashboard provider ──────────────────────────────────────────────

final fleetDashboardProvider =
    FutureProvider.autoDispose<Map<String, dynamic>>((ref) async {
  final response =
      await ref.read(apiClientProvider).get<Map<String, dynamic>>(
            '/fleet/dashboard',
          );
  return response;
});

// ── Screen ────────────────────────────────────────────────────────────────

class FleetDashboardScreen extends ConsumerWidget {
  const FleetDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashAsync = ref.watch(fleetDashboardProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Fleet Dashboard'),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => context.go('/profile'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(fleetDashboardProvider),
        child: dashAsync.when(
          data: (data) => _DashboardBody(data: data),
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error, size: 48, color: Colors.red),
                const SizedBox(height: 12),
                Text(e.toString(), textAlign: TextAlign.center),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => ref.invalidate(fleetDashboardProvider),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DashboardBody extends ConsumerWidget {
  final Map<String, dynamic> data;
  const _DashboardBody({required this.data});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final drivers = (data['drivers'] as List?) ?? [];
    final vehicles = (data['vehicles'] as List?) ?? [];
    final activeBookings = (data['active_bookings'] as List?) ?? [];

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── Stats ──────────────────────────────────────────────────
        Row(children: [
          Expanded(
              child: _StatCard(
                  label: 'Drivers',
                  value: '${data['driver_count'] ?? 0}',
                  icon: Icons.people,
                  color: Colors.blue)),
          const SizedBox(width: 12),
          Expanded(
              child: _StatCard(
                  label: 'Vehicles',
                  value: '${data['vehicle_count'] ?? 0}',
                  icon: Icons.local_shipping,
                  color: Colors.green)),
          const SizedBox(width: 12),
          Expanded(
              child: _StatCard(
                  label: 'Active',
                  value: '${data['active_booking_count'] ?? 0}',
                  icon: Icons.route,
                  color: Colors.orange)),
        ]),

        const SizedBox(height: 24),

        // ── Quick actions ──────────────────────────────────────────
        Row(children: [
          Expanded(
            child: _ActionBtn(
              icon: Icons.person_add,
              label: 'Add Driver',
              color: Colors.blue,
              onTap: () => context.go('/fleet/add-driver'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: _ActionBtn(
              icon: Icons.add_box,
              label: 'Add Vehicle',
              color: Colors.green,
              onTap: () => context.go('/fleet/add-vehicle'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: _ActionBtn(
              icon: Icons.send_rounded,
              label: 'Dispatch',
              color: Colors.orange,
              onTap: () => context.go('/fleet/dispatch'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: _ActionBtn(
              icon: Icons.map_outlined,
              label: 'Live Map',
              color: Colors.teal,
              onTap: () => context.push('/fleet/live-map'),
            ),
          ),
        ]),

        const SizedBox(height: 24),

        // ── Active bookings ────────────────────────────────────────
        if (activeBookings.isNotEmpty) ...[
          const Text('Active Jobs',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          ...activeBookings.map((b) => _ActiveBookingTile(booking: b)),
          const SizedBox(height: 20),
        ],

        // ── Drivers ────────────────────────────────────────────────
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text('My Drivers',
                style:
                    TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
            TextButton(
              onPressed: () => context.go('/fleet/drivers'),
              child: const Text('Manage'),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (drivers.isEmpty)
          const _EmptyState(
              message: 'No drivers yet.\nTap "Add Driver" to link one.')
        else
          ...drivers.take(3).map((d) => _DriverTile(driver: d)),

        const SizedBox(height: 20),

        // ── Vehicles ───────────────────────────────────────────────
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text('My Vehicles',
                style:
                    TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
            TextButton(
              onPressed: () => context.go('/fleet/vehicles'),
              child: const Text('Manage'),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (vehicles.isEmpty)
          const _EmptyState(
              message: 'No vehicles yet.\nTap "Add Vehicle" to register one.')
        else
          ...vehicles.take(3).map((v) => _VehicleTile(vehicle: v)),

        const SizedBox(height: 32),
      ],
    );
  }
}

// ── Driver management screen ──────────────────────────────────────────────

class FleetDriversScreen extends ConsumerStatefulWidget {
  const FleetDriversScreen({super.key});

  @override
  ConsumerState<FleetDriversScreen> createState() =>
      _FleetDriversScreenState();
}

class _FleetDriversScreenState extends ConsumerState<FleetDriversScreen> {
  final _driverIdCtrl = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _driverIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _openCreateDriverSheet() async {
    final created = await showModalBottomSheet<(String, String)>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (_) => const _CreateDriverSheet(),
    );

    if (created == null || !mounted) return;
    final (fullName, phone) = created;
    ref.invalidate(fleetDashboardProvider);
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Driver account created'),
        content: Text(
          'A driver account was created for $fullName.\n\n'
          'Their initial password is their phone number '
          '($phone). Please tell them to change it after '
          'their first login.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Got it'),
          ),
        ],
      ),
    );
  }

  Future<void> _addDriver() async {
    final id = int.tryParse(_driverIdCtrl.text.trim());
    if (id == null) {
      setState(() => _error = 'Enter a valid driver ID');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final res = await ref.read(apiClientProvider).post<Map<String, dynamic>>(
            '/fleet/drivers/add',
            data: {'driver_id': id},
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text((res as Map)['message']?.toString() ??
              'Driver added successfully'),
          backgroundColor: Colors.green,
        ));
        _driverIdCtrl.clear();
        ref.invalidate(fleetDashboardProvider);
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final dashAsync = ref.watch(fleetDashboardProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Manage Drivers'), elevation: 0),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Create a brand new driver account
          const Text('Create New Driver',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
          const SizedBox(height: 6),
          const Text(
            "Don't have a driver account yet? Create one directly — "
            'they can log in right away.',
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: _openCreateDriverSheet,
              icon: const Icon(Icons.person_add_alt_1),
              label: const Text('Create New Driver'),
              style: OutlinedButton.styleFrom(
                foregroundColor: Colors.blue,
                padding: const EdgeInsets.symmetric(vertical: 14),
              ),
            ),
          ),

          const SizedBox(height: 24),
          const Divider(height: 1),
          const SizedBox(height: 24),

          // Add driver by ID
          const Text('Link a Driver to Your Fleet',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
          const SizedBox(height: 6),
          const Text(
            'Ask the driver to share their user ID after registering.',
            style: TextStyle(fontSize: 12, color: Colors.grey),
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: TextField(
                controller: _driverIdCtrl,
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  hintText: 'Driver User ID',
                  prefixIcon: const Icon(Icons.badge_outlined),
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8)),
                  errorText: _error,
                ),
              ),
            ),
            const SizedBox(width: 10),
            ElevatedButton(
              onPressed: _loading ? null : _addDriver,
              style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  padding: const EdgeInsets.symmetric(
                      vertical: 16, horizontal: 18)),
              child: _loading
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Text('Add'),
            ),
          ]),

          const SizedBox(height: 24),
          const Text('Current Drivers',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          dashAsync.when(
            data: (data) {
              final drivers = (data['drivers'] as List?) ?? [];
              if (drivers.isEmpty) {
                return const _EmptyState(message: 'No drivers in your fleet yet.');
              }
              return Column(
                children: drivers
                    .map((d) => _DriverTile(
                          driver: d,
                          onTap: () => context.push('/fleet/drivers/${d["id"]}'),
                          onRemove: () async {
                            try {
                              await ref.read(apiClientProvider).delete(
                                  '/fleet/drivers/${d["id"]}');
                              ref.invalidate(fleetDashboardProvider);
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                                  content: Text('${d["name"] ?? "Driver"} removed from fleet'),
                                  backgroundColor: Colors.green,
                                ));
                              }
                            } catch (e) {
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                                  content: Text(e is ApiException ? e.message : 'Failed to remove driver: $e'),
                                  backgroundColor: Colors.red,
                                ));
                              }
                            }
                          },
                        ))
                    .toList(),
              );
            },
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Text('Error: $e'),
          ),
        ],
      ),
    );
  }
}

// ── Create driver bottom sheet ────────────────────────────────────────────
//
// Pops with a (fullName, phone) record on success so the caller can show a
// follow-up dialog without holding a BuildContext across this widget's own
// async gap.

class _CreateDriverSheet extends ConsumerStatefulWidget {
  const _CreateDriverSheet();

  @override
  ConsumerState<_CreateDriverSheet> createState() =>
      _CreateDriverSheetState();
}

class _CreateDriverSheetState extends ConsumerState<_CreateDriverSheet> {
  final _nameCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final fullName = _nameCtrl.text.trim();
    final phone = _phoneCtrl.text.trim();
    final email = _emailCtrl.text.trim();
    if (fullName.isEmpty || phone.isEmpty) {
      setState(() => _error = 'Name and phone are required');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(fleetRepositoryProvider).createDriver(
            fullName: fullName,
            phone: phone,
            email: email.isEmpty ? null : email,
          );
      if (mounted) Navigator.pop(context, (fullName, phone));
    } catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _error = e.toString();
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Create New Driver',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            const Text(
              'Creates a driver account directly and adds it to your fleet.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _nameCtrl,
              decoration: InputDecoration(
                labelText: 'Full Name',
                prefixIcon: const Icon(Icons.person_outline),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _phoneCtrl,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(
                labelText: 'Phone',
                prefixIcon: const Icon(Icons.phone_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _emailCtrl,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(
                labelText: 'Email (optional)',
                prefixIcon: const Icon(Icons.email_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 10),
              Text(_error!,
                  style: const TextStyle(color: Colors.red, fontSize: 12)),
            ],
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _submitting ? null : _submit,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.blue,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: _submitting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('Create Driver'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Add vehicle screen ────────────────────────────────────────────────────

class FleetAddVehicleScreen extends ConsumerStatefulWidget {
  const FleetAddVehicleScreen({super.key});

  @override
  ConsumerState<FleetAddVehicleScreen> createState() =>
      _FleetAddVehicleScreenState();
}

class _FleetAddVehicleScreenState
    extends ConsumerState<FleetAddVehicleScreen> {
  final _plateCtrl = TextEditingController();
  final _capacityCtrl = TextEditingController();
  final _driverIdCtrl = TextEditingController();
  String? _vehicleCategory; // null = not yet chosen
  String _truckType = 'flatbed';
  String _city = 'Addis Ababa';
  bool _loading = false;

  static const _typesHeavy = [
    'flatbed', 'tanker', 'refrigerated', 'container', 'tipper',
  ];
  static const _typesLight = ['pickup', 'minivan', 'bajaj'];

  List<String> get _types =>
      _vehicleCategory == 'light' ? _typesLight : _typesHeavy;

  static const _cities = [
    'Addis Ababa', 'Adama', 'Hawassa', 'Dire Dawa', 'Bahir Dar',
    'Gondar', 'Mekele', 'Jimma', 'Jijiga', 'Nekemte',
  ];

  @override
  void dispose() {
    _plateCtrl.dispose();
    _capacityCtrl.dispose();
    _driverIdCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_plateCtrl.text.isEmpty || _capacityCtrl.text.isEmpty ||
        _vehicleCategory == null) return;
    setState(() => _loading = true);
    try {
      final driverId = int.tryParse(_driverIdCtrl.text.trim());
      await ref.read(apiClientProvider).post<Map<String, dynamic>>(
        '/fleet/vehicles',
        data: {
          'truck_type': _truckType,
          'plate_number': _plateCtrl.text.trim().toUpperCase(),
          'capacity': WeightFormat.kuntalToTons(
              double.tryParse(_capacityCtrl.text.trim()) ?? 0),
          'current_city': _city,
          'vehicle_category': _vehicleCategory,
          if (driverId != null) 'driver_id': driverId,
        },
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Vehicle added to fleet!'),
          backgroundColor: Colors.green,
        ));
        ref.invalidate(fleetDashboardProvider);
        context.go('/fleet');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e'),
          backgroundColor: Colors.red,
        ));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Add Vehicle'), elevation: 0),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Vehicle Category',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: _CategoryCard(
              icon: Icons.local_shipping_rounded,
              title: 'Heavy',
              subtitle: 'Flatbed, tanker, container…',
              selected: _vehicleCategory == 'heavy',
              onTap: () => setState(() {
                _vehicleCategory = 'heavy';
                _truckType = _typesHeavy.first;
              }),
            )),
            const SizedBox(width: 10),
            Expanded(child: _CategoryCard(
              icon: Icons.airport_shuttle_rounded,
              title: 'Light',
              subtitle: 'Pickup, minivan, bajaj',
              selected: _vehicleCategory == 'light',
              onTap: () => setState(() {
                _vehicleCategory = 'light';
                _truckType = _typesLight.first;
              }),
            )),
          ]),
          if (_vehicleCategory == null) ...[
            const SizedBox(height: 6),
            const Text('Please select a category to continue',
                style: TextStyle(fontSize: 11, color: Colors.red)),
          ],
          const SizedBox(height: 16),
          const Text('Vehicle Details',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            value: _types.contains(_truckType) ? _truckType : _types.first,
            decoration: InputDecoration(
              labelText: 'Vehicle Type',
              prefixIcon: const Icon(Icons.local_shipping_outlined),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
            items: _types
                .map((t) => DropdownMenuItem(
                    value: t,
                    child: Text(t[0].toUpperCase() + t.substring(1))))
                .toList(),
            onChanged: (v) => setState(() => _truckType = v ?? _truckType),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _plateCtrl,
            textCapitalization: TextCapitalization.characters,
            decoration: InputDecoration(
              labelText: 'Plate Number',
              hintText: 'ET-1234-AA',
              prefixIcon: const Icon(Icons.confirmation_number_outlined),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _capacityCtrl,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              labelText: 'Capacity (kuntal)',
              suffixText: 'kuntal',
              prefixIcon: const Icon(Icons.scale_outlined),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            value: _city,
            decoration: InputDecoration(
              labelText: 'Current City',
              prefixIcon: const Icon(Icons.location_city_outlined),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
            items: _cities
                .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                .toList(),
            onChanged: (v) => setState(() => _city = v ?? _city),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _driverIdCtrl,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              labelText: 'Assign Driver (optional — enter driver ID)',
              prefixIcon: const Icon(Icons.person_outlined),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 24),
          ElevatedButton(
            onPressed: _loading ? null : _submit,
            style: ElevatedButton.styleFrom(
                backgroundColor: Colors.green,
                padding: const EdgeInsets.symmetric(vertical: 14)),
            child: _loading
                ? const CircularProgressIndicator(color: Colors.white)
                : const Text('Add Vehicle to Fleet',
                    style: TextStyle(
                        fontSize: 15, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }
}

// ── Category card ─────────────────────────────────────────────────────────

class _CategoryCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool selected;
  final VoidCallback onTap;

  const _CategoryCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    const accent = Colors.green;
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: selected
              ? const Color(0xFF0F3D1A).withAlpha(12)
              : Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected ? accent : const Color(0xFFE5E7EB),
            width: selected ? 1.5 : 0.5,
          ),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, color: selected ? accent : Colors.grey, size: 22),
          const SizedBox(height: 6),
          Text(title,
              style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: selected ? const Color(0xFF0F3D1A) : const Color(0xFF111827))),
          const SizedBox(height: 2),
          Text(subtitle,
              style: const TextStyle(fontSize: 11, color: Color(0xFF6B7280))),
        ]),
      ),
    );
  }
}

// ── Vehicles list screen ──────────────────────────────────────────────────

class FleetVehiclesScreen extends ConsumerWidget {
  const FleetVehiclesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashAsync = ref.watch(fleetDashboardProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Fleet Vehicles'), elevation: 0),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go('/fleet/add-vehicle'),
        icon: const Icon(Icons.add),
        label: const Text('Add Vehicle'),
        backgroundColor: Colors.green,
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(fleetDashboardProvider),
        child: dashAsync.when(
          data: (data) {
            final vehicles = (data['vehicles'] as List?) ?? [];
            if (vehicles.isEmpty) {
              return const Center(
                child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                  Icon(Icons.local_shipping_outlined, size: 56, color: Colors.grey),
                  SizedBox(height: 12),
                  Text('No vehicles in your fleet yet.',
                      style: TextStyle(color: Colors.grey)),
                ]),
              );
            }
            return ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: vehicles.length,
              separatorBuilder: (_, __) => const SizedBox(height: 10),
              itemBuilder: (_, i) => _VehicleDetailTile(
                vehicle: vehicles[i] as Map<String, dynamic>,
                drivers: (data['drivers'] as List?)
                        ?.cast<Map<String, dynamic>>() ??
                    [],
                onAssign: (driverId) async {
                  try {
                    await ref.read(apiClientProvider).patch<Map<String, dynamic>>(
                      '/fleet/vehicles/${vehicles[i]["id"]}/assign',
                      data: {'driver_id': driverId},
                    );
                    ref.invalidate(fleetDashboardProvider);
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                        content: Text('Vehicle assigned successfully'),
                        backgroundColor: Colors.green,
                      ));
                    }
                  } catch (e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: Text(e is ApiException ? e.message : 'Assignment failed: $e'),
                        backgroundColor: Colors.red,
                      ));
                    }
                  }
                },
              ),
            );
          },
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(child: Text('Error: $e')),
        ),
      ),
    );
  }
}

/// Formats a timestamp as a short relative "time ago" string.
String _relativeTime(DateTime dt) {
  final diff = DateTime.now().difference(dt);
  if (diff.inMinutes < 1) return 'just now';
  if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
  if (diff.inHours < 24) return '${diff.inHours}h ago';
  return '${diff.inDays}d ago';
}

class _VehicleDetailTile extends ConsumerWidget {
  final Map<String, dynamic> vehicle;
  final List<Map<String, dynamic>> drivers;
  final Future<void> Function(int driverId) onAssign;

  const _VehicleDetailTile({
    required this.vehicle,
    required this.drivers,
    required this.onAssign,
  });

  Future<void> _openEditSheet(BuildContext context, WidgetRef ref) async {
    final vehicleId = vehicle['id'] as int;
    final plateCtrl =
        TextEditingController(text: vehicle['plate_number']?.toString() ?? '');
    final truckTypeCtrl =
        TextEditingController(text: vehicle['truck_type']?.toString() ?? '');
    final capacityTons = (vehicle['capacity'] as num?)?.toDouble();
    final capacityCtrl = TextEditingController(
        text: capacityTons == null
            ? ''
            : WeightFormat.tonsToKuntal(capacityTons)
                .toStringAsFixed(WeightFormat.tonsToKuntal(capacityTons) % 1 == 0 ? 0 : 1));
    final cityCtrl =
        TextEditingController(text: vehicle['current_city']?.toString() ?? '');

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (sheetContext) => _EditVehicleSheet(
        vehicleId: vehicleId,
        plateCtrl: plateCtrl,
        truckTypeCtrl: truckTypeCtrl,
        capacityCtrl: capacityCtrl,
        cityCtrl: cityCtrl,
      ),
    );
  }

  Future<void> _confirmDelete(BuildContext context, WidgetRef ref) async {
    final vehicleId = vehicle['id'] as int;
    final plateNumber = vehicle['plate_number']?.toString() ?? 'this vehicle';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete this vehicle?'),
        content: Text(
            'Delete $plateNumber from your fleet? This cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Delete',
                style: TextStyle(color: Color(0xFFEF4444))),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(vehicleRepositoryProvider).delete(vehicleId);
      ref.invalidate(fleetDashboardProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('$plateNumber deleted'),
          backgroundColor: Colors.green,
        ));
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(e is ApiException ? e.message : 'Delete failed: $e'),
          backgroundColor: Colors.red,
        ));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isAvailable = vehicle['availability_status'] == 'available';
    final isVerified = vehicle['verification_status'] == true;
    final assignedDriverId = vehicle['driver_id'];
    final assignedDriver = drivers.cast<Map?>().firstWhere(
          (d) => d?['id'] == assignedDriverId,
          orElse: () => null,
        );

    final lat = vehicle['latitude'];
    final lng = vehicle['longitude'];
    final hasLocation = lat != null && lng != null;
    final lastLocationAt = vehicle['last_location_at'] != null
        ? DateTime.tryParse(vehicle['last_location_at'].toString())
        : null;

    final vehicleId = vehicle['id'];
    final plateNumber = vehicle['plate_number']?.toString() ?? '';

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: Colors.green.withAlpha(20),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.local_shipping, color: Colors.green, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(vehicle['plate_number']?.toString() ?? '—',
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
              Text(
                '${vehicle['truck_type'] ?? ''} · '
                '${WeightFormat.display((vehicle['capacity'] as num? ?? 0).toDouble())} · '
                '${vehicle['current_city'] ?? ''}',
                style: TextStyle(fontSize: 12, color: Colors.grey[600]),
              ),
              if (hasLocation) ...[
                const SizedBox(height: 2),
                Text(
                  lastLocationAt != null
                      ? 'Last seen: ${_relativeTime(lastLocationAt)}'
                      : 'Last seen: location available',
                  style: TextStyle(fontSize: 11, color: Colors.grey[500]),
                ),
              ],
            ]),
          ),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: isAvailable ? Colors.green.withAlpha(20) : Colors.orange.withAlpha(20),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                isAvailable ? 'Available' : 'Busy',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: isAvailable ? Colors.green : Colors.orange,
                ),
              ),
            ),
            const SizedBox(height: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
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
        ]),
        if (drivers.isNotEmpty) ...[
          const SizedBox(height: 10),
          const Divider(height: 1),
          const SizedBox(height: 10),
          Row(children: [
            const Icon(Icons.person_outline, size: 15, color: Colors.grey),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                assignedDriver != null
                    ? 'Assigned to: ${assignedDriver['name']}'
                    : 'No driver assigned',
                style: TextStyle(
                  fontSize: 12,
                  color: assignedDriver != null ? Colors.black87 : Colors.grey,
                ),
              ),
            ),
            PopupMenuButton<int>(
              tooltip: isVerified
                  ? 'Assign driver'
                  : 'Vehicle must be verified before it can be assigned',
              enabled: isVerified,
              onSelected: (id) => onAssign(id),
              itemBuilder: (_) => drivers
                  .map((d) => PopupMenuItem<int>(
                        value: d['id'] as int,
                        enabled: d['verified'] == true,
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(d['name']?.toString() ?? '—'),
                            if (d['verified'] != true) ...[
                              const SizedBox(width: 6),
                              Text('(unverified)',
                                  style: TextStyle(
                                      fontSize: 11, color: Colors.grey[500])),
                            ],
                          ],
                        ),
                      ))
                  .toList(),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  border: Border.all(
                      color: isVerified ? Colors.blue.shade200 : Colors.grey.shade300),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text('Assign',
                    style: TextStyle(
                        fontSize: 12,
                        color: isVerified ? Colors.blue : Colors.grey)),
              ),
            ),
          ]),
        ],
        const SizedBox(height: 10),
        const Divider(height: 1),
        const SizedBox(height: 10),
        Row(children: [
          IconButton(
            onPressed: vehicleId == null
                ? null
                : () => _openEditSheet(context, ref),
            icon: const Icon(Icons.edit_outlined, size: 20),
            color: const Color(0xFF0F3D1A),
            tooltip: 'Edit vehicle',
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
          ),
          IconButton(
            onPressed: vehicleId == null
                ? null
                : () => _confirmDelete(context, ref),
            icon: const Icon(Icons.delete_outline, size: 20),
            color: const Color(0xFFEF4444),
            tooltip: 'Delete vehicle',
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
          ),
          const Spacer(),
          OutlinedButton.icon(
            onPressed: vehicleId == null
                ? null
                : () => context.go(
                    '/vehicle-documents/$vehicleId?plate=${Uri.encodeComponent(plateNumber)}'),
            icon: const Icon(Icons.folder_outlined, size: 16),
            label: const Text('Documents'),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF0F3D1A),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              textStyle: const TextStyle(fontSize: 12),
            ),
          ),
        ]),
      ]),
    );
  }
}

// ── Edit vehicle bottom sheet ─────────────────────────────────────────────

class _EditVehicleSheet extends ConsumerStatefulWidget {
  final int vehicleId;
  final TextEditingController plateCtrl;
  final TextEditingController truckTypeCtrl;
  final TextEditingController capacityCtrl;
  final TextEditingController cityCtrl;

  const _EditVehicleSheet({
    required this.vehicleId,
    required this.plateCtrl,
    required this.truckTypeCtrl,
    required this.capacityCtrl,
    required this.cityCtrl,
  });

  @override
  ConsumerState<_EditVehicleSheet> createState() => _EditVehicleSheetState();
}

class _EditVehicleSheetState extends ConsumerState<_EditVehicleSheet> {
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    widget.plateCtrl.dispose();
    widget.truckTypeCtrl.dispose();
    widget.capacityCtrl.dispose();
    widget.cityCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final plate = widget.plateCtrl.text.trim();
      final truckType = widget.truckTypeCtrl.text.trim();
      final capacity = widget.capacityCtrl.text.trim();
      final city = widget.cityCtrl.text.trim();
      await ref.read(vehicleRepositoryProvider).update(widget.vehicleId, {
        if (truckType.isNotEmpty) 'truck_type': truckType,
        if (plate.isNotEmpty) 'plate_number': plate.toUpperCase(),
        if (capacity.isNotEmpty && double.tryParse(capacity) != null)
          'capacity': WeightFormat.kuntalToTons(double.parse(capacity)),
        if (city.isNotEmpty) 'current_city': city,
      });
      if (mounted) {
        ref.invalidate(fleetDashboardProvider);
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Vehicle updated'),
          backgroundColor: Colors.green,
        ));
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = e is ApiException ? e.message : 'Failed to update vehicle: $e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Edit Vehicle',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            TextField(
              controller: widget.truckTypeCtrl,
              decoration: InputDecoration(
                labelText: 'Vehicle Type',
                prefixIcon: const Icon(Icons.local_shipping_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: widget.plateCtrl,
              textCapitalization: TextCapitalization.characters,
              decoration: InputDecoration(
                labelText: 'Plate Number',
                prefixIcon: const Icon(Icons.confirmation_number_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: widget.capacityCtrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: 'Capacity (kuntal)',
                suffixText: 'kuntal',
                prefixIcon: const Icon(Icons.scale_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: widget.cityCtrl,
              decoration: InputDecoration(
                labelText: 'Current City',
                prefixIcon: const Icon(Icons.location_city_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 10),
              Text(_error!,
                  style: const TextStyle(color: Colors.red, fontSize: 12)),
            ],
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saving ? null : _save,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.green,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('Save Changes',
                        style: TextStyle(color: Colors.white)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Helper widgets ────────────────────────────────────────────────────────

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _StatCard({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withAlpha(18),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withAlpha(50)),
      ),
      child: Column(children: [
        Icon(icon, color: color, size: 22),
        const SizedBox(height: 4),
        Text(value,
            style: TextStyle(
                fontSize: 22, fontWeight: FontWeight.bold, color: color)),
        Text(label,
            style: TextStyle(fontSize: 10, color: Colors.grey[600]),
            textAlign: TextAlign.center),
      ]),
    );
  }
}

class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ActionBtn({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color.withAlpha(18),
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Container(
          height: 72,
          decoration: BoxDecoration(
            border: Border.all(color: color.withAlpha(50)),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: color, size: 24),
              const SizedBox(height: 4),
              Text(label,
                  style: TextStyle(
                      fontSize: 12,
                      color: color,
                      fontWeight: FontWeight.w600)),
            ],
          ),
        ),
      ),
    );
  }
}

class _DriverTile extends StatelessWidget {
  final Map<String, dynamic> driver;
  final VoidCallback? onRemove;
  final VoidCallback? onTap;
  const _DriverTile({required this.driver, this.onRemove, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(children: [
            const CircleAvatar(
              backgroundColor: Color(0xFF1976D2),
              child: Icon(Icons.person, color: Colors.white, size: 18),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(driver['name']?.toString() ?? '—',
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.bold)),
                  Text(driver['phone']?.toString() ?? '—',
                      style:
                          TextStyle(fontSize: 12, color: Colors.grey[600])),
                ],
              ),
            ),
            if (onTap != null)
              Icon(Icons.chevron_right, color: Colors.grey[400], size: 20),
            if (onRemove != null)
              IconButton(
                icon: const Icon(Icons.remove_circle_outline,
                    color: Colors.red, size: 20),
                onPressed: onRemove,
                tooltip: 'Remove from fleet',
              ),
          ]),
        ),
      ),
    );
  }
}

class _VehicleTile extends StatelessWidget {
  final Map<String, dynamic> vehicle;
  const _VehicleTile({required this.vehicle});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: Row(children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: Colors.green.withAlpha(20),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.local_shipping,
              color: Colors.green, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(vehicle['plate_number']?.toString() ?? '—',
                  style: const TextStyle(
                      fontSize: 13, fontWeight: FontWeight.bold)),
              Text(
                  '${vehicle['truck_type'] ?? ''} · '
                  '${vehicle['capacity'] ?? 0}t · '
                  '${vehicle['current_city'] ?? ''}',
                  style:
                      TextStyle(fontSize: 12, color: Colors.grey[600])),
            ],
          ),
        ),
        Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: vehicle['availability_status'] == 'available'
                ? Colors.green.withAlpha(20)
                : Colors.orange.withAlpha(20),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            vehicle['availability_status']?.toString() ?? '—',
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.bold,
              color: vehicle['availability_status'] == 'available'
                  ? Colors.green
                  : Colors.orange,
            ),
          ),
        ),
      ]),
    );
  }
}

class _ActiveBookingTile extends StatelessWidget {
  final Map<String, dynamic> booking;
  const _ActiveBookingTile({required this.booking});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.blue.withAlpha(8),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.blue.withAlpha(50)),
      ),
      child: Row(children: [
        const Icon(Icons.route, color: Colors.blue, size: 20),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(booking['route']?.toString() ?? '—',
                  style: const TextStyle(
                      fontSize: 13, fontWeight: FontWeight.w600)),
              Text(
                  'ETB ${booking['estimated_price'] ?? 0}'
                  '  ·  Status: ${booking['booking_status'] ?? '—'}',
                  style:
                      TextStyle(fontSize: 11, color: Colors.grey[600])),
            ],
          ),
        ),
      ]),
    );
  }
}

class _EmptyState extends StatelessWidget {
  final String message;
  const _EmptyState({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.grey[50],
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: Text(message,
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.grey[500], fontSize: 13)),
    );
  }
}
