import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../data/api/api_client.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

// ── Palette ───────────────────────────────────────────────────────────────
const _green = Color(0xFF0F3D1A);
const _greenLight = Color(0xFF1B5E20);
const _amber = Color(0xFFF59E0B);
const _bg = Color(0xFFF8FBF8);
const _border = Color(0xFFE5E7EB);
const _textSecondary = Color(0xFF6B7280);

class DriverDashboardScreen extends ConsumerWidget {
  const DriverDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final driver = ref.read(authNotifierProvider).user;
    final cargoAsync = ref.watch(cargoListProvider);
    final bookingsAsync = ref.watch(bookingListProvider);
    final myBids = ref.watch(myBidsProvider).valueOrNull;

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        title: Text('driver.dashboard'.tr(),
            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
        iconTheme: const IconThemeData(color: Colors.white),
        actions: [
          const NotificationBell(),
          IconButton(
            icon: const Icon(Icons.account_circle_outlined, color: Colors.white),
            onPressed: () => context.go('/profile'),
          ),
        ],
      ),
      body: RefreshIndicator(
        color: _amber,
        onRefresh: () async {
          ref.invalidate(cargoListProvider);
          ref.invalidate(bookingListProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Greeting ──────────────────────────────────────────────
            _GreetingCard(driver: driver),
            const SizedBox(height: 16),

            // ── Stats row ─────────────────────────────────────────────
            bookingsAsync.when(
              data: (list) => _StatsRow(bookings: list),
              loading: () => _StatsRowSkeleton(),
              error: (_, __) => const SizedBox.shrink(),
            ),
            const SizedBox(height: 16),

            // ── AI Backhaul banner ────────────────────────────────────
            _BackhaulBanner(onTap: () => context.go('/ai-tools')),
            const SizedBox(height: 12),

            // ── My Bids & Offers quick-link ───────────────────────────
            _MyBidsBanner(onTap: () => context.go('/driver/bids')),
            const SizedBox(height: 20),

            // ── Jobs needing action ───────────────────────────────────
            bookingsAsync.when(
              data: (list) {
                final pending =
                    list.where((b) => b.bookingStatus == 'pending').toList();
                if (pending.isEmpty) return const SizedBox.shrink();
                return _Section(
                  title: 'driver.needs_action'
                      .tr(namedArgs: {'count': '${pending.length}'}),
                  actionLabel: 'driver.view_all'.tr(),
                  onAction: () => context.go('/my-bookings'),
                  children: pending
                      .take(3)
                      .map((b) => _PendingJobCard(
                            booking: b,
                            onAccepted: () =>
                                ref.invalidate(bookingListProvider),
                          ))
                      .toList(),
                );
              },
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),

            // ── Available cargo ───────────────────────────────────────
            const SizedBox(height: 4),
            _Section(
              title: 'driver.available_cargo'.tr(),
              actionLabel: null,
              onAction: null,
              children: [
                cargoAsync.when(
                  data: (list) {
                    final available = list
                        .where((c) => c.status == 'pending')
                        .take(8)
                        .toList();
                    if (available.isEmpty) {
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 24),
                        child: Center(
                          child: Text('driver.no_cargo'.tr(),
                              style: const TextStyle(color: _textSecondary)),
                        ),
                      );
                    }
                    return Column(
                      children: available
                          .map((c) => _CargoCard(
                                cargo: c,
                                driverId: driver?.id ?? 0,
                                myBids: myBids,
                              ))
                          .toList(),
                    );
                  },
                  loading: () => _CargoSkeleton(),
                  error: (e, _) => Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                        'common.error_prefix'
                            .tr(namedArgs: {'msg': e.toString()}),
                        style: const TextStyle(color: Colors.red)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

// ── Greeting card ─────────────────────────────────────────────────────────

class _GreetingCard extends StatelessWidget {
  final User? driver;
  const _GreetingCard({required this.driver});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Row(children: [
        CircleAvatar(
          radius: 24,
          backgroundColor: _green.withAlpha(20),
          child: Text(
            (driver?.fullName.isNotEmpty == true)
                ? driver!.fullName[0].toUpperCase()
                : 'D',
            style: const TextStyle(
                color: _green, fontSize: 20, fontWeight: FontWeight.bold),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'driver.hello'.tr(
                    namedArgs: {'name': driver?.fullName ?? ''}),
                style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF111827)),
              ),
              const SizedBox(height: 2),
              Row(children: [
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: _green.withAlpha(18),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text('common.driver'.tr(),
                      style: const TextStyle(
                          fontSize: 11,
                          color: _green,
                          fontWeight: FontWeight.w500)),
                ),
              ]),
            ],
          ),
        ),
      ]),
    );
  }
}

