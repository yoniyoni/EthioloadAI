import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Activity, Gavel, RefreshCw, Route, Truck, type LucideIcon } from "lucide-react";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  getHistoryQueryPlan,
  getHistorySectionState,
  historyPageHasNext,
  retryHistoryQuery,
} from "./history-model.js";

type HistoryPage<T> = {
  content: T[];
  number: number;
  hasNext?: boolean;
  last?: boolean;
};

type BookingRecord = {
  id: number;
  freightId?: number | null;
  driverName?: string | null;
  freightDescription?: string | null;
  vehiclePlateNumber?: string | null;
  bookingStatus?: string | null;
  updatedAt?: string | null;
};

type TripRecord = {
  id: number;
  tripStatus?: string | null;
  status?: string | null;
  destination?: string | null;
  startTime?: string | null;
};

type BidRecord = {
  id: number;
  amount?: number | string | null;
  status?: string | null;
  vehiclePlateNumber?: string | null;
};

type FreightRecord = {
  id: number;
  cargoType?: string | null;
  materialType?: string | null;
  description?: string | null;
  status?: string | null;
  pickupLocation?: string | null;
  origin?: string | null;
  deliveryLocation?: string | null;
  destination?: string | null;
  updatedAt?: string | null;
  createdAt?: string | null;
};

type ShipperHistoryRecord = {
  freight: FreightRecord;
  booking?: BookingRecord | null;
  trip?: (TripRecord & { driver?: { fullName?: string | null } | null; updatedAt?: string | null }) | null;
  payment?: { paymentStatus?: string | null } | null;
};

type QueryPage<T> = {
  content?: T[];
};

type FleetHistorySectionProps<T> = {
  title: string;
  icon: LucideIcon;
  items: T[] | undefined;
  isError: boolean;
  isFetching: boolean;
  error: Error | null;
  emptyMessage: string;
  retry: () => void;
  renderItem: (item: T) => ReactNode;
  hasNextPage?: boolean;
  isFetchingNextPage?: boolean;
  loadMore?: () => void;
  loadMoreLabel?: string;
};

