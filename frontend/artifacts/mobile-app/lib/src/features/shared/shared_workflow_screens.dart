import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';
import '../../data/models/models.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});
  @override Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(notificationsProvider);
    return Scaffold(appBar: AppBar(title: const Text('Notifications'), actions: [IconButton(onPressed: () async { await ref.read(notificationRepositoryProvider).markAllRead(); ref.invalidate(notificationsProvider); }, icon: const Icon(Icons.done_all))]), body: async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => _ErrorView(message: e.toString(), retry: () => ref.invalidate(notificationsProvider)),
      data: (data) => data.items.isEmpty ? const _EmptyView(title: 'No notifications yet') : RefreshIndicator(onRefresh: () async => ref.invalidate(notificationsProvider), child: ListView(children: data.items.map((n) => ListTile(leading: Icon(n.isRead ? Icons.notifications_none : Icons.notifications_active), title: Text(n.message), subtitle: Text(n.createdAt.toLocal().toString()), onTap: () async { if (!n.isRead) { await ref.read(notificationRepositoryProvider).markRead(n.id); ref.invalidate(notificationsProvider); } })).toList())),
    ));
  }
}

class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});
  @override Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(authNotifierProvider).user?.role ?? '';
    final bookings = ref.watch(bookingListProvider);
    final bids = role == 'driver' ? ref.watch(myBidsProvider) : const AsyncValue.data(<Bid>[]);
    final cargo = role == 'shipper' ? ref.watch(cargoListProvider) : const AsyncValue.data(<CargoRequest>[]);
    return Scaffold(appBar: AppBar(title: const Text('Activity/History')), body: RefreshIndicator(onRefresh: () async { ref.invalidate(bookingListProvider); if (role == 'driver') ref.invalidate(myBidsProvider); if (role == 'shipper') ref.invalidate(cargoListProvider); }, child: ListView(padding: const EdgeInsets.all(16), children: [const Text('Derived activity from authorized Spring data. This is not an authoritative audit log.'), const SizedBox(height: 12), if (role == 'shipper') ..._cargoSection(cargo), ..._bookingSection(bookings), if (role == 'driver') ..._bidSection(bids)])));
  }
  List<Widget> _bookingSection(AsyncValue<List<Booking>> value) => value.when(loading: () => [const LinearProgressIndicator()], error: (e, _) => [Text('Bookings unavailable: $e')], data: (items) => items.isEmpty ? [const _EmptyView(title: 'No booking history')] : _bookingTiles(items));
  List<Widget> _bidSection(AsyncValue<List<Bid>> value) => value.when(loading: () => [const LinearProgressIndicator()], error: (e, _) => [Text('Bid history unavailable: $e')], data: (items) => items.isEmpty ? [const _EmptyView(title: 'No bid history')] : _bidTiles(items));
  List<Widget> _cargoSection(AsyncValue<List<CargoRequest>> value) => value.when(loading: () => [const LinearProgressIndicator()], error: (e, _) => [Text('Freight activity unavailable: $e')], data: (items) => items.isEmpty ? [const _EmptyView(title: 'No freight activity')] : [const ListTile(title: Text('Freight activity')), ...items.map((c) => ListTile(title: Text('Freight #${c.id}'), subtitle: Text('${c.pickupLocation} → ${c.destination}'), trailing: Text(c.status)))]);
  List<Widget> _bookingTiles(List<Booking> items) => [const ListTile(title: Text('Bookings')), ...items.map((b) => ListTile(title: Text('Booking #${b.id}'), subtitle: Text('${b.routeLabel}\n${_date(b.createdAt)}'), isThreeLine: true, trailing: Text(b.tripStatus ?? b.bookingStatus)))];
  List<Widget> _bidTiles(List<Bid> items) => [const ListTile(title: Text('Bids')), ...items.map((b) => ListTile(title: Text('Bid #${b.id}'), subtitle: Text('${b.cargoPickup ?? 'Freight #${b.cargoRequestId}'}\nETB ${b.amount.toStringAsFixed(2)} · ${_date(b.createdAt)}'), isThreeLine: true, trailing: Text(b.status)))];
  String _date(DateTime? value) => value == null ? 'Date unavailable' : value.toLocal().toString();
}

class MessagesScreen extends ConsumerStatefulWidget { final int freightId; const MessagesScreen({required this.freightId, super.key}); @override ConsumerState<MessagesScreen> createState() => _MessagesScreenState(); }
class _MessagesScreenState extends ConsumerState<MessagesScreen> {
  final controller = TextEditingController();
  bool sending = false;
  @override void dispose() { controller.dispose(); super.dispose(); }
  @override Widget build(BuildContext context) { final future = ref.watch(_messagesProvider(widget.freightId)); return Scaffold(appBar: AppBar(title: Text('Messages · Freight #${widget.freightId}')), body: Column(children: [Expanded(child: future.when(loading: () => const Center(child: CircularProgressIndicator()), error: (e, _) => _ErrorView(message: e.toString(), retry: () => ref.invalidate(_messagesProvider(widget.freightId))), data: (messages) => messages.isEmpty ? const _EmptyView(title: 'No messages yet') : ListView(children: messages.reversed.map((m) => ListTile(title: Text(m.content), subtitle: Text(m.createdAt?.toLocal().toString() ?? 'Date unavailable'))).toList()))), SafeArea(child: Padding(padding: const EdgeInsets.all(8), child: Row(children: [Expanded(child: TextField(controller: controller, enabled: !sending, decoration: const InputDecoration(hintText: 'Message'))), IconButton(onPressed: sending ? null : _send, icon: sending ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.send))])))])); }
  Future<void> _send() async { final content = controller.text.trim(); if (content.isEmpty) return; setState(() => sending = true); try { await ref.read(sharedWorkflowRepositoryProvider).sendMessage(freightId: widget.freightId, content: content); controller.clear(); ref.invalidate(_messagesProvider(widget.freightId)); } catch (e) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Message failed: $e'))); } finally { if (mounted) setState(() => sending = false); } }
}
final _messagesProvider = FutureProvider.autoDispose.family<List<ChatMessage>, int>((ref, id) => ref.read(sharedWorkflowRepositoryProvider).messages(id));

class ContractScreen extends ConsumerWidget { final int freightId; const ContractScreen({required this.freightId, super.key}); @override Widget build(BuildContext context, WidgetRef ref) { final future = ref.watch(_contractProvider(freightId)); return Scaffold(appBar: AppBar(title: const Text('Contract')), body: future.when(loading: () => const Center(child: CircularProgressIndicator()), error: (e, _) => _ErrorView(message: e.toString(), retry: () => ref.invalidate(_contractProvider(freightId))), data: (c) => ListView(padding: const EdgeInsets.all(16), children: [Text('Contract #${c.id}', style: Theme.of(context).textTheme.headlineSmall), Text('Status: ${c.status}'), Text('Payment: ${c.paymentStatus}'), Text('Agreed amount: ETB ${c.agreedPrice.toStringAsFixed(2)}'), Text('${c.pickupLocation ?? 'Origin unavailable'} → ${c.deliveryLocation ?? 'Destination unavailable'}'), const SizedBox(height: 20), const Text('PDF download and digital signing are not available in Spring Boot yet.')]))); } }
final _contractProvider = FutureProvider.autoDispose.family<ContractView, int>((ref, id) => ref.read(sharedWorkflowRepositoryProvider).contract(id));

class DisputeScreen extends ConsumerStatefulWidget { final int freightId; const DisputeScreen({required this.freightId, super.key}); @override ConsumerState<DisputeScreen> createState() => _DisputeScreenState(); }
class _DisputeScreenState extends ConsumerState<DisputeScreen> {
  final reason = TextEditingController();
  final description = TextEditingController();
  bool filing = false;

  @override
  void dispose() {
    reason.dispose();
    description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final future = ref.watch(_disputesProvider(widget.freightId));
    return Scaffold(
      appBar: AppBar(title: const Text('Disputes')),
      body: Column(
        children: [
          Expanded(
            child: future.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => _ErrorView(
                message: e.toString(),
                retry: () => ref.invalidate(_disputesProvider(widget.freightId)),
              ),
              data: (items) => items.isEmpty
                  ? const _EmptyView(title: 'No disputes')
                  : ListView(
                      children: items
                          .map((d) => ListTile(
                              title: Text(d.reason),
                              subtitle: Text('${d.status} · ${d.description.isEmpty ? 'No description' : d.description}\n${d.createdAt?.toLocal().toString() ?? 'Date unavailable'}'),
                              isThreeLine: true,
                            ))
                          .toList(),
                    ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              children: [
                TextField(
                  controller: reason,
                  decoration: const InputDecoration(labelText: 'Reason'),
                ),
                TextField(
                  controller: description,
                  decoration: const InputDecoration(labelText: 'Description'),
                ),
                ElevatedButton(
                  onPressed: filing ? null : () async {
                    if (reason.text.trim().isEmpty) return;
                    final messenger = ScaffoldMessenger.of(context);
                    setState(() => filing = true);
                    try {
                      await ref.read(sharedWorkflowRepositoryProvider).fileDispute(
                        freightId: widget.freightId,
                        reason: reason.text.trim(),
                        description: description.text.trim(),
                      );
                      reason.clear();
                      description.clear();
                      ref.invalidate(_disputesProvider(widget.freightId));
                    } catch (e) {
                      if (mounted) messenger.showSnackBar(SnackBar(content: Text('Dispute filing failed: $e')));
                    } finally {
                      if (mounted) setState(() => filing = false);
                    }
                  },
                  child: filing ? const CircularProgressIndicator() : const Text('File dispute'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
final _disputesProvider = FutureProvider.autoDispose.family<List<DisputeView>, int>((ref, id) => ref.read(sharedWorkflowRepositoryProvider).disputes(id));

class _EmptyView extends StatelessWidget { final String title; const _EmptyView({required this.title}); @override Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(title))); }
class _ErrorView extends StatelessWidget { final String message; final VoidCallback retry; const _ErrorView({required this.message, required this.retry}); @override Widget build(BuildContext context) => Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Text(message, textAlign: TextAlign.center), ElevatedButton(onPressed: retry, child: const Text('Retry'))])); }