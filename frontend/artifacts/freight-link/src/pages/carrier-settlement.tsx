import { useQuery } from "@tanstack/react-query";
import { useParams } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Banknote, TrendingDown, DollarSign, Building2, RefreshCw } from "lucide-react";
import { Link } from "wouter";

/**
 * carrier-settlement.tsx — Phase 7 Carrier Settlement breakdown page.
 *
 * Routes:
 *   /carrier/:companyId/settlements          — list all settlements for company
 *   /carrier/:companyId/settlements/:id      — single settlement detail
 *
 * Phase 7 API:
 *   GET /api/settlements/carrier/:companyId  → CarrierSettlementResponse[]
 *   GET /api/settlements/:id                  → CarrierSettlementResponse
 *
 * Security: server enforces carrier membership.
 * Financial fields are read-only — no client controls amounts.
 */
export default function CarrierSettlement() {
  const { companyId, id } = useParams<{ companyId: string; id?: string }>();
  const { user } = useAuth();

  return id
    ? <SingleSettlement settlementId={Number(id)} companyId={Number(companyId)} />
    : <SettlementList companyId={Number(companyId)} />;
}

// ── Settlement list ───────────────────────────────────────────────────────────

function SettlementList({ companyId }: { companyId: number }) {
  const { data: settlements, isLoading, refetch, error } = useQuery({
    queryKey: ["settlements", "carrier", companyId],
    queryFn: async () => {
      const result = await api.get<SettlementResponse[]>(`/settlements/carrier/${companyId}`);
      if (!Array.isArray(result) || result.some(settlement => settlement.carrierCompanyId !== companyId)) {
        throw new Error("The settlement response did not match the selected company.");
      }
      return result;
    },
  });

  return (
    <main className="container mx-auto max-w-6xl space-y-6 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <header className="flex flex-col gap-4 border-b border-border/70 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium text-muted-foreground">Company #{companyId} · Finance</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">Settlements</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">Review payment status and the financial breakdown for completed deliveries.</p>
        </div>
        <Button type="button" variant="outline" aria-label="Refresh settlements" className="min-h-11 w-full gap-2 sm:w-auto" onClick={() => refetch()}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          Refresh
        </Button>
      </header>

      {error ? <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="break-words text-sm text-destructive">Unable to load settlements: {error.message}</p><Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => refetch()}>Retry settlements</Button></div> : isLoading ? (
        <div className="space-y-3" aria-label="Loading settlements">
          {Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-28 w-full rounded-xl" />)}
        </div>
      ) : !settlements?.length ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="px-5 py-12 text-center">
            <p className="font-medium">No settlements yet</p>
            <p className="mx-auto mt-1 max-w-lg text-sm leading-6 text-muted-foreground">Settlements appear here after deliveries are verified.</p>
          </CardContent>
        </Card>
      ) : (
        <section aria-label="Company settlements" className="grid gap-3 lg:grid-cols-2">
          {settlements.map((s) => (
            <SettlementCard key={s.id} settlement={s} companyId={companyId} />
          ))}
        </section>
      )}

      {/* Summary totals */}
      {settlements && settlements.length > 0 && (
        <SettlementSummary settlements={settlements} />
      )}
    </main>
  );
}

