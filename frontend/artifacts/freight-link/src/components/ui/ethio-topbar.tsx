import { useState } from 'react';
import { Bell, Search, ChevronDown, LogOut, User } from 'lucide-react';
import { useAuth } from '@/contexts/auth-context';

const AMBER = '#F59E0B';
const GREEN = '#0F3D1A';

type Props = {
  title: string;
  subtitle?: string;
};

export function EthioTopbar({ title, subtitle }: Props) {
  const { user, logout } = useAuth();
  const [avatarOpen, setAvatarOpen] = useState(false);

  return (
    <header
      className="fixed top-0 right-0 z-30 flex items-center gap-4 border-b bg-white px-6"
      style={{ left: 240, height: 60, borderColor: '#E2E8E2' }}
    >
      {/* Page title */}
      <div className="flex-1 min-w-0">
        <h1
          className="font-bold text-base leading-none truncate"
          style={{ color: GREEN }}
        >
          {title}
        </h1>
        {subtitle && (
          <p className="text-xs mt-0.5 truncate" style={{ color: '#6B7F6B' }}>
            {subtitle}
          </p>
        )}
      </div>

      {/* Search */}
      <div
        className="hidden md:flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm"
        style={{ background: '#F8FAF8', border: '1px solid #E2E8E2', width: 220 }}
      >
        <Search size={14} color="#8FA893" />
        <input
          className="bg-transparent outline-none flex-1 text-sm"
          placeholder="Search..."
          style={{ color: '#0D1F12' }}
        />
      </div>

      {/* Bell */}
      <button
        className="relative flex items-center justify-center rounded-lg"
        style={{
          width: 36, height: 36,
          background: '#F8FAF8',
          border: '1px solid #E2E8E2',
        }}
      >
        <Bell size={16} color="#6B7F6B" />
        <span
          className="absolute top-1.5 right-1.5 rounded-full"
          style={{ width: 6, height: 6, background: AMBER }}
        />
      </button>

      {/* Avatar dropdown */}
      <div className="relative">
        <button
          onClick={() => setAvatarOpen((o) => !o)}
          className="flex items-center gap-2 rounded-lg px-2.5 py-1.5 transition-colors"
          style={{ border: '1px solid #E2E8E2' }}
        >
          <div
            className="flex items-center justify-center rounded-full text-white text-xs font-bold"
            style={{ background: GREEN, width: 28, height: 28 }}
          >
            {user?.name?.charAt(0).toUpperCase() ?? 'A'}
          </div>
          <span className="hidden sm:block text-sm font-medium" style={{ color: '#0D1F12' }}>
            {user?.name?.split(' ')[0] ?? 'Admin'}
          </span>
          <ChevronDown size={14} color="#8FA893" />
        </button>

        {avatarOpen && (
          <>
            {/* Backdrop */}
            <div
              className="fixed inset-0 z-40"
              onClick={() => setAvatarOpen(false)}
            />
            <div
              className="absolute right-0 top-full mt-2 rounded-xl shadow-lg z-50 py-1 overflow-hidden"
              style={{
                background: 'white',
                border: '1px solid #E2E8E2',
                width: 180,
              }}
            >
              <div className="px-3 py-2 border-b" style={{ borderColor: '#E2E8E2' }}>
                <p className="text-sm font-semibold" style={{ color: '#0D1F12' }}>
                  {user?.name}
                </p>
                <p className="text-xs" style={{ color: '#8FA893' }}>
                  {user?.email}
                </p>
              </div>
              <button
                className="w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors"
                style={{ color: '#6B7F6B' }}
                onMouseEnter={(e) =>
                  ((e.currentTarget as HTMLElement).style.background = '#F8FAF8')
                }
                onMouseLeave={(e) =>
                  ((e.currentTarget as HTMLElement).style.background = 'transparent')
                }
              >
                <User size={14} /> Profile
              </button>
              <button
                onClick={() => { logout(); setAvatarOpen(false); }}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors"
                style={{ color: '#DC2626' }}
                onMouseEnter={(e) =>
                  ((e.currentTarget as HTMLElement).style.background = '#FEF2F2')
                }
                onMouseLeave={(e) =>
                  ((e.currentTarget as HTMLElement).style.background = 'transparent')
                }
              >
                <LogOut size={14} /> Sign out
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  );
}
