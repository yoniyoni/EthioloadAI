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
  const { data: settlements, isLoading, refetch } = useQuery({
    queryKey: ["settlements", "carrier", companyId],
    queryFn: () => api.get<SettlementResponse[]>(`/settlements/carrier/${companyId}`),
  });

  return (
    <div className="container max-w-4xl mx-auto py-8 px-4 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Financial Settlements</h1>
        <Button variant="ghost" size="icon" onClick={() => refetch()}>
          <RefreshCw className="h-4 w-4" />
        </Button>
      </div>

      {isLoading ? (
        Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24 w-full" />)
      ) : !settlements?.length ? (
        <Card>
          <CardContent className="pt-8 pb-8 text-center text-muted-foreground">
            No settlements yet. They appear here after deliveries are verified.
          </CardContent>
        </Card>
      ) : (
        settlements.map((s) => (
          <SettlementCard key={s.id} settlement={s} companyId={companyId} />
        ))
      )}

      {/* Summary totals */}
      {settlements && settlements.length > 0 && (
        <SettlementSummary settlements={settlements} />
      )}
    </div>
  );
}

function SettlementCard({
  settlement: s,
  companyId,
}: {
  settlement: SettlementResponse;
  companyId: number;
}) {
  const statusColor = {
    PENDING: "secondary",
    PROCESSING: "outline",
    PAID: "default",
    FAILED: "destructive",
    BLOCKED: "destructive",
    CANCELLED: "secondary",
  }[s.status] as any;

  return (
    <Link href={`/carrier/${companyId}/settlements/${s.id}`}>
      <Card className="cursor-pointer hover:shadow-md transition-shadow">
        <CardContent className="pt-4 pb-4">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <p className="text-sm text-muted-foreground">Booking #{s.bookingId}</p>
              <p className="text-lg font-bold">{formatETB(s.netAmount)} NET</p>
              <p className="text-xs text-muted-foreground">
                Gross: {formatETB(s.grossAmount)} · Commission: {formatETB(s.commissionAmount)}
              </p>
            </div>
            <div className="text-right">
              <Badge variant={statusColor} className="mb-2">{s.status}</Badge>
              <p className="text-xs text-muted-foreground">
                {new Date(s.createdAt).toLocaleDateString()}
              </p>
            </div>
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
      <CardContent className="grid grid-cols-3 gap-4 text-center">
        <div>
          <p className="text-xs text-muted-foreground">Gross</p>
          <p className="font-bold">{formatETB(totalGross)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Commission</p>
          <p className="font-bold text-orange-600">−{formatETB(totalCommission)}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Net Received</p>
          <p className="font-bold text-green-700">{formatETB(totalNet)}</p>
        </div>
      </CardContent>
    </Card>
  );
}

// ── Single settlement detail ───────────────────────────────────────────────────

function SingleSettlement({ settlementId, companyId }: { settlementId: number; companyId: number }) {
  const { data: s, isLoading } = useQuery({
    queryKey: ["settlement", settlementId],
    queryFn: () => api.get<SettlementResponse>(`/settlements/${settlementId}`),
  });

  if (isLoading)
    return (
      <div className="container max-w-2xl mx-auto py-8 px-4 space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-48 w-full" />
      </div>
    );

  if (!s) return null;

  const statusColor = {
    PENDING: "secondary",
    PROCESSING: "outline",
    PAID: "default",
    FAILED: "destructive",
    BLOCKED: "destructive",
    CANCELLED: "secondary",
  }[s.status] as any;

  const commissionPercent = (s.commissionRate * 100).toFixed(1);

  return (
    <div className="container max-w-2xl mx-auto py-8 px-4 space-y-6">
      <div className="flex items-center gap-3">
        <Link href={`/carrier/${companyId}/settlements`}>
          <Button variant="ghost" size="icon"><ArrowLeft className="h-4 w-4" /></Button>
        </Link>
        <div>
          <h1 className="text-2xl font-bold">Settlement #{s.id}</h1>
          <p className="text-muted-foreground text-sm">Booking #{s.bookingId}</p>
        </div>
        <Badge variant={statusColor} className="ml-auto">{s.status}</Badge>
      </div>

      {/* Financial breakdown */}
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2 text-base">
          <Banknote className="h-5 w-5" /> Financial Breakdown
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
          <hr />
          <FinancialRow
            icon={<Building2 className="h-4 w-4 text-green-700" />}
            label="Your Net Payout"
            value={formatETB(s.netAmount)}
            highlight={true}
            valueClass="text-green-700"
          />
          {/* Read-only notice */}
          <p className="text-xs text-muted-foreground italic mt-2">
            All amounts are calculated server-side from the agreed price negotiated in Phase 5.
          </p>
        </CardContent>
      </Card>

      {/* Payment details */}
      <Card>
        <CardHeader><CardTitle className="text-base">Payment Details</CardTitle></CardHeader>
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
            <div className="rounded-md bg-destructive/10 border border-destructive/20 p-3">
              <p className="font-medium text-destructive">Failure Reason</p>
              <p className="text-destructive/80 text-xs">{s.failureReason}</p>
              {s.retryCount > 0 && (
                <p className="text-xs text-muted-foreground mt-1">Retried {s.retryCount}×</p>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
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
    <div className={`flex items-center gap-3 ${highlight ? "bg-green-50 rounded-lg px-3 py-2" : ""}`}>
      {icon}
      <div className="flex-1">
        <p className={`text-sm ${highlight ? "font-semibold" : ""}`}>{label}</p>
        {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
      </div>
      <p className={`font-bold ${highlight ? "text-lg" : ""} ${valueClass}`}>{value}</p>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
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