// ── Stats row ─────────────────────────────────────────────────────────────

class _StatsRow extends StatelessWidget {
  final List<Booking> bookings;
  const _StatsRow({required this.bookings});

  @override
  Widget build(BuildContext context) {
    final total = bookings.length;
    final active =
        bookings.where((b) => b.bookingStatus == 'confirmed').length;
    final completed =
        bookings.where((b) => b.bookingStatus == 'completed').length;

    return Row(children: [
      Expanded(
          child: _StatCard(
              label: 'driver.total_jobs'.tr(),
              value: '$total',
              icon: Icons.work_outline,
              color: _green)),
      const SizedBox(width: 10),
      Expanded(
          child: _StatCard(
              label: 'driver.active'.tr(),
              value: '$active',
              icon: Icons.local_shipping_outlined,
              color: _amber)),
      const SizedBox(width: 10),
      Expanded(
          child: _StatCard(
              label: 'driver.completed'.tr(),
              value: '$completed',
              icon: Icons.check_circle_outline,
              color: const Color(0xFF059669))),
    ]);
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _StatCard(
      {required this.label,
      required this.value,
      required this.icon,
      required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Column(children: [
        Icon(icon, color: color, size: 22),
        const SizedBox(height: 6),
        Text(value,
            style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w500,
                color: color)),
        const SizedBox(height: 2),
        Text(label,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 11, color: _textSecondary)),
      ]),
    );
  }
}

class _StatsRowSkeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(
          3,
          (i) => Expanded(
                child: Container(
                  margin: EdgeInsets.only(left: i == 0 ? 0 : 10),
                  height: 90,
                  decoration: BoxDecoration(
                    color: Colors.grey[200],
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              )),
    );
  }
}

// ── AI Backhaul banner ────────────────────────────────────────────────────

class _BackhaulBanner extends StatelessWidget {
  final VoidCallback onTap;
  const _BackhaulBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [_green, _greenLight],
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
        ),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(children: [
        const Icon(Icons.psychology, color: Colors.white, size: 28),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('driver.backhaul_title'.tr(),
                  style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 14)),
              const SizedBox(height: 2),
              Text('driver.backhaul_sub'.tr(),
                  style: const TextStyle(color: Colors.white70, fontSize: 12)),
            ],
          ),
        ),
        ElevatedButton(
          onPressed: onTap,
          style: ElevatedButton.styleFrom(
            backgroundColor: _amber,
            foregroundColor: const Color(0xFF111827),
            padding:
                const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
            elevation: 0,
          ),
          child: Text('common.open'.tr(),
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
        ),
      ]),
    );
  }
}

// ── My Bids & Offers banner ───────────────────────────────────────────────

class _MyBidsBanner extends ConsumerWidget {
  final VoidCallback onTap;
  const _MyBidsBanner({required this.onTap});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bidsAsync = ref.watch(myBidsProvider);
    final pendingCount = bidsAsync.whenData(
      (bids) => bids.where((b) => b.needsDriverAction).length,
    ).valueOrNull ?? 0;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: pendingCount > 0
                ? const Color(0xFF7C3AED).withAlpha(120)
                : _border,
            width: pendingCount > 0 ? 1.5 : 0.5,
          ),
        ),
        child: Row(children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: pendingCount > 0
                  ? const Color(0xFF7C3AED).withAlpha(15)
                  : _green.withAlpha(12),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              Icons.gavel_rounded,
              size: 20,
              color: pendingCount > 0
                  ? const Color(0xFF7C3AED)
                  : _green,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  pendingCount > 0
                      ? 'driver.counter_offers_waiting'.tr(namedArgs: {
                          'count': '$pendingCount',
                          'plural': pendingCount > 1 ? 's' : '',
                        })
                      : 'driver.my_bids_title'.tr(),
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: pendingCount > 0
                        ? const Color(0xFF7C3AED)
                        : const Color(0xFF111827),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  pendingCount > 0
                      ? 'driver.tap_to_review'.tr()
                      : 'driver.track_bids'.tr(),
                  style: const TextStyle(
                      fontSize: 11, color: _textSecondary),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right_rounded,
              size: 20, color: _textSecondary),
        ]),
      ),
    );
  }
}

// ── Section wrapper ───────────────────────────────────────────────────────

class _Section extends StatelessWidget {
  final String title;
  final String? actionLabel;
  final VoidCallback? onAction;
  final List<Widget> children;

