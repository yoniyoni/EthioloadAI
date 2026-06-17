import { createContext, useContext, useEffect, useState, ReactNode } from "react";

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
    // Normalize: Laravel returns full_name/verification_status;
    // admin panel expects name/isVerified.
    const r = rawUser as User & { full_name?: string; verification_status?: boolean | number };
    const user: User = {
      ...r,
      name: r.name ?? r.full_name ?? "Unknown",
      isVerified: r.isVerified ?? Boolean(r.verification_status),
    };
    localStorage.setItem(AUTH_KEY, JSON.stringify({ token, user }));
    setState({ user, token, isAuthenticated: true, isLoading: false });
  };

  const logout = () => {
    localStorage.removeItem(AUTH_KEY);
    setState({ user: null, token: null, isAuthenticated: false, isLoading: false });
  };

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