export default function History() {
  const { user } = useAuth();
  const queryPlan = getHistoryQueryPlan(user?.role);

  const bidsQuery = useQuery({
    queryKey: ["my-bids"],
    queryFn: () => api.get<{ content?: BidRecord[] } | BidRecord[]>("/driver/bids"),
    enabled: queryPlan.driver,
  });
  const shipperHistoryQuery = useInfiniteQuery({
    queryKey: ["shipper-freight-history"],
    queryFn: ({ pageParam }) => api.get<HistoryPage<ShipperHistoryRecord>>(
      `/history/shipper/freight?page=${pageParam}&size=20`,
    ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => historyPageHasNext(lastPage) ? lastPage.number + 1 : undefined,
    enabled: queryPlan.shipper,
  });
  const bookingsQuery = useInfiniteQuery({
    queryKey: ["my-bookings-page"],
    queryFn: ({ pageParam }) => api.get<HistoryPage<BookingRecord>>(
      `/bookings/page?page=${pageParam}&size=20`,
    ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => historyPageHasNext(lastPage) ? lastPage.number + 1 : undefined,
    enabled: queryPlan.driver || queryPlan.fleetOwner,
  });
  const driverTripsQuery = useQuery({
    queryKey: ["my-trips"],
    queryFn: () => api.get<QueryPage<TripRecord> | TripRecord[]>("/trips?size=100"),
    enabled: queryPlan.driver,
  });
  const fleetTripsQuery = useInfiniteQuery({
    queryKey: ["fleet-owner-history-trips", user?.id],
    queryFn: ({ pageParam }) => api.get<HistoryPage<TripRecord>>(
      `/trips?page=${pageParam}&size=100`,
    ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => historyPageHasNext(lastPage) ? lastPage.number + 1 : undefined,
    enabled: queryPlan.fleetOwner,
  });

  const bidResponse = bidsQuery.data;
  const bids = Array.isArray(bidResponse)
    ? bidResponse
    : bidResponse?.content ?? [];
  const shipperRows = shipperHistoryQuery.data?.pages.flatMap((page) => page.content);
  const bookingRows = bookingsQuery.data?.pages.flatMap((page) => page.content);
  const bookings = bookingRows
    ? Array.from(new Map(bookingRows.map((booking) => [booking.id, booking])).values())
    : undefined;
  const driverTripResponse = driverTripsQuery.data;
  const driverTrips = Array.isArray(driverTripResponse)
    ? driverTripResponse
    : driverTripResponse?.content ?? [];
  const fleetTrips = fleetTripsQuery.data?.pages.flatMap((page) => page.content);

  if (!queryPlan.driver && !queryPlan.shipper && !queryPlan.fleetOwner) {
    return (
      <div className="container mx-auto max-w-5xl px-4 py-16 text-center">
        <p className="text-lg font-medium">History is available from a shipper, driver, or fleet owner account.</p>
      </div>
    );
  }

  if (queryPlan.shipper) {
    return (
      <div className="container mx-auto max-w-6xl px-4 py-8">
        <PageHeading
          title="Shipment History"
          description="Freight, bookings, trips, and payments returned by Spring Boot"
        />
        <HistoryListSection
          title="Shipments"
          icon={Route}
          items={shipperRows}
          isError={shipperHistoryQuery.isError}
          isFetching={shipperHistoryQuery.isFetching}
          error={shipperHistoryQuery.error}
          emptyMessage="No shipment history yet. Your freight and delivery activity will appear here."
          retry={() => { void retryHistoryQuery(shipperHistoryQuery); }}
          hasNextPage={shipperHistoryQuery.hasNextPage}
          isFetchingNextPage={shipperHistoryQuery.isFetchingNextPage}
          loadMore={() => { void shipperHistoryQuery.fetchNextPage(); }}
          loadMoreLabel="Load more shipments"
          renderItem={({ freight, booking, trip, payment }) => {
            const status = trip?.tripStatus ?? booking?.bookingStatus ?? freight.status ?? "Status unavailable";
            return (
              <article key={freight.id} className="min-w-0 rounded-lg border border-border/60 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="break-words font-medium">
                      {freight.cargoType ?? freight.materialType ?? freight.description ?? `Shipment #${freight.id}`}
                    </p>
                    <p className="break-words text-sm text-muted-foreground">
                      {freight.pickupLocation ?? freight.origin ?? "Origin unavailable"}
                      <span aria-hidden="true"> → </span>
                      {freight.deliveryLocation ?? freight.destination ?? "Destination unavailable"}
                    </p>
                  </div>
                  <Badge variant="outline">{status}</Badge>
                </div>
                <dl className="mt-4 grid min-w-0 gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
                  <HistoryDetail label="Booking">
                    {booking ? `#${booking.id} · ${booking.bookingStatus ?? "Status unavailable"}` : "Not available"}
                  </HistoryDetail>
                  <HistoryDetail label="Driver">
                    {trip?.driver?.fullName ?? booking?.driverName ?? "Not assigned"}
                  </HistoryDetail>
                  <HistoryDetail label="Payment">{payment?.paymentStatus ?? "Not available"}</HistoryDetail>
                  <HistoryDetail label="Updated">
                    {formatDate(trip?.updatedAt ?? booking?.updatedAt ?? freight.updatedAt ?? freight.createdAt)}
                  </HistoryDetail>
                </dl>
                {trip?.id && (
                  <div className="mt-4">
                    <a
                      className="inline-flex min-h-10 items-center text-sm font-medium text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      href={`/tracking/${freight.id}?tripId=${trip.id}`}
                    >
                      Open live tracking
                    </a>
                  </div>
                )}
              </article>
            );
          }}
        />
      </div>
    );
  }

  if (queryPlan.fleetOwner) {
    return (
      <div className="container mx-auto max-w-6xl px-4 py-8">
        <PageHeading
          title="Fleet History"
          description="Bookings and trips for your approved, active company memberships"
        />
        <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-2">
          <HistoryListSection
            title="Fleet bookings"
            icon={Truck}
            items={bookings}
            isError={bookingsQuery.isError}
            isFetching={bookingsQuery.isFetching}
            error={bookingsQuery.error}
            emptyMessage="No bookings are available for your approved companies yet."
            retry={() => { void retryHistoryQuery(bookingsQuery); }}
            hasNextPage={bookingsQuery.hasNextPage}
            isFetchingNextPage={bookingsQuery.isFetchingNextPage}
            loadMore={() => { void bookingsQuery.fetchNextPage(); }}
            loadMoreLabel="Load more bookings"
            renderItem={(booking) => (
              <article key={booking.id} className="min-w-0 rounded-lg border border-border/60 p-3 text-sm">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <span className="font-medium">Booking #{booking.id}</span>
                  <Badge variant="outline">{booking.bookingStatus ?? "Status unavailable"}</Badge>
                </div>
                <p className="mt-2 break-words text-muted-foreground">
                  {booking.driverName ?? "Driver unavailable"}
                  <span aria-hidden="true"> · </span>
                  {booking.freightDescription ?? (booking.freightId ? `Freight #${booking.freightId}` : "Freight unavailable")}
                </p>
                {booking.vehiclePlateNumber && (
                  <p className="mt-1 break-words text-xs text-muted-foreground">
                    Vehicle {booking.vehiclePlateNumber}
                  </p>
                )}
              </article>
            )}
          />
          <HistoryListSection
            title="Fleet trips"
            icon={Route}
            items={fleetTrips}
            isError={fleetTripsQuery.isError}
            isFetching={fleetTripsQuery.isFetching}
            error={fleetTripsQuery.error}
            emptyMessage="No trips are available for your approved companies yet."
            retry={() => { void retryHistoryQuery(fleetTripsQuery); }}
            hasNextPage={fleetTripsQuery.hasNextPage}
            isFetchingNextPage={fleetTripsQuery.isFetchingNextPage}
            loadMore={() => { void fleetTripsQuery.fetchNextPage(); }}
            loadMoreLabel="Load more trips"
            renderItem={(trip) => (
              <article key={trip.id} className="min-w-0 rounded-lg border border-border/60 p-3 text-sm">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <span className="font-medium">Trip #{trip.id}</span>
                  <Badge variant="outline">{trip.tripStatus ?? trip.status ?? "Status unavailable"}</Badge>
                </div>
                <p className="mt-2 break-words text-muted-foreground">
                  {trip.destination ?? "Destination unavailable"}
                </p>
                {trip.startTime && (
                  <p className="mt-1 text-xs text-muted-foreground">Started {formatDate(trip.startTime)}</p>
                )}
              </article>
            )}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <PageHeading
        title="Driver History"
        description="Bids, bookings, and trips returned by Spring Boot"
      />
      <div className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-3">
        <HistoryListSection
          title="Bids"
          icon={Gavel}
          items={bidsQuery.data ? bids : undefined}
          isError={bidsQuery.isError}
          isFetching={bidsQuery.isFetching}
          error={bidsQuery.error}
          emptyMessage="No bids yet."
          retry={() => { void bidsQuery.refetch(); }}
          renderItem={(bid) => (
            <article key={bid.id} className="min-w-0 rounded-lg border border-border/60 p-3 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <span>ETB {Number(bid.amount ?? 0).toLocaleString()}</span>
                <Badge variant="outline">{bid.status ?? "Status unavailable"}</Badge>
              </div>
              <p className="mt-2 break-words text-xs text-muted-foreground">
                {bid.vehiclePlateNumber ?? "Vehicle not set"}
              </p>
            </article>
          )}
        />
        <HistoryListSection
          title="Bookings"
          icon={Truck}
          items={bookings}
          isError={bookingsQuery.isError}
          isFetching={bookingsQuery.isFetching}
          error={bookingsQuery.error}
          emptyMessage="No bookings yet."
          retry={() => { void retryHistoryQuery(bookingsQuery); }}
          hasNextPage={bookingsQuery.hasNextPage}
          isFetchingNextPage={bookingsQuery.isFetchingNextPage}
          loadMore={() => { void bookingsQuery.fetchNextPage(); }}
          loadMoreLabel="Load more bookings"
          renderItem={(booking) => (
            <article key={booking.id} className="min-w-0 rounded-lg border border-border/60 p-3 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <span className="font-medium">Booking #{booking.id}</span>
                <Badge variant="outline">{booking.bookingStatus ?? "Status unavailable"}</Badge>
              </div>
              <p className="mt-2 break-words text-xs text-muted-foreground">
                {booking.freightDescription ?? (booking.freightId ? `Freight #${booking.freightId}` : "Freight unavailable")}
              </p>
            </article>
          )}
        />
        <HistoryListSection
          title="Trips"
          icon={Route}
          items={driverTripsQuery.data ? driverTrips : undefined}
          isError={driverTripsQuery.isError}
          isFetching={driverTripsQuery.isFetching}
          error={driverTripsQuery.error}
          emptyMessage="No trips yet."
          retry={() => { void driverTripsQuery.refetch(); }}
          renderItem={(trip) => (
            <article key={trip.id} className="min-w-0 rounded-lg border border-border/60 p-3 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <span className="font-medium">Trip #{trip.id}</span>
                <Badge variant="outline">{trip.tripStatus ?? trip.status ?? "Status unavailable"}</Badge>
              </div>
              <p className="mt-2 break-words text-xs text-muted-foreground">
                {trip.destination ?? "Destination not set"}
              </p>
            </article>
          )}
        />
      </div>
    </div>
  );
}

function PageHeading({ title, description }: { title: string; description: string }) {
  return (
    <header className="mb-6">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
      <p className="mt-1 text-sm text-muted-foreground sm:text-base">{description}</p>
    </header>
  );
}

function HistoryListSection<T>({
  title,
  icon: Icon,
  items,
  isError,
  isFetching,
  error,
  emptyMessage,
  retry,
  renderItem,
  hasNextPage,
  isFetchingNextPage = false,
  loadMore,
  loadMoreLabel,
}: FleetHistorySectionProps<T>) {
  const state = getHistorySectionState({ items, isError, isFetching });
  const errorMessage = error?.message || `Unable to load ${title.toLowerCase()}.`;

  return (
    <section aria-label={title} className="min-w-0">
      <Card className="h-full min-w-0 overflow-hidden rounded-xl border-border/60">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Icon aria-hidden="true" className="h-4 w-4 shrink-0 text-primary" />
            {title}
          </CardTitle>
        </CardHeader>
        <CardContent className="min-w-0 space-y-3">
          {state === "loading" && <HistorySkeleton label={`Loading ${title.toLowerCase()}`} />}
          {state === "error" && (
            <HistoryErrorNotice message={errorMessage} onRetry={retry} disabled={isFetching} />
          )}
          {isError && state !== "error" && (
            <HistoryErrorNotice message={errorMessage} onRetry={retry} disabled={isFetching} />
          )}
          {state === "empty" && (
            <p role="status" className="rounded-lg border border-dashed border-border/70 px-4 py-8 text-center text-sm text-muted-foreground">
              {emptyMessage}
            </p>
          )}
          {state === "records" && (
            <div className="space-y-2">{items?.map(renderItem)}</div>
          )}
          {hasNextPage && loadMore && (
            <Button
              type="button"
              variant="outline"
              className="min-h-10 w-full"
              onClick={loadMore}
              disabled={isFetchingNextPage}
              aria-label={loadMoreLabel}
            >
              {isFetchingNextPage && <RefreshCw aria-hidden="true" className="h-4 w-4 animate-spin" />}
              {isFetchingNextPage ? "Loading…" : loadMoreLabel}
            </Button>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

function HistorySkeleton({ label }: { label: string }) {
  return (
    <div role="status" aria-label={label} aria-live="polite" className="space-y-3">
      <Skeleton className="h-16 w-full rounded-lg" />
      <Skeleton className="h-16 w-full rounded-lg" />
      <span className="sr-only">{label}</span>
    </div>
  );
}

function HistoryErrorNotice({
  message,
  onRetry,
  disabled,
}: {
  message: string;
  onRetry: () => void;
  disabled: boolean;
}) {
  return (
    <div role="alert" className="flex flex-col gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 sm:flex-row sm:items-center sm:justify-between">
      <p className="break-words text-sm text-destructive">{message}</p>
      <Button
        type="button"
        variant="outline"
        className="min-h-10 shrink-0"
        onClick={onRetry}
        disabled={disabled}
      >
        <RefreshCw aria-hidden="true" className="h-4 w-4" />
        Retry
      </Button>
    </div>
  );
}

function HistoryDetail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words">{children}</dd>
    </div>
  );
}

function formatDate(value?: string | null) {
  if (!value) return "Not available";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Not available" : date.toLocaleDateString();
}
