import type { ReactNode } from "react";
import { useState } from "react";
import { Link } from "wouter";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import {
  companyRequestError,
  formatCompanyDate,
  formatCompanyMoney,
  tripSortFields,
  tripStatuses,
  tripTypes,
  type CompanyPage,
  type CompanyTrip,
} from "@/lib/company-operations";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

const PAGE_SIZE = 20;

export default function CompanyTrips({ companyId: rawCompanyId }: { companyId: string }) {
  const { memberships, membershipError, membershipRevalidating } = useAuth();
  const parsedId = Number(rawCompanyId);
  const companyId = Number.isSafeInteger(parsedId) && parsedId > 0 && String(parsedId) === rawCompanyId
    ? parsedId
    : undefined;
  const membership = companyId
    ? memberships.find(item => item.carrierCompanyId === companyId && item.status === "ACTIVE")
    : undefined;
  const canViewFinancials = membership?.role === "CARRIER_OWNER" || membership?.role === "FLEET_MANAGER";
  const [status, setStatus] = useState<string[]>([]);
  const [type, setType] = useState("");
  const [sortBy, setSortBy] = useState<(typeof tripSortFields)[number]>("startTime");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [page, setPage] = useState(0);
  const [selectedTripId, setSelectedTripId] = useState<number | null>(null);

  const trips = useQuery({
    queryKey: ["company-trips", companyId, status, type, sortBy, sortDir, page, PAGE_SIZE],
    queryFn: async () => {
      if (!companyId || !membership) throw new Error("An active company membership is required.");
      const params = new URLSearchParams({
        page: String(page),
        size: String(PAGE_SIZE),
        sortBy,
        sortDir,
      });
      status.forEach(value => params.append("status", value));
      if (type) params.set("type", type);
      const result = await api.get<CompanyPage<CompanyTrip>>(
        `/carrier-companies/${companyId}/trips?${params.toString()}`,
      );
      if (!result || !Array.isArray(result.content)
          || result.content.some(item => item.carrierCompanyId !== companyId)) {
        throw new Error("The trip response did not match the selected company.");
      }
      return result;
    },
    enabled: !!membership && !membershipRevalidating && !membershipError,
  });

  const detail = useQuery({
    queryKey: ["company-trip-detail", companyId, selectedTripId],
    queryFn: async () => {
      if (!companyId || !membership || selectedTripId === null) {
        throw new Error("An active company membership and trip are required.");
      }
      const result = await api.get<CompanyTrip>(
        `/carrier-companies/${companyId}/trips/${selectedTripId}`,
      );
      if (result.carrierCompanyId !== companyId || result.id !== selectedTripId) {
        throw new Error("The trip response did not match the selected company and trip.");
      }
      return result;
    },
    enabled: !!membership && selectedTripId !== null
      && !membershipRevalidating && !membershipError,
  });

  const records = trips.data?.content ?? [];
  const pageInfo = trips.data;

  return (
    <main className="container mx-auto max-w-6xl space-y-5 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <header className="space-y-2 border-b border-border/70 pb-5">
        <Link href={`/company/${companyId}`} className="text-sm font-medium text-muted-foreground hover:text-foreground">← Company workspace</Link>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Company trips</h1>
        <p className="text-sm text-muted-foreground">Operational progress and trip history for company #{companyId}.</p>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <fieldset className="grid gap-2 rounded-md border border-input p-3 text-sm">
          <legend className="px-1 font-medium">Trip status</legend>
          {tripStatuses.map(value => (
            <label key={value} className="flex items-center gap-2 font-normal">
              <input
                type="checkbox"
                checked={status.includes(value)}
                onChange={event => {
                  setStatus(current => event.target.checked
                    ? [...current, value]
                    : current.filter(item => item !== value));
                  setPage(0);
                }}
              />
              {statusLabel(value)}
            </label>
          ))}
        </fieldset>
        <Filter label="Trip type">
          <select value={type} onChange={event => { setType(event.target.value); setPage(0); }} className="min-h-10 rounded-md border border-input bg-background px-3 text-sm">
            <option value="">All types</option>
            {tripTypes.map(value => <option key={value} value={value}>{statusLabel(value)}</option>)}
          </select>
        </Filter>
        <Filter label="Sort by">
          <select value={sortBy} onChange={event => { setSortBy(event.target.value as (typeof tripSortFields)[number]); setPage(0); }} className="min-h-10 rounded-md border border-input bg-background px-3 text-sm">
            {tripSortFields.map(value => <option key={value} value={value}>{statusLabel(value)}</option>)}
          </select>
        </Filter>
        <Filter label="Sort direction">
          <select value={sortDir} onChange={event => { setSortDir(event.target.value as "asc" | "desc"); setPage(0); }} className="min-h-10 rounded-md border border-input bg-background px-3 text-sm">
            <option value="desc">Descending</option>
            <option value="asc">Ascending</option>
          </select>
        </Filter>
      </div>

      {membershipRevalidating && <p role="status" className="text-sm text-muted-foreground">Rechecking company access…</p>}
      {membershipError && <p role="alert" className="text-sm text-destructive">{membershipError}</p>}
      {trips.isLoading && <div className="grid gap-3 md:grid-cols-2" aria-label="Loading company trips"><Skeleton className="h-36 rounded-xl" /><Skeleton className="h-36 rounded-xl" /></div>}
      {trips.isError && (
        <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-destructive">{companyRequestError(trips.error)}</p>
          <Button type="button" variant="outline" className="min-h-10" onClick={() => void trips.refetch()}>Retry</Button>
        </div>
      )}
      {!trips.isLoading && !trips.isError && records.length === 0 && (
        <Card className="border-dashed shadow-none"><CardContent className="py-12 text-center text-sm text-muted-foreground">No trips found for the selected filters.</CardContent></Card>
      )}
      <section className="grid gap-3 md:grid-cols-2" aria-label="Company trip records">
        {records.map(trip => (
          <Card key={trip.id} className="border-border/70">
            <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0 pb-2">
              <CardTitle className="text-base">Trip #{trip.id}</CardTitle>
              <Badge variant="outline">{statusLabel(trip.tripStatus)}</Badge>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="break-words font-medium">{trip.startLocation || "Origin unavailable"} → {trip.destination || "Destination unavailable"}</p>
              <div className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
                <p>Driver: {trip.driver?.fullName || "—"}</p>
                <p>Vehicle: {trip.vehicle?.plateNumber || "—"}{trip.vehicle?.truckType ? ` · ${trip.vehicle.truckType}` : ""}</p>
                <p>Booking: {trip.booking?.id == null ? "—" : `#${trip.booking.id}`}</p>
                <p>Progress: {trip.completedStops ?? 0} / {trip.totalStops ?? 0} stops</p>
              </div>
              <p className="text-xs text-muted-foreground">Started: {formatCompanyDate(trip.startTime)}</p>
              <Button type="button" variant="outline" size="sm" onClick={() => setSelectedTripId(trip.id)}>View trip details</Button>
            </CardContent>
          </Card>
        ))}
      </section>

      {pageInfo && pageInfo.totalPages > 1 && (
        <nav className="flex items-center justify-between gap-3" aria-label="Trip pages">
          <Button type="button" variant="outline" disabled={pageInfo.first || trips.isFetching} onClick={() => setPage(current => Math.max(0, current - 1))}>Previous</Button>
          <span className="text-sm text-muted-foreground">Page {pageInfo.number + 1} of {pageInfo.totalPages}</span>
          <Button type="button" variant="outline" disabled={pageInfo.last || trips.isFetching} onClick={() => setPage(current => current + 1)}>Next</Button>
        </nav>
      )}

      <Dialog open={selectedTripId !== null} onOpenChange={open => { if (!open) setSelectedTripId(null); }}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Trip details{selectedTripId ? ` · #${selectedTripId}` : ""}</DialogTitle>
            <DialogDescription>Operational trip details. Trip lifecycle actions are not available in this company view.</DialogDescription>
          </DialogHeader>
          {detail.isLoading && <Skeleton className="h-48 w-full" />}
          {detail.isError && (
            <div role="alert" className="flex items-center justify-between gap-3 text-sm text-destructive">
              <span>{companyRequestError(detail.error)}</span>
              <Button type="button" variant="outline" size="sm" onClick={() => void detail.refetch()}>Retry</Button>
            </div>
          )}
          {detail.data && <TripDetails trip={detail.data} canViewFinancials={canViewFinancials} />}
        </DialogContent>
      </Dialog>
    </main>
  );
}

