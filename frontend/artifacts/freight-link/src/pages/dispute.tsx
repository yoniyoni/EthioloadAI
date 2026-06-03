import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useLocation } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Link } from "wouter";
import { ArrowLeft, AlertTriangle, Shield, Loader2, FileText, CheckCircle, Lock, ChevronRight, MessageSquare } from "lucide-react";

export default function Dispute() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const [, setLocation] = useLocation();
  const freightId = Number(id);

  const [reason, setReason] = useState("");
  const [description, setDescription] = useState("");
  const [showForm, setShowForm] = useState(false);

  const { data: freight, isLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
  });

  const { data: payment } = useQuery({
    queryKey: ["payment", freightId],
    queryFn: () => api.get<any>(`/payments/${freightId}`),
    enabled: !isNaN(freightId),
  });

  const { data: disputes } = useQuery({
    queryKey: ["disputes", freightId],
    queryFn: () => api.get<{ disputes: any[] }>(`/disputes?freightId=${freightId}`),
    enabled: !isNaN(freightId),
  });

  const fileDispute = useMutation({
    mutationFn: () =>
      api.post("/disputes", {
        freightId,
        reason,
        description,
      }),
    onSuccess: () => {
      toast({ title: "Dispute filed!", description: "An admin will review your case." });
      setShowForm(false);
      setReason("");
      setDescription("");
      qc.invalidateQueries({ queryKey: ["disputes", freightId] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-2xl px-4 py-8">
        <Skeleton className="h-8 w-48 mb-6" />
        <Skeleton className="h-72 w-full rounded-xl" />
      </div>
    );
  }

  if (!freight) {
    return (
      <div className="container mx-auto max-w-2xl px-4 py-20 text-center">
        <p className="text-lg font-medium">Freight not found</p>
        <Link href="/freight"><Button variant="outline" className="mt-4 rounded-lg">Back</Button></Link>
      </div>
    );
  }

  const isOwner = user?.id === freight.shipperId;
  const isDriver = user?.id === freight.matchedDriverId;
  const isAdmin = user?.role === "admin";
  const canFile = (isOwner || isDriver) && !isAdmin;
  const activeDispute = disputes?.disputes?.find((d: any) => d.status === "open" || d.status === "under_review");

  const STATUS_COLORS: Record<string, string> = {
    open: "bg-sky-50 text-sky-700 border-sky-200",
    under_review: "bg-blue-50 text-blue-700 border-blue-200",
    resolved: "bg-emerald-50 text-emerald-700 border-emerald-200",
    closed: "bg-gray-50 text-gray-600 border-gray-200",
  };

  return (
    <div className="container mx-auto max-w-2xl px-4 py-8">
      <Link href="/freight">
        <Button variant="ghost" className="gap-2 mb-6 -ml-2 rounded-lg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Button>
      </Link>

      <Card className="border-border/60 rounded-xl overflow-hidden">
        {/* Header */}
        <div className="bg-[#0c1e4a] text-white p-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-white/10 flex items-center justify-center">
                <AlertTriangle className="h-4 w-4 text-amber-500" />
              </div>
              <div>
                <CardTitle className="text-lg text-white">Dispute Resolution</CardTitle>
                <p className="text-xs text-slate-400">Freight #{freight.id} · {freight.cargoType}</p>
              </div>
            </div>
            {activeDispute && (
              <Badge className={`border ${STATUS_COLORS[activeDispute.status] ?? "bg-gray-50 text-gray-700 border-gray-200"} bg-white/10 text-white border-white/20`}>
                {activeDispute.status}
              </Badge>
            )}
          </div>
        </div>

        <CardContent className="space-y-6 pt-6">
          {/* Escrow lock notice */}
          {activeDispute && payment && (
            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 space-y-1">
              <div className="flex items-center gap-2">
                <Lock className="h-4 w-4 text-amber-600" />
                <span className="font-medium text-amber-800 text-sm">Funds Locked</span>
              </div>
              <p className="text-xs text-amber-700">
                ETB {Number(payment.amount).toLocaleString()} is held in escrow pending dispute resolution.
              </p>
            </div>
          )}

          {/* Active dispute details */}
          {activeDispute && (
            <div className="space-y-3">
              <h3 className="text-sm font-medium">Active Dispute</h3>
              <div className="p-4 rounded-xl border border-border/60 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">Reason</span>
                  <span className="text-sm text-muted-foreground">{activeDispute.reason}</span>
                </div>
                <p className="text-sm text-muted-foreground">{activeDispute.description}</p>
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>Filed {new Date(activeDispute.createdAt).toLocaleDateString()}</span>
                  <span>ID: #{activeDispute.id}</span>
                </div>
              </div>
            </div>
          )}

          {/* File new dispute */}
          {canFile && !activeDispute && (
            <div className="space-y-3">
              {!showForm ? (
                <Button onClick={() => setShowForm(true)} variant="outline" className="w-full gap-2 rounded-lg">
                  <AlertTriangle className="h-4 w-4" /> File a Dispute
                </Button>
              ) : (
                <div className="space-y-3 p-4 rounded-xl border border-border/60">
                  <div className="space-y-1.5">
                    <Label className="text-sm font-medium">Reason</Label>
                    <Input
                      placeholder="e.g., late delivery, damaged cargo, non-payment"
                      value={reason}
                      onChange={e => setReason(e.target.value)}
                      className="rounded-lg"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-sm font-medium">Description</Label>
                    <Textarea
                      placeholder="Describe the issue in detail..."
                      value={description}
                      onChange={e => setDescription(e.target.value)}
                      rows={4}
                      className="rounded-lg"
                    />
                  </div>
                  <div className="flex gap-2">
                    <Button
                      onClick={() => fileDispute.mutate()}
                      disabled={!reason || fileDispute.isPending}
                      className="flex-1 rounded-lg"
                      variant="destructive"
                    >
                      {fileDispute.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <AlertTriangle className="h-4 w-4 mr-2" />}
                      File Dispute
                    </Button>
                    <Button variant="outline" onClick={() => setShowForm(false)} className="rounded-lg">Cancel</Button>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    Filing a dispute will lock the escrow funds until an admin resolves the case.
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Past disputes */}
          {disputes?.disputes && disputes.disputes.length > 0 && !activeDispute && (
            <div className="space-y-2">
              <h3 className="text-sm font-medium">Past Disputes</h3>
              {disputes.disputes.map((d: any) => (
                <div key={d.id} className="p-3 rounded-xl border border-border/60 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">{d.reason}</p>
                    <p className="text-xs text-muted-foreground">{d.status} · {new Date(d.createdAt).toLocaleDateString()}</p>
                  </div>
                  <Badge className={`border ${STATUS_COLORS[d.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>{d.status}</Badge>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
