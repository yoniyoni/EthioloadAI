import { useEffect, useState, type FormEvent } from "react";
import { useQuery, useQueryClient, type UseQueryResult } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Search, Truck, Users } from "lucide-react";

type DriverStatus = "ACTIVE" | "INACTIVE" | "REMOVED";
type DriverAvailability = "AVAILABLE" | "ASSIGNED" | "ON_TRIP" | "UNAVAILABLE";
type Driver = {
  id: number;
  carrierCompanyId: number;
  driverUserId: number;
  fullName: string;
  status: DriverStatus;
  availability: DriverAvailability;
  verificationStatus: boolean;
  isActive: boolean;
  notes?: string | null;
  joinedAt?: string;
  leftAt?: string | null;
};

type Vehicle = {
  id: number;
  carrierCompanyId: number;
  plateNumber: string;
  truckType: string;
  vehicleCategory?: string | null;
  capacity: number;
  currentCity?: string | null;
  approvalStatus: string;
  availabilityStatus: string;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last: boolean;
};

type AssignmentStatus = "ACTIVE" | "ENDED" | "CANCELLED";
type Assignment = {
  id: number;
  carrierCompanyId: number;
  carrierDriverId: number;
  vehicleId: number;
  status: AssignmentStatus;
  assignedAt: string;
  endedAt?: string | null;
  notes?: string | null;
};

const pageSize = 10;

