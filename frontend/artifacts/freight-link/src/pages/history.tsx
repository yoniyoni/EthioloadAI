import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Activity, Gavel, Route, Truck } from "lucide-react";

export default function History() {
  const { user } = useAuth();
  const enabled = user?.role === "driver" || user?.role === "shipper" || user?.role === "fleet_owner";
  const driverOrFleet = user?.role === "driver" || user?.role === "fleet_owner";
  const bidsQuery = useQuery({ queryKey: ["my-bids"], queryFn: () => api.get<{ content?: any[] }>("/driver/bids"), enabled: user?.role === "driver" });
  const historyQuery = useInfiniteQuery({
    queryKey: ["shipper-freight-history"],
    queryFn: ({ pageParam }) => api.get<{ content: any[]; number: number; hasNext: boolean }>(
      `/history/shipper/freight?page=${pageParam}&size=20`
    ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.number + 1 : undefined,
    enabled: user?.role === "shipper",
  });
  const bookingsQuery = useInfiniteQuery({
    queryKey: ["my-bookings-page"],
    queryFn: ({ pageParam }) => api.get<{ content: any[]; number: number; hasNext: boolean }>(
      `/bookings/page?page=${pageParam}&size=20`
    ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.number + 1 : undefined,
    enabled: driverOrFleet,
  });
  const tripsQuery = useQuery({ queryKey: ["my-trips"], queryFn: () => api.get<{ content?: any[] }>("/trips?size=100"), enabled: driverOrFleet });
  const bids = Array.isArray(bidsQuery.data?.content) ? bidsQuery.data.content : (Array.isArray(bidsQuery.data) ? bidsQuery.data : []);
  const rows = historyQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const bookings = Array.from(new Map(
    bookingsQuery.data?.pages.flatMap((page) => page.content).map((booking) => [booking.id, booking]) ?? []
  ).values());
  const trips = Array.isArray(tripsQuery.data?.content) ? tripsQuery.data.content : (Array.isArray(tripsQuery.data) ? tripsQuery.data : []);
  const loading = bidsQuery.isLoading || historyQuery.isLoading || bookingsQuery.isLoading || tripsQuery.isLoading;
  const error = bidsQuery.isError || historyQuery.isError || bookingsQuery.isError || tripsQuery.isError;

  if (!enabled) return <div className="container mx-auto max-w-5xl px-4 py-16 text-center"><p className="text-lg font-medium">History is available from a shipper or driver account.</p></div>;

  if (user?.role === "shipper") {
    return (
      <div className="container mx-auto max-w-6xl px-4 py-8">
        <div className="mb-6"><h1 className="text-3xl font-bold tracking-tight">Shipment History</h1><p className="text-muted-foreground mt-1">Freight, bookings, trips, and payments returned by Spring Boot</p></div>
        {loading && <div className="space-y-3"><Skeleton className="h-24 w-full rounded-xl" /><Skeleton className="h-24 w-full rounded-xl" /></div>}
        {error && <p className="text-sm text-destructive mb-4">Some shipment history could not be loaded.</p>}
        {!loading && !error && rows.length === 0 && <Card className="rounded-xl border-border/60"><CardContent className="py-16 text-center"><Activity className="h-10 w-10 mx-auto mb-3 text-muted-foreground/40" /><p className="font-medium">No shipment history yet.</p><p className="text-sm text-muted-foreground mt-1">Your freight and delivery activity will appear here.</p></CardContent></Card>}
        {!loading && rows.length > 0 && <Card className="rounded-xl border-border/60 overflow-hidden"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><Route className="h-4 w-4 text-primary" /> Shipments</CardTitle></CardHeader><CardContent className="space-y-3">{rows.map(({ freight, booking, trip, payment }: any) => {
          const status = trip?.tripStatus ?? booking?.bookingStatus ?? freight.status ?? "unknown";
          return <div key={freight.id} className="rounded-lg border border-border/60 p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="font-medium">{freight.cargoType ?? freight.materialType ?? freight.description ?? `Shipment #${freight.id}`}</p><p className="text-sm text-muted-foreground">{freight.pickupLocation ?? freight.origin ?? "Origin unavailable"} <span aria-hidden="true">→</span> {freight.deliveryLocation ?? freight.destination ?? "Destination unavailable"}</p></div><Badge variant="outline">{status}</Badge></div><div className="mt-4 grid gap-3 text-sm sm:grid-cols-4"><div><p className="text-xs text-muted-foreground">Booking</p><p>{booking ? `#${booking.id} · ${booking.bookingStatus}` : "Not available"}</p></div><div><p className="text-xs text-muted-foreground">Driver</p><p>{trip?.driver?.fullName ?? booking?.driverName ?? "Not assigned"}</p></div><div><p className="text-xs text-muted-foreground">Payment</p><p>{payment?.paymentStatus ?? "Not available"}</p></div><div><p className="text-xs text-muted-foreground">Updated</p><p>{formatDate(trip?.updatedAt ?? booking?.updatedAt ?? freight.updatedAt ?? freight.createdAt)}</p></div></div>{trip?.id && <div className="mt-4"><a className="text-sm font-medium text-primary hover:underline" href={`/tracking/${freight.id}?tripId=${trip.id}`}>Open live tracking</a></div>}</div>;
        })}{historyQuery.hasNextPage && <button type="button" className="w-full rounded-lg border p-3 text-sm font-medium hover:bg-muted disabled:opacity-50" onClick={() => historyQuery.fetchNextPage()} disabled={historyQuery.isFetchingNextPage}>{historyQuery.isFetchingNextPage ? "Loading…" : "Load more"}</button>}</CardContent></Card>}
      </div>
    );
  }

  if (user?.role === "fleet_owner") {
    return (
      <div className="container mx-auto max-w-6xl px-4 py-8">
        <div className="mb-6"><h1 className="text-3xl font-bold tracking-tight">Fleet History</h1><p className="mt-1 text-muted-foreground">Bookings and trips visible to your fleet through Spring Boot</p></div>
        {loading && <div className="space-y-3"><Skeleton className="h-24 w-full rounded-xl" /><Skeleton className="h-24 w-full rounded-xl" /></div>}
        {error && <p className="mb-4 text-sm text-destructive">Some fleet history could not be loaded.</p>}
        {!loading && !error && bookings.length === 0 && trips.length === 0 && <Card className="rounded-xl border-border/60"><CardContent className="py-16 text-center"><Activity className="mx-auto mb-3 h-10 w-10 text-muted-foreground/40" /><p className="font-medium">No fleet history yet.</p><p className="mt-1 text-sm text-muted-foreground">Bookings and trips for your fleet will appear here.</p></CardContent></Card>}
        <div className="grid gap-4 md:grid-cols-2">
          <Card className="rounded-xl border-border/60"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><Truck className="h-4 w-4 text-primary" /> Fleet bookings</CardTitle></CardHeader><CardContent>{bookings.length === 0 ? <p className="text-sm text-muted-foreground">No bookings returned.</p> : <div className="space-y-2">{bookings.map((booking: any) => <div key={booking.id} className="rounded-lg border p-3 text-sm"><div className="flex justify-between gap-2"><span>Booking #{booking.id}</span><Badge variant="outline">{booking.bookingStatus}</Badge></div><p className="mt-1 text-xs text-muted-foreground">{booking.driverName ?? "Driver unavailable"} · {booking.freightDescription ?? `Freight #${booking.freightId}`}</p></div>)}</div>}{bookingsQuery.hasNextPage && <button type="button" className="mt-3 w-full rounded-lg border p-3 text-sm font-medium hover:bg-muted disabled:opacity-50" onClick={() => bookingsQuery.fetchNextPage()} disabled={bookingsQuery.isFetchingNextPage}>{bookingsQuery.isFetchingNextPage ? "Loading…" : "Load more"}</button>}</CardContent></Card>
          <Card className="rounded-xl border-border/60"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><Route className="h-4 w-4 text-emerald-600" /> Fleet trips</CardTitle></CardHeader><CardContent>{trips.length === 0 ? <p className="text-sm text-muted-foreground">No trips returned.</p> : <div className="space-y-2">{trips.map((trip: any) => <div key={trip.id} className="rounded-lg border p-3 text-sm"><div className="flex justify-between gap-2"><span>Trip #{trip.id}</span><Badge variant="outline">{trip.tripStatus ?? trip.status}</Badge></div><p className="mt-1 text-xs text-muted-foreground">{trip.destination ?? "Destination unavailable"}</p></div>)}</div>}</CardContent></Card>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <div className="mb-6"><h1 className="text-3xl font-bold tracking-tight">Driver History</h1><p className="text-muted-foreground mt-1">Bids, bookings, and trips returned by Spring Boot</p></div>
      {loading && <div className="space-y-3"><Skeleton className="h-24 w-full rounded-xl" /><Skeleton className="h-24 w-full rounded-xl" /></div>}
      {error && <p className="text-sm text-destructive mb-4">Some history could not be loaded.</p>}
      {!loading && !error && bids.length === 0 && bookings.length === 0 && trips.length === 0 && <Card className="rounded-xl border-border/60"><CardContent className="py-16 text-center"><Activity className="h-10 w-10 mx-auto mb-3 text-muted-foreground/40" /><p className="font-medium">No driver history yet.</p><p className="text-sm text-muted-foreground mt-1">Completed actions and applications will appear here.</p></CardContent></Card>}
      <div className="grid gap-4 md:grid-cols-3">
        <Card className="rounded-xl border-border/60"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><Gavel className="h-4 w-4 text-amber-600" /> Bids</CardTitle></CardHeader><CardContent>{bids.length === 0 ? <p className="text-sm text-muted-foreground">No bids yet.</p> : <div className="space-y-2">{bids.map((bid: any) => <div key={bid.id} className="rounded-lg border p-3 text-sm"><div className="flex justify-between gap-2"><span>ETB {Number(bid.amount ?? 0).toLocaleString()}</span><Badge variant="outline">{bid.status}</Badge></div><p className="text-xs text-muted-foreground mt-1">{bid.vehiclePlateNumber ?? "Vehicle not set"}</p></div>)}</div>}</CardContent></Card>
        <Card className="rounded-xl border-border/60"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><Truck className="h-4 w-4 text-primary" /> Bookings</CardTitle></CardHeader><CardContent>{bookings.length === 0 ? <p className="text-sm text-muted-foreground">No bookings yet.</p> : <div className="space-y-2">{bookings.map((booking: any) => <div key={booking.id} className="rounded-lg border p-3 text-sm"><div className="flex justify-between gap-2"><span>Booking #{booking.id}</span><Badge variant="outline">{booking.bookingStatus}</Badge></div><p className="text-xs text-muted-foreground mt-1">{booking.freightDescription ?? `Freight #${booking.freightId}`}</p></div>)}</div>}{bookingsQuery.hasNextPage && <button type="button" className="mt-3 w-full rounded-lg border p-3 text-sm font-medium hover:bg-muted disabled:opacity-50" onClick={() => bookingsQuery.fetchNextPage()} disabled={bookingsQuery.isFetchingNextPage}>{bookingsQuery.isFetchingNextPage ? "Loading…" : "Load more"}</button>}</CardContent></Card>
        <Card className="rounded-xl border-border/60"><CardHeader><CardTitle className="flex items-center gap-2 text-base"><Route className="h-4 w-4 text-emerald-600" /> Trips</CardTitle></CardHeader><CardContent>{trips.length === 0 ? <p className="text-sm text-muted-foreground">No trips yet.</p> : <div className="space-y-2">{trips.map((trip: any) => <div key={trip.id} className="rounded-lg border p-3 text-sm"><div className="flex justify-between gap-2"><span>Trip #{trip.id}</span><Badge variant="outline">{trip.tripStatus ?? trip.status}</Badge></div><p className="text-xs text-muted-foreground mt-1">{trip.destination ?? "Destination not set"}</p></div>)}</div>}</CardContent></Card>
      </div>
    </div>
  );
}

function formatDate(value?: string) {
  if (!value) return "Not available";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Not available" : date.toLocaleDateString();
}
