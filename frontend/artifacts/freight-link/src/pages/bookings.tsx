import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "wouter";
import { api } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Loader2, Play, Route } from "lucide-react";

type BookingRecord = {
  id: number;
  freightId: number;
  driverId?: number | null;
  driverName?: string | null;
  freightDescription?: string | null;
  vehiclePlateNumber?: string | null;
  bookingStatus?: string | null;
  tripId?: number | null;
  estimatedPrice?: number | null;
};

type TripRecord = {
  id: number;
  startTime?: string | null;
  tripStatus?: string | null;
  booking?: { id?: number | null } | null;
  cargo?: { id?: number | null } | null;
};

function DriverTripAction({ booking, currentUserId }: { booking: BookingRecord; currentUserId: number }) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const tripId = booking.tripId ?? null;
  const tripQuery = useQuery({
    queryKey: ["trip-detail", tripId],
    queryFn: () => api.get<TripRecord>(`/trips/${tripId}`),
    enabled: tripId !== null,
  });

  const startMutation = useMutation({
    mutationFn: async () => {
      let resolvedTripId = tripId;
      if (resolvedTripId === null) {
        const createdTrip = await api.post<TripRecord>("/trips", { bookingId: booking.id });
        if (!Number.isSafeInteger(createdTrip.id) || createdTrip.id <= 0) {
          throw new Error("Spring Boot did not return a valid trip ID.");
        }
        resolvedTripId = createdTrip.id;
      }

      const startedTrip = await api.post<TripRecord>(`/trips/${resolvedTripId}/start`, {});
      if (startedTrip.id !== resolvedTripId || !startedTrip.startTime) {
        throw new Error("Spring Boot did not confirm that the trip started.");
      }
      return resolvedTripId;
    },
    onSuccess: (startedTripId) => {
      toast({ title: "Trip started", description: "Open live tracking to view recorded GPS updates." });
      void queryClient.invalidateQueries({ queryKey: ["trip-detail", startedTripId] });
    },
    onError: (error: Error) => {
      toast({ title: "Unable to start trip", description: error.message, variant: "destructive" });
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["my-bookings"] });
      if (tripId !== null) {
        void queryClient.invalidateQueries({ queryKey: ["trip-detail", tripId] });
      }
    },
  });

  const assignedToCurrentDriver = booking.driverId === currentUserId;
  const isAccepted = booking.bookingStatus?.toLowerCase() === "accepted";
  if (!assignedToCurrentDriver || !isAccepted) return null;

  const tripMatchesBooking = tripQuery.data?.booking?.id === booking.id
    && (tripQuery.data?.cargo?.id == null || tripQuery.data?.cargo?.id === booking.freightId);
  const started = Boolean(tripQuery.data?.startTime);
  const canStart = tripId === null
    || (tripMatchesBooking
      && !started
      && tripQuery.data?.tripStatus?.toUpperCase() === "ONGOING");

  return (
    <div className="mt-3 flex flex-wrap items-center gap-2">
      {canStart && (
        <Button
          type="button"
          size="sm"
          onClick={() => startMutation.mutate()}
          disabled={startMutation.isPending || (tripId !== null && tripQuery.isLoading)}
        >
          {startMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
          Start Trip
        </Button>
      )}
      {started && tripMatchesBooking && (
        <Link
          href={`/tracking/${booking.freightId}?tripId=${tripId}`}
          className="inline-flex min-h-8 items-center gap-2 text-sm font-medium text-primary hover:underline"
        >
          <Route className="h-4 w-4" /> Open live tracking
        </Link>
      )}
      {tripId !== null && tripQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Checking the trip state…</p>
      )}
      {tripId !== null && tripQuery.isError && (
        <div role="alert" className="flex items-center gap-2 text-sm text-destructive">
          <span>Unable to verify the trip state.</span>
          <Button type="button" variant="outline" size="sm" onClick={() => void tripQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}
      {tripId !== null && tripQuery.data && !tripMatchesBooking && (
        <p role="alert" className="text-sm text-destructive">The trip does not match this booking.</p>
      )}
    </div>
  );
}

export default function Bookings() {
  const { user } = useAuth();
  const { data, isLoading, isError, hasNextPage, isFetchingNextPage, fetchNextPage } = useInfiniteQuery({
    queryKey: ["my-bookings"],
    queryFn: ({ pageParam }) => api.get<{ content: BookingRecord[]; number: number; hasNext: boolean }>(
      `/bookings/page?page=${pageParam}&size=20`
    ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.number + 1 : undefined,
  });
  const bookings = Array.from(new Map(
    data?.pages.flatMap((page) => page.content).map((booking) => [booking.id, booking]) ?? []
  ).values());

  return (
    <div className="container mx-auto max-w-5xl px-4 py-8">
      <h1 className="text-3xl font-bold tracking-tight">Bookings</h1>
      <p className="text-muted-foreground mt-1 mb-6">Bookings visible to your account</p>
      {isLoading && <Skeleton className="h-32 w-full rounded-xl" />}
      {isError && <p className="text-sm text-destructive">Unable to load bookings.</p>}
      {!isLoading && !isError && bookings.length === 0 && <p className="text-muted-foreground py-16 text-center">No bookings yet.</p>}
      <div className="space-y-3">{bookings.map((booking) => <Card key={booking.id} className="rounded-xl border-border/60"><CardHeader className="pb-2"><div className="flex items-center justify-between"><CardTitle className="text-base">Booking #{booking.id}</CardTitle><Badge variant="outline">{booking.bookingStatus}</Badge></div></CardHeader><CardContent><p>{booking.freightDescription ?? `Freight #${booking.freightId}`}</p><p className="text-sm text-muted-foreground mt-1">Driver: {booking.driverName ?? "Unassigned"} · Vehicle: {booking.vehiclePlateNumber ?? "—"}</p><p className="font-semibold mt-2">ETB {Number(booking.estimatedPrice ?? 0).toLocaleString()}</p><Link href={`/payment/${booking.freightId}`} className="text-sm text-primary hover:underline">View payment</Link>{user?.role === "driver" && <DriverTripAction booking={booking} currentUserId={user.id} />}</CardContent></Card>)}
        {hasNextPage && <button type="button" className="w-full rounded-lg border p-3 text-sm font-medium hover:bg-muted disabled:opacity-50" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>{isFetchingNextPage ? "Loading…" : "Load more"}</button>}
      </div>
    </div>
  );
}
