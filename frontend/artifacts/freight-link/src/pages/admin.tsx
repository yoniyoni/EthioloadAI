import { useState } from "react";
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
import { Users, Package, Truck, CheckCircle, Clock, UserPlus, Loader2, Shield, Banknote, AlertTriangle, Lock, DollarSign, TrendingUp, BarChart3 } from "lucide-react";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, PieChart, Pie, Cell } from "recharts";

export default function Admin() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    name: "", email: "", phone: "", password: "", licenseNumber: "", nationalId: "", yearsExperience: "", role: "driver" as "driver" | "fleet_owner",
  });
  const [resolveOpen, setResolveOpen] = useState(false);
  const [resolveForm, setResolveForm] = useState({ disputeId: 0 as number | null, resolution: "" as "release" | "refund" | "split" | "investigating" | "", adminNotes: "", refundAmount: "" });

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ["admin-stats"],
    queryFn: () => api.get<any>("/admin/stats"),
  });

  const { data: usersData } = useQuery({
    queryKey: ["admin-users"],
    queryFn: () => api.get<any>("/users?limit=30"),
  });

  const { data: driversData } = useQuery({
    queryKey: ["admin-drivers"],
    queryFn: () => api.get<any>("/drivers?limit=30"),
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

  const ROLE_COLORS: Record<string, string> = {
    admin: "bg-red-50 text-red-700 border-red-200",
    shipper: "bg-blue-50 text-blue-700 border-blue-200",
    driver: "bg-emerald-50 text-emerald-700 border-emerald-200",
    fleet_owner: "bg-purple-50 text-purple-700 border-purple-200",
  };

  const STATUS_COLORS: Record<string, string> = {
    active: "bg-emerald-50 text-emerald-700 border-emerald-200",
    approved: "bg-blue-50 text-blue-700 border-blue-200",
    under_review: "bg-sky-50 text-sky-700 border-sky-200",
    submitted: "bg-purple-50 text-purple-700 border-purple-200",
    suspended: "bg-red-50 text-red-700 border-red-200",
  };

  const CHART_COLORS = ["#059669", "#0c1e4a", "#0ea5e9", "#dc2626", "#7c3aed", "#0891b2", "#ea580c"];

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">Admin Dashboard</h1>
        <p className="text-muted-foreground mt-1">Platform overview and management</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        {statsLoading ? (
          Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)
        ) : (
          <>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Total Users</p>
                  <p className="text-3xl font-bold mt-1">{stats?.users?.total ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-blue-50 flex items-center justify-center">
                  <Users className="h-5 w-5 text-blue-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Active Drivers</p>
                  <p className="text-3xl font-bold mt-1">{stats?.drivers?.active ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-emerald-50 flex items-center justify-center">
                  <Truck className="h-5 w-5 text-emerald-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Open Loads</p>
                  <p className="text-3xl font-bold mt-1">{stats?.freight?.posted ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-sky-50 flex items-center justify-center">
                  <Package className="h-5 w-5 text-sky-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Completed</p>
                  <p className="text-3xl font-bold mt-1">{stats?.freight?.completed ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-emerald-50 flex items-center justify-center">
                  <CheckCircle className="h-5 w-5 text-emerald-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Total Payments</p>
                  <p className="text-3xl font-bold mt-1">{stats?.payments?.total ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-sky-50 flex items-center justify-center">
                  <Banknote className="h-5 w-5 text-sky-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Escrow Held</p>
                  <p className="text-3xl font-bold mt-1">{stats?.payments?.escrowHeld ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-purple-50 flex items-center justify-center">
                  <Lock className="h-5 w-5 text-purple-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Platform Revenue</p>
                  <p className="text-3xl font-bold mt-1">{stats?.payments?.revenue?.toLocaleString?.() ?? stats?.payments?.revenue ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-emerald-50 flex items-center justify-center">
                  <DollarSign className="h-5 w-5 text-emerald-600" />
                </div>
              </div>
            </CardContent></Card>
            <Card className="border-border/60 rounded-xl"><CardContent className="pt-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-muted-foreground">Open Disputes</p>
                  <p className="text-3xl font-bold mt-1">{stats?.payments?.openDisputes ?? 0}</p>
                </div>
                <div className="h-10 w-10 rounded-lg bg-red-50 flex items-center justify-center">
                  <AlertTriangle className="h-5 w-5 text-red-600" />
                </div>
              </div>
            </CardContent></Card>
          </>
        )}
      </div>

      <Tabs defaultValue="drivers">
        <TabsList className="mb-4 flex-wrap rounded-lg">
          <TabsTrigger value="drivers">Drivers</TabsTrigger>
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="payments">Payments</TabsTrigger>
          <TabsTrigger value="escrow">Escrow</TabsTrigger>
          <TabsTrigger value="disputes">Disputes</TabsTrigger>
          <TabsTrigger value="analytics">Analytics</TabsTrigger>
        </TabsList>

        <TabsContent value="drivers">
          <Card className="border-border/60 rounded-xl">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-base">Driver Management</CardTitle>
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
                    {(driversData?.drivers ?? []).map((d: any) => (
                      <tr key={d.id} className="py-3">
                        <td className="py-3">
                          <div className="flex items-center gap-3">
                            <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center text-sm font-bold text-primary">
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
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="users">
          <Card className="border-border/60 rounded-xl">
            <CardHeader><CardTitle className="text-base">User Management</CardTitle></CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="pb-3 font-medium text-muted-foreground">Name</th>
                      <th className="pb-3 font-medium text-muted-foreground">Email</th>
                      <th className="pb-3 font-medium text-muted-foreground">Role</th>
                      <th className="pb-3 font-medium text-muted-foreground">Verified</th>
                      <th className="pb-3 font-medium text-muted-foreground">Joined</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {(usersData?.users ?? []).map((u: any) => (
                      <tr key={u.id}>
                        <td className="py-3 font-medium">{u.name}</td>
                        <td className="py-3 text-muted-foreground">{u.email}</td>
                        <td className="py-3">
                          <Badge className={`text-xs border ${ROLE_COLORS[u.role] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                            {u.role}
                          </Badge>
                        </td>
                        <td className="py-3">
                          {u.isVerified
                            ? <CheckCircle className="h-4 w-4 text-emerald-600" />
                            : <Clock className="h-4 w-4 text-muted-foreground" />}
                        </td>
                        <td className="py-3 text-muted-foreground text-xs">
                          {new Date(u.createdAt).toLocaleDateString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="payments">
          <Card className="border-border/60 rounded-xl">
            <CardHeader><CardTitle className="text-base">Payments</CardTitle></CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="pb-3 font-medium text-muted-foreground">ID</th>
                      <th className="pb-3 font-medium text-muted-foreground">Amount</th>
                      <th className="pb-3 font-medium text-muted-foreground">Status</th>
                      <th className="pb-3 font-medium text-muted-foreground">Escrow</th>
                      <th className="pb-3 font-medium text-muted-foreground">Shipper</th>
                      <th className="pb-3 font-medium text-muted-foreground">Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {(paymentsData?.payments ?? []).map((p: any) => (
                      <tr key={p.id}>
                        <td className="py-3 font-medium">#{p.id}</td>
                        <td className="py-3">{p.amount?.toLocaleString?.()} ETB</td>
                        <td className="py-3">
                          <Badge className={`text-xs border ${STATUS_COLORS[p.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>{p.status}</Badge>
                        </td>
                        <td className="py-3">
                          <Badge className={`text-xs border ${p.escrowStatus === "released" ? "bg-emerald-50 text-emerald-700 border-emerald-200" : p.escrowStatus === "disputed" ? "bg-red-50 text-red-700 border-red-200" : "bg-sky-50 text-sky-700 border-sky-200"}`}>
                            {p.escrowStatus}
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
            </CardContent>
          </Card>
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
                      <Bar dataKey="revenue" fill="#059669" name="Revenue (ETB)" />
                      <Bar dataKey="deliveries" fill="#0c1e4a" name="Deliveries" />
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
                        <Bar dataKey="count" fill="#0c1e4a" name="Shipments" />
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
                    {(disputesData?.disputes ?? []).map((d: any) => (
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
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
