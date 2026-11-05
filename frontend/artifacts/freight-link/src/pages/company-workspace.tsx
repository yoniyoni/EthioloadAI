import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "wouter";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { CompanyTeam } from "@/components/company-team";
import { CompanyFleetManagement } from "@/components/company-fleet-management";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { statusHelp, statusLabel, type Company } from "@/lib/carrier-companies";
import { ArrowRight, Building2, CheckCircle2, ClipboardList, MapPin, PackageCheck, RefreshCw, Truck, Users } from "lucide-react";

type Driver = { id: number; carrierCompanyId: number; driverUserId: number; fullName: string; email: string; phone: string; status: string; availability: string; verificationStatus: boolean; isActive: boolean };
type Vehicle = { id: number; carrierCompanyId: number; plateNumber: string; truckType: string; capacity: number; currentCity?: string; approvalStatus: string; availabilityStatus: string };
type Page<T> = { content: T[]; totalElements?: number };

export default function CompanyWorkspace({ id }: { id: string }) {
  const { user, memberships, membershipError, membershipRevalidating, refreshMemberships } = useAuth();
  const queryClient = useQueryClient();
  const parsedCompanyId = Number(id);
  const companyId = Number.isSafeInteger(parsedCompanyId) && parsedCompanyId > 0 && String(parsedCompanyId) === id
    ? parsedCompanyId
    : undefined;
  const membership = companyId
    ? memberships.find(m => m.carrierCompanyId === companyId && m.status === "ACTIVE")
    : undefined;
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [assignmentDriverId, setAssignmentDriverId] = useState("");
  const [assignmentVehicleId, setAssignmentVehicleId] = useState("");
  useEffect(() => {
    setAssignmentDriverId("");
    setAssignmentVehicleId("");
    setError("");
    setNotice("");
  }, [companyId]);
  const company = useQuery({
    queryKey: ["workspace-company", user?.id, companyId],
    queryFn: async () => {
      if (!membership || !companyId) throw new Error("An active company membership is required.");
      const result = await api.get<Company>(`/carrier-companies/${membership.carrierCompanyId}`);
      if (result.id !== membership.carrierCompanyId) throw new Error("The company response did not match the selected workspace.");
      return result;
    },
    enabled: !!membership && !!companyId && !membershipRevalidating && !membershipError,
  });
  const approved = !company.isFetching && company.data?.verificationStatus === "APPROVED" && !!membership && company.data.id === companyId;
  const base = membership ? `/carrier-companies/${membership.carrierCompanyId}/fleet` : "";
  const drivers = useQuery({
    queryKey: ["company-drivers", companyId],
    queryFn: async () => {
      if (!membership || !companyId) throw new Error("An active company membership is required.");
      const page = await api.get<Page<Driver>>(`${base}/drivers?status=ACTIVE&size=100`);
      if (!page || !Array.isArray(page.content) || page.content.some(driver => driver.carrierCompanyId !== companyId)) {
        throw new Error("The driver response did not match the selected company.");
      }
      return page;
    },
    enabled: !!approved && !membershipRevalidating && !membershipError,
    refetchInterval: 15000,
  });
  const vehicles = useQuery({
    queryKey: ["company-vehicles", companyId],
    queryFn: async () => {
      if (!membership || !companyId) throw new Error("An active company membership is required.");
      const page = await api.get<Page<Vehicle>>(`${base}/vehicles?size=100`);
      if (!page || !Array.isArray(page.content) || page.content.some(vehicle => vehicle.carrierCompanyId !== companyId)) {
        throw new Error("The vehicle response did not match the selected company.");
      }
      return page;
    },
    enabled: !!approved && !membershipRevalidating && !membershipError,
    refetchInterval: 15000,
  });
  const canManageFleet = ["CARRIER_OWNER", "FLEET_MANAGER"].includes(membership?.role ?? "");
  const canManageTeam = membership?.role === "CARRIER_OWNER";
  const canDispatch = ["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"].includes(membership?.role ?? "");
  const canManageAssignments = canDispatch && approved && !membershipRevalidating && !membershipError;
  const driverList = drivers.data?.content ?? [];
  const vehicleList = vehicles.data?.content ?? [];
  const assignmentDrivers = useMemo(() => driverList.filter(d =>
    d.status === "ACTIVE" && d.isActive && !["ON_TRIP", "UNAVAILABLE"].includes(d.availability.toUpperCase()),
  ), [driverList]);
  const assignmentVehicles = useMemo(() => vehicleList.filter(v =>
    v.approvalStatus === "APPROVED" &&
    !["on_trip", "maintenance", "unavailable"].includes((v.availabilityStatus ?? "").toLowerCase()),
  ), [vehicleList]);

  async function refreshFleet() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["company-drivers", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-vehicles", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-drivers", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-vehicles", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-driver", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-vehicle", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-driver-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-vehicle-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-assignments", companyId] }),
    ]);
  }

  async function submitAssignment(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!approved || !canDispatch || !membership || membershipRevalidating || membershipError) {
      setError("Only an owner, fleet manager, or dispatcher of an approved company can manage assignments.");
      return;
    }
    const form = e.currentTarget;
    const driver = assignmentDrivers.find(item => String(item.id) === assignmentDriverId);
    const vehicle = assignmentVehicles.find(item => String(item.id) === assignmentVehicleId);
    const notes = String(new FormData(form).get("assignmentNotes") ?? "").trim();
    if (!driver || !vehicle) {
      setError("Select a driver and vehicle from this company.");
      return;
    }
    if (notes.length > 1000) {
      setError("Assignment notes must be 1,000 characters or fewer.");
      return;
    }
    if (!window.confirm(
      `Assign ${driver.fullName} to ${vehicle.plateNumber} (${vehicle.truckType})?` +
      `\n\nCreating this assignment may automatically end the selected driver's existing active assignment and/or the selected vehicle's existing active assignment.` +
      `\n\nBackend validation remains authoritative; this regular pairing does not guarantee dispatch eligibility.`,
    )) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await api.post<{ id?: number; carrierCompanyId?: number }>(`${base}/assignments`, {
        carrierDriverId: driver.id,
        vehicleId: vehicle.id,
        ...(notes ? { notes } : {}),
      });
      if (!result?.id || result.carrierCompanyId !== companyId) {
        throw new Error("Assignment was not confirmed for the selected company.");
      }
      form.reset();
      setAssignmentDriverId("");
      setAssignmentVehicleId("");
      setNotice("Driver–vehicle assignment created. Review company validation or dispatch eligibility separately.");
      await refreshFleet();
    } catch (e) { setError(e instanceof Error ? e.message : "Unable to create the assignment."); }
    finally { setBusy(false); }
  }

  if (!companyId || !membership) return <main className="container mx-auto max-w-6xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7"><Card className="border-border/70 shadow-sm"><CardContent className="p-5 sm:p-6"><div className="mb-4 flex h-11 w-11 items-center justify-center rounded-xl bg-muted text-muted-foreground"><Building2 className="h-5 w-5" aria-hidden="true" /></div><h1 className="text-xl font-semibold tracking-tight">Company access needed</h1><p className="mt-2 max-w-xl text-sm leading-6 text-muted-foreground">Select a company where your account has an active membership.</p><Button asChild className="mt-5 min-h-11"><Link href="/dashboard">Choose a company</Link></Button></CardContent></Card></main>;
  if (membershipRevalidating) return <main className="container mx-auto max-w-6xl space-y-5 px-3 py-5 sm:px-5 sm:py-7 lg:px-7" aria-label="Refreshing company access"><div className="h-7 w-48 animate-pulse rounded bg-muted" /><div className="h-12 w-2/3 max-w-xl animate-pulse rounded bg-muted" /><div className="h-56 animate-pulse rounded-xl border bg-muted/30" /></main>;
  if (membershipError) return <main className="container mx-auto max-w-6xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7"><Card><CardContent className="space-y-3 p-5 sm:p-6"><h1 className="text-xl font-semibold">Unable to verify company access</h1><p role="alert" className="text-sm leading-6 text-destructive">{membershipError}</p><Button type="button" variant="outline" className="min-h-11" onClick={() => void refreshMemberships().catch(() => undefined)}>Retry company access</Button></CardContent></Card></main>;
  if (company.isLoading) return <main className="container mx-auto max-w-6xl space-y-5 px-3 py-5 sm:px-5 sm:py-7 lg:px-7" aria-label="Loading company workspace"><div className="h-7 w-48 animate-pulse rounded bg-muted" /><div className="h-12 w-2/3 max-w-xl animate-pulse rounded bg-muted" /><div className="grid gap-3 sm:grid-cols-3">{[0, 1, 2].map(item => <div key={item} className="h-24 animate-pulse rounded-xl border bg-muted/30" />)}</div><div className="h-56 animate-pulse rounded-xl border bg-muted/30" /></main>;
  if (company.error) return <main className="container mx-auto max-w-6xl space-y-4 px-3 py-5 sm:px-5 sm:py-7 lg:px-7"><div><p className="text-sm font-medium text-muted-foreground">Company workspace</p><h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Unable to load company</h1></div><div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="break-words text-sm text-destructive">{company.error.message}</p><Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => company.refetch()}>Retry</Button></div></main>;
  const status = company.isFetching ? "PENDING" : company.data?.verificationStatus ?? "DRAFT";
  const driverCount = drivers.data?.totalElements ?? driverList.length;
  const vehicleCount = vehicles.data?.totalElements ?? vehicleList.length;
  const availableVehicleCount = vehicleList.filter(v => v.approvalStatus === "APPROVED" && !["on_trip", "maintenance", "unavailable"].includes((v.availabilityStatus ?? "").toLowerCase())).length;

  return (
    <main className="container mx-auto max-w-6xl space-y-6 px-3 py-5 sm:space-y-7 sm:px-5 sm:py-7 lg:px-7">
      <header className="flex flex-col gap-5 border-b border-border/70 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0 space-y-3">
          <Link href="/fleet/companies" className="inline-flex min-h-10 items-center rounded-md text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2">← My companies</Link>
          <div className="min-w-0">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium text-muted-foreground">Company workspace</span>
              <Badge variant={status === "APPROVED" ? "default" : "outline"}>{statusLabel[status]}</Badge>
            </div>
            <h1 className="break-words text-2xl font-bold tracking-tight sm:text-3xl">{company.data?.companyName}</h1>
          </div>
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground"><Building2 className="h-4 w-4 shrink-0" aria-hidden="true" /><span>{membership.role.replaceAll("_", " ")}</span>{company.data?.city && <><span aria-hidden="true">·</span><MapPin className="h-4 w-4 shrink-0" aria-hidden="true" /><span className="break-words">{company.data.city}</span></>}</p>
        </div>
        {membership.role === "CARRIER_OWNER" && <Button type="button" variant="outline" asChild className="min-h-11 w-full shrink-0 sm:w-auto"><Link href={`/fleet/companies/${membership.carrierCompanyId}`}>Company application <ArrowRight className="ml-2 h-4 w-4" /></Link></Button>}
      </header>

      {status !== "APPROVED" ? <Card className="overflow-hidden border-border/70 shadow-sm"><div className="h-1 bg-primary/60"/><CardContent className="space-y-5 p-5 sm:p-6"><div className="flex items-start gap-4"><div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><ClipboardList className="h-5 w-5" aria-hidden="true"/></div><div><h2 className="text-lg font-semibold">Company is {(statusLabel[status] ?? status.replaceAll("_", " ")).toLowerCase()}</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">{statusHelp[status] ?? "Company operations remain unavailable unless the company is approved."}</p></div></div>{company.data?.reviewMessage && <p className="break-words rounded-lg border border-border/70 bg-muted/40 p-4 text-sm leading-6"><strong>Admin feedback:</strong> {company.data.reviewMessage}</p>}<Button asChild className="min-h-11"><Link href={`/fleet/companies/${membership.carrierCompanyId}`}>View application status <ArrowRight className="ml-2 h-4 w-4"/></Link></Button></CardContent></Card> : <>
        <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-label="Fleet summary">
          <Summary icon={<Users className="h-5 w-5"/>} label="Active drivers" value={driverCount} detail="Company-linked accounts"/>
          <Summary icon={<Truck className="h-5 w-5"/>} label="Vehicles" value={vehicleCount} detail={`${availableVehicleCount} approved and available`}/>
          <Summary icon={<PackageCheck className="h-5 w-5"/>} label="Next step" value="Find freight" detail="Offer, agree, then dispatch"/>
        </section>

        {(error || notice) && <p role={error ? "alert" : "status"} className={`break-words rounded-xl border px-4 py-3 text-sm leading-6 ${error ? "border-destructive/30 bg-destructive/5 text-destructive" : "border-primary/20 bg-primary/5 text-foreground"}`}>{error || notice}</p>}

        <section aria-labelledby="company-operations-heading" className="space-y-3">
          <div><h2 id="company-operations-heading" className="text-lg font-semibold tracking-tight">Company operations</h2><p className="mt-1 text-sm text-muted-foreground">Open the workflows available to your company role.</p></div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-label="Company workspaces">
          {(membership.role === "CARRIER_OWNER" || membership.role === "FLEET_MANAGER" || membership.role === "DISPATCHER") && <WorkspaceLink href={`/carrier-offers/company/${membership.carrierCompanyId}`} title="Open freight & offers" detail="See shipper loads, submit offers and negotiate rates" icon={<PackageCheck className="h-5 w-5"/>}/>}
          {canDispatch && <WorkspaceLink href={`/carrier-dispatch/company/${membership.carrierCompanyId}`} title="Dispatch board" detail="Assign a driver and approved vehicle" icon={<Truck className="h-5 w-5"/>}/>}
          {canDispatch && <WorkspaceLink href={`/carrier-companies/${membership.carrierCompanyId}/bookings`} title="Company bookings" detail="Review bookings, parties, freight, and delivery status" icon={<ClipboardList className="h-5 w-5"/>}/>}
          {canDispatch && <WorkspaceLink href={`/carrier-companies/${membership.carrierCompanyId}/trips`} title="Company trips" detail="Review trip progress, routes, stops, and history" icon={<MapPin className="h-5 w-5"/>}/>}
          {membership.role === "CARRIER_OWNER" && <WorkspaceLink href={`/carrier/${membership.carrierCompanyId}/settlements`} title="Settlements" detail="Review delivery payments and company earnings" icon={<ClipboardList className="h-5 w-5"/>}/>}
          </div>
        </section>

        <Separator className="my-1" />
        <CompanyFleetManagement
          key={`company-fleet-${companyId}`}
          companyId={companyId}
          base={base}
          companyApproved={approved}
          canManageFleet={canManageFleet}
          driverDirectory={driverList}
          vehicleDirectory={vehicleList}
        />

        {canManageAssignments && <Card className="border-border/70 shadow-sm">
          <CardHeader className="space-y-2">
            <CardTitle className="flex items-center gap-2 text-base"><ClipboardList className="h-5 w-5 text-primary" aria-hidden="true" /> Pair a driver and vehicle</CardTitle>
            <CardDescription className="leading-6">This is a regular fleet pairing, not a freight dispatch assignment. Backend validation decides eligibility. An already-assigned driver or vehicle can be reassigned, which may end the prior assignment for either selected resource.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <form key={`assignment-form-${companyId}`} onSubmit={event => void submitAssignment(event)} className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor={`assignment-driver-${companyId}`}>Driver</Label>
                <select id={`assignment-driver-${companyId}`} required value={assignmentDriverId} onChange={event => setAssignmentDriverId(event.target.value)} className="min-h-11 w-full rounded-md border border-input bg-background px-3 text-sm">
                  <option value="">Select an active driver</option>
                  {assignmentDrivers.map(driver => <option key={driver.id} value={driver.id}>{driver.fullName} · {driver.availability.toUpperCase() === "ASSIGNED" ? "Currently assigned — reassignment may end prior pairing" : driver.availability.replaceAll("_", " ")}</option>)}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor={`assignment-vehicle-${companyId}`}>Approved vehicle</Label>
                <select id={`assignment-vehicle-${companyId}`} required value={assignmentVehicleId} onChange={event => setAssignmentVehicleId(event.target.value)} className="min-h-11 w-full rounded-md border border-input bg-background px-3 text-sm">
                  <option value="">Select an approved vehicle</option>
                  {assignmentVehicles.map(vehicle => <option key={vehicle.id} value={vehicle.id}>{vehicle.plateNumber} · {vehicle.truckType} · {vehicle.availabilityStatus.toLowerCase() === "assigned" ? "Currently assigned — reassignment may end prior pairing" : vehicle.availabilityStatus.replaceAll("_", " ")}</option>)}
                </select>
              </div>
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor={`assignment-notes-${companyId}`}>Assignment notes <span className="text-muted-foreground">(optional)</span></Label>
                <textarea id={`assignment-notes-${companyId}`} name="assignmentNotes" maxLength={1000} className="min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm" />
              </div>
              <Button type="submit" disabled={busy || !assignmentDrivers.length || !assignmentVehicles.length} className="min-h-11 gap-2 sm:col-span-2"><CheckCircle2 className="h-4 w-4" />{busy ? "Assigning…" : "Create pairing"}</Button>
            </form>
            {(!assignmentDrivers.length || !assignmentVehicles.length) && <p className="rounded-lg bg-muted/40 px-4 py-3 text-sm leading-6 text-muted-foreground">No eligible active drivers or approved vehicles are currently listed. The backend remains authoritative for eligibility and concurrent assignment conflicts.</p>}
          </CardContent>
        </Card>}

        {canManageTeam && <CompanyTeam companyId={companyId}/>}
        <div className="flex flex-col gap-3 border-t border-border/70 pt-4 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-muted-foreground">Need the latest company status or fleet records?</p><Button type="button" variant="outline" className="min-h-11 w-full sm:w-auto" onClick={() => { void company.refetch(); void refreshFleet(); }} disabled={company.isFetching || drivers.isFetching || vehicles.isFetching}><RefreshCw className={`mr-2 h-4 w-4 ${company.isFetching ? "animate-spin" : ""}`}/> Refresh workspace</Button></div>
      </>}
    </main>
  );
}

function Summary({ icon, label, value, detail }: { icon: ReactNode; label: string; value: string | number; detail: string }) {
  return <Card className="border-border/70 shadow-sm"><CardContent className="flex min-h-24 items-center gap-4 p-4 sm:p-5"><div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">{icon}</div><div className="min-w-0"><p className="text-sm font-medium text-muted-foreground">{label}</p><p className="break-words text-xl font-semibold tracking-tight">{value}</p><p className="break-words text-xs leading-5 text-muted-foreground">{detail}</p></div></CardContent></Card>;
}

function WorkspaceLink({ href, title, detail, icon }: { href: string; title: string; detail: string; icon: ReactNode }) {
  return <Link href={href} className="group flex min-h-36 flex-col rounded-xl border border-border/70 bg-card p-4 transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:p-5"><span className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">{icon}</span><div className="mt-auto flex items-start justify-between gap-3 pt-4"><div className="min-w-0"><h3 className="break-words font-semibold">{title}</h3><p className="mt-1 break-words text-sm leading-5 text-muted-foreground">{detail}</p></div><ArrowRight className="mt-1 h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-1 group-hover:text-primary" aria-hidden="true"/></div></Link>;
}
