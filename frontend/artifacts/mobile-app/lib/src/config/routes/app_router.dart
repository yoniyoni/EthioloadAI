import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../data/providers/data_providers.dart';
import '../../features/landing/landing_screen.dart';
import '../../features/auth/login_screen.dart';
import '../../features/auth/register_screen.dart';
import '../../features/splash/splash_screen.dart';
import '../../features/shipper/shipper_home_screen.dart';
import '../../features/shipper/freight_list_screen.dart';
import '../../features/shipper/create_freight_screen.dart';
import '../../features/shipper/freight_detail_screen.dart';
import '../../features/shipper/shipper_bid_selection_screen.dart';
import '../../features/shipper/tracking_screen.dart';
import '../../features/driver/driver_dashboard_screen.dart';
import '../../features/driver/driver_bids_screen.dart';
import '../../features/driver/active_trip_screen.dart';
// Fleet owner
import '../../features/fleet/fleet_dashboard_screen.dart';
import '../../features/fleet/fleet_dispatch_screen.dart';
import '../../features/driver/driver_documents_screen.dart';
import '../../features/bookings/my_bookings_screen.dart';
import '../../features/profile/profile_screen.dart';
import '../../features/ai/ai_tools_screen.dart';

// ── Router notifier ───────────────────────────────────────────────────────

class _RouterNotifier extends ChangeNotifier {
  final Ref _ref;
  bool _isAuthenticated = false;
  String _role = '';
  bool _isVerified = false;

  _RouterNotifier(this._ref) {
    _ref.listen<AuthState>(authNotifierProvider, (previous, next) {
      final authChanged =
          previous?.isAuthenticated != next.isAuthenticated ||
          previous?.user?.role != next.user?.role ||
          previous?.user?.verificationStatus != next.user?.verificationStatus;
      if (authChanged) {
        _isAuthenticated = next.isAuthenticated;
        _role = next.user?.role ?? '';
        _isVerified = next.user?.verificationStatus ?? false;
        notifyListeners();
      }
    });
    _isAuthenticated = _ref.read(authNotifierProvider).isAuthenticated;
    _role = _ref.read(authNotifierProvider).user?.role ?? '';
    _isVerified =
        _ref.read(authNotifierProvider).user?.verificationStatus ?? false;
  }

  bool get isAuthenticated => _isAuthenticated;
  String get role => _role;
  bool get isVerified => _isVerified;
}

final _routerNotifierProvider = Provider<_RouterNotifier>((ref) {
  return _RouterNotifier(ref);
});

// ── Router ────────────────────────────────────────────────────────────────

final appRouterProvider = Provider<GoRouter>((ref) {
  final notifier = ref.watch(_routerNotifierProvider);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: notifier,
    redirect: (context, state) {
      final isLoggedIn = notifier.isAuthenticated;
      final role = notifier.role;
      final path = state.matchedLocation;

      const publicRoutes = ['/splash', '/landing', '/login', '/register'];
      final isPublic = publicRoutes.contains(path);

      // Unauthenticated on a protected page → landing
      if (!isLoggedIn && !isPublic) return '/landing';

      // Already authenticated on a public page → role dashboard (with gate)
      if (isLoggedIn && isPublic) {
        if (role == 'driver') {
          return notifier.isVerified ? '/driver' : '/driver-documents';
        }
        if (role == 'fleet_owner') return '/fleet';
        return '/shipper';
      }

      // Driver verification gate — fires on every navigation attempt.
      // Keeps unverified drivers on the documents screen regardless of
      // where they navigate (deep links, back button, programmatic pushes).
      if (isLoggedIn &&
          role == 'driver' &&
          !notifier.isVerified &&
          path != '/driver-documents') {
        return '/driver-documents';
      }

      return null;
    },
    routes: [
      // ── Public ────────────────────────────────────────────────────
      GoRoute(path: '/splash', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/landing', builder: (_, __) => const LandingScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, __) => const RegisterScreen()),

      // ── Shipper ────────────────────────────────────────────────────
      GoRoute(
        path: '/shipper',
        builder: (_, __) => _wrap(const ShipperHomeScreen(), isDriver: false),
      ),
      GoRoute(
        path: '/freight',
        builder: (_, __) =>
            _wrap(const FreightListScreen(), isDriver: false),
        routes: [
          GoRoute(
            path: ':id',
            builder: (_, state) {
              final id = int.parse(state.pathParameters['id']!);
              return FreightDetailScreen(freightId: id);
            },
          ),
        ],
      ),
      GoRoute(
        path: '/create-freight',
        builder: (_, __) => const CreateFreightScreen(),
      ),
      GoRoute(
        path: '/cargo-bids/:cargoId',
        builder: (_, state) {
          final id = int.parse(state.pathParameters['cargoId']!);
          return ShipperBidSelectionScreen(cargoId: id);
        },
      ),
      GoRoute(
        path: '/tracking/:freightId',
        builder: (_, state) {
          final id = int.parse(state.pathParameters['freightId']!);
          return TrackingScreen(freightId: id);
        },
      ),

      // ── Driver ─────────────────────────────────────────────────────
      GoRoute(
        path: '/driver',
        builder: (_, __) =>
            _wrap(const DriverDashboardScreen(), isDriver: true),
      ),
      GoRoute(
        path: '/driver/bids',
        builder: (_, __) =>
            _wrap(const DriverBidsScreen(), isDriver: true),
      ),
      GoRoute(
        path: '/driver/active-trip/:tripId',
        builder: (_, state) {
          final id = int.parse(state.pathParameters['tripId']!);
          return ActiveTripScreen(tripId: id);
        },
      ),

      // ── Fleet Owner ────────────────────────────────────────────────
      GoRoute(
        path: '/fleet',
        builder: (_, __) =>
            _wrap(const FleetDashboardScreen(), role: 'fleet_owner'),
      ),
      GoRoute(
        path: '/fleet/drivers',
        builder: (_, __) => const FleetDriversScreen(),
      ),
      GoRoute(
        path: '/fleet/add-driver',
        builder: (_, __) => const FleetDriversScreen(),
      ),
      GoRoute(
        path: '/fleet/add-vehicle',
        builder: (_, __) => const FleetAddVehicleScreen(),
      ),
      GoRoute(
        path: '/fleet/vehicles',
        builder: (_, __) =>
            _wrap(const FleetVehiclesScreen(), role: 'fleet_owner'),
      ),
      GoRoute(
        path: '/fleet/dispatch',
        builder: (_, __) =>
            _wrap(const FleetDispatchScreen(), role: 'fleet_owner'),
      ),
      GoRoute(
        path: '/driver-documents',
        builder: (_, __) => const DriverDocumentsScreen(),
      ),

      // ── Shared (role determines which nav bar is shown) ────────────
      GoRoute(
        path: '/my-bookings',
        builder: (_, state) {
          final role = notifier.role;
          return _wrap(const MyBookingsScreen(),
              isDriver: role == 'driver', role: role);
        },
      ),
      GoRoute(
        path: '/ai-tools',
        builder: (_, state) {
          final role = notifier.role;
          return _wrap(const AiToolsScreen(),
              isDriver: role == 'driver', role: role);
        },
      ),
      GoRoute(
        path: '/profile',
        builder: (_, state) {
          final role = notifier.role;
          return _wrap(const ProfileScreen(),
              isDriver: role == 'driver', role: role);
        },
      ),
    ],
  );
});

