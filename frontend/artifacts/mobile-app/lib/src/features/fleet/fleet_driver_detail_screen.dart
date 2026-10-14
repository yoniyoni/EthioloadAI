import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';
import 'fleet_dashboard_screen.dart';

class FleetDriverDetailScreen extends ConsumerStatefulWidget {
  final int driverId;

  const FleetDriverDetailScreen({super.key, required this.driverId});

  @override
  ConsumerState<FleetDriverDetailScreen> createState() =>
      _FleetDriverDetailScreenState();
}

class _FleetDriverDetailScreenState
    extends ConsumerState<FleetDriverDetailScreen> {
  bool _removing = false;

  Future<void> _removeDriver() async {
    final shouldRemove = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove driver from fleet?'),
        content: const Text(
          'The driver will keep their account. They will only be unlinked '
          'from this fleet.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Remove from fleet'),
          ),
        ],
      ),
    );
    if (shouldRemove != true || !mounted) return;

    setState(() => _removing = true);
    try {
      await ref.read(fleetRepositoryProvider).removeDriver(widget.driverId);
      ref.invalidate(fleetDriversProvider);
      ref.invalidate(fleetDashboardProvider);
      ref.invalidate(fleetDashboardPageProvider(0));
      if (mounted) context.pop();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not remove driver: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _removing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final drivers = ref.watch(fleetDriversProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Driver Details')),
      body: drivers.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Could not load fleet drivers: $error'),
          ),
        ),
        data: (items) {
          Map<String, dynamic>? driver;
          for (final item in items) {
            if ((item['id'] as num?)?.toInt() == widget.driverId) {
              driver = item;
              break;
            }
          }
          if (driver == null) {
            return const Center(child: Text('This driver is not in your fleet.'));
          }

          final isActive = driver['isActive'] == true;
          final isVerified = driver['verificationStatus'] == true;
          return ListView(
            padding: const EdgeInsets.all(20),
            children: [
              const CircleAvatar(
                radius: 36,
                child: Icon(Icons.person_outline, size: 38),
              ),
              const SizedBox(height: 12),
              Center(
                child: Text(
                  driver['fullName']?.toString() ?? 'Driver',
                  style: Theme.of(context).textTheme.titleLarge,
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 20),
              _DriverDetailRow(
                label: 'Driver ID',
                value: widget.driverId.toString(),
              ),
              _DriverDetailRow(
                label: 'Phone',
                value: driver['phone']?.toString() ?? 'Not provided',
              ),
              _DriverDetailRow(
                label: 'Email',
                value: driver['email']?.toString() ?? 'Not provided',
              ),
              _DriverDetailRow(
                label: 'Account status',
                value: isActive ? 'Active' : 'Inactive',
              ),
              _DriverDetailRow(
                label: 'Verification status',
                value: isVerified ? 'Verified' : 'Not verified',
              ),
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: _removing ? null : _removeDriver,
                icon: _removing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.person_remove_outlined),
                label: const Text('Remove from fleet'),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _DriverDetailRow extends StatelessWidget {
  final String label;
  final String value;

  const _DriverDetailRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) => ListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(label),
        subtitle: Text(value),
      );
}
