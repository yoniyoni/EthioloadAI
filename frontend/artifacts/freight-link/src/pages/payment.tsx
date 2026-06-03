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
import { Link } from "wouter";
import { Banknote, Shield, ArrowLeft, CheckCircle, Lock, Loader2, CreditCard, Wallet, Smartphone, ChevronRight } from "lucide-react";

export default function Payment() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const [, setLocation] = useLocation();
  const [provider, setProvider] = useState("chapa");

  const freightId = Number(id);

  const { data: freight, isLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
  });

  const { data: payment } = useQuery({
    queryKey: ["payment", freightId],
    queryFn: () => api.get<any>(`/payments/${freightId}`),
    enabled: !isNaN(freightId),
  });

  const initMutation = useMutation({
    mutationFn: () =>
      api.post("/payments/initialize", {
        freightId,
        amount: freight?.budget || 0,
        provider: provider as "chapa" | "cbe_birr" | "telebirr",
      }),
    onSuccess: () => {
      toast({ title: "Payment initialized!", description: "Complete payment via your chosen provider." });
      qc.invalidateQueries({ queryKey: ["payment", freightId] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const verifyMutation = useMutation({
    mutationFn: () => api.post(`/payments/${freightId}/verify`, {}),
    onSuccess: () => {
      toast({ title: "Payment verified!", description: "Funds held in escrow until delivery." });
      qc.invalidateQueries({ queryKey: ["payment", freightId] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const releaseMutation = useMutation({
    mutationFn: () => api.post(`/payments/${freightId}/release`, {}),
    onSuccess: () => {
      toast({ title: "Payment released!", description: "Funds transferred to driver." });
      qc.invalidateQueries({ queryKey: ["payment", freightId] });
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
  const isAdmin = user?.role === "admin";
  const isDriver = user?.id === freight.matchedDriverId;
  const canPay = isOwner || isAdmin;
  const canRelease = isOwner || isAdmin;

  const statusMap: Record<string, { label: string; color: string }> = {
    pending_payment: { label: "Awaiting Payment", color: "bg-sky-50 text-sky-700 border-sky-200" },
    payment_held: { label: "Payment in Escrow", color: "bg-blue-50 text-blue-700 border-blue-200" },
    in_transit: { label: "In Transit", color: "bg-purple-50 text-purple-700 border-purple-200" },
    delivered: { label: "Delivered", color: "bg-cyan-50 text-cyan-700 border-cyan-200" },
    released: { label: "Released", color: "bg-emerald-50 text-emerald-700 border-emerald-200" },
    refunded: { label: "Refunded", color: "bg-red-50 text-red-700 border-red-200" },
  };
  const escrow = statusMap[payment?.escrowStatus] ?? { label: "No payment", color: "bg-gray-50 text-gray-600 border-gray-200" };

  const providers = [
    { id: "chapa", name: "Chapa", subtitle: "Cards, Apple Pay, Google Pay", icon: CreditCard, color: "bg-blue-500" },
    { id: "cbe_birr", name: "CBE Birr", subtitle: "Direct wallet-to-wallet", icon: Wallet, color: "bg-emerald-500" },
    { id: "telebirr", name: "Telebirr", subtitle: "Mobile banking interface", icon: Smartphone, color: "bg-teal-500" },
  ];

  return (
    <div className="container mx-auto max-w-2xl px-4 py-8">
      <Link href="/freight">
        <Button variant="ghost" className="gap-2 mb-6 -ml-2 rounded-lg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Button>
      </Link>

      <Card className="border-border/60 rounded-xl">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
                <Shield className="h-4 w-4 text-primary" />
              </div>
              <div>
                <CardTitle className="text-xl">Secure Checkout</CardTitle>
                <p className="text-xs text-muted-foreground">Powered by Chapa</p>
              </div>
            </div>
            <Badge className={`${escrow.color} border`}>{escrow.label}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Shipment Summary */}
          <div className="p-5 rounded-xl bg-[#0c1e4a] text-white space-y-3">
            <div className="flex items-center gap-2 mb-2">
              <Shield className="h-4 w-4 text-emerald-400" />
              <span className="text-xs font-semibold text-emerald-400">Escrow Protected</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Shipment ID</span>
              <span className="font-medium text-sm">#{freight.id}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Service Type</span>
              <span className="font-medium text-sm">Priority Freight</span>
            </div>
            <div className="flex items-center justify-between pt-2 border-t border-white/10">
              <span className="text-sm font-medium">Total Amount</span>
              <span className="text-2xl font-bold">ETB {Number(freight.budget).toLocaleString()}</span>
            </div>
          </div>

          {/* Trust badges */}
          <div className="grid grid-cols-2 gap-3">
            <div className="flex items-center gap-2 p-3 rounded-lg border border-border/60">
              <CheckCircle className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">PCI-DSS Compliant</span>
            </div>
            <div className="flex items-center gap-2 p-3 rounded-lg border border-border/60">
              <Lock className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">End-to-End Encrypted</span>
            </div>
          </div>

          {/* Payment Options */}
          <div className="space-y-3">
            <p className="text-sm font-medium text-foreground">Enter Payment Details</p>
            <div className="space-y-2">
              {providers.map((p) => (
                <button
                  key={p.id}
                  onClick={() => setProvider(p.id)}
                  className={`w-full flex items-center gap-3 p-4 rounded-xl border transition-all text-left ${
                    provider === p.id
                      ? "border-primary bg-primary/5 ring-1 ring-primary/20"
                      : "border-border/60 hover:bg-muted/50"
                  }`}
                >
                  <div className={`h-10 w-10 rounded-lg ${p.color} flex items-center justify-center text-white shrink-0`}>
                    <p.icon className="h-5 w-5" />
                  </div>
                  <div className="flex-1">
                    <p className="font-semibold text-sm">{p.name}</p>
                    <p className="text-xs text-muted-foreground">{p.subtitle}</p>
                  </div>
                  <div className={`h-5 w-5 rounded-full border-2 flex items-center justify-center shrink-0 ${
                    provider === p.id ? "border-primary bg-primary" : "border-muted-foreground"
                  }`}>
                    {provider === p.id && <CheckCircle className="h-3 w-3 text-white" />}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Actions */}
          {canPay && !payment && (
            <div className="space-y-3">
              <Button
                onClick={() => initMutation.mutate()}
                disabled={initMutation.isPending}
                className="w-full h-12 rounded-lg bg-primary hover:bg-primary/90 text-white font-semibold"
              >
                {initMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <Shield className="h-4 w-4 mr-2" />}
                Pay ETB {Number(freight.budget).toLocaleString()} Securely
                <ChevronRight className="ml-2 h-4 w-4" />
              </Button>
              <p className="text-xs text-muted-foreground text-center">
                Your payment is held in a secure, audited account. It will only be released after cargo delivery confirmation.
              </p>
            </div>
          )}

          {payment && payment.escrowStatus === "pending_payment" && (
            <div className="space-y-3">
              <Button
                onClick={() => verifyMutation.mutate()}
                disabled={verifyMutation.isPending}
                className="w-full h-12 rounded-lg"
              >
                {verifyMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <CheckCircle className="h-4 w-4 mr-2" />}
                Verify & Hold in Escrow
              </Button>
              <p className="text-xs text-muted-foreground text-center">
                Confirm that payment has been sent. Funds will be locked until delivery.
              </p>
            </div>
          )}

          {payment && (payment.escrowStatus === "in_transit" || payment.escrowStatus === "delivered") && canRelease && (
            <div className="space-y-3">
              <Button
                onClick={() => releaseMutation.mutate()}
                disabled={releaseMutation.isPending}
                className="w-full h-12 rounded-lg bg-emerald-600 hover:bg-emerald-700"
              >
                {releaseMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <CheckCircle className="h-4 w-4 mr-2" />}
                Release Payment to Driver
              </Button>
              <p className="text-xs text-muted-foreground text-center">
                Once released, funds are transferred to the driver and commission deducted.
              </p>
            </div>
          )}

          {payment && payment.escrowStatus === "released" && (
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-center space-y-1">
              <CheckCircle className="h-6 w-6 text-emerald-600 mx-auto" />
              <p className="font-medium text-emerald-800">Payment Released</p>
              <p className="text-xs text-emerald-700">Funds have been transferred to the driver.</p>
            </div>
          )}

          {isDriver && !canPay && (
            <div className="p-4 rounded-xl bg-blue-50 border border-blue-200 text-center space-y-1">
              <Lock className="h-6 w-6 text-blue-600 mx-auto" />
              <p className="font-medium text-blue-800">Payment Secured</p>
              <p className="text-xs text-blue-700">The shipper has deposited funds in escrow. Drive safely!</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