export function CompanyFleetManagement({
  companyId,
  base,
  companyApproved,
  canManageFleet,
  driverDirectory,
  vehicleDirectory,
}: {
  companyId: number;
  base: string;
  companyApproved: boolean;
  canManageFleet: boolean;
  driverDirectory: Array<{ id: number; fullName: string }>;
  vehicleDirectory: Array<{ id: number; plateNumber: string; truckType: string }>;
}) {
  const { memberships, membershipError, membershipRevalidating } = useAuth();
  const queryClient = useQueryClient();
  const membership = memberships.find(item => item.carrierCompanyId === companyId && item.status === "ACTIVE");
  const canWrite = !!membership && companyApproved && canManageFleet && !membershipError && !membershipRevalidating;
  const canManageAssignments = !!membership && companyApproved &&
    ["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"].includes(membership.role) &&
    !membershipError && !membershipRevalidating;
  const [driverStatus, setDriverStatus] = useState<DriverStatus>("ACTIVE");
  const [driverPage, setDriverPage] = useState(0);
  const [vehiclePage, setVehiclePage] = useState(0);
  const [assignmentPage, setAssignmentPage] = useState(0);
  const [driverSearch, setDriverSearch] = useState("");
  const [vehicleSearch, setVehicleSearch] = useState("");
  const [selectedDriverId, setSelectedDriverId] = useState<number | null>(null);
  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null);
  const [driverIdentifier, setDriverIdentifier] = useState("");
  const [driverNotes, setDriverNotes] = useState("");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const drivers = useQuery({
    queryKey: ["company-fleet-drivers", companyId, driverStatus, driverPage, pageSize],
    queryFn: async () => {
      if (!membership) throw new Error("An active company membership is required.");
      const page = await api.get<Page<Driver>>(
        `${base}/drivers?status=${driverStatus}&page=${driverPage}&size=${pageSize}`,
      );
      if (!page || !Array.isArray(page.content) || page.content.some(driver => driver.carrierCompanyId !== companyId)) {
        throw new Error("The driver response did not match the selected company.");
      }
      return page;
    },
    enabled: !!membership && companyApproved && !membershipError && !membershipRevalidating,
  });

  useEffect(() => {
    if (!drivers.isSuccess || drivers.isFetching || drivers.isRefetchError || !drivers.data) return;
    const lastValidPage = Math.max(0, drivers.data.totalPages - 1);
    if (driverPage > lastValidPage) setDriverPage(lastValidPage);
  }, [driverPage, drivers.data, drivers.isFetching, drivers.isRefetchError, drivers.isSuccess]);

  const vehicles = useQuery({
    queryKey: ["company-fleet-vehicles", companyId, vehiclePage, pageSize],
    queryFn: async () => {
      if (!membership) throw new Error("An active company membership is required.");
      const page = await api.get<Page<Vehicle>>(`${base}/vehicles?page=${vehiclePage}&size=${pageSize}`);
      if (!page || !Array.isArray(page.content) || page.content.some(vehicle => vehicle.carrierCompanyId !== companyId)) {
        throw new Error("The vehicle response did not match the selected company.");
      }
      return page;
    },
    enabled: !!membership && companyApproved && !membershipError && !membershipRevalidating,
  });

  const selectedDriver = useQuery({
    queryKey: ["company-fleet-driver", companyId, selectedDriverId],
    queryFn: async () => {
      if (!membership || selectedDriverId === null) throw new Error("An active company membership is required.");
      const driver = await api.get<Driver>(`${base}/drivers/${selectedDriverId}`);
      if (driver.carrierCompanyId !== companyId) throw new Error("The driver response did not match the selected company.");
      return driver;
    },
    enabled: !!membership && companyApproved && selectedDriverId !== null && !membershipError && !membershipRevalidating,
  });

  const selectedVehicle = useQuery({
    queryKey: ["company-fleet-vehicle", companyId, selectedVehicleId],
    queryFn: async () => {
      if (!membership || selectedVehicleId === null) throw new Error("An active company membership is required.");
      const vehicle = await api.get<Vehicle>(`${base}/vehicles/${selectedVehicleId}`);
      if (vehicle.carrierCompanyId !== companyId) throw new Error("The vehicle response did not match the selected company.");
      return vehicle;
    },
    enabled: !!membership && companyApproved && selectedVehicleId !== null && !membershipError && !membershipRevalidating,
  });

  const driverAssignments = useQuery({
    queryKey: ["company-driver-assignment-history", companyId, selectedDriverId],
    queryFn: async () => {
      if (!membership || selectedDriverId === null) throw new Error("An active company membership is required.");
      const result = await api.get<Assignment[]>(`${base}/drivers/${selectedDriverId}/assignments`);
      if (!Array.isArray(result) || result.some(assignment => assignment.carrierCompanyId !== companyId)) {
        throw new Error("The assignment response did not match the selected company.");
      }
      return result;
    },
    enabled: !!membership && companyApproved && selectedDriverId !== null && !membershipError && !membershipRevalidating,
  });

  const vehicleAssignments = useQuery({
    queryKey: ["company-vehicle-assignment-history", companyId, selectedVehicleId],
    queryFn: async () => {
      if (!membership || selectedVehicleId === null) throw new Error("An active company membership is required.");
      const result = await api.get<Assignment[]>(`${base}/vehicles/${selectedVehicleId}/assignments`);
      if (!Array.isArray(result) || result.some(assignment => assignment.carrierCompanyId !== companyId)) {
        throw new Error("The assignment response did not match the selected company.");
      }
      return result;
    },
    enabled: !!membership && companyApproved && selectedVehicleId !== null && !membershipError && !membershipRevalidating,
  });

  const companyAssignments = useQuery({
    queryKey: ["company-assignment-history", companyId, assignmentPage, pageSize],
    queryFn: async () => {
      if (!membership) throw new Error("An active company membership is required.");
      const result = await api.get<Page<Assignment>>(`${base}/assignments?page=${assignmentPage}&size=${pageSize}`);
      if (!result || !Array.isArray(result.content) ||
          result.content.some(assignment => assignment.carrierCompanyId !== companyId)) {
        throw new Error("The assignment response did not match the selected company.");
      }
      return result;
    },
    enabled: !!membership && companyApproved && !membershipError && !membershipRevalidating,
  });

  useEffect(() => {
    if (!companyAssignments.isSuccess || companyAssignments.isFetching ||
        companyAssignments.isRefetchError || !companyAssignments.data) return;
    const lastValidPage = Math.max(0, companyAssignments.data.totalPages - 1);
    if (assignmentPage > lastValidPage) setAssignmentPage(lastValidPage);
  }, [assignmentPage, companyAssignments.data, companyAssignments.isFetching,
    companyAssignments.isRefetchError, companyAssignments.isSuccess]);

  async function refreshDrivers(driverId?: number) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["company-fleet-drivers", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-driver", companyId, ...(driverId === undefined ? [] : [driverId])] }),
      queryClient.invalidateQueries({ queryKey: ["company-driver-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-drivers", companyId] }),
    ]);
  }

  async function refreshVehicles(vehicleId?: number) {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["company-fleet-vehicles", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-vehicle", companyId, ...(vehicleId === undefined ? [] : [vehicleId])] }),
      queryClient.invalidateQueries({ queryKey: ["company-vehicle-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-vehicles", companyId] }),
    ]);
  }

  async function refreshAssignments() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["company-driver-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-vehicle-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-assignment-history", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-assignments", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-driver", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-vehicle", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-drivers", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-fleet-vehicles", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-drivers", companyId] }),
      queryClient.invalidateQueries({ queryKey: ["company-vehicles", companyId] }),
    ]);
  }

  async function endAssignment(assignment: Assignment) {
    if (!canManageAssignments || assignment.status !== "ACTIVE") return;
    const notes = window.prompt("Optional reason for ending this assignment:", "");
    if (notes === null) return;
    if (notes.length > 1000) {
      setError("Ending notes must be 1,000 characters or fewer.");
      setNotice("");
      return;
    }
    if (!window.confirm("End this active driver–vehicle assignment? The assignment will be recorded as ended, and the backend may return the driver and vehicle to available status when their current states allow it.")) return;
    await runMutation(
      `end-assignment-${assignment.id}`,
      async () => {
        const result = await api.post<Assignment>(
          `${base}/assignments/${assignment.id}/end`,
          notes.trim() ? { notes: notes.trim() } : {},
        );
        if (result.carrierCompanyId !== companyId) {
          throw new Error("The assignment response did not match the selected company.");
        }
      },
      "Assignment ended.",
      refreshAssignments,
      canManageAssignments,
    );
  }

  async function runMutation<T>(
    key: string,
    action: () => Promise<T>,
    success: string,
    refresh: () => Promise<void>,
    authorized = canWrite,
  ): Promise<boolean> {
    if (!authorized) {
      setError("Only authorized members of an approved company can manage this operation.");
      return false;
    }
    setBusy(key);
    setError("");
    setNotice("");
    try {
      await action();
      await refresh();
      setNotice(success);
      return true;
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to save this company fleet change.");
      return false;
    } finally {
      setBusy("");
    }
  }

  async function addDriver(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const identifier = driverIdentifier.trim();
    if (!identifier || identifier.length > 255 || driverNotes.length > 1000) {
      setNotice("");
      setError("Enter a registered driver email or phone and keep notes within 1,000 characters.");
      return;
    }
    const saved = await runMutation(
      "add-driver",
      async () => {
        const result = await api.post<Driver>(`${base}/drivers`, {
          driverIdentifier: identifier,
          notes: driverNotes.trim() || undefined,
        });
        if (result.carrierCompanyId !== companyId) throw new Error("The driver response did not match the selected company.");
      },
      "Driver added to the company fleet.",
      () => refreshDrivers(),
    );
    if (saved) {
      setDriverIdentifier("");
      setDriverNotes("");
    }
  }

  async function updateDriver(event: FormEvent<HTMLFormElement>, driver: Driver) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const notes = String(form.get("notes") ?? "");
    const availabilityValue = String(form.get("availability") ?? "");
    if (notes.length > 1000) {
      setNotice("");
      setError("Driver notes must be 1,000 characters or fewer.");
      return;
    }
    const payload: { notes: string; availability?: "AVAILABLE" | "UNAVAILABLE" } = { notes };
    if (availabilityValue === "AVAILABLE" || availabilityValue === "UNAVAILABLE") {
      payload.availability = availabilityValue;
    } else if (availabilityValue) {
      setNotice("");
      setError("Choose a supported manual availability.");
      return;
    }
    await runMutation(
      `update-driver-${driver.id}`,
      async () => {
        const result = await api.patch<Driver>(`${base}/drivers/${driver.id}`, payload);
        if (result.carrierCompanyId !== companyId) throw new Error("The driver response did not match the selected company.");
      },
      "Driver details updated.",
      () => refreshDrivers(driver.id),
    );
  }

  async function changeDriverStatus(driver: Driver, action: "activate" | "deactivate" | "remove" | "readd") {
    const message = action === "deactivate"
      ? `Deactivate ${driver.fullName}? The server will end any active vehicle assignment.`
      : action === "remove"
        ? `Remove ${driver.fullName} from this company? The server will end any active vehicle assignment; the driver's account and history remain.`
        : action === "readd"
          ? `Re-add ${driver.fullName} to this company fleet?`
          : `Reactivate ${driver.fullName}?`;
    if (!window.confirm(message)) return;
    await runMutation(
      `${action}-driver-${driver.id}`,
      async () => {
        const result = action === "readd"
          ? await api.post<Driver>(`${base}/drivers`, { driverUserId: driver.driverUserId })
          : action === "remove"
            ? await api.del<Driver>(`${base}/drivers/${driver.id}`)
            : await api.post<Driver>(`${base}/drivers/${driver.id}/${action}`);
        if (result.carrierCompanyId !== companyId) throw new Error("The driver response did not match the selected company.");
      },
      action === "deactivate" ? "Driver deactivated."
        : action === "remove" ? "Driver removed from the company fleet."
          : action === "readd" ? "Driver re-added to the company fleet."
            : "Driver reactivated.",
      () => refreshDrivers(driver.id),
    );
  }

  async function registerVehicle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const truckType = String(form.get("truckType") ?? "").trim();
    const plateNumber = String(form.get("plateNumber") ?? "").trim().toUpperCase();
    const capacity = Number(form.get("capacity"));
    const vehicleCategory = String(form.get("vehicleCategory") ?? "");
    const currentCity = String(form.get("currentCity") ?? "").trim();
    if (!truckType || truckType.length > 255 || !plateNumber || plateNumber.length > 255 ||
        !Number.isFinite(capacity) || capacity < 0.1 || !["HEAVY", "LIGHT"].includes(vehicleCategory) ||
        currentCity.length > 255) {
      setNotice("");
      setError("Check the required vehicle fields, category, and capacity (at least 0.1 tons).");
      return;
    }
    const saved = await runMutation(
      "register-vehicle",
      async () => {
        const result = await api.post<Vehicle>(`${base}/vehicles`, {
          truckType,
          plateNumber,
          capacity,
          vehicleCategory,
          currentCity: currentCity || undefined,
        });
        if (result.carrierCompanyId !== companyId) throw new Error("The vehicle response did not match the selected company.");
      },
      "Vehicle registered. It requires approval before operational use.",
      () => refreshVehicles(),
    );
    if (saved) formElement.reset();
  }

  async function updateVehicle(event: FormEvent<HTMLFormElement>, vehicle: Vehicle) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const truckType = String(form.get("truckType") ?? "").trim();
    const capacity = Number(form.get("capacity"));
    const vehicleCategory = String(form.get("vehicleCategory") ?? "");
    const currentCity = String(form.get("currentCity") ?? "").trim();
    if (!truckType || truckType.length > 255 || !Number.isFinite(capacity) || capacity < 0.1 ||
        (vehicleCategory && !["HEAVY", "LIGHT"].includes(vehicleCategory)) || currentCity.length > 255) {
      setNotice("");
      setError("Check the vehicle type, category, capacity, and current city.");
      return;
    }
    await runMutation(
      `update-vehicle-${vehicle.id}`,
      async () => {
        const result = await api.patch<Vehicle>(`${base}/vehicles/${vehicle.id}`, {
          truckType,
          capacity,
          ...(vehicleCategory ? { vehicleCategory } : {}),
          currentCity,
        });
        if (result.carrierCompanyId !== companyId) throw new Error("The vehicle response did not match the selected company.");
      },
      "Vehicle details updated.",
      () => refreshVehicles(vehicle.id),
    );
  }

  async function setVehicleAvailability(vehicle: Vehicle, availability: "AVAILABLE" | "MAINTENANCE" | "UNAVAILABLE") {
    if (["ASSIGNED", "ON_TRIP"].includes(vehicle.availabilityStatus.toUpperCase())) {
      setError("Availability for an assigned or on-trip vehicle is system-managed.");
      return;
    }
    await runMutation(
      `availability-vehicle-${vehicle.id}`,
      async () => {
        const result = await api.patch<Vehicle>(
          `${base}/vehicles/${vehicle.id}/availability?availability=${availability}`,
          {},
        );
        if (result.carrierCompanyId !== companyId) throw new Error("The vehicle response did not match the selected company.");
      },
      "Vehicle availability updated.",
      () => refreshVehicles(vehicle.id),
    );
  }

  const driverRows = (drivers.data?.content ?? []).filter(driver =>
    driver.fullName.toLowerCase().includes(driverSearch.trim().toLowerCase()),
  );
  const vehicleRows = (vehicles.data?.content ?? []).filter(vehicle =>
    `${vehicle.plateNumber} ${vehicle.truckType} ${vehicle.currentCity ?? ""}`
      .toLowerCase().includes(vehicleSearch.trim().toLowerCase()),
  );

  return (
    <section className="space-y-5" aria-label="Company driver and vehicle management">
      {(error || notice) && (
        <p role={error ? "alert" : "status"} className={`break-words rounded-xl border px-4 py-3 text-sm leading-6 ${error ? "border-destructive/30 bg-destructive/5 text-destructive" : "border-primary/20 bg-primary/5 text-foreground"}`}>
          {error || notice}
        </p>
      )}

      <div className="grid gap-5 lg:grid-cols-2">
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="space-y-2">
            <CardTitle className="flex items-center gap-2 text-base"><Users className="h-5 w-5 text-primary" aria-hidden="true" /> Company drivers</CardTitle>
            <CardDescription>Link existing, active, verified driver accounts. Contact details are not displayed in this directory.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {canWrite && (
              <form onSubmit={event => void addDriver(event)} className="space-y-3 rounded-xl border border-border/70 bg-muted/30 p-4">
                <div className="space-y-2">
                  <Label htmlFor={`company-${companyId}-driver-identifier`}>Registered driver email or phone</Label>
                  <Input id={`company-${companyId}-driver-identifier`} autoComplete="off" maxLength={255} required value={driverIdentifier} onChange={event => setDriverIdentifier(event.target.value)} placeholder="Driver email or phone" />
                </div>
                <div className="space-y-2">
                  <Label htmlFor={`company-${companyId}-driver-notes`}>Company notes <span className="text-muted-foreground">(optional)</span></Label>
                  <textarea id={`company-${companyId}-driver-notes`} name="driverNotes" maxLength={1000} value={driverNotes} onChange={event => setDriverNotes(event.target.value)} className="min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm" />
                </div>
                <Button type="submit" disabled={busy !== ""} className="min-h-11"><Users className="mr-2 h-4 w-4" />{busy === "add-driver" ? "Adding…" : "Add driver"}</Button>
              </form>
            )}

            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_170px]">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <Input aria-label="Search current driver page" className="pl-9" value={driverSearch} onChange={event => setDriverSearch(event.target.value)} placeholder="Search this page by name" />
              </div>
              <select aria-label="Filter drivers by company status" className="min-h-10 rounded-md border border-input bg-background px-3 text-sm" value={driverStatus} onChange={event => { setDriverStatus(event.target.value as DriverStatus); setDriverPage(0); }}>
                <option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="REMOVED">Removed</option>
              </select>
            </div>
            <p className="text-xs leading-5 text-muted-foreground">Search filters the currently loaded page; status and pagination are provided by the company fleet API.</p>
            {drivers.isError ? <div role="alert" className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4"><p className="text-sm text-destructive">{drivers.error.message}</p><Button type="button" variant="outline" className="min-h-10" onClick={() => void drivers.refetch()}>Retry drivers</Button></div>
              : drivers.isLoading ? <div aria-label="Loading drivers" className="space-y-3">{[0, 1, 2].map(item => <div key={item} className="h-20 animate-pulse rounded-lg border bg-muted/30" />)}</div>
                : driverRows.length === 0 ? <EmptyState title={drivers.data?.content.length ? "No drivers match this search" : `No ${driverStatus.toLowerCase()} drivers`} detail={driverStatus === "ACTIVE" ? "Add an existing verified driver account to this company." : "Drivers in this status will appear here when available."} />
                  : <div className="divide-y divide-border/70 overflow-hidden rounded-xl border border-border/70">{driverRows.map(driver => (
                    <article key={driver.id} className="space-y-3 p-4">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="min-w-0">
                          <h3 className="break-words font-medium">{driver.fullName || "Company driver"}</h3>
                          <p className="mt-1 text-sm text-muted-foreground">KYC {driver.verificationStatus ? "approved" : "not approved"} · Account {driver.isActive ? "active" : "inactive"}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Badge variant={driver.status === "ACTIVE" ? "default" : "secondary"}>{driver.status}</Badge>
                          <Badge variant="outline">{driver.availability.replaceAll("_", " ")}</Badge>
                        </div>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <Button type="button" size="sm" variant="outline" className="min-h-10" onClick={() => { setSelectedDriverId(selectedDriverId === driver.id ? null : driver.id); setSelectedVehicleId(null); }}>
                          {selectedDriverId === driver.id ? "Hide details" : "Details"}
                        </Button>
                        {canWrite && driver.status === "ACTIVE" && <Button type="button" size="sm" variant="outline" className="min-h-10" disabled={busy !== ""} onClick={() => void changeDriverStatus(driver, "deactivate")}>Deactivate</Button>}
                        {canWrite && driver.status === "INACTIVE" && <Button type="button" size="sm" variant="outline" className="min-h-10" disabled={busy !== ""} onClick={() => void changeDriverStatus(driver, "activate")}>Activate</Button>}
                        {canWrite && driver.status === "REMOVED" && <Button type="button" size="sm" variant="outline" className="min-h-10" disabled={busy !== ""} onClick={() => void changeDriverStatus(driver, "readd")}>Re-add</Button>}
                        {canWrite && driver.status !== "REMOVED" && <Button type="button" size="sm" variant="destructive" className="min-h-10" disabled={busy !== ""} onClick={() => void changeDriverStatus(driver, "remove")}>Remove</Button>}
                      </div>
                    </article>
                  ))}</div>}
            <Pagination page={drivers.data} current={driverPage} setPage={setDriverPage} label="drivers" />
            {selectedDriverId !== null && (
              <DriverDetails
                query={selectedDriver}
                canWrite={canWrite}
                busy={busy}
                update={updateDriver}
                assignments={driverAssignments}
                vehicleDirectory={vehicleDirectory}
                canManageAssignments={canManageAssignments}
                endAssignment={endAssignment}
              />
            )}
          </CardContent>
        </Card>

        <Card className="border-border/70 shadow-sm">
          <CardHeader className="space-y-2">
            <CardTitle className="flex items-center gap-2 text-base"><Truck className="h-5 w-5 text-primary" aria-hidden="true" /> Company vehicles</CardTitle>
            <CardDescription>Register and maintain company vehicles. Approval and operational availability are separate statuses.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {canWrite && (
              <form onSubmit={event => void registerVehicle(event)} className="grid gap-3 rounded-xl border border-border/70 bg-muted/30 p-4 sm:grid-cols-2">
                <div className="space-y-2"><Label htmlFor={`company-${companyId}-plate`}>Plate number</Label><Input id={`company-${companyId}-plate`} name="plateNumber" autoComplete="off" maxLength={255} required /></div>
                <div className="space-y-2"><Label htmlFor={`company-${companyId}-truck-type`}>Vehicle type</Label><Input id={`company-${companyId}-truck-type`} name="truckType" maxLength={255} required placeholder="Flatbed, tanker…" /></div>
                <div className="space-y-2"><Label htmlFor={`company-${companyId}-capacity`}>Capacity (tons)</Label><Input id={`company-${companyId}-capacity`} name="capacity" type="number" min="0.1" step="0.1" required /></div>
                <div className="space-y-2"><Label htmlFor={`company-${companyId}-category`}>Category</Label><select id={`company-${companyId}-category`} name="vehicleCategory" required className="min-h-10 w-full rounded-md border border-input bg-background px-3 text-sm"><option value="">Select category</option><option value="HEAVY">Heavy vehicle</option><option value="LIGHT">Light vehicle</option></select></div>
                <div className="space-y-2 sm:col-span-2"><Label htmlFor={`company-${companyId}-city`}>Current city <span className="text-muted-foreground">(optional)</span></Label><Input id={`company-${companyId}-city`} name="currentCity" maxLength={255} /></div>
                <Button type="submit" disabled={busy !== ""} className="min-h-11 sm:col-span-2"><Truck className="mr-2 h-4 w-4" />{busy === "register-vehicle" ? "Registering…" : "Register vehicle"}</Button>
              </form>
            )}

            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
              <Input aria-label="Search current vehicle page" className="pl-9" value={vehicleSearch} onChange={event => setVehicleSearch(event.target.value)} placeholder="Search this page by plate, type, or city" />
            </div>
            <p className="text-xs leading-5 text-muted-foreground">Search filters the currently loaded page; pagination is provided by the company fleet API.</p>
            {vehicles.isError ? <div role="alert" className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4"><p className="text-sm text-destructive">{vehicles.error.message}</p><Button type="button" variant="outline" className="min-h-10" onClick={() => void vehicles.refetch()}>Retry vehicles</Button></div>
              : vehicles.isLoading ? <div aria-label="Loading vehicles" className="space-y-3">{[0, 1, 2].map(item => <div key={item} className="h-20 animate-pulse rounded-lg border bg-muted/30" />)}</div>
                : vehicleRows.length === 0 ? <EmptyState title={vehicles.data?.content.length ? "No vehicles match this search" : "No vehicles registered"} detail="Approved vehicles can be considered for operations; registration alone does not grant approval or prove dispatch eligibility." />
                  : <div className="divide-y divide-border/70 overflow-hidden rounded-xl border border-border/70">{vehicleRows.map(vehicle => (
                    <article key={vehicle.id} className="space-y-3 p-4">
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                        <div className="min-w-0">
                          <h3 className="break-words font-medium">{vehicle.plateNumber} <span className="font-normal text-muted-foreground">· {vehicle.truckType}</span></h3>
                          <p className="mt-1 break-words text-sm text-muted-foreground">{vehicle.capacity} tons{vehicle.vehicleCategory ? ` · ${vehicle.vehicleCategory}` : ""}{vehicle.currentCity ? ` · ${vehicle.currentCity}` : ""}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Badge variant={vehicle.approvalStatus === "APPROVED" ? "default" : "outline"}>{vehicle.approvalStatus.replaceAll("_", " ")}</Badge>
                          <Badge variant="secondary">{statusLabel(vehicle.availabilityStatus)}</Badge>
                        </div>
                      </div>
                      <Button type="button" size="sm" variant="outline" className="min-h-10" onClick={() => { setSelectedVehicleId(selectedVehicleId === vehicle.id ? null : vehicle.id); setSelectedDriverId(null); }}>
                        {selectedVehicleId === vehicle.id ? "Hide details" : "Details"}
                      </Button>
                    </article>
                  ))}</div>}
            <Pagination page={vehicles.data} current={vehiclePage} setPage={setVehiclePage} label="vehicles" />
            {selectedVehicleId !== null && (
              <VehicleDetails
                query={selectedVehicle}
                canWrite={canWrite}
                busy={busy}
                update={updateVehicle}
                setAvailability={setVehicleAvailability}
                assignments={vehicleAssignments}
                driverDirectory={driverDirectory}
                canManageAssignments={canManageAssignments}
                endAssignment={endAssignment}
              />
            )}
          </CardContent>
        </Card>
      </div>
      <Card className="border-border/70 shadow-sm">
        <CardHeader>
          <CardTitle className="text-base">Company assignment history</CardTitle>
          <CardDescription>Regular fleet driver–vehicle pairings are separate from freight dispatch eligibility.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {companyAssignments.isError ? (
            <div role="alert" className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
              <p className="text-sm text-destructive">{companyAssignments.error.message}</p>
              <Button type="button" variant="outline" className="min-h-10" onClick={() => void companyAssignments.refetch()}>Retry assignment history</Button>
            </div>
          ) : companyAssignments.isLoading ? (
            <div aria-label="Loading assignment history" className="space-y-3">
              {[0, 1].map(item => <div key={item} className="h-20 animate-pulse rounded-lg border bg-muted/30" />)}
            </div>
          ) : !companyAssignments.data?.content.length ? (
            <EmptyState title="No assignment history" detail="Company driver–vehicle assignments will appear here." />
          ) : (
            <div className="divide-y divide-border/70 overflow-hidden rounded-xl border border-border/70">
              {companyAssignments.data.content.map(assignment => (
                <AssignmentRow
                  key={assignment.id}
                  assignment={assignment}
                  driverName={driverDirectory.find(driver => driver.id === assignment.carrierDriverId)?.fullName}
                  vehicleName={vehicleName(vehicleDirectory.find(vehicle => vehicle.id === assignment.vehicleId))}
                  canEnd={canManageAssignments}
                  busy={busy !== ""}
                  onEnd={endAssignment}
                />
              ))}
            </div>
          )}
          <Pagination page={companyAssignments.data} current={assignmentPage} setPage={setAssignmentPage} label="company assignment history" />
        </CardContent>
      </Card>
    </section>
  );
}

