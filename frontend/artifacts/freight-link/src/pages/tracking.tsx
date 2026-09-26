import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";import { useParams } from "wouter";
import { api } from "@/lib/api";
import { SpringWebSocketClient } from "@/lib/realtime";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Link } from "wouter";
import { ArrowLeft, MapPin, Navigation, Clock, Loader2, Truck, CheckCircle, Route, AlertTriangle, Package, ChevronRight } from "lucide-react";

const MAPTILER_KEY = import.meta.env.VITE_MAPTILER_KEY as string | undefined;
const TILE_SIZE = 256;
const tileCache = new Map<string, HTMLImageElement | null>();

function worldPixel(latitude: number, longitude: number, zoom: number) {
  const scale = TILE_SIZE * 2 ** zoom;
  const clampedLatitude = Math.max(-85.0511, Math.min(85.0511, latitude));
  const sine = Math.sin((clampedLatitude * Math.PI) / 180);
  return { x: ((longitude + 180) / 360) * scale, y: (0.5 - Math.log((1 + sine) / (1 - sine)) / (4 * Math.PI)) * scale };
}

type MapMarker = { latitude: number; longitude: number; color: string; label: string };

function RouteMap({ points, markers = [] }: { points: any[]; markers?: MapMarker[] }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [redraw, setRedraw] = useState(0);
  const coordinates = useMemo(() => points.map((point) => ({ latitude: Number(point.latitude), longitude: Number(point.longitude) })).filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude)), [points]);
  const validMarkers = useMemo(() => markers.filter((marker) => Number.isFinite(marker.latitude) && Number.isFinite(marker.longitude)), [markers]);
  const mapCoordinates = coordinates.length > 0 ? coordinates : validMarkers;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || mapCoordinates.length === 0 || !MAPTILER_KEY) return;
    const context = canvas.getContext("2d");
    if (!context) return;
    const minLat = Math.min(...mapCoordinates.map((point) => point.latitude));
    const maxLat = Math.max(...mapCoordinates.map((point) => point.latitude));
    const minLng = Math.min(...mapCoordinates.map((point) => point.longitude));
    const maxLng = Math.max(...mapCoordinates.map((point) => point.longitude));
    const center = { lat: (minLat + maxLat) / 2, lon: (minLng + maxLng) / 2 };
    const span = Math.max(maxLat - minLat, maxLng - minLng, 0.02);
    const tileZoom = Math.max(3, Math.min(14, Math.floor(Math.log2(140 / span))));
    const centerPixel = worldPixel(center.lat, center.lon, tileZoom);
    const width = canvas.clientWidth * window.devicePixelRatio;
    const height = canvas.clientHeight * window.devicePixelRatio;
    canvas.width = width;
    canvas.height = height;
    const left = centerPixel.x - width / 2;
    const top = centerPixel.y - height / 2;
    context.fillStyle = "#e8eef2";
    context.fillRect(0, 0, width, height);
    const worldTiles = 2 ** tileZoom;
    const minX = Math.floor(left / TILE_SIZE) - 1;
    const maxX = Math.floor((left + width) / TILE_SIZE) + 1;
    const minY = Math.max(0, Math.floor(top / TILE_SIZE) - 1);
    const maxY = Math.min(worldTiles - 1, Math.floor((top + height) / TILE_SIZE) + 1);
    for (let tileX = minX; tileX <= maxX; tileX += 1) for (let tileY = minY; tileY <= maxY; tileY += 1) {
      const wrappedX = ((tileX % worldTiles) + worldTiles) % worldTiles;
      const key = `${tileZoom}/${wrappedX}/${tileY}`;
      let image = tileCache.get(key);
      if (!image && !tileCache.has(key)) {
        const requested = new Image();
        requested.crossOrigin = "anonymous";
        tileCache.set(key, null);
        requested.onload = () => { tileCache.set(key, requested); setRedraw((value) => value + 1); };
        requested.src = `https://api.maptiler.com/tiles/streets-v2/${tileZoom}/${wrappedX}/${tileY}.png?key=${encodeURIComponent(MAPTILER_KEY)}`;
        image = null;
      }
      if (image) context.drawImage(image, tileX * TILE_SIZE - left, tileY * TILE_SIZE - top, TILE_SIZE, TILE_SIZE);
    }
    const project = (point: { latitude: number; longitude: number }) => { const pixel = worldPixel(point.latitude, point.longitude, tileZoom); return { x: pixel.x - left, y: pixel.y - top }; };
    const projected = coordinates.map(project);
    if (projected.length > 1) {
      context.strokeStyle = "#0f766e";
      context.lineWidth = 5 * window.devicePixelRatio;
      context.lineCap = "round";
      context.lineJoin = "round";
      context.beginPath();
      projected.forEach((point, index) => index === 0 ? context.moveTo(point.x, point.y) : context.lineTo(point.x, point.y));
      context.stroke();
    }
    validMarkers.forEach((marker) => {
      const projectedMarker = project(marker);
      context.fillStyle = marker.color;
      context.beginPath();
      context.arc(projectedMarker.x, projectedMarker.y, 8 * window.devicePixelRatio, 0, Math.PI * 2);
      context.fill();
      context.strokeStyle = "white";
      context.lineWidth = 3 * window.devicePixelRatio;
      context.stroke();
    });
  }, [coordinates, mapCoordinates, redraw, validMarkers]);

  if (mapCoordinates.length === 0) return <div className="flex h-72 items-center justify-center rounded-xl border border-border/60 bg-muted/30 text-sm text-muted-foreground">No authoritative trip coordinates are available yet.</div>;
  if (!MAPTILER_KEY) return <div className="flex h-72 items-center justify-center rounded-xl border border-border/60 bg-muted/30 px-6 text-center text-sm text-muted-foreground">MapTiler is not configured. Recorded GPS coordinates are available below.</div>;
  return <canvas ref={canvasRef} className="h-72 w-full rounded-xl border border-border/60 bg-muted/30" role="img" aria-label="MapTiler map showing the recorded trip route" />;
}

