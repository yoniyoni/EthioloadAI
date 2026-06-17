import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

// ── Design tokens ─────────────────────────────────────────────────────────
const kGreen       = Color(0xFF0F3D1A);
const kGreenLight  = Color(0xFF1B5E20);
const kGreenTint   = Color(0xFFE8F5E9);
const kAmber       = Color(0xFFF59E0B);
const kAmberLight  = Color(0xFFFEF3C7);
const kSuccess     = Color(0xFF22C55E);
const kSuccessBg   = Color(0xFFDCFCE7);
const kDanger      = Color(0xFFEF4444);
const kDangerBg    = Color(0xFFFEE2E2);
const kWarningBg   = Color(0xFFFEF3C7);
const kSurface     = Color(0xFFFFFFFF);
const kBackground  = Color(0xFFF8FAF8);
const kBorder      = Color(0xFFE2E8E2);
const kTextPrimary = Color(0xFF0D1F12);
const kTextSecond  = Color(0xFF4B6350);
const kTextMuted   = Color(0xFF8FA893);

// ── Amharic city map ──────────────────────────────────────────────────────
const Map<String, String> kCityAmharic = {
  'addis ababa':  'አዲስ አበባ',
  'addis abeba':  'አዲስ አበባ',
  'addis':        'አዲስ አበባ',
  'gondar':       'ጎንደር',
  'gonder':       'ጎንደር',
  'mekele':       'መቀሌ',
  'mekelle':      'መቀሌ',
  'bahir dar':    'ባህር ዳር',
  'bahar dar':    'ባህር ዳር',
  'hawassa':      'ሐዋሳ',
  'awasa':        'ሐዋሳ',
  'jimma':        'ጅማ',
  'jima':         'ጅማ',
  'dire dawa':    'ድሬ ዳዋ',
  'diredawa':     'ድሬ ዳዋ',
  'humera':       'ሁመራ',
  'metema':       'መተማ',
  'shire':        'ሽሬ',
  'addis zemen':  'አዲስ ዘመን',
  'debre tabor':  'ደብረ ታቦር',
  'debre markos': 'ደብረ ማርቆስ',
};

/// Returns Amharic name for a city string, or empty string if not found.
String cityAmharic(String city) =>
    kCityAmharic[city.toLowerCase().trim()] ?? '';

// ─────────────────────────────────────────────────────────────────────────
// EthioAppBar
// ─────────────────────────────────────────────────────────────────────────
class EthioAppBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final List<Widget>? actions;
  final bool showBack;
  final Widget? leading;
  final Color backgroundColor;

  const EthioAppBar({
    super.key,
    required this.title,
    this.actions,
    this.showBack = false,
    this.leading,
    this.backgroundColor = kGreen,
  });

  @override
  Widget build(BuildContext context) {
    return AppBar(
      backgroundColor: backgroundColor,
      elevation: 0,
      scrolledUnderElevation: 0,
      leading: leading ??
          (showBack
              ? IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new,
                      color: Colors.white, size: 18),
                  onPressed: () => Navigator.of(context).pop(),
                )
              : null),
      automaticallyImplyLeading: showBack,
      title: Text(
        title,
        style: GoogleFonts.inter(
          fontSize: 17,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
      iconTheme: const IconThemeData(color: Colors.white),
      actionsIconTheme: const IconThemeData(color: Colors.white),
      actions: actions,
    );
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}