function DriverDetails({
  query,
  canWrite,
  busy,
  update,
  assignments,
  vehicleDirectory,
  canManageAssignments,
  endAssignment,
}: {
  query: UseQueryResult<Driver, Error>;
  canWrite: boolean;
  busy: string;
  update: (event: FormEvent<HTMLFormElement>, driver: Driver) => Promise<void>;
  assignments: UseQueryResult<Assignment[], Error>;
  vehicleDirectory: Array<{ id: number; plateNumber: string; truckType: string }>;
  canManageAssignments: boolean;
  endAssignment: (assignment: Assignment) => Promise<void>;
}) {
  const driver = query.data;
  return (
    <Card className="bg-muted/20">
      <CardHeader><CardTitle className="text-base">Driver details</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        {query.isLoading ? <p className="text-sm text-muted-foreground">Loading driver details…</p>
          : query.isError ? <div className="space-y-2"><p role="alert" className="text-sm text-destructive">{query.error.message}</p><Button type="button" variant="outline" className="min-h-10" onClick={() => void query.refetch()}>Retry details</Button></div>
            : driver ? <>
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <Detail label="Company status" value={driver.status} />
                <Detail label="Availability" value={driver.availability.replaceAll("_", " ")} />
                <Detail label="Driver verification" value={driver.verificationStatus ? "Approved" : "Not approved"} />
                <Detail label="Account status" value={driver.isActive ? "Active" : "Inactive"} />
                <Detail label="Joined" value={dateLabel(driver.joinedAt)} />
                {driver.leftAt && <Detail label="Left company" value={dateLabel(driver.leftAt)} />}
                <Detail label="Company notes" value={driver.notes?.trim() || "None"} />
              </dl>
              <AssignmentHistory
                query={assignments}
                driverName={driver.fullName}
                vehicleDirectory={vehicleDirectory}
                canEnd={canManageAssignments}
                busy={busy !== ""}
                onEnd={endAssignment}
              />
              {canWrite && driver.status === "ACTIVE" && (
                <form key={driver.id} onSubmit={event => void update(event, driver)} className="space-y-3 border-t border-border/70 pt-4">
                  <div className="space-y-2"><Label htmlFor={`driver-${driver.id}-notes`}>Company notes</Label><textarea id={`driver-${driver.id}-notes`} name="notes" maxLength={1000} defaultValue={driver.notes ?? ""} className="min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm" /></div>
                  <div className="space-y-2"><Label htmlFor={`driver-${driver.id}-availability`}>Manual availability</Label>
                    <select id={`driver-${driver.id}-availability`} name="availability" defaultValue={["ASSIGNED", "ON_TRIP"].includes(driver.availability) ? "" : driver.availability} disabled={["ASSIGNED", "ON_TRIP"].includes(driver.availability)} className="min-h-10 w-full rounded-md border border-input bg-background px-3 text-sm">
                      <option value="">System-managed — keep current value</option><option value="AVAILABLE">Available</option><option value="UNAVAILABLE">Unavailable</option>
                    </select>
                    {["ASSIGNED", "ON_TRIP"].includes(driver.availability) && <p className="text-xs text-muted-foreground">Availability is system-managed while assigned or on a trip.</p>}
                  </div>
                  <Button type="submit" disabled={busy !== ""} className="min-h-10">{busy === `update-driver-${driver.id}` ? "Saving…" : "Save driver details"}</Button>
                </form>
              )}
            </> : null}
      </CardContent>
    </Card>
  );
}

