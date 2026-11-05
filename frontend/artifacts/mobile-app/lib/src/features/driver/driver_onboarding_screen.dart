import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/providers/data_providers.dart';
import 'driver_navigation.dart';

class DriverOnboardingScreen extends ConsumerWidget {
  const DriverOnboardingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isVerified = ref.watch(
      authNotifierProvider.select(
        (state) => state.user?.verificationStatus ?? false,
      ),
    );
    final documents = ref.watch(driverDocumentsProvider);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FBF8),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F3D1A),
        title: const Text(
          'Driver onboarding / የአሽከርካሪ ምዝገባ',
          style: TextStyle(color: Colors.white, fontSize: 17),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Icon(
            Icons.local_shipping_outlined,
            color: Color(0xFF0F3D1A),
            size: 52,
          ),
          const SizedBox(height: 12),
          const Text(
            'Welcome to EthioLoad AI',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          const Text(
            'Complete your driver documents to continue. Your operational '
            'dashboard will be available after verification.',
            style: TextStyle(color: Color(0xFF6B7280), height: 1.4),
          ),
          const SizedBox(height: 20),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: documents.when(
                loading: () => const Row(
                  children: [
                    SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    SizedBox(width: 12),
                    Text('Loading document status...'),
                  ],
                ),
                error: (error, _) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Document status is unavailable.',
                      style: TextStyle(fontWeight: FontWeight.w600),
                    ),
                    TextButton(
                      onPressed: () => ref.invalidate(driverDocumentsProvider),
                      child: const Text('Retry'),
                    ),
                  ],
                ),
                data: (items) {
                  final uploadedTypes =
                      items.map((document) => document.documentType).toSet();
                  final hasRejected =
                      items.any((document) => document.isRejected);
                  final hasPending =
                      items.any((document) => document.isPending);
                  final status = isVerified
                      ? 'Verified'
                      : hasRejected
                          ? 'Changes requested'
                          : hasPending
                              ? 'Under review'
                              : 'Documents needed';
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isVerified
                            ? 'Verification status'
                            : 'Onboarding status',
                        style: const TextStyle(
                          color: Color(0xFF6B7280),
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        status,
                        style: const TextStyle(
                          color: Color(0xFF0F3D1A),
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (!isVerified) ...[
                        const SizedBox(height: 6),
                        Text(
                            '${uploadedTypes.length} of 5 document types submitted'),
                      ],
                    ],
                  );
                },
              ),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: () => context.push(driverDocumentsPath),
            icon: const Icon(Icons.upload_file_outlined),
            label: const Text('Review or submit documents'),
          ),
          TextButton.icon(
            onPressed: () => ref.invalidate(driverDocumentsProvider),
            icon: const Icon(Icons.refresh),
            label: const Text('Refresh status'),
          ),
        ],
      ),
    );
  }
}