// ─────────────────────────────────────────────────────────────────────────
// EthioCard
// ─────────────────────────────────────────────────────────────────────────
class EthioCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final Color? color;
  final Border? border;
  final double radius;
  final VoidCallback? onTap;

  const EthioCard({
    super.key,
    required this.child,
    this.padding,
    this.color,
    this.border,
    this.radius = 12,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color ?? kSurface,
      borderRadius: BorderRadius.circular(radius),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(radius),
        splashColor: kGreenTint,
        highlightColor: kGreenTint.withValues(alpha: 0.4),
        child: Container(
          padding: padding ?? const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(radius),
            border: border ?? Border.all(color: kBorder, width: 0.8),
            boxShadow: const [
              BoxShadow(
                color: Color(0x0F000000),
                blurRadius: 8,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: child,
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// EthioButton
// ─────────────────────────────────────────────────────────────────────────
enum EthioButtonVariant { primary, secondary, ghost }

class EthioButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final EthioButtonVariant variant;
  final IconData? icon;
  final bool isLoading;
  final double height;
  final bool expand;
  final double fontSize;

  const EthioButton({
    super.key,
    required this.label,
    this.onPressed,
    this.variant = EthioButtonVariant.primary,
    this.icon,
    this.isLoading = false,
    this.height = 48,
    this.expand = false,
    this.fontSize = 14,
  });

  Widget get _content {
    if (isLoading) {
      return const SizedBox(
        width: 18,
        height: 18,
        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
      );
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[Icon(icon, size: 16), const SizedBox(width: 6)],
        Text(label,
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, fontSize: fontSize)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final minSize = Size(expand ? double.infinity : 80, height);
    final shape = RoundedRectangleBorder(borderRadius: BorderRadius.circular(8));

    switch (variant) {
      case EthioButtonVariant.primary:
        return ElevatedButton(
          onPressed: isLoading ? null : onPressed,
          style: ElevatedButton.styleFrom(
            backgroundColor: kAmber,
            disabledBackgroundColor: kAmber.withValues(alpha: 0.5),
            foregroundColor: Colors.white,
            minimumSize: minSize,
            shape: shape,
            elevation: 0,
          ),
          child: _content,
        );
      case EthioButtonVariant.secondary:
        return OutlinedButton(
          onPressed: isLoading ? null : onPressed,
          style: OutlinedButton.styleFrom(
            foregroundColor: kGreen,
            minimumSize: minSize,
            side: const BorderSide(color: kGreen, width: 1.5),
            shape: shape,
          ),
          child: _content,
        );
      case EthioButtonVariant.ghost:
        return TextButton(
          onPressed: isLoading ? null : onPressed,
          style: TextButton.styleFrom(
            foregroundColor: kGreen,
            minimumSize: minSize,
          ),
          child: _content,
        );
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────
// StatusBadge
// ─────────────────────────────────────────────────────────────────────────
class StatusBadge extends StatelessWidget {
  final String status;
  final String? overrideLabel;

  const StatusBadge({super.key, required this.status, this.overrideLabel});

  (Color bg, Color dot) _theme(String s) {
    switch (s.toLowerCase()) {
      case 'pending':
        return (kWarningBg, kAmber);
      case 'confirmed':
      case 'matched':
      case 'active':
      case 'accepted':
        return (kGreenTint, kGreen);
      case 'ongoing':
      case 'in_transit':
        return (kGreenTint, kGreenLight);
      case 'completed':
        return (kSuccessBg, kSuccess);
      case 'rejected':
      case 'cancelled':
        return (kDangerBg, kDanger);
      case 'urgent':
        return (kDangerBg, kDanger);
      case 'express':
        return (kWarningBg, kAmber);
      default:
        return (const Color(0xFFF3F4F6), const Color(0xFF6B7280));
    }
  }

  String _label(String s) {
    const map = {
      'pending': 'Pending',
      'confirmed': 'Confirmed',
      'matched': 'Matched',
      'accepted': 'Accepted',
      'active': 'Active',
      'ongoing': 'In Transit',
      'in_transit': 'In Transit',
      'completed': 'Completed',
      'rejected': 'Rejected',
      'cancelled': 'Cancelled',
      'urgent': 'Urgent',
      'express': 'Express',
      'normal': 'Normal',
      'bid_placed': 'Bid Placed',
      'dismissed': 'Dismissed',
    };
    return map[s.toLowerCase()] ?? s;
  }

  @override
  Widget build(BuildContext context) {
    final (bg, dot) = _theme(status);
    final text = overrideLabel ?? _label(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 5,
            height: 5,
            decoration: BoxDecoration(color: dot, shape: BoxShape.circle),
          ),
          const SizedBox(width: 5),
          Text(
            text,
            style: GoogleFonts.inter(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: dot,
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// RouteDisplay  "City A ──► City B" + optional Amharic line
// ─────────────────────────────────────────────────────────────────────────
class RouteDisplay extends StatelessWidget {
  final String from;
  final String to;
  final bool showAmharic;
  final double fontSize;

  const RouteDisplay({
    super.key,
    required this.from,
    required this.to,
    this.showAmharic = true,
    this.fontSize = 13,
  });

  @override
  Widget build(BuildContext context) {
    final fromAm = cityAmharic(from);
    final toAm   = cityAmharic(to);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(from,
                  overflow: TextOverflow.ellipsis,
                  style: GoogleFonts.inter(
                      fontSize: fontSize,
                      fontWeight: FontWeight.w600,
                      color: kTextPrimary)),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 5),
              child: Text('──►',
                  style: TextStyle(
                      color: kAmber,
                      fontSize: fontSize,
                      fontWeight: FontWeight.w700)),
            ),
            Flexible(
              child: Text(to,
                  overflow: TextOverflow.ellipsis,
                  style: GoogleFonts.inter(
                      fontSize: fontSize,
                      fontWeight: FontWeight.w600,
                      color: kTextPrimary)),
            ),
          ],
        ),
        if (showAmharic && (fromAm.isNotEmpty || toAm.isNotEmpty))
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Text(
              '${fromAm.isNotEmpty ? fromAm : from} ──► ${toAm.isNotEmpty ? toAm : to}',
              style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
            ),
          ),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// PriceTag  "ETB X,XXX" in amber bold
// ─────────────────────────────────────────────────────────────────────────
class PriceTag extends StatelessWidget {
  final dynamic amount; // num or String
  final double fontSize;
  final Color color;

  const PriceTag({
    super.key,
    required this.amount,
    this.fontSize = 15,
    this.color = kAmber,
  });

  static String _fmt(num n) {
    final s = n.toStringAsFixed(0);
    final buf = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
      buf.write(s[i]);
    }
    return buf.toString();
  }

  @override
  Widget build(BuildContext context) {
    final parsed = amount is num ? amount as num : num.tryParse(amount.toString());
    final display = parsed != null ? _fmt(parsed) : amount.toString();
    return Text(
      'ETB $display',
      style: GoogleFonts.inter(
        fontSize: fontSize,
        fontWeight: FontWeight.w600,
        color: color,
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// LoadingShimmer  — wrap any widget to give it a shimmer skeleton effect
// ─────────────────────────────────────────────────────────────────────────
class LoadingShimmer extends StatefulWidget {
  final Widget child;
  const LoadingShimmer({super.key, required this.child});

  @override
  State<LoadingShimmer> createState() => _LoadingShimmerState();
}

class _LoadingShimmerState extends State<LoadingShimmer>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 1400))
      ..repeat();
    _anim = Tween<double>(begin: -1.5, end: 2.5)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _anim,
      builder: (_, child) => ShaderMask(
        blendMode: BlendMode.srcATop,
        shaderCallback: (bounds) => LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: const [
            Color(0xFFE4EDE4),
            Color(0xFFF5F8F5),
            Color(0xFFE4EDE4),
          ],
          stops: [
            (_anim.value - 0.4).clamp(0.0, 1.0),
            _anim.value.clamp(0.0, 1.0),
            (_anim.value + 0.4).clamp(0.0, 1.0),
          ],
        ).createShader(bounds),
        child: child!,
      ),
      child: widget.child,
    );
  }
}

/// A placeholder block used inside [LoadingShimmer].
class ShimmerBox extends StatelessWidget {
  final double? width;
  final double height;
  final double radius;

  const ShimmerBox({
    super.key,
    this.width,
    this.height = 16,
    this.radius = 6,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: const Color(0xFFE2E8E2),
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// EmptyState
// ─────────────────────────────────────────────────────────────────────────
class EmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;

  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 84,
              height: 84,
              decoration: BoxDecoration(
                color: kGreenTint,
                shape: BoxShape.circle,
                border: Border.all(color: kBorder),
              ),
              child: Icon(icon, size: 38, color: kTextMuted),
            ),
            const SizedBox(height: 20),
            Text(
              title,
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: kTextPrimary),
            ),
            const SizedBox(height: 6),
            Text(
              subtitle,
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(fontSize: 13, color: kTextSecond),
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 24),
              EthioButton(label: actionLabel!, onPressed: onAction),
            ],
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// TruckTypeChip
// ─────────────────────────────────────────────────────────────────────────
class TruckTypeChip extends StatelessWidget {
  final String truckType;

  const TruckTypeChip({super.key, required this.truckType});

  static const _labels = <String, String>{
    'isuzu_npr':         'Isuzu NPR',
    'faw_j6':            'FAW J6',
    'howo':              'HOWO',
    'sino_truck':        'Sino Truck',
    'mitsubishi_canter': 'Mitsubishi Canter',
  };

  @override
  Widget build(BuildContext context) {
    final label = _labels[truckType.toLowerCase()] ?? truckType;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: kGreenTint,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: kBorder, width: 0.8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.local_shipping, size: 11, color: kGreen),
          const SizedBox(width: 4),
          Text(
            label,
            style: GoogleFonts.inter(
                fontSize: 11, fontWeight: FontWeight.w500, color: kGreen),
          ),
        ],
      ),
    );
  }
}
