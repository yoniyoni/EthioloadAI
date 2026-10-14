import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/api/api_client.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';

// ── Fleet dashboard provider ──────────────────────────────────────────────

final fleetDashboardProvider =
    FutureProvider.autoDispose<Map<String, dynamic>>((ref) async {
  return ref.read(fleetRepositoryProvider).getDashboard();
});

final fleetDashboardPageProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, int>((ref, page) async {
  return ref.read(fleetRepositoryProvider).getDashboard(page: page);
});

dynamic _fleetField(Map<String, dynamic> data, String camel, String snake) =>
    data[camel] ?? data[snake];

// ── Screen ────────────────────────────────────────────────────────────────

class FleetDashboardScreen extends ConsumerWidget {
  const FleetDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashAsync = ref.watch(fleetDashboardPageProvider(0));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Fleet Dashboard'),
        elevation: 0,
        actions: [
          IconButton(
            tooltip: 'Company registration',
            icon: const Icon(Icons.business_outlined),
            onPressed: () => context.go('/fleet/companies'),
          ),
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => context.go('/profile'),
          ),
        ],
      ),
      body: dashAsync.when(
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
                onPressed: () => ref.invalidate(fleetDashboardPageProvider(0)),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DashboardBody extends ConsumerStatefulWidget {
  final Map<String, dynamic> data;
  const _DashboardBody({required this.data});

  @override
  ConsumerState<_DashboardBody> createState() => _DashboardBodyState();
}

class _DashboardBodyState extends ConsumerState<_DashboardBody> {
  final List<dynamic> _additionalBookings = [];
  int _nextPage = 1;
  bool? _hasMore;
  bool _loadingMore = false;
  String? _loadError;

  Future<void> _refresh() async {
    setState(() {
      _additionalBookings.clear();
      _nextPage = 1;
      _hasMore = null;
      _loadError = null;
    });
    ref.invalidate(fleetDashboardProvider);
    ref.invalidate(fleetDashboardPageProvider(0));
  }

  Future<void> _loadMore() async {
    if (_loadingMore) return;
    setState(() {
      _loadingMore = true;
      _loadError = null;
    });
    try {
      final page =
          await ref.read(fleetRepositoryProvider).getDashboard(page: _nextPage);
      if (!mounted) return;
      final rows = (page['activeBookings'] ?? page['active_bookings'] ?? const []) as List;
      setState(() {
        final existingIds = _additionalBookings
            .map((booking) => booking['bookingId'] ?? booking['booking_id'])
            .toSet();
        _additionalBookings.addAll(rows.where((booking) {
          final id = booking['bookingId'] ?? booking['booking_id'];
          return existingIds.add(id);
        }));
        _nextPage = ((page['page'] as num?)?.toInt() ?? _nextPage) + 1;
        _hasMore = page['hasNext'] as bool? ?? page['has_next'] as bool? ?? false;
      });
    } catch (error) {
      if (mounted) setState(() => _loadError = error.toString());
    } finally {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.data;
    final drivers = (data['drivers'] as List?) ?? [];
    final vehicles = (data['vehicles'] as List?) ?? [];
    final firstPageBookings =
        (data['activeBookings'] ?? data['active_bookings'] ?? const []) as List;
    final activeBookings = [...firstPageBookings, ..._additionalBookings];
    final hasMore = _hasMore ??
        (data['hasNext'] as bool? ?? data['has_next'] as bool? ?? false);
    final activeBookingCount =
        _fleetField(data, 'activeBookingCount', 'active_booking_count') ?? 0;

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
        Card(child: ListTile(
          leading: const Icon(Icons.business_outlined),
          title: const Text('Company registration'),
          subtitle: const Text('Register your company, upload documents and check approval.'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.go('/fleet/companies'),
        )),
        // ── Stats ──────────────────────────────────────────────────
        Row(children: [
          Expanded(
              child: _StatCard(
                  label: 'Drivers',
                  value: '${_fleetField(data, 'driverCount', 'driver_count') ?? 0}',
                  icon: Icons.people,
                  color: Colors.blue)),
          const SizedBox(width: 12),
          Expanded(
              child: _StatCard(
                  label: 'Vehicles',
                  value: '${_fleetField(data, 'vehicleCount', 'vehicle_count') ?? 0}',
                  icon: Icons.local_shipping,
                  color: Colors.green)),
          const SizedBox(width: 12),
          Expanded(
              child: _StatCard(
                  label: 'Active',
                  value: '$activeBookingCount',
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
        ]),

        const SizedBox(height: 24),
        Card(
          child: ListTile(
            leading: const Icon(Icons.map_outlined),
            title: const Text('Fleet live map'),
            subtitle: const Text('View authorized vehicle locations'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.go('/fleet/live-map'),
          ),
        ),
        const SizedBox(height: 12),

        // ── Active bookings ────────────────────────────────────────
        if (activeBookingCount > 0 || activeBookings.isNotEmpty) ...[
          const Text('Active Jobs',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
          const SizedBox(height: 10),
          if (activeBookings.isEmpty)
            const _EmptyState(message: 'No active bookings on this page.')
          else
            ...activeBookings.map((b) => _ActiveBookingTile(booking: b)),
          if (_loadError != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('Could not load more active bookings: $_loadError'),
            ),
          if (hasMore)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: OutlinedButton(
                onPressed: _loadingMore ? null : _loadMore,
                child: Text(_loadingMore ? 'Loading…' : 'Load more active bookings'),
              ),
            ),
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
          ...drivers.take(3).map(
                (driver) => _DriverTile(
                  driver: driver,
                  onTap: () => context.push(
                    '/fleet/drivers/${driver['id']}',
                  ),
                ),
              ),

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
      ),
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
      final driver = await ref.read(fleetRepositoryProvider).addDriver(id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              '${driver['fullName']?.toString() ?? 'Driver'} added to fleet'),
          backgroundColor: Colors.green,
        ));
        _driverIdCtrl.clear();
        ref.invalidate(fleetDriversProvider);
        ref.invalidate(fleetDashboardProvider);
        ref.invalidate(fleetDashboardPageProvider(0));
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final driversAsync = ref.watch(fleetDriversProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Manage Drivers'), elevation: 0),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
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
          driversAsync.when(
            data: (drivers) {
              if (drivers.isEmpty) {
                return const _EmptyState(message: 'No drivers in your fleet yet.');
              }
              return Column(
                children: drivers
                    .map((d) => _DriverTile(
                          driver: d,
                          onTap: () => context.push(
                            '/fleet/drivers/${d['id']}',
                          ),
                          onRemove: () async {
                            final shouldRemove = await showDialog<bool>(
                              context: context,
                              builder: (context) => AlertDialog(
                                title: const Text('Remove driver from fleet?'),
                                content: const Text(
                                  'The driver will keep their account. They '
                                  'will only be unlinked from this fleet.',
                                ),
                                actions: [
                                  TextButton(
                                    onPressed: () =>
                                        Navigator.pop(context, false),
                                    child: const Text('Cancel'),
                                  ),
                                  FilledButton(
                                    onPressed: () =>
                                        Navigator.pop(context, true),
                                    child: const Text('Remove from fleet'),
                                  ),
                                ],
                              ),
                            );
                            if (shouldRemove != true) return;
                            try {
                              await ref
                                  .read(fleetRepositoryProvider)
                                  .removeDriver((d['id'] as num).toInt());
                              ref.invalidate(fleetDriversProvider);
                              ref.invalidate(fleetDashboardProvider);
                              ref.invalidate(fleetDashboardPageProvider(0));
                            } catch (error) {
                              if (context.mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text(error.toString())),
                                );
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
        _vehicleCategory == null) {
      return;
    }
    setState(() => _loading = true);
    try {
      final driverId = int.tryParse(_driverIdCtrl.text.trim());
      await ref.read(apiClientProvider).post<Map<String, dynamic>>(
        '/fleet/vehicles',
        data: {
          'truckType': _truckType,
          'plateNumber': _plateCtrl.text.trim().toUpperCase(),
          'capacity': double.tryParse(_capacityCtrl.text.trim()) ?? 0,
          'currentCity': _city,
          'vehicleCategory': _vehicleCategory,
          if (driverId != null) 'driverId': driverId,
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
            initialValue: _types.contains(_truckType) ? _truckType : _types.first,
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
              labelText: 'Capacity (tons)',
              suffixText: 'tons',
              prefixIcon: const Icon(Icons.scale_outlined),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            initialValue: _city,
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
    final vehiclesAsync = ref.watch(fleetVehiclesProvider);
    final driversAsync = ref.watch(fleetDriversProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Fleet Vehicles'), elevation: 0),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.go('/fleet/add-vehicle'),
        icon: const Icon(Icons.add),
        label: const Text('Add Vehicle'),
        backgroundColor: Colors.green,
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(fleetVehiclesProvider);
          ref.invalidate(fleetDriversProvider);
          ref.invalidate(fleetDashboardProvider);
          ref.invalidate(fleetDashboardPageProvider(0));
        },
        child: vehiclesAsync.when(
          data: (vehicles) => driversAsync.when(
            data: (drivers) {
              if (vehicles.isEmpty) {
                return const Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.local_shipping_outlined,
                          size: 56, color: Colors.grey),
                      SizedBox(height: 12),
                      Text(
                        'No vehicles in your fleet yet.',
                        style: TextStyle(color: Colors.grey),
                      ),
                    ],
                  ),
                );
              }
              return ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: vehicles.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (_, i) => _VehicleDetailTile(
                  vehicle: vehicles[i],
                  drivers: drivers,
                  onAssign: (driverId) async {
                    await ref.read(apiClientProvider).put<Map<String, dynamic>>(
                      '/fleet/vehicles/${vehicles[i]["id"]}/assign',
                      data: {'driverId': driverId},
                    );
                    ref.invalidate(fleetDashboardProvider);
                    ref.invalidate(fleetDashboardPageProvider(0));
                    ref.invalidate(fleetVehiclesProvider);
                  },
                ),
              );
            },
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(child: Text('Error: $e')),
          ),
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(child: Text('Error: $e')),
        ),
      ),
    );
  }
}

class _VehicleDetailTile extends StatelessWidget {
  final Map<String, dynamic> vehicle;
  final List<Map<String, dynamic>> drivers;
  final Future<void> Function(int driverId) onAssign;

  const _VehicleDetailTile({
    required this.vehicle,
    required this.drivers,
    required this.onAssign,
  });

  @override
  Widget build(BuildContext context) {
    final isAvailable = (vehicle['availabilityStatus'] ?? vehicle['availability_status']) == 'available';
    final assignedDriverId = vehicle['assignedDriverId'] ?? vehicle['assigned_driver_id'];
    final assignedDriver = drivers.cast<Map?>().firstWhere(
          (d) => d?['id'] == assignedDriverId,
          orElse: () => null,
        );

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
              Text((vehicle['plateNumber'] ?? vehicle['plate_number'])?.toString() ?? '—',
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
              Text(
                '${vehicle['truckType'] ?? vehicle['truck_type'] ?? ''} · '
                '${vehicle['capacity'] ?? 0} tons · '
                '${vehicle['currentCity'] ?? vehicle['current_city'] ?? ''}',
                style: TextStyle(fontSize: 12, color: Colors.grey[600]),
              ),
            ]),
          ),
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
              tooltip: 'Assign driver',
              onSelected: (id) => onAssign(id),
              itemBuilder: (_) => drivers
                  .map((d) => PopupMenuItem<int>(
                        value: d['id'] as int,
                        child: Text(d['name']?.toString() ?? '—'),
                      ))
                  .toList(),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.blue.shade200),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: const Text('Assign',
                    style: TextStyle(fontSize: 12, color: Colors.blue)),
              ),
            ),
          ]),
        ],
      ]),
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
  final VoidCallback? onTap;
  final VoidCallback? onRemove;
  const _DriverTile({required this.driver, this.onTap, this.onRemove});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colors.grey[200]!),
        ),
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
                Text(driver['fullName']?.toString() ?? '—',
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.bold)),
                Text(driver['phone']?.toString() ?? '—',
                    style:
                        TextStyle(fontSize: 12, color: Colors.grey[600])),
              ],
            ),
          ),
          if (onRemove != null)
            IconButton(
              icon: const Icon(Icons.remove_circle_outline,
                  color: Colors.red, size: 20),
              onPressed: onRemove,
              tooltip: 'Remove from fleet',
            ),
        ]),
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
              Text((vehicle['plateNumber'] ?? vehicle['plate_number'])?.toString() ?? '—',
                  style: const TextStyle(
                      fontSize: 13, fontWeight: FontWeight.bold)),
              Text(
                  '${vehicle['truckType'] ?? vehicle['truck_type'] ?? ''} · '
                  '${vehicle['capacity'] ?? 0}t · '
                  '${vehicle['currentCity'] ?? vehicle['current_city'] ?? ''}',
                  style:
                      TextStyle(fontSize: 12, color: Colors.grey[600])),
            ],
          ),
        ),
        Container(
          padding:
              const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: (vehicle['availabilityStatus'] ?? vehicle['availability_status']) == 'available'
                ? Colors.green.withAlpha(20)
                : Colors.orange.withAlpha(20),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(
            (vehicle['availabilityStatus'] ?? vehicle['availability_status'])?.toString() ?? '—',
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.bold,
              color: (vehicle['availabilityStatus'] ?? vehicle['availability_status']) == 'available'
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
