import 'package:flutter/foundation.dart';
import 'package:url_launcher/url_launcher.dart';

/// Hands turn-by-turn driving off to the phone's own navigation app
/// (Google Maps, then Waze) instead of relying on the in-app OSRM/MapTiler
/// preview — neither of which has voice guidance, live traffic, or
/// reroute-on-wrong-turn. The in-app map (NavigationScreen) stays as the
/// fallback for devices with no compatible navigation app installed, and
/// remains the shipper-facing route preview regardless.
class NavigationLauncher {
  /// Tries each candidate navigation app in preference order and launches
  /// the first one available. Returns true if one was launched — callers
  /// should fall back to in-app navigation when this returns false.
  static Future<bool> launchExternalNavigation({
    required double lat,
    required double lng,
    String label = '',
  }) async {
    final isAndroid = defaultTargetPlatform == TargetPlatform.android;
    final isIOS = defaultTargetPlatform == TargetPlatform.iOS;

    final candidates = <Uri>[
      if (isAndroid) Uri.parse('google.navigation:q=$lat,$lng&mode=d'),
      if (isIOS)
        Uri.parse('comgooglemaps://?daddr=$lat,$lng&directionsmode=driving'),
      Uri.parse('waze://?ll=$lat,$lng&navigate=yes'),
      if (isIOS) Uri.parse('http://maps.apple.com/?daddr=$lat,$lng&dirflg=d'),
    ];

    for (final uri in candidates) {
      try {
        if (await canLaunchUrl(uri)) {
          final launched =
              await launchUrl(uri, mode: LaunchMode.externalApplication);
          if (launched) return true;
        }
      } catch (_) {
        // Try the next candidate.
      }
    }
    return false;
  }
}
