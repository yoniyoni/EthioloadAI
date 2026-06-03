import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// DriverDashboardScreen — shows the driver's stats and available cargo.
class DriverDashboardScreen extends ConsumerWidget {
  const DriverDashboardScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Use ref.read to avoid rebuild loops — user data doesn't change during a session
    final driver = ref.read(authNotifierProvider).user;

    // Pending cargo requests = potential jobs for drivers
    final cargoAsync = ref.watch(cargoListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Driver Dashboard'),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            onPressed: () {},
          ),
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => context.go('/profile'),
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Status banner ─────────────────────────────────────────
            Container(
              color: Colors.blue[50],
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Welcome, ${driver?.fullName ?? 'Driver'}',
                            style: const TextStyle(
                                fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                          const SizedBox(height: 4),
                          Row(children: [
                            Container(
                              width: 10,
                              height: 10,
                              decoration: const BoxDecoration(
                                  color: Colors.green,
                                  shape: BoxShape.circle),
                            ),
                            const SizedBox(width: 6),
                            const Text('Available for jobs',
                                style: TextStyle(fontSize: 13)),
                          ]),
                        ],
                      ),
                      OutlinedButton(
                        onPressed: () {},
                        child: const Text('Go Offline'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  // Stats row
                  cargoAsync.when(
                    data: (list) {
                      final pending =
                          list.where((c) => c.status == 'pending').length;
                      return Row(
                        children: [
                          _StatTile(
                              label: 'Open Jobs',
                              value: pending.toString()),
                          const SizedBox(width: 12),
                          _StatTile(
                              label: 'My Bookings',
                              value: '—'),
                          const SizedBox(width: 12),
                          _StatTile(label: 'Rating', value: '4.8'),
                        ],
                      );
                    },
                    loading: () =>
                        const Center(child: CircularProgressIndicator()),
                    error: (_, __) => const SizedBox.shrink(),
                  ),
                ],
              ),
            ),

            // ── AI Backhaul banner ────────────────────────────────────
            Padding(
              padding: const EdgeInsets.all(16),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [Colors.green[600]!, Colors.green[400]!],
                  ),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.psychology,
                        color: Colors.white, size: 28),
                    const SizedBox(width: 12),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('AI Backhaul Optimizer',
                              style: TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold)),
                          SizedBox(height: 2),
                          Text(
                            'Find return cargo to avoid empty trips',
                            style: TextStyle(
                                color: Colors.white70, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () {},
                      style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.white,
                          foregroundColor: Colors.green[700],
                          padding: const EdgeInsets.symmetric(
                              horizontal: 14, vertical: 8)),
                      child: const Text('Find',
                          style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ],
                ),
              ),
            ),

            // ── Available cargo ───────────────────────────────────────
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Available Cargo',
                      style: TextStyle(
                          fontSize: 16, fontWeight: FontWeight.bold)),
                  TextButton(
                    onPressed: () => context.go('/freight'),
                    child: const Text('View All'),
                  ),
                ],
              ),
            ),

            cargoAsync.when(
              data: (cargoList) {
                final pending = cargoList
                    .where((c) => c.status == 'pending')
                    .take(5)
                    .toList();

                if (pending.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(
                        child: Text('No pending cargo at the moment.',
                            style: TextStyle(color: Colors.grey))),
                  );
                }

                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Column(
                    children: pending
                        .map((cargo) => Padding(
                              padding: const EdgeInsets.only(bottom: 12),
                              child: _CargoJobCard(
                                cargo: cargo,
                                onAccept: () async {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: Text(
                                          'Accepted: ${cargo.pickupLocation} → ${cargo.destination}'),
                                      backgroundColor: Colors.green,
                                    ),
                                  );
                                },
                                onDetails: () =>
                                    context.go('/freight/${cargo.id}'),
                              ),
                            ))
                        .toList(),
                  ),
                );
              },
              loading: () =>
                  const Center(child: CircularProgressIndicator()),
              error: (error, _) => Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text('Error: $error',
                      style: const TextStyle(color: Colors.red)),
                ),
              ),
            ),

            const SizedBox(height: 32),
          ],
        ),
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: 0,
        items: const [
          BottomNavigationBarItem(
              icon: Icon(Icons.dashboard), label: 'Dashboard'),
          BottomNavigationBarItem(
              icon: Icon(Icons.local_shipping), label: 'Jobs'),
          BottomNavigationBarItem(
              icon: Icon(Icons.person), label: 'Profile'),
        ],
        onTap: (index) {
          if (index == 1) context.go('/freight');
          if (index == 2) context.go('/profile');
        },
      ),
    );
  }
}

// ── Widgets ───────────────────────────────────────────────────────────────

class _StatTile extends StatelessWidget {
  final String label;
  final String value;
  const _StatTile({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        children: [
          Text(value,
              style: const TextStyle(
                  fontSize: 20, fontWeight: FontWeight.bold)),
          const SizedBox(height: 2),
          Text(label,
              style: TextStyle(fontSize: 11, color: Colors.grey[600]),
              textAlign: TextAlign.center),
        ],
      ),
    );
  }
}

class _CargoJobCard extends StatelessWidget {
  final CargoRequest cargo;
  final VoidCallback onAccept;
  final VoidCallback onDetails;

  const _CargoJobCard({
    required this.cargo,
    required this.onAccept,
    required this.onDetails,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[200]!),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 4,
              offset: const Offset(0, 2))
        ],
      ),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Route
          Text(
            '${cargo.pickupLocation} → ${cargo.destination}',
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 6),

          // Cargo type & weight
          Row(children: [
            const Icon(Icons.inventory_2, size: 15, color: Colors.grey),
            const SizedBox(width: 5),
            Text('${cargo.materialType} • ${cargo.weight} tons',
                style: const TextStyle(fontSize: 12)),
          ]),
          const SizedBox(height: 6),

          // Urgency badge
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: Colors.purple.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              cargo.urgencyLevel.toUpperCase(),
              style: const TextStyle(
                  fontSize: 10,
                  color: Colors.purple,
                  fontWeight: FontWeight.w600),
            ),
          ),
          const SizedBox(height: 12),

          // Budget + actions
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Budget',
                      style:
                          TextStyle(fontSize: 11, color: Colors.grey)),
                  Text(
                    cargo.budget != null
                        ? 'ETB ${cargo.budget!.toStringAsFixed(0)}'
                        : 'Negotiable',
                    style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.green),
                  ),
                ],
              ),
              Row(children: [
                OutlinedButton(
                  onPressed: onDetails,
                  style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 8)),
                  child: const Text('Details',
                      style: TextStyle(fontSize: 12)),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: onAccept,
                  style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 8)),
                  child: const Text('Accept',
                      style: TextStyle(fontSize: 12)),
                ),
              ]),
            ],
          ),
        ],
      ),
    );
  }
}
