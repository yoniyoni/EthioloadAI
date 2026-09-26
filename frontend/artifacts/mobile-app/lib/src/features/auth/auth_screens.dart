import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/providers/data_providers.dart';
import '../shared/widgets/shared_widgets.dart';

String _roleDestination(String role) {
  switch (role.toLowerCase()) {
    case 'driver':
      return '/driver';
    case 'fleet_owner':
      return '/fleet/companies';
    case 'admin':
      return '/landing';
    default:
      return '/shipper';
  }
}

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
    await authNotifier.checkAuthStatus();
    if (!mounted) return;
    final authState = ref.read(authNotifierProvider);
    if (authState.isAuthenticated) {
      context.go(_roleDestination(authState.user?.role ?? ''));
    } else {
      context.go('/landing');
    }
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
  String? validationError;

  @override
  void initState() {
    super.initState();
    emailController = TextEditingController();
    passwordController = TextEditingController();
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
        context.go(_roleDestination(next.user?.role ?? ''));
      }
    });

    final authState = ref.watch(authNotifierProvider);
    final error = authState.error ?? validationError;

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(title: 'auth.login'.tr()),
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
            if (error != null) ...[
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
                      child: Text(error,
                          style: GoogleFonts.inter(
                              color: kDanger, fontSize: 13)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            ElevatedButton(
              onPressed: authState.isLoading
                  ? null
                  : () async {
                      final identifier = emailController.text.trim();
                      final password = passwordController.text;
                      if (identifier.isEmpty || password.isEmpty) {
                        setState(() => validationError =
                            'Enter your email or phone and password.');
                        return;
                      }
                      setState(() => validationError = null);
                      await ref.read(authNotifierProvider.notifier).login(
                            identifier: identifier,
                            password: password,
                          );
                    },
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

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  late TextEditingController nameController;
  late TextEditingController emailController;
  late TextEditingController phoneController;
  late TextEditingController passwordController;
  String selectedRole = 'shipper';
  bool obscurePassword = true;
  String? validationError;

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
        context.go(_roleDestination(next.user?.role ?? ''));
      }
    });

    final authState = ref.watch(authNotifierProvider);
    final error = authState.error ?? validationError;

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
            if (selectedRole == 'fleet_owner') const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text('First create your owner account. Next, register your company and upload its business license, registration certificate and TIN certificate for admin approval.'),
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
            if (error != null) ...[
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
                      child: Text(error,
                          style: GoogleFonts.inter(
                              color: kDanger, fontSize: 13)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            ElevatedButton(
              onPressed: authState.isLoading
                  ? null
                  : () async {
                      final fullName = nameController.text.trim();
                      final email = emailController.text.trim();
                      final phone = phoneController.text.trim();
                      final password = passwordController.text;
                      final emailIsValid = email.isEmpty ||
                          RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$')
                              .hasMatch(email);
                      final phoneIsValid = RegExp(r'^\+?[0-9]{10,15}$')
                          .hasMatch(phone);
                      String? error;
                      if (fullName.isEmpty) {
                        error = 'Enter your full name.';
                      } else if (!emailIsValid) {
                        error = 'Enter a valid email address.';
                      } else if (!phoneIsValid) {
                        error = 'Enter a valid phone number.';
                      } else if (password.length < 6) {
                        error = 'Password must be at least 6 characters.';
                      }
                      if (error != null) {
                        setState(() => validationError = error);
                        return;
                      }
                      setState(() => validationError = null);
                      await ref.read(authNotifierProvider.notifier).register(
                            fullName: fullName,
                            email: email,
                            phone: phone,
                            password: password,
                            role: selectedRole,
                          );
                    },
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
