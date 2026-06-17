import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';

// ── Palette ───────────────────────────────────────────────────────────────
const _green = Color(0xFF0F3D1A);
const _amber = Color(0xFFF59E0B);
const _purple = Color(0xFF7C3AED);
const _bg = Color(0xFFF8FBF8);
const _border = Color(0xFFE5E7EB);
const _textPrimary = Color(0xFF111827);
const _textSecondary = Color(0xFF6B7280);
const _danger = Color(0xFFEF4444);

class DriverBidsScreen extends ConsumerStatefulWidget {
  const DriverBidsScreen({super.key});

  @override
  ConsumerState<DriverBidsScreen> createState() => _DriverBidsScreenState();
}

class _DriverBidsScreenState extends ConsumerState<DriverBidsScreen> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // Poll every 15 seconds so shipper accept/counter/reject shows up promptly
    _timer = Timer.periodic(const Duration(seconds: 15), (_) {
      if (mounted) ref.invalidate(myBidsProvider);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bidsAsync = ref.watch(myBidsProvider);

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Text('bid.my_bids_title'.tr(),
            style: const TextStyle(
                color: Colors.white, fontWeight: FontWeight.w600)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            onPressed: () => ref.invalidate(myBidsProvider),
          ),
        ],
      ),
      body: RefreshIndicator(
        color: _amber,
        onRefresh: () async => ref.invalidate(myBidsProvider),
        child: bidsAsync.when(
          data: (bids) => _BidsList(bids: bids),
          loading: () => ListView(
            padding: const EdgeInsets.all(16),
            children: List.generate(3, (_) => const _SkeletonCard()),
          ),
          error: (e, _) => Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 40, color: _danger),
                const SizedBox(height: 8),
                Text(e.toString(),
                    textAlign: TextAlign.center,
                    style: const TextStyle(color: _textSecondary)),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => ref.invalidate(myBidsProvider),
                  icon: const Icon(Icons.refresh, size: 16),
                  label: Text('common.retry'.tr()),
                  style: OutlinedButton.styleFrom(foregroundColor: _green),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ── Bids list ─────────────────────────────────────────────────────────────

class _BidsList extends StatelessWidget {
  final List<Bid> bids;
  const _BidsList({required this.bids});

  @override
  Widget build(BuildContext context) {
    final actionNeeded = bids.where((b) => b.needsDriverAction).toList();
    final pending = bids.where((b) => b.status == 'pending').toList();
    final waiting = bids
        .where((b) => b.isCountered && b.counterBy == 'driver')
        .toList();
    final history = bids
        .where((b) =>
            b.status == 'accepted' ||
            b.status == 'rejected' ||
            b.status == 'expired')
        .toList();

    if (bids.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SizedBox(height: 60),
          const Icon(Icons.gavel_outlined, size: 56, color: _border),
          const SizedBox(height: 12),
          Text('bid.no_driver_bids'.tr(),
              textAlign: TextAlign.center,
              style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: _textPrimary)),
          const SizedBox(height: 6),
          Text(
            'bid.no_driver_bids_sub'.tr(),
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 13, color: _textSecondary),
          ),
          const SizedBox(height: 20),
          Center(
            child: ElevatedButton.icon(
              onPressed: () => context.go('/driver'),
              icon: const Icon(Icons.search, size: 16),
              label: Text('bid.browse_cargo'.tr()),
              style: ElevatedButton.styleFrom(
                backgroundColor: _green,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── Counter-offers needing action ─────────────────────────
        if (actionNeeded.isNotEmpty) ...[
          _SectionHeader(
            icon: Icons.notification_important_rounded,
            label: 'bid.action_required'.tr(),
            count: actionNeeded.length,
            color: _purple,
          ),
          const SizedBox(height: 8),
          ...actionNeeded.map((b) => _DriverBidCard(bid: b)),
          const SizedBox(height: 16),
        ],

        // ── Pending (waiting for shipper) ─────────────────────────
        if (pending.isNotEmpty) ...[
          _SectionHeader(
            icon: Icons.hourglass_empty_rounded,
            label: 'bid.awaiting_shipper'.tr(),
            count: pending.length,
            color: _amber,
          ),
          const SizedBox(height: 8),
          ...pending.map((b) => _DriverBidCard(bid: b)),
          const SizedBox(height: 16),
        ],

        // ── Waiting for driver counter response ───────────────────
        if (waiting.isNotEmpty) ...[
          _SectionHeader(
            icon: Icons.schedule_rounded,
            label: 'bid.counter_sent'.tr(),
            count: waiting.length,
            color: _amber,
          ),
          const SizedBox(height: 8),
          ...waiting.map((b) => _DriverBidCard(bid: b)),
          const SizedBox(height: 16),
        ],

        // ── History ───────────────────────────────────────────────
        if (history.isNotEmpty) ...[
          _SectionHeader(
            icon: Icons.history_rounded,
            label: 'bid.history'.tr(),
            count: history.length,
            color: _textSecondary,
          ),
          const SizedBox(height: 8),
          ...history.map((b) => _DriverBidCard(bid: b)),
        ],
      ],
    );
  }
}

// ── Section header ────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final IconData icon;
  final String label;
  final int count;
  final Color color;
  const _SectionHeader(
      {required this.icon,
      required this.label,
      required this.count,
      required this.color});

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Icon(icon, size: 15, color: color),
      const SizedBox(width: 6),
      Text(label,
          style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: color)),
      const SizedBox(width: 6),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 1),
        decoration: BoxDecoration(
          color: color.withAlpha(20),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text('$count',
            style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: color)),
      ),
    ]);
  }
}

