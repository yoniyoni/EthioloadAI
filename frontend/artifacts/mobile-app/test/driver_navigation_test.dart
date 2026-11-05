import 'package:ethioload_ai/src/features/driver/driver_navigation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('driver verification navigation', () {
    test('unverified drivers return to onboarding, not the dashboard', () {
      expect(driverHomePath(false), driverOnboardingPath);
      expect(
        driverVerificationRedirect(
            isVerified: false, path: driverDashboardPath),
        driverOnboardingPath,
      );
      expect(
        driverVerificationRedirect(
            isVerified: false, path: driverDocumentsPath),
        isNull,
      );
      expect(
        driverVerificationRedirect(
            isVerified: false, path: driverOnboardingPath),
        isNull,
      );
    });

    test('verified drivers return to the dashboard', () {
      expect(driverHomePath(true), driverDashboardPath);
      expect(
        driverVerificationRedirect(
          isVerified: true,
          path: driverOnboardingPath,
        ),
        driverDashboardPath,
      );
      expect(
        driverVerificationRedirect(isVerified: true, path: driverDocumentsPath),
        isNull,
      );
    });
  });
}
