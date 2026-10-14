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
import { Separator } from "@/components/ui/separator";
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
  Banknote,
  CheckCircle2,
  Clock,
  History,
  Loader2,
  MessageSquare,
  Package,
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
  pickupLocation: string;
  destination: string;
  materialType: string;
  weightTons: number;
  budget: number;
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
  freightPickupLocation: string;
  freightDestination: string;
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
  nextActor: ActorType | null;
  agreedAmount: number | null;
  acceptedAt: string | null;
  rejectedAt: string | null;
  withdrawnAt: string | null;
  createdAt: string;
  updatedAt: string;
  negotiationHistory: NegotiationEntry[];
}

// ── Status helpers ─────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<OfferStatus, string> = {
  PENDING:     "bg-blue-50 text-blue-700 border-blue-200",
  NEGOTIATING: "bg-amber-50 text-amber-700 border-amber-200",
  ACCEPTED:    "bg-emerald-50 text-emerald-700 border-emerald-200",
  REJECTED:    "bg-red-50 text-red-700 border-red-200",
  WITHDRAWN:   "bg-gray-50 text-gray-600 border-gray-200",
  SUPERSEDED:  "bg-gray-50 text-gray-400 border-gray-200",
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
  const { data: offersPage, isLoading } = useQuery({
    queryKey: ["carrier-offers", companyId],
    queryFn: () =>
      api.get<{ content: CarrierOffer[]; totalElements: number }>(
        `/carrier-companies/${companyId}/offers?page=0&size=50`
      ),
    refetchInterval: 30_000,
  });

  const offers = offersPage?.content ?? [];
  const { data: selectedFreight } = useQuery({
    queryKey: ["freight", freightId],
    queryFn: () => api.get<FreightBoardItem>(`/freight/${freightId}`),
    enabled: freightId != null,
  });
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

  const { memberships } = useAuth();
  const isCommercialRole = memberships.some(m => m.carrierCompanyId === companyId && ["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"].includes(m.role));
  const myTurn = (o: CarrierOffer) => o.nextActor === "CARRIER";

  return (
    <div className="space-y-6">
      {!freightId && <AvailableFreightBoard companyId={companyId} />}
      {/* Submit new offer */}
      {freightId && isCommercialRole && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Send className="h-4 w-4" /> Apply to work
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {!showSubmit ? (
              <Button onClick={() => setShowSubmit(true)} size="sm">
                Apply for Freight #{freightId}
              </Button>
            ) : (
              <div className="space-y-3 max-w-sm">
                {activePairs.length === 0 ? <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">No eligible pre-assigned driver and approved vehicle are available. Pair a vehicle with an active driver in the company workspace, then try again.</p> : <div>
                  <Label htmlFor="application-pair">Pre-assigned driver and vehicle</Label>
                  <select id="application-pair" value={selectedPairId} onChange={event => setSelectedPairId(event.target.value)} className="mt-1 h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
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
                    value={submitNote}
                    onChange={e => setSubmitNote(e.target.value)}
                  />
                </div>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    disabled={!selectedPair || (!fixedPrice && !submitAmount) || submitMut.isPending}
                    onClick={() =>
                      submitMut.mutate({
                        amount: fixedPrice ? (selectedFreight?.budget ?? 0) : parseFloat(submitAmount),
                        driverUserId: selectedPair!.driver.driverUserId,
                        vehicleId: selectedPair!.vehicle.id,
                        note: submitNote || undefined,
                      })
                    }
                  >
                    {submitMut.isPending && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
                    Send application
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setShowSubmit(false)}>
                    Cancel
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Existing offers */}
      <div>
        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-3">
          Active &amp; Recent Offers
        </h3>

        {isLoading ? (
          <div className="space-y-3">
            {[1, 2].map(i => <Skeleton key={i} className="h-20 w-full rounded-lg" />)}
          </div>
        ) : offers.length === 0 ? (
          <p className="text-sm text-muted-foreground">No offers yet.</p>
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
      </div>

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
  const { data, isLoading, isError } = useQuery({
    queryKey: ["available-freight", companyId],
    queryFn: () => api.get<{ content: FreightBoardItem[]; totalElements: number }>(
      `/carrier-companies/${companyId}/available-freight?page=0&size=50`
    ),
    refetchInterval: 30_000,
  });
  const freight = data?.content ?? [];
  return <section className="space-y-3" aria-labelledby="available-freight-heading">
    <div><h2 id="available-freight-heading" className="text-lg font-semibold">Open freight</h2><p className="text-sm text-muted-foreground">Loads posted by shippers that are still accepting offers.</p></div>
    {isLoading ? <div className="space-y-3">{[1, 2].map(i => <Skeleton key={i} className="h-28 w-full rounded-lg" />)}</div> : isError ? <p role="alert" className="rounded-lg bg-destructive/10 p-4 text-sm text-destructive">Unable to load open freight. Refresh and try again.</p> : freight.length === 0 ? <Card><CardContent className="p-5"><p className="font-medium">No open loads right now</p><p className="mt-1 text-sm text-muted-foreground">New shipper posts will appear here automatically.</p></CardContent></Card> : <div className="space-y-3">{freight.map(item => <Card key={item.id}><CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0 space-y-1"><p className="font-semibold">{item.pickupLocation} <span aria-hidden="true" className="text-muted-foreground">→</span> {item.destination}</p><p className="text-sm text-muted-foreground">{item.materialType} · {item.weightTons} tons{item.vehicleTypeNeeded ? ` · ${item.vehicleTypeNeeded}` : ""}</p><p className="text-sm"><Badge variant="outline">{item.priceType === "NEGOTIABLE" ? "Negotiable" : "Fixed price"}</Badge><span className="ml-2 font-medium">{fmt(item.budget)}</span>{item.preferredDate && <span className="ml-2 text-muted-foreground">Pickup {item.preferredDate}</span>}</p></div><Button asChild className="shrink-0"><Link href={`/carrier-offers/company/${companyId}/freight/${item.id}`}>{item.priceType === "NEGOTIABLE" ? "Make an offer" : "Apply at fixed price"}</Link></Button></CardContent></Card>)}</div>}
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
    <Card className={`border ${offer.status === "ACCEPTED" ? "border-emerald-300" : ""}`}>
      <CardContent className="p-4 space-y-3">
        {/* Header row */}
        <div className="flex items-start justify-between gap-3">
          <div>
            {viewAs === "shipper" && (
              <p className="font-semibold text-sm">{offer.carrierCompanyName}</p>
            )}
            {viewAs === "carrier" && (
              <p className="text-sm text-muted-foreground">
                Freight #{offer.freightId} — {offer.freightPickupLocation} → {offer.freightDestination}
              </p>
            )}
            <div className="flex items-center gap-2 mt-1">
              <span className="text-lg font-bold">{fmt(offer.currentAmount)}</span>
              {isFixed && (
                <Badge variant="outline" className="text-xs">Fixed Price</Badge>
              )}
            </div>
          </div>
          <Badge className={`text-xs border ${STATUS_COLORS[offer.status]}`}>
            {offer.status}
          </Badge>
        </div>

        {/* Agreed amount banner */}
        {viewAs === "shipper" && (offer.proposedDriverName || offer.proposedVehiclePlate) && (
          <div className="grid gap-2 rounded-lg bg-muted/50 p-3 text-sm sm:grid-cols-2">
            <p><span className="text-muted-foreground">Assigned driver:</span> {offer.proposedDriverName ?? `Driver #${offer.proposedDriverUserId}`}</p>
            <p><span className="text-muted-foreground">Assigned vehicle:</span> {offer.proposedVehiclePlate ?? `Vehicle #${offer.proposedVehicleId}`} {offer.proposedVehicleType ? `· ${offer.proposedVehicleType}` : ""}</p>
          </div>
        )}

        {/* Agreed amount banner */}
        {offer.status === "ACCEPTED" && offer.agreedAmount && (
          <div className="flex items-center gap-2 rounded-md bg-emerald-50 px-3 py-2 text-emerald-800 text-sm">
            <ShieldCheck className="h-4 w-4 shrink-0" />
            Commercial agreement: <strong>{fmt(offer.agreedAmount)}</strong>
          </div>
        )}

        {/* Turn indicator */}
        {isActive && (
          <p className="text-xs text-muted-foreground flex items-center gap-1">
            <Clock className="h-3 w-3" />
            {myTurn ? "Your turn to respond" : `Waiting for ${offer.nextActor?.toLowerCase()}`}
          </p>
        )}

        {/* Action buttons */}
        {isActive && myTurn && (viewAs === "shipper" || allowCarrierActions) && (
          <div className="flex flex-wrap gap-2 pt-1">
            {viewAs === "carrier" && (
              <>
                {!isFixed && (
                  <Button size="sm" variant="outline" onClick={onCounter}>
                    <MessageSquare className="mr-1 h-3 w-3" /> Counter
                  </Button>
                )}
                {offer.status === "NEGOTIATING" && (
                  <Button size="sm" onClick={onAccept}>
                    <CheckCircle2 className="mr-1 h-3 w-3" /> Accept
                  </Button>
                )}
              </>
            )}
            {viewAs === "shipper" && (
              <>
                {!isFixed && (
                  <Button size="sm" variant="outline" onClick={onCounter}>
                    <MessageSquare className="mr-1 h-3 w-3" /> Counter
                  </Button>
                )}
                <Button size="sm" onClick={onAccept}>
                  <CheckCircle2 className="mr-1 h-3 w-3" /> Accept
                </Button>
                <Button size="sm" variant="outline" className="text-red-600 border-red-200 hover:bg-red-50" onClick={onReject}>
                  <XCircle className="mr-1 h-3 w-3" /> Reject
                </Button>
              </>
            )}
            {viewAs === "carrier" && (
              <Button size="sm" variant="ghost" className="text-muted-foreground" onClick={onWithdraw}>
                Withdraw
              </Button>
            )}
          </div>
        )}

        {/* Withdrawal allowed when it's not your turn too */}
        {isActive && !myTurn && viewAs === "carrier" && allowCarrierActions && (
          <div className="pt-1">
            <Button size="sm" variant="ghost" className="text-muted-foreground text-xs" onClick={onWithdraw}>
              Withdraw offer
            </Button>
          </div>
        )}

        {/* Negotiation history accordion */}
        {offer.negotiationHistory.length > 0 && (
          <Accordion type="single" collapsible>
            <AccordionItem value="history" className="border-0">
              <AccordionTrigger className="text-xs text-muted-foreground py-1">
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
                      className={`flex items-start gap-3 text-xs ${
                        entry.actorType === "CARRIER"
                          ? "text-blue-800"
                          : "text-amber-800"
                      }`}
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
                        {entry.note && (
                          <span className="ml-1 italic text-muted-foreground">"{entry.note}"</span>
                        )}
                      </div>
                      <span className="shrink-0 text-muted-foreground">{timeAgo(entry.createdAt)}</span>
                    </div>
                  ))}
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        )}

        <p className="text-xs text-muted-foreground">{timeAgo(offer.createdAt)}</p>
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
    <div className="max-w-3xl mx-auto px-4 py-8 space-y-6">
      {/* Back nav */}
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link href={freightId ? `/freight/${freightId}` : "/dashboard"}>
            <ArrowLeft className="mr-1 h-4 w-4" /> Back
          </Link>
        </Button>
        <h1 className="text-xl font-semibold">
          {isShipperView
            ? `Carrier Offers — Shipment #${freightId}`
            : `Offer Portal — Company #${companyId}`}
        </h1>
      </div>

      <Separator />

      {isShipperView && freightId && (
        <ShipperOfferInbox freightId={freightId} />
      )}

      {isCarrierView && companyId && (
        <CarrierOfferPortal companyId={companyId} freightId={freightId} />
      )}
    </div>
  );
}
