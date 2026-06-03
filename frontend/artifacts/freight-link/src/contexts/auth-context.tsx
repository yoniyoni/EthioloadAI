import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { User } from "@workspace/api-client-react";

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

  const login = (token: string, user: User) => {
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
