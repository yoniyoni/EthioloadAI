import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';

/// FreightListScreen - Browse and filter available freight
class FreightListScreen extends ConsumerStatefulWidget {
  const FreightListScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<FreightListScreen> createState() => _FreightListScreenState();
}

class _FreightListScreenState extends ConsumerState<FreightListScreen> {
  String selectedFilter = 'all';
  final TextEditingController searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
  }  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Get cargo list from EthioLoadAI backend
    final cargoListAsync = ref.watch(cargoListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cargo Requests'),
        elevation: 0,
      ),
      body: cargoListAsync.when(
        data: (cargoList) {
          final q = searchController.text.toLowerCase();
          final filtered = cargoList.where((c) {
            final matchesFilter = selectedFilter == 'all' || c.status == selectedFilter;
            final matchesSearch = q.isEmpty ||
                c.pickupLocation.toLowerCase().contains(q) ||
                c.destination.toLowerCase().contains(q) ||
                c.materialType.toLowerCase().contains(q) ||
                (c.city ?? '').toLowerCase().contains(q) ||
                (c.pickupArea ?? '').toLowerCase().contains(q) ||
                (c.dropoffArea ?? '').toLowerCase().contains(q) ||
                (c.itemsDescription ?? '').toLowerCase().contains(q);
            return matchesFilter && matchesSearch;
          }).toList();

          return SingleChildScrollView(
            child: Column(
              children: [
                // Search Bar
                Padding(
                  padding: EdgeInsets.all(16),
                  child: TextField(
                    controller: searchController,
                    decoration: InputDecoration(
                      hintText: 'Search by route or cargo...',
                      prefixIcon: Icon(Icons.search),
                      suffixIcon: searchController.text.isNotEmpty
                          ? IconButton(
                              icon: Icon(Icons.clear),
                              onPressed: () {
                                searchController.clear();
                                setState(() {});
                              },
                            )
                          : null,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    ),
                    onChanged: (value) {
                      setState(() {});
                    },
                  ),
                ),

                // Filter Chips
                Padding(
                  padding: EdgeInsets.symmetric(horizontal: 16),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        _FilterChip(
                          label: 'All',
                          isSelected: selectedFilter == 'all',
                          onTap: () => setState(() => selectedFilter = 'all'),
                        ),
                        SizedBox(width: 8),
                        _FilterChip(
                          label: 'Open',
                          isSelected: selectedFilter == 'open',
                          onTap: () => setState(() => selectedFilter = 'open'),
                        ),
                        SizedBox(width: 8),
                        _FilterChip(
                          label: 'Assigned',
                          isSelected: selectedFilter == 'assigned',
                          onTap: () => setState(() => selectedFilter = 'assigned'),
                        ),
                        SizedBox(width: 8),
                        _FilterChip(
                          label: 'Completed',
                          isSelected: selectedFilter == 'completed',
                          onTap: () => setState(() => selectedFilter = 'completed'),
                        ),
                      ],
                    ),
                  ),
                ),

                SizedBox(height: 16),

                // Cargo List
                filtered.isEmpty
                    ? Padding(
                        padding: EdgeInsets.all(32),
                        child: Column(
                          children: [
                            Icon(Icons.inbox, size: 64, color: Colors.grey[400]),
                            SizedBox(height: 16),
                            Text(
                              'No cargo requests found',
                              style: TextStyle(
                                fontSize: 16,
                                color: Colors.grey[600],
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      )
                    : Padding(
                        padding: EdgeInsets.symmetric(horizontal: 16),
                        child: Column(
                          children: filtered.map((cargo) {
                            return Padding(
                              padding: EdgeInsets.only(bottom: 12),
                              child: _CargoCard(
                                cargo: cargo,
                                onTap: () => context.go('/freight/${cargo.id}'),
                              ),
                            );
                          }).toList(),
                        ),
                      ),

                SizedBox(height: 24),
              ],
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, size: 48, color: Colors.red),
              const SizedBox(height: 16),
              const Text('Error loading cargo', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(error.toString()),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => ref.refresh(cargoListProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Filter chip widget for status filtering
class _FilterChip extends StatelessWidget {
  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  const _FilterChip({
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(20),
      child: Container(
        padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? Colors.blue : Colors.grey[200],
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? Colors.blue : Colors.transparent,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontWeight: FontWeight.w600,
            color: isSelected ? Colors.white : Colors.grey[800],
          ),
        ),
      ),
    );
  }
}

/// Cargo card widget bound to the real CargoRequest model
class _CargoCard extends StatelessWidget {
  final CargoRequest cargo;
  final VoidCallback onTap;

  const _CargoCard({required this.cargo, required this.onTap});

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

  bool get _isIntracity => cargo.serviceType == 'intracity';

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.grey[200]!),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha:0.05),
              blurRadius: 4,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: _isIntracity
                      ? Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${cargo.city ?? ''}: ${cargo.pickupArea ?? ''} → ${cargo.dropoffArea ?? ''}',
                              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.purple.withValues(alpha:0.1),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: const Text('Intra-city Moving',
                                  style: TextStyle(fontSize: 10, color: Colors.purple, fontWeight: FontWeight.w600)),
                            ),
                          ],
                        )
                      : Text(
                          '${cargo.pickupLocation} → ${cargo.destination}',
                          style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
                        ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: _statusColor.withValues(alpha:0.1),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    cargo.status.toUpperCase(),
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: _statusColor),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Icon(_isIntracity ? Icons.move_to_inbox : Icons.inventory_2,
                    size: 16, color: Colors.grey),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    _isIntracity
                        ? (cargo.itemsDescription?.isNotEmpty == true ? cargo.itemsDescription! : 'Intra-city move')
                        : '${cargo.materialType} • ${cargo.weight.toStringAsFixed(1)} tons',
                    style: const TextStyle(fontSize: 13),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Budget', style: TextStyle(fontSize: 11, color: Colors.grey)),
                    Text(
                      cargo.budget != null
                          ? 'ETB ${cargo.budget!.toStringAsFixed(0)}'
                          : 'Negotiable',
                      style: const TextStyle(
                          fontSize: 15, fontWeight: FontWeight.bold, color: Colors.green),
                    ),
                  ],
                ),
                if (!_isIntracity)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.purple.withValues(alpha:0.1),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      cargo.urgencyLevel.toUpperCase(),
                      style: const TextStyle(
                          fontSize: 11, color: Colors.purple, fontWeight: FontWeight.w600),
                    ),
                  )
                else if (cargo.preferredDate != null)
                  Text(
                    '📅 ${cargo.preferredDate!.day}/${cargo.preferredDate!.month}/${cargo.preferredDate!.year}',
                    style: const TextStyle(fontSize: 12, color: Colors.grey),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
