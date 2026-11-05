const driverOnboardingPath = '/driver-onboarding';
const driverDocumentsPath = '/driver-documents';
const driverDashboardPath = '/driver';

String driverHomePath(bool isVerified) =>
    isVerified ? driverDashboardPath : driverOnboardingPath;

String? driverVerificationRedirect({
  required bool isVerified,
  required String path,
}) {
  if (!isVerified &&
      path != driverOnboardingPath &&
      path != driverDocumentsPath) {
    return driverOnboardingPath;
  }
  if (isVerified && path == driverOnboardingPath) {
    return driverDashboardPath;
  }
  return null;
}
