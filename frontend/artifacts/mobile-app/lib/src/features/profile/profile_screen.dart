import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// ProfileScreen — user profile and settings.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.read(authNotifierProvider).user;

    return Scaffold(
      appBar: AppBar(title: const Text('Profile'), elevation: 0),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Avatar header ────────────────────────────────────────
            Container(
              width: double.infinity,
              color: Colors.blue[50],
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Column(
                children: [
                  Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      color: Colors.blue[300],
                      shape: BoxShape.circle,
                    ),
                    child:
                        const Icon(Icons.person, size: 40, color: Colors.white),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    user?.fullName ?? 'User',
                    style: const TextStyle(
                        fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.blue[100],
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      _capitalize(user?.role ?? 'user'),
                      style: TextStyle(
                          fontSize: 12,
                          color: Colors.blue[900],
                          fontWeight: FontWeight.w600),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    user?.verificationStatus == true
                        ? '✓ Verified account'
                        : 'Unverified account',
                    style: TextStyle(
                        fontSize: 12,
                        color: user?.verificationStatus == true
                            ? Colors.green
                            : Colors.orange),
                  ),
                ],
              ),
            ),

            // ── Contact info ─────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle('Contact Information'),
                  const SizedBox(height: 12),
                  _ProfileField(
                      label: 'Email',
                      value: user?.email ?? '—',
                      icon: Icons.email_outlined),
                  _ProfileField(
                      label: 'Phone',
                      value: user?.phone ?? '—',
                      icon: Icons.phone_outlined),
                  if (user?.location != null)
                    _ProfileField(
                        label: 'Location',
                        value: user!.location!,
                        icon: Icons.location_on_outlined),
                ],
              ),
            ),

            // ── Preferences ──────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _SectionTitle('Preferences'),
                  const SizedBox(height: 12),
                  _SettingTile(
                    icon: Icons.language,
                    label: 'Language',
                    value: 'English',
                    onTap: () {},
                  ),
                  _SettingTile(
                    icon: Icons.notifications_outlined,
                    label: 'Notifications',
                    value: 'Enabled',
                    onTap: () {},
                  ),
                  _SettingTile(
                    icon: Icons.dark_mode_outlined,
                    label: 'Dark Mode',
                    value: 'Off',
                    onTap: () {},
                  ),
                ],
              ),
            ),

            // ── Actions ──────────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
              child: Column(
                children: [
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.edit_outlined),
                      label: const Text('Edit Profile'),
                      onPressed: () => _showEditDialog(context, user),
                    ),
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.security),
                      label: const Text('Change Password'),
                      onPressed: () => _showChangePasswordDialog(context, ref),
                    ),
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: const Icon(Icons.help_outline),
                      label: const Text('Help & Support'),
                      onPressed: () {},
                    ),
                  ),
                ],
              ),
            ),

            // ── Logout ───────────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red,
                      padding:
                          const EdgeInsets.symmetric(vertical: 14)),
                  onPressed: () => _showLogoutDialog(context, ref),
                  child: const Text('Logout',
                      style: TextStyle(fontSize: 15)),
                ),
              ),
            ),

            const Center(
              child: Padding(
                padding: EdgeInsets.only(bottom: 24, top: 8),
                child: Text('EthioLoad AI  v1.0.0',
                    style: TextStyle(fontSize: 12, color: Colors.grey)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _capitalize(String s) =>
      s.isEmpty ? s : s[0].toUpperCase() + s.substring(1);

  void _showEditDialog(BuildContext context, User? user) {
    final nameCtrl =
        TextEditingController(text: user?.fullName ?? '');
    final phoneCtrl =
        TextEditingController(text: user?.phone ?? '');

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Profile'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameCtrl,
              decoration: const InputDecoration(
                  labelText: 'Full Name', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: phoneCtrl,
              decoration: const InputDecoration(
                  labelText: 'Phone', border: OutlineInputBorder()),
              keyboardType: TextInputType.phone,
            ),
          ],
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Profile updated')),
              );
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  void _showChangePasswordDialog(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => _ChangePasswordDialog(
        authRepo: ref.read(authRepositoryProvider),
        parentContext: context,
      ),
    );
  }

  void _showLogoutDialog(BuildContext context, WidgetRef ref) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Logout'),
        content: const Text('Are you sure you want to logout?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel')),
          ElevatedButton(
            style:
                ElevatedButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () {
              Navigator.pop(ctx);
              ref.read(authNotifierProvider.notifier).logout();
              context.go('/login');
            },
            child: const Text('Logout'),
          ),
        ],
      ),
    );
  }
}

