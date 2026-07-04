import { useState, useMemo, useEffect } from "react";
import { useLocation, useSearch } from "wouter";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { Users, Package, Truck, CheckCircle, Clock, UserPlus, Loader2, Shield, Banknote, AlertTriangle, Lock, DollarSign, TrendingUp, BarChart3, MapPin, Activity, FileText, ThumbsUp, ThumbsDown, Eye, Search, ChevronLeft, ChevronRight, Building2, ChevronDown, Settings, Gavel, Star, CreditCard } from "lucide-react";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, PieChart, Pie, Cell } from "recharts";
import { EthioSidebar } from "@/components/ui/ethio-sidebar";
import { EthioTopbar } from "@/components/ui/ethio-topbar";
import { MetricCard } from "@/components/ui/metric-card";
import { PageHeader } from "@/components/ui/page-header";

const ADMIN_PAGE_SIZE = 10;

function TablePager({
  page, total, pageSize, onPageChange,
}: {
  page: number; total: number; pageSize: number; onPageChange: (p: number) => void;
}) {
  if (total === 0) return null;
  const totalPages = Math.max(Math.ceil(total / pageSize), 1);
  return (
    <div className="flex items-center justify-between pt-4 border-t mt-4">
      <p className="text-xs text-muted-foreground">
        Showing {Math.min(page * pageSize + 1, total)}–{Math.min((page + 1) * pageSize, total)} of {total}
      </p>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="sm" className="h-7 w-7 p-0 rounded-lg"
          disabled={page === 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft className="h-3.5 w-3.5" />
        </Button>
        <span className="text-xs px-2 text-muted-foreground">{page + 1} / {totalPages}</span>
        <Button variant="outline" size="sm" className="h-7 w-7 p-0 rounded-lg"
          disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
          <ChevronRight className="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>
  );
}

export default function Admin() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [, navigate] = useLocation();
  const rawSearch = useSearch();
  const searchStr = rawSearch.startsWith('?') ? rawSearch.slice(1) : rawSearch;
  const [activeTab, setActiveTab] = useState(() => new URLSearchParams(searchStr).get('tab') || 'overview');

  useEffect(() => {
    const tab = new URLSearchParams(searchStr).get('tab') || 'overview';
    setActiveTab(tab);
  }, [rawSearch]);

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    navigate(tab === 'overview' ? '/admin' : `/admin?tab=${tab}`);
  };
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    name: "", email: "", phone: "", password: "", licenseNumber: "", nationalId: "", yearsExperience: "", role: "driver" as "driver" | "fleet_owner",
  });
  const [resolveOpen, setResolveOpen] = useState(false);
  const [resolveForm, setResolveForm] = useState({ disputeId: 0 as number | null, resolution: "" as "release" | "refund" | "split" | "investigating" | "", adminNotes: "", refundAmount: "" });
  const [docStatusFilter, setDocStatusFilter] = useState("pending");
  const [rejectDocOpen, setRejectDocOpen] = useState(false);
  const [rejectDocForm, setRejectDocForm] = useState({ docId: null as number | null, reason: "" });
  const [docSearch, setDocSearch] = useState("");
  const [docPage, setDocPage] = useState(0);
  const [driverDocOpen, setDriverDocOpen] = useState(false);
  const [selectedDriver, setSelectedDriver] = useState<{ driverId: number; name: string; phone: string; docs: any[] } | null>(null);
  const [pricingForm, setPricingForm] = useState({ rate_min: 18, rate_max: 28 });

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ["admin-stats"],
    queryFn: () => api.get<any>("/admin/stats"),
  });

  const { data: usersData } = useQuery({
    queryKey: ["admin-users"],
    queryFn: () => api.get<any>("/users"),
  });

  const { data: driversData } = useQuery({
    queryKey: ["admin-drivers"],
    queryFn: () => api.get<any>("/drivers"),
  });

  const { data: paymentsData } = useQuery({
    queryKey: ["admin-payments"],
    queryFn: () => api.get<any>("/admin/payments"),
  });

  const { data: disputesData } = useQuery({
    queryKey: ["admin-disputes"],
    queryFn: () => api.get<any>("/disputes"),
  });

  const { data: escrowData } = useQuery({
    queryKey: ["admin-escrow"],
    queryFn: () => api.get<any>("/admin/escrow"),
  });

  const { data: docsData, isLoading: docsLoading } = useQuery({
    queryKey: ["admin-docs"],
    queryFn: () => api.get<any>("/admin/driver-documents"),
  });

  const reviewDoc = useMutation({
    mutationFn: ({ id, action, rejectionReason }: { id: number; action: "approve" | "reject"; rejectionReason?: string }) =>
      api.patch(`/admin/driver-documents/${id}/review`, { action, rejection_reason: rejectionReason }),
    onSuccess: (_data, vars) => {
      toast({ title: vars.action === "approve" ? "Document approved" : "Document rejected" });
      setRejectDocOpen(false);
      setRejectDocForm({ docId: null, reason: "" });
      qc.invalidateQueries({ queryKey: ["admin-docs"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const { data: pricingData } = useQuery({
    queryKey: ["admin-pricing"],
    queryFn: () => api.get<any>("/admin/settings/pricing"),
  });

  useEffect(() => {
    if (pricingData) {
      setPricingForm({ rate_min: (pricingData as any).rate_min, rate_max: (pricingData as any).rate_max });
    }
  }, [pricingData]);

  const savePricing = useMutation({
    mutationFn: (vals: { rate_min: number; rate_max: number }) =>
      api.patch("/admin/settings/pricing", vals),
    onSuccess: () => {
      toast({ title: "Pricing rates saved" });
      qc.invalidateQueries({ queryKey: ["admin-pricing"] });
    },
    onError: (err: any) => toast({ title: "Save failed", description: err.message, variant: "destructive" }),
  });

  const { data: tripsData, isLoading: tripsLoading } = useQuery({
    queryKey: ["admin-trips"],
    queryFn: () => api.get<any>("/trips"),
    refetchInterval: 30_000,
  });

  const [selectedCargo, setSelectedCargo] = useState<any>(null);
  const [cargoServiceFilter, setCargoServiceFilter] = useState<'all' | 'intercity' | 'intracity'>('all');

  const { data: cargoData, isLoading: cargoLoading } = useQuery({
    queryKey: ["admin-cargo"],
    queryFn: () => api.get<any>("/cargo-requests"),
  });

  const { data: bidsData, isLoading: bidsLoading } = useQuery({
    queryKey: ["admin-cargo-bids", selectedCargo?.id],
    queryFn: () => api.get<any>(`/cargo-requests/${selectedCargo!.id}/bids`),
    enabled: !!selectedCargo,
  });

  const { data: revenueAnalytics } = useQuery({
    queryKey: ["admin-analytics-revenue"],
    queryFn: () => api.get<any>("/admin/analytics/revenue"),
  });

  const { data: routeAnalytics } = useQuery({
    queryKey: ["admin-analytics-routes"],
    queryFn: () => api.get<any>("/admin/analytics/routes"),
  });

  const { data: cargoAnalytics } = useQuery({
    queryKey: ["admin-analytics-cargo"],
    queryFn: () => api.get<any>("/admin/analytics/cargo"),
  });

  const updateDriverStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      api.patch(`/drivers/${id}/status`, { status }),
    onSuccess: () => {
      toast({ title: "Driver status updated" });
      qc.invalidateQueries({ queryKey: ["admin-drivers"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const createDriver = useMutation({
    mutationFn: () => api.post("/admin/drivers", {
      ...createForm,
      yearsExperience: createForm.yearsExperience ? Number(createForm.yearsExperience) : 0,
    }),
    onSuccess: () => {
      toast({ title: "Driver created successfully!" });
      setCreateOpen(false);
      setCreateForm({ name: "", email: "", phone: "", password: "", licenseNumber: "", nationalId: "", yearsExperience: "", role: "driver" });
      qc.invalidateQueries({ queryKey: ["admin-drivers"] });
      qc.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const resolveDispute = useMutation({
    mutationFn: ({ id, resolution, adminNotes, refundAmount }: { id: number; resolution: string; adminNotes: string; refundAmount?: number }) =>
      api.patch(`/disputes/${id}/resolve`, { resolution, adminNotes, refundAmount }),
    onSuccess: () => {
      toast({ title: "Dispute resolved successfully" });
      setResolveOpen(false);
      setResolveForm({ disputeId: null, resolution: "", adminNotes: "", refundAmount: "" });
      qc.invalidateQueries({ queryKey: ["admin-disputes"] });
      qc.invalidateQueries({ queryKey: ["admin-stats"] });
      qc.invalidateQueries({ queryKey: ["admin-escrow"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const { data: unpaidBookings, isLoading: unpaidLoading } = useQuery({
    queryKey: ["admin-unpaid-bookings"],
    queryFn: () => api.get<any>("/admin/bookings/unpaid"),
    refetchInterval: 30_000,
  });

  const markCashPaid = useMutation({
    mutationFn: (bookingId: number) =>
      api.post(`/admin/bookings/${bookingId}/mark-cash-paid`, {}),
    onSuccess: () => {
      toast({ title: "Cash payment recorded", description: "Booking marked as paid." });
      qc.invalidateQueries({ queryKey: ["admin-unpaid-bookings"] });
      qc.invalidateQueries({ queryKey: ["admin-payments"] });
      qc.invalidateQueries({ queryKey: ["admin-stats"] });
      qc.invalidateQueries({ queryKey: ["admin-analytics-revenue"] });
    },
    onError: (err: any) => toast({ title: "Failed to record payment", description: err.message, variant: "destructive" }),
  });

  const { data: fleetOwnersData, isLoading: fleetOwnersLoading } = useQuery({
    queryKey: ["admin-fleet-owners"],
    queryFn: () => api.get<any>("/admin/fleet-owners"),
  });

  const [fleetSearch, setFleetSearch] = useState("");
  const [expandedFleet, setExpandedFleet] = useState<number | null>(null);
  const [expandedTrip, setExpandedTrip] = useState<number | null>(null);

  // Pagination state for each table
  const [tripsPage, setTripsPage] = useState(0);
  const [driversPage, setDriversPage] = useState(0);
  const [usersPage, setUsersPage] = useState(0);
  const [paymentsPage, setPaymentsPage] = useState(0);
  const [unpaidBookingsPage, setUnpaidBookingsPage] = useState(0);
  const [disputesPage, setDisputesPage] = useState(0);
  const [cargoPage, setCargoPage] = useState(0);
  const [cargoSearch, setCargoSearch] = useState("");
  const [bidsDialogOpen, setBidsDialogOpen] = useState(false);

  // User management state
  const [userCreateOpen, setUserCreateOpen] = useState(false);
  const [userEditOpen, setUserEditOpen] = useState(false);
  const [userDeleteOpen, setUserDeleteOpen] = useState(false);
  const [selectedUserForAction, setSelectedUserForAction] = useState<any>(null);
  const [userForm, setUserForm] = useState({ name: "", email: "", phone: "", password: "", role: "shipper" as "driver" | "shipper" | "fleet_owner" });

  const createUser = useMutation({
    mutationFn: () => api.post("/admin/users", userForm),
    onSuccess: () => {
      toast({ title: "User created successfully!" });
      setUserCreateOpen(false);
      setUserForm({ name: "", email: "", phone: "", password: "", role: "shipper" });
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      qc.invalidateQueries({ queryKey: ["admin-stats"] });
    },
    onError: (err: any) => toast({ title: "Failed to create user", description: err.message, variant: "destructive" }),
  });

  const updateUser = useMutation({
    mutationFn: () => api.put(`/admin/users/${selectedUserForAction?.id}`, {
      name: userForm.name, email: userForm.email, phone: userForm.phone, role: userForm.role,
      ...(userForm.password ? { password: userForm.password } : {}),
    }),
    onSuccess: () => {
      toast({ title: "User updated successfully!" });
      setUserEditOpen(false);
      setSelectedUserForAction(null);
      qc.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: any) => toast({ title: "Failed to update user", description: err.message, variant: "destructive" }),
  });

  const deleteUser = useMutation({
    mutationFn: (id: number) => api.del(`/admin/users/${id}`),
    onSuccess: () => {
      toast({ title: "User deleted" });
      setUserDeleteOpen(false);
      setSelectedUserForAction(null);
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      qc.invalidateQueries({ queryKey: ["admin-stats"] });
    },
    onError: (err: any) => toast({ title: "Failed to delete user", description: err.message, variant: "destructive" }),
  });

  const filteredFleetOwners = useMemo(() => {
    const owners: any[] = fleetOwnersData?.fleet_owners ?? [];
    if (!fleetSearch) return owners;
    const q = fleetSearch.toLowerCase();
    return owners.filter((o: any) =>
      (o.name ?? "").toLowerCase().includes(q) ||
      (o.email ?? "").toLowerCase().includes(q) ||
      (o.phone ?? "").includes(fleetSearch)
    );
  }, [fleetOwnersData, fleetSearch]);

  async function handleViewDocument(doc: any) {
    try {
      const stored = localStorage.getItem("freightlink_auth");
      const token = stored ? JSON.parse(stored).token : null;
      const res = await fetch(`/api/driver/documents/${doc.id}/file`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) { toast({ title: "Could not load file", variant: "destructive" }); return; }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank");
    } catch {
      toast({ title: "Could not load file", variant: "destructive" });
    }
  }

  const DOC_TYPES = [
    { type: "license",              label: "Driver's License" },
    { type: "national_id",          label: "National ID" },
    { type: "vehicle_registration", label: "Vehicle Registration" },
    { type: "insurance",            label: "Insurance Certificate" },
    { type: "tin",                  label: "TIN Certificate" },
  ] as const;

  const allDocs: any[] = (docsData as any)?.data ?? [];
  const driverGroups = useMemo(() => {
    const map = new Map<string, { driverId: number; name: string; phone: string; docs: any[] }>();
    for (const doc of allDocs) {
      const key = String(doc.user_id ?? doc.driver_name ?? "?");
      if (!map.has(key)) {
        map.set(key, { driverId: doc.user_id ?? 0, name: doc.driver_name ?? "Unknown", phone: doc.driver_phone ?? "", docs: [] });
      }
      map.get(key)!.docs.push(doc);
    }
    return [...map.values()];
  }, [allDocs]);

  const DOC_PAGE_SIZE = 10;
  const filteredGroups = useMemo(() => driverGroups.filter(g => {
    const matchSearch = !docSearch ||
      g.name.toLowerCase().includes(docSearch.toLowerCase()) ||
      g.phone.includes(docSearch);
    const matchStatus = docStatusFilter === "all" || g.docs.some(d => d.status === docStatusFilter);
    return matchSearch && matchStatus;
  }), [driverGroups, docSearch, docStatusFilter]);
  const totalDocPages = Math.ceil(filteredGroups.length / DOC_PAGE_SIZE);
  const pagedGroups = filteredGroups.slice(docPage * DOC_PAGE_SIZE, (docPage + 1) * DOC_PAGE_SIZE);

  const ROLE_COLORS: Record<string, string> = {
    admin: "bg-red-50 text-red-700 border-red-200",
    shipper: "bg-sky-50 text-sky-700 border-sky-200",
    driver: "bg-green-50 text-green-800 border-green-200",
    fleet_owner: "bg-amber-50 text-amber-700 border-amber-200",
  };

  const STATUS_COLORS: Record<string, string> = {
    active: "bg-green-50 text-green-800 border-green-200",
    approved: "bg-green-50 text-green-700 border-green-200",
    under_review: "bg-amber-50 text-amber-700 border-amber-200",
    submitted: "bg-sky-50 text-sky-700 border-sky-200",
    suspended: "bg-red-50 text-red-700 border-red-200",
  };

  const CHART_COLORS = ["#0F3D1A", "#F59E0B", "#0ea5e9", "#dc2626", "#7c3aed", "#0891b2", "#ea580c"];

  return (
    <div className="flex min-h-screen" style={{ background: "#F8FAF8" }}>
      <EthioSidebar />
      <div style={{ marginLeft: 240, flex: 1, minWidth: 0 }}>
        <EthioTopbar title="Admin Dashboard" subtitle="Platform overview and management" />
        <main style={{ marginTop: 60 }} className="px-6 py-6">

      <Tabs value={activeTab} onValueChange={handleTabChange}>
        <TabsList className="mb-4 flex-wrap rounded-lg">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="cargo">Cargo &amp; Bids</TabsTrigger>
          <TabsTrigger value="drivers">Drivers</TabsTrigger>
          <TabsTrigger value="fleet">Fleet Owners</TabsTrigger>
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="payments">Payments</TabsTrigger>
          <TabsTrigger value="escrow">Escrow</TabsTrigger>
          <TabsTrigger value="disputes">Disputes</TabsTrigger>
          <TabsTrigger value="documents">Documents</TabsTrigger>
          <TabsTrigger value="analytics">Analytics</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>

        {/* ── Overview tab ─────────────────────────────────────────────── */}
        <TabsContent value="overview">
          <div className="space-y-6">

            {/* KPI grid */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {statsLoading ? (
                Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)
              ) : (
                <>
                  <MetricCard label="Total Users"      value={stats?.users?.total ?? 0}       icon={<Users size={18}/>}         accentColor="#0F3D1A" />
                  <MetricCard label="Active Drivers"   value={stats?.drivers?.active ?? 0}    icon={<Truck size={18}/>}         accentColor="#F59E0B" />
                  <MetricCard label="Open Loads"       value={stats?.freight?.posted ?? 0}    icon={<Package size={18}/>}       accentColor="#0EA5E9" />
                  <MetricCard label="Completed Trips"  value={stats?.freight?.completed ?? 0} icon={<CheckCircle size={18}/>}   accentColor="#22C55E" />
                  <MetricCard label="Total Payments"   value={stats?.payments?.total ?? 0}    icon={<Banknote size={18}/>}      accentColor="#0F3D1A" />
                  <MetricCard label="Escrow Held"      value={stats?.payments?.escrowHeld ?? 0} icon={<Lock size={18}/>}        accentColor="#7C3AED" />
                  <MetricCard label="Platform Revenue" value={`${(stats?.payments?.revenue ?? 0).toLocaleString()} ETB`} icon={<DollarSign size={18}/>} accentColor="#F59E0B" />
                  <MetricCard label="Open Disputes"    value={stats?.payments?.openDisputes ?? 0} icon={<AlertTriangle size={18}/>} accentColor="#DC2626" />
                </>
              )}
            </div>

            {/* Recent trips table */}
            <Card className="border-border/60 rounded-xl">
              <CardHeader className="flex flex-row items-center justify-between pb-3">
                <CardTitle className="text-base flex items-center gap-2">
                  <Activity className="h-4 w-4 text-emerald-600" />
                  Recent Trips
                </CardTitle>
                <span className="text-xs text-muted-foreground">Last 50 · refreshes every 30 s</span>
              </CardHeader>
              <CardContent>
                {tripsLoading ? (
                  <div className="space-y-2">
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Skeleton key={i} className="h-10 rounded-lg" />
                    ))}
                  </div>
                ) : (
                  <>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-left">
                          <th className="pb-3 w-6"></th>
                          <th className="pb-3 font-medium text-muted-foreground">#</th>
                          <th className="pb-3 font-medium text-muted-foreground">Route</th>
                          <th className="pb-3 font-medium text-muted-foreground">Type</th>
                          <th className="pb-3 font-medium text-muted-foreground">Driver</th>
                          <th className="pb-3 font-medium text-muted-foreground">Truck</th>
                          <th className="pb-3 font-medium text-muted-foreground">Stops</th>
                          <th className="pb-3 font-medium text-muted-foreground">Total Value</th>
                          <th className="pb-3 font-medium text-muted-foreground">Payment</th>
                          <th className="pb-3 font-medium text-muted-foreground">Status</th>
                          <th className="pb-3 font-medium text-muted-foreground">Started</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y">
                        {((tripsData as any)?.data ?? []).slice(tripsPage * ADMIN_PAGE_SIZE, (tripsPage + 1) * ADMIN_PAGE_SIZE).map((trip: any) => {
                          const cargo      = trip.booking?.cargo_request;
                          const driver     = trip.booking?.driver;
                          const vehicle    = trip.booking?.vehicle;
                          const isOngoing  = trip.trip_status === "ongoing";
                          const isCompleted = trip.trip_status === "completed";
                          const isMultiStop = trip.trip_type === "multi_stop";
                          const stops: any[] = trip.stops ?? [];
                          const isExpanded = expandedTrip === trip.id;
                          const totalAmount = trip.total_amount ?? trip.booking?.estimated_price;

                          const stopDotColor = (status: string) => {
                            if (status === "completed") return "bg-emerald-500";
                            if (status === "arrived")   return "bg-amber-400";
                            if (status === "loaded")    return "bg-blue-500";
                            return "bg-gray-300";
                          };

                          return (
                            <>
                              <tr
                                key={trip.id}
                                className={`hover:bg-muted/30 transition-colors ${isMultiStop ? "cursor-pointer" : ""}`}
                                onClick={() => isMultiStop && setExpandedTrip(isExpanded ? null : trip.id)}
                              >
                                <td className="py-3 pr-1">
                                  {isMultiStop && (
                                    <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${isExpanded ? "rotate-180" : ""}`} />
                                  )}
                                </td>
                                <td className="py-3 font-medium text-muted-foreground">#{trip.id}</td>
                                <td className="py-3">
                                  {cargo ? (
                                    <span className="flex items-center gap-1 text-xs font-medium">
                                      <MapPin className="h-3 w-3 text-muted-foreground shrink-0" />
                                      {cargo.pickup_location} → {cargo.destination}
                                    </span>
                                  ) : (
                                    <span className="text-muted-foreground text-xs">
                                      {trip.start_location ?? "—"} → {trip.destination ?? "—"}
                                    </span>
                                  )}
                                </td>
                                <td className="py-3">
                                  <Badge className={`text-[10px] border ${
                                    isMultiStop
                                      ? "bg-blue-50 text-blue-700 border-blue-200"
                                      : "bg-gray-50 text-gray-600 border-gray-200"
                                  }`}>
                                    {isMultiStop ? "Multi-Stop" : "Direct"}
                                  </Badge>
                                </td>
                                <td className="py-3">
                                  <div className="flex items-center gap-2">
                                    <div className="h-6 w-6 rounded-md bg-emerald-50 flex items-center justify-center text-[10px] font-bold text-emerald-700">
                                      {driver?.full_name?.split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase() ?? "D"}
                                    </div>
                                    <span className="text-xs">{driver?.full_name ?? "—"}</span>
                                  </div>
                                </td>
                                <td className="py-3 text-muted-foreground text-xs">
                                  {vehicle ? `${vehicle.truck_type} · ${vehicle.plate_number}` : "—"}
                                </td>
                                <td className="py-3 text-xs">
                                  {isMultiStop ? (
                                    <span className="font-medium text-blue-700">
                                      {trip.completed_stops ?? 0}/{trip.total_stops ?? 1}
                                    </span>
                                  ) : (
                                    <span className="text-muted-foreground">1/1</span>
                                  )}
                                </td>
                                <td className="py-3 font-medium text-amber-600 text-xs">
                                  {totalAmount
                                    ? `${Number(totalAmount).toLocaleString()} ETB`
                                    : "—"}
                                </td>
                                <td className="py-3">
                                  {trip.booking?.payment_method ? (
                                    <span className="flex items-center gap-1 text-xs text-muted-foreground">
                                      <CreditCard className="h-3 w-3 shrink-0" />
                                      {trip.booking.payment_method.replace(/_/g, " ")}
                                    </span>
                                  ) : (
                                    <span className="text-xs text-muted-foreground">—</span>
                                  )}
                                </td>
                                <td className="py-3">
                                  <Badge className={`text-xs border ${
                                    isOngoing
                                      ? "bg-sky-50 text-sky-700 border-sky-200"
                                      : isCompleted
                                      ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                      : "bg-gray-50 text-gray-600 border-gray-200"
                                  }`}>
                                    {trip.trip_status}
                                  </Badge>
                                </td>
                                <td className="py-3 text-muted-foreground text-xs">
                                  {trip.start_time
                                    ? new Date(trip.start_time).toLocaleDateString("en-ET", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })
                                    : new Date(trip.created_at).toLocaleDateString("en-ET", { day: "2-digit", month: "short" })}
                                </td>
                              </tr>
                              {/* Expandable stop timeline */}
                              {isExpanded && stops.length > 0 && (
                                <tr key={`${trip.id}-stops`} className="bg-blue-50/50">
                                  <td colSpan={10} className="px-8 py-3">
                                    <div className="flex flex-col gap-2">
                                      <p className="text-xs font-semibold text-blue-800 mb-1">Stop Timeline</p>
                                      {stops.map((stop: any, idx: number) => (
                                        <div key={stop.id} className="flex items-start gap-3">
                                          {/* Dot + line */}
                                          <div className="flex flex-col items-center">
                                            <div className={`h-5 w-5 rounded-full flex items-center justify-center text-[10px] font-bold text-white shrink-0 ${stopDotColor(stop.status)}`}>
                                              {stop.stop_order}
                                            </div>
                                            {idx < stops.length - 1 && (
                                              <div className="w-0.5 h-4 bg-gray-200 mt-0.5" />
                                            )}
                                          </div>
                                          {/* Stop info */}
                                          <div className="flex-1 pb-1">
                                            <div className="flex items-center gap-2 flex-wrap">
                                              <span className="text-xs font-medium text-gray-800">{stop.location_name}</span>
                                              <Badge className={`text-[9px] border px-1.5 py-0 ${
                                                stop.status === "completed" ? "bg-emerald-50 text-emerald-700 border-emerald-200" :
                                                stop.status === "arrived"   ? "bg-amber-50 text-amber-700 border-amber-200" :
                                                stop.status === "loaded"    ? "bg-blue-50 text-blue-700 border-blue-200" :
                                                "bg-gray-50 text-gray-600 border-gray-200"
                                              }`}>
                                                {stop.status}
                                              </Badge>
                                              <span className="text-xs text-amber-600 font-medium">
                                                {stop.agreed_price_formatted ?? `ETB ${Number(stop.agreed_price).toLocaleString()}`}
                                              </span>
                                              {stop.cargo_material && (
                                                <span className="text-[10px] text-muted-foreground">
                                                  {stop.cargo_material}{stop.cargo_weight ? ` · ${stop.cargo_weight}t` : ""}
                                                </span>
                                              )}
                                            </div>
                                          </div>
                                        </div>
                                      ))}
                                    </div>
                                  </td>
                                </tr>
                              )}
                            </>
                          );
                        })}
                        {!tripsLoading && ((tripsData as any)?.data ?? []).length === 0 && (
                          <tr>
                            <td colSpan={10} className="py-10 text-center text-muted-foreground">
                              No trips yet
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  <TablePager
                    page={tripsPage}
                    total={((tripsData as any)?.data ?? []).length}
                    pageSize={ADMIN_PAGE_SIZE}
                    onPageChange={setTripsPage}
                  />
                  </>
                )}
              </CardContent>
            </Card>

          </div>
        </TabsContent>

        {/* ── Cargo & Bids tab ─────────────────────────────────────────── */}
        <TabsContent value="cargo">
          <PageHeader title="Cargo & Bids" breadcrumbs={[{ label: 'Admin' }, { label: 'Cargo & Bids' }]} />

          {/* Bids dialog */}
          <Dialog open={bidsDialogOpen} onOpenChange={(o) => { setBidsDialogOpen(o); if (!o) setSelectedCargo(null); }}>
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2 text-base">
                  <Gavel className="h-4 w-4 text-amber-500" />
                  Bids for Cargo #{selectedCargo?.id}
                  {selectedCargo?.price_type === "fixed" && (
                    <Badge className="text-[10px] border bg-amber-50 text-amber-700 border-amber-200 ml-1">Fixed Price</Badge>
                  )}
                </DialogTitle>
              </DialogHeader>
              {bidsLoading ? (
                <div className="space-y-2 py-4">
                  {[1, 2, 3].map((i) => <Skeleton key={i} className="h-14 rounded-lg" />)}
                </div>
              ) : (
                (() => {
                  const rawBids: any[] = (bidsData as any)?.data ?? (Array.isArray(bidsData) ? bidsData : []);
                  const isFixed = selectedCargo?.price_type === "fixed";
                  return rawBids.length === 0 ? (
                    <div className="flex flex-col items-center py-10 gap-2 text-muted-foreground">
                      <Gavel className="h-8 w-8 opacity-30" />
                      <p className="text-sm">No bids yet for this cargo</p>
                    </div>
                  ) : (
                    <div className="space-y-2 max-h-96 overflow-y-auto pr-1">
                      {rawBids.map((bid: any, idx: number) => {
                        const isTop = idx === 0 && isFixed;
                        const rating = bid.driver?.rating ?? bid.driver_rating ?? null;
                        return (
                          <div
                            key={bid.id}
                            className={`flex items-center gap-3 rounded-xl border px-4 py-3 ${isTop ? "border-amber-300 bg-amber-50" : "border-border/60 bg-white"}`}
                          >
                            {/* Rank */}
                            <span className="text-xs font-bold text-muted-foreground w-5 text-center">#{idx + 1}</span>
                            {/* Avatar */}
                            <div className="h-8 w-8 rounded-md bg-emerald-50 flex items-center justify-center text-[10px] font-bold text-emerald-700 shrink-0">
                              {(bid.driver?.full_name ?? bid.driver_name ?? "D").split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase()}
                            </div>
                            {/* Info */}
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2">
                                <span className="text-sm font-semibold text-foreground truncate">
                                  {bid.driver?.full_name ?? bid.driver_name ?? "Unknown Driver"}
                                </span>
                                {isTop && (
                                  <span className="flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-100 rounded-full px-2 py-0.5 border border-amber-300 shrink-0">
                                    <Star className="h-2.5 w-2.5 fill-amber-500 stroke-amber-500" /> Top Rated
                                  </span>
                                )}
                              </div>
                              <div className="flex items-center gap-3 mt-0.5">
                                {rating !== null && (
                                  <span className="flex items-center gap-0.5 text-xs text-amber-600">
                                    <Star className="h-3 w-3 fill-amber-400 stroke-amber-400" />
                                    {Number(rating).toFixed(1)}
                                    {bid.driver?.rating_count ? <span className="text-muted-foreground ml-0.5">({bid.driver.rating_count})</span> : null}
                                  </span>
                                )}
                                {bid.vehicle && (
                                  <span className="text-xs text-muted-foreground">
                                    {bid.vehicle.truck_type} · {bid.vehicle.plate_number}
                                  </span>
                                )}
                              </div>
                              {selectedCargo?.service_type === "intracity" && bid.available_datetime && (
                                <div className="mt-1">
                                  <span className="text-[11px] text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-1.5 py-0.5">
                                    Available: {new Date(bid.available_datetime).toLocaleString('en-ET', { dateStyle: 'medium', timeStyle: 'short' })}
                                  </span>
                                </div>
                              )}
                            </div>
                            {/* Amount & status */}
                            <div className="text-right shrink-0">
                              <p className="text-sm font-bold text-amber-600">
                                {Number(bid.amount).toLocaleString()} ETB
                              </p>
                              <Badge className={`text-[10px] border mt-1 ${
                                bid.status === "accepted"
                                  ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                  : bid.status === "rejected"
                                  ? "bg-red-50 text-red-700 border-red-200"
                                  : bid.status === "countered"
                                  ? "bg-sky-50 text-sky-700 border-sky-200"
                                  : "bg-gray-50 text-gray-600 border-gray-200"
                              }`}>
                                {bid.status}
                              </Badge>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  );
                })()
              )}
            </DialogContent>
          </Dialog>

          {/* Cargo table */}
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="pb-3">
              <div className="flex items-center gap-3">
                <div className="relative flex-1 max-w-sm">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                  <Input
                    placeholder="Search route or cargo type…"
                    className="pl-9 h-9 text-sm rounded-lg"
                    value={cargoSearch}
                    onChange={(e) => { setCargoSearch(e.target.value); setCargoPage(0); }}
                  />
                </div>
                <div className="flex gap-1">
                  {(['all', 'intercity', 'intracity'] as const).map((f) => (
                    <Button
                      key={f}
                      size="sm"
                      variant={cargoServiceFilter === f ? 'default' : 'outline'}
                      className={`h-9 text-xs rounded-lg capitalize ${cargoServiceFilter === f ? 'bg-emerald-600 hover:bg-emerald-700' : ''}`}
                      onClick={() => { setCargoServiceFilter(f); setCargoPage(0); }}
                    >
                      {f === 'all' ? 'All' : f === 'intercity' ? 'Intercity' : 'Intra-city'}
                    </Button>
                  ))}
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {cargoLoading ? (
                <div className="space-y-2">{[1,2,3,4,5].map((i) => <Skeleton key={i} className="h-10 rounded-lg" />)}</div>
              ) : (() => {
                const allCargo: any[] = Array.isArray(cargoData) ? cargoData : ((cargoData as any)?.data ?? []);
                const filtered = allCargo.filter((c: any) => {
                  if (cargoServiceFilter !== 'all' && c.service_type !== cargoServiceFilter) return false;
                  if (!cargoSearch) return true;
                  const q = cargoSearch.toLowerCase();
                  return (
                    (c.pickup_location ?? "").toLowerCase().includes(q) ||
                    (c.destination ?? "").toLowerCase().includes(q) ||
                    (c.pickup_area ?? "").toLowerCase().includes(q) ||
                    (c.dropoff_area ?? "").toLowerCase().includes(q) ||
                    (c.city ?? "").toLowerCase().includes(q) ||
                    (c.material_type ?? "").toLowerCase().includes(q)
                  );
                });
                const paged = filtered.slice(cargoPage * ADMIN_PAGE_SIZE, (cargoPage + 1) * ADMIN_PAGE_SIZE);
                return (
                  <>
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b text-left">
                            <th className="pb-3 font-medium text-muted-foreground">#</th>
                            <th className="pb-3 font-medium text-muted-foreground">Service</th>
                            <th className="pb-3 font-medium text-muted-foreground">Route</th>
                            <th className="pb-3 font-medium text-muted-foreground">Cargo Type</th>
                            <th className="pb-3 font-medium text-muted-foreground">Weight</th>
                            <th className="pb-3 font-medium text-muted-foreground">Pricing</th>
                            <th className="pb-3 font-medium text-muted-foreground">Budget</th>
                            <th className="pb-3 font-medium text-muted-foreground">Status</th>
                            <th className="pb-3 font-medium text-muted-foreground">Shipper</th>
                            <th className="pb-3 font-medium text-muted-foreground">Bids</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y">
                          {paged.map((c: any) => {
                            const isFixed = c.price_type === "fixed";
                            const isIntracity = c.service_type === "intracity";
                            return (
                              <tr key={c.id} className="hover:bg-muted/30 transition-colors">
                                <td className="py-3 font-medium text-muted-foreground text-xs">#{c.id}</td>
                                <td className="py-3">
                                  <Badge className={`text-[10px] border ${isIntracity ? "bg-purple-50 text-purple-700 border-purple-200" : "bg-blue-50 text-blue-700 border-blue-200"}`}>
                                    {isIntracity ? "Intra-city" : "Intercity"}
                                  </Badge>
                                </td>
                                <td className="py-3">
                                  {isIntracity ? (
                                    <span className="flex flex-col gap-0.5">
                                      <span className="flex items-center gap-1 text-xs font-medium">
                                        <MapPin className="h-3 w-3 text-purple-400 shrink-0" />
                                        {c.city ?? "—"}
                                      </span>
                                      <span className="text-[11px] text-muted-foreground">{c.pickup_area ?? "—"} → {c.dropoff_area ?? "—"}</span>
                                    </span>
                                  ) : (
                                    <span className="flex items-center gap-1 text-xs font-medium">
                                      <MapPin className="h-3 w-3 text-muted-foreground shrink-0" />
                                      {c.pickup_location} → {c.destination}
                                    </span>
                                  )}
                                </td>
                                <td className="py-3 text-xs text-muted-foreground">{isIntracity ? (c.items_description ?? "—") : (c.material_type ?? "—")}</td>
                                <td className="py-3 text-xs text-muted-foreground">{c.weight ? `${c.weight} t` : "—"}</td>
                                <td className="py-3">
                                  <Badge className={`text-[10px] border ${
                                    isFixed
                                      ? "bg-amber-50 text-amber-700 border-amber-200"
                                      : "bg-sky-50 text-sky-700 border-sky-200"
                                  }`}>
                                    {isFixed ? "Fixed" : "Negotiable"}
                                  </Badge>
                                </td>
                                <td className="py-3 text-xs font-semibold text-amber-600">
                                  {c.budget ? `${Number(c.budget).toLocaleString()} ETB` : "—"}
                                </td>
                                <td className="py-3">
                                  <Badge className={`text-[10px] border ${
                                    c.status === "pending"
                                      ? "bg-amber-50 text-amber-700 border-amber-200"
                                      : c.status === "matched" || c.status === "completed"
                                      ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                      : c.status === "cancelled"
                                      ? "bg-red-50 text-red-700 border-red-200"
                                      : "bg-gray-50 text-gray-600 border-gray-200"
                                  }`}>
                                    {c.status}
                                  </Badge>
                                </td>
                                <td className="py-3 text-xs text-muted-foreground">{c.user?.full_name ?? c.user?.name ?? "—"}</td>
                                <td className="py-3">
                                  <Button
                                    variant="outline"
                                    size="sm"
                                    className="h-7 text-xs rounded-lg gap-1"
                                    onClick={() => { setSelectedCargo(c); setBidsDialogOpen(true); }}
                                  >
                                    <Gavel className="h-3 w-3" /> View Bids
                                  </Button>
                                </td>
                              </tr>
                            );
                          })}
                          {paged.length === 0 && (
                            <tr>
                              <td colSpan={10} className="py-10 text-center text-sm text-muted-foreground">
                                No cargo requests found
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                    </div>
                    <TablePager page={cargoPage} total={filtered.length} pageSize={ADMIN_PAGE_SIZE} onPageChange={setCargoPage} />
                  </>
                );
              })()}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="drivers">
          <PageHeader title="Driver Management" breadcrumbs={[{ label: 'Admin' }, { label: 'Drivers' }]} />
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">All Drivers</CardTitle>
              <Dialog open={createOpen} onOpenChange={setCreateOpen}>
                <DialogTrigger asChild>
                  <Button size="sm" className="gap-2 rounded-lg bg-primary hover:bg-primary/90"><UserPlus className="h-4 w-4" /> Create Driver</Button>
                </DialogTrigger>
                <DialogContent className="max-w-md rounded-xl">
                  <DialogHeader><DialogTitle>Create New Driver</DialogTitle></DialogHeader>
                  <div className="space-y-3 pt-2">
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Full Name</Label>
                      <Input placeholder="Abebe Girma" value={createForm.name}
                        onChange={e => setCreateForm(f => ({ ...f, name: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Email</Label>
                      <Input type="email" placeholder="driver@example.com" value={createForm.email}
                        onChange={e => setCreateForm(f => ({ ...f, email: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Phone</Label>
                      <Input placeholder="+251911000000" value={createForm.phone}
                        onChange={e => setCreateForm(f => ({ ...f, phone: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Role</Label>
                      <Select value={createForm.role} onValueChange={v => setCreateForm(f => ({ ...f, role: v as "driver" | "fleet_owner" }))}>
                        <SelectTrigger className="rounded-lg"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="driver">Driver</SelectItem>
                          <SelectItem value="fleet_owner">Fleet Owner</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">License Number</Label>
                      <Input placeholder="ETH-DRV-12345" value={createForm.licenseNumber}
                        onChange={e => setCreateForm(f => ({ ...f, licenseNumber: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">National ID</Label>
                      <Input placeholder="ETH-123456789" value={createForm.nationalId}
                        onChange={e => setCreateForm(f => ({ ...f, nationalId: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Years of Experience</Label>
                      <Input type="number" placeholder="5" value={createForm.yearsExperience}
                        onChange={e => setCreateForm(f => ({ ...f, yearsExperience: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Password</Label>
                      <Input type="text" placeholder="Temporary password" value={createForm.password}
                        onChange={e => setCreateForm(f => ({ ...f, password: e.target.value }))} className="rounded-lg" />
                    </div>
                    <Button className="w-full rounded-lg" onClick={() => createDriver.mutate()}
                      disabled={!createForm.name || !createForm.email || !createForm.phone || !createForm.password || createDriver.isPending}>
                      {createDriver.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                      Create Driver Account
                    </Button>
                  </div>
                </DialogContent>
              </Dialog>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="pb-3 font-medium text-muted-foreground">Driver</th>
                      <th className="pb-3 font-medium text-muted-foreground">Rating</th>
                      <th className="pb-3 font-medium text-muted-foreground">Deliveries</th>
                      <th className="pb-3 font-medium text-muted-foreground">Status</th>
                      <th className="pb-3 font-medium text-muted-foreground">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {(driversData?.drivers ?? []).slice(driversPage * ADMIN_PAGE_SIZE, (driversPage + 1) * ADMIN_PAGE_SIZE).map((d: any) => (
                      <tr key={d.id} className="py-3">
                        <td className="py-3">
                          <div className="flex items-center gap-3">
                            <div className="h-8 w-8 rounded-lg flex items-center justify-center text-sm font-bold"
                              style={{ background: 'rgba(15,61,26,0.1)', color: '#0F3D1A' }}>
                              {d.user?.name?.split(" ").map((n: string) => n[0]).join("") ?? "D"}
                            </div>
                            <div>
                              <p className="font-medium">{d.user?.name ?? `Driver #${d.id}`}</p>
                              <p className="text-xs text-muted-foreground">{d.user?.email}</p>
                            </div>
                          </div>
                        </td>
                        <td className="py-3">
                          <span className="flex items-center gap-1">
                            <svg className="h-3.5 w-3.5 text-amber-500 fill-amber-500" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                            {d.rating > 0 ? d.rating.toFixed(1) : "—"}
                            {d.totalRatings > 0 && (
                              <span className="text-xs text-muted-foreground">({d.totalRatings})</span>
                            )}
                          </span>
                        </td>
                        <td className="py-3">{d.totalDeliveries}</td>
                        <td className="py-3">
                          <Badge className={`text-xs border ${STATUS_COLORS[d.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                            {d.status}
                          </Badge>
                        </td>
                        <td className="py-3">
                          <Select
                            defaultValue={d.status}
                            onValueChange={v => updateDriverStatus.mutate({ id: d.id, status: v })}
                          >
                            <SelectTrigger className="h-7 w-[130px] text-xs rounded-lg">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="submitted">Submitted</SelectItem>
                              <SelectItem value="under_review">Under Review</SelectItem>
                              <SelectItem value="approved">Approved</SelectItem>
                              <SelectItem value="active">Active</SelectItem>
                              <SelectItem value="suspended">Suspended</SelectItem>
                            </SelectContent>
                          </Select>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <TablePager
                page={driversPage}
                total={(driversData?.drivers ?? []).length}
                pageSize={ADMIN_PAGE_SIZE}
                onPageChange={setDriversPage}
              />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="users">
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">User Management</CardTitle>
              <Dialog open={userCreateOpen} onOpenChange={setUserCreateOpen}>
                <DialogTrigger asChild>
                  <Button size="sm" className="gap-2 rounded-lg bg-primary hover:bg-primary/90">
                    <UserPlus className="h-4 w-4" /> Add User
                  </Button>
                </DialogTrigger>
                <DialogContent className="max-w-md rounded-xl">
                  <DialogHeader><DialogTitle>Add New User</DialogTitle></DialogHeader>
                  <div className="space-y-3 pt-2">
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Role</Label>
                      <Select value={userForm.role} onValueChange={v => setUserForm(f => ({ ...f, role: v as any }))}>
                        <SelectTrigger className="rounded-lg"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="shipper">Shipper</SelectItem>
                          <SelectItem value="driver">Driver</SelectItem>
                          <SelectItem value="fleet_owner">Fleet Owner</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Full Name</Label>
                      <Input placeholder="Abebe Girma" value={userForm.name}
                        onChange={e => setUserForm(f => ({ ...f, name: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Email</Label>
                      <Input type="email" placeholder="user@example.com" value={userForm.email}
                        onChange={e => setUserForm(f => ({ ...f, email: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Phone</Label>
                      <Input placeholder="+251911000000" value={userForm.phone}
                        onChange={e => setUserForm(f => ({ ...f, phone: e.target.value }))} className="rounded-lg" />
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Password</Label>
                      <Input type="text" placeholder="Temporary password" value={userForm.password}
                        onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))} className="rounded-lg" />
                    </div>
                    <Button className="w-full rounded-lg" onClick={() => createUser.mutate()}
                      disabled={!userForm.name || !userForm.email || !userForm.phone || !userForm.password || createUser.isPending}>
                      {createUser.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                      Create Account
                    </Button>
                  </div>
                </DialogContent>
              </Dialog>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="pb-3 font-medium text-muted-foreground">Name</th>
                      <th className="pb-3 font-medium text-muted-foreground">Email</th>
                      <th className="pb-3 font-medium text-muted-foreground">Phone</th>
                      <th className="pb-3 font-medium text-muted-foreground">Role</th>
                      <th className="pb-3 font-medium text-muted-foreground">Joined</th>
                      <th className="pb-3 font-medium text-muted-foreground">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {(usersData?.users ?? []).slice(usersPage * ADMIN_PAGE_SIZE, (usersPage + 1) * ADMIN_PAGE_SIZE).map((u: any) => (
                      <tr key={u.id} className="hover:bg-muted/30 transition-colors">
                        <td className="py-3">
                          <div className="flex items-center gap-2">
                            <div className="h-7 w-7 rounded-md flex items-center justify-center text-[10px] font-bold"
                              style={{ background: 'rgba(15,61,26,0.1)', color: '#0F3D1A' }}>
                              {(u.name ?? "U").split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase()}
                            </div>
                            <span className="font-medium">{u.name}</span>
                          </div>
                        </td>
                        <td className="py-3 text-muted-foreground">{u.email}</td>
                        <td className="py-3 text-muted-foreground text-xs">{u.phone ?? "—"}</td>
                        <td className="py-3">
                          <Badge className={`text-xs border ${ROLE_COLORS[u.role] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                            {u.role}
                          </Badge>
                        </td>
                        <td className="py-3 text-muted-foreground text-xs">
                          {new Date(u.createdAt).toLocaleDateString()}
                        </td>
                        <td className="py-3">
                          <div className="flex items-center gap-1.5">
                            <Button size="sm" variant="outline" className="h-7 px-2 text-xs rounded-lg gap-1"
                              onClick={() => {
                                setSelectedUserForAction(u);
                                setUserForm({ name: u.name ?? "", email: u.email ?? "", phone: u.phone ?? "", password: "", role: u.role ?? "shipper" });
                                setUserEditOpen(true);
                              }}>
                              Edit
                            </Button>
                            <Button size="sm" variant="outline"
                              className="h-7 px-2 text-xs rounded-lg gap-1 text-red-600 border-red-200 hover:bg-red-50"
                              onClick={() => { setSelectedUserForAction(u); setUserDeleteOpen(true); }}>
                              Delete
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                    {(usersData?.users ?? []).length === 0 && (
                      <tr><td colSpan={6} className="py-8 text-center text-muted-foreground">No users found</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
              <TablePager
                page={usersPage}
                total={(usersData?.users ?? []).length}
                pageSize={ADMIN_PAGE_SIZE}
                onPageChange={setUsersPage}
              />
            </CardContent>
          </Card>

          {/* Edit User Dialog */}
          <Dialog open={userEditOpen} onOpenChange={setUserEditOpen}>
            <DialogContent className="max-w-md rounded-xl">
              <DialogHeader><DialogTitle>Edit User — {selectedUserForAction?.name}</DialogTitle></DialogHeader>
              <div className="space-y-3 pt-2">
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">Role</Label>
                  <Select value={userForm.role} onValueChange={v => setUserForm(f => ({ ...f, role: v as any }))}>
                    <SelectTrigger className="rounded-lg"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="shipper">Shipper</SelectItem>
                      <SelectItem value="driver">Driver</SelectItem>
                      <SelectItem value="fleet_owner">Fleet Owner</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">Full Name</Label>
                  <Input value={userForm.name} onChange={e => setUserForm(f => ({ ...f, name: e.target.value }))} className="rounded-lg" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">Email</Label>
                  <Input type="email" value={userForm.email} onChange={e => setUserForm(f => ({ ...f, email: e.target.value }))} className="rounded-lg" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">Phone</Label>
                  <Input value={userForm.phone} onChange={e => setUserForm(f => ({ ...f, phone: e.target.value }))} className="rounded-lg" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">New Password <span className="text-muted-foreground font-normal">(leave blank to keep current)</span></Label>
                  <Input type="text" placeholder="Leave blank to keep current" value={userForm.password}
                    onChange={e => setUserForm(f => ({ ...f, password: e.target.value }))} className="rounded-lg" />
                </div>
                <Button className="w-full rounded-lg" onClick={() => updateUser.mutate()} disabled={updateUser.isPending}>
                  {updateUser.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                  Save Changes
                </Button>
              </div>
            </DialogContent>
          </Dialog>

          {/* Delete Confirmation Dialog */}
          <Dialog open={userDeleteOpen} onOpenChange={setUserDeleteOpen}>
            <DialogContent className="max-w-sm rounded-xl">
              <DialogHeader><DialogTitle>Delete User</DialogTitle></DialogHeader>
              <div className="pt-2 space-y-4">
                <p className="text-sm text-muted-foreground">
                  Are you sure you want to delete <span className="font-semibold text-foreground">{selectedUserForAction?.name}</span> ({selectedUserForAction?.email})?
                  This cannot be undone.
                </p>
                <div className="flex gap-2">
                  <Button variant="outline" className="flex-1 rounded-lg" onClick={() => setUserDeleteOpen(false)}>Cancel</Button>
                  <Button className="flex-1 rounded-lg bg-red-600 hover:bg-red-700" disabled={deleteUser.isPending}
                    onClick={() => selectedUserForAction && deleteUser.mutate(selectedUserForAction.id)}>
                    {deleteUser.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                    Delete
                  </Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </TabsContent>

        <TabsContent value="payments">
          <div className="space-y-6">

            {/* ── Pending cash payments ──────────────────────────────────── */}
            <Card className="border-amber-200 rounded-xl border-t-2 border-t-amber-400">
              <CardHeader className="pb-3">
                <div className="flex items-center gap-2">
                  <div className="h-8 w-8 rounded-lg bg-amber-50 flex items-center justify-center">
                    <Banknote className="h-4 w-4 text-amber-600" />
                  </div>
                  <div>
                    <CardTitle className="text-base">Pending Cash Payments</CardTitle>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      Bookings without a recorded payment — click "Mark Paid" after collecting cash from the driver
                    </p>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                {unpaidLoading ? (
                  <div className="space-y-2">
                    {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 rounded-lg" />)}
                  </div>
                ) : (
                  <>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-left">
                          <th className="pb-3 font-medium text-muted-foreground">#</th>
                          <th className="pb-3 font-medium text-muted-foreground">Route</th>
                          <th className="pb-3 font-medium text-muted-foreground">Driver</th>
                          <th className="pb-3 font-medium text-muted-foreground">Shipper</th>
                          <th className="pb-3 font-medium text-muted-foreground">Amount</th>
                          <th className="pb-3 font-medium text-muted-foreground">Status</th>
                          <th className="pb-3 font-medium text-muted-foreground">Action</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y">
                        {(unpaidBookings?.bookings ?? []).slice(unpaidBookingsPage * ADMIN_PAGE_SIZE, (unpaidBookingsPage + 1) * ADMIN_PAGE_SIZE).map((b: any) => (
                          <tr key={b.id} className="hover:bg-muted/30 transition-colors">
                            <td className="py-3 font-medium text-muted-foreground">#{b.id}</td>
                            <td className="py-3 text-xs font-medium max-w-[180px] truncate">{b.route}</td>
                            <td className="py-3">
                              <div>
                                <p className="font-medium text-xs">{b.driver?.name ?? "—"}</p>
                                <p className="text-[11px] text-muted-foreground">{b.driver?.phone ?? ""}</p>
                              </div>
                            </td>
                            <td className="py-3">
                              <div>
                                <p className="font-medium text-xs">{b.shipper?.name ?? "—"}</p>
                                <p className="text-[11px] text-muted-foreground">{b.shipper?.phone ?? ""}</p>
                              </div>
                            </td>
                            <td className="py-3 font-semibold text-amber-700">
                              {b.estimated_price > 0
                                ? `${Number(b.estimated_price).toLocaleString()} ETB`
                                : "—"}
                            </td>
                            <td className="py-3">
                              <Badge className="text-xs border bg-amber-50 text-amber-700 border-amber-200">
                                {b.booking_status}
                              </Badge>
                            </td>
                            <td className="py-3">
                              <Button
                                size="sm"
                                className="h-7 px-3 text-xs gap-1.5 rounded-lg bg-amber-600 hover:bg-amber-700 text-white"
                                disabled={markCashPaid.isPending}
                                onClick={() => markCashPaid.mutate(b.id)}
                              >
                                {markCashPaid.isPending && markCashPaid.variables === b.id
                                  ? <Loader2 className="h-3 w-3 animate-spin" />
                                  : <Banknote className="h-3 w-3" />}
                                Mark Paid (Cash)
                              </Button>
                            </td>
                          </tr>
                        ))}
                        {(unpaidBookings?.bookings ?? []).length === 0 && (
                          <tr>
                            <td colSpan={7} className="py-8 text-center text-muted-foreground">
                              <CheckCircle className="h-5 w-5 mx-auto mb-2 text-emerald-500" />
                              All bookings have recorded payments
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  <TablePager
                    page={unpaidBookingsPage}
                    total={(unpaidBookings?.bookings ?? []).length}
                    pageSize={ADMIN_PAGE_SIZE}
                    onPageChange={setUnpaidBookingsPage}
                  />
                  </>
                )}
              </CardContent>
            </Card>

            {/* ── Payment history ────────────────────────────────────────── */}
            <Card className="border-border/60 rounded-xl">
              <CardHeader><CardTitle className="text-base">Payment History</CardTitle></CardHeader>
              <CardContent>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left">
                        <th className="pb-3 font-medium text-muted-foreground">ID</th>
                        <th className="pb-3 font-medium text-muted-foreground">Amount</th>
                        <th className="pb-3 font-medium text-muted-foreground">Method</th>
                        <th className="pb-3 font-medium text-muted-foreground">Status</th>
                        <th className="pb-3 font-medium text-muted-foreground">Shipper</th>
                        <th className="pb-3 font-medium text-muted-foreground">Date</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y">
                      {(paymentsData?.payments ?? []).slice(paymentsPage * ADMIN_PAGE_SIZE, (paymentsPage + 1) * ADMIN_PAGE_SIZE).map((p: any) => (
                        <tr key={p.id} className="hover:bg-muted/30 transition-colors">
                          <td className="py-3 font-medium">#{p.id}</td>
                          <td className="py-3 font-medium">{p.amount?.toLocaleString?.()} ETB</td>
                          <td className="py-3">
                            <Badge className={`text-xs border ${
                              p.payment_method === "cash"
                                ? "bg-amber-50 text-amber-700 border-amber-200"
                                : "bg-sky-50 text-sky-700 border-sky-200"
                            }`}>
                              {p.payment_method ?? "—"}
                            </Badge>
                          </td>
                          <td className="py-3">
                            <Badge className={`text-xs border ${STATUS_COLORS[p.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                              {p.status}
                            </Badge>
                          </td>
                          <td className="py-3 text-muted-foreground">{p.shipper?.name ?? "—"}</td>
                          <td className="py-3 text-muted-foreground text-xs">{new Date(p.createdAt).toLocaleDateString()}</td>
                        </tr>
                      ))}
                      {(!paymentsData?.payments || paymentsData.payments.length === 0) && (
                        <tr><td colSpan={6} className="py-6 text-center text-muted-foreground">No payments yet</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
                <TablePager
                  page={paymentsPage}
                  total={(paymentsData?.payments ?? []).length}
                  pageSize={ADMIN_PAGE_SIZE}
                  onPageChange={setPaymentsPage}
                />
              </CardContent>
            </Card>

          </div>
        </TabsContent>

        <TabsContent value="escrow">
          <Card className="border-border/60 rounded-xl">
            <CardHeader><CardTitle className="text-base">Escrow Overview</CardTitle></CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
                <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
                  <p className="text-sm text-muted-foreground">Held</p>
                  <p className="text-2xl font-bold mt-1">{escrowData?.held?.count ?? 0}</p>
                  <p className="text-xs text-muted-foreground">{escrowData?.held?.total?.toLocaleString?.() ?? 0} ETB</p>
                </CardContent></Card>
                <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
                  <p className="text-sm text-muted-foreground">In Transit</p>
                  <p className="text-2xl font-bold mt-1">{escrowData?.inTransit?.count ?? 0}</p>
                  <p className="text-xs text-muted-foreground">{escrowData?.inTransit?.total?.toLocaleString?.() ?? 0} ETB</p>
                </CardContent></Card>
                <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
                  <p className="text-sm text-muted-foreground">Released</p>
                  <p className="text-2xl font-bold mt-1">{escrowData?.released?.count ?? 0}</p>
                  <p className="text-xs text-muted-foreground">{escrowData?.released?.total?.toLocaleString?.() ?? 0} ETB</p>
                </CardContent></Card>
                <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
                  <p className="text-sm text-muted-foreground">Disputed</p>
                  <p className="text-2xl font-bold mt-1">{escrowData?.disputed?.count ?? 0}</p>
                  <p className="text-xs text-muted-foreground">{escrowData?.disputed?.total?.toLocaleString?.() ?? 0} ETB</p>
                </CardContent></Card>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="analytics">
          <div className="space-y-6">
            <Card className="border-border/60 rounded-xl">
              <CardHeader>
                <CardTitle className="text-base flex items-center gap-2">
                  <TrendingUp className="h-4 w-4" /> Revenue & Deliveries
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={revenueAnalytics ?? []}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="month" />
                      <YAxis />
                      <Tooltip />
                      <Legend />
                      <Bar dataKey="revenue" fill="#0F3D1A" name="Revenue (ETB)" />
                      <Bar dataKey="deliveries" fill="#F59E0B" name="Deliveries" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <Card className="border-border/60 rounded-xl">
                <CardHeader>
                  <CardTitle className="text-base flex items-center gap-2">
                    <BarChart3 className="h-4 w-4" /> Top Routes
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="h-56">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={(routeAnalytics ?? []).slice(0, 8)} layout="vertical">
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis type="number" />
                        <YAxis dataKey="route" type="category" width={120} />
                        <Tooltip />
                        <Bar dataKey="count" fill="#0F3D1A" name="Shipments" />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </CardContent>
              </Card>
              <Card className="border-border/60 rounded-xl">
                <CardHeader>
                  <CardTitle className="text-base flex items-center gap-2">
                    <Package className="h-4 w-4" /> Cargo Types
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="h-56">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={cargoAnalytics ?? []}
                          dataKey="count"
                          nameKey="cargoType"
                          cx="50%"
                          cy="50%"
                          outerRadius={80}
                          label={({ cargoType, count }) => `${cargoType}: ${count}`}
                        >
                          {(cargoAnalytics ?? []).map((_: any, i: number) => (
                            <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                          ))}
                        </Pie>
                        <Tooltip />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </TabsContent>

        {/* ── Fleet Owners tab ─────────────────────────────────────────── */}
        <TabsContent value="fleet">
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="pb-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <CardTitle className="text-base flex items-center gap-2">
                  <Building2 className="h-4 w-4 text-purple-600" />
                  Fleet Owner Management
                  <Badge className="text-xs border bg-purple-50 text-purple-700 border-purple-200 ml-1">
                    {fleetOwnersData?.total ?? 0} fleets
                  </Badge>
                </CardTitle>
                <div className="relative">
                  <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                  <Input
                    className="h-8 pl-8 pr-3 text-xs w-52 rounded-lg"
                    placeholder="Search fleet owners…"
                    value={fleetSearch}
                    onChange={e => setFleetSearch(e.target.value)}
                  />
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {fleetOwnersLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14 rounded-lg" />)}
                </div>
              ) : filteredFleetOwners.length === 0 ? (
                <div className="py-12 text-center text-muted-foreground">
                  <Building2 className="h-8 w-8 mx-auto mb-3 opacity-30" />
                  <p className="text-sm">{fleetSearch ? `No fleet owners matching "${fleetSearch}"` : "No fleet owners yet"}</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {filteredFleetOwners.map((owner: any) => {
                    const isExpanded = expandedFleet === owner.id;
                    return (
                      <div key={owner.id} className="border border-border/60 rounded-xl overflow-hidden">
                        {/* Owner summary row */}
                        <button
                          className="w-full flex items-center gap-3 p-4 hover:bg-muted/30 transition-colors text-left"
                          onClick={() => setExpandedFleet(isExpanded ? null : owner.id)}
                        >
                          <div className="h-9 w-9 rounded-lg bg-purple-50 flex items-center justify-center text-sm font-bold text-purple-700 shrink-0">
                            {(owner.name ?? "F").split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase()}
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="font-semibold text-sm truncate">{owner.name ?? "—"}</p>
                            <p className="text-xs text-muted-foreground">{owner.email} · {owner.phone}</p>
                          </div>
                          <div className="flex items-center gap-3 shrink-0">
                            <div className="text-center">
                              <p className="text-lg font-bold text-blue-600">{owner.driver_count ?? 0}</p>
                              <p className="text-[10px] text-muted-foreground">Drivers</p>
                            </div>
                            <div className="text-center">
                              <p className="text-lg font-bold text-green-600">{owner.vehicle_count ?? 0}</p>
                              <p className="text-[10px] text-muted-foreground">Vehicles</p>
                            </div>
                            <Badge className={`text-xs border ${owner.isVerified ? "bg-emerald-50 text-emerald-700 border-emerald-200" : "bg-gray-50 text-gray-600 border-gray-200"}`}>
                              {owner.isVerified ? "Verified" : "Unverified"}
                            </Badge>
                            <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${isExpanded ? "rotate-180" : ""}`} />
                          </div>
                        </button>

                        {/* Expanded detail */}
                        {isExpanded && (
                          <div className="border-t border-border/60 bg-muted/20 p-4 grid grid-cols-1 md:grid-cols-2 gap-4">
                            {/* Drivers */}
                            <div>
                              <p className="text-xs font-semibold text-muted-foreground mb-2 flex items-center gap-1">
                                <Users className="h-3 w-3" /> DRIVERS ({(owner.drivers ?? []).length})
                              </p>
                              {(owner.drivers ?? []).length === 0 ? (
                                <p className="text-xs text-muted-foreground italic">No drivers linked</p>
                              ) : (
                                <div className="space-y-1.5">
                                  {(owner.drivers ?? []).map((d: any) => (
                                    <div key={d.id} className="flex items-center gap-2 bg-white rounded-lg px-3 py-2 border border-border/40">
                                      <div className="h-6 w-6 rounded bg-blue-50 flex items-center justify-center text-[10px] font-bold text-blue-700">
                                        {(d.name ?? "D").split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase()}
                                      </div>
                                      <div className="flex-1 min-w-0">
                                        <p className="text-xs font-medium truncate">{d.name}</p>
                                        <p className="text-[10px] text-muted-foreground">{d.phone}</p>
                                      </div>
                                      {d.verified
                                        ? <CheckCircle className="h-3 w-3 text-emerald-500 shrink-0" />
                                        : <Clock className="h-3 w-3 text-muted-foreground shrink-0" />}
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>

                            {/* Vehicles */}
                            <div>
                              <p className="text-xs font-semibold text-muted-foreground mb-2 flex items-center gap-1">
                                <Truck className="h-3 w-3" /> VEHICLES ({(owner.vehicles ?? []).length})
                              </p>
                              {(owner.vehicles ?? []).length === 0 ? (
                                <p className="text-xs text-muted-foreground italic">No vehicles registered</p>
                              ) : (
                                <div className="space-y-1.5">
                                  {(owner.vehicles ?? []).map((v: any) => (
                                    <div key={v.id} className="flex items-center gap-2 bg-white rounded-lg px-3 py-2 border border-border/40">
                                      <div className="h-6 w-6 rounded bg-green-50 flex items-center justify-center">
                                        <Truck className="h-3 w-3 text-green-600" />
                                      </div>
                                      <div className="flex-1 min-w-0">
                                        <p className="text-xs font-medium">{v.plate_number}</p>
                                        <p className="text-[10px] text-muted-foreground capitalize">{v.truck_type}</p>
                                      </div>
                                      <Badge className={`text-[10px] border ${
                                        v.status === "available"
                                          ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                          : "bg-amber-50 text-amber-700 border-amber-200"
                                      }`}>
                                        {v.status}
                                      </Badge>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ── Documents tab ────────────────────────────────────────────── */}
        <TabsContent value="documents">
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="pb-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <CardTitle className="text-base flex items-center gap-2">
                  <FileText className="h-4 w-4 text-emerald-600" />
                  Driver Document Review
                </CardTitle>
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                    <Input
                      className="h-8 pl-8 pr-3 text-xs w-48 rounded-lg"
                      placeholder="Search driver…"
                      value={docSearch}
                      onChange={e => { setDocSearch(e.target.value); setDocPage(0); }}
                    />
                  </div>
                  <Select value={docStatusFilter} onValueChange={v => { setDocStatusFilter(v); setDocPage(0); }}>
                    <SelectTrigger className="h-8 w-[140px] text-xs rounded-lg"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="pending">Has Pending</SelectItem>
                      <SelectItem value="approved">Has Approved</SelectItem>
                      <SelectItem value="rejected">Has Rejected</SelectItem>
                      <SelectItem value="all">All Drivers</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {/* Driver detail dialog */}
              <Dialog open={driverDocOpen} onOpenChange={setDriverDocOpen}>
                <DialogContent className="max-w-2xl rounded-xl">
                  <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                      <div className="h-8 w-8 rounded-lg bg-emerald-50 flex items-center justify-center text-sm font-bold text-emerald-700">
                        {(selectedDriver?.name ?? "D").split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase()}
                      </div>
                      <div>
                        <p>{selectedDriver?.name ?? "Driver"}</p>
                        <p className="text-sm font-normal text-muted-foreground">{selectedDriver?.phone}</p>
                      </div>
                    </DialogTitle>
                  </DialogHeader>
                  <div className="pt-2 space-y-3">
                    {selectedDriver?.docs.some((d: any) => d.status === "pending") && (
                      <div className="flex justify-end">
                        <Button
                          size="sm"
                          className="h-7 px-3 text-xs gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700"
                          disabled={reviewDoc.isPending}
                          onClick={async () => {
                            const pending = selectedDriver!.docs.filter((d: any) => d.status === "pending");
                            for (const doc of pending) {
                              await reviewDoc.mutateAsync({ id: doc.id, action: "approve" });
                            }
                            toast({ title: `${pending.length} document(s) approved` });
                            setDriverDocOpen(false);
                          }}
                        >
                          {reviewDoc.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <ThumbsUp className="h-3 w-3" />}
                          Approve All Pending ({selectedDriver?.docs.filter((d: any) => d.status === "pending").length})
                        </Button>
                      </div>
                    )}
                    {DOC_TYPES.map(({ type, label }) => {
                      const doc = selectedDriver?.docs.find((d: any) => d.document_type === type);
                      return (
                        <div key={type} className="flex items-center gap-3 p-3 rounded-lg border border-border/60 bg-muted/20">
                          <div className="w-40 shrink-0">
                            <p className="text-xs font-medium text-foreground">{label}</p>
                            {doc && <p className="text-[10px] text-muted-foreground truncate max-w-[150px]">{doc.original_name}</p>}
                          </div>
                          {doc ? (
                            <>
                              <Badge className={`text-xs border shrink-0 ${
                                doc.status === "approved"
                                  ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                  : doc.status === "rejected"
                                  ? "bg-red-50 text-red-700 border-red-200"
                                  : "bg-amber-50 text-amber-700 border-amber-200"
                              }`}>
                                {doc.status}
                              </Badge>
                              {doc.status === "rejected" && doc.rejection_reason && (
                                <p className="text-[10px] text-red-500 max-w-[90px] truncate">{doc.rejection_reason}</p>
                              )}
                              <div className="ml-auto flex items-center gap-1.5">
                                <Button size="sm" variant="outline" className="h-7 px-2 text-xs gap-1 rounded-lg"
                                  onClick={() => handleViewDocument(doc)}>
                                  <Eye className="h-3 w-3" /> View
                                </Button>
                                {doc.status !== "approved" && (
                                  <Button size="sm" className="h-7 px-2 text-xs gap-1 rounded-lg bg-emerald-600 hover:bg-emerald-700"
                                    disabled={reviewDoc.isPending}
                                    onClick={() => reviewDoc.mutate({ id: doc.id, action: "approve" })}>
                                    <ThumbsUp className="h-3 w-3" /> Approve
                                  </Button>
                                )}
                                {doc.status !== "rejected" && (
                                  <Button size="sm" variant="outline"
                                    className="h-7 px-2 text-xs gap-1 rounded-lg text-red-600 border-red-200 hover:bg-red-50"
                                    onClick={() => {
                                      setDriverDocOpen(false);
                                      setRejectDocForm({ docId: doc.id, reason: "" });
                                      setRejectDocOpen(true);
                                    }}>
                                    <ThumbsDown className="h-3 w-3" /> Reject
                                  </Button>
                                )}
                              </div>
                            </>
                          ) : (
                            <span className="text-xs text-muted-foreground italic">Not uploaded</span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </DialogContent>
              </Dialog>

              {/* Reject reason dialog */}
              <Dialog open={rejectDocOpen} onOpenChange={setRejectDocOpen}>
                <DialogContent className="max-w-sm rounded-xl">
                  <DialogHeader><DialogTitle>Reject Document</DialogTitle></DialogHeader>
                  <div className="space-y-3 pt-2">
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Rejection reason</Label>
                      <Input
                        placeholder="e.g. Photo is blurry, document expired…"
                        value={rejectDocForm.reason}
                        onChange={e => setRejectDocForm(f => ({ ...f, reason: e.target.value }))}
                        className="rounded-lg"
                      />
                    </div>
                    <Button
                      className="w-full rounded-lg bg-red-600 hover:bg-red-700"
                      disabled={!rejectDocForm.reason.trim() || reviewDoc.isPending}
                      onClick={() => {
                        if (!rejectDocForm.docId) return;
                        reviewDoc.mutate({ id: rejectDocForm.docId, action: "reject", rejectionReason: rejectDocForm.reason });
                      }}
                    >
                      {reviewDoc.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                      Confirm Rejection
                    </Button>
                  </div>
                </DialogContent>
              </Dialog>

              {docsLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12 rounded-lg" />)}
                </div>
              ) : (
                <>
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b text-left">
                          <th className="pb-3 font-medium text-muted-foreground">Driver</th>
                          <th className="pb-3 font-medium text-muted-foreground">Submitted</th>
                          <th className="pb-3 font-medium text-muted-foreground">Approved</th>
                          <th className="pb-3 font-medium text-muted-foreground">Pending</th>
                          <th className="pb-3 font-medium text-muted-foreground">Rejected</th>
                          <th className="pb-3 font-medium text-muted-foreground">Verification</th>
                          <th className="pb-3 font-medium text-muted-foreground">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y">
                        {pagedGroups.map(g => {
                          const approved = g.docs.filter((d: any) => d.status === "approved").length;
                          const pending  = g.docs.filter((d: any) => d.status === "pending").length;
                          const rejected = g.docs.filter((d: any) => d.status === "rejected").length;
                          return (
                            <tr key={g.driverId} className="hover:bg-muted/30 transition-colors">
                              <td className="py-3">
                                <div className="flex items-center gap-2">
                                  <div className="h-7 w-7 rounded-md bg-emerald-50 flex items-center justify-center text-[10px] font-bold text-emerald-700">
                                    {g.name.split(" ").map((n: string) => n[0]).join("").slice(0, 2).toUpperCase()}
                                  </div>
                                  <div>
                                    <p className="font-medium text-xs">{g.name}</p>
                                    <p className="text-[11px] text-muted-foreground">{g.phone}</p>
                                  </div>
                                </div>
                              </td>
                              <td className="py-3 text-xs">{g.docs.length} / 5</td>
                              <td className="py-3 text-xs font-medium text-emerald-700">{approved}</td>
                              <td className="py-3">
                                {pending > 0
                                  ? <Badge className="text-xs border bg-amber-50 text-amber-700 border-amber-200">{pending} pending</Badge>
                                  : <span className="text-xs text-muted-foreground">—</span>}
                              </td>
                              <td className="py-3">
                                {rejected > 0
                                  ? <Badge className="text-xs border bg-red-50 text-red-700 border-red-200">{rejected} rejected</Badge>
                                  : <span className="text-xs text-muted-foreground">—</span>}
                              </td>
                              <td className="py-3">
                                {approved === 5
                                  ? <Badge className="text-xs border bg-emerald-50 text-emerald-700 border-emerald-200 gap-1"><CheckCircle className="h-3 w-3" />Verified</Badge>
                                  : <Badge className="text-xs border bg-gray-50 text-gray-600 border-gray-200">{approved}/5 approved</Badge>}
                              </td>
                              <td className="py-3">
                                <Button size="sm" variant="outline" className="h-7 px-3 text-xs rounded-lg"
                                  onClick={() => { setSelectedDriver(g); setDriverDocOpen(true); }}>
                                  Review
                                </Button>
                              </td>
                            </tr>
                          );
                        })}
                        {pagedGroups.length === 0 && (
                          <tr>
                            <td colSpan={7} className="py-10 text-center text-muted-foreground">
                              No drivers found{docSearch ? ` matching "${docSearch}"` : ""}
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                  {totalDocPages > 1 && (
                    <div className="flex items-center justify-between pt-4 border-t mt-4">
                      <p className="text-xs text-muted-foreground">
                        Showing {docPage * DOC_PAGE_SIZE + 1}–{Math.min((docPage + 1) * DOC_PAGE_SIZE, filteredGroups.length)} of {filteredGroups.length} drivers
                      </p>
                      <div className="flex items-center gap-1">
                        <Button variant="outline" size="sm" className="h-7 w-7 p-0 rounded-lg"
                          disabled={docPage === 0} onClick={() => setDocPage(p => p - 1)}>
                          <ChevronLeft className="h-3.5 w-3.5" />
                        </Button>
                        <span className="text-xs px-2">{docPage + 1} / {totalDocPages}</span>
                        <Button variant="outline" size="sm" className="h-7 w-7 p-0 rounded-lg"
                          disabled={docPage >= totalDocPages - 1} onClick={() => setDocPage(p => p + 1)}>
                          <ChevronRight className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="disputes">
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">Dispute Management</CardTitle>
              <Dialog open={resolveOpen} onOpenChange={setResolveOpen}>
                <DialogContent className="max-w-md rounded-xl">
                  <DialogHeader><DialogTitle>Resolve Dispute</DialogTitle></DialogHeader>
                  <div className="space-y-3 pt-2">
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Resolution</Label>
                      <Select value={resolveForm.resolution} onValueChange={v => setResolveForm(f => ({ ...f, resolution: v as any }))}>
                        <SelectTrigger className="rounded-lg"><SelectValue /></SelectTrigger>
                        <SelectContent>
                          <SelectItem value="investigating">Start Investigation</SelectItem>
                          <SelectItem value="release">Release to Driver</SelectItem>
                          <SelectItem value="refund">Full Refund to Shipper</SelectItem>
                          <SelectItem value="split">Split Amount</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1.5">
                      <Label className="text-sm font-medium">Admin Notes</Label>
                      <Input placeholder="Describe the resolution rationale..." value={resolveForm.adminNotes}
                        onChange={e => setResolveForm(f => ({ ...f, adminNotes: e.target.value }))} className="rounded-lg" />
                    </div>
                    {resolveForm.resolution === "split" && (
                      <div className="space-y-1.5">
                        <Label className="text-sm font-medium">Refund Amount (ETB)</Label>
                        <Input type="number" placeholder="Amount to refund to shipper" value={resolveForm.refundAmount}
                          onChange={e => setResolveForm(f => ({ ...f, refundAmount: e.target.value }))} className="rounded-lg" />
                      </div>
                    )}
                    <Button className="w-full rounded-lg" onClick={() => {
                      if (!resolveForm.disputeId || !resolveForm.resolution) return;
                      resolveDispute.mutate({
                        id: resolveForm.disputeId,
                        resolution: resolveForm.resolution,
                        adminNotes: resolveForm.adminNotes,
                        refundAmount: resolveForm.refundAmount ? Number(resolveForm.refundAmount) : undefined,
                      });
                    }} disabled={!resolveForm.disputeId || !resolveForm.resolution || resolveDispute.isPending}>
                      {resolveDispute.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                      Resolve Dispute
                    </Button>
                  </div>
                </DialogContent>
              </Dialog>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="pb-3 font-medium text-muted-foreground">ID</th>
                      <th className="pb-3 font-medium text-muted-foreground">Freight</th>
                      <th className="pb-3 font-medium text-muted-foreground">Reason</th>
                      <th className="pb-3 font-medium text-muted-foreground">Status</th>
                      <th className="pb-3 font-medium text-muted-foreground">Filed</th>
                      <th className="pb-3 font-medium text-muted-foreground">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {(disputesData?.disputes ?? []).slice(disputesPage * ADMIN_PAGE_SIZE, (disputesPage + 1) * ADMIN_PAGE_SIZE).map((d: any) => (
                      <tr key={d.id}>
                        <td className="py-3 font-medium">#{d.id}</td>
                        <td className="py-3">#{d.freightId}</td>
                        <td className="py-3">{d.reason}</td>
                        <td className="py-3">
                          <Badge className={`text-xs border ${d.status === "resolved" ? "bg-emerald-50 text-emerald-700 border-emerald-200" : d.status === "investigating" ? "bg-sky-50 text-sky-700 border-sky-200" : "bg-red-50 text-red-700 border-red-200"}`}>
                            {d.status}
                          </Badge>
                        </td>
                        <td className="py-3 text-muted-foreground text-xs">{new Date(d.createdAt).toLocaleDateString()}</td>
                        <td className="py-3">
                          {d.status === "open" && (
                            <Button size="sm" className="rounded-lg" onClick={() => {
                              setResolveForm({ disputeId: d.id, resolution: "", adminNotes: "", refundAmount: "" });
                              setResolveOpen(true);
                            }}>
                              Resolve
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                    {(!disputesData?.disputes || disputesData.disputes.length === 0) && (
                      <tr><td colSpan={6} className="py-6 text-center text-muted-foreground">No disputes</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
              <TablePager
                page={disputesPage}
                total={(disputesData?.disputes ?? []).length}
                pageSize={ADMIN_PAGE_SIZE}
                onPageChange={setDisputesPage}
              />
            </CardContent>
          </Card>
        </TabsContent>
        {/* ── Settings tab ──────────────────────────────────────────────── */}
        <TabsContent value="settings">
          <div className="max-w-xl space-y-6">
            <Card className="border-border/60 rounded-xl">
              <CardHeader className="flex flex-row items-center gap-2 pb-3">
                <Settings className="h-4 w-4 text-emerald-600" />
                <CardTitle className="text-base">Freight Pricing Rate (ETB / km / ton)</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  These rates are used when the AI engine is unavailable to estimate price ranges
                  shown to shippers when posting cargo.
                </p>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1">
                    <Label htmlFor="rate-min">Minimum rate</Label>
                    <Input
                      id="rate-min"
                      type="number"
                      min={1}
                      value={pricingForm.rate_min}
                      onChange={(e) => setPricingForm((f) => ({ ...f, rate_min: Number(e.target.value) }))}
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="rate-max">Maximum rate</Label>
                    <Input
                      id="rate-max"
                      type="number"
                      min={1}
                      value={pricingForm.rate_max}
                      onChange={(e) => setPricingForm((f) => ({ ...f, rate_max: Number(e.target.value) }))}
                    />
                  </div>
                </div>
                {pricingData && (
                  <p className="text-xs text-muted-foreground">
                    Current saved rates: {pricingData.rate_min} – {pricingData.rate_max} ETB / km / ton
                  </p>
                )}
                <Button
                  className="w-full rounded-lg"
                  disabled={savePricing.isPending}
                  onClick={() => savePricing.mutate(pricingForm)}
                >
                  {savePricing.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                  Save Pricing Rates
                </Button>
              </CardContent>
            </Card>

            <Card className="border-border/60 rounded-xl">
              <CardHeader className="flex flex-row items-center gap-2 pb-3">
                <MapPin className="h-4 w-4 text-purple-600" />
                <CardTitle className="text-base">Intra-city Moving Reference Rates (ETB / move)</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  Reference price range displayed to shippers for intra-city moving requests. Configure these in the backend settings.
                </p>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1">
                    <Label className="text-muted-foreground">Minimum rate</Label>
                    <div className="h-9 px-3 flex items-center rounded-md border bg-muted/40 text-sm text-muted-foreground">
                      {(pricingData as any)?.intracity_rate_min ?? "—"} ETB
                    </div>
                  </div>
                  <div className="space-y-1">
                    <Label className="text-muted-foreground">Maximum rate</Label>
                    <div className="h-9 px-3 flex items-center rounded-md border bg-muted/40 text-sm text-muted-foreground">
                      {(pricingData as any)?.intracity_rate_max ?? "—"} ETB
                    </div>
                  </div>
                </div>
                <p className="text-xs text-muted-foreground">These rates are read-only. Update via backend admin settings API.</p>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

      </Tabs>
        </main>
      </div>
    </div>
  );
}
