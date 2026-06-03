import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.read(authNotifierProvider).user;
    final cargoAsync = ref.watch(cargoListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('EthioLoad AI'),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => context.go('/profile'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(cargoListProvider),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Greeting ─────────────────────────────────────────
            Text(
              'Welcome back, ${user?.fullName ?? 'User'}!',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            Text(
              _formatRole(user?.role ?? 'unknown'),
              style: TextStyle(color: Colors.grey[600], fontSize: 13),
            ),

            const SizedBox(height: 24),

            // ── Quick Actions ─────────────────────────────────────
            const Text(
              'Quick Actions',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),

            // Use a Row + Expanded instead of GridView to avoid unbounded height
            Row(
              children: [
                Expanded(
                  child: _ActionCard(
                    icon: Icons.add_circle,
                    title: 'Post Cargo',
                    color: Colors.blue,
                    onTap: () => context.go('/create-freight'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _ActionCard(
                    icon: Icons.local_shipping,
                    title: 'Browse Cargo',
                    color: Colors.green,
                    onTap: () => context.go('/freight'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _ActionCard(
                    icon: Icons.psychology,
                    title: 'AI Tools',
                    color: Colors.purple,
                    onTap: () => context.go('/ai-tools'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _ActionCard(
                    icon: Icons.dashboard,
                    title: 'Driver View',
                    color: Colors.orange,
                    onTap: () => context.go('/driver-dashboard'),
                  ),
                ),
              ],
            ),

            const SizedBox(height: 24),

            // ── Stats ─────────────────────────────────────────────
            const Text(
              'Overview',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),

            cargoAsync.when(
              data: (cargoList) {
                final total = cargoList.length;
                final pending =
                    cargoList.where((c) => c.status == 'pending').length;
                final completed =
                    cargoList.where((c) => c.status == 'completed').length;
                return Column(
                  children: [
                    Row(
                      children: [
                        Expanded(
                            child: _StatCard(
                                label: 'Total',
                                value: '$total',
                                unit: 'requests')),
                        const SizedBox(width: 12),
                        Expanded(
                            child: _StatCard(
                                label: 'Pending',
                                value: '$pending',
                                unit: 'requests')),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                            child: _StatCard(
                                label: 'Completed',
                                value: '$completed',
                                unit: 'requests')),
                        const SizedBox(width: 12),
                        Expanded(
                            child: _StatCard(
                                label: 'Role',
                                value: _formatRole(user?.role ?? '—'),
                                unit: 'account')),
                      ],
                    ),
                  ],
                );
              },
              loading: () => const Center(
                  child: Padding(
                padding: EdgeInsets.all(16),
                child: CircularProgressIndicator(),
              )),
              error: (e, _) => Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red[50],
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text('Error loading cargo: $e',
                    style: const TextStyle(color: Colors.red)),
              ),
            ),

            const SizedBox(height: 24),

            // ── Recent Activity ───────────────────────────────────
            const Text(
              'Recent Activity',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),

            cargoAsync.when(
              data: (cargoList) {
                if (cargoList.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.symmetric(vertical: 16),
                    child: Text('No cargo requests yet.',
                        style: TextStyle(color: Colors.grey)),
                  );
                }
                return Column(
                  children: cargoList.take(5).map((cargo) {
                    final color = cargo.status == 'completed'
                        ? Colors.green
                        : cargo.status == 'matched'
                            ? Colors.blue
                            : Colors.orange;
                    final icon = cargo.status == 'completed'
                        ? Icons.check_circle
                        : cargo.status == 'matched'
                            ? Icons.local_shipping
                            : Icons.access_time;
                    return _ActivityTile(
                      title:
                          '${cargo.materialType} → ${cargo.destination}',
                      subtitle: cargo.status[0].toUpperCase() +
                          cargo.status.substring(1),
                      icon: icon,
                      color: color,
                      onTap: () => context.go('/freight/${cargo.id}'),
                    );
                  }).toList(),
                );
              },
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),

            const SizedBox(height: 32),
          ],
        ),
      ),
      bottomNavigationBar: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        currentIndex: 0,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(icon: Icon(Icons.local_shipping), label: 'Cargo'),
          BottomNavigationBarItem(icon: Icon(Icons.receipt_long), label: 'Bookings'),
          BottomNavigationBarItem(icon: Icon(Icons.psychology), label: 'AI'),
          BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Profile'),
        ],
        onTap: (i) {
          switch (i) {
            case 1: context.go('/freight');
            case 2: context.go('/my-bookings');
            case 3: context.go('/ai-tools');
            case 4: context.go('/profile');
          }
        },
      ),
    );
  }

  String _formatRole(String role) => role.isEmpty
      ? ''
      : role[0].toUpperCase() + role.substring(1).replaceAll('_', ' ');
}

// ── Widgets ───────────────────────────────────────────────────────────────

class _ActionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final Color color;
  final VoidCallback onTap;

  const _ActionCard({
    required this.icon,
    required this.title,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color.withAlpha(25),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          height: 90,
          decoration: BoxDecoration(
            border: Border.all(color: color.withAlpha(60)),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 28, color: color),
              const SizedBox(height: 6),
              Text(
                title,
                textAlign: TextAlign.center,
                style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: color),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final String unit;

  const _StatCard(
      {required this.label, required this.value, required this.unit});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.grey[100],
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[300]!),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: TextStyle(fontSize: 12, color: Colors.grey[600])),
          const SizedBox(height: 4),
          Text(value,
              style: const TextStyle(
                  fontSize: 22, fontWeight: FontWeight.bold)),
          Text(unit,
              style:
                  TextStyle(fontSize: 10, color: Colors.grey[500])),
        ],
      ),
    );
  }
}

class _ActivityTile extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _ActivityTile({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: color.withAlpha(30),
        child: Icon(icon, color: color, size: 20),
      ),
      title: Text(title,
          style: const TextStyle(
              fontSize: 13, fontWeight: FontWeight.w600)),
      subtitle: Text(subtitle,
          style: TextStyle(fontSize: 12, color: Colors.grey[600])),
      trailing: const Icon(Icons.chevron_right, color: Colors.grey),
      onTap: onTap,
    );
  }
}