function SettlementCard({
  settlement: s,
  companyId,
}: {
  settlement: SettlementResponse;
  companyId: number;
}) {
  return (
    <Link href={`/carrier/${companyId}/settlements/${s.id}`} className="group rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2">
      <Card className="h-full border-border/70 shadow-sm transition-shadow group-hover:shadow-md">
        <CardContent className="flex h-full flex-col gap-4 p-4 sm:p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Settlement #{s.id}</p>
              <p className="mt-1 text-sm text-muted-foreground">Booking #{s.bookingId}</p>
            </div>
            <Badge variant={settlementStatusVariant(s.status)}>{s.status}</Badge>
          </div>
          <div className="border-t border-border/70 pt-3">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Net amount</p>
            <p className="mt-1 break-words text-xl font-semibold tracking-tight">{formatETB(s.netAmount)}</p>
          </div>
          <div className="flex flex-col gap-1 border-t border-border/70 pt-3 text-xs text-muted-foreground sm:flex-row sm:flex-wrap sm:justify-between sm:gap-x-4">
            <span className="break-words">Gross: {formatETB(s.grossAmount)}</span>
            <span className="break-words">Commission: {formatETB(s.commissionAmount)}</span>
            <span>Created {new Date(s.createdAt).toLocaleDateString()}</span>
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}

function SettlementSummary({ settlements }: { settlements: SettlementResponse[] }) {
  const paid = settlements.filter((s) => s.status === "PAID");
  const totalGross = paid.reduce((acc, s) => acc + s.grossAmount, 0);
  const totalCommission = paid.reduce((acc, s) => acc + s.commissionAmount, 0);
  const totalNet = paid.reduce((acc, s) => acc + s.netAmount, 0);

  return (
    <Card className="bg-muted/50">
      <CardHeader><CardTitle className="text-sm font-semibold">PAID Totals</CardTitle></CardHeader>
      <CardContent className="grid grid-cols-1 gap-4 text-center sm:grid-cols-3">
        <div>
          <p className="text-xs text-muted-foreground">Gross</p>
          <p className="break-words font-bold">{formatETB(totalGross)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Commission</p>
          <p className="break-words font-bold text-orange-600">−{formatETB(totalCommission)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Net Received</p>
          <p className="break-words font-bold text-green-700">{formatETB(totalNet)}</p>
        </div>
      </CardContent>
    </Card>
  );
}

// ── Single settlement detail ───────────────────────────────────────────────────

function SingleSettlement({ settlementId, companyId }: { settlementId: number; companyId: number }) {
  const { data: s, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["settlement", companyId, settlementId],
    queryFn: async () => {
      const result = await api.get<SettlementResponse>(`/settlements/${settlementId}`);
      if (result.carrierCompanyId !== companyId) {
        throw new Error("The settlement response did not match the selected company.");
      }
      return result;
    },
  });

  if (isLoading)
    return (
      <main className="container mx-auto max-w-6xl space-y-4 px-3 py-5 sm:px-5 sm:py-7 lg:px-7" aria-label="Loading settlement details">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-48 w-full" />
      </main>
    );

  if (isError) return (
    <main className="container mx-auto max-w-6xl space-y-4 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <Button type="button" variant="outline" className="min-h-11" asChild><Link href={`/carrier/${companyId}/settlements`}><ArrowLeft className="mr-2 h-4 w-4" />Back to settlements</Link></Button>
      <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="break-words text-sm text-destructive">{error instanceof Error ? error.message : "Unable to load settlement details."}</p><Button type="button" variant="outline" className="min-h-11 shrink-0" onClick={() => refetch()}>Retry settlement</Button></div>
    </main>
  );
  if (!s) return (
    <main className="container mx-auto max-w-6xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <Card className="border-dashed shadow-none"><CardContent className="p-5"><p className="font-medium">Settlement unavailable</p><p className="mt-1 text-sm text-muted-foreground">The requested settlement details are not available.</p><Button type="button" variant="outline" className="mt-4 min-h-11" asChild><Link href={`/carrier/${companyId}/settlements`}>Back to settlements</Link></Button></CardContent></Card>
    </main>
  );

  const commissionPercent = (s.commissionRate * 100).toFixed(1);

  return (
    <main className="container mx-auto max-w-6xl space-y-6 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
      <header className="flex flex-col gap-4 border-b border-border/70 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <Button type="button" variant="outline" className="mb-4 min-h-11" asChild><Link href={`/carrier/${companyId}/settlements`}><ArrowLeft className="mr-2 h-4 w-4" />Back to settlements</Link></Button>
          <p className="text-sm font-medium text-muted-foreground">Company #{companyId} · Booking #{s.bookingId}</p>
          <h1 className="mt-1 break-words text-2xl font-bold tracking-tight sm:text-3xl">Settlement #{s.id}</h1>
        </div>
        <Badge variant={settlementStatusVariant(s.status)} className="self-start">{s.status}</Badge>
      </header>

      {/* Financial breakdown */}
      <Card className="border-border/70 shadow-sm">
        <CardHeader><CardTitle className="flex items-center gap-2 text-base">
          <Banknote className="h-5 w-5 text-primary" aria-hidden="true" /> Financial breakdown
        </CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <FinancialRow
            icon={<DollarSign className="h-4 w-4 text-blue-600" />}
            label="Gross Amount (Agreed Price)"
            value={formatETB(s.grossAmount)}
            sub={`${s.currency}`}
            highlight={false}
          />
          <FinancialRow
            icon={<TrendingDown className="h-4 w-4 text-orange-600" />}
            label={`Platform Commission (${commissionPercent}%)`}
            value={`−${formatETB(s.commissionAmount)}`}
            highlight={false}
            valueClass="text-orange-600"
          />
          <hr className="border-border/70" />
          <FinancialRow
            icon={            <Building2 className="h-4 w-4 text-primary" />}
            label="Your Net Payout"
            value={formatETB(s.netAmount)}
            highlight={true}
            valueClass="text-primary"
          />
          {/* Read-only notice */}
          <p className="text-xs text-muted-foreground italic mt-2">
            All amounts are calculated server-side from the agreed price negotiated in Phase 5.
          </p>
        </CardContent>
      </Card>

      {/* Payment details */}
      <Card className="border-border/70 shadow-sm">
        <CardHeader><CardTitle className="text-base">Payment details</CardTitle></CardHeader>
        <CardContent className="space-y-2 text-sm">
          <DetailRow label="Status" value={s.status} />
          <DetailRow label="Currency" value={s.currency} />
          <DetailRow label="Created" value={new Date(s.createdAt).toLocaleString()} />
          {s.processedAt && (
            <DetailRow label="Processed" value={new Date(s.processedAt).toLocaleString()} />
          )}
          {s.paidAt && (
            <DetailRow label="Paid at" value={new Date(s.paidAt).toLocaleString()} />
          )}
          {s.paymentReference && (
            <DetailRow label="Payment Reference" value={s.paymentReference} />
          )}
          {s.externalTxId && (
            <DetailRow label="External TX ID" value={s.externalTxId} />
          )}
          {s.failureReason && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4">
              <p className="font-medium text-destructive">Failure Reason</p>
              <p className="text-destructive/80 text-xs">{s.failureReason}</p>
              {s.retryCount > 0 && (
                <p className="text-xs text-muted-foreground mt-1">Retried {s.retryCount}×</p>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </main>
  );
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function formatETB(amount: number): string {
  return `${amount.toLocaleString("en-ET", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ETB`;
}

function FinancialRow({
  icon,
  label,
  value,
  sub,
  highlight,
  valueClass = "",
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub?: string;
  highlight: boolean;
  valueClass?: string;
}) {
  return (
    <div className={`flex flex-wrap items-center gap-3 ${highlight ? "rounded-lg border border-primary/20 bg-primary/5 px-3 py-3" : ""}`}>
      <span className="shrink-0">{icon}</span>
      <div className="min-w-0 flex-1">
        <p className={`text-sm ${highlight ? "font-semibold" : ""}`}>{label}</p>
        {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
      </div>
      <p className={`max-w-full break-words text-right font-bold ${highlight ? "text-lg" : ""} ${valueClass}`}>{value}</p>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1 border-b border-border/60 py-2 last:border-0 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="break-all text-left font-medium sm:text-right">{value}</span>
    </div>
  );
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface SettlementResponse {
  id: number;
  bookingId: number;
  carrierCompanyId: number;
  carrierCompanyName: string;
  proofOfDeliveryId?: number;
  grossAmount: number;
  commissionRate: number;
  commissionAmount: number;
  netAmount: number;
  currency: string;
  status: "PENDING" | "PROCESSING" | "PAID" | "FAILED" | "BLOCKED" | "CANCELLED";
  paymentReference?: string;
  externalTxId?: string;
  createdAt: string;
  processedAt?: string;
  paidAt?: string;
  failedAt?: string;
  updatedAt: string;
  failureReason?: string;
  retryCount: number;
}

function settlementStatusVariant(status: SettlementResponse["status"]) {
  switch (status) {
    case "PENDING":
    case "CANCELLED":
      return "secondary" as const;
    case "PROCESSING":
      return "outline" as const;
    case "PAID":
      return "default" as const;
    case "FAILED":
    case "BLOCKED":
      return "destructive" as const;
  }
}
