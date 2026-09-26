import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useLocation, useParams } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Activity, ArrowLeft, CheckCircle, FileText, MapPin,
  Shield, Star, Truck, AlertTriangle, XCircle,
  Eye, ThumbsUp, ThumbsDown, Loader2,
} from "lucide-react";
import { EthioSidebar } from "@/components/ui/ethio-sidebar";
import { EthioTopbar } from "@/components/ui/ethio-topbar";

// ── Interfaces ─────────────────────────────────────────────────────────────────
interface Driver {
  id: number;
  fullName: string;
  phone: string;
  email: string | null;
  location: string | null;
  status: string;
  rating: number;
  totalDeliveries: number;
  yearsExperience: number;
  successRate: number;
  isAvailable: boolean;
  vehicles: Array<{ id: number; truckType: string; plateNumber: string; capacity: number; currentCity: string | null; availabilityStatus: string }>;
}

export const REQUIRED_KYC_TYPES = [
  { type: "LICENSE",              label: "Driver's License" },
  { type: "NATIONAL_ID",          label: "National ID / Kebele ID" },
  { type: "VEHICLE_REGISTRATION", label: "Vehicle Registration" },
  { type: "INSURANCE",            label: "Insurance Certificate" },
  { type: "TIN",                  label: "TIN Certificate" },
] as const;

interface DriverDocument {
  id: number;
  userId?: number | null;
  user_id?: number | null;
  driverName?: string | null;
  driver_name?: string | null;
  driverPhone?: string | null;
  driver_phone?: string | null;
  documentType?: string | null;
  document_type?: string | null;
  originalName?: string | null;
  original_name?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  status?: string | null;
  rejectionReason?: string | null;
  rejection_reason?: string | null;
  reviewedByUserId?: number | null;
  reviewedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  fileUrl?: string | null;
  file_url?: string | null;
}

interface DriverVehicle {
  id: number;
  userId?: number | null;
  truckType?: string | null;
  plateNumber?: string | null;
  capacity?: number | null;
  currentCity?: string | null;
  availabilityStatus?: string | null;
  approvalStatus?: "UNDER_REVIEW" | "APPROVED" | "REJECTED" | "SUSPENDED" | null;
  vehicleCategory?: string | null;
}

interface KycStatus {
  driverId: number;
  isActive?: boolean;
  active?: boolean;
  kycComplete: boolean;
  approvedCount: number;
  requiredCount: number;
  documentStatuses?: Record<string, string>;
}

