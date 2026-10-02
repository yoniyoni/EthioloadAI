import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/api/api_client.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

/// Step 2 of password reset: the identifier from ForgotPasswordScreen
/// (passed via `extra`, not the URL, so it never ends up in navigation
/// history/logs), the 6-digit code just sent to it, and a new password.
class ResetPasswordScreen extends ConsumerStatefulWidget {
  final String? identifier;
  const ResetPasswordScreen({super.key, this.identifier});

  @override
  ConsumerState<ResetPasswordScreen> createState() => _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  late final TextEditingController _identifierController;
  final _codeController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();
  bool _obscure = true;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _identifierController = TextEditingController(text: widget.identifier ?? '');
  }

  @override
  void dispose() {
    _identifierController.dispose();
    _codeController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
    super.dispose();
  }

  static InputDecoration _field({
    required String label,
    required String hint,
    required IconData prefix,
    Widget? suffix,
  }) =>
      InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: Icon(prefix, color: kTextMuted, size: 20),
        suffixIcon: suffix,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: kBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: kBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: kGreen, width: 1.5),
        ),
        labelStyle: GoogleFonts.inter(fontSize: 14, color: kTextMuted),
        hintStyle: GoogleFonts.inter(fontSize: 14, color: kTextMuted),
      );

  Future<void> _submit() async {
    final identifier = _identifierController.text.trim();
    final code = _codeController.text.trim();
    final password = _passwordController.text;

    if (identifier.isEmpty || code.isEmpty || password.isEmpty) {
      setState(() => _error = 'Fill in every field.');
      return;
    }
    if (password.length < 6) {
      setState(() => _error = 'Password must be at least 6 characters.');
      return;
    }
    if (password != _confirmController.text) {
      setState(() => _error = 'Passwords do not match.');
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(authRepositoryProvider).resetPassword(
            identifier: identifier,
            code: code,
            newPassword: password,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Password reset. Please log in.'),
          backgroundColor: kGreen,
        ));
        context.go('/login');
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (e) {
      if (mounted) setState(() => _error = 'Something went wrong. Please try again.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: kBackground,
      appBar: const EthioAppBar(title: 'Reset Password'),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 24),
            Text(
              'Enter your code',
              style: GoogleFonts.inter(
                  fontSize: 24, fontWeight: FontWeight.bold, color: kTextPrimary),
            ),
            const SizedBox(height: 8),
            Text(
              'If an account exists for that email or phone, a 6-digit code was just sent. It expires in 10 minutes.',
              style: GoogleFonts.inter(fontSize: 14, color: kTextMuted),
            ),
            const SizedBox(height: 32),
            TextField(
              controller: _identifierController,
              decoration: _field(
                label: 'Email or Phone',
                hint: 'e.g. 0912 345 678',
                prefix: Icons.person_outlined,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _codeController,
              keyboardType: TextInputType.number,
              maxLength: 6,
              decoration: _field(
                label: '6-Digit Code',
                hint: 'e.g. 482913',
                prefix: Icons.pin_outlined,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _passwordController,
              obscureText: _obscure,
              decoration: _field(
                label: 'New Password',
                hint: 'Minimum 6 characters',
                prefix: Icons.lock_outlined,
                suffix: IconButton(
                  icon: Icon(
                    _obscure ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                    color: kTextMuted,
                    size: 20,
                  ),
                  onPressed: () => setState(() => _obscure = !_obscure),
                ),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _confirmController,
              obscureText: _obscure,
              decoration: _field(
                label: 'Confirm New Password',
                hint: 'Re-enter your new password',
                prefix: Icons.lock_outlined,
              ),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 24),
            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: kDanger.withValues(alpha: 0.06),
                  border: Border.all(color: kDanger.withValues(alpha: 0.3)),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline_rounded, color: kDanger, size: 16),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(_error!,
                          style: GoogleFonts.inter(color: kDanger, fontSize: 13)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            ElevatedButton(
              onPressed: _loading ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: kGreen,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
              child: _loading
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.white)))
                  : Text('Reset Password',
                      style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 20),
            Center(
              child: GestureDetector(
                onTap: () => context.go('/forgot-password'),
                child: Text(
                  "Didn't get a code? Request again",
                  style: GoogleFonts.inter(
                      color: kGreen, fontWeight: FontWeight.bold, fontSize: 14),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
