import { useQuery } from "@tanstack/react-query";
import { Link } from "wouter";
import { api } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

export default function Bookings() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["my-bookings"],
    queryFn: () => api.get<any[]>("/bookings"),
  });
  const bookings = Array.isArray(data) ? data : ((data as any)?.content ?? []);

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <h1 className="text-3xl font-bold tracking-tight">Bookings</h1>
      <p className="text-muted-foreground mt-1 mb-6">Bookings visible to your account</p>
      {isLoading && <Skeleton className="h-32 w-full rounded-xl" />}
      {isError && <p className="text-sm text-destructive">Unable to load bookings.</p>}
      {!isLoading && !isError && bookings.length === 0 && <p className="text-muted-foreground py-16 text-center">No bookings yet.</p>}
      <div className="space-y-3">{bookings.map((booking: any) => <Card key={booking.id} className="rounded-xl border-border/60"><CardHeader className="pb-2"><div className="flex items-center justify-between"><CardTitle className="text-base">Booking #{booking.id}</CardTitle><Badge variant="outline">{booking.bookingStatus}</Badge></div></CardHeader><CardContent><p>{booking.freightDescription ?? `Freight #${booking.freightId}`}</p><p className="text-sm text-muted-foreground mt-1">Driver: {booking.driverName ?? "Unassigned"} · Vehicle: {booking.vehiclePlateNumber ?? "—"}</p><p className="font-semibold mt-2">ETB {Number(booking.estimatedPrice ?? 0).toLocaleString()}</p><Link href={`/payment/${booking.freightId}`} className="text-sm text-primary hover:underline">View payment</Link></CardContent></Card>)}</div>
    </div>
  );
}