  const _Section({
    required this.title,
    required this.actionLabel,
    required this.onAction,
    required this.children,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(title,
                style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFF111827))),
            if (actionLabel != null && onAction != null)
              TextButton(
                onPressed: onAction,
                style: TextButton.styleFrom(foregroundColor: _green),
                child: Text(actionLabel!,
                    style: const TextStyle(fontSize: 13)),
              ),
          ],
        ),
        const SizedBox(height: 8),
        ...children,
      ],
    );
  }
}

// ── Pending job card (needs driver action) ────────────────────────────────

class _PendingJobCard extends ConsumerStatefulWidget {
  final Booking booking;
  final VoidCallback onAccepted;

  const _PendingJobCard(
      {required this.booking, required this.onAccepted});

  @override
  ConsumerState<_PendingJobCard> createState() => _PendingJobCardState();
}

class _PendingJobCardState extends ConsumerState<_PendingJobCard> {
  bool _loading = false;

  Future<void> _accept() async {
    setState(() => _loading = true);
    try {
      await ref.read(apiClientProvider).patch<void>(
            '/bookings/${widget.booking.id}',
            data: {'booking_status': 'accepted'},
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('driver.job_accepted'.tr()),
          backgroundColor: _green,
        ));
        widget.onAccepted();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.error_prefix'
              .tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: Colors.red,
        ));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.booking;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _amber.withAlpha(80), width: 0.5),
      ),
      child: Row(children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: _amber.withAlpha(20),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.hourglass_top_rounded,
              color: _amber, size: 22),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                  'driver.booking_label'.tr(namedArgs: {'id': '${b.id}'}),
                  style: const TextStyle(
                      fontWeight: FontWeight.w600, fontSize: 13)),
              const SizedBox(height: 2),
              Text(b.routeLabel,
                  style: const TextStyle(
                      fontSize: 12,
                      color: _textSecondary,
                      fontWeight: FontWeight.w500)),
              Text(
                  'ETB ${_fmt(b.estimatedPrice)}'
                  '${b.weight != null ? "  ·  ${b.weight!.toStringAsFixed(0)}t" : ""}',
                  style:
                      const TextStyle(fontSize: 11, color: _textSecondary)),
            ],
          ),
        ),
        _loading
            ? const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: _green))
            : ElevatedButton(
                onPressed: _accept,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _green,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(
                      horizontal: 14, vertical: 10),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                  elevation: 0,
                ),
                child: Text('bid.accept'.tr(),
                    style: const TextStyle(
                        fontSize: 12, fontWeight: FontWeight.w600)),
              ),
      ]),
    );
  }
}

// ── Cargo card with Place Bid ─────────────────────────────────────────────

class _CargoCard extends StatelessWidget {
  final CargoRequest cargo;
  final int driverId;
  final List<Bid>? myBids;
  const _CargoCard({required this.cargo, required this.driverId, this.myBids});

  Bid? get _existingBid {
    if (myBids == null) return null;
    final matches = myBids!.where(
      (b) => b.cargoRequestId == cargo.id &&
             (b.status == 'pending' || b.status == 'countered'),
    );
    return matches.isEmpty ? null : matches.first;
  }

