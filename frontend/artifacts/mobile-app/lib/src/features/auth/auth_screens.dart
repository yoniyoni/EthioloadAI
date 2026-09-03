import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/providers/data_providers.dart';
import '../shared/widgets/shared_widgets.dart';
import '../shared/widgets/server_url_dialog.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _navigateToNext();
  }

  Future<void> _navigateToNext() async {
    await Future.delayed(const Duration(seconds: 3));
    if (!mounted) return;
    final authNotifier = ref.read(authNotifierProvider.notifier);
    // Never gate this screen's navigation on checkAuthStatus() completing —
    // a Dart-level .timeout() only protects against a hung *network* call;
    // it can't preempt a stuck native platform-channel call (e.g. known
    // flutter_secure_storage keystore-read hangs on some Android versions),
    // since that blocks below Dart's event loop entirely. Always leave the
    // splash screen after the brand delay; restore the session in the
    // background. The router listens for auth-state changes (see
    // _RouterNotifier) and will seamlessly upgrade from /landing to the
    // real home screen the moment — if ever — the check resolves.
    unawaited(authNotifier.checkAuthStatus());
    if (!mounted) return;
    context.go('/landing');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: kGreen,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(
                color: kAmber,
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(
                    color: kAmber.withValues(alpha: 0.4),
                    blurRadius: 24,
                    spreadRadius: 4,
                  ),
                ],
              ),
              child: const Icon(
                Icons.local_shipping_rounded,
                size: 52,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 32),
            Text(
              'app_name'.tr(),
              style: GoogleFonts.inter(
                fontSize: 28,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'tagline'.tr(),
              style: GoogleFonts.inter(
                  fontSize: 16,
                  color: Colors.white.withValues(alpha: 0.7)),
            ),
            const SizedBox(height: 48),
            SizedBox(
              width: 32,
              height: 32,
              child: CircularProgressIndicator(
                valueColor: const AlwaysStoppedAnimation<Color>(kAmber),
                strokeWidth: 2.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  late TextEditingController emailController;
  late TextEditingController passwordController;
  bool obscurePassword = true;
  String? _validationError;

  @override
  void initState() {
    super.initState();
    emailController = TextEditingController();
    passwordController = TextEditingController();
  }

  String? _validate() {
    if (emailController.text.trim().isEmpty) {
      return '${'auth.identifier'.tr()} is required.';
    }
    if (passwordController.text.isEmpty) {
      return '${'auth.password'.tr()} is required.';
    }
    return null;
  }

  void _submit() {
    final error = _validate();
    setState(() => _validationError = error);
    if (error != null) return;
    ref.read(authNotifierProvider.notifier).login(
          identifier: emailController.text.trim(),
          password: passwordController.text,
        );
  }

  @override
  void dispose() {
    emailController.dispose();
    passwordController.dispose();
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

  @override
  Widget build(BuildContext context) {
    ref.listen<AuthState>(authNotifierProvider, (previous, next) {
      if (!(previous?.isAuthenticated ?? false) && next.isAuthenticated) {
        final role = next.user?.role ?? '';
        context.go(role == 'driver' ? '/driver' : role == 'fleet_owner' ? '/fleet' : '/shipper');
      }
    });

    final authState = ref.watch(authNotifierProvider);

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(
        title: 'auth.login'.tr(),
        actions: [
          // Reachable without logging in on purpose — this is the only way
          // to fix an unreachable local/test server address, since login
          // itself needs a working server to succeed in the first place.
          IconButton(
            icon: const Icon(Icons.settings_outlined, color: Colors.white),
            tooltip: 'Server URL',
            onPressed: () => showDialog(
              context: context,
              builder: (_) => const ServerUrlDialog(),
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 24),
            Text(
              'auth.login_title'.tr(),
              style: GoogleFonts.inter(
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                  color: kTextPrimary),
            ),
            const SizedBox(height: 8),
            Text(
              'auth.login_subtitle'.tr(),
              style: GoogleFonts.inter(fontSize: 14, color: kTextMuted),
            ),
            const SizedBox(height: 32),
            TextField(
              controller: emailController,
              decoration: _field(
                label: 'auth.identifier'.tr(),
                hint: 'auth.identifier_hint'.tr(),
                prefix: Icons.person_outlined,
              ),
              keyboardType: TextInputType.text,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: passwordController,
              obscureText: obscurePassword,
              decoration: _field(
                label: 'auth.password'.tr(),
                hint: 'auth.password_hint'.tr(),
                prefix: Icons.lock_outlined,
                suffix: IconButton(
                  icon: Icon(
                    obscurePassword
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                    color: kTextMuted,
                    size: 20,
                  ),
                  onPressed: () =>
                      setState(() => obscurePassword = !obscurePassword),
                ),
              ),
            ),
            const SizedBox(height: 24),
            if ((authState.error ?? _validationError) != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: kDanger.withValues(alpha: 0.06),
                  border: Border.all(color: kDanger.withValues(alpha: 0.3)),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline_rounded,
                        color: kDanger, size: 16),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(authState.error ?? _validationError!,
                          style: GoogleFonts.inter(
                              color: kDanger, fontSize: 13)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            ElevatedButton(
              onPressed: authState.isLoading ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: kGreen,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
              child: authState.isLoading
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor:
                              AlwaysStoppedAnimation<Color>(Colors.white)))
                  : Text('auth.login'.tr(),
                      style: GoogleFonts.inter(
                          fontSize: 16, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 14),
            Center(
              child: GestureDetector(
                onTap: () => context.go('/forgot-password'),
                child: Text(
                  'auth.forgot_password'.tr(),
                  style: GoogleFonts.inter(
                      color: kTextMuted,
                      fontWeight: FontWeight.w600,
                      fontSize: 13),
                ),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('auth.dont_have_account'.tr(),
                    style: GoogleFonts.inter(
                        color: kTextMuted, fontSize: 14)),
                GestureDetector(
                  onTap: () => context.go('/register'),
                  child: Text(
                    'auth.sign_up'.tr(),
                    style: GoogleFonts.inter(
                        color: kGreen,
                        fontWeight: FontWeight.bold,
                        fontSize: 14),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

/// Mirrors backend App\Support\PhoneNumber::normalize() so the register
/// screen gives immediate feedback instead of waiting on a round trip to
/// discover the phone didn't match the server's expected shape.
String _normalizePhone(String raw) {
  final digits = raw.replaceAll(RegExp(r'\D+'), '');
  if (digits.isEmpty) return raw;
  if (digits.startsWith('251') && digits.length == 12) {
    return '0${digits.substring(3)}';
  }
  if (digits.length == 9) {
    return '0$digits';
  }
  return digits;
}

final RegExp _phoneRegex = RegExp(r'^0\d{9}$');
final RegExp _emailRegex = RegExp(r'^\S+@\S+\.\S+$');

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  late TextEditingController nameController;
  late TextEditingController emailController;
  late TextEditingController phoneController;
  late TextEditingController passwordController;
  String selectedRole = 'shipper';
  bool obscurePassword = true;
  String? _validationError;

  @override
  void initState() {
    super.initState();
    nameController = TextEditingController();
    emailController = TextEditingController();
    phoneController = TextEditingController();
    passwordController = TextEditingController();
  }

  @override
  void dispose() {
    nameController.dispose();
    emailController.dispose();
    phoneController.dispose();
    passwordController.dispose();
    super.dispose();
  }

  String? _validate() {
    if (nameController.text.trim().isEmpty) {
      return '${'auth.full_name'.tr()} is required.';
    }
    if (!_emailRegex.hasMatch(emailController.text.trim())) {
      return 'Enter a valid email address.';
    }
    if (!_phoneRegex.hasMatch(_normalizePhone(phoneController.text.trim()))) {
      return 'Enter a valid phone number, e.g. 0911234567.';
    }
    if (passwordController.text.length < 6) {
      return 'Password must be at least 6 characters.';
    }
    return null;
  }

  void _submit() {
    final error = _validate();
    setState(() => _validationError = error);
    if (error != null) return;
    ref.read(authNotifierProvider.notifier).register(
          fullName: nameController.text.trim(),
          email: emailController.text.trim(),
          phone: phoneController.text.trim(),
          password: passwordController.text,
          role: selectedRole,
        );
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

  @override
  Widget build(BuildContext context) {
    ref.listen<AuthState>(authNotifierProvider, (previous, next) {
      if (!(previous?.isAuthenticated ?? false) && next.isAuthenticated) {
        final role = next.user?.role ?? '';
        context.go(role == 'driver' ? '/driver' : role == 'fleet_owner' ? '/fleet' : '/shipper');
      }
    });

    final authState = ref.watch(authNotifierProvider);

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(title: 'auth.create_account'.tr()),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 24),
            Text(
              'auth.register_title'.tr(),
              style: GoogleFonts.inter(
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                  color: kTextPrimary),
            ),
            const SizedBox(height: 8),
            Text(
              'auth.register_subtitle'.tr(),
              style: GoogleFonts.inter(fontSize: 14, color: kTextMuted),
            ),
            const SizedBox(height: 32),
            TextField(
              controller: nameController,
              decoration: _field(
                label: 'auth.full_name'.tr(),
                hint: 'auth.full_name_hint'.tr(),
                prefix: Icons.person_outline_rounded,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: emailController,
              decoration: _field(
                label: 'auth.email'.tr(),
                hint: 'auth.email_hint'.tr(),
                prefix: Icons.email_outlined,
              ),
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: phoneController,
              decoration: _field(
                label: 'auth.phone'.tr(),
                hint: 'auth.phone_hint'.tr(),
                prefix: Icons.phone_outlined,
              ),
              keyboardType: TextInputType.phone,
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              value: selectedRole,
              decoration: InputDecoration(
                labelText: 'auth.account_type'.tr(),
                prefixIcon: const Icon(Icons.business_outlined,
                    color: kTextMuted, size: 20),
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
                labelStyle:
                    GoogleFonts.inter(fontSize: 14, color: kTextMuted),
              ),
              dropdownColor: kSurface,
              items: [
                DropdownMenuItem(
                    value: 'shipper',
                    child: Text('auth.shipper_role'.tr(),
                        style: GoogleFonts.inter(fontSize: 14))),
                DropdownMenuItem(
                    value: 'driver',
                    child: Text('auth.driver_role'.tr(),
                        style: GoogleFonts.inter(fontSize: 14))),
                DropdownMenuItem(
                    value: 'fleet_owner',
                    child: Text('Fleet Owner',
                        style: GoogleFonts.inter(fontSize: 14))),
              ],
              onChanged: (value) =>
                  setState(() => selectedRole = value ?? 'shipper'),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: passwordController,
              obscureText: obscurePassword,
              decoration: _field(
                label: 'auth.password'.tr(),
                hint: 'auth.password_min'.tr(),
                prefix: Icons.lock_outlined,
                suffix: IconButton(
                  icon: Icon(
                    obscurePassword
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                    color: kTextMuted,
                    size: 20,
                  ),
                  onPressed: () =>
                      setState(() => obscurePassword = !obscurePassword),
                ),
              ),
            ),
            const SizedBox(height: 24),
            if ((authState.error ?? _validationError) != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: kDanger.withValues(alpha: 0.06),
                  border: Border.all(color: kDanger.withValues(alpha: 0.3)),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.error_outline_rounded,
                        color: kDanger, size: 16),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(authState.error ?? _validationError!,
                          style: GoogleFonts.inter(
                              color: kDanger, fontSize: 13)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            ElevatedButton(
              onPressed: authState.isLoading ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: kGreen,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
                elevation: 0,
              ),
              child: authState.isLoading
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor:
                              AlwaysStoppedAnimation<Color>(Colors.white)))
                  : Text('auth.create_account'.tr(),
                      style: GoogleFonts.inter(
                          fontSize: 16, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('auth.already_have_account'.tr(),
                    style: GoogleFonts.inter(
                        color: kTextMuted, fontSize: 14)),
                GestureDetector(
                  onTap: () => context.go('/login'),
                  child: Text(
                    'auth.login'.tr(),
                    style: GoogleFonts.inter(
                        color: kGreen,
                        fontWeight: FontWeight.bold,
                        fontSize: 14),
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
