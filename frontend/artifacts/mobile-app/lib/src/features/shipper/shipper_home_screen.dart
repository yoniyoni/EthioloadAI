import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../shared/widgets/shared_widgets.dart';

class ShipperHomeScreen extends ConsumerWidget {
  const ShipperHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.read(authNotifierProvider).user;
    final cargoAsync = ref.watch(cargoListProvider);
    final bookingsAsync = ref.watch(bookingListProvider);

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(
        title: 'EthioLoad AI',
        actions: [
          const NotificationBell(),
          IconButton(
            icon: const Icon(Icons.account_circle_outlined, color: Colors.white),
            onPressed: () => context.go('/profile'),
          ),
        ],
      ),
      body: RefreshIndicator(
        color: kAmber,
        onRefresh: () async {
          ref.invalidate(cargoListProvider);
          ref.invalidate(bookingListProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Greeting ──────────────────────────────────────────────
            _GreetingCard(userName: user?.fullName ?? 'Shipper'),
            const SizedBox(height: 20),

            // ── Quick actions ──────────────────────────────────────────
            _SectionLabel(text: 'common.quick_actions'.tr()),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                child: _ActionTile(
                  icon: Icons.add_circle_outline,
                  label: 'shipper.post_cargo'.tr(),
                  isPrimary: true,
                  onTap: () => context.go('/create-freight'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ActionTile(
                  icon: Icons.local_shipping_outlined,
                  label: 'shipper.browse_cargo'.tr(),
                  onTap: () => context.go('/freight'),
                ),
              ),
            ]),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                child: _ActionTile(
                  icon: Icons.receipt_long_outlined,
                  label: 'shipper.my_bookings'.tr(),
                  onTap: () => context.go('/my-bookings'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ActionTile(
                  icon: Icons.psychology_outlined,
                  label: 'shipper.ai_tools'.tr(),
                  onTap: () => context.go('/ai-tools'),
                ),
              ),
            ]),

            const SizedBox(height: 24),

            // ── Stats ─────────────────────────────────────────────────
            _SectionLabel(text: 'shipper.overview'.tr()),
            const SizedBox(height: 10),
            cargoAsync.when(
              data: (list) => Row(children: [
                Expanded(
                  child: _StatCard(
                    label: 'shipper.total_cargo'.tr(),
                    value: '${list.length}',
                    icon: Icons.inventory_2_outlined,
                    color: kGreen,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _StatCard(
                    label: 'shipper.pending'.tr(),
                    value: '${list.where((c) => c.status == "pending").length}',
                    icon: Icons.hourglass_top_outlined,
                    color: kAmber,
                  ),
                ),
              ]),
              loading: () => const Row(children: [
                Expanded(child: ShimmerBox(height: 80, radius: 12)),
                SizedBox(width: 10),
                Expanded(child: ShimmerBox(height: 80, radius: 12)),
              ]),
              error: (e, _) => Text(
                'common.error_prefix'.tr(namedArgs: {'msg': e.toString()}),
                style: const TextStyle(color: Colors.red),
              ),
            ),
            const SizedBox(height: 10),
            bookingsAsync.when(
              data: (list) => Row(children: [
                Expanded(
                  child: _StatCard(
                    label: 'shipper.bookings'.tr(),
                    value: '${list.length}',
                    icon: Icons.bookmark_outline,
                    color: kGreen,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _StatCard(
                    label: 'shipper.awaiting_accept'.tr(),
                    value: '${list.where((b) => b.bookingStatus == "pending").length}',
                    icon: Icons.pending_outlined,
                    color: kAmber,
                  ),
                ),
              ]),
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),

            const SizedBox(height: 24),

            // ── Recent cargo ──────────────────────────────────────────
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _SectionLabel(text: 'shipper.recent_cargo'.tr()),
                TextButton(
                  onPressed: () => context.go('/freight'),
                  style: TextButton.styleFrom(foregroundColor: kGreen),
                  child: Text(
                    'shipper.view_all'.tr(),
                    style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            cargoAsync.when(
              data: (list) {
                if (list.isEmpty) {
                  return EmptyState(
                    icon: Icons.inventory_2_outlined,
                    title: 'shipper.no_cargo'.tr(),
                    subtitle: 'shipper.post_cargo'.tr(),
                  );
                }
                return Column(
                  children: list
                      .take(3)
                      .map((c) => _CargoListItem(
                            cargo: c,
                            onTap: () => context.go('/freight/${c.id}'),
                          ))
                      .toList(),
                );
              },
              loading: () => Column(
                children: List.generate(
                  3,
                  (_) => const Padding(
                    padding: EdgeInsets.only(bottom: 8),
                    child: ShimmerBox(height: 72, radius: 12),
                  ),
                ),
              ),
              error: (_, __) => const SizedBox.shrink(),
            ),

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

// ── Section label ─────────────────────────────────────────────────────────

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel({required this.text});

  @override
  Widget build(BuildContext context) => Text(
        text,
        style: GoogleFonts.inter(
          fontSize: 14, fontWeight: FontWeight.w700, color: kTextPrimary,
        ),
      );
}

// ── Greeting card ─────────────────────────────────────────────────────────

class _GreetingCard extends StatelessWidget {
  final String userName;
  const _GreetingCard({required this.userName});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [kGreen, kGreenLight],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'shipper.hello'.tr(namedArgs: {'name': userName}),
                  style: GoogleFonts.inter(
                    fontSize: 18, fontWeight: FontWeight.w700, color: Colors.white,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  'shipper.account'.tr(),
                  style: GoogleFonts.inter(
                    fontSize: 12, color: Colors.white.withValues(alpha: 0.7),
                  ),
                ),
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    'common.shipper'.tr(),
                    style: GoogleFonts.inter(
                      fontSize: 11, fontWeight: FontWeight.w600,
                      color: Colors.white.withValues(alpha: 0.9),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Container(
            width: 52, height: 52,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(14),
            ),
            child: const Icon(Icons.local_shipping_outlined, color: Colors.white, size: 26),
          ),
        ],
      ),
    );
  }
}

// ── Action tile ───────────────────────────────────────────────────────────

class _ActionTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool isPrimary;

