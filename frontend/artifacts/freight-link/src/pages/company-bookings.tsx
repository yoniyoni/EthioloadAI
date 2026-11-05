import { useState } from "react";
import { Link } from "wouter";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import {
  bookingStatuses,
  companyRequestError,
  formatCompanyDate,
  formatCompanyMoney,
  type CompanyBooking,
  type CompanyPage,
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

export default function CompanyBookings({ companyId: rawCompanyId }: { companyId: string }) {
  const { memberships, membershipError, membershipRevalidating } = useAuth();
  const parsedId = Number(rawCompanyId);
  const companyId = Number.isSafeInteger(parsedId) && parsedId > 0 && String(parsedId) === rawCompanyId
    ? parsedId
    : undefined;
  const membership = companyId
    ? memberships.find(item => item.carrierCompanyId === companyId && item.status === "ACTIVE")
    : undefined;
  const canViewFinancials = membership?.role === "CARRIER_OWNER" || membership?.role === "FLEET_MANAGER";
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [selectedBookingId, setSelectedBookingId] = useState<number | null>(null);

  const bookings = useQuery({
    queryKey: ["company-bookings", companyId, status, page, PAGE_SIZE],
    queryFn: async () => {
      if (!companyId || !membership) throw new Error("An active company membership is required.");
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (status) params.set("status", status);
      const result = await api.get<CompanyPage<CompanyBooking>>(
        `/carrier-companies/${companyId}/bookings?${params.toString()}`,
      );
      if (!result || !Array.isArray(result.content)
          || result.content.some(item => item.carrierCompanyId !== companyId)) {
        throw new Error("The booking response did not match the selected company.");
      }
      return result;
    },
    enabled: !!membership && !membershipRevalidating && !membershipError,
  });

  const detail = useQuery({
    queryKey: ["company-booking-detail", companyId, selectedBookingId],
    queryFn: async () => {
      if (!companyId || !membership || selectedBookingId === null) {
        throw new Error("An active company membership and booking are required.");
      }
      const result = await api.get<CompanyBooking>(
        `/carrier-companies/${companyId}/bookings/${selectedBookingId}`,
      );
      if (result.carrierCompanyId !== companyId || result.id !== selectedBookingId) {
        throw new Error("The booking response did not match the selected company and booking.");
      }
      return result;
    },
    enabled: !!membership && selectedBookingId !== null
      && !membershipRevalidating && !membershipError,
  });

  const records = bookings.data?.content ?? [];
  const pageInfo = bookings.data;

  return (
    <main className="container mx-auto max-w-6xl space-y-5 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <header className="space-y-2 border-b border-border/70 pb-5">
        <Link href={`/company/${companyId}`} className="text-sm font-medium text-muted-foreground hover:text-foreground">← Company workspace</Link>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Company bookings</h1>
        <p className="text-sm text-muted-foreground">Bookings and delivery details for company #{companyId}.</p>
      </header>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <label className="grid gap-1 text-sm font-medium sm:max-w-xs">
          Booking status
          <select
            value={status}
            onChange={event => { setStatus(event.target.value); setPage(0); }}
            className="min-h-10 rounded-md border border-input bg-background px-3"
          >
            <option value="">All statuses</option>
            {bookingStatuses.map(value => <option key={value} value={value}>{statusLabel(value)}</option>)}
          </select>
        </label>
        {pageInfo && <p className="text-sm text-muted-foreground">{pageInfo.totalElements} bookings</p>}
      </div>

      {membershipRevalidating && <p role="status" className="text-sm text-muted-foreground">Rechecking company access…</p>}
      {membershipError && <p role="alert" className="text-sm text-destructive">{membershipError}</p>}
      {bookings.isLoading && <div className="space-y-3" aria-label="Loading company bookings"><Skeleton className="h-28 w-full rounded-xl" /><Skeleton className="h-28 w-full rounded-xl" /></div>}
      {bookings.isError && (
        <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-destructive">{companyRequestError(bookings.error)}</p>
          <Button type="button" variant="outline" className="min-h-10" onClick={() => void bookings.refetch()}>Retry</Button>
        </div>
      )}
      {!bookings.isLoading && !bookings.isError && records.length === 0 && (
        <Card className="border-dashed shadow-none"><CardContent className="py-12 text-center text-sm text-muted-foreground">No bookings found for this status.</CardContent></Card>
      )}
      <section className="space-y-3" aria-label="Company booking records">
        {records.map(booking => (
          <Card key={booking.id} className="border-border/70">
            <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0 pb-2">
              <CardTitle className="text-base">Booking #{booking.id}</CardTitle>
              <Badge variant="outline">{statusLabel(booking.bookingStatus)}</Badge>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="font-medium">{booking.freightDescription || `Freight #${booking.freightId}`}</p>
              <div className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2 lg:grid-cols-3">
                <p>Shipper: {booking.shipperName || "—"}</p>
                <p>Driver: {booking.driverName || "Unassigned"}</p>
                <p>Vehicle: {booking.vehiclePlateNumber || "—"}{booking.vehicleType ? ` · ${booking.vehicleType}` : ""}</p>
                <p>Freight: #{booking.freightId}</p>
                <p>Trip: {booking.tripId == null ? "—" : `#${booking.tripId} · ${statusLabel(booking.tripStatus)}`}</p>
                <p>Updated: {formatCompanyDate(booking.updatedAt)}</p>
              </div>
              {canViewFinancials && booking.estimatedPrice != null && (
                <p className="text-sm font-medium">Estimated price: {formatCompanyMoney(booking.estimatedPrice)}</p>
              )}
              {canViewFinancials && booking.commissionFee != null && (
                <p className="text-sm font-medium">Commission: {formatCompanyMoney(booking.commissionFee)}</p>
              )}
              <Button type="button" variant="outline" size="sm" onClick={() => setSelectedBookingId(booking.id)}>View details</Button>
            </CardContent>
          </Card>
        ))}
      </section>

      {pageInfo && pageInfo.totalPages > 1 && (
        <nav className="flex items-center justify-between gap-3" aria-label="Booking pages">
          <Button type="button" variant="outline" disabled={pageInfo.first || bookings.isFetching} onClick={() => setPage(current => Math.max(0, current - 1))}>Previous</Button>
          <span className="text-sm text-muted-foreground">Page {pageInfo.number + 1} of {pageInfo.totalPages}</span>
          <Button type="button" variant="outline" disabled={pageInfo.last || bookings.isFetching} onClick={() => setPage(current => current + 1)}>Next</Button>
        </nav>
      )}

      <Dialog open={selectedBookingId !== null} onOpenChange={open => { if (!open) setSelectedBookingId(null); }}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Booking details{selectedBookingId ? ` · #${selectedBookingId}` : ""}</DialogTitle>
            <DialogDescription>Company-scoped booking and related freight information.</DialogDescription>
          </DialogHeader>
          {detail.isLoading && <Skeleton className="h-40 w-full" />}
          {detail.isError && (
            <div role="alert" className="flex items-center justify-between gap-3 text-sm text-destructive">
              <span>{companyRequestError(detail.error)}</span>
              <Button type="button" variant="outline" size="sm" onClick={() => void detail.refetch()}>Retry</Button>
            </div>
          )}
          {detail.data && <BookingDetails booking={detail.data} canViewFinancials={canViewFinancials} />}
        </DialogContent>
      </Dialog>
    </main>
  );
}

function BookingDetails({ booking, canViewFinancials }: { booking: CompanyBooking; canViewFinancials: boolean }) {
  const fields: [string, string][] = [
    ["Status", statusLabel(booking.bookingStatus)],
    ["Freight", booking.freightDescription || `#${booking.freightId}`],
    ["Shipper", booking.shipperName || "—"],
    ["Driver", booking.driverName || "Unassigned"],
    ["Vehicle", booking.vehiclePlateNumber || "—"],
    ["Vehicle type", booking.vehicleType || "—"],
    ["Trip", booking.tripId == null ? "—" : `#${booking.tripId} · ${statusLabel(booking.tripStatus)}`],
    ["Created", formatCompanyDate(booking.createdAt)],
    ["Updated", formatCompanyDate(booking.updatedAt)],
  ];
  return (
    <dl className="grid gap-3 sm:grid-cols-2">
      {fields.map(([label, value]) => <DetailField key={label} label={label} value={value} />)}
      {canViewFinancials && booking.estimatedPrice != null && <DetailField label="Estimated price" value={formatCompanyMoney(booking.estimatedPrice) ?? "—"} />}
      {canViewFinancials && booking.commissionFee != null && <DetailField label="Commission" value={formatCompanyMoney(booking.commissionFee) ?? "—"} />}
    </dl>
  );
}

function DetailField({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0 rounded-lg border border-border/60 p-3"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm font-medium">{value}</dd></div>;
}

function statusLabel(value: string | null | undefined): string {
  return value ? value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase()) : "Unknown";
}
