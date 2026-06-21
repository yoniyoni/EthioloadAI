import { useState } from 'react';
import { Bell, Search, ChevronDown, LogOut, User, Gavel, CheckCircle, XCircle, ArrowLeftRight, Package } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useAuth } from '@/contexts/auth-context';

const AMBER = '#F59E0B';
const GREEN = '#0F3D1A';

type Props = {
  title: string;
  subtitle?: string;
};

function timeAgo(iso: string): string {
  const secs = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (secs < 60) return 'just now';
  if (secs < 3600) return `${Math.floor(secs / 60)}m ago`;
  if (secs < 86400) return `${Math.floor(secs / 3600)}h ago`;
  return `${Math.floor(secs / 86400)}d ago`;
}

function notifIcon(type: string) {
  switch (type) {
    case 'bid_placed':    return <Gavel size={14} color={AMBER} />;
    case 'bid_accepted':  return <CheckCircle size={14} color="#22C55E" />;
    case 'bid_rejected':  return <XCircle size={14} color="#DC2626" />;
    case 'bid_countered': return <ArrowLeftRight size={14} color="#0EA5E9" />;
    default:              return <Package size={14} color={GREEN} />;
  }
}

export function EthioTopbar({ title, subtitle }: Props) {
  const { user, logout } = useAuth();
  const qc = useQueryClient();
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);

  const { data: notifData } = useQuery({
    queryKey: ['admin-notifications'],
    queryFn: () => api.get<any>('/notifications'),
    refetchInterval: 30_000,
  });

  const notifications: any[] = (notifData as any)?.data ?? [];
  const unreadCount: number  = (notifData as any)?.unread_count ?? 0;

  const markAllRead = useMutation({
    mutationFn: () => api.patch('/notifications/read-all', {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-notifications'] }),
  });

  const markOneRead = useMutation({
    mutationFn: (id: string) => api.patch(`/notifications/${id}/read`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-notifications'] }),
  });

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
      <div className="relative">
        <button
          onClick={() => setNotifOpen((o) => !o)}
          className="relative flex items-center justify-center rounded-lg"
          style={{ width: 36, height: 36, background: '#F8FAF8', border: '1px solid #E2E8E2' }}
        >
          <Bell size={16} color="#6B7F6B" />
          {unreadCount > 0 && (
            <span
              className="absolute -top-1 -right-1 flex items-center justify-center rounded-full text-white font-bold"
              style={{ minWidth: 16, height: 16, fontSize: 9, background: AMBER, padding: '0 3px' }}
            >
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>

        {notifOpen && (
          <>
            <div className="fixed inset-0 z-40" onClick={() => setNotifOpen(false)} />
            <div
              className="absolute right-0 top-full mt-2 rounded-xl shadow-xl z-50 overflow-hidden flex flex-col"
              style={{ background: 'white', border: '1px solid #E2E8E2', width: 340, maxHeight: 420 }}
            >
              {/* Header */}
              <div className="flex items-center justify-between px-4 py-3 border-b" style={{ borderColor: '#E2E8E2' }}>
                <p className="text-sm font-bold" style={{ color: GREEN }}>Notifications</p>
                {unreadCount > 0 && (
                  <button
                    onClick={() => markAllRead.mutate()}
                    disabled={markAllRead.isPending}
                    className="text-xs font-semibold"
                    style={{ color: GREEN }}
                  >
                    Mark all read
                  </button>
                )}
              </div>
              {/* List */}
              <div className="overflow-y-auto flex-1">
                {notifications.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-10 gap-2">
                    <Bell size={28} color="#D1D5DB" />
                    <p className="text-xs" style={{ color: '#8FA893' }}>No notifications yet</p>
                  </div>
                ) : (
                  notifications.map((n: any) => {
                    const isRead = !!n.read_at;
                    return (
                      <div
                        key={n.id}
                        onClick={() => { if (!isRead) markOneRead.mutate(n.id); }}
                        className="flex items-start gap-3 px-4 py-3 cursor-pointer transition-colors hover:bg-gray-50 border-b last:border-b-0"
                        style={{ borderColor: '#F3F4F6', background: isRead ? 'white' : '#FFFBEB' }}
                      >
                        <div
                          className="mt-0.5 flex items-center justify-center rounded-full shrink-0"
                          style={{ width: 28, height: 28, background: '#F8FAF8', border: '1px solid #E2E8E2' }}
                        >
                          {notifIcon(n.data?.type ?? n.type ?? '')}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-xs leading-snug" style={{ color: '#0D1F12', fontWeight: isRead ? 400 : 600 }}>
                            {n.data?.message ?? 'New notification'}
                          </p>
                          {n.data?.route && (
                            <p className="text-xs mt-0.5 truncate" style={{ color: '#8FA893' }}>{n.data.route}</p>
                          )}
                          <p className="text-[10px] mt-1" style={{ color: '#8FA893' }}>
                            {timeAgo(n.created_at)}
                          </p>
                        </div>
                        {!isRead && (
                          <span className="mt-1.5 shrink-0 rounded-full" style={{ width: 7, height: 7, background: AMBER, display: 'block' }} />
                        )}
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </>
        )}
      </div>

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