function VehicleDetails({
  query,
  canWrite,
  busy,
  update,
  setAvailability,
  assignments,
  driverDirectory,
  canManageAssignments,
  endAssignment,
}: {
  query: UseQueryResult<Vehicle, Error>;
  canWrite: boolean;
  busy: string;
  update: (event: FormEvent<HTMLFormElement>, vehicle: Vehicle) => Promise<void>;
  setAvailability: (vehicle: Vehicle, availability: "AVAILABLE" | "MAINTENANCE" | "UNAVAILABLE") => Promise<void>;
  assignments: UseQueryResult<Assignment[], Error>;
  driverDirectory: Array<{ id: number; fullName: string }>;
  canManageAssignments: boolean;
  endAssignment: (assignment: Assignment) => Promise<void>;
}) {
  const vehicle = query.data;
  return (
    <Card className="bg-muted/20">
      <CardHeader><CardTitle className="text-base">Vehicle details</CardTitle><CardDescription>Approval status is assigned by the review process. Plate edits and vehicle deletion are not supported by the current company fleet API.</CardDescription></CardHeader>
      <CardContent className="space-y-4">
        {query.isLoading ? <p className="text-sm text-muted-foreground">Loading vehicle details…</p>
          : query.isError ? <div className="space-y-2"><p role="alert" className="text-sm text-destructive">{query.error.message}</p><Button type="button" variant="outline" className="min-h-10" onClick={() => void query.refetch()}>Retry details</Button></div>
            : vehicle ? <>
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <Detail label="Plate" value={vehicle.plateNumber} />
                <Detail label="Vehicle type" value={vehicle.truckType} />
                <Detail label="Capacity" value={`${vehicle.capacity} tons`} />
                <Detail label="Category" value={vehicle.vehicleCategory || "Not specified"} />
                <Detail label="Current city" value={vehicle.currentCity || "Not specified"} />
                <Detail label="Approval" value={vehicle.approvalStatus.replaceAll("_", " ")} />
                <Detail label="Availability" value={statusLabel(vehicle.availabilityStatus)} />
              </dl>
              <AssignmentHistory
                query={assignments}
                vehicleName={vehicleName(vehicle)}
                driverDirectory={driverDirectory}
                canEnd={canManageAssignments}
                busy={busy !== ""}
                onEnd={endAssignment}
              />
              {canWrite && (
                <>
                  <form key={vehicle.id} onSubmit={event => void update(event, vehicle)} className="grid gap-3 border-t border-border/70 pt-4 sm:grid-cols-2">
                    <div className="space-y-2"><Label htmlFor={`vehicle-${vehicle.id}-type`}>Vehicle type</Label><Input id={`vehicle-${vehicle.id}-type`} name="truckType" maxLength={255} required defaultValue={vehicle.truckType} /></div>
                    <div className="space-y-2"><Label htmlFor={`vehicle-${vehicle.id}-capacity`}>Capacity (tons)</Label><Input id={`vehicle-${vehicle.id}-capacity`} name="capacity" type="number" min="0.1" step="0.1" required defaultValue={vehicle.capacity} /></div>
                    <div className="space-y-2"><Label htmlFor={`vehicle-${vehicle.id}-category`}>Category</Label><select id={`vehicle-${vehicle.id}-category`} name="vehicleCategory" defaultValue={vehicle.vehicleCategory ?? ""} className="min-h-10 w-full rounded-md border border-input bg-background px-3 text-sm"><option value="">Keep current</option><option value="HEAVY">Heavy vehicle</option><option value="LIGHT">Light vehicle</option></select></div>
                    <div className="space-y-2"><Label htmlFor={`vehicle-${vehicle.id}-city`}>Current city</Label><Input id={`vehicle-${vehicle.id}-city`} name="currentCity" maxLength={255} defaultValue={vehicle.currentCity ?? ""} /></div>
                    <Button type="submit" disabled={busy !== ""} className="min-h-10 sm:col-span-2">{busy === `update-vehicle-${vehicle.id}` ? "Saving…" : "Save vehicle details"}</Button>
                  </form>
                  <div className="space-y-2 border-t border-border/70 pt-4">
                    <Label htmlFor={`vehicle-${vehicle.id}-availability`}>Manual availability</Label>
                    <select
                      id={`vehicle-${vehicle.id}-availability`}
                      value={vehicle.availabilityStatus.toUpperCase()}
                      disabled={busy !== "" || ["ASSIGNED", "ON_TRIP"].includes(vehicle.availabilityStatus.toUpperCase())}
                      onChange={event => {
                        const value = event.target.value;
                        if (["AVAILABLE", "MAINTENANCE", "UNAVAILABLE"].includes(value)) {
                          void setAvailability(vehicle, value as "AVAILABLE" | "MAINTENANCE" | "UNAVAILABLE");
                        }
                      }}
                      className="min-h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                    >
                      <option value="AVAILABLE">Available</option><option value="ASSIGNED">Assigned</option><option value="ON_TRIP">On trip</option><option value="MAINTENANCE">Maintenance</option><option value="UNAVAILABLE">Unavailable</option>
                    </select>
                    {["ASSIGNED", "ON_TRIP"].includes(vehicle.availabilityStatus.toUpperCase())
                      ? <p className="text-xs text-muted-foreground">Availability is system-managed while assigned or on a trip.</p>
                      : <p className="text-xs text-muted-foreground">Only manual availability states are editable; assignment and trip states remain system-managed.</p>}
                  </div>
                </>
              )}
            </> : null}
      </CardContent>
    </Card>
  );
}

