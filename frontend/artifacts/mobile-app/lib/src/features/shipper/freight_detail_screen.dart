import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// CargoDetailScreen — view details and book a cargo request.
class FreightDetailScreen extends ConsumerWidget {
  final int freightId;
  const FreightDetailScreen({required this.freightId, super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cargoAsync = ref.watch(singleCargoProvider(freightId));

    return cargoAsync.when(
      data: (cargo) => _DetailScaffold(cargo: cargo),
      loading: () => Scaffold(
        appBar: AppBar(title: const Text('Cargo Details')),
        body: const Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => Scaffold(
        appBar: AppBar(title: const Text('Cargo Details')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, size: 48, color: Colors.red),
              const SizedBox(height: 12),
              Text(e.toString()),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => ref.refresh(singleCargoProvider(freightId)),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Main scaffold ──────────────────────────────────────────────────────────

class _DetailScaffold extends ConsumerWidget {
  final CargoRequest cargo;
  const _DetailScaffold({required this.cargo});

  Color get _statusColor {
    switch (cargo.status) {
      case 'matched':
        return Colors.blue;
      case 'completed':
        return Colors.green;
      default:
        return Colors.orange;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final vehiclesAsync = ref.watch(vehicleListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Cargo Details'), elevation: 0),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Header ────────────────────────────────────────────────
            Container(
              width: double.infinity,
              color: Colors.blue[50],
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          '${cargo.pickupLocation} → ${cargo.destination}',
                          style: const TextStyle(
                              fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: _statusColor.withAlpha(30),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          cargo.status.toUpperCase(),
                          style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: _statusColor,
                              fontSize: 11),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Row(children: [
                    Expanded(
                      child: _InfoTile(
                        label: 'Budget',
                        value: cargo.budget != null
                            ? 'ETB ${cargo.budget!.toStringAsFixed(0)}'
                            : 'Negotiable',
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _InfoTile(
                          label: 'Weight',
                          value: '${cargo.weight} t'),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _InfoTile(
                          label: 'Urgency', value: cargo.urgencyLevel),
                    ),
                  ]),
                ],
              ),
            ),

            // ── Cargo details ──────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Cargo Details',
                      style: TextStyle(
                          fontSize: 14, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 10),
                  _DetailRow('Material', cargo.materialType),
                  _DetailRow('Weight', '${cargo.weight} tons'),
                  _DetailRow('Urgency', cargo.urgencyLevel),
                  _DetailRow('Status', cargo.status),
                  if (cargo.budget != null)
                    _DetailRow('Budget',
                        'ETB ${cargo.budget!.toStringAsFixed(2)}'),
                  if (cargo.createdAt != null)
                    _DetailRow('Posted',
                        cargo.createdAt!.toLocal().toString().split(' ').first),

                  const SizedBox(height: 20),
                  const Text('Locations',
                      style: TextStyle(
                          fontSize: 14, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 10),
                  _LocationCard(
                      icon: Icons.location_on,
                      title: 'Pickup',
                      address: cargo.pickupLocation,
                      color: Colors.blue),
                  const SizedBox(height: 10),
                  _LocationCard(
                      icon: Icons.flag,
                      title: 'Destination',
                      address: cargo.destination,
                      color: Colors.green),

                  // ── Available vehicles ─────────────────────────────
                  if (cargo.status == 'pending') ...[
                    const SizedBox(height: 24),
                    const Text('Available Vehicles',
                        style: TextStyle(
                            fontSize: 14, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 10),
                    vehiclesAsync.when(
                      data: (vehicles) {
                        final available = vehicles
                            .where((v) =>
                                v.availabilityStatus == 'available' &&
                                v.capacity >= cargo.weight)
                            .toList();

                        if (available.isEmpty) {
                          return Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: Colors.grey[100],
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: const Text(
                              'No vehicles available with sufficient capacity right now.',
                              style: TextStyle(color: Colors.grey),
                            ),
                          );
                        }

                        return Column(
                          children: available
                              .map((v) => _VehicleCard(
                                    vehicle: v,
                                    cargo: cargo,
                                    onBooked: () {
                                      // Invalidate providers to refresh data
                                      ref.invalidate(cargoListProvider);
                                      ref.invalidate(singleCargoProvider(cargo.id));
                                      ref.invalidate(bookingListProvider);
                                      context.go('/freight');
                                    },
                                  ))
                              .toList(),
                        );
                      },
                      loading: () => const Center(
                          child: Padding(
                        padding: EdgeInsets.all(16),
                        child: CircularProgressIndicator(),
                      )),
                      error: (e, _) => Text('Error loading vehicles: $e',
                          style: const TextStyle(color: Colors.red)),
                    ),
                  ],

                  const SizedBox(height: 32),
                ],
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.all(16),
        child: OutlinedButton(
          onPressed: () => context.pop(),
          child: const Text('Back'),
        ),
      ),
    );
  }
}

// ── Vehicle card with Book button ──────────────────────────────────────────

class _VehicleCard extends ConsumerStatefulWidget {
  final Vehicle vehicle;
  final CargoRequest cargo;
  final VoidCallback onBooked;

  const _VehicleCard({
    required this.vehicle,
    required this.cargo,
    required this.onBooked,
  });

  @override
  ConsumerState<_VehicleCard> createState() => _VehicleCardState();
}

class _VehicleCardState extends ConsumerState<_VehicleCard> {
  bool _loading = false;

  Future<void> _book() async {
    setState(() => _loading = true);
    try {
      // Use AI to get estimated price first
      double estimatedPrice = widget.cargo.budget ?? 10000;
      try {
        final aiResult = await ref.read(aiRepositoryProvider).predictPrice(
              pickupLocation: widget.cargo.pickupLocation,
              destination: widget.cargo.destination,
              weight: widget.cargo.weight,
              materialType: widget.cargo.materialType,
            );
        final predicted = aiResult['estimated_price'] ?? aiResult['predicted_price'];
        if (predicted != null) {
          estimatedPrice = (predicted as num).toDouble();
        }
      } catch (_) {
        // fall back to budget if AI fails
      }

      await ref.read(bookingRepositoryProvider).create(
            cargoId: widget.cargo.id,
            vehicleId: widget.vehicle.id,
            driverId: widget.vehicle.userId,
            estimatedPrice: estimatedPrice,
          );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
                'Booked! Vehicle ${widget.vehicle.plateNumber} assigned. '
                'Estimated: ETB ${estimatedPrice.toStringAsFixed(0)}'),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 3),
          ),
        );
        widget.onBooked();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Booking failed: $e'),
              backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final v = widget.vehicle;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey[200]!),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withAlpha(10),
              blurRadius: 4,
              offset: const Offset(0, 2))
        ],
      ),
      child: Row(
        children: [
          // Vehicle icon
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: Colors.blue.withAlpha(20),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.local_shipping,
                color: Colors.blue, size: 24),
          ),
          const SizedBox(width: 12),
          // Vehicle info
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(v.plateNumber,
                    style: const TextStyle(
                        fontSize: 14, fontWeight: FontWeight.bold)),
                Text(
                    '${v.truckType} · ${v.capacity}t · ${v.currentCity}',
                    style: TextStyle(
                        fontSize: 12, color: Colors.grey[600])),
                Row(children: [
                  const Icon(Icons.star, size: 12, color: Colors.amber),
                  const SizedBox(width: 2),
                  Text(v.rating.toStringAsFixed(1),
                      style: const TextStyle(fontSize: 12)),
                ]),
              ],
            ),
          ),
          // Book button
          ElevatedButton(
            onPressed: _loading ? null : _book,
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.blue,
              padding: const EdgeInsets.symmetric(
                  horizontal: 16, vertical: 10),
            ),
            child: _loading
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: Colors.white))
                : const Text('Book', style: TextStyle(fontSize: 13)),
          ),
        ],
      ),
    );
  }
}

// ── Shared helper widgets ──────────────────────────────────────────────────

class _InfoTile extends StatelessWidget {
  final String label;
  final String value;
  const _InfoTile({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(fontSize: 11, color: Colors.black54)),
        const SizedBox(height: 2),
        Text(value,
            style: const TextStyle(
                fontSize: 13, fontWeight: FontWeight.bold)),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  const _DetailRow(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: TextStyle(fontSize: 13, color: Colors.grey[600])),
          Text(value,
              style: const TextStyle(
                  fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

class _LocationCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String address;
  final Color color;

  const _LocationCard({
    required this.icon,
    required this.title,
    required this.address,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey[300]!),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
                color: color.withAlpha(25),
                borderRadius: BorderRadius.circular(8)),
            child: Icon(icon, color: color, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey[500],
                        fontWeight: FontWeight.w500)),
                const SizedBox(height: 2),
                Text(address,
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.w600),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