  const _ActionTile({
    required this.icon,
    required this.label,
    required this.onTap,
    this.isPrimary = false,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: isPrimary ? kAmber : kSurface,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          height: 76,
          decoration: BoxDecoration(
            border: Border.all(color: isPrimary ? kAmber : kBorder, width: 1),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 24, color: isPrimary ? Colors.white : kGreen),
              const SizedBox(height: 6),
              Text(
                label,
                textAlign: TextAlign.center,
                style: GoogleFonts.inter(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: isPrimary ? Colors.white : kTextPrimary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Stat card ─────────────────────────────────────────────────────────────

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
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Container(
        decoration: BoxDecoration(
          color: kSurface,
          border: Border.all(color: kBorder),
        ),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Container(width: 3, color: color),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    children: [
                      Icon(icon, color: color, size: 20),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              value,
                              style: GoogleFonts.inter(
                                fontSize: 22,
                                fontWeight: FontWeight.w700,
                                color: color,
                              ),
                            ),
                            Text(
                              label,
                              style: GoogleFonts.inter(
                                  fontSize: 11, color: kTextMuted),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Cargo list item ───────────────────────────────────────────────────────

class _CargoListItem extends StatelessWidget {
  final CargoRequest cargo;
  final VoidCallback onTap;
  const _CargoListItem({required this.cargo, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: kSurface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: kBorder, width: 0.5),
        ),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: kGreenTint,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.inventory_2_outlined, color: kGreen, size: 18),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (cargo.serviceType == 'intracity') ...[
                    Row(children: [
                      const Icon(Icons.location_city, size: 13, color: kGreen),
                      const SizedBox(width: 3),
                      Expanded(
                        child: Text(
                          '${cargo.city ?? ''}: ${cargo.pickupArea ?? ''} → ${cargo.dropoffArea ?? ''}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: kTextPrimary),
                        ),
                      ),
                    ]),
                    const SizedBox(height: 2),
                    Text(
                      cargo.itemsDescription ?? '',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
                    ),
                  ] else ...[
                    RouteDisplay(
                      from: cargo.pickupLocation,
                      to: cargo.destination,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${cargo.materialType} · ${cargo.weight.toStringAsFixed(0)} t',
                      style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            StatusBadge(status: cargo.status),
          ],
        ),
      ),
    );
  }
}