  @override
  Widget build(BuildContext context) {
    final urgencyColor = _urgencyColor(cargo.urgencyLevel);
    final existingBid = _existingBid;
    final deadline = cargo.bidDeadline;
    final isClosed = deadline != null && DateTime.now().isAfter(deadline);
    final isFixed = cargo.priceType == 'fixed';

    return Opacity(
      opacity: isClosed ? 0.65 : 1.0,
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: isClosed ? const Color(0xFFF5F5F5) : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isClosed ? const Color(0xFFD1D5DB) : _border,
            width: 0.5,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: (isClosed ? Colors.grey : _green).withAlpha(15),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  Icons.inventory_2_outlined,
                  color: isClosed ? Colors.grey : _green,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${cargo.pickupLocation} → ${cargo.destination}',
                        style: TextStyle(
                            fontSize: 13,
                            fontWeight: FontWeight.w600,
                            color: isClosed
                                ? Colors.grey
                                : const Color(0xFF111827))),
                    const SizedBox(height: 2),
                    Text(
                        '${cargo.materialType}  ·  ${cargo.weight.toStringAsFixed(0)} t',
                        style: TextStyle(
                            fontSize: 12,
                            color: isClosed
                                ? Colors.grey[400]
                                : _textSecondary)),
                  ],
                ),
              ),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: (isClosed ? Colors.grey : urgencyColor).withAlpha(25),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                    isClosed ? 'Closed' : cargo.urgencyLevel,
                    style: TextStyle(
                        fontSize: 10,
                        color: isClosed ? Colors.grey : urgencyColor,
                        fontWeight: FontWeight.w600)),
              ),
            ]),
            // Bid deadline banner
            if (deadline != null) ...[
              const SizedBox(height: 6),
              _DeadlineBanner(deadline: deadline, isClosed: isClosed),
            ],
            if (cargo.budget != null) ...[
              const SizedBox(height: 8),
              const Divider(height: 1, color: _border),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '${isFixed ? 'Fixed Price' : 'bid.budget_label'.tr()} ETB ${_fmt(cargo.budget!)}',
                    style: const TextStyle(
                        fontSize: 12,
                        color: _amber,
                        fontWeight: FontWeight.w600),
                  ),
                  if (isFixed)
                    ElevatedButton(
                      onPressed: isClosed
                          ? null
                          : () => context.go('/freight/${cargo.id}'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: _green,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 8),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8)),
                        elevation: 0,
                      ),
                      child: const Text('Accept Offer',
                          style: TextStyle(
                              fontSize: 12, fontWeight: FontWeight.w600)),
                    )
                  else
                    _BidActionButton(
                      existingBid: existingBid,
                      enabled: !isClosed,
                      onTap: isClosed
                          ? () {}
                          : () => _showBidSheet(context, cargo,
                              existingBid: existingBid),
                    ),
                ],
              ),
            ] else ...[
              const SizedBox(height: 8),
              Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('bid.budget_label'.tr(),
                        style: const TextStyle(
                            fontSize: 12, color: _textSecondary)),
                    if (isFixed)
                      ElevatedButton(
                        onPressed: isClosed
                            ? null
                            : () => context.go('/freight/${cargo.id}'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: _green,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(
                              horizontal: 14, vertical: 8),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8)),
                          elevation: 0,
                        ),
                        child: const Text('Accept Offer',
                            style: TextStyle(
                                fontSize: 12, fontWeight: FontWeight.w600)),
                      )
                    else
                      _BidActionButton(
                        existingBid: existingBid,
                        enabled: !isClosed,
                        onTap: isClosed
                            ? () {}
                            : () => _showBidSheet(context, cargo,
                                existingBid: existingBid),
                      ),
                  ]),
            ],
          ],
        ),
      ),
    );
  }

  void _showBidSheet(BuildContext context, CargoRequest cargo, {Bid? existingBid}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => _PlaceBidSheet(cargo: cargo, existingBid: existingBid),
    );
  }

  Color _urgencyColor(String level) {
    switch (level.toLowerCase()) {
      case 'express':
        return Colors.red;
      case 'high':
        return Colors.orange;
      case 'normal':
        return _green;
      default:
        return _textSecondary;
    }
  }
}

// ── Cargo skeleton ────────────────────────────────────────────────────────

class _CargoSkeleton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Column(
      children: List.generate(
          3,
          (_) => Container(
                margin: const EdgeInsets.only(bottom: 10),
                height: 90,
                decoration: BoxDecoration(
                  color: Colors.grey[200],
                  borderRadius: BorderRadius.circular(12),
                ),
              )),
    );
  }
}

// ── Bid action button (Place Bid / Edit Bid) ──────────────────────────────

class _BidActionButton extends StatelessWidget {
  final Bid? existingBid;
  final VoidCallback onTap;
  final bool enabled;
  const _BidActionButton({
    required this.existingBid,
    required this.onTap,
    this.enabled = true,
  });

