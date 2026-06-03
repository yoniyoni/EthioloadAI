import 'package:flutter/foundation.dart';
import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/providers/data_providers.dart';
import '../../features/auth/login_screen.dart';
import '../../features/auth/register_screen.dart';
import '../../features/splash/splash_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/shipper/freight_list_screen.dart';
import '../../features/shipper/create_freight_screen.dart';
import '../../features/shipper/freight_detail_screen.dart';
import '../../features/shipper/tracking_screen.dart';
import '../../features/driver/driver_dashboard_screen.dart';
import '../../features/profile/profile_screen.dart';
import '../../features/ai/ai_tools_screen.dart';
import '../../features/bookings/my_bookings_screen.dart';

/// A [ChangeNotifier] that GoRouter listens to for refresh signals.
/// We notify it manually whenever auth state changes — this tells
/// GoRouter to re-evaluate its redirect without rebuilding the whole
/// provider tree.
class _RouterNotifier extends ChangeNotifier {
  final Ref _ref;
  bool _isAuthenticated = false;

  _RouterNotifier(this._ref) {
    // Listen to auth changes and notify GoRouter when they happen
    _ref.listen<AuthState>(
      authNotifierProvider,
      (previous, next) {
        if (previous?.isAuthenticated != next.isAuthenticated) {
          _isAuthenticated = next.isAuthenticated;
          notifyListeners();
        }
      },
    );
    // Seed initial value without triggering a rebuild
    _isAuthenticated = _ref.read(authNotifierProvider).isAuthenticated;
  }

  bool get isAuthenticated => _isAuthenticated;
}

final _routerNotifierProvider = Provider<_RouterNotifier>((ref) {
  return _RouterNotifier(ref);
});

final appRouterProvider = Provider<GoRouter>((ref) {
  final notifier = ref.watch(_routerNotifierProvider);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: notifier,
    redirect: (context, state) {
      final isLoggedIn = notifier.isAuthenticated;
      final path = state.matchedLocation;

      // If logged in and still on a public route, push to home
      if (isLoggedIn &&
          (path == '/login' || path == '/register' || path == '/splash')) {
        return '/home';
      }

      // All other routes require authentication
      if (!isLoggedIn && path != '/splash' && path != '/login' && path != '/register') {
        return '/login';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        name: 'splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        name: 'register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/home',
        name: 'home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/freight',
        name: 'freight_list',
        builder: (context, state) => const FreightListScreen(),
        routes: [
          GoRoute(
            path: ':id',
            name: 'freight_detail',
            builder: (context, state) {
              final id = int.parse(state.pathParameters['id']!);
              return FreightDetailScreen(freightId: id);
            },
          ),
        ],
      ),
      GoRoute(
        path: '/create-freight',
        name: 'create_freight',
        builder: (context, state) => const CreateFreightScreen(),
      ),
      GoRoute(
        path: '/driver-dashboard',
        name: 'driver_dashboard',
        builder: (context, state) => const DriverDashboardScreen(),
      ),
      GoRoute(
        path: '/tracking/:freightId',
        name: 'tracking',
        builder: (context, state) {
          final id = int.parse(state.pathParameters['freightId']!);
          return TrackingScreen(freightId: id);
        },
      ),
      GoRoute(
        path: '/profile',
        name: 'profile',
        builder: (context, state) => const ProfileScreen(),
      ),
      GoRoute(
        path: '/ai-tools',
        name: 'ai_tools',
        builder: (context, state) => const AiToolsScreen(),
      ),
      GoRoute(
        path: '/my-bookings',
        name: 'my_bookings',
        builder: (context, state) => const MyBookingsScreen(),
      ),
    ],
  );
});
