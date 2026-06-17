import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
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
  ChevronRight, CheckCircle2, Route, DollarSign
} from "lucide-react";
import { Link } from "wouter";

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

  const { data: freight, isLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
    refetchInterval: 30_000,
  });

  // Driver's own vehicles (for bid vehicle selection)
  const { data: myVehiclesData } = useQuery({
    queryKey: ["my-vehicles"],
    queryFn: () => api.get<{ vehicles: any[] }>("/my-vehicles"),
    enabled: user?.role === "driver",
  });
  const myVehicles = myVehiclesData?.vehicles ?? [];

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

  const { data: applications } = useQuery({
    queryKey: ["applications", id],
    queryFn: async () => {
      const resp = await api.get<{ data: any[] }>(`/cargo-requests/${id}/bids`);
      const bids = Array.isArray(resp?.data) ? resp.data : [];
      return {
        applications: bids.map((b: any) => ({
          id: b.id,
          driverId: b.driver_id,
          driverName: b.driver_name,
          driverPhone: b.driver_phone,
          proposedPrice: b.amount,
          message: b.note,
          status: b.status,
          truckType: b.truck_type,
          plateNumber: b.plate_number,
          driverRating: b.driver_rating,
          driverTripCount: b.driver_trip_count,
          distanceKm: b.distance_km,
          bidderType: b.bidder_type,
          isRecommended: b.is_recommended,
        })),
      };
    },
    enabled: !!freight && (user?.role === "admin" || user?.role === "shipper"),
    refetchInterval: 20_000,
  });

  const applyMutation = useMutation({
    mutationFn: () => api.post(`/cargo-requests/${id}/bids`, {
      vehicle_id: Number(applyForm.vehicleId),
      amount: Number(applyForm.proposedPrice),
      note: applyForm.message,
    }),
    onSuccess: () => {
      toast({ title: "Bid submitted!", description: "The shipper will review your bid." });
      setShowApply(false);
      setApplyForm({ proposedPrice: "", message: "", vehicleId: "" });
      qc.invalidateQueries({ queryKey: ["freight", id] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const acceptMutation = useMutation({
    mutationFn: (bidId: number) => api.patch(`/bids/${bidId}/accept`, {}),
    onSuccess: () => {
      toast({ title: "Driver accepted!" });
      qc.invalidateQueries({ queryKey: ["freight", id] });
      qc.invalidateQueries({ queryKey: ["applications", id] });
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
  const canApply = isDriver && freight.status === "posted" && isAuthenticated;

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
                  <p className="text-xs text-muted-foreground">Fixed Price</p>
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

          {/* Apply section for drivers */}
          {canApply && (
            <Card className="border-border/60 rounded-xl">
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <Truck className="h-4 w-4" /> Apply for this Load
                </CardTitle>
              </CardHeader>
              <CardContent>
                {!showApply ? (
                  <Button onClick={() => setShowApply(true)} className="gap-2 w-full rounded-lg bg-primary hover:bg-primary/90">
                    <Truck className="h-4 w-4" /> Apply Now
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
                          {myVehicles.map((v: any) => (
                            <option key={v.id} value={v.id}>
                              {v.plateNumber} · {v.truckType?.replace(/_/g, " ")} · {v.capacityTons}t
                            </option>
                          ))}
                        </select>
                      )}
                    </div>
                    <div>
                      <Label>Your Price (ETB) *</Label>
                      <Input
                        type="number"
                        placeholder={`Budget: ETB ${Number(freight.budget).toLocaleString()}`}
                        value={applyForm.proposedPrice}
                        onChange={e => setApplyForm(f => ({ ...f, proposedPrice: e.target.value }))}
                        className="mt-1 rounded-lg"
                      />
                    </div>
                    <div>
                      <Label>Message (optional)</Label>
                      <Textarea
                        placeholder="Tell the shipper about your truck and experience…"
                        value={applyForm.message}
                        onChange={e => setApplyForm(f => ({ ...f, message: e.target.value }))}
                        className="mt-1 rounded-lg"
                        rows={3}
                      />
                    </div>
                    <div className="flex gap-2">
                      <Button
                        onClick={() => applyMutation.mutate()}
                        disabled={!applyForm.proposedPrice || !applyForm.vehicleId || applyMutation.isPending}
                        className="flex-1 rounded-lg"
                      >
                        {applyMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                        Submit Bid
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
                  <Users className="h-4 w-4" /> Applications ({applications.applications.length})
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
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
                        {app.status === "pending" && freight.status === "posted" && (
                          <Button size="sm" onClick={() => acceptMutation.mutate(app.id)}
                            disabled={acceptMutation.isPending} className="rounded-lg">
                            Accept
                          </Button>
                        )}
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
                {aiMatches.matches.slice(0, 3).map((m: any, i: number) => (
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
                        <span className="text-xs font-bold text-emerald-600">{m.matchScore.toFixed(0)}% Match</span>
                      </div>
                    </div>
                    <div className="w-full bg-muted rounded-full h-1.5 mt-2">
                      <div className="bg-emerald-500 rounded-full h-1.5 transition-all" style={{ width: `${m.matchScore}%` }} />
                    </div>
                    <div className="flex gap-2 mt-2">
                      <Button variant="outline" size="sm" className="flex-1 rounded-lg text-xs">View Profile</Button>
                      <Button size="sm" className="flex-1 rounded-lg text-xs bg-primary hover:bg-primary/90">Select Driver</Button>
                    </div>
                  </div>
                ))}
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
