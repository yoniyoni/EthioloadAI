import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { auth } from "@/lib/api";
import { queryClient } from "@/lib/query-client";

export interface User {
  id: number;
  name: string;
  email: string;
  phone: string;
  role: string;
  isVerified?: boolean;
  avatarUrl?: string | null;
  address?: string | null;
  businessName?: string | null;
  preferredLanguage?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface AuthContextType extends AuthState {
  login: (token: string, user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const AUTH_KEY = "freightlink_auth";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: true,
  });

  useEffect(() => {
    try {
      const stored = localStorage.getItem(AUTH_KEY);
      if (stored) {
        const { token, user } = JSON.parse(stored);
        if (token && user) {
          setState({ user, token, isAuthenticated: true, isLoading: false });
          return;
        }
      }
    } catch (e) {
      console.error("Failed to parse auth state", e);
    }
    setState((s) => ({ ...s, isLoading: false }));
  }, []);

  const login = (token: string, rawUser: User) => {
    // Spring Boot UserResponse returns: full_name, verification_status, is_active.
    // Role is returned as uppercase enum (ADMIN, DRIVER, SHIPPER, FLEET_OWNER).
    // The frontend interface uses: name, isVerified, and lowercase role strings.
    // Normalize both field name variants so stored state is consistent.
    const r = rawUser as User & { full_name?: string; verification_status?: boolean | number };
    const user: User = {
      ...r,
      name: r.name ?? r.full_name ?? "Unknown",
      // Normalize role to lowercase so all UI role comparisons work uniformly
      role: (r.role ?? "").toLowerCase(),
      isVerified: r.isVerified ?? Boolean(r.verification_status),
    };
    localStorage.setItem(AUTH_KEY, JSON.stringify({ token, user }));
    queryClient.clear();
    setState({ user, token, isAuthenticated: true, isLoading: false });
  };

  const logout = () => {
    // Fire-and-forget server logout. Spring Boot invalidates the token server-side.
    // We do NOT await this — local session is cleared regardless of network outcome.
    auth.post("/auth/logout", {}).catch(() => {
      // Server logout failed (network error, already expired, etc.).
      // Local session is still cleared below — this is intentional.
    });
    queryClient.clear();
    localStorage.removeItem(AUTH_KEY);
    setState({ user: null, token: null, isAuthenticated: false, isLoading: false });
  };

  // ── 401 / unauthorized event from api.ts ─────────────────────────────────
  // When any fetch receives HTTP 401, api.ts dispatches "freightlink:unauthorized".
  // We listen here, clear local auth state, and redirect to /login.
  // window.location.replace is used so the back button does not loop back.
  useEffect(() => {
    const handle = () => {
      // Clear stored session, clear React Query cache, and redirect to login.
      // window.location.replace avoids adding a history entry (no back-button loop).
      queryClient.clear();
      localStorage.removeItem(AUTH_KEY);
      setState({ user: null, token: null, isAuthenticated: false, isLoading: false });
      window.location.replace("/login");
    };
    window.addEventListener("freightlink:unauthorized", handle);
    return () => window.removeEventListener("freightlink:unauthorized", handle);
  }, []); // runs once — setState is stable

  return (
    <AuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
