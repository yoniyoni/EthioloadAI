import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

/// Where a driver's earnings actually go — the bank account
/// PaymentService::releaseToDriver() pays out to via Chapa Transfers once a
/// shipper confirms a delivery arrived. Reached from Profile → "Payout
/// Bank Account".
class DriverPayoutDetailsScreen extends ConsumerStatefulWidget {
  const DriverPayoutDetailsScreen({super.key});

  @override
  ConsumerState<DriverPayoutDetailsScreen> createState() =>
      _DriverPayoutDetailsScreenState();
}

class _DriverPayoutDetailsScreenState
    extends ConsumerState<DriverPayoutDetailsScreen> {
  late final TextEditingController _accountNumberCtrl;
  late final TextEditingController _accountNameCtrl;
  List<Bank>? _banks;
  String? _bankListError;
  Bank? _selectedBank;
  bool _loadingBanks = true;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final user = ref.read(authNotifierProvider).user;
    _accountNumberCtrl = TextEditingController(text: user?.payoutAccountNumber ?? '');
    _accountNameCtrl = TextEditingController(text: user?.payoutAccountName ?? user?.fullName ?? '');
    _loadBanks(preselectCode: user?.payoutBankCode);
  }

  @override
  void dispose() {
    _accountNumberCtrl.dispose();
    _accountNameCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadBanks({String? preselectCode}) async {
    setState(() {
      _loadingBanks = true;
      _bankListError = null;
    });
    try {
      final banks = await ref.read(authRepositoryProvider).listBanks();
      if (!mounted) return;
      setState(() {
        _banks = banks;
        _loadingBanks = false;
        if (preselectCode != null) {
          try {
            _selectedBank = banks.firstWhere((b) => b.code == preselectCode);
          } catch (_) {
            // Saved bank code no longer in the list — leave unselected
            // rather than guess.
          }
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loadingBanks = false;
        _bankListError = e.toString();
      });
    }
  }

  Future<void> _save() async {
    if (_selectedBank == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Select your bank first.'),
        backgroundColor: Colors.red,
      ));
      return;
    }
    if (_accountNumberCtrl.text.trim().isEmpty || _accountNameCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Enter your account number and account name.'),
        backgroundColor: Colors.red,
      ));
      return;
    }

    setState(() => _saving = true);
    try {
      final updated = await ref.read(authRepositoryProvider).updatePayoutDetails(
            bankCode: _selectedBank!.code,
            accountNumber: _accountNumberCtrl.text.trim(),
            accountName: _accountNameCtrl.text.trim(),
          );
      if (!mounted) return;
      ref.read(authNotifierProvider.notifier).setUser(updated);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Payout bank account saved.'),
        backgroundColor: Colors.green,
      ));
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Could not save: $e'),
        backgroundColor: Colors.red,
      ));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: const EthioAppBar(title: 'Payout Bank Account'),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'This is where your earnings are sent once a shipper confirms '
              'a delivery has arrived — payment is released automatically, '
              'no need to arrange it yourself.',
              style: TextStyle(fontSize: 13, color: Colors.grey),
            ),
            const SizedBox(height: 20),
            const Text('Bank', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            _buildBankPicker(),
            const SizedBox(height: 16),
            const Text('Account Number', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(
              controller: _accountNumberCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: 'e.g. 1000123456789',
              ),
            ),
            const SizedBox(height: 16),
            const Text('Account Name', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            TextField(
              controller: _accountNameCtrl,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: 'Name on the bank account',
              ),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        width: 20, height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Text('Save'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBankPicker() {
    if (_loadingBanks) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: LinearProgressIndicator(),
      );
    }
    if (_bankListError != null) {
      return Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: Colors.red.shade50,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline, color: Colors.red, size: 18),
            const SizedBox(width: 8),
            const Expanded(
              child: Text(
                "Couldn't load the bank list. Try again shortly.",
                style: TextStyle(fontSize: 12, color: Colors.red),
              ),
            ),
            TextButton(onPressed: () => _loadBanks(), child: const Text('Retry')),
          ],
        ),
      );
    }

    final banks = _banks ?? [];
    return InputDecorator(
      decoration: const InputDecoration(border: OutlineInputBorder(), isDense: true),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<Bank>(
          isExpanded: true,
          hint: const Text('Select your bank'),
          value: _selectedBank,
          items: banks
              .map((b) => DropdownMenuItem(value: b, child: Text(b.name)))
              .toList(),
          onChanged: (b) => setState(() => _selectedBank = b),
        ),
      ),
    );
  }
}
