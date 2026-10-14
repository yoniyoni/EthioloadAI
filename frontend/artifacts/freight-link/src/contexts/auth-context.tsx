import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from "react";
import { useRef } from "react";
import { api, auth, ApiError, getToken } from "@/lib/api";
import { queryClient } from "@/lib/query-client";
import { pendingInvitation } from "@/lib/invitation-session";
export interface User {
  id: number; name: string; email: string; phone: string; role: string;
  isVerified?: boolean; avatarUrl?: string | null; address?: string | null;
  businessName?: string | null; preferredLanguage?: string; createdAt?: string; updatedAt?: string;
}
export interface Membership { id: number; carrierCompanyId: number; role: string; status: string }
interface AuthContextType {
  user: User | null; token: string | null; isAuthenticated: boolean; isLoading: boolean;
  memberships: Membership[]; refreshMemberships: () => Promise<void>;
  membershipError: string;
  login: (token: string, user: User) => void; logout: () => void;
}
const AuthContext = createContext<AuthContextType | undefined>(undefined);
const AUTH_KEY = "freightlink_auth";
function normalize(raw: User): User {
  const r = raw as User & { full_name?: string; verification_status?: boolean | number };
  return {...r, name:r.name ?? r.full_name ?? "Unknown", role:(r.role ?? "").toLowerCase(), isVerified:r.isVerified ?? Boolean(r.verification_status)};
}
export function AuthProvider({children}: {children: ReactNode}) {
  const [user,setUser] = useState<User|null>(null);
  const [token,setToken] = useState<string|null>(null);
  const [isLoading,setLoading] = useState(true);
  const [memberships,setMemberships] = useState<Membership[]>([]);
  const [membershipLoading,setMembershipLoading] = useState(false);
  const [membershipError,setMembershipError] = useState("");
  const membershipRequest = useRef(0);
  const membershipReady = useRef(false);
  const refreshMemberships = useCallback(async () => {
    const currentToken = getToken();
    const request = ++membershipRequest.current;
    if (!currentToken) return;
    if (!membershipReady.current) setMembershipLoading(true);
    setMembershipError("");
    try {
      const page = await api.get<{content:Membership[]}>("/company-memberships/me?size=100");
      if (request === membershipRequest.current && currentToken === getToken()) {
        setMemberships(page.content.filter(m=>m.status === "ACTIVE"));
        membershipReady.current = true;
      }
    } catch (error) {
      if (request === membershipRequest.current && currentToken === getToken()) {
        setMembershipError("Unable to load company access. Please retry.");
      }
      throw error;
    } finally {
      if (request === membershipRequest.current) setMembershipLoading(false);
    }
  },[]);
  const clear = useCallback(() => {
    ++membershipRequest.current; membershipReady.current = false; setMembershipLoading(false); setMembershipError("");
    localStorage.removeItem(AUTH_KEY); queryClient.clear(); setUser(null); setToken(null); setMemberships([]);
  },[]);
  useEffect(()=>{
    let active = true;
    const restoredToken = getToken();
    (async()=>{
      try {
        const stored=JSON.parse(localStorage.getItem(AUTH_KEY)||"null");
        if (!stored?.token || stored.token.startsWith("mock.")) { clear(); return; }
        const current=normalize(await auth.get<User>("/auth/me"));
        if (!active || stored.token !== getToken()) return;
        localStorage.setItem(AUTH_KEY,JSON.stringify({token:stored.token,user:current}));
        setUser(current);setToken(stored.token);
        await refreshMemberships();
      } catch(e) { if(active && restoredToken === getToken() && e instanceof ApiError && e.status===401) clear(); }
      finally { if(active) setLoading(false); }
    })();
    return ()=>{active=false;};
  },[clear,refreshMemberships]);
  const login=(nextToken:string,raw:User)=>{
    membershipReady.current = false;
    const current=normalize(raw);
    localStorage.setItem(AUTH_KEY,JSON.stringify({token:nextToken,user:current}));
    queryClient.clear();setUser(current);setToken(nextToken);setMemberships([]);setLoading(false);
    void refreshMemberships().catch(()=>{});
  };
  const logout=()=>{ void auth.post("/auth/logout",{}).catch(()=>{});clear(); };
  useEffect(()=>{
    const handle=()=>{clear();window.location.replace(pendingInvitation()?"/login?invitation=1":"/login");};
    window.addEventListener("freightlink:unauthorized",handle);
    return ()=>window.removeEventListener("freightlink:unauthorized",handle);
  },[clear]);
  useEffect(()=>{
    if(!token)return;
    const reload=()=>{void refreshMemberships().catch(()=>{});};
    window.addEventListener("focus",reload);
    return ()=>window.removeEventListener("focus",reload);
  },[token,refreshMemberships]);
  return <AuthContext.Provider value={{user,token,isAuthenticated:!!user && !!token,isLoading:isLoading || membershipLoading,memberships,membershipError,refreshMemberships,login,logout}}>{children}</AuthContext.Provider>;
}
export function useAuth(){const context=useContext(AuthContext);if(!context)throw new Error("useAuth requires AuthProvider");return context;}
