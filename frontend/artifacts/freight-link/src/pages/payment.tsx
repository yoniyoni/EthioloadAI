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

/**
 * payment.tsx — F5 migrated to Spring Boot M7.
 *
 * Route param `:id` is a FREIGHT ID (as navigated from freight-detail.tsx).
 * The page resolves freightId → bookingId via the user's trip list.
 *
 * Spring Boot M7 payment contract:
 *   POST /api/payments           { bookingId, paymentMethod }  → PaymentResponse (PENDING)
 *   POST /api/payments/{id}/complete                           → PaymentResponse (PAID)
 *   GET  /api/payments/booking/{bookingId}                     → PaymentResponse
 *
 * Amount is SERVER-AUTHORITATIVE. The client never sends or controls amount,
 * commission, or driver net. All amounts are read from the server response.
 *
 * Spring Boot accepted paymentMethod values:
 *   cash | bank_transfer | telebirr | cbe_birr | chapa | awash_bank | dashen_bank | in_app
 * Unrecognised values default to 'cash' server-side.
 */
export default function Payment() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();
  const [, setLocation] = useLocation();

  // Verified SB paymentMethod strings (from PaymentServiceImpl.sanitisePaymentMethod)
  const [paymentMethodValue, setPaymentMethodValue] = useState("chapa");

  const freightId = Number(id);

  // ── Freight detail ────────────────────────────────────────────────────────
  // Already Spring Boot via F3. Used for display and status checks.
  const { data: freightRaw, isLoading } = useQuery({
    queryKey: ["freight", id],
    queryFn: () => api.get<any>(`/freight/${id}`),
  });

  const freight = freightRaw ? {
    ...freightRaw,
    deliveryLocation: freightRaw.deliveryLocation ?? freightRaw.destination,
    cargoType:        freightRaw.cargoType        ?? freightRaw.materialType,
  } : freightRaw;

  // ── freightId → bookingId resolution ─────────────────────────────────────
  // Spring Boot has no GET /api/bookings/freight/{freightId} endpoint.
  // We resolve via the user's trip list (same pattern as tracking.tsx).
  // TripResponse contains booking.id via the nested BookingInfo.
  const { data: tripsPage } = useQuery({
    queryKey: ["trips-for-freight", id],
    queryFn: () => api.get<{ content: any[] }>(`/trips?size=20`),
    enabled: !isNaN(freightId),
  });

  const activeTrip: any = (tripsPage?.content ?? []).find(
    (t: any) => t.cargo?.id === freightId || t.booking?.freightId === freightId
  );
  const bookingId: number | null = activeTrip?.booking?.id ?? null;

  // ── Payment retrieval: GET /api/payments/booking/{bookingId} ──────────────
  // SB uses bookingId (not freightId) to look up an existing payment.
  const { data: payment } = useQuery({
    queryKey: ["payment-by-booking", bookingId],
    queryFn: () => api.get<any>(`/payments/booking/${bookingId}`),
    enabled: bookingId !== null,
    // Treat 404 (no payment yet) as null rather than error
    retry: false,
  });

  // ── Initiate payment: POST /api/payments ──────────────────────────────────
  // Sends ONLY { bookingId, paymentMethod }.
  // Amount is never sent — Spring Boot derives it from booking.estimatedPrice.
  const initMutation = useMutation({
    mutationFn: () => {
      if (!bookingId) throw new Error("Booking not found for this freight. Accept a bid first.");
      return api.post<any>("/payments", {
        bookingId,
        paymentMethod: paymentMethodValue,
        // amount intentionally omitted — server authoritative
      });
    },
    onSuccess: () => {
      toast({ title: "Payment initiated!", description: "Payment record created. Proceed to complete." });
      qc.invalidateQueries({ queryKey: ["payment-by-booking", bookingId] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  // ── Complete payment: POST /api/payments/{paymentId}/complete ─────────────
  // Transitions PENDING → PAID. Also updates booking → COMPLETED server-side.
  // This replaces the old "verify+release escrow" and F4 deferred confirm-delivery flow.
  const completeMutation = useMutation({
    mutationFn: () => {
      if (!payment?.id) throw new Error("No pending payment to complete.");
      return api.post<any>(`/payments/${payment.id}/complete`, {});
    },
    onSuccess: () => {
      toast({ title: "Payment recorded", description: "Spring Boot marked the payment as paid." });
      qc.invalidateQueries({ queryKey: ["payment-by-booking", bookingId] });
      qc.invalidateQueries({ queryKey: ["freight", id] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  // ── Loading / not found states ────────────────────────────────────────────
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
  const canComplete = isOwner || isAdmin;

  // ── Map SB paymentStatus to display labels ────────────────────────────────
  // SB PaymentStatus enum: PENDING | PAID | FAILED
  const statusMap: Record<string, { label: string; color: string }> = {
    PENDING: { label: "Awaiting Completion",  color: "bg-sky-50 text-sky-700 border-sky-200" },
    PAID:    { label: "Payment Complete",      color: "bg-emerald-50 text-emerald-700 border-emerald-200" },
    FAILED:  { label: "Payment Failed",        color: "bg-red-50 text-red-700 border-red-200" },
  };
  const escrow = payment?.paymentStatus
    ? (statusMap[payment.paymentStatus] ?? { label: payment.paymentStatus, color: "bg-gray-50 text-gray-600 border-gray-200" })
    : { label: "No payment yet", color: "bg-gray-50 text-gray-600 border-gray-200" };

  // ── Payment method providers ──────────────────────────────────────────────
  // Only methods accepted by Spring Boot M7 PaymentServiceImpl.sanitisePaymentMethod.
  // Values: cash | bank_transfer | telebirr | cbe_birr | chapa | awash_bank | dashen_bank | in_app
  // Note: Spring Boot records the method but does NOT implement external gateway integration.
  // The UI preserves the provider selection experience; gateway calls are NOT made.
  const providers = [
    { id: "chapa",        name: "Chapa",            subtitle: "Cards, Apple Pay, Google Pay",   icon: CreditCard,  color: "bg-blue-500" },
    { id: "cbe_birr",     name: "CBE Birr",          subtitle: "Direct wallet-to-wallet",        icon: Wallet,      color: "bg-emerald-500" },
    { id: "telebirr",     name: "Telebirr",          subtitle: "Mobile banking interface",       icon: Smartphone,  color: "bg-teal-500" },
    { id: "cash",         name: "Cash on Delivery",  subtitle: "Pay driver directly on delivery", icon: Banknote,   color: "bg-amber-500" },
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
                <p className="text-xs text-muted-foreground">Spring Boot M7 Payment</p>
              </div>
            </div>
            <Badge className={`${escrow.color} border`}>{escrow.label}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">

          {/* Booking resolution notice */}
          {bookingId === null && (
            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200">
              <p className="text-sm font-medium text-amber-800">Booking not yet resolved</p>
              <p className="text-xs text-amber-700 mt-1">
                A driver must accept a bid and the booking must be created before payment can proceed.
                {tripsPage === undefined && " (Loading trip data…)"}
              </p>
            </div>
          )}

          {/* Shipment Summary — display amounts from server response if available */}
          <div className="p-5 rounded-xl bg-[#0c1e4a] text-white space-y-3">
            <div className="flex items-center gap-2 mb-2">
              <Shield className="h-4 w-4 text-emerald-400" />
              <span className="text-xs font-semibold text-emerald-400">Spring Boot M7 Payment</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Shipment ID</span>
              <span className="font-medium text-sm">#{freight.id}</span>
            </div>
            {bookingId && (
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-300">Booking ID</span>
                <span className="font-medium text-sm">#{bookingId}</span>
              </div>
            )}
            <div className="flex items-center justify-between pt-2 border-t border-white/10">
              <span className="text-sm font-medium">
                {payment?.amount ? "Authorised Amount" : "Agreed Price"}
              </span>
              <span className="text-2xl font-bold">
                ETB {Number(payment?.amount ?? freight.budget ?? 0).toLocaleString()}
              </span>
            </div>
            {/* Show server-computed commission and driver net if payment exists */}
            {payment?.commissionAmount && (
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400">Platform commission</span>
                <span className="text-slate-300">ETB {Number(payment.commissionAmount).toLocaleString()}</span>
              </div>
            )}
            {payment?.driverNetAmount && (
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-400">Driver receives</span>
                <span className="text-emerald-400 font-medium">ETB {Number(payment.driverNetAmount).toLocaleString()}</span>
              </div>
            )}
          </div>

          {/* Trust badges */}
          <div className="grid grid-cols-2 gap-3">
            <div className="flex items-center gap-2 p-3 rounded-lg border border-border/60">
              <CheckCircle className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">Spring Boot payment record</span>
            </div>
            <div className="flex items-center gap-2 p-3 rounded-lg border border-border/60">
              <Lock className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">Amount server-verified</span>
            </div>
          </div>

          {/* Payment method selection — only shown before payment is initiated */}
          {canPay && !payment && bookingId !== null && (
            <div className="space-y-3">
              <p className="text-sm font-medium text-foreground">Select Payment Method</p>
              <p className="text-xs text-muted-foreground">
                Spring Boot records your method choice. External gateway integration is not yet active.
              </p>
              <div className="space-y-2">
                {providers.map((p) => (
                  <button
                    key={p.id}
                    onClick={() => setPaymentMethodValue(p.id)}
                    className={`w-full flex items-center gap-3 p-4 rounded-xl border transition-all text-left ${
                      paymentMethodValue === p.id
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
                      paymentMethodValue === p.id ? "border-primary bg-primary" : "border-muted-foreground"
                    }`}>
                      {paymentMethodValue === p.id && <CheckCircle className="h-3 w-3 text-white" />}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* ── Action: Initiate payment (PENDING state) ── */}
          {canPay && !payment && bookingId !== null && (
            <div className="space-y-3">
              <Button
                onClick={() => initMutation.mutate()}
                disabled={initMutation.isPending}
                className="w-full h-12 rounded-lg bg-primary hover:bg-primary/90 text-white font-semibold"
              >
                {initMutation.isPending
                  ? <><Loader2 className="h-4 w-4 animate-spin mr-2" />Processing…</>
                  : <><Shield className="h-4 w-4 mr-2" />Initiate Payment<ChevronRight className="ml-2 h-4 w-4" /></>}
              </Button>
              <p className="text-xs text-muted-foreground text-center">
                Amount is confirmed server-side from the accepted booking price. No external gateway call is made yet.
              </p>
            </div>
          )}

          {/* ── Action: Complete payment (PENDING → PAID) ── */}
          {payment?.paymentStatus === "PENDING" && canComplete && (
            <div className="space-y-3">
              <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
                <p className="font-medium text-blue-800 text-sm">Payment is PENDING</p>
                <p className="text-xs text-blue-700 mt-1">
                  Confirm that payment has been made via <strong>{payment.paymentMethod}</strong> and mark as complete.
                  This will transition the booking to COMPLETED.
                </p>
              </div>
              <Button
                onClick={() => completeMutation.mutate()}
                disabled={completeMutation.isPending}
                className="w-full h-12 rounded-lg bg-emerald-600 hover:bg-emerald-700 gap-2"
              >
                {completeMutation.isPending
                  ? <Loader2 className="h-4 w-4 animate-spin" />
                  : <CheckCircle className="h-4 w-4" />}
                Confirm &amp; Complete Payment
              </Button>
              <p className="text-xs text-muted-foreground text-center">
                ETB {Number(payment.amount).toLocaleString()} will be recorded as paid by Spring Boot.
                Commission: ETB {Number(payment.commissionAmount ?? 0).toLocaleString()}.
                Driver net: ETB {Number(payment.driverNetAmount ?? 0).toLocaleString()}.
              </p>
            </div>
          )}

          {/* ── Paid state ── */}
          {payment?.paymentStatus === "PAID" && (
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-center space-y-1">
              <CheckCircle className="h-6 w-6 text-emerald-600 mx-auto" />
              <p className="font-medium text-emerald-800">Payment Complete</p>
              <p className="text-xs text-emerald-700">
                ETB {Number(payment.amount).toLocaleString()} recorded as PAID via {payment.paymentMethod}.
              </p>
            </div>
          )}

          {/* ── Failed state ── */}
          {payment?.paymentStatus === "FAILED" && (
            <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-center space-y-1">
              <p className="font-medium text-red-800">Payment Failed</p>
              <p className="text-xs text-red-700">Contact support or retry with a different payment method.</p>
            </div>
          )}

          {/* ── Driver view: payment secured ── */}
          {isDriver && !canPay && (
            <div className="p-4 rounded-xl bg-blue-50 border border-blue-200 text-center space-y-1">
              <Lock className="h-6 w-6 text-blue-600 mx-auto" />
              <p className="font-medium text-blue-800">Payment Secured</p>
              <p className="text-xs text-blue-700">
                {payment?.paymentStatus === "PAID"
                  ? "Payment has been completed. Drive safely!"
                  : "Awaiting payment confirmation from the shipper."}
              </p>
            </div>
          )}

        </CardContent>
      </Card>
    </div>
  );
}