// ── Driver bid card ───────────────────────────────────────────────────────

class _DriverBidCard extends ConsumerStatefulWidget {
  final Bid bid;
  const _DriverBidCard({required this.bid});

  @override
  ConsumerState<_DriverBidCard> createState() => _DriverBidCardState();
}

class _DriverBidCardState extends ConsumerState<_DriverBidCard> {
  bool _accepting = false;
  bool _rejecting = false;

  Future<void> _acceptShipperCounter() async {
    setState(() => _accepting = true);
    try {
      await ref.read(bidRepositoryProvider).acceptCounter(widget.bid.id);
      if (mounted) {
        ref.invalidate(myBidsProvider);
        ref.invalidate(bookingListProvider);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.success'.tr()),
          backgroundColor: _green,
        ));
        context.go('/my-bookings');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.error_prefix'
              .tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: _danger,
        ));
      }
    } finally {
      if (mounted) setState(() => _accepting = false);
    }
  }

  Future<void> _rejectShipperCounter() async {
    setState(() => _rejecting = true);
    try {
      await ref.read(bidRepositoryProvider).rejectBid(widget.bid.id);
      if (mounted) {
        ref.invalidate(myBidsProvider);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.success'.tr()),
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.error_prefix'
              .tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: _danger,
        ));
      }
    } finally {
      if (mounted) setState(() => _rejecting = false);
    }
  }

  void _openCounterBack() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _DriverCounterSheet(
        bid: widget.bid,
        onSent: () => ref.invalidate(myBidsProvider),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.bid;
    final needsAction = b.needsDriverAction;

    Color borderColor = _border;
    if (needsAction) borderColor = _purple.withAlpha(120);
    if (b.status == 'accepted') borderColor = _green.withAlpha(100);
    if (b.status == 'rejected') borderColor = _danger.withAlpha(60);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: borderColor, width: needsAction ? 1.5 : 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // ── Cargo route row ─────────────────────────────────────────
        Row(children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: _green.withAlpha(15),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.inventory_2_outlined,
                color: _green, size: 18),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${b.cargoPickup ?? '?'} → ${b.cargoDestination ?? '?'}',
                    style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: _textPrimary),
                  ),
                  if (b.cargoMaterial != null)
                    Text(
                      '${b.cargoMaterial}'
                      '${b.cargoWeight != null ? '  ·  ${b.cargoWeight!.toStringAsFixed(0)} t' : ''}',
                      style: const TextStyle(
                          fontSize: 11, color: _textSecondary),
                    ),
                ]),
          ),
          _StatusBadge(status: b.status, counterBy: b.counterBy),
        ]),

        const SizedBox(height: 10),
        const Divider(height: 1, color: _border),
        const SizedBox(height: 10),

        // ── Price row ───────────────────────────────────────────────
        Row(children: [
          _PriceChip(
            label: 'bid.your_bid'.tr(),
            amount: b.amount,
            color: _textSecondary,
            strikethrough: needsAction,
          ),
          if (b.counterAmount != null) ...[
            const SizedBox(width: 8),
            const Icon(Icons.arrow_forward_rounded,
                size: 14, color: _textSecondary),
            const SizedBox(width: 8),
            _PriceChip(
              label: b.counterBy == 'shipper'
                  ? 'bid.shipper_offers'.tr()
                  : 'bid.your_counter'.tr(),
              amount: b.counterAmount!,
              color: b.counterBy == 'shipper' ? _purple : _amber,
            ),
          ],
          if (b.cargoBudget != null) ...[
            const Spacer(),
            Text('${('bid.budget_label'.tr())} ETB ${_fmt(b.cargoBudget!)}',
                style: const TextStyle(fontSize: 11, color: _amber)),
          ],
        ]),

        // ── Accepted: shipper contact ───────────────────────────────
        if (b.status == 'accepted') ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: _green.withAlpha(10),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: _green.withAlpha(60), width: 0.5),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  const Icon(Icons.check_circle_rounded, size: 13, color: _green),
                  const SizedBox(width: 6),
                  Text('bid.price_agreed_shipper'.tr(),
                      style: const TextStyle(
                          fontSize: 11,
                          color: _green,
                          fontWeight: FontWeight.w600)),
                ]),
                const SizedBox(height: 8),
                if (b.shipperName != null) ...[
                  Row(children: [
                    const Icon(Icons.person_outline,
                        size: 14, color: _textSecondary),
                    const SizedBox(width: 6),
                    Text(b.shipperName!,
                        style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: _textPrimary)),
                  ]),
                  const SizedBox(height: 4),
                ],
                if (b.shipperPhone != null)
                  Row(children: [
                    const Icon(Icons.phone_outlined, size: 14, color: _green),
                    const SizedBox(width: 6),
                    Text(b.shipperPhone!,
                        style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: _green)),
                  ])
                else
                  Text('bid.shipper_contact_na'.tr(),
                      style:
                          const TextStyle(fontSize: 12, color: _textSecondary)),
              ],
            ),
          ),
        ],

        // ── Shipper counter note ────────────────────────────────────
        if (needsAction &&
            b.counterNote != null &&
            b.counterNote!.isNotEmpty) ...[
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: _purple.withAlpha(10),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.format_quote_rounded,
                    size: 14, color: _purple),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(b.counterNote!,
                      style: const TextStyle(
                          fontSize: 12,
                          fontStyle: FontStyle.italic,
                          color: _textSecondary)),
                ),
              ],
            ),
          ),
        ],

        // ── Action buttons (only when shipper has countered) ────────
        if (needsAction) ...[
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: (_accepting || _rejecting) ? null : _acceptShipperCounter,
              style: ElevatedButton.styleFrom(
                backgroundColor: _purple,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
                elevation: 0,
              ),
              child: _accepting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text(
                      'bid.accept_amount'
                          .tr(namedArgs: {'amount': _fmt(b.counterAmount!)}),
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(
              child: OutlinedButton(
                onPressed: (_accepting || _rejecting) ? null : _openCounterBack,
                style: OutlinedButton.styleFrom(
                  foregroundColor: _amber,
                  side: const BorderSide(color: _amber, width: 0.5),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                ),
                child: Text('bid.counter_back'.tr(),
                    style: const TextStyle(
                        fontSize: 12, fontWeight: FontWeight.w600)),
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: (_accepting || _rejecting)
                  ? null
                  : _rejectShipperCounter,
              style: OutlinedButton.styleFrom(
                foregroundColor: _danger,
                side: const BorderSide(color: _danger, width: 0.5),
                padding: const EdgeInsets.symmetric(
                    vertical: 11, horizontal: 18),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: _rejecting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: _danger))
                  : Text('bid.decline'.tr(),
                      style: const TextStyle(fontSize: 12)),
            ),
          ]),
        ],
      ]),
    );
  }
}

