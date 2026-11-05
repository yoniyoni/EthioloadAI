/**
 * carrier-offers.tsx — Phase 5 carrier offer management page.
 *
 * Two views driven by user role:
 *
 *   CARRIER (CARRIER_OWNER / DISPATCHER)
 *     • Browse open POSTED freight
 *     • Submit offer on a freight
 *     • View own active offers + negotiation history
 *     • Counter-offer / accept / withdraw
 *
 *   SHIPPER
 *     • View all incoming carrier offers on own freight
 *     • Counter / accept / reject individual offers
 *
 * Backend API base: /api  (via the `api` client)
 * All monetary values use string ↔ number conversion at the boundary;
 * BigDecimal from Spring is serialized as a JSON number.
 */

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useLocation } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import {
  ArrowLeft,
  CheckCircle2,
  Clock,
  History,
  Loader2,
  MessageSquare,
  Send,
  ShieldCheck,
  XCircle,
} from "lucide-react";
import { Link } from "wouter";

// ── Types ─────────────────────────────────────────────────────────────────────

type OfferStatus =
  | "PENDING"
  | "NEGOTIATING"
  | "ACCEPTED"
  | "REJECTED"
  | "WITHDRAWN"
  | "SUPERSEDED";

type PricingType = "FIXED" | "NEGOTIABLE";
interface FreightBoardItem {
  id: number;
  pickupLocation?: string | null;
  destination?: string | null;
  materialType: string;
  weightTons: number;
  budget?: number | null;
  priceType: PricingType;
  status: string;
  preferredDate?: string | null;
  vehicleTypeNeeded?: string | null;
}
interface CompanyDriver { id: number; driverUserId: number; fullName: string; status: string; verificationStatus: boolean; isActive: boolean; }
interface CompanyVehicle { id: number; plateNumber: string; truckType: string; approvalStatus: string; availabilityStatus: string; }
interface FleetPair { id: number; carrierDriverId: number; vehicleId: number; status: string; }
type ActorType = "SHIPPER" | "CARRIER";
type NegotiationAction =
  | "OFFER"
  | "COUNTER_OFFER"
  | "ACCEPT"
  | "REJECT"
  | "WITHDRAW";

interface NegotiationEntry {
  id: number;
  actorType: ActorType;
  actorUserId: number;
  action: NegotiationAction;
  amount: number | null;
  note: string | null;
  createdAt: string;
}

interface CarrierOffer {
  id: number;
  freightId: number;
  freightPickupLocation?: string | null;
  freightDestination?: string | null;
  carrierCompanyId: number;
  carrierCompanyName: string;
  submittedById: number;
  submittedByName: string;
  proposedDriverUserId: number | null;
  proposedDriverName: string | null;
  proposedVehicleId: number | null;
  proposedVehiclePlate: string | null;
  proposedVehicleType: string | null;
  pricingType: PricingType;
  currentAmount: number;
  status: OfferStatus;
  assignmentStatus: CarrierAssignmentStatus | null;
  nextActor: ActorType | null;
  agreedAmount: number | null;
  acceptedAt: string | null;
  rejectedAt: string | null;
  withdrawnAt: string | null;
  createdAt: string;
  updatedAt: string;
  negotiationHistory: NegotiationEntry[];
}

type CarrierAssignmentStatus =
  | "PENDING_ASSIGNMENT"
  | "DISPATCH_PENDING"
  | "BOOKING_CREATED";

// ── Status helpers ─────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<OfferStatus, string> = {
  PENDING:     "bg-blue-50 text-blue-700 border-blue-200",
  NEGOTIATING: "bg-amber-50 text-amber-700 border-amber-200",
  ACCEPTED:    "bg-emerald-50 text-emerald-700 border-emerald-200",
  REJECTED:    "bg-red-50 text-red-700 border-red-200",
  WITHDRAWN:   "bg-gray-50 text-gray-600 border-gray-200",
  SUPERSEDED:  "bg-gray-50 text-gray-400 border-gray-200",
};

const ASSIGNMENT_STATUS_LABELS: Record<CarrierAssignmentStatus, string> = {
  PENDING_ASSIGNMENT: "Pending eligible driver and vehicle assignment",
  DISPATCH_PENDING: "Dispatch assigned; awaiting driver response",
  BOOKING_CREATED: "Driver accepted; booking created",
};

const ACTION_LABELS: Record<NegotiationAction, string> = {
  OFFER:         "Initial Offer",
  COUNTER_OFFER: "Counter-Offer",
  ACCEPT:        "Accepted",
  REJECT:        "Rejected",
  WITHDRAW:      "Withdrawn",
};