function AssignmentHistory({
  query,
  driverName,
  vehicleName: selectedVehicleName,
  driverDirectory,
  vehicleDirectory,
  canEnd,
  busy,
  onEnd,
}: {
  query: UseQueryResult<Assignment[], Error>;
  driverName?: string;
  vehicleName?: string;
  driverDirectory?: Array<{ id: number; fullName: string }>;
  vehicleDirectory?: Array<{ id: number; plateNumber: string; truckType: string }>;
  canEnd: boolean;
  busy: boolean;
  onEnd: (assignment: Assignment) => Promise<void>;
}) {
  const active = query.data?.filter(assignment => assignment.status === "ACTIVE") ?? [];
  const history = query.data?.filter(assignment => assignment.status !== "ACTIVE") ?? [];

  function renderRows(assignments: Assignment[]) {
    return assignments.map(assignment => (
      <AssignmentRow
        key={assignment.id}
        assignment={assignment}
        driverName={driverName ?? driverDirectory?.find(driver => driver.id === assignment.carrierDriverId)?.fullName}
        vehicleName={selectedVehicleName ?? vehicleName(vehicleDirectory?.find(vehicle => vehicle.id === assignment.vehicleId))}
        canEnd={canEnd}
        busy={busy}
        onEnd={onEnd}
      />
    ));
  }

  return (
    <section className="space-y-3 border-t border-border/70 pt-4" aria-label="Driver and vehicle assignment history">
      <h3 className="font-medium">Current assignment</h3>
      {query.isLoading ? <p className="text-sm text-muted-foreground">Loading assignment information…</p>
        : query.isError ? <div className="space-y-2"><p role="alert" className="text-sm text-destructive">{query.error.message}</p><Button type="button" variant="outline" className="min-h-10" onClick={() => void query.refetch()}>Retry assignment history</Button></div>
          : active.length ? <div className="divide-y divide-border/70 overflow-hidden rounded-xl border border-border/70">{renderRows(active)}</div>
            : <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">No active vehicle assignment.</p>}
      {!query.isLoading && !query.isError && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium">Previous assignments</h4>
          {history.length ? <div className="divide-y divide-border/70 overflow-hidden rounded-xl border border-border/70">{renderRows(history)}</div>
            : <p className="text-sm text-muted-foreground">No previous assignments.</p>}
        </div>
      )}
    </section>
  );
}