// ── Status badge ──────────────────────────────────────────────────────────

class _StatusBadge extends StatelessWidget {
  final String status;
  final String? counterBy;
  const _StatusBadge({required this.status, this.counterBy});

  @override
  Widget build(BuildContext context) {
    Color color;
    String label;

    switch (status) {
      case 'countered':
        color = counterBy == 'shipper' ? _purple : _amber;
        label = counterBy == 'shipper' ? 'Countered' : 'You Countered';
        break;
      case 'accepted':
        color = _green;
        label = 'Accepted';
        break;
      case 'rejected':
        color = _danger;
        label = 'Rejected';
        break;
      case 'expired':
        color = _textSecondary;
        label = 'Expired';
        break;
      default:
        color = _amber;
        label = 'Pending';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withAlpha(18),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withAlpha(60), width: 0.5),
      ),
      child: Text(label,
          style: TextStyle(
              fontSize: 10, color: color, fontWeight: FontWeight.w700)),
    );
  }
}

// ── Price chip ────────────────────────────────────────────────────────────

class _PriceChip extends StatelessWidget {
  final String label;
  final double amount;
  final Color color;
  final bool strikethrough;
  const _PriceChip({
    required this.label,
    required this.amount,
    required this.color,
    this.strikethrough = false,
  });

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(label,
          style: const TextStyle(fontSize: 10, color: _textSecondary)),
      Text(
        'ETB ${_fmt(amount)}',
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w700,
          color: color,
          decoration:
              strikethrough ? TextDecoration.lineThrough : null,
        ),
      ),
    ]);
  }
}