function fmt(amount: number | null): string {
  if (amount == null) return "—";
  return new Intl.NumberFormat("en-ET", {
    style: "currency",
    currency: "ETB",
    maximumFractionDigits: 2,
  }).format(amount);
}

function timeAgo(iso: string): string {
  const d = new Date(iso);
  const secs = Math.floor((Date.now() - d.getTime()) / 1000);
  if (secs < 60) return "just now";
  if (secs < 3600) return `${Math.floor(secs / 60)}m ago`;
  if (secs < 86400) return `${Math.floor(secs / 3600)}h ago`;
  return d.toLocaleDateString();
}

// ── Carrier view: offers submitted by my company ─────────────────────────────

function CarrierOfferPortal({
  companyId,
  freightId,
}: {
  companyId: number;
  freightId?: number;
}) {
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();

  const [submitAmount, setSubmitAmount] = useState("");
  const [submitNote, setSubmitNote]     = useState("");
  const [showSubmit, setShowSubmit]     = useState(false);
  const [selectedPairId, setSelectedPairId] = useState("");
  const [counterAmt, setCounterAmt]     = useState("");
  const [counterNote, setCounterNote]   = useState("");
  const [activeOffer, setActiveOffer]   = useState<CarrierOffer | null>(null);
  const [actionDialog, setActionDialog] = useState<
    "counter" | "accept" | "withdraw" | null
  >(null);

  // Company's own offers (paginated)
  const { data: offersPage, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["carrier-offers", companyId],
    queryFn: () =>
      api.get<{ content: CarrierOffer[]; totalElements: number }>(
        `/carrier-companies/${companyId}/offers?page=0&size=50`
      ),
    refetchInterval: 30_000,
  });

  const offers = offersPage?.content ?? [];
  const {
    data: selectedFreight,
    isLoading: isFreightLoading,
    isError: isFreightError,
    isFetching: isFreightFetching,
    isSuccess: isFreightSuccess,
    refetch: refetchFreight,
  } = useQuery({
    queryKey: ["freight", freightId],
    queryFn: () => api.get<FreightBoardItem>(`/freight/${freightId}`),
    enabled: freightId != null,
  });
  const hasVerifiedFreight =
    isFreightSuccess &&
    !isFreightError &&
    selectedFreight?.id === freightId;
  const isFreightPosted =
    hasVerifiedFreight &&
    selectedFreight.status.toUpperCase() === "POSTED";
  const isFreightReady =
    isFreightPosted &&
    !isFreightFetching;
  const fixedPrice = selectedFreight?.priceType === "FIXED";
  const { data: pairsPage } = useQuery({
    queryKey: ["company-assignments", companyId],
    queryFn: () => api.get<{ content: FleetPair[] }>(`/carrier-companies/${companyId}/fleet/assignments?size=100`),
    enabled: showSubmit,
  });
  const { data: driversPage } = useQuery({
    queryKey: ["company-drivers", companyId],
    queryFn: () => api.get<{ content: CompanyDriver[] }>(`/carrier-companies/${companyId}/fleet/drivers?status=ACTIVE&size=100`),
    enabled: showSubmit,
  });
  const { data: vehiclesPage } = useQuery({
    queryKey: ["company-vehicles", companyId],
    queryFn: () => api.get<{ content: CompanyVehicle[] }>(`/carrier-companies/${companyId}/fleet/vehicles?size=100`),
    enabled: showSubmit,
  });
  const activePairs = (pairsPage?.content ?? []).filter(pair => pair.status === "ACTIVE").flatMap(pair => {
    const driver = driversPage?.content.find(item => item.id === pair.carrierDriverId);
    const vehicle = vehiclesPage?.content.find(item => item.id === pair.vehicleId);
    if (!driver || !vehicle || driver.status !== "ACTIVE" || !driver.isActive || !driver.verificationStatus || vehicle.approvalStatus !== "APPROVED") return [];
    if (["on_trip", "maintenance", "unavailable"].includes((vehicle.availabilityStatus ?? "").toLowerCase())) return [];
    return [{ pair, driver, vehicle }];
  });
  const selectedPair = activePairs.find(item => String(item.pair.id) === selectedPairId);
  const { memberships } = useAuth();
  const isCommercialRole = memberships.some(m => m.carrierCompanyId === companyId && ["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"].includes(m.role));

  // Submit offer mutation
  const submitMut = useMutation({
    mutationFn: (body: { amount: number; driverUserId: number; vehicleId: number; note?: string }) =>
      api.post<CarrierOffer>(
        `/carrier-companies/${companyId}/freight/${freightId}/offers`,
        body
      ),
    onSuccess: () => {
      toast({ title: "Application sent", description: "The shipper can review your price, driver, and vehicle." });
      setShowSubmit(false);
      setSubmitAmount("");
      setSubmitNote("");
      setSelectedPairId("");
      qc.invalidateQueries({ queryKey: ["carrier-offers", companyId] });
      qc.invalidateQueries({ queryKey: ["available-freight", companyId] });
    },
    onError: (e: Error) => toast({ title: "Submit failed", description: e.message, variant: "destructive" }),
  });

  // Counter mutation
  const counterMut = useMutation({
    mutationFn: (body: { amount: number; note?: string }) =>
      api.post<CarrierOffer>(
        `/carrier-companies/${companyId}/offers/${activeOffer?.id}/counter`,
        body
      ),
    onSuccess: () => {
      toast({ title: "Counter-offer sent" });
      setActionDialog(null);
      qc.invalidateQueries({ queryKey: ["carrier-offers", companyId] });
    },
    onError: (e: Error) => toast({ title: "Counter failed", description: e.message, variant: "destructive" }),
  });

  // Accept mutation (carrier accepts shipper's counter)
  const acceptMut = useMutation({
    mutationFn: () =>
      api.post<CarrierOffer>(
        `/carrier-companies/${companyId}/offers/${activeOffer?.id}/accept`,
        {}
      ),
    onSuccess: () => {
      toast({ title: "Offer accepted!", description: "Commercial agreement reached." });
      setActionDialog(null);
      qc.invalidateQueries({ queryKey: ["carrier-offers", companyId] });
    },
    onError: (e: Error) => toast({ title: "Accept failed", description: e.message, variant: "destructive" }),
  });

  // Withdraw mutation
  const withdrawMut = useMutation({
    mutationFn: () =>
      api.post<CarrierOffer>(
        `/carrier-companies/${companyId}/offers/${activeOffer?.id}/withdraw`,
        {}
      ),
    onSuccess: () => {
      toast({ title: "Offer withdrawn" });
      setActionDialog(null);
      qc.invalidateQueries({ queryKey: ["carrier-offers", companyId] });
    },
    onError: (e: Error) => toast({ title: "Withdraw failed", description: e.message, variant: "destructive" }),
  });

  const canSubmitOffer =
    isCommercialRole &&
    isFreightReady &&
    Boolean(selectedPair) &&
    (fixedPrice || Boolean(submitAmount)) &&
    !submitMut.isPending;
  const handleSubmitOffer = () => {
    if (!canSubmitOffer || !selectedPair || !selectedFreight) return;
    submitMut.mutate({
      amount: fixedPrice ? (selectedFreight.budget ?? 0) : parseFloat(submitAmount),
      driverUserId: selectedPair.driver.driverUserId,
      vehicleId: selectedPair.vehicle.id,
      note: submitNote || undefined,
    });
  };
  const myTurn = (o: CarrierOffer) => o.nextActor === "CARRIER";

  return (
    <div className="space-y-6">
      {!freightId && <AvailableFreightBoard companyId={companyId} />}
      {/* Submit new offer */}
      {freightId && isCommercialRole && (
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="space-y-2">
            <CardTitle className="flex items-center gap-2 text-base">
              <Send className="h-4 w-4" /> Apply to work
            </CardTitle>
            <p className="text-sm leading-6 text-muted-foreground">Submit your price with an eligible pre-assigned driver and approved vehicle.</p>
          </CardHeader>
          <CardContent className="space-y-3">
            {isFreightLoading || (isFreightFetching && !isFreightError) ? (
              <p role="status" className="rounded-lg border border-border/70 bg-muted/20 p-3 text-sm text-muted-foreground">
                Loading and verifying freight details…
              </p>
            ) : isFreightError ? (
              <div role="alert" className="flex flex-col gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-destructive">Unable to verify this freight. Retry before submitting an offer.</p>
                <Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => void refetchFreight()}>
                  Retry freight details
                </Button>
              </div>
            ) : !hasVerifiedFreight ? (
              <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                Freight could not be verified for this route. An offer cannot be submitted.
              </p>
            ) : !isFreightPosted ? (
              <p role="status" className="rounded-lg border border-border/70 bg-muted/20 p-3 text-sm text-muted-foreground">
                This freight is no longer posted and is not open for new offers.
              </p>
            ) : null}
            {!showSubmit ? (
              <Button type="button" disabled={!isFreightReady} onClick={() => setShowSubmit(true)} className="min-h-11 w-full sm:w-auto">
                {isFreightLoading || isFreightFetching ? "Verifying freight…" : `Apply for Freight #${freightId}`}
              </Button>
            ) : (
              <div className="max-w-2xl space-y-4 rounded-xl border border-border/70 bg-muted/20 p-4 sm:p-5">
                {activePairs.length === 0 ? <p className="rounded-lg border border-border/70 bg-background p-3 text-sm leading-6 text-muted-foreground">No eligible pre-assigned driver and approved vehicle are available. Pair a vehicle with an active driver in the company workspace, then try again.</p> : <div className="space-y-2">
                  <Label htmlFor="application-pair">Pre-assigned driver and vehicle</Label>
                  <select id="application-pair" value={selectedPairId} onChange={event => setSelectedPairId(event.target.value)} className="min-h-11 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2">
                    <option value="">Select a driver and vehicle</option>
                    {activePairs.map(({ pair, driver, vehicle }) => <option key={pair.id} value={pair.id}>{driver.fullName} · {vehicle.plateNumber} ({vehicle.truckType})</option>)}
                  </select>
                </div>}
                {fixedPrice ? <p className="text-sm text-muted-foreground">This load has a fixed price. Apply at {fmt(selectedFreight?.budget ?? null)}; the amount is set by the shipper.</p> : <div>
                  <Label htmlFor="sub-amount">Your price (ETB)</Label>
                  <Input
                    id="sub-amount"
                    type="number"
                    step="0.01"
                    min="1"
                    placeholder="Enter the price you agree to work for"
                    className="mt-1 min-h-11 bg-background"
                    value={submitAmount}
                    onChange={e => setSubmitAmount(e.target.value)}
                  />
                </div>}
                <div>
                  <Label htmlFor="sub-note">Note (optional)</Label>
                  <Textarea
                    id="sub-note"
                    placeholder="Any remarks for the shipper…"
                    rows={2}
                    className="mt-1 bg-background"
                    value={submitNote}
                    onChange={e => setSubmitNote(e.target.value)}
                  />
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button
                    type="button"
                    className="min-h-11"
                    disabled={!canSubmitOffer}
                    onClick={handleSubmitOffer}
                  >
                    {submitMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
                    Send application
                  </Button>
                  <Button type="button" variant="outline" className="min-h-11" onClick={() => setShowSubmit(false)}>
                    Cancel
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Existing offers */}
      <section aria-labelledby="carrier-offers-heading" className="space-y-3">
        <div>
          <h2 id="carrier-offers-heading" className="text-lg font-semibold tracking-tight">Your offers</h2>
          <p className="mt-1 text-sm text-muted-foreground">Review offer status, agreed amounts, and negotiation history.</p>
        </div>

        {isLoading ? (
          <div className="space-y-3">
            {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full rounded-lg" />)}
          </div>
        ) : isError ? (
          <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="break-words text-sm text-destructive">{error instanceof Error ? error.message : "Unable to load your offers."}</p>
            <Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => refetch()}>Retry offers</Button>
          </div>
        ) : offers.length === 0 ? (
          <Card className="border-dashed shadow-none"><CardContent className="p-5"><p className="font-medium">No offers yet</p><p className="mt-1 text-sm leading-6 text-muted-foreground">Offers submitted by this company will appear here.</p></CardContent></Card>
        ) : (
          <div className="space-y-3">
            {offers.map(offer => (
              <OfferCard
                key={offer.id}
                offer={offer}
                viewAs="carrier"
                allowCarrierActions={isCommercialRole}
                myTurn={myTurn(offer)}
                onCounter={() => { setActiveOffer(offer); setCounterAmt(""); setCounterNote(""); setActionDialog("counter"); }}
                onAccept={() => { setActiveOffer(offer); setActionDialog("accept"); }}
                onWithdraw={() => { setActiveOffer(offer); setActionDialog("withdraw"); }}
              />
            ))}
          </div>
        )}
      </section>

      {/* Counter dialog */}
      <Dialog open={actionDialog === "counter"} onOpenChange={o => !o && setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Submit Counter-Offer</DialogTitle>
            <DialogDescription>
              Current amount: {fmt(activeOffer?.currentAmount ?? null)}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div>
              <Label htmlFor="ctr-amount">Your Counter Amount (ETB)</Label>
              <Input
                id="ctr-amount"
                type="number"
                step="0.01"
                min="1"
                value={counterAmt}
                onChange={e => setCounterAmt(e.target.value)}
                autoFocus
              />
            </div>
            <div>
              <Label htmlFor="ctr-note">Note (optional)</Label>
              <Textarea
                id="ctr-note"
                rows={2}
                value={counterNote}
                onChange={e => setCounterNote(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>Cancel</Button>
            <Button
              disabled={!counterAmt || counterMut.isPending}
              onClick={() => counterMut.mutate({ amount: parseFloat(counterAmt), note: counterNote || undefined })}
            >
              {counterMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
              Send Counter
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Accept dialog */}
      <Dialog open={actionDialog === "accept"} onOpenChange={o => !o && setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Accept Shipper's Counter-Offer?</DialogTitle>
            <DialogDescription>
              You are accepting {fmt(activeOffer?.currentAmount ?? null)}. This creates a commercial agreement.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>Cancel</Button>
            <Button
              disabled={acceptMut.isPending}
              onClick={() => acceptMut.mutate()}
            >
              {acceptMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
              Accept Agreement
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Withdraw dialog */}
      <Dialog open={actionDialog === "withdraw"} onOpenChange={o => !o && setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Withdraw Offer?</DialogTitle>
            <DialogDescription>
              This will withdraw your offer on freight #{activeOffer?.freightId}. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>Cancel</Button>
            <Button
              variant="destructive"
              disabled={withdrawMut.isPending}
              onClick={() => withdrawMut.mutate()}
            >
              {withdrawMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
              Withdraw
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function AvailableFreightBoard({ companyId }: { companyId: number }) {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["available-freight", companyId],
    queryFn: () => api.get<{ content: FreightBoardItem[]; totalElements: number }>(
      `/carrier-companies/${companyId}/available-freight?page=0&size=50`
    ),
    refetchInterval: 30_000,
  });
  const freight = data?.content ?? [];
  return <section className="space-y-4" aria-labelledby="available-freight-heading">
    <header className="border-b border-border/70 pb-4"><p className="text-sm font-medium text-muted-foreground">Company operations</p><h1 id="available-freight-heading" className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Freight &amp; offers</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">Browse loads that are accepting offers and review your company’s recent submissions.</p></header>
    <div><h2 className="text-lg font-semibold tracking-tight">Open freight</h2><p className="mt-1 text-sm text-muted-foreground">Loads posted by shippers that are still accepting offers.</p></div>
    {isLoading ? <div className="space-y-3">{[1, 2].map(i => <Skeleton key={i} className="h-28 w-full rounded-lg" />)}</div> : isError ? <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="break-words text-sm text-destructive">{error instanceof Error ? error.message : "Unable to load open freight."}</p><Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => refetch()}>Retry open freight</Button></div> : freight.length === 0 ? <Card className="border-dashed shadow-none"><CardContent className="p-5"><p className="font-medium">No open loads right now</p><p className="mt-1 text-sm leading-6 text-muted-foreground">New shipper posts will appear here automatically.</p></CardContent></Card> : <div className="space-y-3">{freight.map(item => <Card key={item.id} className="border-border/70 shadow-sm"><CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5"><div className="min-w-0 space-y-2">    <p className="break-words font-semibold">{item.pickupLocation && item.destination ? `${item.pickupLocation} → ${item.destination}` : `Freight #${item.id} · Route details shared after award`}</p><p className="break-words text-sm text-muted-foreground">{item.materialType} · {item.weightTons} tons{item.vehicleTypeNeeded ? ` · ${item.vehicleTypeNeeded}` : ""}</p><div className="flex flex-wrap items-center gap-2 text-sm"><Badge variant="outline">{item.priceType === "NEGOTIABLE" ? "Negotiable" : "Fixed price"}</Badge>{item.priceType === "FIXED" && item.budget != null && <span className="font-semibold">{fmt(item.budget)}</span>}{item.preferredDate && <span className="text-muted-foreground">Pickup {item.preferredDate}</span>}</div></div><Button asChild className="min-h-11 w-full shrink-0 sm:w-auto"><Link href={`/carrier-offers/company/${companyId}/freight/${item.id}`}>{item.priceType === "NEGOTIABLE" ? "Make an offer" : "Apply at fixed price"}</Link></Button></CardContent></Card>)}</div>}
  </section>;
}

// ── Shipper view: all offers on a specific freight ────────────────────────────

function ShipperOfferInbox({ freightId }: { freightId: number }) {
  const { toast } = useToast();
  const qc = useQueryClient();

  const [counterAmt, setCounterAmt]   = useState("");
  const [counterNote, setCounterNote] = useState("");
  const [activeOffer, setActiveOffer] = useState<CarrierOffer | null>(null);
  const [actionDialog, setActionDialog] = useState<
    "counter" | "accept" | "reject" | null
  >(null);

  const { data: offers = [], isLoading } = useQuery({
    queryKey: ["freight-carrier-offers", freightId],
    queryFn: () =>
      api.get<CarrierOffer[]>(`/freight/${freightId}/carrier-offers`),
    refetchInterval: 20_000,
  });

  const counterMut = useMutation({
    mutationFn: (body: { amount: number; note?: string }) =>
      api.post<CarrierOffer>(
        `/freight/${freightId}/carrier-offers/${activeOffer?.id}/counter`,
        body
      ),
    onSuccess: () => {
      toast({ title: "Counter-offer sent" });
      setActionDialog(null);
      qc.invalidateQueries({ queryKey: ["freight-carrier-offers", freightId] });
    },
    onError: (e: Error) => toast({ title: "Counter failed", description: e.message, variant: "destructive" }),
  });

  const acceptMut = useMutation({
    mutationFn: () =>
      api.post<CarrierOffer>(
        `/freight/${freightId}/carrier-offers/${activeOffer?.id}/accept`,
        {}
      ),
    onSuccess: () => {
      toast({ title: "Carrier accepted!", description: "Commercial award granted." });
      setActionDialog(null);
      qc.invalidateQueries({ queryKey: ["freight-carrier-offers", freightId] });
    },
    onError: (e: Error) => toast({ title: "Accept failed", description: e.message, variant: "destructive" }),
  });

  const rejectMut = useMutation({
    mutationFn: () =>
      api.post<CarrierOffer>(
        `/freight/${freightId}/carrier-offers/${activeOffer?.id}/reject`,
        {}
      ),
    onSuccess: () => {
      toast({ title: "Offer rejected" });
      setActionDialog(null);
      qc.invalidateQueries({ queryKey: ["freight-carrier-offers", freightId] });
    },
    onError: (e: Error) => toast({ title: "Reject failed", description: e.message, variant: "destructive" }),
  });

  const myTurn = (o: CarrierOffer) => o.nextActor === "SHIPPER";

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
        Carrier Offers ({offers.length})
      </h3>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full rounded-lg" />)}
        </div>
      ) : offers.length === 0 ? (
        <p className="text-sm text-muted-foreground">No carrier offers yet for this shipment.</p>
      ) : (
        <div className="space-y-3">
          {offers.map(offer => (
            <OfferCard
              key={offer.id}
              offer={offer}
              viewAs="shipper"
              myTurn={myTurn(offer)}
              onCounter={() => { setActiveOffer(offer); setCounterAmt(""); setCounterNote(""); setActionDialog("counter"); }}
              onAccept={() => { setActiveOffer(offer); setActionDialog("accept"); }}
              onReject={() => { setActiveOffer(offer); setActionDialog("reject"); }}
            />
          ))}
        </div>
      )}

      {/* Counter dialog */}
      <Dialog open={actionDialog === "counter"} onOpenChange={o => !o && setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Counter {activeOffer?.carrierCompanyName}</DialogTitle>
            <DialogDescription>
              Their offer: {fmt(activeOffer?.currentAmount ?? null)}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div>
              <Label>Your Counter Amount (ETB)</Label>
              <Input
                type="number"
                step="0.01"
                min="1"
                value={counterAmt}
                onChange={e => setCounterAmt(e.target.value)}
                autoFocus
              />
            </div>
            <div>
              <Label>Note (optional)</Label>
              <Textarea rows={2} value={counterNote} onChange={e => setCounterNote(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>Cancel</Button>
            <Button
              disabled={!counterAmt || counterMut.isPending}
              onClick={() => counterMut.mutate({ amount: parseFloat(counterAmt), note: counterNote || undefined })}
            >
              {counterMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
              Send Counter
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Accept dialog */}
      <Dialog open={actionDialog === "accept"} onOpenChange={o => !o && setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Accept {activeOffer?.carrierCompanyName}?</DialogTitle>
            <DialogDescription>
              Agreed price: {fmt(activeOffer?.currentAmount ?? null)}.
              This creates a commercial award. Other carrier offers will be closed.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>Cancel</Button>
            <Button disabled={acceptMut.isPending} onClick={() => acceptMut.mutate()}>
              {acceptMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
              <CheckCircle2 className="mr-1 h-4 w-4" />
              Accept Carrier
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reject dialog */}
      <Dialog open={actionDialog === "reject"} onOpenChange={o => !o && setActionDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject offer from {activeOffer?.carrierCompanyName}?</DialogTitle>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setActionDialog(null)}>Cancel</Button>
            <Button variant="destructive" disabled={rejectMut.isPending} onClick={() => rejectMut.mutate()}>
              {rejectMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
              Reject
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ── Shared offer card ─────────────────────────────────────────────────────────

interface OfferCardProps {
  offer: CarrierOffer;
  viewAs: "carrier" | "shipper";
  myTurn: boolean;
  allowCarrierActions?: boolean;
  onCounter?: () => void;
  onAccept?: () => void;
  onReject?: () => void;
  onWithdraw?: () => void;
}

function OfferCard({
  offer,
  viewAs,
  myTurn,
  allowCarrierActions = true,
  onCounter,
  onAccept,
  onReject,
  onWithdraw,
}: OfferCardProps) {
  const isActive = offer.status === "PENDING" || offer.status === "NEGOTIATING";
  const isFixed  = offer.pricingType === "FIXED";

  return (
    <Card className={`border-border/70 shadow-sm ${offer.status === "ACCEPTED" ? "border-primary/40" : ""}`}>
      <CardContent className="space-y-4 p-4 sm:p-5">
        {/* Header row */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            {viewAs === "shipper" && (
              <p className="break-words font-semibold text-sm">{offer.carrierCompanyName}</p>
            )}
            {viewAs === "carrier" && (
                  <p className="break-words text-sm text-muted-foreground">
                    Freight #{offer.freightId}
                    {offer.freightPickupLocation && offer.freightDestination
                      ? ` — ${offer.freightPickupLocation} → ${offer.freightDestination}`
                      : offer.status === "ACCEPTED"
                        ? " — Operational route available in dispatch details"
                        : " — Route details shared after award"}
                  </p>
            )}
            <div className="mt-2 flex flex-wrap items-center gap-2">
              <span className="text-xl font-semibold tracking-tight">{fmt(offer.currentAmount)}</span>
              {isFixed && (
                <Badge variant="outline" className="text-xs">Fixed Price</Badge>
              )}
            </div>
          </div>
          <Badge className={`self-start border text-xs ${STATUS_COLORS[offer.status]}`}>
            {offer.status}
          </Badge>
        </div>

        {/* Proposed pairing is not the operational dispatch assignment. */}
        {viewAs === "shipper" && (offer.proposedDriverName || offer.proposedVehiclePlate) && (
          <div className="grid gap-3 rounded-lg border border-border/70 bg-muted/30 p-3 text-sm sm:grid-cols-2">
            <p className="break-words"><span className="text-muted-foreground">Proposed driver:</span> {offer.proposedDriverName ?? `Driver #${offer.proposedDriverUserId}`}</p>
            <p className="break-words"><span className="text-muted-foreground">Proposed vehicle:</span> {offer.proposedVehiclePlate ?? `Vehicle #${offer.proposedVehicleId}`} {offer.proposedVehicleType ? `· ${offer.proposedVehicleType}` : ""}</p>
          </div>
        )}

        {/* Commercial award and operational assignment have separate states. */}
        {offer.status === "ACCEPTED" && offer.agreedAmount && (
          <div className="flex items-start gap-3 rounded-lg border border-primary/20 bg-primary/5 px-3 py-3 text-sm">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
            <div>
              <p>Commercial agreement: <strong>{fmt(offer.agreedAmount)}</strong></p>
              <p className="mt-1 text-muted-foreground">
                {offer.assignmentStatus
                  ? ASSIGNMENT_STATUS_LABELS[offer.assignmentStatus]
                  : "Operational assignment status unavailable"}
              </p>
            </div>
          </div>
        )}

        {/* Turn indicator */}
        {isActive && (
          <p className="flex items-center gap-1 text-xs text-muted-foreground">
            <Clock className="h-3 w-3" />
            {myTurn ? "Your turn to respond" : `Waiting for ${offer.nextActor?.toLowerCase()}`}
          </p>
        )}

        {/* Action buttons */}
        {isActive && myTurn && (viewAs === "shipper" || allowCarrierActions) && (
          <div className="flex flex-wrap gap-2 border-t border-border/70 pt-3">
            {viewAs === "carrier" && (
              <>
                {!isFixed && (
                  <Button type="button" size="sm" variant="outline" className="min-h-10" onClick={onCounter}>
                    <MessageSquare className="mr-1 h-3 w-3" /> Counter
                  </Button>
                )}
                {offer.status === "NEGOTIATING" && (
                  <Button type="button" size="sm" className="min-h-10" onClick={onAccept}>
                    <CheckCircle2 className="mr-1 h-3 w-3" /> Accept
                  </Button>
                )}
              </>
            )}
            {viewAs === "shipper" && (
              <>
                {!isFixed && (
                  <Button type="button" size="sm" variant="outline" className="min-h-10" onClick={onCounter}>
                    <MessageSquare className="mr-1 h-3 w-3" /> Counter
                  </Button>
                )}
                <Button type="button" size="sm" className="min-h-10" onClick={onAccept}>
                  <CheckCircle2 className="mr-1 h-3 w-3" /> Accept
                </Button>
                <Button type="button" size="sm" variant="outline" className="min-h-10" onClick={onReject}>
                  <XCircle className="mr-1 h-3 w-3" /> Reject
                </Button>
              </>
            )}
            {viewAs === "carrier" && (
              <Button type="button" size="sm" variant="ghost" className="min-h-10 text-muted-foreground" onClick={onWithdraw}>
                Withdraw
              </Button>
            )}
          </div>
        )}

        {/* Withdrawal allowed when it's not your turn too */}
        {isActive && !myTurn && viewAs === "carrier" && allowCarrierActions && (
          <div className="pt-1">
            <Button type="button" size="sm" variant="ghost" className="min-h-10 text-muted-foreground text-xs" onClick={onWithdraw}>
              Withdraw offer
            </Button>
          </div>
        )}

        {/* Negotiation history accordion */}
        {offer.negotiationHistory.length > 0 && (
          <Accordion type="single" collapsible>
            <AccordionItem value="history" className="border-0">
              <AccordionTrigger className="min-h-11 py-2 text-xs text-muted-foreground">
                <span className="flex items-center gap-1">
                  <History className="h-3 w-3" />
                  Negotiation history ({offer.negotiationHistory.length})
                </span>
              </AccordionTrigger>
              <AccordionContent>
                <div className="space-y-2 pt-1">
                  {offer.negotiationHistory.map((entry, idx) => (
                    <div
                      key={entry.id}
                      className="flex flex-col gap-1 rounded-lg border border-border/60 bg-muted/20 p-3 text-xs sm:flex-row sm:items-start sm:gap-3"
                    >
                      <span className="shrink-0 font-mono w-5 text-muted-foreground">
                        {idx + 1}.
                      </span>
                      <div className="flex-1">
                        <span className="font-medium">{entry.actorType}</span>
                        {" — "}
                        <span>{ACTION_LABELS[entry.action]}</span>
                        {entry.amount != null && (
                          <span className="ml-1 font-semibold">{fmt(entry.amount)}</span>
                        )}
                        {entry.note && <span className="ml-1 break-words italic text-muted-foreground">"{entry.note}"</span>}
                      </div>
                      <span className="shrink-0 text-muted-foreground">{timeAgo(entry.createdAt)}</span>
                    </div>
                  ))}
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        )}

        <p className="border-t border-border/70 pt-3 text-xs text-muted-foreground">Submitted {timeAgo(offer.createdAt)}</p>
      </CardContent>
    </Card>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

/**
 * Route params:
 *   /carrier-offers/freight/:freightId          — shipper view (all offers on freight)
 *   /carrier-offers/company/:companyId          — carrier portal (company's offers)
 *   /carrier-offers/company/:companyId/freight/:freightId — carrier submitting on a freight
 */
export default function CarrierOffersPage(_props: {companyId?: string; freightId?: string}) {
  const params = useParams<{
    freightId?: string;
    companyId?: string;
  }>();
  const { user } = useAuth();
  const [, navigate] = useLocation();

  const freightId = params.freightId ? parseInt(params.freightId) : undefined;
  const companyId = params.companyId ? parseInt(params.companyId) : undefined;

  // Determine view based on role and params
  const isShipperView = user?.role === "shipper" && freightId != null && companyId == null;
  const isCarrierView = companyId != null;

  if (!isShipperView && !isCarrierView) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-8">
        <p className="text-muted-foreground">
          Invalid route. Use{" "}
          <code>/carrier-offers/freight/:id</code> for shipper view or{" "}
          <code>/carrier-offers/company/:id</code> for carrier view.
        </p>
      </div>
    );
  }

  return (
    <main className="container mx-auto max-w-6xl space-y-6 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      {/* Back nav */}
      <header className="flex flex-col gap-3 border-b border-border/70 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">{isShipperView ? "Shipment offers" : "Company operations"}</p>
          <h1 className="mt-1 break-words text-2xl font-bold tracking-tight sm:text-3xl">
            {isShipperView ? `Carrier offers · Shipment #${freightId}` : `Freight & offers · Company #${companyId}`}
          </h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">{isShipperView ? "Review and respond to offers submitted for this shipment." : freightId ? "Review the load and submit or negotiate your company’s offer." : "Browse available loads and track your company’s offers."}</p>
        </div>
        <Button variant="outline" className="min-h-11 w-full shrink-0 sm:w-auto" asChild>
          <Link href={freightId ? `/freight/${freightId}` : "/dashboard"}>
            <ArrowLeft className="mr-1 h-4 w-4" /> {freightId ? "Back to shipment" : "Company workspaces"}
          </Link>
        </Button>
      </header>

      {isShipperView && freightId && (
        <ShipperOfferInbox freightId={freightId} />
      )}

      {isCarrierView && companyId && (
        <CarrierOfferPortal companyId={companyId} freightId={freightId} />
      )}
    </main>
  );
}