/// Wraps [child] in the appropriate bottom-nav scaffold for the role.
Widget _wrap(Widget child, {bool isDriver = false, String role = ''}) {
  // Fleet owner nav
  if (role == 'fleet_owner') {
    return _NavScaffold(
      child: child,
      tabs: const [
        _Tab(icon: Icons.dashboard,      labelKey: 'nav.fleet',    path: '/fleet'),
        _Tab(icon: Icons.people,         labelKey: 'nav.drivers',  path: '/fleet/drivers'),
        _Tab(icon: Icons.local_shipping, labelKey: 'nav.vehicles', path: '/fleet/vehicles'),
        _Tab(icon: Icons.send_rounded,   labelKey: 'nav.dispatch', path: '/fleet/dispatch'),
        _Tab(icon: Icons.person,         labelKey: 'nav.profile',  path: '/profile'),
      ],
    );
  }
  // Driver nav
  if (isDriver) {
    return _NavScaffold(
      child: child,
      tabs: const [
        _Tab(icon: Icons.dashboard,       labelKey: 'nav.jobs',     path: '/driver'),
        _Tab(icon: Icons.receipt_long,    labelKey: 'nav.bookings', path: '/my-bookings'),
        _Tab(icon: Icons.folder_outlined, labelKey: 'nav.docs',     path: '/driver-documents'),
        _Tab(icon: Icons.psychology,      labelKey: 'nav.ai',       path: '/ai-tools'),
        _Tab(icon: Icons.person,          labelKey: 'nav.profile',  path: '/profile'),
      ],
    );
  }
  // Shipper nav (default)
  return _NavScaffold(
    child: child,
    tabs: const [
      _Tab(icon: Icons.home,           labelKey: 'nav.home',     path: '/shipper'),
      _Tab(icon: Icons.local_shipping, labelKey: 'nav.cargo',    path: '/freight'),
      _Tab(icon: Icons.receipt_long,   labelKey: 'nav.bookings', path: '/my-bookings'),
      _Tab(icon: Icons.psychology,     labelKey: 'nav.ai',       path: '/ai-tools'),
      _Tab(icon: Icons.person,         labelKey: 'nav.profile',  path: '/profile'),
    ],
  );
}

// ── Bottom-nav tab definition ─────────────────────────────────────────────

class _Tab {
  final IconData icon;
  final String labelKey; // translation key, e.g. 'nav.jobs'
  final String path;
  const _Tab({required this.icon, required this.labelKey, required this.path});
}

// ── Nav scaffold ──────────────────────────────────────────────────────────

class _NavScaffold extends StatelessWidget {
  final Widget child;
  final List<_Tab> tabs;

  const _NavScaffold({
    required this.tabs,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final currentPath = GoRouterState.of(context).matchedLocation;

    // Find the active tab index by matching the current path
    int currentIndex = 0;
    for (int i = 0; i < tabs.length; i++) {
      if (currentPath == tabs[i].path ||
          currentPath.startsWith('${tabs[i].path}/')) {
        currentIndex = i;
        break;
      }
    }

    return Scaffold(
      body: child,
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: Color(0xFFE2E8E2))),
        ),
        child: BottomNavigationBar(
          type: BottomNavigationBarType.fixed,
          currentIndex: currentIndex,
          backgroundColor: Colors.white,
          selectedItemColor: const Color(0xFFF59E0B),
          unselectedItemColor: const Color(0xFF8FA893),
          elevation: 0,
          onTap: (i) => context.go(tabs[i].path),
          items: tabs
              .map((t) => BottomNavigationBarItem(
                    icon: Icon(t.icon),
                    label: t.labelKey.tr(),
                  ))
              .toList(),
        ),
      ),
    );
  }
}
