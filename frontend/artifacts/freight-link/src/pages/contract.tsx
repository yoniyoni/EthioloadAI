import { useQuery } from "@tanstack/react-query";
import { useParams } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Link } from "wouter";
import { ArrowLeft, FileText, CheckCircle, Truck, User, MapPin, Banknote, Calendar, Shield, ChevronRight, Stamp, Download } from "lucide-react";

export default function Contract() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const freightId = Number(id);

  const { data: contract, isLoading } = useQuery({
    queryKey: ["contract", freightId],
    queryFn: () => api.get<any>(`/contracts/${freightId}`),
    enabled: !isNaN(freightId),
  });

  if (isLoading) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-8">
        <Skeleton className="h-8 w-48 mb-6" />
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    );
  }

  if (!contract) {
    return (
      <div className="container mx-auto max-w-3xl px-4 py-20 text-center">
        <FileText className="h-12 w-12 mx-auto mb-3 opacity-40" />
        <p className="text-lg font-medium">No contract found</p>
        <p className="text-sm text-muted-foreground mt-1">Contracts are generated after payment is confirmed.</p>
        <Link href="/freight"><Button variant="outline" className="mt-4 rounded-lg">Back</Button></Link>
      </div>
    );
  }

  const STATUS_COLORS: Record<string, string> = {
    draft: "bg-gray-50 text-gray-600 border-gray-200",
    active: "bg-blue-50 text-blue-700 border-blue-200",
    completed: "bg-emerald-50 text-emerald-700 border-emerald-200",
    cancelled: "bg-red-50 text-red-700 border-red-200",
  };

  return (
    <div className="container mx-auto max-w-3xl px-4 py-8">
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
                <FileText className="h-4 w-4 text-emerald-400" />
              </div>
              <div>
                <CardTitle className="text-lg text-white">Freight Contract</CardTitle>
                <p className="text-xs text-slate-400">Contract #{contract.id} · Freight #{contract.freightId}</p>
              </div>
            </div>
            <Badge className={`${STATUS_COLORS[contract.status] ?? "bg-gray-50 text-gray-700 border-gray-200"} bg-white/10 text-white border-white/20`}>
              {contract.status}
            </Badge>
          </div>
          <div className="flex items-center gap-2 mt-4">
            <div className="h-2 w-2 rounded-full bg-emerald-400" />
            <span className="text-xs text-emerald-400">Escrow: Active Payment held in secure escrow</span>
          </div>
        </div>

        <CardContent className="space-y-6 pt-6">
          {/* Parties */}
          <div className="grid grid-cols-2 gap-4">
            <div className="p-4 rounded-xl border border-border/60 bg-emerald-50/50 space-y-1">
              <div className="flex items-center gap-2 text-sm font-medium text-emerald-800">
                <User className="h-4 w-4" /> Shipper
              </div>
              <p className="text-sm font-semibold text-foreground">{contract.shipper?.name ?? "Unknown"}</p>
              <p className="text-xs text-muted-foreground">{contract.shipper?.email ?? "No email"}</p>
            </div>
            <div className="p-4 rounded-xl border border-border/60 bg-blue-50/50 space-y-1">
              <div className="flex items-center gap-2 text-sm font-medium text-blue-800">
                <Truck className="h-4 w-4" /> Driver
              </div>
              <p className="text-sm font-semibold text-foreground">{contract.driver?.name ?? "Unknown"}</p>
              <p className="text-xs text-muted-foreground">{contract.driver?.email ?? "No email"}</p>
            </div>
          </div>

          <Separator />

          {/* Details */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <MapPin className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">Route</span>
            </div>
            <div className="flex items-center justify-between p-3 rounded-xl border border-border/60 bg-muted/30">
              <span className="text-sm font-medium">{contract.freight?.pickupLocation ?? "-"}</span>
              <div className="flex items-center gap-2">
                <div className="h-px w-8 bg-muted-foreground/30" />
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
                <div className="h-px w-8 bg-muted-foreground/30" />
              </div>
              <span className="text-sm font-medium">{contract.freight?.deliveryLocation ?? "-"}</span>
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Banknote className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">Agreed Amount</span>
            </div>
            <div className="p-4 rounded-xl border border-emerald-200 bg-emerald-50">
              <p className="text-2xl font-bold text-emerald-700">ETB {Number(contract.agreedPrice).toLocaleString()}</p>
              <p className="text-xs text-emerald-600">Payment held in escrow until delivery confirmed</p>
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">Deadline</span>
            </div>
            <p className="text-sm p-3 rounded-xl border border-border/60 bg-muted/30">
              {contract.deadline ? new Date(contract.deadline).toLocaleDateString() : "Not specified"}
            </p>
          </div>

          <Separator />

          {/* Status timeline */}
          <div className="space-y-3">
            <p className="text-sm font-medium">Contract Status</p>
            <div className="space-y-2">
              {[
                { label: "Contract Generated", icon: FileText, status: contract.status !== "draft" },
                { label: "Payment in Escrow", icon: Banknote, status: contract.paymentStatus !== "pending" },
                { label: "Delivery Confirmed", icon: CheckCircle, status: contract.status === "completed" },
              ].map((item, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className={`h-6 w-6 rounded-full flex items-center justify-center ${item.status ? "bg-emerald-500" : "bg-gray-200"}`}>
                    <item.icon className={`h-3 w-3 ${item.status ? "text-white" : "text-gray-500"}`} />
                  </div>
                  <span className={`text-sm ${item.status ? "text-foreground font-medium" : "text-muted-foreground"}`}>{item.label}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Actions */}
          <div className="flex gap-2">
            <Button variant="outline" className="flex-1 rounded-lg gap-2">
              <Download className="h-4 w-4" /> Download PDF
            </Button>
            <Button variant="outline" className="flex-1 rounded-lg gap-2">
              <Stamp className="h-4 w-4" /> Sign Contract
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
