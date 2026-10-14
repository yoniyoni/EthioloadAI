import { useState } from "react";
import { useInfiniteQuery, useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useLocation } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { useToast } from "@/hooks/use-toast";
import {
  MapPin, Package, Scale, Calendar, Banknote, Truck, Star, Loader2,
  ArrowLeft, Zap, Users, Clock, Shield, Navigation, FileText, AlertTriangle, MessageSquare,
  ChevronRight, CheckCircle2, Route, DollarSign, Gavel, XCircle
} from "lucide-react";
import { Link } from "wouter";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";

const STATUS_COLORS: Record<string, string> = {
  posted: "bg-blue-50 text-blue-700 border-blue-200",
  matched: "bg-sky-50 text-sky-700 border-sky-200",
  in_transit: "bg-purple-50 text-purple-700 border-purple-200",
  completed: "bg-emerald-50 text-emerald-700 border-emerald-200",
  cancelled: "bg-red-50 text-red-700 border-red-200",
};

export default function FreightDetail() {
  const { id } = useParams<{ id: string }>();
  const { user, isAuthenticated } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const [, setLocation] = useLocation();

  const [applyForm, setApplyForm] = useState({ proposedPrice: "", message: "", vehicleId: "" });
  const [showApply, setShowApply] = useState(false);
  const [rejectBidId, setRejectBidId] = useState<number | null>(null);
  const [counterBid, setCounterBid] = useState<any | null>(null);
  const [counterAmount, setCounterAmount] = useState("");
  const [counterNote, setCounterNote] = useState("");
  const [bookingCreated, setBookingCreated] = useState<any | null>(null);

  const { data: freightRaw, isLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
    refetchInterval: 30_000,
  });

  // Normalise Spring Boot FreightResponse field names for the existing UI:
  // destination → deliveryLocation, materialType → cargoType
  const freight = freightRaw ? {
    ...freightRaw,
    deliveryLocation: freightRaw.deliveryLocation ?? freightRaw.destination,
    cargoType:        freightRaw.cargoType        ?? freightRaw.materialType,
  } : freightRaw;

  // Driver's own vehicles for bid submission
  const { data: myVehiclesData } = useQuery({
    queryKey: ["my-vehicles", user?.id],
    queryFn: () => api.get<any[]>("/vehicles/my-vehicles"),  // SB: GET /api/vehicles/my-vehicles
    enabled: user?.role === "driver",
  });
  const myVehicles = myVehiclesData ?? [];

  // Driver's own bids to check if this driver already bid on this freight
  const { data: driverBidsData } = useQuery({
    queryKey: ["driver-bids", user?.id],
    queryFn: () => api.get<any>("/driver/bids?size=100"), // SB: GET /api/driver/bids
    enabled: user?.role === "driver",
    refetchInterval: 15_000,
  });
  const driverBids: any[] = Array.isArray(driverBidsData?.content)
    ? driverBidsData.content
    : Array.isArray(driverBidsData)
    ? driverBidsData
    : [];
  const myExistingBid = driverBids.find(
    (b: any) => (Number(b.freightId ?? b.cargoRequestId) === Number(id))
  );

  const { data: aiMatches } = useQuery({
    queryKey: ["ai-matches", id],
    queryFn: () => api.post<{ matches: any[] }>("/ai/recommend-truck", {
      freight_id: Number(id),
      weight: freight?.weightTons || 0,
      cargo_type: freight?.cargoType || "general",
      budget: freight?.budget || undefined,
    }),
    enabled: !!freight && (user?.role === "admin" || user?.role === "shipper"),
  });

  const { data: aiPrice } = useQuery({
    queryKey: ["ai-price", id],
    queryFn: () => api.get<any>(`/ai/price-prediction?weight=${Number(freight?.weightTons || 0)}&distance_km=${Number(freight?.distanceKm || 300)}&cargo_type=${freight?.cargoType || "general"}`),
    enabled: !!freight,
  });

  const applicationsQuery = useInfiniteQuery({
    queryKey: ["applications", id],
    queryFn: ({ pageParam }) =>
      api.get<any>(`/freight/${id}/bids/page?size=20&page=${pageParam}`),
    initialPageParam: 0,
    getNextPageParam: (lastPage: any) => lastPage.nextPage ?? undefined,
    enabled: !!freight && (user?.role === "admin" || user?.role === "shipper"),
  });
  const refreshApplications = () => {
    qc.setQueryData(["applications", id], (previous: any) => previous
      ? { pages: previous.pages.slice(0, 1), pageParams: previous.pageParams.slice(0, 1) }
      : previous);
    void qc.invalidateQueries({ queryKey: ["applications", id] });
  };
  const applications = applicationsQuery.data ? {
    applications: applicationsQuery.data.pages.flatMap((page: any) =>
      (Array.isArray(page.content) ? page.content : []).map((b: any) => ({
          id:               b.id,
          driverId:         b.driverId,
          driverName:       b.driverName,
          driverPhone:      b.driverPhone,     // not in SB BidResponse — will be undefined
          proposedPrice:    b.amount,
          message:          b.note,
          status:           b.status,
          truckType:        b.vehicleType,     // SB: vehicleType
          plateNumber:      b.vehiclePlateNumber, // SB: vehiclePlateNumber
          driverRating:     b.driverRating,    // not in SB BidResponse
          driverTripCount:  b.driverTripCount, // not in SB BidResponse
          distanceKm:       b.distanceKm,
          bidderType:       b.bidderType,      // not in SB BidResponse
          isRecommended:    b.isRecommended,
          counterAmount:    b.counterAmount,
          counterNote:      b.counterNote,
          counterBy:        b.counterBy,
      })),
    ),
  } : undefined;

  const applyMutation = useMutation({
    // SB: POST /api/freight/{freightId}/bids  (was /cargo-requests/{id}/bids)
    // SB CreateBidRequest: vehicleId, amount, note, availableDatetime
    mutationFn: () => api.post(`/freight/${id}/bids`, {
      vehicleId: Number(applyForm.vehicleId),
      amount:    isFixedPrice ? Number(freight.budget) : Number(applyForm.proposedPrice),
      note:      applyForm.message || undefined,
    }),
    onSuccess: () => {
      toast({ 
        title: isFixedPrice ? "Application submitted!" : "Bid submitted!", 
        description: isFixedPrice ? "The shipper will review your application." : "The shipper will review your bid." 
      });
      setShowApply(false);
      setApplyForm({ proposedPrice: "", message: "", vehicleId: "" });
      qc.invalidateQueries({ queryKey: ["freight", id] });
      qc.invalidateQueries({ queryKey: ["driver-bids", user?.id] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const acceptMutation = useMutation({
    // SB: PATCH /api/bids/{bidId}/accept — returns BookingResponse (booking created transactionally)
    mutationFn: (bidId: number) => api.patch(`/bids/${bidId}/accept`, {}),
    onSuccess: (booking: any) => {
      setBookingCreated(booking);
      toast({ title: "Driver accepted!" });
      qc.invalidateQueries({ queryKey: ["freight", id] });
      refreshApplications();
      qc.invalidateQueries({ queryKey: ["driver-bids", user?.id] });
      qc.invalidateQueries({ queryKey: ["my-bookings"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const rejectMutation = useMutation({
    mutationFn: (bidId: number) => api.patch(`/bids/${bidId}/reject`, {}),
    onSuccess: () => {
      setRejectBidId(null);
      toast({ title: "Bid rejected" });
      refreshApplications();
      qc.invalidateQueries({ queryKey: ["driver-bids", user?.id] });
      qc.invalidateQueries({ queryKey: ["freight", id] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const counterMutation = useMutation({
    mutationFn: () => {
      if (!counterBid || !counterAmount || Number(counterAmount) <= 0) {
        throw new Error("Enter a counter amount greater than zero.");
      }
      return api.patch(`/bids/${counterBid.id}/counter`, {
        counterAmount: Number(counterAmount),
        counterNote: counterNote || undefined,
      });
    },
    onSuccess: () => {
      setCounterBid(null);
      setCounterAmount("");
      setCounterNote("");
      toast({ title: "Counter-offer sent" });
      refreshApplications();
      qc.invalidateQueries({ queryKey: ["driver-bids", user?.id] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const acceptCounterMutation = useMutation({
    mutationFn: (bidId: number) => api.patch(`/bids/${bidId}/accept-counter`, {}),
    onSuccess: (booking: any) => {
      setBookingCreated(booking);
      toast({ title: "Counter-offer accepted!" });
      qc.invalidateQueries({ queryKey: ["freight", id] });
      refreshApplications();
      qc.invalidateQueries({ queryKey: ["driver-bids", user?.id] });
      qc.invalidateQueries({ queryKey: ["my-bookings"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-4xl px-4 py-8">
        <Skeleton className="h-8 w-40 mb-6" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (!freight) {
    return (
      <div className="container mx-auto max-w-4xl px-4 py-20 text-center">
        <Package className="h-12 w-12 mx-auto mb-3 opacity-40" />
        <p className="text-lg font-medium">Freight not found</p>
        <Link href="/freight"><Button variant="outline" className="mt-4 rounded-lg">Back to Freight</Button></Link>
      </div>
    );
  }

  const isOwner = user?.id === freight.shipperId;
  const isDriver = user?.role === "driver";
  const canManageBids = user?.role === "shipper" && isOwner;
  const isFixedPrice = String(freight.priceType ?? "").toLowerCase() === "fixed";
  const canApply = isDriver && freight.status === "posted" && isAuthenticated;
  // Phase 5: carrier offers button visible to shipper-owners on POSTED freight
  const showCarrierOffers = isOwner && freight.status === "posted";

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      <Link href="/freight">
        <Button variant="ghost" className="gap-2 mb-6 -ml-2 rounded-lg">
          <ArrowLeft className="h-4 w-4" /> Back to Freight
        </Button>
      </Link>

      <div className="grid md:grid-cols-3 gap-6">
        {/* Main info */}
        <div className="md:col-span-2 space-y-6">
          <Card className="border-border/60 rounded-xl">
            <CardHeader>
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2 flex-wrap mb-2">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full border ${STATUS_COLORS[freight.status] ?? "bg-gray-50 text-gray-700 border-gray-200"}`}>
                      {freight.status}
                    </span>
                    <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full border border-slate-200 capitalize">
                      {freight.cargoType}
                    </span>
                    {freight.distanceKm && (
                      <span className="text-xs bg-slate-50 text-slate-500 px-2 py-0.5 rounded-full border border-slate-200">
                        <Route className="h-3 w-3 inline mr-1" />{freight.distanceKm} km
                      </span>
                    )}
                  </div>
                  <CardTitle className="text-xl">{freight.pickupLocation} → {freight.deliveryLocation}</CardTitle>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-2xl font-bold text-foreground">
                    ETB {Number(freight.budget).toLocaleString()}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {String(freight.priceType ?? "fixed").toLowerCase() === "negotiable"
                      ? "Negotiable"
                      : "Fixed Price"}
                  </p>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {freight.cargoDescription && (
                <p className="text-muted-foreground text-sm">{freight.cargoDescription}</p>
              )}

              <div className="grid grid-cols-2 gap-4">
                <div className="flex items-center gap-2 text-sm p-3 rounded-lg bg-muted/50">
                  <Scale className="h-4 w-4 text-muted-foreground" />
                  <div>
                    <span className="text-muted-foreground text-xs">Weight</span>
                    <p className="font-medium text-sm">{freight.weightTons} tons</p>
                  </div>
                </div>
                {freight.volumeM3 && (
                  <div className="flex items-center gap-2 text-sm p-3 rounded-lg bg-muted/50">
                    <Package className="h-4 w-4 text-muted-foreground" />
                    <div>
                      <span className="text-muted-foreground text-xs">Volume</span>
                      <p className="font-medium text-sm">{freight.volumeM3} m³</p>
                    </div>
                  </div>
                )}
                {freight.deadline && (
                  <div className="flex items-center gap-2 text-sm p-3 rounded-lg bg-muted/50">
                    <Calendar className="h-4 w-4 text-muted-foreground" />
                    <div>
                      <span className="text-muted-foreground text-xs">Deadline</span>
                      <p className="font-medium text-sm">{new Date(freight.deadline).toLocaleDateString()}</p>
                    </div>
                  </div>
                )}
                <div className="flex items-center gap-2 text-sm p-3 rounded-lg bg-muted/50">
                  <DollarSign className="h-4 w-4 text-muted-foreground" />
                  <div>
                    <span className="text-muted-foreground text-xs">Budget</span>
                    <p className="font-medium text-sm">ETB {Number(freight.budget).toLocaleString()}</p>
                  </div>
                </div>
              </div>

              <Separator />

              <div className="flex items-center gap-2 text-sm p-3 rounded-lg bg-emerald-50 border border-emerald-100">
                <MapPin className="h-4 w-4 text-emerald-600" />
                <div>
                  <p className="font-medium text-emerald-800">Pickup</p>
                  <p className="text-emerald-600">{freight.pickupLocation}</p>
                </div>
              </div>
              <div className="flex items-center gap-2 text-sm p-3 rounded-lg bg-blue-50 border border-blue-100">
                <MapPin className="h-4 w-4 text-blue-600" />
                <div>
                  <p className="font-medium text-blue-800">Delivery</p>
                  <p className="text-blue-600">{freight.deliveryLocation}</p>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Driver has already submitted a bid / application for this load */}
          {isDriver && freight.status === "posted" && myExistingBid && (
            <Card className="border-border/60 rounded-xl bg-slate-50/50">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-base flex items-center gap-2">
                    <Gavel className="h-4 w-4 text-primary" /> {isFixedPrice ? "Your Application" : "Your Bid"}
                  </CardTitle>
                  <span className={`text-xs px-2.5 py-0.5 rounded-full font-medium border ${STATUS_COLORS[myExistingBid.status] ?? "bg-gray-50 text-gray-700 border-gray-200"}`}>
                    {myExistingBid.status?.toUpperCase()}
                  </span>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                  <div className="p-3 rounded-lg bg-background border border-border/60">
                    <span className="text-xs text-muted-foreground">{isFixedPrice ? "Fixed Price" : "Proposed Price"}</span>
                    <p className="text-lg font-bold text-foreground">
                      ETB {Number(myExistingBid.amount).toLocaleString()}
                    </p>
                  </div>
                  <div className="p-3 rounded-lg bg-background border border-border/60">
                    <span className="text-xs text-muted-foreground">Vehicle</span>
                    <p className="text-sm font-semibold text-foreground">
                      {myExistingBid.vehiclePlateNumber || "Assigned Vehicle"}
                    </p>
                    {myExistingBid.vehicleType && (
                      <p className="text-xs text-muted-foreground capitalize">
                        {myExistingBid.vehicleType.replace(/_/g, " ")}
                      </p>
                    )}
                  </div>
                </div>

                {myExistingBid.note && (
                  <div className="p-3 rounded-lg bg-background border border-border/60">
                    <span className="text-xs text-muted-foreground">Your Message</span>
                    <p className="text-sm text-foreground mt-0.5 italic">"{myExistingBid.note}"</p>
                  </div>
                )}

                {/* State-specific feedback */}
                {String(myExistingBid.status).toLowerCase() === "pending" && (
                  <div className="flex items-center gap-2 p-3 rounded-lg bg-amber-50 border border-amber-200 text-amber-800 text-sm">
                    <Clock className="h-4 w-4 shrink-0 text-amber-600 animate-pulse" />
                    <span>Waiting for shipper response. You will be notified once the shipper accepts or {isFixedPrice ? "declines" : "counters"} your {isFixedPrice ? "application" : "bid"}.</span>
                  </div>
                )}

                {String(myExistingBid.status).toLowerCase() === "countered" && (
                  <div className="p-4 rounded-xl bg-purple-50/70 border border-purple-200 space-y-3">
                    <div className="flex items-start justify-between">
                      <div>
                        <p className="text-xs font-semibold text-purple-900 uppercase tracking-wide">
                          {String(myExistingBid.counterBy).toUpperCase() === "SHIPPER" ? "Shipper Counter-Offer" : "Your Counter-Offer"}
                        </p>
                        <p className="text-xl font-bold text-purple-950 mt-1">
                          ETB {Number(myExistingBid.counterAmount).toLocaleString()}
                        </p>
                      </div>
                      <span className="text-xs px-2 py-0.5 rounded-full bg-purple-100 text-purple-800 border border-purple-200 font-medium">
                        {String(myExistingBid.counterBy).toUpperCase() === "SHIPPER" ? "Action Required" : "Awaiting Shipper"}
                      </span>
                    </div>

                    {myExistingBid.counterNote && (
                      <p className="text-sm text-purple-900 bg-white/60 p-2.5 rounded-lg border border-purple-100 italic">
                        "{myExistingBid.counterNote}"
                      </p>
                    )}

                    {String(myExistingBid.counterBy).toUpperCase() === "SHIPPER" ? (
                      <div className="flex flex-wrap gap-2 pt-1">
                        <Button
                          size="sm"
                          onClick={() => acceptCounterMutation.mutate(myExistingBid.id)}
                          disabled={acceptCounterMutation.isPending}
                          className="rounded-lg bg-primary hover:bg-primary/90"
                        >
                          {acceptCounterMutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" /> : <CheckCircle2 className="h-3.5 w-3.5 mr-1.5" />}
                          Accept Shipper's ETB {Number(myExistingBid.counterAmount).toLocaleString()}
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setCounterBid(myExistingBid)}
                          disabled={counterMutation.isPending}
                          className="rounded-lg border-purple-300 hover:bg-purple-100/50"
                        >
                          Counter Back
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          onClick={() => setRejectBidId(myExistingBid.id)}
                          disabled={rejectMutation.isPending}
                          className="rounded-lg"
                        >
                          Decline
                        </Button>
                      </div>
                    ) : (
                      <p className="text-xs text-purple-800">
                        Counter-offer sent to shipper. Waiting for shipper to accept or decline.
                      </p>
                    )}
                  </div>
                )}

                {String(myExistingBid.status).toLowerCase() === "accepted" && (
                  <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 space-y-2">
                    <div className="flex items-center gap-2 text-emerald-800 font-semibold">
                      <CheckCircle2 className="h-5 w-5 text-emerald-600" />
                      <span>Congratulations! Your bid was accepted.</span>
                    </div>
                    {myExistingBid.shipperPhone && (
                      <p className="text-sm text-emerald-700">
                        Shipper Contact: <span className="font-bold">{myExistingBid.shipperPhone}</span> {myExistingBid.shipperName ? `(${myExistingBid.shipperName})` : ""}
                      </p>
                    )}
                  </div>
                )}

                {String(myExistingBid.status).toLowerCase() === "rejected" && (
                  <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-sm flex items-center gap-2">
                    <XCircle className="h-4 w-4 shrink-0 text-rose-600" />
                    <span>This bid was declined by the shipper.</span>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Apply section for drivers (only when no existing bid) */}
          {canApply && !myExistingBid && (
            <Card className="border-border/60 rounded-xl">
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Truck className="h-4 w-4" /> {isFixedPrice ? "Apply for Fixed-Price Load" : "Apply for this Load"}
                </CardTitle>
              </CardHeader>
              <CardContent>
                {isFixedPrice && (
                  <div className="p-3 mb-4 rounded-lg bg-slate-50 border border-slate-200 text-slate-700 text-xs">
                    This freight is listed with a fixed price of <span className="font-semibold text-foreground">ETB {Number(freight.budget).toLocaleString()}</span>. Bidding and counter-offers are not available on fixed-price loads.
                  </div>
                )}
                {!showApply ? (
                  <Button onClick={() => setShowApply(true)} className="gap-2 w-full rounded-lg bg-primary hover:bg-primary/90">
                    <Truck className="h-4 w-4" /> {isFixedPrice ? "Apply at Fixed Price" : "Apply Now"}
                  </Button>
                ) : (
                  <div className="space-y-3">
                    <div>
                      <Label>Select Vehicle *</Label>
                      {myVehicles.length === 0 ? (
                        <p className="text-xs text-destructive mt-1">No vehicles registered. <a href="/vehicles" className="underline">Add one first.</a></p>
                      ) : (
                        <select
                          className="mt-1 w-full rounded-lg border border-input px-3 py-2 text-sm bg-background"
                          value={applyForm.vehicleId}
                          onChange={e => setApplyForm(f => ({ ...f, vehicleId: e.target.value }))}
                        >
                          <option value="">-- Select vehicle --</option>
                          {myVehicles.map((v: any) => {
                            const approvedAndAvailable = v.approvalStatus === "APPROVED" && v.availabilityStatus?.toLowerCase() === "available";
                            return <option key={v.id} value={v.id} disabled={!approvedAndAvailable}>
                              {v.plateNumber} · {v.truckType?.replace(/_/g, " ")} · {v.capacity}t · {approvedAndAvailable ? "Eligible" : `${String(v.approvalStatus ?? "UNDER_REVIEW").replace(/_/g, " ")} / ${v.availabilityStatus ?? "unavailable"}`}
                            </option>;
                          })}
                        </select>
                      )}
                    </div>
                    {isFixedPrice ? (
                      <div>
                        <Label>Fixed Price</Label>
                        <Input
                          type="text"
                          disabled
                          value={`ETB ${Number(freight.budget).toLocaleString()} (Non-negotiable)`}
                          className="mt-1 rounded-lg bg-muted text-foreground font-medium cursor-not-allowed"
                        />
                      </div>
                    ) : (
                      <div>
                        <Label>Your Price (ETB) *</Label>
                        <Input
                          type="number"
                          placeholder={`Shipper budget: ETB ${Number(freight.budget).toLocaleString()}`}
                          value={applyForm.proposedPrice}
                          onChange={e => setApplyForm(f => ({ ...f, proposedPrice: e.target.value }))}
                          className="mt-1 rounded-lg"
                        />
                      </div>
                    )}
                    <div>
                      <Label>Message (optional)</Label>
                      <Textarea
                        placeholder={isFixedPrice ? "Message to shipper (e.g., ready to load today)…" : "Tell the shipper about your truck and experience…"}
                        value={applyForm.message}
                        onChange={e => setApplyForm(f => ({ ...f, message: e.target.value }))}
                        className="mt-1 rounded-lg"
                        rows={3}
                      />
                    </div>
                    <div className="flex gap-2">
                      <Button
                        onClick={() => applyMutation.mutate()}
                        disabled={(!isFixedPrice && !applyForm.proposedPrice) || !applyForm.vehicleId || applyMutation.isPending}
                        className="flex-1 rounded-lg"
                      >
                        {applyMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                        {isFixedPrice ? "Apply at Fixed Price" : "Submit Bid"}
                      </Button>
                      <Button variant="outline" onClick={() => setShowApply(false)} className="rounded-lg">Cancel</Button>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Applications (owner/admin) */}
          {(isOwner || user?.role === "admin") && applications && applications.applications?.length > 0 && (
            <Card className="border-border/60 rounded-xl">
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Users className="h-4 w-4" /> Applications ({applications.applications.length} loaded)
                </CardTitle>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={refreshApplications}
                  disabled={applicationsQuery.isFetching}
                >
                  Refresh
                </Button>
              </CardHeader>
              <CardContent className="space-y-3">
                {bookingCreated && <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">Booking #{bookingCreated.id} created with status {bookingCreated.bookingStatus ?? "pending"}. Payment and trip creation have not been assumed.</div>}
                {applications.applications.map((app: any) => (
                  <div key={app.id} className={`p-3 rounded-xl border ${app.status === "accepted" ? "border-emerald-200 bg-emerald-50/40" : app.isRecommended ? "border-emerald-300 bg-emerald-50/20" : "border-border/60"}`}>
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5 flex-wrap mb-0.5">
                          <p className="font-medium text-sm">{app.driverName ?? `Driver #${app.driverId}`}</p>
                          {app.isRecommended && (
                            <span className="inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded-full font-semibold" style={{ background: "#EAF3DE", color: "#27500A" }}>
                              <CheckCircle2 className="h-3 w-3" /> Best Price
                            </span>
                          )}
                          {app.bidderType === "fleet_owner" && (
                            <span className="text-xs px-1.5 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 font-medium">Fleet</span>
                          )}
                        </div>
                        <p className="text-xs text-muted-foreground">
                          ETB {Number(app.proposedPrice).toLocaleString()}
                          {app.truckType && ` · ${app.truckType.replace(/_/g, " ")}`}
                          {app.plateNumber && ` · ${app.plateNumber}`}
                          {app.distanceKm != null && ` · ${app.distanceKm} km away`}
                        </p>
                        {app.createdAt && <p className="text-xs text-muted-foreground mt-1">Submitted {new Date(app.createdAt).toLocaleString()}</p>}
                        {String(app.status).toLowerCase() === "countered" && app.counterAmount != null && <p className="text-xs text-sky-700 mt-1">{String(app.counterBy).toUpperCase() === "DRIVER" ? "Driver counter" : "Shipper counter"}: ETB {Number(app.counterAmount).toLocaleString()}{app.counterNote ? ` · ${app.counterNote}` : ""}</p>}
                        {app.driverRating != null && (
                          <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                            <Star className="h-3 w-3 text-amber-500 fill-amber-500" />
                            {Number(app.driverRating).toFixed(1)}
                            {app.driverTripCount != null && ` · ${app.driverTripCount} trips`}
                          </p>
                        )}
                        {app.message && <p className="text-xs text-muted-foreground mt-1 italic">"{app.message}"</p>}
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <span className={`text-xs px-2 py-0.5 rounded-full border ${STATUS_COLORS[app.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                          {app.status}
                        </span>
                        {canManageBids && String(app.status).toLowerCase() === "pending" && freight.status === "posted" && <div className="flex flex-wrap justify-end gap-2">
                          <Button size="sm" onClick={() => acceptMutation.mutate(app.id)} disabled={acceptMutation.isPending} className="rounded-lg">Accept</Button>
                          {!isFixedPrice && <Button size="sm" variant="outline" onClick={() => setCounterBid(app)} disabled={counterMutation.isPending} className="rounded-lg">Counter</Button>}
                          <Button size="sm" variant="destructive" onClick={() => setRejectBidId(app.id)} disabled={rejectMutation.isPending} className="rounded-lg">Reject</Button>
                        </div>}
                        {canManageBids && String(app.status).toLowerCase() === "countered" && String(app.counterBy).toUpperCase() === "DRIVER" && freight.status === "posted" && <div className="flex flex-wrap justify-end gap-2">
                          <Button size="sm" onClick={() => acceptCounterMutation.mutate(app.id)} disabled={acceptCounterMutation.isPending} className="rounded-lg">Accept Counter</Button>
                          {!isFixedPrice && <Button size="sm" variant="outline" onClick={() => setCounterBid(app)} disabled={counterMutation.isPending} className="rounded-lg">Counter</Button>}
                          <Button size="sm" variant="destructive" onClick={() => setRejectBidId(app.id)} disabled={rejectMutation.isPending} className="rounded-lg">Reject</Button>
                        </div>}
                      </div>
                    </div>
                    {app.status === "accepted" && app.driverPhone && (
                      <div className="mt-2 pt-2 border-t border-emerald-200 flex items-center gap-2">
                        <div className="h-5 w-5 rounded-full bg-emerald-100 flex items-center justify-center shrink-0">
                          <svg className="h-3 w-3 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                          </svg>
                        </div>
                        <span className="text-xs text-emerald-700 font-medium">Price agreed — Driver: </span>
                        <span className="text-sm font-bold text-emerald-700">{app.driverPhone}</span>
                      </div>
                    )}
                  </div>
                ))}
                {applicationsQuery.hasNextPage && (
                  <div className="flex justify-center pt-2">
                    <Button
                      variant="outline"
                      onClick={() => applicationsQuery.fetchNextPage()}
                      disabled={applicationsQuery.isFetchingNextPage}
                    >
                      {applicationsQuery.isFetchingNextPage ? "Loading..." : "Load more bids"}
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-4">
          {/* AI Matches */}
          {(isOwner || user?.role === "admin") && aiMatches && aiMatches.matches?.length > 0 && (
            <Card className="border-border/60 rounded-xl">
              <CardHeader className="pb-3">
                <CardTitle className="text-base flex items-center gap-2">
                  <Zap className="h-4 w-4 text-emerald-500" /> AI Best Matches
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {aiMatches.matches.slice(0, 3).map((m: any, i: number) => {
                  const matchScore = Number.isFinite(Number(m.matchScore)) ? Number(m.matchScore) : 0;
                  return (
                  <div key={m.driverId} className="p-3 rounded-xl border border-border/60">
                    <div className="flex items-center gap-3 mb-2">
                      <div className="h-10 w-10 rounded-full bg-slate-100 flex items-center justify-center text-sm font-bold text-slate-600">
                        {m.driverName?.split(" ").map((n: string) => n[0]).join("")}
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <p className="font-medium text-sm">{m.driverName}</p>
                          <Badge className="bg-blue-500/10 text-blue-600 text-xs">Premium</Badge>
                        </div>
                        <div className="flex items-center gap-1 text-xs text-muted-foreground">
                          <Truck className="h-3 w-3" />
                          {m.vehicleTruckType} • {m.vehicleCapacity}t
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 text-xs">
                        <Star className="h-3 w-3 text-amber-500 fill-amber-500" />
                        <span className="font-medium">{m.avgRating?.toFixed(1)}</span>
                        <span className="text-muted-foreground">({m.totalDeliveries} deliveries)</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span className="text-xs font-bold text-emerald-600">{matchScore.toFixed(0)}% Match</span>
                      </div>
                    </div>
                    <div className="w-full bg-muted rounded-full h-1.5 mt-2">
                      <div className="bg-emerald-500 rounded-full h-1.5 transition-all" style={{ width: `${matchScore}%` }} />
                    </div>
                    <div className="flex gap-2 mt-2">
                      <Link href={`/drivers/${m.driverId}`} className="flex-1"><Button variant="outline" size="sm" className="w-full rounded-lg text-xs">View Profile</Button></Link>
                      <Button size="sm" className="flex-1 rounded-lg text-xs bg-primary hover:bg-primary/90">Select Driver</Button>
                    </div>
                  </div>
                  );
                })}
              </CardContent>
            </Card>
          )}

          {/* Price Prediction */}
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="pb-3">
              <CardTitle className="text-base flex items-center gap-2">
                <Banknote className="h-4 w-4 text-emerald-600" /> AI Price Prediction
              </CardTitle>
            </CardHeader>
            <CardContent>
              <PricePrediction aiPrice={aiPrice} />
            </CardContent>
          </Card>

          {/* Quick Actions */}
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm flex items-center gap-2">
                <Shield className="h-4 w-4 text-primary" /> Actions
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {isOwner && freight.status === "matched" && (
                <Link href={`/payment/${freight.id}`}>
                  <Button variant="outline" className="w-full gap-2 justify-start rounded-lg" size="sm">
                    <Banknote className="h-4 w-4" /> Pay & Escrow
                  </Button>
                </Link>
              )}
              {freight.status !== "posted" && freight.status !== "draft" && (
                <Link href={`/contract/${freight.id}`}>
                  <Button variant="outline" className="w-full gap-2 justify-start rounded-lg" size="sm">
                    <FileText className="h-4 w-4" /> View Contract
                  </Button>
                </Link>
              )}
              {/* Phase 5 — Carrier Offers link (shipper, freight POSTED) */}
              {showCarrierOffers && (
                <Link href={`/carrier-offers/freight/${freight.id}`}>
                  <Button variant="outline" className="w-full gap-2 justify-start rounded-lg" size="sm">
                    <Users className="h-4 w-4" /> Carrier Offers
                  </Button>
                </Link>
              )}
              {(freight.status === "in_transit" || freight.status === "delivered" || freight.status === "completed") && (
                <Link href={`/tracking/${freight.id}`}>
                  <Button variant="outline" className="w-full gap-2 justify-start rounded-lg" size="sm">
                    <Navigation className="h-4 w-4" /> Live Tracking
                  </Button>
                </Link>
              )}
              {freight.status !== "posted" && freight.status !== "draft" && freight.status !== "completed" && freight.status !== "cancelled" && (
                <Link href={`/dispute/${freight.id}`}>
                  <Button variant="outline" className="w-full gap-2 justify-start rounded-lg" size="sm">
                    <AlertTriangle className="h-4 w-4" /> File Dispute
                  </Button>
                </Link>
              )}
              {freight.status !== "posted" && freight.status !== "draft" && (
                <Link href={`/messages/${freight.id}`}>
                  <Button variant="outline" className="w-full gap-2 justify-start rounded-lg" size="sm">
                    <MessageSquare className="h-4 w-4" /> Messages
                  </Button>
                </Link>
              )}
              <div className="flex items-center gap-2 text-xs text-muted-foreground pt-1">
                <Clock className="h-3 w-3" />
                Posted {new Date(freight.createdAt).toLocaleDateString()}
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Package className="h-3 w-3" />
                Freight ID #{freight.id}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog open={Boolean(counterBid)} onOpenChange={(open) => { if (!open && !counterMutation.isPending) { setCounterBid(null); setCounterAmount(""); setCounterNote(""); } }}>
        <DialogContent className="rounded-xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Make a counter-offer</DialogTitle>
            <DialogDescription>Enter the amount the Spring bid contract will send to the driver.</DialogDescription>
          </DialogHeader>
          <form onSubmit={(event) => { event.preventDefault(); counterMutation.mutate(); }} className="space-y-4">
            <div className="space-y-1.5"><Label htmlFor="counter-amount">Counter amount (ETB)</Label><Input id="counter-amount" type="number" min="0.01" step="0.01" required value={counterAmount} onChange={event => setCounterAmount(event.target.value)} placeholder="e.g. 80000" /></div>
            <div className="space-y-1.5"><Label htmlFor="counter-note">Note (optional)</Label><Textarea id="counter-note" maxLength={500} value={counterNote} onChange={event => setCounterNote(event.target.value)} placeholder="Add context for the driver" rows={3} /></div>
            <DialogFooter><Button type="button" variant="outline" onClick={() => setCounterBid(null)} disabled={counterMutation.isPending}>Cancel</Button><Button type="submit" disabled={!counterAmount || Number(counterAmount) <= 0 || counterMutation.isPending}>{counterMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : "Send Counter"}</Button></DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={rejectBidId !== null} onOpenChange={(open) => { if (!open && !rejectMutation.isPending) setRejectBidId(null); }}>
        <AlertDialogContent className="rounded-xl">
          <AlertDialogHeader><AlertDialogTitle>Reject this bid?</AlertDialogTitle><AlertDialogDescription>This action marks the bid as rejected. The driver will no longer be eligible for acceptance on this bid.</AlertDialogDescription></AlertDialogHeader>
          <AlertDialogFooter><AlertDialogCancel disabled={rejectMutation.isPending}>Cancel</AlertDialogCancel><AlertDialogAction className="bg-destructive text-destructive-foreground hover:bg-destructive/90" disabled={rejectMutation.isPending} onClick={() => { if (rejectBidId !== null) rejectMutation.mutate(rejectBidId); }}>Reject bid</AlertDialogAction></AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function PricePrediction({ aiPrice }: { aiPrice: any }) {
  const isLoading = !aiPrice && aiPrice !== null;
  const data = aiPrice?.prediction;

  if (isLoading) return <Skeleton className="h-16 w-full rounded-lg" />;
  if (!data) return <p className="text-xs text-muted-foreground">No prediction available</p>;

  return (
    <div className="space-y-2">
      <div className="text-center py-2">
        <p className="text-2xl font-bold text-emerald-700">
          ETB {Number(data.recommendedPrice ?? 0).toLocaleString()}
        </p>
        <p className="text-xs text-muted-foreground">Recommended market price</p>
      </div>
      <div className="space-y-1 text-xs">
        <div className="flex justify-between">
          <span className="text-muted-foreground">Min price</span>
          <span className="font-medium">ETB {Number(data.minPrice).toLocaleString()}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted-foreground">Max price</span>
          <span className="font-medium">ETB {Number(data.maxPrice).toLocaleString()}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted-foreground">Confidence</span>
          <span className="font-medium">{Math.round((data.confidence || 0) * 100)}%</span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted-foreground">Model</span>
          <span className="font-medium capitalize">{data.model}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted-foreground">Per km</span>
          <span className="font-medium">ETB {data.pricePerKm}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-muted-foreground">Per ton</span>
          <span className="font-medium">ETB {data.pricePerTon}</span>
        </div>
      </div>
      {data.breakdown && (
        <div className="space-y-1 pt-1 border-t">
          <p className="text-xs font-medium text-muted-foreground">Price breakdown</p>
          {Object.entries(data.breakdown).map(([k, v]) => (
            <div key={k} className="flex justify-between text-xs">
              <span className="text-muted-foreground capitalize">{k.replace(/_/g, " ")}</span>
              <span className="font-medium">ETB {Number(v).toLocaleString()}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