// ── Driver counter-back bottom sheet ─────────────────────────────────────

class _DriverCounterSheet extends ConsumerStatefulWidget {
  final Bid bid;
  final VoidCallback onSent;
  const _DriverCounterSheet({required this.bid, required this.onSent});

  @override
  ConsumerState<_DriverCounterSheet> createState() =>
      _DriverCounterSheetState();
}

class _DriverCounterSheetState extends ConsumerState<_DriverCounterSheet> {
  final _amountCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  bool _sending = false;

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _send() async {
    final amount = double.tryParse(_amountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('common.error'.tr()),
        backgroundColor: _danger,
      ));
      return;
    }
    setState(() => _sending = true);
    try {
      await ref.read(bidRepositoryProvider).counterBid(
            widget.bid.id,
            counterAmount: amount,
            counterNote: _noteCtrl.text.trim().isEmpty
                ? null
                : _noteCtrl.text.trim(),
          );
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.success'.tr()),
          backgroundColor: _green,
        ));
        widget.onSent();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.error_prefix'
              .tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: _danger,
        ));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.bid;
    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: _border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text('bid.counter_back_title'.tr(),
                style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: _textPrimary)),
            const SizedBox(height: 4),
            Text(
              'bid.shipper_offers_label'
                  .tr(namedArgs: {'amount': _fmt(b.counterAmount!)}),
              style: const TextStyle(fontSize: 12, color: _textSecondary),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _amountCtrl,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: 'bid.counter_amount_label'.tr(),
                prefixIcon: const Icon(Icons.payments_outlined),
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10)),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 14, vertical: 12),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _noteCtrl,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: 'bid.note_label'.tr(),
                hintText: 'bid.note_hint_driver'.tr(),
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10)),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 14, vertical: 12),
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _sending ? null : _send,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _amber,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10)),
                  elevation: 0,
                ),
                child: _sending
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : Text('bid.send_counter'.tr(),
                        style: const TextStyle(
                            fontSize: 14, fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Skeleton ──────────────────────────────────────────────────────────────

class _SkeletonCard extends StatelessWidget {
  const _SkeletonCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      height: 130,
      decoration: BoxDecoration(
        color: Colors.grey[200],
        borderRadius: BorderRadius.circular(12),
      ),
    );
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────

String _fmt(double v) => v
    .toStringAsFixed(0)
    .replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
