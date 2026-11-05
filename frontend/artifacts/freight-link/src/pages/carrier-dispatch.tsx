/**
 * carrier-dispatch.tsx — Phase 6 carrier internal dispatch management page.
 *
 * Accessible to: CARRIER_OWNER, FLEET_MANAGER, DISPATCHER
 *
 * Features:
 *   • List all shipments awarded to the authenticated carrier company
 *   • For each awarded shipment: show freight details, agreed amount, dispatch status
 *   • Dispatch an eligible driver + vehicle to an awarded freight
 *   • View dispatch history for a freight (accepted, rejected, cancelled)
 *   • Cancel a pending dispatch assignment
 *
 * Backend API:
 *   GET  /carrier-companies/{companyId}/awarded-freights
 *   POST /carrier-companies/{companyId}/freight/{freightId}/dispatch
 *   GET  /carrier-companies/{companyId}/dispatches
 *   POST /carrier-companies/{companyId}/dispatches/{dispatchId}/cancel
 *   GET  /carrier-companies/{companyId}/freight/{freightId}/dispatches   (history)
 *   GET  /carrier-companies/{companyId}/fleet/drivers                    (eligible drivers)
 *   GET  /carrier-companies/{companyId}/fleet/vehicles                   (eligible vehicles)
 */

import { useState } from "react";
import { Link, useParams } from "wouter";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import {
  companyRequestError,
  formatCompanyDate,
  formatCompanyMoney,
  type AwardedFreight,
  type CompanyDispatch,
  type CompanyPage,
} from "@/lib/company-operations";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Alert,
  AlertDescription,
} from "@/components/ui/alert";
import {
  Truck,
  User,
  MapPin,
  DollarSign,
  Clock,
  CheckCircle,
  XCircle,
  AlertCircle,
  RefreshCw,
} from "lucide-react";

function isAccessDeniedOrNotFound(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 403 || error.status === 404);
}

// ── Types ────────────────────────────────────────────────────────────────────

interface Driver {
  id: number;
  carrierDriverId: number;
  driverUserId: number;
  fullName: string;
  availability: string;
  status: string;
}

interface Vehicle {
  id: number;
  truckType: string;
  plateNumber: string;
  capacity: number;
  availabilityStatus: string;
  approvalStatus: string;
}

// ── Status badge helpers ─────────────────────────────────────────────────────

const statusColor: Record<string, string> = {
  ASSIGNED:        "bg-yellow-100 text-yellow-800",
  DRIVER_ACCEPTED: "bg-green-100 text-green-800",
  DRIVER_REJECTED: "bg-red-100 text-red-800",
  CANCELLED:       "bg-gray-100 text-gray-600",
  SUPERSEDED:      "bg-gray-100 text-gray-600",
};

const DispatchStatusBadge = ({ status }: { status: string }) => (
  <Badge className={statusColor[status] ?? "bg-blue-100 text-blue-800"}>
    {status.replace("_", " ")}
  </Badge>
);

const StatusIcon = ({ status }: { status: string }) => {
  switch (status) {
    case "DRIVER_ACCEPTED": return <CheckCircle className="h-4 w-4 text-green-600" />;
    case "DRIVER_REJECTED": return <XCircle className="h-4 w-4 text-red-600" />;
    case "ASSIGNED":        return <Clock className="h-4 w-4 text-yellow-600" />;
    default:                return <AlertCircle className="h-4 w-4 text-gray-400" />;
  }
};

// ── Dispatch Dialog ──────────────────────────────────────────────────────────

interface DispatchDialogProps {
  freight: AwardedFreight;
  companyId: number;
  canViewFinancials: boolean;
  onSuccess: () => void;
}