// ── Helper widgets ────────────────────────────────────────────────────────

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) => Text(
        text,
        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
      );
}

class _ProfileField extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;

  const _ProfileField(
      {required this.label, required this.value, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          border: Border.all(color: Colors.grey[300]!),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(icon, color: Colors.blue, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label,
                      style: TextStyle(
                          fontSize: 11, color: Colors.grey[600])),
                  const SizedBox(height: 3),
                  Text(value,
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.w500)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SettingTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final VoidCallback onTap;

  const _SettingTile(
      {required this.icon,
      required this.label,
      required this.value,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(icon, color: Colors.blue),
      title: Text(label, style: const TextStyle(fontSize: 13)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(value,
              style:
                  TextStyle(fontSize: 12, color: Colors.grey[600])),
          const SizedBox(width: 6),
          const Icon(Icons.chevron_right, color: Colors.grey),
        ],
      ),
      onTap: onTap,
    );
  }
}

// ── Change Password Dialog ────────────────────────────────────────────────

class _ChangePasswordDialog extends StatefulWidget {
  final AuthRepository authRepo;
  final BuildContext parentContext;

  const _ChangePasswordDialog({
    required this.authRepo,
    required this.parentContext,
  });

  @override
  State<_ChangePasswordDialog> createState() => _ChangePasswordDialogState();
}

class _ChangePasswordDialogState extends State<_ChangePasswordDialog> {
  final _currentCtrl = TextEditingController();
  final _newCtrl     = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _obscureCurrent = true;
  bool _obscureNew     = true;
  bool _obscureConfirm = true;
  bool _loading        = false;

  @override
  void dispose() {
    _currentCtrl.dispose();
    _newCtrl.dispose();
    _confirmCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final current = _currentCtrl.text.trim();
    final newPass = _newCtrl.text.trim();
    final confirm = _confirmCtrl.text.trim();

    if (current.isEmpty || newPass.isEmpty || confirm.isEmpty) {
      _snack('Please fill in all fields.', isError: true);
      return;
    }
    if (newPass.length < 6) {
      _snack('New password must be at least 6 characters.', isError: true);
      return;
    }
    if (newPass != confirm) {
      _snack('Passwords do not match.', isError: true);
      return;
    }

    setState(() => _loading = true);
    try {
      await widget.authRepo.changePassword(
        currentPassword: current,
        newPassword: newPass,
      );
      if (mounted) Navigator.pop(context);
      _snack('Password changed successfully.', isError: false);
    } catch (e) {
      if (mounted) setState(() => _loading = false);
      final msg = e.toString().replaceFirst('Exception: ', '');
      _snack(msg, isError: true);
    }
  }

  void _snack(String msg, {required bool isError}) {
    ScaffoldMessenger.of(widget.parentContext).showSnackBar(SnackBar(
      content: Text(msg),
      backgroundColor: isError ? Colors.red : Colors.green,
    ));
  }

  InputDecoration _deco(String label, {required bool obscure, required VoidCallback toggle}) =>
      InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        suffixIcon: IconButton(
          icon: Icon(obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined, size: 20),
          onPressed: toggle,
        ),
      );

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Change Password', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _currentCtrl,
            obscureText: _obscureCurrent,
            decoration: _deco(
              'Current Password',
              obscure: _obscureCurrent,
              toggle: () => setState(() => _obscureCurrent = !_obscureCurrent),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _newCtrl,
            obscureText: _obscureNew,
            decoration: _deco(
              'New Password',
              obscure: _obscureNew,
              toggle: () => setState(() => _obscureNew = !_obscureNew),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _confirmCtrl,
            obscureText: _obscureConfirm,
            decoration: _deco(
              'Confirm New Password',
              obscure: _obscureConfirm,
              toggle: () => setState(() => _obscureConfirm = !_obscureConfirm),
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: _loading ? null : () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _loading ? null : _submit,
          child: _loading
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Text('Save'),
        ),
      ],
    );
  }
}
