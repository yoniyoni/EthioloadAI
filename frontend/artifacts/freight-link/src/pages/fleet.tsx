import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useLocation } from "wouter";
import { api } from "@/lib/api";
import { useToast } from "@/hooks/use-toast";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Building2, CheckCircle2, Loader2, MapPin, Plus, ShieldCheck, Truck, UserRound, Users, XCircle } from "lucide-react";

interface FleetDriver {
  id: number;
  fullName: string;
  phone?: string | null;
  email?: string | null;
  isActive?: boolean | null;
  verificationStatus?: boolean | null;
}

interface FleetVehicle {
  id: number;
  plateNumber: string;
  truckType: string;
  vehicleCategory?: string | null;
  capacity?: number | null;
  currentCity?: string | null;
  availabilityStatus?: string | null;
  assignedDriverId?: number | null;
}

interface FleetBooking {
  bookingId: number;
  driverName?: string | null;
  status?: string | null;
  route?: string | null;
}

interface FleetDashboard {
  driverCount: number;
  vehicleCount: number;
  activeBookingCount: number;
  drivers: FleetDriver[];
  vehicles: FleetVehicle[];
  activeBookings: FleetBooking[];
}

const truckTypes = ["pickup", "light_truck", "medium_truck", "heavy_truck", "tanker", "refrigerated", "flatbed", "tipper"];

function statusClass(status?: string | null) {
  const normalized = String(status ?? "").toLowerCase();
  if (["available", "active", "verified"].includes(normalized)) return "bg-emerald-50 text-emerald-700 border-emerald-200";
  if (["in_use", "in_transit", "pending"].includes(normalized)) return "bg-amber-50 text-amber-700 border-amber-200";
  return "bg-slate-50 text-slate-600 border-slate-200";
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return <div className="rounded-xl border border-dashed border-border/70 bg-muted/20 px-6 py-12 text-center"><p className="font-medium">{title}</p><p className="mt-1 text-sm text-muted-foreground">{description}</p></div>;
}