function AssignmentRow({
  assignment,
  driverName,
  vehicleName: assignedVehicle,
  canEnd,
  busy,
  onEnd,
}: {
  assignment: Assignment;
  driverName?: string;
  vehicleName?: string;
  canEnd: boolean;
  busy: boolean;
  onEnd: (assignment: Assignment) => Promise<void>;
}) {
  return (
    <article className="space-y-3 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Badge variant={assignment.status === "ACTIVE" ? "default" : "secondary"}>{assignment.status}</Badge>
        <span className="text-xs text-muted-foreground">Assignment #{assignment.id}</span>
      </div>
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <Detail label="Driver" value={driverName ?? `Driver #${assignment.carrierDriverId}`} />
        <Detail label="Vehicle" value={assignedVehicle ?? `Vehicle #${assignment.vehicleId}`} />
        <Detail label="Assigned" value={dateTimeLabel(assignment.assignedAt)} />
        {assignment.endedAt && <Detail label="Ended" value={dateTimeLabel(assignment.endedAt)} />}
        <Detail label="Notes" value={assignment.notes?.trim() || "None"} />
      </dl>
      {canEnd && assignment.status === "ACTIVE" && (
        <Button type="button" variant="destructive" className="min-h-10" disabled={busy} onClick={() => void onEnd(assignment)}>
          End assignment
        </Button>
      )}
    </article>
  );
}