  @override
  Widget build(BuildContext context) {
    if (!enabled) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFFE5E7EB),
          borderRadius: BorderRadius.circular(8),
        ),
        child: const Text('Closed',
            style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Colors.grey)),
      );
    }
    if (existingBid != null) {
      return OutlinedButton(
        onPressed: onTap,
        style: OutlinedButton.styleFrom(
          foregroundColor: _amber,
          side: const BorderSide(color: _amber),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: Text('bid.edit'.tr(),
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
      );
    }
    return ElevatedButton(
      onPressed: onTap,
      style: ElevatedButton.styleFrom(
        backgroundColor: _amber,
        foregroundColor: const Color(0xFF111827),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        elevation: 0,
      ),
      child: Text('bid.place'.tr(),
          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }
}

// ── Bid deadline countdown banner ─────────────────────────────────────────

class _DeadlineBanner extends StatelessWidget {
  final DateTime deadline;
  final bool isClosed;
  const _DeadlineBanner({required this.deadline, required this.isClosed});

  String get _text {
    if (isClosed) return 'Bidding Closed / ጨረታ ተዘጋ';
    final diff = deadline.difference(DateTime.now());
    if (diff.inDays >= 1) {
      return 'Bids close in ${diff.inDays}d ${diff.inHours % 24}h  /  ጨረታ ይዘጋል';
    }
    if (diff.inHours >= 1) {
      return 'Bids close in ${diff.inHours}h ${diff.inMinutes % 60}m';
    }
    return 'Bids close in ${diff.inMinutes}m — hurry!';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: (isClosed ? Colors.grey : _amber).withAlpha(20),
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
          color: (isClosed ? Colors.grey : _amber).withAlpha(70),
          width: 0.5,
        ),
      ),
      child: Row(children: [
        Icon(
          isClosed ? Icons.lock_rounded : Icons.timer_outlined,
          size: 12,
          color: isClosed ? Colors.grey : _amber,
        ),
        const SizedBox(width: 5),
        Text(
          _text,
          style: TextStyle(
            fontSize: 11,
            color: isClosed ? Colors.grey : _amber,
            fontWeight: FontWeight.w600,
          ),
        ),
      ]),
    );
  }
}

// ── Place bid bottom sheet ────────────────────────────────────────────────

class _PlaceBidSheet extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  final Bid? existingBid;
  const _PlaceBidSheet({required this.cargo, this.existingBid});

  @override
  ConsumerState<_PlaceBidSheet> createState() => _PlaceBidSheetState();
}

class _PlaceBidSheetState extends ConsumerState<_PlaceBidSheet> {
  late TextEditingController _amountCtrl;
  late TextEditingController _noteCtrl;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _amountCtrl = TextEditingController(
      text: widget.existingBid != null
          ? widget.existingBid!.amount.toStringAsFixed(0)
          : '',
    );
    _noteCtrl = TextEditingController(text: widget.existingBid?.note ?? '');
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final amountText = _amountCtrl.text.trim();
    final amount = double.tryParse(amountText);
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('common.error'.tr()),
        backgroundColor: Colors.red,
      ));
      return;
    }

    setState(() => _submitting = true);
    try {
      if (widget.existingBid != null) {
        await ref.read(bidRepositoryProvider).update(
              widget.existingBid!.id,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
            );
      } else {
        final vehicles = await ref.read(vehicleListProvider.future);
        if (!mounted) return;
        if (vehicles.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('common.failed'.tr()),
            backgroundColor: Colors.red,
          ));
          setState(() => _submitting = false);
          return;
        }
        await ref.read(bidRepositoryProvider).place(
              cargoId: widget.cargo.id,
              vehicleId: vehicles.first.id,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
            );
      }
      if (mounted) {
        Navigator.of(context).pop();
        ref.invalidate(myBidsProvider);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.success'.tr()),
          backgroundColor: _green,
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.error_prefix'
              .tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: Colors.red,
        ));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.cargo;
    final bottom = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: _border,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),

          Text(widget.existingBid != null ? 'bid.edit_title'.tr() : 'bid.place_title'.tr(),
              style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF111827))),
          const SizedBox(height: 4),
          Text('${c.pickupLocation} → ${c.destination}  ·  ${c.weight.toStringAsFixed(0)} t  ·  ${c.materialType}',
              style: const TextStyle(fontSize: 13, color: _textSecondary)),
          const SizedBox(height: 20),

          Text('bid.amount_label'.tr(),
              style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF374151))),
          const SizedBox(height: 6),
          TextField(
            controller: _amountCtrl,
            keyboardType:
                const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              hintText: 'e.g. 15,000',
              prefixText: 'ETB  ',
              prefixStyle: const TextStyle(
                  color: _amber, fontWeight: FontWeight.w600),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _border),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _green, width: 1.5),
              ),
            ),
          ),
          const SizedBox(height: 12),

          Text('bid.note_label'.tr(),
              style: const TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: Color(0xFF374151))),
          const SizedBox(height: 6),
          TextField(
            controller: _noteCtrl,
            maxLines: 2,
            decoration: InputDecoration(
              hintText: 'bid.note_hint_driver'.tr(),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _border),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: const BorderSide(color: _green, width: 1.5),
              ),
            ),
          ),
          const SizedBox(height: 20),

          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _submitting ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: _green,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text(widget.existingBid != null ? 'bid.edit'.tr() : 'bid.place'.tr(),
                      style: const TextStyle(
                          fontSize: 15, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────

String _fmt(double v) =>
    v.toStringAsFixed(0).replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
