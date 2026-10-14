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

class HistoryScreen extends ConsumerStatefulWidget {
  const HistoryScreen({super.key});

  @override
  ConsumerState<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends ConsumerState<HistoryScreen> {
  final List<Booking> _additionalBookings = [];
  int _nextPage = 1;
  bool? _hasMoreBookings;
  bool _loadingMore = false;
  String? _loadError;

  @override
  Widget build(BuildContext context) {
    final role = ref.watch(authNotifierProvider).user?.role ?? '';
    final bookings = ref.watch(bookingPageProvider(0));
    final bids = role == 'driver' ? ref.watch(myBidsProvider) : const AsyncValue.data(<Bid>[]);
    final cargo = role == 'shipper' ? ref.watch(cargoListProvider) : const AsyncValue.data(<CargoRequest>[]);
    return Scaffold(appBar: AppBar(title: const Text('Activity/History')), body: RefreshIndicator(onRefresh: () async { _resetBookings(); if (role == 'driver') ref.invalidate(myBidsProvider); if (role == 'shipper') ref.invalidate(cargoListProvider); }, child: ListView(padding: const EdgeInsets.all(16), children: [const Text('Derived activity from authorized Spring data. This is not an authoritative audit log.'), const SizedBox(height: 12), if (role == 'shipper') ..._cargoSection(cargo), ..._bookingSection(bookings), if (role == 'driver') ..._bidSection(bids)])));
  }

  void _resetBookings() {
    setState(() {
      _additionalBookings.clear();
      _nextPage = 1;
      _hasMoreBookings = null;
      _loadError = null;
    });
    ref.invalidate(bookingPageProvider(0));
  }

  Future<void> _loadMoreBookings() async {
    if (_loadingMore) return;
    setState(() { _loadingMore = true; _loadError = null; });
    try {
      final page = await ref.read(bookingRepositoryProvider).page(page: _nextPage);
      if (!mounted) return;
      setState(() {
        final ids = _additionalBookings.map((booking) => booking.id).toSet();
        _additionalBookings.addAll(page.content.where((booking) => ids.add(booking.id)));
        _nextPage = page.number + 1;
        _hasMoreBookings = page.hasNext;
      });
    } catch (error) {
      if (mounted) setState(() => _loadError = error.toString());
    } finally {
      if (mounted) setState(() => _loadingMore = false);
    }
  }

  List<Widget> _bookingSection(AsyncValue<BookingPage> value) => value.when(
        loading: () => [const LinearProgressIndicator()],
        error: (e, _) => [Text('Bookings unavailable: $e')],
        data: (page) {
          final byId = <int, Booking>{
            for (final booking in _additionalBookings) booking.id: booking,
            for (final booking in page.content) booking.id: booking,
          };
          final seenIds = <int>{};
          final items = [
            for (final booking in [...page.content, ..._additionalBookings])
              if (seenIds.add(booking.id)) byId[booking.id]!,
          ];
          return [
            if (items.isEmpty) const _EmptyView(title: 'No booking history') else ..._bookingTiles(items),
            if (_loadError != null) Text('Could not load more bookings: $_loadError'),
            if (_hasMoreBookings ?? page.hasNext)
              Center(
                child: OutlinedButton(
                  onPressed: _loadingMore ? null : _loadMoreBookings,
                  child: Text(_loadingMore ? 'Loading…' : 'Load more'),
                ),
              ),
          ];
        },
      );
  List<Widget> _bidSection(AsyncValue<List<Bid>> value) => value.when(loading: () => [const LinearProgressIndicator()], error: (e, _) => [Text('Bid history unavailable: $e')], data: (items) => items.isEmpty ? [const _EmptyView(title: 'No bid history')] : _bidTiles(items));
  List<Widget> _cargoSection(AsyncValue<List<CargoRequest>> value) => value.when(loading: () => [const LinearProgressIndicator()], error: (e, _) => [Text('Freight activity unavailable: $e')], data: (items) => items.isEmpty ? [const _EmptyView(title: 'No freight activity')] : [const ListTile(title: Text('Freight activity')), ...items.map((c) => ListTile(title: Text('Freight #${c.id}'), subtitle: Text('${c.pickupLocation} → ${c.destination}'), trailing: Text(c.status)))]);
  List<Widget> _bookingTiles(List<Booking> items) => [const ListTile(title: Text('Bookings')), ...items.map((b) => ListTile(title: Text('Booking #${b.id}'), subtitle: Text('${b.routeLabel}\n${_date(b.createdAt)}'), isThreeLine: true, trailing: Text(b.tripStatus ?? b.bookingStatus)))];
  List<Widget> _bidTiles(List<Bid> items) => [const ListTile(title: Text('Bids')), ...items.map((b) => ListTile(title: Text('Bid #${b.id}'), subtitle: Text('${b.cargoPickup ?? 'Freight #${b.cargoRequestId}'}\nETB ${b.amount.toStringAsFixed(2)} · ${_date(b.createdAt)}'), isThreeLine: true, trailing: Text(b.status)))];
  String _date(DateTime? value) => value == null ? 'Date unavailable' : value.toLocal().toString();
}

class MessagesScreen extends ConsumerStatefulWidget {
  final int freightId;
  const MessagesScreen({required this.freightId, super.key});

  @override
  ConsumerState<MessagesScreen> createState() => _MessagesScreenState();
}

class _MessagesScreenState extends ConsumerState<MessagesScreen> {
  final controller = TextEditingController();
  List<ChatMessage> messages = [];
  String? olderCursor;
  String? afterCursor;
  bool hasOlder = false;
  bool loading = true;
  bool loadingOlder = false;
  bool refreshing = false;
  bool sending = false;
  Object? loadError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadInitial());
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  Future<void> _loadInitial() async {
    setState(() {
      loading = true;
      loadError = null;
    });
    try {
      final page = await ref.read(sharedWorkflowRepositoryProvider).messages(widget.freightId);
      if (!mounted) return;
      setState(() {
        messages = mergeChatMessages([], page.messages);
        olderCursor = page.olderCursor;
        afterCursor = page.afterCursor;
        hasOlder = page.hasOlder;
        loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        loadError = error;
        loading = false;
      });
    }
  }

  Future<void> _refreshMessages() async {
    if (refreshing) return;
    setState(() => refreshing = true);
    try {
      final page = await ref.read(sharedWorkflowRepositoryProvider).messages(
            widget.freightId,
            after: afterCursor,
          );
      if (!mounted) return;
      setState(() {
        messages = mergeChatMessages(messages, page.messages);
        if (page.mode == 'LATEST') {
          olderCursor = page.olderCursor;
          hasOlder = page.hasOlder;
        }
        afterCursor = page.afterCursor ?? afterCursor;
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Messages unavailable: $error')));
      }
    } finally {
      if (mounted) setState(() => refreshing = false);
    }
  }

  Future<void> _loadOlder() async {
    final cursor = olderCursor;
    if (cursor == null || loadingOlder) return;
    setState(() => loadingOlder = true);
    try {
      final page = await ref.read(sharedWorkflowRepositoryProvider).messages(
            widget.freightId,
            before: cursor,
          );
      if (!mounted) return;
      setState(() {
        messages = mergeChatMessages(messages, page.messages);
        olderCursor = page.olderCursor;
        hasOlder = page.hasOlder;
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Older messages unavailable: $error')));
      }
    } finally {
      if (mounted) setState(() => loadingOlder = false);
    }
  }

  Future<void> _send() async {
    final content = controller.text.trim();
    if (content.isEmpty) return;
    setState(() => sending = true);
    try {
      await ref.read(sharedWorkflowRepositoryProvider).sendMessage(
            freightId: widget.freightId,
            content: content,
          );
      controller.clear();
      await _refreshMessages();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Message failed: $error')));
      }
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Messages · Freight #${widget.freightId}')),
      body: Column(
        children: [
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : loadError != null
                    ? _ErrorView(
                        message: loadError.toString(),
                        retry: _loadInitial,
                      )
                    : RefreshIndicator(
                        onRefresh: _refreshMessages,
                        child: ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: [
                            if (hasOlder)
                              Center(
                                child: TextButton(
                                  onPressed: loadingOlder ? null : _loadOlder,
                                  child: loadingOlder
                                      ? const SizedBox(
                                          width: 18,
                                          height: 18,
                                          child: CircularProgressIndicator(strokeWidth: 2),
                                        )
                                      : const Text('Load older messages'),
                                ),
                              ),
                            if (messages.isEmpty)
                              const _EmptyView(title: 'No messages yet'),
                            ...messages.reversed.map(
                              (message) => ListTile(
                                title: Text(message.content),
                                subtitle: Text(message.createdAt
                                        ?.toLocal()
                                        .toString() ??
                                    'Date unavailable'),
                              ),
                            ),
                          ],
                        ),
                      ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: controller,
                      enabled: !sending,
                      decoration: const InputDecoration(hintText: 'Message'),
                    ),
                  ),
                  IconButton(
                    onPressed: sending ? null : _send,
                    icon: sending
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

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