export default function Fleet() {
  const [location] = useLocation();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [vehicleForm, setVehicleForm] = useState({ truckType: "medium_truck", plateNumber: "", capacity: "", currentCity: "", vehicleCategory: "" });
  const [assignment, setAssignment] = useState<Record<number, string>>({});

  const dashboardQuery = useQuery<FleetDashboard>({
    queryKey: ["fleet-dashboard"],
    queryFn: () => api.get<FleetDashboard>("/fleet/dashboard"),
  });
  const driversQuery = useQuery<FleetDriver[]>({
    queryKey: ["fleet-drivers"],
    queryFn: () => api.get<FleetDriver[]>("/fleet/drivers"),
  });
  const vehiclesQuery = useQuery<FleetVehicle[]>({
    queryKey: ["fleet-vehicles"],
    queryFn: () => api.get<FleetVehicle[]>("/fleet/vehicles"),
  });

  const addVehicleMutation = useMutation({
    mutationFn: () => api.post<FleetVehicle>("/fleet/vehicles", {
      truckType: vehicleForm.truckType,
      plateNumber: vehicleForm.plateNumber.trim(),
      capacity: Number(vehicleForm.capacity),
      currentCity: vehicleForm.currentCity.trim(),
      vehicleCategory: vehicleForm.vehicleCategory || undefined,
    }),
    onSuccess: () => {
      toast({ title: "Vehicle added", description: "The vehicle is now part of your fleet." });
      setVehicleForm({ truckType: "medium_truck", plateNumber: "", capacity: "", currentCity: "", vehicleCategory: "" });
      queryClient.invalidateQueries({ queryKey: ["fleet-vehicles"] });
      queryClient.invalidateQueries({ queryKey: ["fleet-dashboard"] });
    },
    onError: (error: Error) => toast({ title: "Unable to add vehicle", description: error.message, variant: "destructive" }),
  });

  const assignMutation = useMutation({
    mutationFn: ({ vehicleId, driverId }: { vehicleId: number; driverId: number }) => api.put<FleetVehicle>(`/fleet/vehicles/${vehicleId}/assign`, { driverId }),
    onSuccess: () => {
      toast({ title: "Assignment updated" });
      queryClient.invalidateQueries({ queryKey: ["fleet-vehicles"] });
      queryClient.invalidateQueries({ queryKey: ["fleet-dashboard"] });
    },
    onError: (error: Error) => toast({ title: "Assignment failed", description: error.message, variant: "destructive" }),
  });

  const dashboard = dashboardQuery.data;
  const drivers: FleetDriver[] = Array.isArray(driversQuery.data) ? driversQuery.data : (((driversQuery.data as any)?.content ?? []) as FleetDriver[]);
  const vehicles: FleetVehicle[] = Array.isArray(vehiclesQuery.data) ? vehiclesQuery.data : (((vehiclesQuery.data as any)?.content ?? []) as FleetVehicle[]);
  const currentTab = location === "/fleet/drivers" ? "drivers" : location === "/fleet/vehicles" ? "vehicles" : "overview";
  const isLoading = dashboardQuery.isLoading || driversQuery.isLoading || vehiclesQuery.isLoading;

  const driverName = (driverId?: number | null) => drivers.find((driver) => driver.id === driverId)?.fullName ?? "Unassigned";

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex items-center gap-2"><Building2 className="h-5 w-5 text-primary" /><h1 className="text-3xl font-bold tracking-tight">Fleet workspace</h1></div>
          <p className="mt-1 text-muted-foreground">Manage your fleet drivers, vehicles, and assignments.</p>
        </div>
        {currentTab === "vehicles" && <Button className="gap-2 rounded-lg" onClick={() => document.getElementById("add-fleet-vehicle")?.scrollIntoView({ behavior: "smooth" })}><Plus className="h-4 w-4" /> Add vehicle</Button>}
      </div>

      <nav className="mb-6 flex gap-2 overflow-x-auto border-b border-border/60 pb-2" aria-label="Fleet workspace">
        <Link href="/fleet/companies" className="whitespace-nowrap rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground">Company registration</Link>
        {[{ href: "/fleet", label: "Overview" }, { href: "/fleet/drivers", label: "Drivers" }, { href: "/fleet/vehicles", label: "Vehicles" }].map((tab) => (
          <Link key={tab.href} href={tab.href} className={`whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium ${location === tab.href ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground"}`}>{tab.label}</Link>
        ))}
      </nav>

      {isLoading && <div className="grid gap-4 md:grid-cols-3"><Skeleton className="h-28 rounded-xl" /><Skeleton className="h-28 rounded-xl" /><Skeleton className="h-28 rounded-xl" /></div>}
      {(dashboardQuery.isError || driversQuery.isError || vehiclesQuery.isError) && <p role="alert" className="mb-6 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">Some fleet data could not be loaded. Refresh to try again.</p>}

      {!isLoading && currentTab === "overview" && <div className="space-y-6">
        <div className="grid gap-4 md:grid-cols-3">
          {[{ label: "Fleet drivers", value: dashboard?.driverCount ?? drivers.length, icon: Users }, { label: "Fleet vehicles", value: dashboard?.vehicleCount ?? vehicles.length, icon: Truck }, { label: "Active bookings", value: dashboard?.activeBookingCount ?? 0, icon: ShieldCheck }].map((metric) => <Card key={metric.label} className="border-border/60"><CardContent className="flex items-center justify-between pt-6"><div><p className="text-sm text-muted-foreground">{metric.label}</p><p className="mt-1 text-3xl font-bold">{metric.value}</p></div><metric.icon className="h-7 w-7 text-primary" /></CardContent></Card>)}
        </div>
        <div className="grid gap-6 lg:grid-cols-2">
          <Card className="border-border/60"><CardHeader><CardTitle className="text-base">Vehicle assignments</CardTitle></CardHeader><CardContent>{vehicles.length === 0 ? <EmptyState title="No fleet vehicles" description="Vehicles registered to your fleet will appear here." /> : <div className="space-y-3">{vehicles.slice(0, 5).map((vehicle) => <div key={vehicle.id} className="flex items-center justify-between gap-3 rounded-lg border border-border/60 p-3"><div><p className="font-medium">{vehicle.plateNumber}</p><p className="text-sm text-muted-foreground">{vehicle.truckType?.replace(/_/g, " ")} · {vehicle.capacity ?? "-"}t</p></div><div className="text-right"><p className="text-sm">{driverName(vehicle.assignedDriverId)}</p><Badge variant="outline" className={statusClass(vehicle.availabilityStatus)}>{vehicle.availabilityStatus ?? "status unavailable"}</Badge></div></div>)}</div>}</CardContent></Card>
          <Card className="border-border/60"><CardHeader><CardTitle className="text-base">Active fleet bookings</CardTitle></CardHeader><CardContent>{(dashboard?.activeBookings ?? []).length === 0 ? <EmptyState title="No active bookings" description="Active bookings for your fleet drivers will appear here." /> : <div className="space-y-3">{dashboard?.activeBookings.map((booking) => <div key={booking.bookingId} className="rounded-lg border border-border/60 p-3"><div className="flex items-center justify-between gap-3"><p className="font-medium">Booking #{booking.bookingId}</p><Badge variant="outline" className={statusClass(booking.status)}>{booking.status ?? "status unavailable"}</Badge></div><p className="mt-1 text-sm text-muted-foreground">{booking.driverName ?? "Driver unavailable"} · {booking.route ?? "Route unavailable"}</p></div>)}</div>}</CardContent></Card>
        </div>
      </div>}

      {!isLoading && currentTab === "drivers" && <Card className="border-border/60"><CardHeader><CardTitle className="text-base">Fleet drivers</CardTitle></CardHeader><CardContent>{drivers.length === 0 ? <EmptyState title="No drivers in this fleet" description="Drivers associated with your fleet will appear here." /> : <div className="grid gap-3 md:grid-cols-2">{drivers.map((driver) => <div key={driver.id} className="rounded-lg border border-border/60 p-4"><div className="flex items-start justify-between gap-3"><div><p className="font-medium">{driver.fullName}</p><p className="text-sm text-muted-foreground">{driver.email ?? driver.phone ?? "Contact unavailable"}</p></div><Badge variant="outline" className={driver.isActive ? "bg-emerald-50 text-emerald-700 border-emerald-200" : "bg-amber-50 text-amber-700 border-amber-200"}>{driver.isActive ? "Active" : "Inactive"}</Badge></div><div className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">{driver.verificationStatus ? <><CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> KYC verified</> : <><XCircle className="h-3.5 w-3.5 text-amber-600" /> KYC status unavailable or pending</>}</div></div>)}</div>}</CardContent></Card>}

      {!isLoading && currentTab === "vehicles" && <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Card className="border-border/60"><CardHeader><CardTitle className="text-base">Fleet vehicles</CardTitle></CardHeader><CardContent>{vehicles.length === 0 ? <EmptyState title="No fleet vehicles" description="Add a vehicle using the form beside this list." /> : <div className="space-y-3">{vehicles.map((vehicle) => <div key={vehicle.id} className="rounded-lg border border-border/60 p-4"><div className="flex items-start justify-between gap-3"><div><p className="font-medium">{vehicle.plateNumber}</p><p className="text-sm capitalize text-muted-foreground">{vehicle.truckType?.replace(/_/g, " ")} · {vehicle.capacity ?? "-"}t</p></div><Badge variant="outline" className={statusClass(vehicle.availabilityStatus)}>{vehicle.availabilityStatus ?? "status unavailable"}</Badge></div><div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground"><MapPin className="h-4 w-4" />{vehicle.currentCity ?? "Current city unavailable"}</div><div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center"><UserRound className="hidden h-4 w-4 text-muted-foreground sm:block" /><Select value={assignment[vehicle.id] ?? ""} onValueChange={(value) => setAssignment((current) => ({ ...current, [vehicle.id]: value }))}><SelectTrigger className="rounded-lg"><SelectValue placeholder={driverName(vehicle.assignedDriverId)} /></SelectTrigger><SelectContent>{drivers.map((driver) => <SelectItem key={driver.id} value={String(driver.id)} disabled={!driver.isActive}>{driver.fullName}{!driver.isActive ? " (inactive)" : ""}</SelectItem>)}</SelectContent></Select><Button variant="outline" className="rounded-lg" disabled={!assignment[vehicle.id] || assignMutation.isPending} onClick={() => assignMutation.mutate({ vehicleId: vehicle.id, driverId: Number(assignment[vehicle.id]) })}>{assignMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : "Assign"}</Button></div><p className="mt-2 text-xs text-muted-foreground">The backend requires the selected driver to belong to this fleet and be active.</p></div>)}</div>}</CardContent></Card>
        <Card id="add-fleet-vehicle" className="h-fit border-border/60"><CardHeader><CardTitle className="text-base">Add fleet vehicle</CardTitle></CardHeader><CardContent className="space-y-4"><div className="space-y-1.5"><Label>Truck type</Label><Select value={vehicleForm.truckType} onValueChange={(value) => setVehicleForm((current) => ({ ...current, truckType: value }))}><SelectTrigger className="rounded-lg"><SelectValue /></SelectTrigger><SelectContent>{truckTypes.map((type) => <SelectItem key={type} value={type}>{type.replace(/_/g, " ")}</SelectItem>)}</SelectContent></Select></div><div className="space-y-1.5"><Label>Plate number</Label><Input value={vehicleForm.plateNumber} onChange={(event) => setVehicleForm((current) => ({ ...current, plateNumber: event.target.value }))} placeholder="AA-12345" className="rounded-lg" /></div><div className="space-y-1.5"><Label>Capacity (tons)</Label><Input type="number" min="0.1" step="0.1" value={vehicleForm.capacity} onChange={(event) => setVehicleForm((current) => ({ ...current, capacity: event.target.value }))} placeholder="20" className="rounded-lg" /></div><div className="space-y-1.5"><Label>Current city</Label><Input value={vehicleForm.currentCity} onChange={(event) => setVehicleForm((current) => ({ ...current, currentCity: event.target.value }))} placeholder="Addis Ababa" className="rounded-lg" /></div><div className="space-y-1.5"><Label>Vehicle category</Label><Select value={vehicleForm.vehicleCategory} onValueChange={(value) => setVehicleForm((current) => ({ ...current, vehicleCategory: value }))}><SelectTrigger className="rounded-lg"><SelectValue placeholder="Optional" /></SelectTrigger><SelectContent><SelectItem value="heavy">Heavy</SelectItem><SelectItem value="light">Light</SelectItem></SelectContent></Select></div><Button className="w-full rounded-lg" disabled={!vehicleForm.plateNumber.trim() || !vehicleForm.capacity || !vehicleForm.currentCity.trim() || addVehicleMutation.isPending} onClick={() => addVehicleMutation.mutate()}>{addVehicleMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Plus className="mr-2 h-4 w-4" />}Add vehicle</Button></CardContent></Card>
      </div>}
    </div>
  );
}
