import { useAuth } from "@/contexts/auth-context";
import { useLocation } from "wouter";
import { useEffect } from "react";
import { Loader2, Clock, AlertTriangle } from "lucide-react";

/**
 * Route guard that enforces:
 * 1. Authentication — redirects to /login if not logged in.
 * 2. Role — redirects to /dashboard if the user's role is not in allowedRoles.
 * 3. Driver KYC — a driver with isVerified=false is shown a "pending approval"
 *    screen instead of the protected content. They cannot access driver workspace
 *    pages (bidding, marketplace, trips) until an admin approves their account.
 *    They CAN access /register (to upload docs) and /documents.
 */
export function ProtectedRoute({
  children,
  allowedRoles,
}: {
  children: React.ReactNode;
  allowedRoles?: string[];
}) {
  const { isAuthenticated, isLoading, user, membershipError, refreshMemberships } = useAuth();
  const [location, setLocation] = useLocation();

  // A driver whose account is not yet verified (KYC pending) must not access
  // the normal driver workspace. They are redirected to /documents so they can
  // see their upload status and wait for approval.
  const isPendingDriver =
    isAuthenticated &&
    user?.role === "driver" &&
    user?.isVerified === false;

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      setLocation("/login");
    } else if (
      !isLoading &&
      isAuthenticated &&
      allowedRoles &&
      user &&
      !allowedRoles.includes(user.role)
    ) {
      setLocation("/dashboard");
    }
    // Pending drivers trying to access routes other than /documents or /register
    // are redirected to /documents so they can complete their KYC submission.
    else if (
      !isLoading &&
      isPendingDriver &&
      location !== "/documents" &&
      location !== "/register"
    ) {
      setLocation("/documents");
    }
  }, [isLoading, isAuthenticated, user, allowedRoles, setLocation, isPendingDriver, location]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!isAuthenticated || (allowedRoles && user && !allowedRoles.includes(user.role))) {
    return null;
  }

  if (membershipError) return <div role="alert" className="p-6 space-y-3"><p>{membershipError}</p><button className="underline" onClick={() => void refreshMemberships().catch(() => {})}>Retry company access</button></div>;

  // Show a clear pending-approval wall for unverified drivers rather than
  // blank content on driver workspace routes. Unverified drivers ARE allowed
  // on /documents and /register so they can complete their KYC submission.
  if (isPendingDriver && location !== "/documents" && location !== "/register") {
    return (
      <div className="flex min-h-[60vh] items-center justify-center px-4">
        <div className="text-center max-w-sm space-y-4">
          <div className="mx-auto h-16 w-16 rounded-full bg-amber-100 flex items-center justify-center">
            <Clock className="h-8 w-8 text-amber-600" />
          </div>
          <h2 className="text-xl font-bold">Account Pending Approval</h2>
          <p className="text-muted-foreground text-sm leading-relaxed">
            Your driver application is under review. You will be able to access
            the driver workspace once an admin approves your account.
          </p>
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800 flex items-start gap-2 text-left">
            <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>
              Make sure you have uploaded all 5 required documents on the{" "}
              <a href="/documents" className="underline font-medium">Documents</a> page.
            </span>
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
