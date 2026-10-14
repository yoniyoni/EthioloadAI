import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "wouter";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { CompanyTeam } from "@/components/company-team";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { statusHelp, statusLabel, type Company } from "@/lib/carrier-companies";
import { ArrowRight, Building2, CheckCircle2, ClipboardList, MapPin, PackageCheck, Plus, RefreshCw, Truck, Users } from "lucide-react";

type Driver = { id: number; driverUserId: number; fullName: string; email: string; phone: string; status: string; availability: string; verificationStatus: boolean; isActive: boolean };
type Vehicle = { id: number; plateNumber: string; truckType: string; capacity: number; currentCity?: string; approvalStatus: string; availabilityStatus: string };
type Page<T> = { content: T[] };

export default function CompanyWorkspace({ id }: { id: string }) {
  const { memberships } = useAuth();
  const queryClient = useQueryClient();
  const companyId = Number(id);
  const membership = memberships.find(m => m.carrierCompanyId === companyId);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [driverEmail, setDriverEmail] = useState("");
  const company = useQuery({ queryKey: ["workspace-company", id], queryFn: () => api.get<Company>(`/carrier-companies/${id}`) });
  const approved = company.data?.verificationStatus === "APPROVED" && membership?.status === "ACTIVE";
  const base = `/carrier-companies/${id}/fleet`;
  const drivers = useQuery({ queryKey: ["company-drivers", id], queryFn: () => api.get<Page<Driver>>(`${base}/drivers?status=ACTIVE&size=100`), enabled: !!approved, refetchInterval: 15000 });
  const vehicles = useQuery({ queryKey: ["company-vehicles", id], queryFn: () => api.get<Page<Vehicle>>(`${base}/vehicles?size=100`), enabled: !!approved, refetchInterval: 15000 });
  const canManageFleet = ["CARRIER_OWNER", "FLEET_MANAGER"].includes(membership?.role ?? "");
  const canManageTeam = membership?.role === "CARRIER_OWNER";
  const driverList = drivers.data?.content ?? [];
  const vehicleList = vehicles.data?.content ?? [];
  const availableDrivers = useMemo(() => driverList.filter(d => d.status === "ACTIVE" && d.isActive && d.availability !== "ON_TRIP" && d.availability !== "UNAVAILABLE"), [driverList]);
  const availableVehicles = useMemo(() => vehicleList.filter(v => v.approvalStatus === "APPROVED" && !["assigned", "on_trip", "maintenance", "unavailable"].includes((v.availabilityStatus ?? "").toLowerCase())), [vehicleList]);

  async function refreshFleet() {
    await queryClient.invalidateQueries({ queryKey: ["company-drivers", id] });
    await queryClient.invalidateQueries({ queryKey: ["company-vehicles", id] });
  }

  async function addDriver(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true); setError(""); setNotice("");
    try {
      const identifier = driverEmail.trim();
      const result = await api.post<Driver>(`${base}/drivers`, { driverIdentifier: identifier });
      setDriverEmail(""); setNotice(`${result.fullName} is linked to this fleet and can receive dispatch assignments.`);
      await refreshFleet();
    } catch (e) { setError(e instanceof Error ? e.message : "Unable to add this driver."); }
    finally { setBusy(false); }
  }

  async function submit(e: React.FormEvent<HTMLFormElement>, path: string, body: (form: FormData) => object, success: string) {
    e.preventDefault();
    const form = e.currentTarget;
    setBusy(true); setError(""); setNotice("");
    try {
      const payload = body(new FormData(form));
      if (path === "/vehicles") await api.post(base + path, payload);
      else {
        const result = await api.post<{ id?: number }>(base + path, payload);
        if (!result?.id) throw new Error("Assignment was not confirmed by the server.");
      }
      form.reset(); setNotice(success); await refreshFleet();
    } catch (e) { setError(e instanceof Error ? e.message : "Unable to save this fleet change."); }
    finally { setBusy(false); }
  }

  if (company.isLoading) return <main className="container mx-auto max-w-6xl space-y-5 p-6"><div className="h-8 w-64 animate-pulse rounded bg-muted" /><div className="h-48 animate-pulse rounded-2xl bg-muted" /></main>;
  if (company.error) return <main className="container mx-auto max-w-6xl p-6"><p role="alert" className="rounded-xl bg-destructive/10 p-4 text-destructive">{company.error.message}</p></main>;
  if (!membership) return <main className="container mx-auto max-w-6xl p-6"><Card><CardContent className="p-6"><h1 className="text-xl font-semibold">Company access needed</h1><p className="mt-2 text-muted-foreground">Your account is not an active member of this company.</p><Button asChild className="mt-4"><Link href="/fleet/companies">My companies</Link></Button></CardContent></Card></main>;

  const status = company.data?.verificationStatus ?? "DRAFT";
  const canDispatch = ["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"].includes(membership.role);
  const availableVehicleCount = vehicleList.filter(v => v.approvalStatus === "APPROVED" && !["on_trip", "maintenance", "unavailable"].includes((v.availabilityStatus ?? "").toLowerCase())).length;

  return (
    <main className="container mx-auto max-w-6xl space-y-7 px-4 py-7 sm:px-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-2"><Link href="/fleet/companies" className="text-sm text-muted-foreground hover:text-foreground">← My companies</Link><div className="flex flex-wrap items-center gap-3"><h1 className="text-3xl font-bold tracking-tight">{company.data?.companyName}</h1><Badge variant={status === "APPROVED" ? "default" : "outline"}>{statusLabel[status]}</Badge></div><p className="flex items-center gap-2 text-sm text-muted-foreground"><Building2 className="h-4 w-4" /> {membership.role.replaceAll("_", " ")} {company.data?.city ? <><span>·</span><MapPin className="h-4 w-4" />{company.data.city}</> : null}</p></div>
        {membership.role === "CARRIER_OWNER" && <Button variant="outline" asChild><Link href={`/fleet/companies/${id}`}>Company application <ArrowRight className="ml-2 h-4 w-4" /></Link></Button>}
      </header>

      {status !== "APPROVED" ? <Card className="overflow-hidden rounded-2xl"><div className="h-1.5 bg-amber-400"/><CardContent className="space-y-4 p-6"><div className="flex items-start gap-3"><div className="rounded-xl bg-amber-100 p-3 text-amber-800"><ClipboardList className="h-6 w-6"/></div><div><h2 className="text-xl font-semibold">Your company is {statusLabel[status].toLowerCase()}</h2><p className="mt-1 max-w-2xl text-sm text-muted-foreground">{statusHelp[status]}</p></div></div>{company.data?.reviewMessage && <p className="rounded-lg bg-muted p-3 text-sm"><strong>Admin feedback:</strong> {company.data.reviewMessage}</p>}<Button asChild><Link href={`/fleet/companies/${id}`}>View application status <ArrowRight className="ml-2 h-4 w-4"/></Link></Button></CardContent></Card> : <>
        <section className="grid gap-3 sm:grid-cols-3" aria-label="Fleet summary">
          <Summary icon={<Users className="h-5 w-5"/>} label="Active drivers" value={driverList.length} detail="Company-linked accounts"/>
          <Summary icon={<Truck className="h-5 w-5"/>} label="Vehicles" value={vehicleList.length} detail={`${availableVehicleCount} approved and available`}/>
          <Summary icon={<PackageCheck className="h-5 w-5"/>} label="Next step" value="Find freight" detail="Offer, agree, then dispatch"/>
        </section>

        {(error || notice) && <p role={error ? "alert" : "status"} className={`rounded-xl px-4 py-3 text-sm ${error ? "bg-destructive/10 text-destructive" : "bg-emerald-50 text-emerald-800"}`}>{error || notice}</p>}

        <section className="grid gap-3 sm:grid-cols-3" aria-label="Company workspaces">
          {(membership.role === "CARRIER_OWNER" || membership.role === "FLEET_MANAGER" || membership.role === "DISPATCHER") && <WorkspaceLink href={`/carrier-offers/company/${id}`} title="Open freight & offers" detail="See shipper loads, submit offers and negotiate rates" icon={<PackageCheck className="h-5 w-5"/>}/>}
          {canDispatch && <WorkspaceLink href={`/carrier-dispatch/company/${id}`} title="Dispatch board" detail="Assign a driver and approved vehicle" icon={<Truck className="h-5 w-5"/>}/>}
          {membership.role === "CARRIER_OWNER" && <WorkspaceLink href={`/carrier/${id}/settlements`} title="Settlements" detail="Review delivery payments and company earnings" icon={<ClipboardList className="h-5 w-5"/>}/>}
        </section>

        <Separator />
        <section className="grid gap-6 lg:grid-cols-2">
          <Card className="rounded-2xl border-border/70 shadow-sm">
            <CardHeader><CardTitle className="flex items-center gap-2"><Users className="h-5 w-5 text-primary"/> Drivers</CardTitle><CardDescription>Drivers must register with the Driver account type and submit their required documents first.</CardDescription></CardHeader>
            <CardContent className="space-y-4">
              {canManageFleet && <form onSubmit={addDriver} className="flex flex-col gap-2 rounded-xl bg-muted/40 p-4 sm:flex-row sm:items-end"><div className="flex-1 space-y-2"><Label htmlFor="driver-email">Registered driver email or phone</Label><Input id="driver-email" autoComplete="email" placeholder="driver@example.com or +251…" required value={driverEmail} onChange={e => setDriverEmail(e.target.value)} /><p className="text-xs text-muted-foreground">The driver must already have a registered, approved Driver account. Staff invitations use the Company Team section.</p></div><Button type="submit" disabled={busy} className="gap-2"><Plus className="h-4 w-4"/>{busy ? "Adding…" : "Add driver"}</Button></form>}
              {drivers.isError ? <p role="alert" className="rounded-xl bg-destructive/10 p-3 text-sm text-destructive">{drivers.error.message}</p> : drivers.isLoading ? <p className="text-sm text-muted-foreground">Loading drivers…</p> : driverList.length === 0 ? <EmptyState title="No drivers linked yet" detail="Add a registered driver by their account email. They’ll appear here when linked to this company."/> : <div className="divide-y rounded-xl border">{driverList.map(driver => <div key={driver.id} className="flex flex-wrap items-center justify-between gap-3 p-3"><div className="min-w-0"><p className="truncate font-medium">{driver.fullName}</p><p className="truncate text-sm text-muted-foreground">{driver.email} · account #{driver.driverUserId}</p></div><div className="flex items-center gap-2"><Badge variant="outline">{driver.availability.replaceAll("_", " ")}</Badge><Badge variant={driver.status === "ACTIVE" && driver.isActive ? "default" : "secondary"}>{driver.status}</Badge></div></div>)}</div>}
            </CardContent>
          </Card>

          <Card className="rounded-2xl border-border/70 shadow-sm">
            <CardHeader><CardTitle className="flex items-center gap-2"><Truck className="h-5 w-5 text-primary"/> Vehicles</CardTitle><CardDescription>Keep vehicle capacity and readiness current so dispatch only selects suitable equipment.</CardDescription></CardHeader>
            <CardContent className="space-y-4">
              {canManageFleet && <form onSubmit={e => void submit(e, "/vehicles", f => ({plateNumber: String(f.get("plateNumber")).trim().toUpperCase(), truckType: f.get("truckType"), capacity: Number(f.get("capacity")), currentCity: f.get("currentCity") || undefined}), "Vehicle registered. It must be approved before it can be dispatched.")} className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2"><Label htmlFor="vehicle-plate">Plate number</Label><Input id="vehicle-plate" name="plateNumber" placeholder="3-A12345" required /></div><div className="space-y-2"><Label htmlFor="vehicle-type">Vehicle type</Label><Input id="vehicle-type" name="truckType" placeholder="Flatbed, tanker…" required /></div><div className="space-y-2"><Label htmlFor="vehicle-capacity">Capacity (tons)</Label><Input id="vehicle-capacity" name="capacity" type="number" min="0.1" step="0.1" placeholder="10" required /></div><div className="space-y-2"><Label htmlFor="vehicle-category">Category</Label><select id="vehicle-category" name="vehicleCategory" required className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"><option value="">Select category</option><option value="HEAVY">Heavy vehicle</option><option value="LIGHT">Light vehicle</option></select></div><div className="space-y-2"><Label htmlFor="vehicle-city">Current city <span className="text-muted-foreground">(optional)</span></Label><Input id="vehicle-city" name="currentCity" placeholder="Addis Ababa" /></div><div className="sm:col-span-2"><Button type="submit" disabled={busy} className="gap-2"><Plus className="h-4 w-4"/> Register vehicle</Button></div>
              </form>}
              {vehicles.isError ? <p role="alert" className="rounded-xl bg-destructive/10 p-3 text-sm text-destructive">{vehicles.error.message}</p> : vehicles.isLoading ? <p className="text-sm text-muted-foreground">Loading vehicles…</p> : vehicleList.length === 0 ? <EmptyState title="No vehicles registered" detail="Register your company vehicles here. Only approved, available vehicles can be dispatched."/> : <div className="divide-y rounded-xl border">{vehicleList.map(vehicle => <div key={vehicle.id} className="flex flex-wrap items-center justify-between gap-3 p-3"><div className="min-w-0"><p className="truncate font-medium">{vehicle.plateNumber} <span className="font-normal text-muted-foreground">· {vehicle.truckType}</span></p><p className="text-sm text-muted-foreground">{vehicle.capacity} tons{vehicle.currentCity ? ` · ${vehicle.currentCity}` : ""}</p></div><div className="flex items-center gap-2"><Badge variant={vehicle.approvalStatus === "APPROVED" ? "default" : "outline"}>{vehicle.approvalStatus.replaceAll("_", " ")}</Badge><Badge variant="secondary">{vehicle.availabilityStatus.replaceAll("_", " ")}</Badge></div></div>)}</div>}
            </CardContent>
          </Card>
        </section>

        {canManageFleet && <Card className="rounded-2xl border-border/70 shadow-sm"><CardHeader><CardTitle className="flex items-center gap-2"><ClipboardList className="h-5 w-5 text-primary"/> Pair a driver and vehicle</CardTitle><CardDescription>Set a regular fleet assignment. A shipment dispatch can still choose an eligible driver and vehicle separately.</CardDescription></CardHeader><CardContent><form onSubmit={e => void submit(e, "/assignments", f => ({carrierDriverId: Number(f.get("driver")), vehicleId: Number(f.get("vehicle"))}), "Driver and vehicle assigned.")} className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"><div className="space-y-2"><Label htmlFor="assignment-driver">Driver</Label><select id="assignment-driver" name="driver" required className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" defaultValue=""><option value="" disabled>Select a driver</option>{availableDrivers.map(driver => <option key={driver.id} value={driver.id}>{driver.fullName} · {driver.availability}</option>)}</select></div><div className="space-y-2"><Label htmlFor="assignment-vehicle">Vehicle</Label><select id="assignment-vehicle" name="vehicle" required className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" defaultValue=""><option value="" disabled>Select an available vehicle</option>{availableVehicles.map(vehicle => <option key={vehicle.id} value={vehicle.id}>{vehicle.plateNumber} · {vehicle.truckType}</option>)}</select></div><Button type="submit" disabled={busy || !availableDrivers.length || !availableVehicles.length} className="gap-2"><CheckCircle2 className="h-4 w-4"/> Assign</Button></form>{(!availableDrivers.length || !availableVehicles.length) && <p className="mt-3 text-sm text-muted-foreground">Add an available driver and vehicle to create a regular pairing.</p>}</CardContent></Card>}

        {canManageTeam && <CompanyTeam companyId={companyId}/>}
        <div className="flex items-center justify-between border-t pt-4"><p className="text-sm text-muted-foreground">Need the latest company status or fleet records?</p><Button variant="ghost" size="sm" onClick={() => { void company.refetch(); void refreshFleet(); }} disabled={company.isFetching || drivers.isFetching || vehicles.isFetching}><RefreshCw className={`mr-2 h-4 w-4 ${company.isFetching ? "animate-spin" : ""}`}/> Refresh workspace</Button></div>
      </>}
    </main>
  );
}

function Summary({ icon, label, value, detail }: { icon: ReactNode; label: string; value: string | number; detail: string }) {
  return <Card className="rounded-2xl border-border/70"><CardContent className="flex items-center gap-4 p-4"><div className="rounded-xl bg-primary/10 p-3 text-primary">{icon}</div><div className="min-w-0"><p className="text-sm text-muted-foreground">{label}</p><p className="text-xl font-semibold">{value}</p><p className="truncate text-xs text-muted-foreground">{detail}</p></div></CardContent></Card>;
}

function WorkspaceLink({ href, title, detail, icon }: { href: string; title: string; detail: string; icon: ReactNode }) {
  return <Link href={href} className="group rounded-2xl border bg-card p-4 transition-colors hover:border-primary/50 hover:bg-primary/[0.03]"><div className="flex items-center justify-between"><span className="rounded-lg bg-primary/10 p-2 text-primary">{icon}</span><ArrowRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:translate-x-1 group-hover:text-primary"/></div><h2 className="mt-3 font-semibold">{title}</h2><p className="mt-1 text-sm text-muted-foreground">{detail}</p></Link>;
}

function EmptyState({ title, detail }: { title: string; detail: string }) {
  return <div className="rounded-xl border border-dashed p-5"><p className="font-medium">{title}</p><p className="mt-1 text-sm text-muted-foreground">{detail}</p></div>;
}
