import { useAuth } from "@/contexts/auth-context";
import { Link, useLocation } from "wouter";
import { useEffect } from "react";
import { Loader2 } from "lucide-react";

export function ProtectedRoute({ children, allowedRoles }: { children: React.ReactNode, allowedRoles?: string[] }) {
  const { isAuthenticated, isLoading, user } = useAuth();
  const [, setLocation] = useLocation();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      setLocation("/login");
    } else if (!isLoading && isAuthenticated && allowedRoles && user && !allowedRoles.includes(user.role)) {
      setLocation("/dashboard");
    }
  }, [isLoading, isAuthenticated, user, allowedRoles, setLocation]);

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

  return <>{children}</>;
}