export default function Tracking() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const freightId = Number(id);
  const hasFreightId = Boolean(id && Number.isFinite(freightId));

  const [driverLat, setDriverLat] = useState("");
  const [driverLng, setDriverLng] = useState("");

  // ── Freight detail (already Spring Boot via F3) ───────────────────────────
  const { data: freightRaw, isLoading, isError: freightError } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
    enabled: hasFreightId,
  });

  const { data: shipperFreights = [], isLoading: shipperFreightsLoading, isError: shipperFreightsError } = useQuery({
    queryKey: ["tracking-freights"],
    queryFn: () => api.get<any[]>("/freight/my-freights"),
    enabled: !hasFreightId,
  });

  const { data: driverTripsPage, isLoading: driverTripsLoading, isError: driverTripsError } = useQuery({
    queryKey: ["tracking-driver-trips"],
    queryFn: () => api.get<{ content: any[] }>("/trips?size=100"),
    enabled: !hasFreightId && user?.role === "driver",
  });

  // Normalise SB FreightResponse field names (same pattern as freight-detail.tsx)
  const freight = freightRaw ? {
    ...freightRaw,
    deliveryLocation: freightRaw.deliveryLocation ?? freightRaw.destination,
    cargoType:        freightRaw.cargoType        ?? freightRaw.materialType,
  } : freightRaw;

  const driverShipments = (driverTripsPage?.content ?? [])
    .map((trip: any) => ({
      id: trip.cargo?.id ?? trip.booking?.freightId,
      cargoType: trip.cargo?.cargoType ?? trip.cargo?.materialType ?? `Trip #${trip.id}`,
      pickupLocation: trip.cargo?.pickupLocation ?? trip.cargo?.origin,
      deliveryLocation: trip.cargo?.deliveryLocation ?? trip.cargo?.destination,
    }))
    .filter((shipment: any) => Number.isFinite(Number(shipment.id)));

  // ── Trip resolution: find the trip for this freight ───────────────────────
  // SB does not provide GET /api/trips/by-freight/{id}.
  // We query the caller's trips (paginated) and find the one matching this freight.
  const { data: tripsPage, isLoading: tripsLoading, isError: tripsError } = useQuery({
    queryKey: ["trips-for-freight", id],
    queryFn: () => api.get<{ content: any[] }>(`/trips?size=20`),
    enabled: hasFreightId,
  });

  // Find the trip whose booking references this freight
  const activeTrip: any = (tripsPage?.content ?? []).find(
    (t: any) => t.cargo?.id === freightId || t.booking?.freightId === freightId
  );
  const tripId: number | null = activeTrip?.id ?? null;

  // ── Trip detail poll and live GPS data from Spring Boot ───────────────────
  const { data: tripDetail, isError: tripError } = useQuery({
    queryKey: ["trip-detail", tripId],
    queryFn: () => api.get<any>(`/trips/${tripId}`),
    enabled: tripId !== null,
    refetchInterval: 30000,   // same 30 s polling cadence as the old tracking endpoint
  });

  const { data: latestLocation, isLoading: locationLoading, isError: locationError } = useQuery({
    queryKey: ["trip-location", tripId],
    queryFn: () => api.get<any>(`/trips/${tripId}/location`),
    enabled: tripId !== null,
    refetchInterval: 15000,
  });

  const queryClient = useQueryClient();
  useEffect(() => {
    if (tripId === null) return;
    const client = new SpringWebSocketClient("/ws/location", `trip.${tripId}.location`);
    const unsubscribe = client.onMessage((message) => {
      if (message.type !== "LOCATION_UPDATE" || !message.data) return;
      if (String(message.data.tripId) !== String(tripId)) return;
      queryClient.setQueryData(["trip-location", tripId], (previous: any) => ({
        ...(previous ?? {}),
        latitude: message.data?.latitude,
        longitude: message.data?.longitude,
        timestamp: message.data?.timestamp,
      }));
    });
    client.connect();
    return () => { unsubscribe(); client.dispose(); };
  }, [tripId, queryClient]);

  const { data: routeHistory = [], isLoading: routeLoading, isError: routeError } = useQuery<any[]>({
    queryKey: ["trip-route", tripId],
    queryFn: () => api.get<any[]>(`/trips/${tripId}/route?limit=500`),
    enabled: tripId !== null,
    refetchInterval: 30000,
  });

  const { data: routeAnalytics, isError: analyticsError } = useQuery<any>({
    queryKey: ["trip-route-analytics", tripId],
    queryFn: () => api.get<any>(`/trips/${tripId}/route/analytics`),
    enabled: tripId !== null,
    refetchInterval: 30000,
  });

  // ── GPS update mutation (driver): was POST /api/tracking → SB POST /api/trips/{tripId}/gps ─
  // SB AddGpsLocationRequest: { latitude, longitude, speed?, accuracy?, timestamp? }
  const updateMutation = useMutation({
    mutationFn: () => {
      if (!tripId) throw new Error("No active trip found for this freight.");
      return api.post(`/trips/${tripId}/gps`, {
        latitude:  Number(driverLat),
        longitude: Number(driverLng),
        // timestamp defaults to server time if omitted; acceptable for manual entry
      });
    },
    onSuccess: () => {
      toast({ title: "Location updated!" });
      setDriverLat("");
      setDriverLng("");
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  // ── Mark delivered (driver): was POST /api/freight/{id}/deliver ──────────
  // SB: POST /api/trips/{tripId}/complete → transitions trip to COMPLETED.
  // This replaces the Laravel deliver workflow. Booking/freight status cascade server-side.
  const deliverMutation = useMutation({
    mutationFn: () => {
      if (!tripId) throw new Error("No active trip found for this freight.");
      return api.post(`/trips/${tripId}/complete`, {});
    },
    onSuccess: () => {
      toast({ title: "Trip completed!", description: "Waiting for payment confirmation." });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  // ── Confirm delivery (shipper): navigates to payment page — see payment.tsx
  // Spring Boot M7: POST /api/payments/{paymentId}/complete is handled there.

  if (!hasFreightId) {
    const trackingShipments = user?.role === "driver" ? driverShipments : shipperFreights;
    const trackingLoading = user?.role === "driver" ? driverTripsLoading : shipperFreightsLoading;
    const trackingError = user?.role === "driver" ? driverTripsError : shipperFreightsError;
    return (
      <div className="container mx-auto max-w-5xl px-4 py-8">
        <div className="mb-6"><h1 className="text-3xl font-bold tracking-tight">{user?.role === "driver" ? "Trip Tracking" : "Track Shipments"}</h1><p className="mt-1 text-muted-foreground">Select a shipment with an authorized trip to view recorded location data.</p></div>
        {trackingLoading && <div className="space-y-3"><Skeleton className="h-24 w-full rounded-xl" /><Skeleton className="h-24 w-full rounded-xl" /></div>}
        {trackingError && <p role="alert" className="mb-4 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">Unable to load authorized trips for tracking. Refresh to try again.</p>}
        {!trackingLoading && trackingShipments.length === 0 && <Card className="rounded-xl border-border/60"><CardContent className="py-16 text-center"><Navigation className="mx-auto mb-3 h-10 w-10 text-muted-foreground/40" /><p className="font-medium">No trips to track</p><p className="mt-1 text-sm text-muted-foreground">Recorded tracking will appear after Spring Boot returns an authorized trip.</p></CardContent></Card>}
        <div className="grid gap-3">{trackingShipments.map((shipment: any) => <Link key={shipment.id} href={`/tracking/${shipment.id}`}><Card className="rounded-xl border-border/60 transition-colors hover:border-primary/50"><CardContent className="flex items-center justify-between gap-4 p-4"><div><p className="font-medium">{shipment.cargoType ?? shipment.materialType ?? shipment.description ?? `Shipment #${shipment.id}`}</p><p className="text-sm text-muted-foreground">{shipment.pickupLocation ?? shipment.origin ?? "Origin unavailable"} <span aria-hidden="true">→</span> {shipment.deliveryLocation ?? shipment.destination ?? "Destination unavailable"}</p></div><ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" /></CardContent></Card></Link>)}</div>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <Skeleton className="h-8 w-48 mb-6" />
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    );
  }

  if (freightError) {
    return <div className="container mx-auto max-w-3xl px-4 py-20 text-center"><p className="text-lg font-medium">Unable to load this shipment</p><p className="mt-2 text-sm text-muted-foreground">Spring Boot did not return the freight details.</p><Link href="/freight"><Button variant="outline" className="mt-4 rounded-lg">Back to Freight</Button></Link></div>;
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
    { label: "Completed", status: freight.status === "completed" },
  ];

  const coordinate = (latitude: any, longitude: any) => {
    const lat = Number(latitude);
    const lng = Number(longitude);
    return Number.isFinite(lat) && Number.isFinite(lng) ? { latitude: lat, longitude: lng } : null;
  };
  const mapMarkers: MapMarker[] = [];
  const origin = coordinate(freight.pickupLatitude, freight.pickupLongitude);
  const destination = coordinate(freight.destinationLatitude, freight.destinationLongitude);
  if (origin) mapMarkers.push({ ...origin, color: "#0f766e", label: "Origin" });
  if (destination) mapMarkers.push({ ...destination, color: "#2563eb", label: "Destination" });
  (tripDetail?.stops ?? []).forEach((stop: any) => {
    const stopPosition = coordinate(stop.pickupLatitude, stop.pickupLongitude);
    if (stopPosition) mapMarkers.push({ ...stopPosition, color: "#8b5cf6", label: stop.locationName ?? `Stop ${stop.stopOrder ?? ""}` });
  });
  const latestPosition = coordinate(latestLocation?.latitude, latestLocation?.longitude);
  if (latestPosition) mapMarkers.push({ ...latestPosition, color: "#f59e0b", label: "Current location" });
  const mapPoints = routeHistory.length > 0 ? routeHistory : latestPosition ? [latestPosition] : [];

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
          {tripsLoading && <div className="rounded-lg border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">Resolving the authorized trip for this shipment…</div>}
          {tripsError && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">Unable to load trip records from Spring Boot.</div>}
          {!tripsLoading && !tripsError && !tripId && <div className="rounded-lg border border-border/60 bg-muted/20 px-4 py-3 text-sm text-muted-foreground">No active trip is associated with this shipment yet. The map and live location will appear after a trip is created.</div>}
          {tripError && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">Trip details could not be refreshed.</div>}

          {/* Shipment Progress */}
          <div className="bg-[#0c1e4a] text-white rounded-xl p-5">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Package className="h-4 w-4 text-emerald-400" />
                <span className="text-sm font-semibold">Shipment Progress</span>
              </div>
              {freight.deadline && <span className="text-sm text-emerald-400">Deadline: {new Date(freight.deadline).toLocaleDateString()}</span>}
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
            {locationLoading ? <p className="text-sm text-muted-foreground">Refreshing latest location…</p> : latestLocation ? (
              <div className="space-y-1">
                {latestLocation.latitude != null && latestLocation.longitude != null && (
                  <p className="text-sm font-medium text-emerald-700">
                    {Number(latestLocation.latitude).toFixed(5)}°, {Number(latestLocation.longitude).toFixed(5)}°
                  </p>
                )}
                {tripDetail?.routeInfo?.completionPercentage != null && (
                  <p className="text-sm text-emerald-700">
                    Route progress: {Number(tripDetail.routeInfo.completionPercentage).toFixed(1)}%
                  </p>
                )}
                {routeHistory.length > 0 && (
                  <p className="text-xs text-muted-foreground">
                    {routeHistory.length} GPS point{routeHistory.length !== 1 ? "s" : ""} recorded
                  </p>
                )}
                <p className="text-xs text-muted-foreground">Last update: {latestLocation.timestamp ? new Date(latestLocation.timestamp).toLocaleString() : "Timestamp unavailable"}</p>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No location data yet. Driver hasn't started tracking.</p>
            )}
          </div>

          {tripDetail?.driver && <Card className="border-border/60"><CardContent className="p-4"><div className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">{tripDetail.driver.fullName?.split(" ").map((part: string) => part[0]).join("") ?? "D"}</div><div><p className="font-medium">{tripDetail.driver.fullName ?? "Driver"}</p><p className="text-sm text-muted-foreground">{tripDetail.driver.phone ?? "Phone unavailable"}{tripDetail.vehicle?.plateNumber ? ` · ${tripDetail.vehicle.plateNumber}` : ""}</p></div><Badge className="ml-auto" variant="outline">{tripDetail.tripStatus ?? freight.status}</Badge></div></CardContent></Card>}

          {routeAnalytics && !analyticsError && <div className="grid gap-3 sm:grid-cols-3"><div className="rounded-lg border border-border/60 p-3"><p className="text-xs text-muted-foreground">Recorded distance</p><p className="font-semibold">{routeAnalytics.totalDistanceKm != null ? `${Number(routeAnalytics.totalDistanceKm).toFixed(1)} km` : "Unavailable"}</p></div><div className="rounded-lg border border-border/60 p-3"><p className="text-xs text-muted-foreground">Average speed</p><p className="font-semibold">{routeAnalytics.averageSpeedKmh != null ? `${Number(routeAnalytics.averageSpeedKmh).toFixed(1)} km/h` : "Unavailable"}</p></div><div className="rounded-lg border border-border/60 p-3"><p className="text-xs text-muted-foreground">GPS points</p><p className="font-semibold">{routeAnalytics.totalGpsPoints ?? routeHistory.length}</p></div></div>}

          {tripDetail?.stops?.length > 0 && <div className="space-y-2"><h3 className="text-sm font-medium flex items-center gap-2"><MapPin className="h-4 w-4" /> Trip stops</h3><div className="space-y-2">{tripDetail.stops.map((stop: any) => <div key={stop.id ?? stop.stopOrder} className="flex items-center justify-between gap-3 rounded-lg border border-border/60 p-3 text-sm"><div><p className="font-medium">{stop.stopOrder != null ? `${stop.stopOrder}. ` : ""}{stop.locationName ?? "Unnamed stop"}</p><p className="text-xs text-muted-foreground">{stop.stopStatus ?? "Status unavailable"}</p></div><Badge variant="outline">{stop.completionTime ? "Completed" : stop.arrivalTime ? "Arrived" : "Pending"}</Badge></div>)}</div></div>}

          {/* Route History */}
          {(mapPoints.length > 0 || mapMarkers.length > 0) && (
            <div className="space-y-2">
              <RouteMap points={mapPoints} markers={mapMarkers} />
              <h3 className="text-sm font-medium flex items-center gap-2">
                <Route className="h-4 w-4" /> Route History ({routeHistory.length} points)
              </h3>
              {routeLoading ? <Skeleton className="h-20 w-full rounded-lg" /> : routeHistory.length > 0 && <div className="max-h-60 overflow-y-auto space-y-2 rounded-xl border border-border/60 p-3">
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
              </div>}
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

          {/* Shipper Confirm — navigate to payment page for Spring Boot M7 completion */}
          {(isOwner || isAdmin) && delivered && (
            <div className="space-y-3">
              <div className="p-4 rounded-xl bg-cyan-50 border border-cyan-200 text-center space-y-1">
                <Truck className="h-6 w-6 text-cyan-600 mx-auto" />
                <p className="font-medium text-cyan-800">Trip completed by driver</p>
                <p className="text-xs text-cyan-700">
                  Go to the Payment page to confirm and complete the payment.
                </p>
              </div>
              <Link href={`/payment/${freightId}`}>
                <Button className="w-full h-12 rounded-lg bg-emerald-600 hover:bg-emerald-700 gap-2">
                  <CheckCircle className="h-4 w-4" />
                  Go to Payment Page
                </Button>
              </Link>
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
