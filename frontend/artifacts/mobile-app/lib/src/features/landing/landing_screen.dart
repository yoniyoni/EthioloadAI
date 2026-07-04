import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:easy_localization/easy_localization.dart';
import '../shared/widgets/shared_widgets.dart';

class LandingScreen extends StatefulWidget {
  const LandingScreen({super.key});

  @override
  State<LandingScreen> createState() => _LandingScreenState();
}

class _LandingScreenState extends State<LandingScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 10),
    )..repeat();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: kGreen,
      body: Column(
        children: [
          // ── Hero — top 55% ─────────────────────────────────────────────
          Expanded(
            flex: 55,
            child: Stack(
              alignment: Alignment.center,
              children: [
                // Drifting particle background
                Positioned.fill(
                  child: AnimatedBuilder(
                    animation: _ctrl,
                    builder: (_, __) =>
                        CustomPaint(painter: _DotsPainter(_ctrl.value)),
                  ),
                ),

                // Language toggle — top right
                Positioned(
                  top: MediaQuery.of(context).padding.top + 12,
                  right: 16,
                  child: _LangToggle(),
                ),

                // Hero content
                SafeArea(
                  bottom: false,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // Truck with amber glow
                      Container(
                        width: 96,
                        height: 96,
                        decoration: BoxDecoration(
                          color: kGreenLight,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: kAmber.withValues(alpha: 0.38),
                              blurRadius: 40,
                              spreadRadius: 10,
                            ),
                          ],
                        ),
                        child: const Icon(
                          Icons.local_shipping,
                          size: 46,
                          color: kAmber,
                        ),
                      ),
                      const SizedBox(height: 24),

                      Text(
                        'app_name'.tr(),
                        style: GoogleFonts.inter(
                          fontSize: 38,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(height: 8),

                      // Amharic tagline (always shown in Amharic — it's the brand slogan)
                      Text(
                        'ምርጥ ጭነት — ምርጥ ዋጋ',
                        style: GoogleFonts.inter(
                          fontSize: 16,
                          fontWeight: FontWeight.w500,
                          color: kAmber,
                        ),
                      ),
                      const SizedBox(height: 6),

                      Text(
                        'landing.tagline_sub'.tr(),
                        style: GoogleFonts.inter(
                          fontSize: 13,
                          color: Colors.white.withValues(alpha: 0.6),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),

          // ── White card — bottom 45% ────────────────────────────────────
          Expanded(
            flex: 45,
            child: Container(
              width: double.infinity,
              decoration: const BoxDecoration(
                color: kSurface,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(32),
                  topRight: Radius.circular(32),
                ),
              ),
              child: SafeArea(
                top: false,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(24, 28, 24, 12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Feature pills
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          _FeaturePill(
                            icon: Icons.psychology_outlined,
                            label: 'landing.feat_matching'.tr(),
                          ),
                          _FeaturePill(
                            icon: Icons.location_on_outlined,
                            label: 'landing.feat_tracking'.tr(),
                          ),
                          _FeaturePill(
                            icon: Icons.verified_outlined,
                            label: 'landing.feat_price'.tr(),
                          ),
                        ],
                      ),

                      const Spacer(),

                      // Primary CTA — amber
                      SizedBox(
                        height: 52,
                        child: ElevatedButton(
                          onPressed: () => context.go('/register'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: kAmber,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                            elevation: 0,
                          ),
                          child: Text(
                            'landing.get_started'.tr(),
                            style: GoogleFonts.inter(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 10),

                      // Login link
                      TextButton(
                        onPressed: () => context.go('/login'),
                        style: TextButton.styleFrom(
                          foregroundColor: kGreen,
                        ),
                        child: Text(
                          'landing.have_account'.tr(),
                          style: GoogleFonts.inter(
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      const SizedBox(height: 4),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Language toggle ───────────────────────────────────────────────────────

class _LangToggle extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final isEn = context.locale.languageCode == 'en';
    return Row(mainAxisSize: MainAxisSize.min, children: [
      _LangBtn(
        label: 'EN',
        active: isEn,
        onTap: () => context.setLocale(const Locale('en', 'US')),
      ),
      const SizedBox(width: 6),
      _LangBtn(
        label: 'አማ',
        active: !isEn,
        onTap: () => context.setLocale(const Locale('am', 'ET')),
      ),
    ]);
  }
}

class _LangBtn extends StatelessWidget {
  final String label;
  final bool active;
  final VoidCallback onTap;
  const _LangBtn({required this.label, required this.active, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: active ? kAmber : Colors.white.withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: active ? kAmber : Colors.white.withValues(alpha: 0.4),
            width: 1,
          ),
        ),
        child: Text(
          label,
          style: GoogleFonts.inter(
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: active ? const Color(0xFF111827) : Colors.white,
          ),
        ),
      ),
    );
  }
}

// ── Feature pill chip ─────────────────────────────────────────────────────
class _FeaturePill extends StatelessWidget {
  final IconData icon;
  final String label;
  const _FeaturePill({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: kGreenTint,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: kBorder),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 13, color: kGreen),
          const SizedBox(width: 5),
          Text(
            label,
            style: GoogleFonts.inter(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: kGreen,
            ),
          ),
        ],
      ),
    );
  }
}

// ── Drifting dots CustomPainter ───────────────────────────────────────────
class _DotsPainter extends CustomPainter {
  final double t;

  static const _px = [
    0.07, 0.24, 0.41, 0.63, 0.82, 0.15, 0.55, 0.78,
    0.32, 0.90, 0.48, 0.70, 0.13, 0.35, 0.66, 0.85,
    0.22, 0.50, 0.75, 0.95,
  ];
  static const _py = [
    0.08, 0.20, 0.35, 0.12, 0.28, 0.50, 0.42, 0.60,
    0.75, 0.18, 0.55, 0.38, 0.80, 0.65, 0.22, 0.45,
    0.90, 0.70, 0.15, 0.55,
  ];
  static const _sz = [
    1.5, 2.0, 1.2, 1.8, 2.5, 1.0, 2.2, 1.5,
    2.0, 1.2, 1.8, 2.5, 1.0, 2.2, 1.5, 2.0,
    1.2, 1.8, 2.5, 1.0,
  ];
  static const _sp = [
    0.004, 0.006, 0.005, 0.007, 0.003, 0.008, 0.004, 0.006,
    0.005, 0.007, 0.003, 0.008, 0.004, 0.006, 0.005, 0.007,
    0.003, 0.008, 0.004, 0.006,
  ];

  const _DotsPainter(this.t);

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.14)
      ..style = PaintingStyle.fill;

    for (var i = 0; i < _px.length; i++) {
      final drift = (_sp[i] * size.height * t * 10) % size.height;
      final y = (_py[i] * size.height + drift) % size.height;
      canvas.drawCircle(Offset(_px[i] * size.width, y), _sz[i], paint);
    }
  }

  @override
  bool shouldRepaint(_DotsPainter old) => old.t != t;
}
