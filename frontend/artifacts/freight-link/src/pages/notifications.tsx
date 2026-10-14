import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { SpringWebSocketClient } from "@/lib/realtime";
import { useAuth } from "@/contexts/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Bell, CheckCircle, CircleAlert, Gavel, Package } from "lucide-react";

interface NotificationItem {
  id: string;
  type: string;
  data?: { message?: string; [key: string]: unknown };
  read_at?: string | null;
  readAt?: string | null;
  created_at?: string;
  createdAt?: string;
}

interface NotificationResponse {
  data?: NotificationItem[];
  unread_count?: number;
  unreadCount?: number;
}

function iconFor(type: string) {
  if (type.includes("bid")) return <Gavel className="h-4 w-4 text-amber-600" />;
  if (type.includes("trip") || type.includes("package")) return <Package className="h-4 w-4 text-primary" />;
  if (type.includes("error")) return <CircleAlert className="h-4 w-4 text-destructive" />;
  return <Bell className="h-4 w-4 text-emerald-600" />;
}

function messageFor(notification: NotificationItem) {
  return notification.data?.message ?? notification.type.replace(/_/g, " ");
}

function timeFor(notification: NotificationItem) {
  const value = notification.created_at ?? notification.createdAt;
  return value ? new Date(value).toLocaleString() : "";
}

export default function Notifications() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const enabled = user?.role === "driver" || user?.role === "shipper" || user?.role === "fleet_owner";
  const { data, isLoading, isError } = useQuery({
    queryKey: ["notifications"],
    queryFn: () => api.get<NotificationResponse>("/notifications"),
    enabled,
    refetchInterval: 30_000,
  });
  useEffect(() => {
    if (!enabled || !user?.id) return;
    const client = new SpringWebSocketClient("/ws/notifications", `user.${user.id}.notifications`);
    const unsubscribe = client.onMessage((message) => {
      if (message.type === "NOTIFICATION" || message.type === "TRIP_STATUS_CHANGED") {
        queryClient.invalidateQueries({ queryKey: ["notifications"] });
      }
    });
    client.connect();
    return () => { unsubscribe(); client.dispose(); };
  }, [enabled, user?.id, queryClient]);
  const markAllRead = useMutation({
    mutationFn: () => api.patch("/notifications/read-all", {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
  const markRead = useMutation({
    mutationFn: (id: string) => api.patch(`/notifications/${id}/read`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
  const notifications = data?.data ?? [];
  const unread = data?.unread_count ?? data?.unreadCount ?? 0;

  if (!enabled) return <div className="container mx-auto max-w-4xl px-4 py-16 text-center"><p className="text-lg font-medium">Notifications are available from an authenticated account.</p></div>;

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
        <div><h1 className="text-3xl font-bold tracking-tight">Notifications</h1><p className="text-muted-foreground mt-1">Updates about bids, trips, and freight activity</p></div>
        <Button variant="outline" className="rounded-lg" onClick={() => markAllRead.mutate()} disabled={!unread || markAllRead.isPending}>Mark all read</Button>
      </div>
      {isLoading && <div className="space-y-3"><Skeleton className="h-20 w-full rounded-xl" /><Skeleton className="h-20 w-full rounded-xl" /></div>}
      {isError && <p className="text-sm text-destructive">Unable to load notifications.</p>}
      {!isLoading && !isError && notifications.length === 0 && <Card className="rounded-xl border-border/60"><CardContent className="py-16 text-center"><Bell className="h-10 w-10 mx-auto mb-3 text-muted-foreground/40" /><p className="font-medium">No notifications yet.</p><p className="text-sm text-muted-foreground mt-1">New activity will appear here.</p></CardContent></Card>}
      <div className="space-y-3">{notifications.map((notification) => { const read = (notification.read_at ?? notification.readAt) != null; return <Card key={notification.id} className={`rounded-xl border-border/60 ${read ? "" : "border-primary/40 bg-primary/[0.03]"}`}><CardContent className="flex items-start gap-3 p-4"><div className="h-9 w-9 shrink-0 rounded-full bg-muted flex items-center justify-center">{iconFor(notification.type)}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><p className={`text-sm ${read ? "" : "font-semibold"}`}>{messageFor(notification)}</p>{!read && <Badge className="bg-amber-50 text-amber-700 border-amber-200">Unread</Badge>}</div><p className="text-xs text-muted-foreground mt-1">{timeFor(notification)}</p></div>{!read && <Button size="sm" variant="ghost" onClick={() => markRead.mutate(notification.id)} disabled={markRead.isPending}><CheckCircle className="h-4 w-4 mr-1" />Read</Button>}</CardContent></Card>; })}</div>
    </div>
  );
}