// ── Component ──────────────────────────────────────────────────────────────────
export default function DriverDetail() {
  const { id } = useParams<{ id: string }>();
  const [, navigate] = useLocation();
  const { user } = useAuth();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const isAdmin = user?.role?.toLowerCase() === "admin";

  // Viewing document state (tracks doc ID being retrieved)
  const [viewingDocId, setViewingDocId] = useState<number | null>(null);
  // Rejection dialog state for individual document review
  const [rejectingDoc, setRejectingDoc] = useState<DriverDocument | null>(null);
  const [singleDocRejectReason, setSingleDocRejectReason] = useState("");
  // Rejection reason for overall driver KYC reject
  const [driverRejectReason, setDriverRejectReason] = useState("");
  const [showRejectInput, setShowRejectInput] = useState(false);

  // ── Queries ──────────────────────────────────────────────────────────────────
  const { data, isLoading } = useQuery({
    queryKey: ["driver-detail", id],
    queryFn: () => api.get<{ content: Driver[] }>("/drivers?size=100"),
  });

  const { data: documents = [], isLoading: documentsLoading } = useQuery<DriverDocument[]>({
    queryKey: ["driver-documents", id],
    queryFn: () => api.get<DriverDocument[]>(`/documents/driver/${id}`),
    enabled: Boolean(id) && isAdmin,
  });

  const { data: kycStatus, isLoading: kycLoading } = useQuery<KycStatus>({
    queryKey: ["driver-kyc", id],
    queryFn: () => api.get<KycStatus>(`/documents/kyc/${id}`),
    enabled: Boolean(id) && isAdmin,
  });

  const { data: allVehicles = [], isLoading: vehiclesLoading, isError: vehiclesError } = useQuery<DriverVehicle[]>({
    queryKey: ["admin-vehicles", id],
    queryFn: () => api.get<DriverVehicle[]>("/vehicles"),
    enabled: Boolean(id) && isAdmin,
  });

  const { data: bookings = [], isLoading: bookingsLoading } = useQuery<any[]>({
    queryKey: ["driver-bookings", id],
    queryFn: () => api.get<any[]>("/bookings"),
    enabled: Boolean(id) && isAdmin,
  });

  const { data: tripsPage, isLoading: tripsLoading } = useQuery<{ content?: any[] }>({
    queryKey: ["driver-trips", id],
    queryFn: () => api.get<{ content?: any[] }>("/trips?size=100"),
    enabled: Boolean(id) && isAdmin,
  });

  // ── Authenticated Document Viewing ───────────────────────────────────────────
  async function handleViewDocument(doc: DriverDocument) {
    try {
      setViewingDocId(doc.id);
      const fileUrl = doc.fileUrl || doc.file_url || `/driver/documents/${doc.id}/file`;
      const blob = await api.getBlob(fileUrl);
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, "_blank");
    } catch (err: any) {
      toast({
        title: "Could not load document file",
        description: err.message || "Failed to retrieve authenticated document file",
        variant: "destructive",
      });
    } finally {
      setViewingDocId(null);
    }
  }

  // ── Mutations ─────────────────────────────────────────────────────────────────

  // Vehicle approval (per-vehicle dropdown)
  const approvalMutation = useMutation({
    mutationFn: ({ vehicleId, status }: { vehicleId: number; status: string }) =>
      api.patch(`/vehicles/${vehicleId}/approval-status`, { status }),
    onSuccess: () => {
      toast({ title: "Vehicle approval status updated" });
      queryClient.invalidateQueries({ queryKey: ["admin-vehicles", id] });
    },
    onError: (error: Error) =>
      toast({ title: "Vehicle approval update failed", description: error.message, variant: "destructive" }),
  });

  // Per-document review (individual document approve/reject)
  const documentMutation = useMutation({
    mutationFn: ({ documentId, status, reason }: { documentId: number; status: "APPROVED" | "REJECTED"; reason?: string }) =>
      api.post(`/documents/${documentId}/review`, {
        status,
        rejectionReason: status === "REJECTED" ? (reason ?? "").trim() : null,
      }),
    onSuccess: () => {
      toast({ title: "Document review saved" });
      queryClient.invalidateQueries({ queryKey: ["driver-documents", id] });
      queryClient.invalidateQueries({ queryKey: ["driver-kyc", id] });
      queryClient.invalidateQueries({ queryKey: ["driver-detail", id] });
      queryClient.invalidateQueries({ queryKey: ["admin-drivers"] });
      queryClient.invalidateQueries({ queryKey: ["admin-docs"] });
    },
    onError: (error: Error) =>
      toast({ title: "Document review failed", description: error.message, variant: "destructive" }),
  });

  // ── Overall APPROVE DRIVER: POST /documents/kyc/{driverId}/approve-all ─────
  const approveDriverMutation = useMutation({
    mutationFn: () => api.post(`/documents/kyc/${id}/approve-all`, {}),
    onSuccess: () => {
      toast({ title: "Driver approved", description: "All documents approved. Driver account is now active." });
      queryClient.invalidateQueries({ queryKey: ["driver-documents", id] });
      queryClient.invalidateQueries({ queryKey: ["driver-kyc", id] });
      queryClient.invalidateQueries({ queryKey: ["driver-detail", id] });
      queryClient.invalidateQueries({ queryKey: ["admin-drivers"] });
      queryClient.invalidateQueries({ queryKey: ["admin-docs"] });
    },
    onError: (error: Error) =>
      toast({ title: "Driver approval failed", description: error.message, variant: "destructive" }),
  });

  // ── Overall REJECT DRIVER: reject all pending docs individually ───────────
  // Spring has no single "reject all" endpoint — call per-document reject for each pending doc.
  const rejectDriverMutation = useMutation({
    mutationFn: async (reason: string) => {
      const pending = documents.filter(d => (d.status ?? "PENDING").toUpperCase() === "PENDING");
      if (pending.length === 0) {
        throw new Error("No pending documents to reject.");
      }
      for (const doc of pending) {
        await api.post(`/documents/${doc.id}/review`, {
          status: "REJECTED",
          rejectionReason: reason,
        });
      }
    },
    onSuccess: () => {
      toast({ title: "Driver rejected", description: "All pending documents have been rejected." });
      setShowRejectInput(false);
      setDriverRejectReason("");
      queryClient.invalidateQueries({ queryKey: ["driver-documents", id] });
      queryClient.invalidateQueries({ queryKey: ["driver-kyc", id] });
      queryClient.invalidateQueries({ queryKey: ["driver-detail", id] });
      queryClient.invalidateQueries({ queryKey: ["admin-drivers"] });
      queryClient.invalidateQueries({ queryKey: ["admin-docs"] });
    },
    onError: (error: Error) =>
      toast({ title: "Driver rejection failed", description: error.message, variant: "destructive" }),
  });

  // ── Derived state ─────────────────────────────────────────────────────────────
  const driver = (Array.isArray(data?.content) ? data.content : []).find(
    (candidate) => candidate.id === Number(id),
  );
  const driverVehicles = allVehicles.filter((v) => Number(v.userId) === Number(id));
  const driverBookings = bookings.filter((b) => Number(b.driverId) === Number(id));
  const driverTrips = (tripsPage?.content ?? []).filter(
    (trip: any) => Number(trip.driver?.id ?? trip.driverId) === Number(id),
  );
  const accountActive = ["active", "approved"].includes((driver?.status ?? "").toLowerCase());
  const kycComplete = Boolean(kycStatus?.kycComplete);

  const pendingDocCount = documents.filter(d => (d.status ?? "PENDING").toUpperCase() === "PENDING").length;
  const hasAnyDoc = documents.length > 0;

  const requiredItems = REQUIRED_KYC_TYPES.map(({ type, label }) => {
    const doc = documents.find((d) => (d.documentType || d.document_type || "").toUpperCase() === type);
    return { type, label, doc };
  });

  const extraItems = documents.filter((d) => {
    const rawType = (d.documentType || d.document_type || "").toUpperCase();
    return !REQUIRED_KYC_TYPES.some((rt) => rt.type === rawType);
  });

  // ── Guard renders ─────────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <Skeleton className="h-80 w-full rounded-xl" />
      </div>
    );
  }
  if (!driver) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-20 text-center">
        <p>Driver not found</p>
        <Button variant="outline" className="mt-4" onClick={() => navigate("/admin?tab=drivers")}>
          Back to drivers
        </Button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#F8FAF8]">
      <EthioSidebar />
      <div className="ml-0 min-w-0 md:ml-[240px]">
        <EthioTopbar title="Driver Details" subtitle="Verified account and operational history" />
        <main className="mt-[60px] px-3 py-4 md:px-6 md:py-8">
          <div className="mx-auto max-w-5xl">
            <Button
              variant="ghost"
              className="mb-6 -ml-2 gap-2"
              onClick={() => navigate("/admin?tab=drivers")}
            >
              <ArrowLeft className="h-4 w-4" /> Back to drivers
            </Button>

            <Card className="rounded-xl border-border/60">
              {/* ── Header ── */}
              <CardHeader className="flex flex-row items-start justify-between gap-4">
                <div className="flex items-center gap-4">
                  <div className="h-16 w-16 rounded-xl bg-primary/10 flex items-center justify-center text-lg font-bold text-primary">
                    {driver.fullName.split(" ").map((p) => p[0]).join("")}
                  </div>
                  <div>
                    <CardTitle>{driver.fullName}</CardTitle>
                    <p className="text-sm text-muted-foreground mt-1">{driver.email ?? driver.phone}</p>
                  </div>
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  <Badge variant="outline">{driver.status}</Badge>
                  <Badge variant={kycComplete ? "default" : "secondary"}>
                    {kycLoading ? "KYC loading" : kycComplete ? "KYC approved" : "KYC pending"}
                  </Badge>
                </div>
              </CardHeader>

              <CardContent className="space-y-6">
                {/* ── Eligibility summary ── */}
                <section className="rounded-xl border border-border/60 bg-muted/20 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h2 className="font-semibold">Bidding eligibility</h2>
                      <p className="text-sm text-muted-foreground">
                        Driver KYC and vehicle approval are independent. Both must be met before a driver can bid.
                      </p>
                    </div>
                    <Badge variant={accountActive && kycComplete ? "default" : "secondary"}>
                      {accountActive && kycComplete ? "Eligible" : "Not eligible"}
                    </Badge>
                  </div>
                  <div className="grid gap-2 sm:grid-cols-3 mt-4 text-sm">
                    <div className="flex items-center gap-2">
                      <CheckCircle className={accountActive ? "h-4 w-4 text-emerald-600" : "h-4 w-4 text-muted-foreground"} />
                      Account active: {accountActive ? "Yes" : "No"}
                    </div>
                    <div className="flex items-center gap-2">
                      <Shield className={kycComplete ? "h-4 w-4 text-emerald-600" : "h-4 w-4 text-muted-foreground"} />
                      KYC complete: {kycComplete ? "Yes" : "No"}
                    </div>
                    <div className="flex items-center gap-2">
                      <Truck className={driverVehicles.length ? "h-4 w-4 text-emerald-600" : "h-4 w-4 text-muted-foreground"} />
                      Vehicles: {vehiclesLoading ? "Loading" : driverVehicles.length}
                    </div>
                  </div>
                </section>

                {/* ── Tabs ── */}
                <Tabs defaultValue="documents">
                  <TabsList className="grid w-full grid-cols-4">
                    <TabsTrigger value="profile">Profile</TabsTrigger>
                    <TabsTrigger value="vehicles">Vehicles</TabsTrigger>
                    <TabsTrigger value="documents">
                      Documents
                      {pendingDocCount > 0 && (
                        <Badge variant="secondary" className="ml-1 h-5 px-1.5 text-xs">
                          {pendingDocCount}
                        </Badge>
                      )}
                    </TabsTrigger>
                    <TabsTrigger value="activity">Activity</TabsTrigger>
                  </TabsList>

                  {/* ── Profile tab ── */}
                  <TabsContent value="profile" className="mt-4">
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                      <div>
                        <Star className="h-4 w-4 text-amber-500 fill-amber-500" />
                        <p className="font-semibold mt-1">{driver.rating ? driver.rating.toFixed(1) : "No rating"}</p>
                        <p className="text-muted-foreground">Rating</p>
                      </div>
                      <div>
                        <CheckCircle className="h-4 w-4 text-emerald-600" />
                        <p className="font-semibold mt-1">{driver.totalDeliveries}</p>
                        <p className="text-muted-foreground">Deliveries</p>
                      </div>
                      <div>
                        <Shield className="h-4 w-4 text-primary" />
                        <p className="font-semibold mt-1">{driver.yearsExperience} yrs</p>
                        <p className="text-muted-foreground">Experience</p>
                      </div>
                      <div>
                        <MapPin className="h-4 w-4 text-primary" />
                        <p className="font-semibold mt-1">{driver.location ?? "Not set"}</p>
                        <p className="text-muted-foreground">Location</p>
                      </div>
                    </div>
                    <div className="grid gap-3 md:grid-cols-2 mt-6 text-sm">
                      <div className="rounded-lg border p-4">
                        <p className="text-muted-foreground">Email</p>
                        <p className="font-medium mt-1">{driver.email ?? "Not set"}</p>
                      </div>
                      <div className="rounded-lg border p-4">
                        <p className="text-muted-foreground">Phone</p>
                        <p className="font-medium mt-1">{driver.phone}</p>
                      </div>
                    </div>
                  </TabsContent>

                  {/* ── Vehicles tab ── */}
                  <TabsContent value="vehicles" className="mt-4">
                    <p className="mb-3 text-xs text-muted-foreground">
                      Vehicle approval is separate from driver KYC. Only APPROVED + available vehicles may be used for bidding.
                    </p>
                    {vehiclesLoading ? (
                      <Skeleton className="h-24 w-full rounded-lg" />
                    ) : vehiclesError ? (
                      <p className="text-sm text-destructive">Vehicle data could not be loaded.</p>
                    ) : (
                      <div className="space-y-2">
                        {driverVehicles.length ? (
                          driverVehicles.map((vehicle) => {
                            const approval = vehicle.approvalStatus ?? "UNDER_REVIEW";
                            return (
                              <div
                                key={vehicle.id}
                                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3 text-sm"
                              >
                                <span className="flex items-center gap-2">
                                  <Truck className="h-4 w-4" />
                                  <span>
                                    <span className="font-medium">{(vehicle.truckType ?? "Vehicle").replace(/_/g, " ")}</span>
                                    <span className="block text-xs text-muted-foreground">{vehicle.plateNumber ?? "Plate not set"}</span>
                                  </span>
                                </span>
                                <span className="flex flex-wrap items-center gap-2 text-muted-foreground">
                                  {vehicle.capacity != null ? `${vehicle.capacity}t` : "Capacity not set"} · {vehicle.currentCity ?? "City not set"}
                                  <select
                                    value={approval}
                                    onChange={(e) => approvalMutation.mutate({ vehicleId: vehicle.id, status: e.target.value })}
                                    disabled={approvalMutation.isPending}
                                    aria-label={`Approval status for ${vehicle.plateNumber ?? vehicle.id}`}
                                    className="h-8 rounded-md border border-input bg-background px-2 text-xs"
                                  >
                                    <option value="UNDER_REVIEW">Under Review</option>
                                    <option value="APPROVED">Approved</option>
                                    <option value="REJECTED">Rejected</option>
                                    <option value="SUSPENDED">Suspended</option>
                                  </select>
                                  <Badge variant="outline">Availability: {vehicle.availabilityStatus ?? "unknown"}</Badge>
                                </span>
                              </div>
                            );
                          })
                        ) : (
                          <p className="text-sm text-muted-foreground">No vehicles registered for this driver.</p>
                        )}
                      </div>
                    )}
                  </TabsContent>

                  {/* ── Documents tab ── */}
                  <TabsContent value="documents" className="mt-4 space-y-4">
                    {documentsLoading ? (
                      <Skeleton className="h-24 w-full rounded-lg" />
                    ) : (
                      <>
                        {/* Document list — 5 Required KYC types + any extra uploaded docs */}
                        <div className="space-y-3">
                          {requiredItems.map(({ type, label, doc }) => {
                            if (!doc) {
                              return (
                                <div
                                  key={type}
                                  className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-dashed border-border/70 bg-muted/10 p-3 text-sm"
                                >
                                  <span className="flex items-center gap-2.5 min-w-0">
                                    <FileText className="h-4 w-4 text-muted-foreground/60 shrink-0" />
                                    <span className="min-w-0">
                                      <span className="font-medium text-foreground/80">{label}</span>
                                      <span className="block text-xs text-muted-foreground italic">
                                        Required KYC document not uploaded
                                      </span>
                                    </span>
                                  </span>
                                  <Badge variant="outline" className="text-xs text-muted-foreground border-dashed">
                                    Not Uploaded
                                  </Badge>
                                </div>
                              );
                            }

                            const docStatus = (doc.status ?? "PENDING").toUpperCase();
                            const isApproved = docStatus === "APPROVED";
                            const isRejected = docStatus === "REJECTED";
                            const originalFileName = doc.originalName || doc.original_name;
                            const rejectionReason = doc.rejectionReason || doc.rejection_reason;
                            const reviewedAt = doc.reviewedAt ? new Date(doc.reviewedAt).toLocaleString() : null;
                            const reviewerInfo = doc.reviewedByUserId ? `Reviewer #${doc.reviewedByUserId}` : null;

                            return (
                              <div
                                key={doc.id}
                                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3 text-sm bg-card hover:bg-muted/10 transition-colors"
                              >
                                <span className="flex items-start gap-2.5 min-w-0">
                                  <FileText className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" />
                                  <span className="min-w-0 space-y-0.5">
                                    <span className="font-medium text-foreground block">
                                      {label}
                                    </span>
                                    {originalFileName && (
                                      <span className="block text-xs text-muted-foreground truncate max-w-sm">
                                        {originalFileName}
                                        {doc.fileSize ? ` · ${Math.round(doc.fileSize / 1024)} KB` : ""}
                                      </span>
                                    )}
                                    {rejectionReason && (
                                      <span className="block text-xs text-destructive font-medium">
                                        Reason: {rejectionReason}
                                      </span>
                                    )}
                                    {(reviewedAt || reviewerInfo) && (
                                      <span className="block text-[11px] text-muted-foreground">
                                        {[reviewedAt, reviewerInfo].filter(Boolean).join(" · ")}
                                      </span>
                                    )}
                                  </span>
                                </span>

                                <div className="flex flex-wrap items-center gap-2 shrink-0">
                                  <Badge
                                    className={`text-xs border shrink-0 ${
                                      isApproved
                                        ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                        : isRejected
                                        ? "bg-red-50 text-red-700 border-red-200"
                                        : "bg-amber-50 text-amber-700 border-amber-200"
                                    }`}
                                  >
                                    {docStatus}
                                  </Badge>

                                  <Button
                                    size="sm"
                                    variant="outline"
                                    className="h-8 px-2.5 text-xs gap-1.5 rounded-lg"
                                    disabled={viewingDocId === doc.id}
                                    onClick={() => handleViewDocument(doc)}
                                  >
                                    {viewingDocId === doc.id ? (
                                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                    ) : (
                                      <Eye className="h-3.5 w-3.5" />
                                    )}
                                    View
                                  </Button>

                                  {isAdmin && !isApproved && (
                                    <Button
                                      size="sm"
                                      className="h-8 px-2.5 text-xs gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white"
                                      disabled={documentMutation.isPending}
                                      onClick={() => documentMutation.mutate({ documentId: doc.id, status: "APPROVED" })}
                                    >
                                      <ThumbsUp className="h-3.5 w-3.5" /> Approve
                                    </Button>
                                  )}

                                  {isAdmin && !isRejected && (
                                    <Button
                                      size="sm"
                                      variant="outline"
                                      className="h-8 px-2.5 text-xs gap-1.5 rounded-lg text-red-600 border-red-200 hover:bg-red-50"
                                      disabled={documentMutation.isPending}
                                      onClick={() => {
                                        setRejectingDoc(doc);
                                        setSingleDocRejectReason("");
                                      }}
                                    >
                                      <ThumbsDown className="h-3.5 w-3.5" /> Reject
                                    </Button>
                                  )}
                                </div>
                              </div>
                            );
                          })}

                          {extraItems.map((doc) => {
                            const docStatus = (doc.status ?? "PENDING").toUpperCase();
                            const isApproved = docStatus === "APPROVED";
                            const isRejected = docStatus === "REJECTED";
                            const typeLabel = (doc.documentType || doc.document_type || "Document").replace(/_/g, " ");
                            const originalFileName = doc.originalName || doc.original_name;
                            const rejectionReason = doc.rejectionReason || doc.rejection_reason;
                            const reviewedAt = doc.reviewedAt ? new Date(doc.reviewedAt).toLocaleString() : null;
                            const reviewerInfo = doc.reviewedByUserId ? `Reviewer #${doc.reviewedByUserId}` : null;

                            return (
                              <div
                                key={doc.id}
                                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3 text-sm bg-card hover:bg-muted/10 transition-colors"
                              >
                                <span className="flex items-start gap-2.5 min-w-0">
                                  <FileText className="h-4 w-4 text-muted-foreground shrink-0 mt-0.5" />
                                  <span className="min-w-0 space-y-0.5">
                                    <span className="font-medium text-foreground capitalize block">
                                      {typeLabel}
                                    </span>
                                    {originalFileName && (
                                      <span className="block text-xs text-muted-foreground truncate max-w-sm">
                                        {originalFileName}
                                        {doc.fileSize ? ` · ${Math.round(doc.fileSize / 1024)} KB` : ""}
                                      </span>
                                    )}
                                    {rejectionReason && (
                                      <span className="block text-xs text-destructive font-medium">
                                        Reason: {rejectionReason}
                                      </span>
                                    )}
                                    {(reviewedAt || reviewerInfo) && (
                                      <span className="block text-[11px] text-muted-foreground">
                                        {[reviewedAt, reviewerInfo].filter(Boolean).join(" · ")}
                                      </span>
                                    )}
                                  </span>
                                </span>

                                <div className="flex flex-wrap items-center gap-2 shrink-0">
                                  <Badge
                                    className={`text-xs border shrink-0 ${
                                      isApproved
                                        ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                        : isRejected
                                        ? "bg-red-50 text-red-700 border-red-200"
                                        : "bg-amber-50 text-amber-700 border-amber-200"
                                    }`}
                                  >
                                    {docStatus}
                                  </Badge>

                                  <Button
                                    size="sm"
                                    variant="outline"
                                    className="h-8 px-2.5 text-xs gap-1.5 rounded-lg"
                                    disabled={viewingDocId === doc.id}
                                    onClick={() => handleViewDocument(doc)}
                                  >
                                    {viewingDocId === doc.id ? (
                                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                    ) : (
                                      <Eye className="h-3.5 w-3.5" />
                                    )}
                                    View
                                  </Button>

                                  {isAdmin && !isApproved && (
                                    <Button
                                      size="sm"
                                      className="h-8 px-2.5 text-xs gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white"
                                      disabled={documentMutation.isPending}
                                      onClick={() => documentMutation.mutate({ documentId: doc.id, status: "APPROVED" })}
                                    >
                                      <ThumbsUp className="h-3.5 w-3.5" /> Approve
                                    </Button>
                                  )}

                                  {isAdmin && !isRejected && (
                                    <Button
                                      size="sm"
                                      variant="outline"
                                      className="h-8 px-2.5 text-xs gap-1.5 rounded-lg text-red-600 border-red-200 hover:bg-red-50"
                                      disabled={documentMutation.isPending}
                                      onClick={() => {
                                        setRejectingDoc(doc);
                                        setSingleDocRejectReason("");
                                      }}
                                    >
                                      <ThumbsDown className="h-3.5 w-3.5" /> Reject
                                    </Button>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>

                        {/* ── Overall KYC decision ── */}
                        {isAdmin && (
                          <div className="rounded-xl border border-border/60 bg-muted/20 p-4 space-y-3">
                            <div>
                              <h3 className="font-semibold text-sm">KYC Decision</h3>
                              <p className="text-xs text-muted-foreground mt-0.5">
                                {kycComplete
                                  ? "This driver's KYC is complete and the account is active."
                                  : `${kycStatus?.approvedCount ?? 0} of ${kycStatus?.requiredCount ?? 5} documents approved.`}
                                {" "}Approving sets all pending documents to APPROVED and activates the driver.
                                Rejecting marks all pending documents as REJECTED.
                              </p>
                            </div>

                            {kycComplete ? (
                              <div className="flex items-center gap-2 text-sm text-emerald-700 font-medium">
                                <CheckCircle className="h-4 w-4" />
                                Driver KYC approved and account active.
                              </div>
                            ) : (
                              <>
                                {!showRejectInput ? (
                                  <div className="flex flex-wrap gap-2">
                                    <Button
                                      size="sm"
                                      className="bg-emerald-600 hover:bg-emerald-700 text-white gap-1.5"
                                      onClick={() => approveDriverMutation.mutate()}
                                      disabled={approveDriverMutation.isPending || !hasAnyDoc}
                                    >
                                      <CheckCircle className="h-4 w-4" />
                                      {approveDriverMutation.isPending ? "Approving…" : "Approve Driver"}
                                    </Button>
                                    <Button
                                      size="sm"
                                      variant="destructive"
                                      className="gap-1.5"
                                      onClick={() => setShowRejectInput(true)}
                                      disabled={pendingDocCount === 0}
                                    >
                                      <XCircle className="h-4 w-4" />
                                      Reject Driver
                                    </Button>
                                  </div>
                                ) : (
                                  <div className="space-y-2">
                                    <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 flex items-start gap-2 text-xs text-destructive">
                                      <AlertTriangle className="h-4 w-4 mt-0.5 shrink-0" />
                                      Rejecting will mark all pending documents as REJECTED and keep the driver account inactive.
                                    </div>
                                    <input
                                      type="text"
                                      placeholder="Rejection reason (required)"
                                      value={driverRejectReason}
                                      onChange={e => setDriverRejectReason(e.target.value)}
                                      className="w-full h-9 rounded-md border border-input bg-background px-3 text-sm"
                                    />
                                    <div className="flex gap-2">
                                      <Button
                                        size="sm"
                                        variant="destructive"
                                        disabled={!driverRejectReason.trim() || rejectDriverMutation.isPending}
                                        onClick={() => rejectDriverMutation.mutate(driverRejectReason.trim())}
                                      >
                                        {rejectDriverMutation.isPending ? "Rejecting…" : "Confirm Rejection"}
                                      </Button>
                                      <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => { setShowRejectInput(false); setDriverRejectReason(""); }}
                                      >
                                        Cancel
                                      </Button>
                                    </div>
                                  </div>
                                )}
                              </>
                            )}
                          </div>
                        )}
                      </>
                    )}
                  </TabsContent>

                  {/* ── Activity tab ── */}
                  <TabsContent value="activity" className="mt-4">
                    <div className="grid gap-3 sm:grid-cols-3 mb-4">
                      <div className="rounded-lg border p-4">
                        <Activity className="h-4 w-4 text-primary" />
                        <p className="text-2xl font-semibold mt-2">{driverBookings.length}</p>
                        <p className="text-sm text-muted-foreground">Bookings</p>
                      </div>
                      <div className="rounded-lg border p-4">
                        <Truck className="h-4 w-4 text-primary" />
                        <p className="text-2xl font-semibold mt-2">{driverTrips.length}</p>
                        <p className="text-sm text-muted-foreground">Trips</p>
                      </div>
                      <div className="rounded-lg border p-4">
                        <FileText className="h-4 w-4 text-primary" />
                        <p className="text-2xl font-semibold mt-2">{documents.length}</p>
                        <p className="text-sm text-muted-foreground">Documents</p>
                      </div>
                    </div>
                    {bookingsLoading || tripsLoading ? (
                      <Skeleton className="h-20 w-full rounded-lg" />
                    ) : driverBookings.length === 0 && driverTrips.length === 0 ? (
                      <p className="text-sm text-muted-foreground">No booking or trip activity recorded.</p>
                    ) : (
                      <div className="space-y-2">
                        {driverBookings.map((booking: any) => (
                          <div key={`booking-${booking.id}`} className="rounded-lg border p-3 text-sm">
                            Booking #{booking.id} · {booking.bookingStatus} · ETB {Number(booking.estimatedPrice ?? 0).toLocaleString()}
                          </div>
                        ))}
                        {driverTrips.map((trip: any) => (
                          <div key={`trip-${trip.id}`} className="rounded-lg border p-3 text-sm">
                            Trip #{trip.id} · {trip.tripStatus ?? trip.status ?? "unknown"}
                          </div>
                        ))}
                      </div>
                    )}
                  </TabsContent>
                </Tabs>

                {/* ── Reject Single Document Dialog ── */}
                <Dialog open={!!rejectingDoc} onOpenChange={(open) => { if (!open) setRejectingDoc(null); }}>
                  <DialogContent className="max-w-md rounded-xl">
                    <DialogHeader>
                      <DialogTitle>Reject Document</DialogTitle>
                    </DialogHeader>
                    <div className="space-y-4 pt-2">
                      <p className="text-xs text-muted-foreground">
                        Please provide a reason for rejecting{" "}
                        <span className="font-semibold text-foreground">
                          {rejectingDoc?.originalName || rejectingDoc?.original_name || (rejectingDoc?.documentType ?? "document").replace(/_/g, " ")}
                        </span>
                        .
                      </p>
                      <div className="space-y-1.5">
                        <Label htmlFor="single-reject-reason" className="text-xs font-medium">Rejection reason (required)</Label>
                        <Input
                          id="single-reject-reason"
                          placeholder="e.g. Photo is blurry, document expired, name mismatch…"
                          value={singleDocRejectReason}
                          onChange={(e) => setSingleDocRejectReason(e.target.value)}
                          className="rounded-lg text-sm"
                        />
                      </div>
                      <div className="flex justify-end gap-2 pt-2">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setRejectingDoc(null)}
                        >
                          Cancel
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          disabled={!singleDocRejectReason.trim() || documentMutation.isPending}
                          onClick={() => {
                            if (!rejectingDoc) return;
                            documentMutation.mutate({
                              documentId: rejectingDoc.id,
                              status: "REJECTED",
                              reason: singleDocRejectReason.trim(),
                            });
                            setRejectingDoc(null);
                          }}
                        >
                          {documentMutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin mr-1.5" /> : null}
                          Confirm Rejection
                        </Button>
                      </div>
                    </div>
                  </DialogContent>
                </Dialog>
              </CardContent>
            </Card>
          </div>
        </main>
      </div>
    </div>
  );
}