function Pagination({ page, current, setPage, label }: {
  page?: Page<unknown>;
  current: number;
  setPage: (page: number) => void;
  label: string;
}) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <nav aria-label={`${label} pages`} className="flex items-center justify-between gap-3 border-t border-border/70 pt-3">
      <Button type="button" variant="outline" className="min-h-10" disabled={current === 0} onClick={() => setPage(current - 1)}>Previous</Button>
      <span className="text-sm text-muted-foreground" aria-live="polite">Page {current + 1} of {page.totalPages} · {page.totalElements} total</span>
      <Button type="button" variant="outline" className="min-h-10" disabled={page.last} onClick={() => setPage(current + 1)}>Next</Button>
    </nav>
  );
}

function EmptyState({ title, detail }: { title: string; detail: string }) {
  return <div className="rounded-xl border border-dashed p-6 text-center"><p className="font-medium">{title}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{detail}</p></div>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-words font-medium">{value}</dd></div>;
}

function dateLabel(value?: string | null) {
  return value ? new Date(value).toLocaleDateString() : "Not available";
}

function dateTimeLabel(value?: string | null) {
  return value ? new Date(value).toLocaleString() : "Not available";
}

function statusLabel(value: string) {
  return value.toUpperCase().replaceAll("_", " ");
}

function vehicleName(vehicle?: { plateNumber: string; truckType: string }) {
  return vehicle ? `${vehicle.plateNumber} · ${vehicle.truckType}` : undefined;
}
