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
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
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

// ── Types ────────────────────────────────────────────────────────────────────

interface DispatchSummary {
  dispatchId: number;
  status: string;
  driverUserId: number;
  vehicleId: number;
  assignedAt: string;
  bookingId: number | null;
}

interface AwardedFreight {
  freightId: number;
  pickupLocation: string;
  destination: string;
  materialType: string;
  weightTons: number;
  agreedAmount: number;
  carrierOfferId: number;
  activeDispatch: DispatchSummary | null;
  awardedAt: string;
}

interface DispatchAssignment {
  id: number;
  freightId: number;
  carrierOfferId: number;
  carrierCompanyId: number;
  driverUserId: number;
  vehicleId: number;
  assignedBy: number;
  status: string;
  agreedAmount: number;
  notes: string | null;
  rejectionReason: string | null;
  assignedAt: string;
  respondedAt: string | null;
  cancelledAt: string | null;
  bookingId: number | null;
}

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
  onSuccess: () => void;
}

function DispatchDialog({ freight, companyId, onSuccess }: DispatchDialogProps) {
  const [open, setOpen] = useState(false);
  const [driverUserId, setDriverUserId] = useState<string>("");
  const [vehicleId, setVehicleId] = useState<string>("");
  const [notes, setNotes] = useState("");
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // Load eligible drivers
  const { data: driversPage } = useQuery({
    queryKey: ["company-drivers", companyId],
    queryFn: () =>
      api.get(`/carrier-companies/${companyId}/fleet/drivers?status=ACTIVE`)
         .then(r => r.data),
    enabled: open,
  });

  // Load eligible vehicles
  const { data: vehiclesPage } = useQuery({
    queryKey: ["company-vehicles", companyId],
    queryFn: () =>
      api.get(`/carrier-companies/${companyId}/fleet/vehicles`)
         .then(r => r.data),
    enabled: open,
  });

  const createMutation = useMutation({
    mutationFn: (payload: { driverUserId: number; vehicleId: number; notes?: string }) =>
      api.post(`/carrier-companies/${companyId}/freight/${freight.freightId}/dispatch`, payload)
         .then(r => r.data),
    onSuccess: () => {
      toast({ title: "Dispatch created", description: "Driver has been notified." });
      queryClient.invalidateQueries({ queryKey: ["awarded-freights", companyId] });
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

  const drivers: Driver[] = driversPage?.content ?? driversPage ?? [];
  const vehicles: Vehicle[] = vehiclesPage?.content ?? vehiclesPage ?? [];

  const eligibleDrivers = drivers.filter(
    d => d.availability !== "ON_TRIP" && d.status === "ACTIVE"
  );
  const eligibleVehicles = vehicles.filter(
    v => v.approvalStatus === "APPROVED" &&
         !["on_trip", "maintenance", "unavailable"].includes(
           (v.availabilityStatus ?? "").toLowerCase()
         )
  );

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" className="gap-2">
          <Truck className="h-4 w-4" />
          Dispatch
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Dispatch Driver & Vehicle</DialogTitle>
          <DialogDescription>
            {freight.pickupLocation} → {freight.destination} ·{" "}
            <span className="font-semibold">
              ETB {freight.agreedAmount.toLocaleString()}
            </span>
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div>
            <Label htmlFor="driver-select">Driver</Label>
            <Select value={driverUserId} onValueChange={setDriverUserId}>
              <SelectTrigger id="driver-select" className="mt-1">
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
          </div>

          <div>
            <Label htmlFor="vehicle-select">Vehicle</Label>
            <Select value={vehicleId} onValueChange={setVehicleId}>
              <SelectTrigger id="vehicle-select" className="mt-1">
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
          </div>

          <div>
            <Label htmlFor="notes">Notes (optional)</Label>
            <textarea
              id="notes"
              className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm min-h-[80px] resize-none focus:outline-none focus:ring-2 focus:ring-ring"
              placeholder="Route instructions, special requirements…"
              value={notes}
              onChange={e => setNotes(e.target.value)}
            />
          </div>

          <Alert>
            <AlertDescription className="text-xs">
              The agreed price (ETB {freight.agreedAmount.toLocaleString()}) is fixed from the
              commercial award and cannot be changed during dispatch.
            </AlertDescription>
          </Alert>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={createMutation.isPending}>
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

  const { data: history, isLoading } = useQuery<DispatchAssignment[]>({
    queryKey: ["freight-dispatches", companyId, freight.freightId],
    queryFn: () =>
      api.get(`/carrier-companies/${companyId}/freight/${freight.freightId}/dispatches`)
         .then(r => r.data),
    enabled: open,
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
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
          ) : !history || history.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-4">
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
  const { user } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // Resolve company ID from the user's active membership
  const { data: membership } = useQuery({
    queryKey: ["my-membership"],
    queryFn: () =>
      api.get("/company-memberships/me").then(r => r.data),
  });

  const companyId: number | null = membership?.carrierCompanyId ?? null;

  const {
    data: awardedFreights,
    isLoading,
    isError,
    refetch,
  } = useQuery<AwardedFreight[]>({
    queryKey: ["awarded-freights", companyId],
    queryFn: () =>
      api.get(`/carrier-companies/${companyId}/awarded-freights`).then(r => r.data),
    enabled: !!companyId,
  });

  const cancelMutation = useMutation({
    mutationFn: (dispatchId: number) =>
      api.post(`/carrier-companies/${companyId}/dispatches/${dispatchId}/cancel`).then(r => r.data),
    onSuccess: () => {
      toast({ title: "Dispatch cancelled", description: "Resources have been released." });
      queryClient.invalidateQueries({ queryKey: ["awarded-freights", companyId] });
    },
    onError: (err: any) => {
      toast({
        title: "Cancel failed",
        description: err?.response?.data?.message ?? "Could not cancel dispatch.",
        variant: "destructive",
      });
    },
  });

  if (!companyId) {
    return (
      <div className="container mx-auto p-6">
        <Alert>
          <AlertDescription>
            You must be an active member of a carrier company to access this page.
          </AlertDescription>
        </Alert>
      </div>
    );
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Carrier Dispatch</h1>
          <p className="text-muted-foreground text-sm mt-1">
            Manage driver and vehicle assignments for awarded shipments
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()} className="gap-2">
          <RefreshCw className="h-4 w-4" />
          Refresh
        </Button>
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="grid gap-4 md:grid-cols-2">
          {[1, 2, 3].map(i => (
            <Skeleton key={i} className="h-48 w-full rounded-xl" />
          ))}
        </div>
      )}

      {/* Error */}
      {isError && (
        <Alert>
          <AlertDescription>
            Failed to load awarded shipments. Please try refreshing.
          </AlertDescription>
        </Alert>
      )}

      {/* Empty state */}
      {!isLoading && !isError && (!awardedFreights || awardedFreights.length === 0) && (
        <div className="text-center py-16 text-muted-foreground">
          <Truck className="h-12 w-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium">No awarded shipments</p>
          <p className="text-sm">
            Once a shipper accepts your offer, awarded shipments will appear here.
          </p>
        </div>
      )}

      {/* Awarded freight cards */}
      {awardedFreights && awardedFreights.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {awardedFreights.map(freight => {
            const dispatch = freight.activeDispatch;
            const isPending = dispatch?.status === "ASSIGNED";
            const isAccepted = dispatch?.status === "DRIVER_ACCEPTED";
            const needsDispatch = !dispatch ||
              dispatch.status === "DRIVER_REJECTED" ||
              dispatch.status === "CANCELLED";

            return (
              <Card key={freight.freightId} className="relative overflow-hidden">
                {isAccepted && (
                  <div className="absolute top-0 left-0 right-0 h-1 bg-green-500" />
                )}
                {isPending && (
                  <div className="absolute top-0 left-0 right-0 h-1 bg-yellow-400" />
                )}

                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base font-semibold leading-tight">
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

                <CardContent className="space-y-3">
                  {/* Route */}
                  <div className="flex items-start gap-2 text-sm">
                    <MapPin className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
                    <div>
                      <span className="font-medium">{freight.pickupLocation}</span>
                      <span className="text-muted-foreground mx-1">→</span>
                      <span className="font-medium">{freight.destination}</span>
                    </div>
                  </div>

                  {/* Cargo details */}
                  <div className="flex items-center gap-4 text-sm text-muted-foreground">
                    <span>{freight.materialType}</span>
                    <span>·</span>
                    <span>{freight.weightTons} tons</span>
                  </div>

                  {/* Agreed amount */}
                  <div className="flex items-center gap-2 text-sm">
                    <DollarSign className="h-4 w-4 text-green-600" />
                    <span className="font-semibold text-green-700">
                      ETB {freight.agreedAmount.toLocaleString()}
                    </span>
                    <span className="text-xs text-muted-foreground">(agreed)</span>
                  </div>

                  {/* Active dispatch info */}
                  {dispatch && (
                    <>
                      <Separator />
                      <div className="text-xs text-muted-foreground space-y-1">
                        <div className="flex items-center gap-1">
                          <User className="h-3 w-3" />
                          Driver #{dispatch.driverUserId} · Vehicle #{dispatch.vehicleId}
                        </div>
                        {dispatch.bookingId && (
                          <div className="text-green-600 font-medium">
                            ✓ Booking #{dispatch.bookingId} confirmed
                          </div>
                        )}
                      </div>
                    </>
                  )}

                  {/* Actions */}
                  <div className="flex gap-2 pt-1">
                    {needsDispatch && (
                      <DispatchDialog
                        freight={freight}
                        companyId={companyId}
                        onSuccess={refetch}
                      />
                    )}
                    {isPending && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="text-red-600 border-red-200 hover:bg-red-50"
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
        </div>
      )}
    </div>
  );
}