function TripDetails({ trip, canViewFinancials }: { trip: CompanyTrip; canViewFinancials: boolean }) {
  const details: [string, string][] = [
    ["Status", statusLabel(trip.tripStatus)],
    ["Type", statusLabel(trip.tripType)],
    ["Route", `${trip.startLocation || "—"} → ${trip.destination || "—"}`],
    ["Booking", trip.booking?.id == null ? "—" : `#${trip.booking.id} · ${statusLabel(trip.booking.status)}`],
    ["Driver", trip.driver?.fullName || "—"],
    ["Vehicle", `${trip.vehicle?.plateNumber || "—"}${trip.vehicle?.truckType ? ` · ${trip.vehicle.truckType}` : ""}${trip.vehicle?.loadCapacity == null ? "" : ` · ${trip.vehicle.loadCapacity} tons capacity`}`],
    ["Shipper", trip.shipper?.fullName || "—"],
    ["Cargo", `${trip.cargo?.materialType || "—"}${trip.cargo?.serviceType ? ` · ${statusLabel(trip.cargo.serviceType)}` : ""}`],
    ["Weight", trip.cargo?.weightTons == null ? "—" : `${trip.cargo.weightTons} tons`],
    ["Volume", trip.cargo?.volumeM3 == null ? "—" : `${trip.cargo.volumeM3} m³`],
    ["Urgency", statusLabel(trip.cargo?.urgencyLevel)],
    ["Progress", `${trip.completedStops ?? 0} / ${trip.totalStops ?? 0} stops`],
    ["Distance", trip.routeInfo?.totalDistanceKm == null ? "—" : `${trip.routeInfo.totalDistanceKm} km`],
    ["Completion", trip.routeInfo?.completionPercentage == null ? "—" : `${trip.routeInfo.completionPercentage}%`],
    ["GPS points", trip.routeInfo?.gpsPointCount == null ? "—" : String(trip.routeInfo.gpsPointCount)],
    ["Average speed", trip.routeInfo?.averageSpeed == null ? "—" : `${trip.routeInfo.averageSpeed} km/h`],
    ["Last GPS update", formatCompanyDate(trip.routeInfo?.lastGpsUpdate)],
    ["Started", formatCompanyDate(trip.startTime)],
    ["Ended", formatCompanyDate(trip.endTime)],
    ["Delivery confirmed", formatCompanyDate(trip.shipperDeliveryConfirmedAt)],
    ["Created", formatCompanyDate(trip.createdAt)],
    ["Updated", formatCompanyDate(trip.updatedAt)],
  ];
  const bookingPrice = canViewFinancials ? formatCompanyMoney(trip.booking?.estimatedPrice) : null;
  const commission = canViewFinancials ? formatCompanyMoney(trip.booking?.commissionFee) : null;

  return (
    <div className="space-y-5">
      <dl className="grid gap-3 sm:grid-cols-2">
        {details.map(([label, value]) => <DetailField key={label} label={label} value={value} />)}
        {bookingPrice && <DetailField label="Booking estimated price" value={bookingPrice} />}
        {commission && <DetailField label="Commission" value={commission} />}
      </dl>
      {!!trip.stops?.length && (
        <section className="space-y-2">
          <h3 className="font-semibold">Stops</h3>
          <div className="space-y-2">
            {trip.stops.map(stop => (
              <article key={stop.id} className="rounded-lg border border-border/60 p-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-medium">{stop.stopOrder == null ? "" : `${stop.stopOrder}. `}{stop.locationName || "Stop location unavailable"}</p>
                  <Badge variant="outline">{statusLabel(stop.stopStatus)}</Badge>
                </div>
                {stop.cargoInfo?.materialType && <p className="mt-1 text-muted-foreground">{stop.cargoInfo.materialType}{stop.cargoInfo.weightTons == null ? "" : ` · ${stop.cargoInfo.weightTons} tons`}</p>}
                <p className="mt-1 text-xs text-muted-foreground">Arrival: {formatCompanyDate(stop.arrivalTime)} · Loading: {formatCompanyDate(stop.loadingTime)} · Completed: {formatCompanyDate(stop.completionTime)}</p>
                {canViewFinancials && stop.agreedPrice != null && <p className="mt-1 font-medium">{formatCompanyMoney(stop.agreedPrice)}</p>}
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function Filter({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-1 text-sm font-medium">{label}{children}</label>;
}

function DetailField({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0 rounded-lg border border-border/60 p-3"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm font-medium">{value}</dd></div>;
}

function statusLabel(value: string | null | undefined): string {
  return value ? value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : "Unknown";
}
