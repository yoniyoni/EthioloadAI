import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useLocation } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  CheckCircle,
  XCircle,
  Download,
  FileImage,
  ArrowLeft,
  AlertTriangle,
  Clock,
  Loader2,
} from "lucide-react";
import { Link } from "wouter";

/**
 * proof-verification.tsx — Phase 7 Proof of Delivery verification page.
 *
 * Route: /freight/:freightId/proof
 *
 * Shows the shipper the submitted proof of delivery for their freight,
 * and provides Verify / Reject actions.
 *
 * Phase 7 API contract:
 *   GET  /api/proof-of-delivery/trips/{tripId}/proof    → ProofOfDeliveryResponse
 *   GET  /api/proof-of-delivery/{proofId}/download      → file download
 *   POST /api/proof-of-delivery/{proofId}/verify        { action: "VERIFY"|"REJECT", rejectionReason? }
 *
 * Security: server enforces shipper ownership. Only the freight's shipper can verify/reject.
 * The download endpoint is also authorization-gated server-side.
 */
export default function ProofVerification() {
  const { freightId, tripId } = useParams<{ freightId: string; tripId: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const [, setLocation] = useLocation();
  const [rejectionReason, setRejectionReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [downloading, setDownloading] = useState(false);
  async function downloadProof() {
    if (!proof) return;
    setDownloading(true);
    try {
      const blob = await api.getBlob(`/proof-of-delivery/${proof.id}/download`);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url; link.download = proof.fileName; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      toast({title: "Download failed", description: e instanceof Error ? e.message : "Try again.", variant: "destructive"});
    } finally { setDownloading(false); }
  }

  // ── Load proof ────────────────────────────────────────────────────────────
  const { data: proof, isLoading, error } = useQuery({
    queryKey: ["proof", "trip", tripId],
    queryFn: () => api.get<ProofOfDeliveryResponse>(`/proof-of-delivery/trips/${tripId}/proof`),
    enabled: !!tripId,
    refetchInterval: (query) => query.state.data?.status === "SUBMITTED" ? 15_000 : false,
  });

  // ── Verify mutation ───────────────────────────────────────────────────────
  const verifyMutation = useMutation({
    mutationFn: (action: "VERIFY" | "REJECT") =>
      api.post<ProofOfDeliveryResponse>(`/proof-of-delivery/${proof!.id}/verify`, {
        action,
        rejectionReason: action === "REJECT" ? rejectionReason.trim() : undefined,
      }),
    onSuccess: (updated) => {
      qc.invalidateQueries({ queryKey: ["proof", "trip", tripId] });
      qc.invalidateQueries({ queryKey: ["freight", freightId] });
      if (updated.status === "VERIFIED") {
        toast({ title: "Delivery Verified", description: "Settlement has been initiated." });
        setTimeout(() => setLocation(`/freight/${freightId}`), 1500);
      } else {
        toast({
          title: "Proof Rejected",
          description: "The driver has been notified to resubmit.",
          variant: "destructive",
        });
        setShowRejectForm(false);
      }
    },
    onError: (err: any) =>
      toast({ title: "Error", description: err?.message ?? "Unknown error", variant: "destructive" }),
  });

  if (isLoading) return <ProofSkeleton />;
  if (error || !proof)
    return (
      <div className="container max-w-2xl mx-auto py-8 px-4">
        <p className="text-destructive">Could not load proof of delivery.</p>
        <Link href={`/freight/${freightId}`}>
          <Button variant="outline" className="mt-4">← Back to Freight</Button>
        </Link>
      </div>
    );

  const statusColor = {
    SUBMITTED: "secondary",
    VERIFIED: "default",
    REJECTED: "destructive",
  }[proof.status] as any;

  return (
    <div className="container max-w-2xl mx-auto py-8 px-4 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link href={`/freight/${freightId}`}>
          <Button variant="ghost" size="icon"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div>
          <h1 className="text-2xl font-bold">Proof of Delivery</h1>
          <p className="text-muted-foreground text-sm">Trip #{tripId}</p>
        </div>
        <Badge variant={statusColor} className="ml-auto">{proof.status}</Badge>
      </div>

      {/* Proof details */}
      <Card>
        <CardHeader><CardTitle className="text-base">Submission Details</CardTitle></CardHeader>
        <CardContent className="space-y-3 text-sm">
          <Row label="File" value={proof.fileName} />
          <Row label="Submitted by Driver ID" value={String(proof.uploadedById)} />
          <Row label="Submitted at" value={new Date(proof.uploadedAt).toLocaleString()} />
          {proof.recipientName && <Row label="Recipient" value={proof.recipientName} />}
          {proof.note && <Row label="Note" value={proof.note} />}
          {proof.reviewedAt && (
            <Row label="Reviewed at" value={new Date(proof.reviewedAt).toLocaleString()} />
          )}
          {proof.rejectionReason && (
            <div className="rounded-md bg-destructive/10 p-3 border border-destructive/20">
              <p className="font-medium text-destructive">Rejection reason</p>
              <p className="text-destructive/80">{proof.rejectionReason}</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Download proof file */}
      <Card>
        <CardContent className="pt-6">
          <button
            onClick={() => void downloadProof()}
            disabled={downloading}
            className="flex items-center gap-2 text-primary hover:underline"
          >
            <FileImage className="h-5 w-5" />
            View / Download Proof File
            <Download className="h-4 w-4 ml-auto" />
          </button>
        </CardContent>
      </Card>

      {/* Actions — only for SUBMITTED proofs and the shipper */}
      {proof.status === "SUBMITTED" && user?.role === "shipper" && (
        <Card>
          <CardHeader><CardTitle className="text-base">Verify or Reject</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Review the proof file above. If the goods were received in good condition, verify
              the delivery. If there is an issue, reject and provide a reason.
            </p>

            {!showRejectForm ? (
              <div className="flex gap-3">
                <Button
                  className="flex-1"
                  onClick={() => verifyMutation.mutate("VERIFY")}
                  disabled={verifyMutation.isPending}
                >
                  {verifyMutation.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin mr-2" />
                  ) : (
                    <CheckCircle className="h-4 w-4 mr-2" />
                  )}
                  Verify Delivery
                </Button>
                <Button
                  variant="destructive"
                  className="flex-1"
                  onClick={() => setShowRejectForm(true)}
                  disabled={verifyMutation.isPending}
                >
                  <XCircle className="h-4 w-4 mr-2" />
                  Reject Proof
                </Button>
              </div>
            ) : (
              <div className="space-y-3">
                <div>
                  <Label htmlFor="reason">Rejection Reason *</Label>
                  <Textarea
                    id="reason"
                    value={rejectionReason}
                    onChange={(e) => setRejectionReason(e.target.value)}
                    placeholder="Describe the issue with the delivery (damaged goods, wrong items, etc.)"
                    className="mt-1"
                    rows={3}
                  />
                </div>
                <div className="flex gap-3">
                  <Button
                    variant="destructive"
                    className="flex-1"
                    onClick={() => verifyMutation.mutate("REJECT")}
                    disabled={verifyMutation.isPending || !rejectionReason.trim()}
                  >
                    {verifyMutation.isPending && (
                      <Loader2 className="h-4 w-4 animate-spin mr-2" />
                    )}
                    Confirm Rejection
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => setShowRejectForm(false)}
                    disabled={verifyMutation.isPending}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Verified state */}
      {proof.status === "VERIFIED" && (
        <Card className="bg-green-50 border-green-200">
          <CardContent className="pt-6 flex items-center gap-3">
            <CheckCircle className="h-8 w-8 text-green-600 shrink-0" />
            <div>
              <p className="font-semibold text-green-800">Delivery Verified</p>
              <p className="text-sm text-green-700">
                Settlement has been initiated for the carrier company.
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Pending state info */}
      {proof.status === "SUBMITTED" && user?.role !== "SHIPPER" && (
        <Card className="bg-amber-50 border-amber-200">
          <CardContent className="pt-6 flex items-center gap-3">
            <Clock className="h-6 w-6 text-amber-600 shrink-0" />
            <p className="text-sm text-amber-700">Awaiting shipper verification.</p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  );
}

function ProofSkeleton() {
  return (
    <div className="container max-w-2xl mx-auto py-8 px-4 space-y-4">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  );
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface ProofOfDeliveryResponse {
  id: number;
  tripId: number;
  bookingId: number;
  freightId: number;
  uploadedById: number;
  fileName: string;
  contentType: string;
  fileSize: number;
  recipientName?: string;
  note?: string;
  status: "SUBMITTED" | "VERIFIED" | "REJECTED";
  reviewedById?: number;
  reviewedAt?: string;
  rejectionReason?: string;
  uploadedAt: string;
  createdAt: string;
  updatedAt: string;
}