function DispatchDialog({ freight, companyId, canViewFinancials, onSuccess }: DispatchDialogProps) {
  const [open, setOpen] = useState(false);
  const [driverUserId, setDriverUserId] = useState<string>("");
  const [vehicleId, setVehicleId] = useState<string>("");
  const [notes, setNotes] = useState("");
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // Load eligible drivers
  const { data: driversPage, isLoading: driversLoading, isError: driversError, refetch: refetchDrivers } = useQuery({
    queryKey: ["company-drivers", companyId],
    queryFn: () =>
      api.get<{content: Driver[]}>(`/carrier-companies/${companyId}/fleet/drivers?status=ACTIVE`),
    enabled: open,
  });

  // Load eligible vehicles
  const { data: vehiclesPage, isLoading: vehiclesLoading, isError: vehiclesError, refetch: refetchVehicles } = useQuery({
    queryKey: ["company-vehicles", companyId],
    queryFn: () =>
      api.get<{content: Vehicle[]}>(`/carrier-companies/${companyId}/fleet/vehicles?size=100`),
    enabled: open,
  });

  const createMutation = useMutation({
    mutationFn: (payload: { driverUserId: number; vehicleId: number; notes?: string }) =>
      api.post(`/carrier-companies/${companyId}/freight/${freight.freightId}/dispatch`, payload),
    onSuccess: () => {
      toast({ title: "Dispatch created", description: "Driver has been notified." });
      queryClient.invalidateQueries({ queryKey: ["awarded-freights", companyId] });
      queryClient.invalidateQueries({ queryKey: ["company-dispatch-history", companyId] });
      queryClient.invalidateQueries({ queryKey: ["company-dispatch-detail", companyId] });
      queryClient.invalidateQueries({ queryKey: ["freight-dispatches", companyId] });
      setOpen(false);
      onSuccess();
    },
    onError: (err: any) => {
      toast({
        title: "Dispatch failed",
        description: err?.response?.data?.message ?? "An error occurred.",
        variant: "destructive",
      });
    },
  });

  const handleSubmit = () => {
    if (!driverUserId || !vehicleId) {
      toast({ title: "Select driver and vehicle", variant: "destructive" });
      return;
    }
    createMutation.mutate({
      driverUserId: parseInt(driverUserId),
      vehicleId: parseInt(vehicleId),
      notes: notes.trim() || undefined,
    });
  };

  const drivers: Driver[] = driversPage?.content ?? [];
  const vehicles: Vehicle[] = vehiclesPage?.content ?? [];

  const eligibleDrivers = drivers.filter(
    d => d.availability !== "ON_TRIP" && d.availability !== "UNAVAILABLE" && d.status === "ACTIVE"
  );
  const eligibleVehicles = vehicles.filter(
    v => v.approvalStatus === "APPROVED" &&
           !["assigned", "on_trip", "maintenance", "unavailable"].includes(
           (v.availabilityStatus ?? "").toLowerCase()
         )
  );

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button type="button" size="sm" className="min-h-11 gap-2">
          <Truck className="h-4 w-4" />
          Dispatch
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Dispatch Driver & Vehicle</DialogTitle>
          <DialogDescription>
            {freight.pickupLocation} → {freight.destination}
            {canViewFinancials && formatCompanyMoney(freight.agreedAmount) && (
              <> · <span className="font-semibold">{formatCompanyMoney(freight.agreedAmount)}</span></>
            )}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="driver-select">Driver</Label>
            <Select value={driverUserId} onValueChange={setDriverUserId}>
              <SelectTrigger id="driver-select" className="min-h-11">
                <SelectValue placeholder="Select eligible driver…" />
              </SelectTrigger>
              <SelectContent>
                {eligibleDrivers.length === 0 ? (
                  <SelectItem value="none" disabled>
                    No eligible drivers available
                  </SelectItem>
                ) : (
                  eligibleDrivers.map(d => (
                    <SelectItem key={d.driverUserId} value={String(d.driverUserId)}>
                      {d.fullName ?? `Driver #${d.driverUserId}`}{" "}
                      <span className="text-xs text-muted-foreground ml-1">
                        ({d.availability})
                      </span>
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
            {driversLoading && <p className="text-xs text-muted-foreground" role="status">Loading eligible drivers…</p>}
            {driversError && <div role="alert" className="flex flex-col gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 sm:flex-row sm:items-center sm:justify-between"><p className="text-xs text-destructive">Unable to load eligible drivers.</p><Button type="button" variant="outline" size="sm" className="min-h-10" onClick={() => refetchDrivers()}>Retry drivers</Button></div>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="vehicle-select">Vehicle</Label>
            <Select value={vehicleId} onValueChange={setVehicleId}>
              <SelectTrigger id="vehicle-select" className="min-h-11">
                <SelectValue placeholder="Select eligible vehicle…" />
              </SelectTrigger>
              <SelectContent>
                {eligibleVehicles.length === 0 ? (
                  <SelectItem value="none" disabled>
                    No eligible vehicles available
                  </SelectItem>
                ) : (
                  eligibleVehicles.map(v => (
                    <SelectItem key={v.id} value={String(v.id)}>
                      {v.truckType} · {v.plateNumber}{" "}
                      <span className="text-xs text-muted-foreground ml-1">
                        ({v.capacity}t)
                      </span>
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
            {vehiclesLoading && <p className="text-xs text-muted-foreground" role="status">Loading eligible vehicles…</p>}
            {vehiclesError && <div role="alert" className="flex flex-col gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 sm:flex-row sm:items-center sm:justify-between"><p className="text-xs text-destructive">Unable to load eligible vehicles.</p><Button type="button" variant="outline" size="sm" className="min-h-10" onClick={() => refetchVehicles()}>Retry vehicles</Button></div>}
          </div>

          <div>
            <Label htmlFor="notes">Notes (optional)</Label>
            <textarea
              id="notes"
              className="mt-1 min-h-20 w-full resize-y rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              placeholder="Route instructions, special requirements…"
              value={notes}
              onChange={e => setNotes(e.target.value)}
            />
          </div>

          <Alert>
            <AlertDescription className="text-xs">
              {canViewFinancials && formatCompanyMoney(freight.agreedAmount)
                ? `The agreed price (${formatCompanyMoney(freight.agreedAmount)}) is fixed from the commercial award and cannot be changed during dispatch.`
                : "The commercial award amount is fixed and cannot be changed during dispatch."}
            </AlertDescription>
          </Alert>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" className="min-h-11" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="button" className="min-h-11" onClick={handleSubmit} disabled={createMutation.isPending}>
            {createMutation.isPending ? "Dispatching…" : "Create Dispatch"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ── History Dialog ────────────────────────────────────────────────────────────

interface HistoryDialogProps {
  freight: AwardedFreight;
  companyId: number;
}

function HistoryDialog({ freight, companyId }: HistoryDialogProps) {
  const [open, setOpen] = useState(false);

  const { data: history, isLoading, isError, refetch } = useQuery<CompanyDispatch[]>({
    queryKey: ["freight-dispatches", companyId, freight.freightId],
    queryFn: async () => {
      const result = await api.get<CompanyDispatch[]>(
        `/carrier-companies/${companyId}/freight/${freight.freightId}/dispatches`,
      );
      if (!Array.isArray(result) || result.some(item => item.carrierCompanyId !== companyId)) {
        throw new Error("The dispatch history did not match the selected company.");
      }
      return result;
    },
    enabled: open,
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="min-h-11">
          History
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Dispatch History</DialogTitle>
          <DialogDescription>
            {freight.pickupLocation} → {freight.destination}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3 max-h-[400px] overflow-y-auto">
          {isLoading ? (
            <Skeleton className="h-20 w-full" />
          ) : isError ? (
            <div role="alert" className="flex flex-col gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-destructive">Unable to load dispatch history.</p><Button type="button" variant="outline" className="min-h-10" onClick={() => refetch()}>Retry history</Button></div>
          ) : !history || history.length === 0 ? (
            <p className="py-4 text-center text-sm text-muted-foreground">
              No dispatch history yet.
            </p>
          ) : (
            history.map(d => (
              <div key={d.id} className="border rounded-lg p-3 space-y-1">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <StatusIcon status={d.status} />
                    <DispatchStatusBadge status={d.status} />
                  </div>
                  <span className="text-xs text-muted-foreground">
                    {new Date(d.assignedAt).toLocaleDateString()}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">
                  Driver #{d.driverUserId} · Vehicle #{d.vehicleId}
                </p>
                {d.rejectionReason && (
                  <p className="text-xs text-red-600">
                    Rejection: {d.rejectionReason}
                  </p>
                )}
                {d.bookingId && (
                  <p className="text-xs text-green-600">
                    Booking #{d.bookingId} created
                  </p>
                )}
              </div>
            ))
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ── Main Page ────────────────────────────────────────────────────────────────

export default function CarrierDispatchPage() {
  const { memberships, membershipError, membershipRevalidating } = useAuth();
  const params = useParams<{companyId?: string}>();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // Resolve company ID from the user's active membership
  const parsedCompanyId = params.companyId ? Number(params.companyId) : null;
  const requestedCompanyId = params.companyId
    && parsedCompanyId !== null
    && Number.isSafeInteger(parsedCompanyId)
    && parsedCompanyId > 0
    && String(parsedCompanyId) === params.companyId
    ? parsedCompanyId
    : undefined;
  const membership = requestedCompanyId
    ? memberships.find(m => m.carrierCompanyId === requestedCompanyId && m.status === "ACTIVE")
    : undefined;

  const companyId: number | null = membership?.carrierCompanyId ?? null;
  const canViewFinancials = membership?.role === "CARRIER_OWNER" || membership?.role === "FLEET_MANAGER";
  const [dispatchPage, setDispatchPage] = useState(0);
  const [selectedDispatchId, setSelectedDispatchId] = useState<number | null>(null);

  const {
    data: awardedFreights,
    isLoading,
    isError,
    refetch,
  } = useQuery<AwardedFreight[]>({
    queryKey: ["awarded-freights", companyId],
    queryFn: async () => {
      const result = await api.get<AwardedFreight[]>(`/carrier-companies/${companyId}/awarded-freights`);
      if (!Array.isArray(result) || result.some(item => item.carrierCompanyId !== companyId)) {
        throw new Error("The awarded-freight response did not match the selected company.");
      }
      return result;
    },
    enabled: !!companyId && !membershipRevalidating && !membershipError,
  });

  const dispatchHistory = useQuery({
    queryKey: ["company-dispatch-history", companyId, dispatchPage, 20],
    queryFn: async () => {
      const result = await api.get<CompanyPage<CompanyDispatch>>(
        `/carrier-companies/${companyId}/dispatches?page=${dispatchPage}&size=20`,
      );
      if (!result || !Array.isArray(result.content)
          || result.content.some(item => item.carrierCompanyId !== companyId)) {
        throw new Error("The dispatch response did not match the selected company.");
      }
      return result;
    },
    enabled: !!companyId && !membershipRevalidating && !membershipError,
  });

  const dispatchDetail = useQuery({
    queryKey: ["company-dispatch-detail", companyId, selectedDispatchId],
    queryFn: async () => {
      if (companyId === null || selectedDispatchId === null) {
        throw new Error("A selected company and dispatch are required.");
      }
      const result = await api.get<CompanyDispatch>(
        `/carrier-companies/${companyId}/dispatches/${selectedDispatchId}`,
      );
      if (result.carrierCompanyId !== companyId || result.id !== selectedDispatchId) {
        throw new Error("The dispatch detail did not match the selected company and dispatch.");
      }
      return result;
    },
    enabled: !!companyId && selectedDispatchId !== null
      && !membershipRevalidating && !membershipError,
  });
  const historyAccessDenied = dispatchHistory.isError
    && isAccessDeniedOrNotFound(dispatchHistory.error);
  const detailAccessDenied = dispatchDetail.isError
    && isAccessDeniedOrNotFound(dispatchDetail.error);

  const cancelMutation = useMutation({
    mutationFn: (dispatchId: number) =>
      api.post(`/carrier-companies/${companyId}/dispatches/${dispatchId}/cancel`),
    onSuccess: () => {
      toast({ title: "Dispatch cancelled", description: "Resources have been released." });
      queryClient.invalidateQueries({ queryKey: ["awarded-freights", companyId] });
      queryClient.invalidateQueries({ queryKey: ["company-dispatch-history", companyId] });
      queryClient.invalidateQueries({ queryKey: ["company-dispatch-detail", companyId] });
      queryClient.invalidateQueries({ queryKey: ["freight-dispatches", companyId] });
    },
    onError: (err: any) => {
      toast({
        title: "Cancel failed",
        description: err?.response?.data?.message ?? "Could not cancel dispatch.",
        variant: "destructive",
      });
    },
  });

  if (membershipRevalidating) {
    return <main className="container mx-auto max-w-6xl space-y-4 px-3 py-6 sm:px-5 lg:px-7" aria-label="Refreshing company access"><Skeleton className="h-10 w-full rounded-xl" /><Skeleton className="h-40 w-full rounded-xl" /></main>;
  }

  if (membershipError) {
    return <main className="container mx-auto max-w-6xl px-3 py-6 sm:px-5 lg:px-7"><Alert role="alert"><AlertDescription>{membershipError}</AlertDescription></Alert></main>;
  }

  if (!companyId) {
    return (
      <main className="container mx-auto max-w-6xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
        <Alert className="border-border/70 bg-card">
          <AlertDescription className="leading-6">
            {memberships.length > 1 ? (
              <>Choose a company workspace before opening dispatch. <Link href="/dashboard" className="underline">Select a company</Link>.</>
            ) : (
              "You must be an active member of a carrier company to access this page."
            )}
          </AlertDescription>
        </Alert>
      </main>
    );
  }

  return (
    <main className="container mx-auto max-w-6xl space-y-6 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <header className="flex flex-col gap-4 border-b border-border/70 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">Company #{companyId} · Operations</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Dispatch board</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
            Manage driver and vehicle assignments for awarded shipments. The agreed price remains fixed.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={() => refetch()} className="min-h-11 w-full gap-2 sm:w-auto">
          <RefreshCw className="h-4 w-4" />
          Refresh
        </Button>
      </header>

      {/* Loading */}
      {isLoading && (
        <div className="grid gap-4 md:grid-cols-2" aria-label="Loading awarded shipments">
          {[1, 2, 3].map(i => (
            <Skeleton key={i} className="h-48 w-full rounded-xl" />
          ))}
        </div>
      )}

      {/* Error */}
      {isError && (
        <Alert role="alert" className="flex flex-col gap-3 border-destructive/30 bg-destructive/5 sm:flex-row sm:items-center sm:justify-between">
          <AlertDescription className="text-destructive">
            Failed to load awarded shipments. Please try refreshing.
          </AlertDescription>
          <Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => refetch()}>Retry dispatch list</Button>
        </Alert>
      )}

      {/* Empty state */}
      {!isLoading && !isError && (!awardedFreights || awardedFreights.length === 0) && (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center px-5 py-12 text-center">
          <Truck className="mb-4 h-10 w-10 text-muted-foreground/50" aria-hidden="true" />
          <p className="text-lg font-semibold">No awarded shipments</p>
          <p className="mt-1 max-w-lg text-sm leading-6 text-muted-foreground">
            Once a shipper accepts your offer, awarded shipments will appear here.
          </p>
          </CardContent>
        </Card>
      )}

      {/* Awarded freight cards */}
      {awardedFreights && awardedFreights.length > 0 && (
        <section aria-label="Awarded shipments" className="grid gap-4 lg:grid-cols-2">
          {awardedFreights.map(freight => {
            const dispatch = freight.activeDispatch;
            const isPending = dispatch?.status === "ASSIGNED";
            const isAccepted = dispatch?.status === "DRIVER_ACCEPTED";
            const needsDispatch = !dispatch ||
              dispatch.status === "DRIVER_REJECTED" ||
              dispatch.status === "CANCELLED";

            return (
              <Card key={freight.freightId} className="relative overflow-hidden border-border/70 shadow-sm">
                {isAccepted && (
                  <div className="absolute top-0 left-0 right-0 h-1 bg-green-500" />
                )}
                {isPending && (
                  <div className="absolute top-0 left-0 right-0 h-1 bg-yellow-400" />
                )}

                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="break-words text-base font-semibold leading-tight">
                      Freight #{freight.freightId}
                    </CardTitle>
                    {dispatch ? (
                      <DispatchStatusBadge status={dispatch.status} />
                    ) : (
                      <Badge variant="outline" className="text-xs">
                        Awaiting dispatch
                      </Badge>
                    )}
                  </div>
                </CardHeader>

                <CardContent className="space-y-4">
                  {/* Route */}
                  <div className="flex items-start gap-2 text-sm leading-6">
                    <MapPin className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
                    <div className="min-w-0 break-words">
                      <span className="font-medium">{freight.pickupLocation}</span>
                      <span className="text-muted-foreground mx-1">→</span>
                      <span className="font-medium">{freight.destination}</span>
                    </div>
                  </div>

                  {/* Cargo details */}
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
                    <span>{freight.materialType}</span>
                    <span>·</span>
                    <span>{freight.weightTons} tons</span>
                  </div>

                  {canViewFinancials && formatCompanyMoney(freight.agreedAmount) && (
                    <div className="flex flex-wrap items-center gap-2 rounded-lg bg-muted/40 px-3 py-2 text-sm">
                      <DollarSign className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                      <span className="font-semibold">{formatCompanyMoney(freight.agreedAmount)}</span>
                      <span className="text-xs text-muted-foreground">(agreed)</span>
                    </div>
                  )}

                  {/* Active dispatch info */}
                  {dispatch && (
                    <>
                      <Separator />
                      <div className="space-y-2 text-xs text-muted-foreground">
                        <div className="flex flex-wrap items-center gap-1">
                          <User className="h-3 w-3 shrink-0" aria-hidden="true" />
                          Driver #{dispatch.driverUserId} · Vehicle #{dispatch.vehicleId}
                        </div>
                        {dispatch.bookingId && (
                          <div className="break-words font-medium text-primary">
                            ✓ Booking #{dispatch.bookingId} confirmed
                          </div>
                        )}
                      </div>
                    </>
                  )}

                  {/* Actions */}
                  <div className="flex flex-wrap gap-2 border-t border-border/70 pt-3">
                    {needsDispatch && (
                      <DispatchDialog
                        freight={freight}
                        companyId={companyId}
                        canViewFinancials={canViewFinancials}
                        onSuccess={refetch}
                      />
                    )}
                    {isPending && (
                      <Button
                        variant="outline"
                        type="button"
                        size="sm"
                        className="min-h-11"
                        onClick={() => {
                          if (dispatch) cancelMutation.mutate(dispatch.dispatchId);
                        }}
                        disabled={cancelMutation.isPending}
                      >
                        Cancel
                      </Button>
                    )}
                    <HistoryDialog freight={freight} companyId={companyId} />
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </section>
      )}

      <section aria-labelledby="company-dispatch-history-heading" className="space-y-3 border-t border-border/70 pt-5">
        <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 id="company-dispatch-history-heading" className="text-lg font-semibold">Dispatch history</h2>
            <p className="text-sm text-muted-foreground">Company dispatch assignments, newest first.</p>
          </div>
          {!historyAccessDenied && dispatchHistory.data && <p className="text-sm text-muted-foreground">{dispatchHistory.data.totalElements} records</p>}
        </header>
        {dispatchHistory.isLoading && <div className="space-y-3" aria-label="Loading dispatch history"><Skeleton className="h-24 w-full rounded-xl" /><Skeleton className="h-24 w-full rounded-xl" /></div>}
        {dispatchHistory.isError && (
          <Alert role="alert" className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <AlertDescription className="text-destructive">{companyRequestError(dispatchHistory.error)}</AlertDescription>
            <Button type="button" variant="outline" className="min-h-10" onClick={() => void dispatchHistory.refetch()}>Retry</Button>
          </Alert>
        )}
        {!dispatchHistory.isLoading && !dispatchHistory.isError && dispatchHistory.data?.content.length === 0 && (
          <Card className="border-dashed shadow-none"><CardContent className="py-10 text-center text-sm text-muted-foreground">No dispatch history is available for this company.</CardContent></Card>
        )}
        {!historyAccessDenied && dispatchHistory.data?.content.map(dispatch => (
          <Card key={dispatch.id} className="border-border/70">
            <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">Dispatch #{dispatch.id}</span>
                  <DispatchStatusBadge status={dispatch.status} />
                </div>
                <p className="text-sm text-muted-foreground">Freight #{dispatch.freightId} · Driver #{dispatch.driverUserId} · Vehicle #{dispatch.vehicleId}</p>
                <p className="text-xs text-muted-foreground">Assigned {formatCompanyDate(dispatch.assignedAt)}</p>
                {canViewFinancials && formatCompanyMoney(dispatch.agreedAmount) && (
                  <p className="text-sm font-medium">{formatCompanyMoney(dispatch.agreedAmount)}</p>
                )}
              </div>
              <Button type="button" variant="outline" size="sm" className="min-h-10" onClick={() => setSelectedDispatchId(dispatch.id)}>View details</Button>
            </CardContent>
          </Card>
        ))}
        {!historyAccessDenied && dispatchHistory.data && dispatchHistory.data.totalPages > 1 && (
          <nav className="flex items-center justify-between gap-3" aria-label="Dispatch history pages">
            <Button type="button" variant="outline" disabled={dispatchHistory.data.first || dispatchHistory.isFetching} onClick={() => setDispatchPage(current => Math.max(0, current - 1))}>Previous</Button>
            <span className="text-sm text-muted-foreground">Page {dispatchHistory.data.number + 1} of {dispatchHistory.data.totalPages}</span>
            <Button type="button" variant="outline" disabled={dispatchHistory.data.last || dispatchHistory.isFetching} onClick={() => setDispatchPage(current => current + 1)}>Next</Button>
          </nav>
        )}
      </section>

      <Dialog open={selectedDispatchId !== null} onOpenChange={open => { if (!open) setSelectedDispatchId(null); }}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>Dispatch details{selectedDispatchId ? ` · #${selectedDispatchId}` : ""}</DialogTitle>
            <DialogDescription>Company-scoped assignment details.</DialogDescription>
          </DialogHeader>
          {dispatchDetail.isLoading && <Skeleton className="h-40 w-full" />}
          {dispatchDetail.isError && (
            <div role="alert" className="flex items-center justify-between gap-3 text-sm text-destructive">
              <span>{companyRequestError(dispatchDetail.error)}</span>
              <Button type="button" variant="outline" size="sm" onClick={() => void dispatchDetail.refetch()}>Retry</Button>
            </div>
          )}
          {!detailAccessDenied && dispatchDetail.data && <DispatchDetails dispatch={dispatchDetail.data} canViewFinancials={canViewFinancials} />}
        </DialogContent>
      </Dialog>

      <div className="border-t border-border/70 pt-4 text-sm leading-6 text-muted-foreground">
        Drivers respond from the mobile app’s <strong>My Assignments</strong> screen or the web <Button variant="link" size="sm" className="h-auto p-0" asChild><a href="/driver/assignments">driver assignment inbox</a></Button>. Accepted assignments create a booking; drivers start the trip from Bookings or the mobile trip screen.
      </div>
    </main>
  );
}

function DispatchDetails({ dispatch, canViewFinancials }: { dispatch: CompanyDispatch; canViewFinancials: boolean }) {
  const fields: [string, string][] = [
    ["Status", dispatch.status.replaceAll("_", " ")],
    ["Freight", `#${dispatch.freightId}`],
    ["Carrier offer", `#${dispatch.carrierOfferId}`],
    ["Driver", `#${dispatch.driverUserId}`],
    ["Vehicle", `#${dispatch.vehicleId}`],
    ["Assigned by", `#${dispatch.assignedBy}`],
    ["Assigned", formatCompanyDate(dispatch.assignedAt)],
    ["Responded", formatCompanyDate(dispatch.respondedAt)],
    ["Cancelled", formatCompanyDate(dispatch.cancelledAt)],
    ["Booking", dispatch.bookingId == null ? "—" : `#${dispatch.bookingId}`],
    ["Notes", dispatch.notes || "—"],
    ["Rejection reason", dispatch.rejectionReason || "—"],
    ["Created", formatCompanyDate(dispatch.createdAt)],
    ["Updated", formatCompanyDate(dispatch.updatedAt)],
  ];
  const amount = canViewFinancials ? formatCompanyMoney(dispatch.agreedAmount) : null;
  return (
    <dl className="grid gap-3 sm:grid-cols-2">
      {fields.map(([label, value]) => <div key={label} className="min-w-0 rounded-lg border border-border/60 p-3"><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm font-medium">{value}</dd></div>)}
      {amount && <div className="min-w-0 rounded-lg border border-border/60 p-3"><dt className="text-xs text-muted-foreground">Agreed amount</dt><dd className="mt-1 text-sm font-medium">{amount}</dd></div>}
    </dl>
  );
}
