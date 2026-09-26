/**
 * notification-bell.tsx
 *
 * Self-contained notification bell for the top navigation bar.
 * Used by Layout for authenticated users (all roles).
 *
 * API:
 *   GET  /api/notifications          → { data: Notification[], unread_count: number }
 *   PATCH /api/notifications/read-all → { success: true }
 *   PATCH /api/notifications/{id}/read → { success: true }
 *
 * Polls every 30 seconds while the user is authenticated.
 * Shows a badge with unread count, a dropdown list, and mark-read actions.
 * No 404 errors — the Spring Boot NotificationController handles all three routes.
 */

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Bell, Gavel, CheckCircle, XCircle, ArrowLeftRight, Package } from 'lucide-react'
import { api } from '@/lib/api'
import { useAuth } from '@/contexts/auth-context'

// ── Types ────────────────────────────────────────────────────────────────────

interface NotificationItem {
  id: string
  type: string
  data: Record<string, unknown>
  read_at: string | null
  created_at: string
}

interface NotificationListResponse {
  data: NotificationItem[]
  unread_count: number
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function timeAgo(iso: string): string {
  if (!iso) return ''
  const secs = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (secs < 60) return 'just now'
  if (secs < 3600) return `${Math.floor(secs / 60)}m ago`
  if (secs < 86400) return `${Math.floor(secs / 3600)}h ago`
  return `${Math.floor(secs / 86400)}d ago`
}

function notifIcon(type: string) {
  switch (type) {
    case 'bid_placed':    return <Gavel    className="h-3.5 w-3.5 text-amber-500" />
    case 'bid_accepted':  return <CheckCircle className="h-3.5 w-3.5 text-emerald-500" />
    case 'bid_rejected':  return <XCircle  className="h-3.5 w-3.5 text-red-500" />
    case 'bid_countered': return <ArrowLeftRight className="h-3.5 w-3.5 text-sky-500" />
    default:              return <Package  className="h-3.5 w-3.5 text-primary" />
  }
}

function getMessage(n: NotificationItem): string {
  const d = n.data as Record<string, unknown>
  if (typeof d?.message === 'string') return d.message
  return n.type.replace(/_/g, ' ')
}

// ── Component ─────────────────────────────────────────────────────────────────

export function NotificationBell() {
  const { isAuthenticated } = useAuth()
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)

  const { data } = useQuery<NotificationListResponse>({
    queryKey: ['notifications'],
    queryFn: () => api.get<NotificationListResponse>('/notifications'),
    enabled: isAuthenticated,
    // Poll every 30 s — only when the tab is visible to avoid pointless requests.
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  })

  const notifications: NotificationItem[] = data?.data ?? []
  const unreadCount: number = data?.unread_count ?? 0

  const markAllRead = useMutation({
    mutationFn: () => api.patch('/notifications/read-all', {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  const markOneRead = useMutation({
    mutationFn: (id: string) => api.patch(`/notifications/${id}/read`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })

  if (!isAuthenticated) return null

  return (
    <div className="relative">
      {/* Bell button */}
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
        className="relative flex h-9 w-9 items-center justify-center rounded-lg border border-border hover:bg-muted transition-colors"
      >
        <Bell className="h-4 w-4 text-muted-foreground" />
        {unreadCount > 0 && (
          <span
            aria-hidden="true"
            className="absolute -top-1 -right-1 flex min-w-[16px] h-4 items-center justify-center rounded-full bg-amber-500 px-1 text-[9px] font-bold text-white leading-none"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown */}
      {open && (
        <>
          {/* Backdrop */}
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />

          <div
            role="dialog"
            aria-label="Notifications"
            className="absolute right-0 top-full mt-2 z-50 flex flex-col w-80 max-h-96 rounded-xl shadow-xl bg-background border border-border overflow-hidden"
          >
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-border">
              <p className="text-sm font-semibold text-foreground">Notifications</p>
              {unreadCount > 0 && (
                <button
                  onClick={() => markAllRead.mutate()}
                  disabled={markAllRead.isPending}
                  className="text-xs font-medium text-primary hover:underline disabled:opacity-50"
                >
                  Mark all read
                </button>
              )}
            </div>

            {/* List */}
            <div className="overflow-y-auto flex-1">
              {notifications.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 gap-2 text-center px-4">
                  <Bell className="h-7 w-7 text-muted-foreground/30" />
                  <p className="text-xs text-muted-foreground">No notifications yet</p>
                </div>
              ) : (
                notifications.map((n) => {
                  const isRead = n.read_at !== null
                  return (
                    <div
                      key={n.id}
                      onClick={() => {
                        if (!isRead) markOneRead.mutate(n.id)
                      }}
                      className={`flex items-start gap-3 px-4 py-3 cursor-pointer hover:bg-muted/60 border-b border-border/40 last:border-b-0 transition-colors ${
                        isRead ? 'bg-background' : 'bg-amber-50/60 dark:bg-amber-950/20'
                      }`}
                    >
                      {/* Icon */}
                      <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-border bg-muted">
                        {notifIcon(n.type)}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <p className={`text-xs leading-snug text-foreground ${isRead ? 'font-normal' : 'font-semibold'}`}>
                          {getMessage(n)}
                        </p>
                        <p className="text-[10px] mt-1 text-muted-foreground">
                          {timeAgo(n.created_at)}
                        </p>
                      </div>

                      {/* Unread dot */}
                      {!isRead && (
                        <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-amber-500" />
                      )}
                    </div>
                  )
                })
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
