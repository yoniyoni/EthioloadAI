import { useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useParams } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Link } from "wouter";
import { ArrowLeft, MapPin, Navigation, Clock, Loader2, Truck, CheckCircle, Route, AlertTriangle, Package, ChevronRight } from "lucide-react";

export default function Tracking() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const freightId = Number(id);

  const [driverLat, setDriverLat] = useState("");
  const [driverLng, setDriverLng] = useState("");

  const { data: freight, isLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
  });

  const { data: latestLocation } = useQuery({
    queryKey: ["tracking", freightId, "latest"],
    queryFn: () => api.get<any>(`/tracking/${freightId}/latest`),
    enabled: !isNaN(freightId),
    refetchInterval: 30000,
  });

  const { data: routeHistory } = useQuery({
    queryKey: ["tracking", freightId],
    queryFn: () => api.get<any[]>(`/tracking/${freightId}`),
    enabled: !isNaN(freightId),
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      api.post("/tracking", {
        freightId,
        latitude: Number(driverLat),
        longitude: Number(driverLng),
      }),
    onSuccess: () => {
      toast({ title: "Location updated!" });
      setDriverLat("");
      setDriverLng("");
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const deliverMutation = useMutation({
    mutationFn: () => api.post(`/freight/${freightId}/deliver`, {}),
    onSuccess: () => {
      toast({ title: "Delivery marked!", description: "Waiting for shipper confirmation." });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const confirmMutation = useMutation({
    mutationFn: () => api.post(`/freight/${freightId}/confirm-delivery`, {}),
    onSuccess: () => {
      toast({ title: "Delivery confirmed!", description: "Payment released to driver." });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <Skeleton className="h-8 w-48 mb-6" />
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    );
  }

  if (!freight) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-20 text-center">
        <p className="text-lg font-medium">Freight not found</p>
        <Link href="/freight"><Button variant="outline" className="mt-4 rounded-lg">Back</Button></Link>
      </div>
    );
  }

  const isDriver = user?.id === freight.matchedDriverId;
  const isOwner = user?.id === freight.shipperId;
  const isAdmin = user?.role === "admin";
  const inTransit = freight.status === "in_transit";
  const delivered = freight.status === "delivered";

  const STATUS_COLORS: Record<string, string> = {
    posted: "bg-blue-50 text-blue-700 border-blue-200",
    matched: "bg-sky-50 text-sky-700 border-sky-200",
    accepted: "bg-indigo-50 text-indigo-700 border-indigo-200",
    in_transit: "bg-purple-50 text-purple-700 border-purple-200",
    delivered: "bg-cyan-50 text-cyan-700 border-cyan-200",
    completed: "bg-emerald-50 text-emerald-700 border-emerald-200",
    cancelled: "bg-red-50 text-red-700 border-red-200",
  };

  const checkpoints = [
    { label: "Picked Up", status: freight.status !== "posted" && freight.status !== "draft" },
    { label: "In Transit", status: ["in_transit", "delivered", "completed"].includes(freight.status) },
    { label: "Delivered", status: freight.status === "delivered" || freight.status === "completed" },
    { label: "Payment Released", status: freight.status === "completed" },
  ];

  return (
    <div className="container mx-auto max-w-3xl px-4 py-8">
      <Link href="/freight">
        <Button variant="ghost" className="gap-2 mb-6 -ml-2 rounded-lg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Button>
      </Link>

      <Card className="border-border/60 rounded-xl overflow-hidden">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
                <Navigation className="h-4 w-4 text-primary" />
              </div>
              <div>
                <CardTitle className="text-xl">Live Tracking</CardTitle>
                <p className="text-sm text-muted-foreground">{freight.cargoType} · {freight.pickupLocation} → {freight.deliveryLocation}</p>
              </div>
            </div>
            <Badge className={`border ${STATUS_COLORS[freight.status] ?? "bg-gray-50 text-gray-700 border-gray-200"}`}>
              {freight.status}
            </Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Shipment Progress */}
          <div className="bg-[#0c1e4a] text-white rounded-xl p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Package className="h-4 w-4 text-emerald-400" />
                <span className="text-sm font-semibold">Shipment Progress</span>
              </div>
              <span className="text-sm text-emerald-400">ETA: {new Date(freight.deadline).toLocaleDateString()}</span>
            </div>
            <div className="flex items-center gap-0">
              {checkpoints.map((cp, i) => (
                <div key={i} className="flex items-center flex-1">
                  <div className="flex flex-col items-center">
                    <div className={`h-8 w-8 rounded-full flex items-center justify-center ${cp.status ? "bg-emerald-500" : "bg-white/10"}`}>
                      <CheckCircle className="h-4 w-4 text-white" />
                    </div>
                    <span className="text-xs mt-1 text-slate-300">{cp.label}</span>
                  </div>
                  {i < checkpoints.length - 1 && (
                    <div className={`flex-1 h-0.5 mx-2 ${checkpoints[i+1].status ? "bg-emerald-500" : "bg-white/10"}`} />
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Current Location */}
          <div className="p-5 rounded-xl border border-border/60 bg-gradient-to-r from-emerald-50 to-white">
            <div className="flex items-center gap-2 mb-3">
              <MapPin className="h-4 w-4 text-emerald-600" />
              <span className="text-sm font-semibold text-emerald-800">Current Location</span>
            </div>
            {latestLocation ? (
              <div className="space-y-1">
                <p className="text-sm font-mono">
                  {latestLocation.latitude.toFixed(5)}°, {latestLocation.longitude.toFixed(5)}°
                </p>
                <p className="text-xs text-muted-foreground">
                  Updated {new Date(latestLocation.timestamp).toLocaleString()}
                </p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No location data yet. Driver hasn't started tracking.</p>
            )}
          </div>

          {/* Route History */}
          {routeHistory && routeHistory.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-sm font-medium flex items-center gap-2">
                <Route className="h-4 w-4" /> Route History ({routeHistory.length} points)
              </h3>
              <div className="max-h-60 overflow-y-auto space-y-2 rounded-xl border border-border/60 p-3">
                {routeHistory.map((loc: any, i: number) => (
                  <div key={i} className="flex items-center justify-between p-2 rounded-lg bg-muted/30 text-sm">
                    <span className="font-mono text-xs">
                      {loc.latitude.toFixed(5)}°, {loc.longitude.toFixed(5)}°
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {new Date(loc.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Driver Actions */}
          {isDriver && inTransit && (
            <div className="space-y-3 p-4 rounded-xl border border-border/60">
              <h3 className="text-sm font-medium">Update Location</h3>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs text-muted-foreground">Latitude</label>
                  <input
                    type="number"
                    step="any"
                    value={driverLat}
                    onChange={e => setDriverLat(e.target.value)}
                    className="w-full rounded-lg border border-input px-3 py-2 text-sm"
                    placeholder="9.1450"
                  />
                </div>
                <div>
                  <label className="text-xs text-muted-foreground">Longitude</label>
                  <input
                    type="number"
                    step="any"
                    value={driverLng}
                    onChange={e => setDriverLng(e.target.value)}
                    className="w-full rounded-lg border border-input px-3 py-2 text-sm"
                    placeholder="40.4897"
                  />
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  onClick={() => updateMutation.mutate()}
                  disabled={!driverLat || !driverLng || updateMutation.isPending}
                  className="flex-1 rounded-lg"
                  size="sm"
                >
                  {updateMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <MapPin className="h-4 w-4 mr-2" />}
                  Update Location
                </Button>
                <Button
                  onClick={() => deliverMutation.mutate()}
                  disabled={deliverMutation.isPending}
                  variant="outline"
                  size="sm"
                  className="gap-2 rounded-lg"
                >
                  {deliverMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Truck className="h-4 w-4" />}
                  Mark Delivered
                </Button>
              </div>
            </div>
          )}

          {/* Shipper Confirm */}
          {(isOwner || isAdmin) && delivered && (
            <div className="space-y-3">
              <div className="p-4 rounded-xl bg-cyan-50 border border-cyan-200 text-center space-y-1">
                <Truck className="h-6 w-6 text-cyan-600 mx-auto" />
                <p className="font-medium text-cyan-800">Driver marked delivery</p>
                <p className="text-xs text-cyan-700">Confirm to release payment from escrow.</p>
              </div>
              <Button
                onClick={() => confirmMutation.mutate()}
                disabled={confirmMutation.isPending}
                className="w-full h-12 rounded-lg bg-emerald-600 hover:bg-emerald-700 gap-2"
              >
                {confirmMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle className="h-4 w-4" />}
                Confirm Delivery & Release Payment
              </Button>
            </div>
          )}

          {freight.status === "completed" && (
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-center space-y-1">
              <CheckCircle className="h-6 w-6 text-emerald-600 mx-auto" />
              <p className="font-medium text-emerald-800">Delivery Completed</p>
              <p className="text-xs text-emerald-700">Payment has been released. Thank you!</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
