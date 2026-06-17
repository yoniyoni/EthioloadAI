import { useLocation, useSearch } from 'wouter';
import {
  LayoutDashboard, Truck, Users, CreditCard,
  AlertTriangle, LogOut, ChevronRight, Building2, Lock, FileText, TrendingUp, Banknote,
} from 'lucide-react';
import { useAuth } from '@/contexts/auth-context';

const GREEN = '#0F3D1A';
const AMBER = '#F59E0B';

type NavItem = {
  label: string;
  icon: React.ReactNode;
  tab: string;
};

const NAV_ITEMS: NavItem[] = [
  { label: 'Overview',   icon: <LayoutDashboard size={18} />, tab: 'overview'  },
  { label: 'Drivers',    icon: <Truck size={18} />,           tab: 'drivers'   },
  { label: 'Fleet',      icon: <Building2 size={18} />,       tab: 'fleet'     },
  { label: 'Users',      icon: <Users size={18} />,           tab: 'users'     },
  { label: 'Payments',   icon: <CreditCard size={18} />,      tab: 'payments'  },
  { label: 'Escrow',     icon: <Banknote size={18} />,        tab: 'escrow'    },
  { label: 'Disputes',   icon: <AlertTriangle size={18} />,   tab: 'disputes'  },
  { label: 'Documents',  icon: <FileText size={18} />,        tab: 'documents' },
  { label: 'Analytics',  icon: <TrendingUp size={18} />,      tab: 'analytics' },
];

export function EthioSidebar() {
  const [, navigate] = useLocation();
  const rawSearch = useSearch();
  const { user, logout } = useAuth();

  const searchStr = rawSearch.startsWith('?') ? rawSearch.slice(1) : rawSearch;
  const currentTab = new URLSearchParams(searchStr).get('tab') || 'overview';

  const handleNav = (tab: string) => {
    navigate(tab === 'overview' ? '/admin' : `/admin?tab=${tab}`);
  };

  return (
    <aside
      style={{ background: GREEN, width: 240, flexShrink: 0 }}
      className="fixed left-0 top-0 h-screen flex flex-col z-40"
    >
      {/* Logo */}
      <div className="flex items-center gap-3 px-5 py-5 border-b border-white/10">
        <div
          className="flex items-center justify-center rounded-lg"
          style={{ background: AMBER, width: 36, height: 36 }}
        >
          <Truck size={20} color="white" />
        </div>
        <div>
          <p className="text-white font-bold text-sm leading-none">EthioLoad</p>
          <p style={{ color: AMBER }} className="text-xs font-semibold">AI Admin</p>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto py-4 px-3">
        <p className="text-white/40 text-[10px] font-semibold uppercase tracking-widest px-2 mb-2">
          Management
        </p>
        <ul className="space-y-0.5">
          {NAV_ITEMS.map((item) => {
            const active = currentTab === item.tab;
            return (
              <li key={item.tab}>
                <button
                  onClick={() => handleNav(item.tab)}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors group relative"
                  style={{
                    color: active ? 'white' : 'rgba(255,255,255,0.6)',
                    background: active ? 'rgba(255,255,255,0.1)' : 'transparent',
                    borderLeft: active ? `3px solid ${AMBER}` : '3px solid transparent',
                  }}
                >
                  <span style={{ color: active ? AMBER : 'rgba(255,255,255,0.6)' }}>
                    {item.icon}
                  </span>
                  {item.label}
                  {active && (
                    <ChevronRight size={14} className="ml-auto" style={{ color: AMBER }} />
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* User + Logout */}
      <div className="border-t border-white/10 p-4">
        {user && (
          <div className="flex items-center gap-3 mb-3">
            <div
              className="flex items-center justify-center rounded-full text-white text-xs font-bold shrink-0"
              style={{ background: AMBER, width: 34, height: 34 }}
            >
              {user.name?.charAt(0).toUpperCase() ?? 'A'}
            </div>
            <div className="min-w-0">
              <p className="text-white text-sm font-semibold truncate">{user.name}</p>
              <p className="text-white/40 text-xs truncate">{user.role}</p>
            </div>
          </div>
        )}
        <button
          onClick={() => logout()}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors"
          style={{ color: 'rgba(255,255,255,0.5)' }}
          onMouseEnter={(e) =>
            ((e.currentTarget as HTMLElement).style.color = 'white')
          }
          onMouseLeave={(e) =>
            ((e.currentTarget as HTMLElement).style.color = 'rgba(255,255,255,0.5)')
          }
        >
          <LogOut size={16} />
          Sign out
        </button>
      </div>
    </aside>
  );
}
