import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { useLanguage } from "@/lib/i18n/language-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { MapPin, Package, Scale, Calendar, Banknote, Plus, Search, ArrowRight, Filter, Navigation } from "lucide-react";

const STATUS_COLORS: Record<string, string> = {
  posted: "bg-blue-50 text-blue-700 border-blue-200",
  matched: "bg-sky-50 text-sky-700 border-sky-200",
  in_transit: "bg-purple-50 text-purple-700 border-purple-200",
  delivered: "bg-emerald-50 text-emerald-700 border-emerald-200",
  completed: "bg-emerald-50 text-emerald-700 border-emerald-200",
  cancelled: "bg-red-50 text-red-700 border-red-200",
};

const CARGO_TYPES = ["grain", "cement", "construction", "perishables", "electronics", "livestock", "fuel", "general", "other"];

const STATUS_KEYS: Record<string, string> = {
  posted: "freight.posted",
  matched: "freight.matched",
  in_transit: "freight.inTransit",
  delivered: "freight.completed",
  completed: "freight.completed",
  cancelled: "freight.cancelled",
};

export default function FreightList() {
  const { isAuthenticated, user } = useAuth();
  const { t } = useLanguage();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [cargoFilter, setCargoFilter] = useState("all");

  const params = new URLSearchParams({ limit: "20" });
  if (statusFilter !== "all") params.set("status", statusFilter);
  if (cargoFilter !== "all") params.set("cargoType", cargoFilter);

  const { data, isLoading } = useQuery({
    queryKey: ["freight", statusFilter, cargoFilter],
    queryFn: () => api.get<{ freight: any[]; total: number }>(`/freight?${params}`),
    refetchInterval: 30_000,
  });

  const freight = (data?.freight ?? []).filter((f: any) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      f.pickupLocation?.toLowerCase().includes(q) ||
      f.deliveryLocation?.toLowerCase().includes(q) ||
      f.cargoType?.toLowerCase().includes(q) ||
      f.cargoDescription?.toLowerCase().includes(q)
    );
  });

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t("freight.title")}</h1>
          <p className="text-muted-foreground mt-1">Browse and apply for freight loads</p>
        </div>
        {isAuthenticated && (user?.role === "shipper" || user?.role === "admin") && (
          <Link href="/freight/new">
            <Button className="gap-2 rounded-lg bg-primary hover:bg-primary/90">
              <Plus className="h-4 w-4" />
              {t("freight.postNew")}
            </Button>
          </Link>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-6">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t("freight.search")}
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="pl-9 rounded-lg"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[160px] rounded-lg">
            <SelectValue placeholder={t("common.status")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("freight.allStatuses")}</SelectItem>
            <SelectItem value="posted">{t("freight.posted")}</SelectItem>
            <SelectItem value="matched">{t("freight.matched")}</SelectItem>
            <SelectItem value="in_transit">{t("freight.inTransit")}</SelectItem>
            <SelectItem value="completed">{t("freight.completed")}</SelectItem>
          </SelectContent>
        </Select>
        <Select value={cargoFilter} onValueChange={setCargoFilter}>
          <SelectTrigger className="w-[160px] rounded-lg">
            <SelectValue placeholder={t("common.type")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("freight.allTypes")}</SelectItem>
            {CARGO_TYPES.map(tItem => (
              <SelectItem key={tItem} value={tItem}>{tItem.charAt(0).toUpperCase() + tItem.slice(1)}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Count */}
      <p className="text-sm text-muted-foreground mb-4">
        {isLoading ? t("common.loading") : `${freight.length} result${freight.length !== 1 ? "s" : ""}`}
      </p>

      {/* List */}
      <div className="space-y-3">
        {isLoading ? (
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-32 w-full rounded-xl" />)
        ) : freight.length === 0 ? (
          <div className="text-center py-20 text-muted-foreground">
            <Package className="h-12 w-12 mx-auto mb-3 opacity-40" />
            <p className="text-lg font-medium">{t("freight.noResults")}</p>
            <p className="text-sm">{t("freight.search")}</p>
          </div>
        ) : (
          freight.map((f: any) => (
            <Link key={f.id} href={`/freight/${f.id}`}>
              <Card className="hover:shadow-md transition-all hover:border-primary/30 cursor-pointer border-border/60 rounded-xl">
                <CardContent className="pt-5 pb-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap mb-2">
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full border ${STATUS_COLORS[f.status] ?? "bg-gray-50 text-gray-700 border-gray-200"}`}>
                          {t(STATUS_KEYS[f.status] ?? f.status)}
                        </span>
                        <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full border border-slate-200 capitalize">
                          {f.cargoType}
                        </span>
                        {f.distanceKm && (
                          <span className="text-xs bg-slate-50 text-slate-500 px-2 py-0.5 rounded-full border border-slate-200">
                            <Navigation className="h-3 w-3 inline mr-1" />{f.distanceKm} km
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-2">
                        <MapPin className="h-4 w-4 text-muted-foreground shrink-0" />
                        <span className="text-sm font-semibold truncate">{f.pickupLocation}</span>
                        <ArrowRight className="h-3 w-3 text-muted-foreground" />
                        <span className="text-sm font-semibold truncate">{f.deliveryLocation}</span>
                      </div>
                      {f.cargoDescription && (
                        <p className="text-xs text-muted-foreground mt-1 truncate">{f.cargoDescription}</p>
                      )}
                      <div className="flex items-center gap-4 mt-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1"><Scale className="h-3 w-3" />{f.weightTons} {t("freight.tons")}</span>
                        {f.volumeM3 && <span>{f.volumeM3}m³</span>}
                        {f.deadline && (
                          <span className="flex items-center gap-1">
                            <Calendar className="h-3 w-3" />
                            {t("freight.due")} {new Date(f.deadline).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-lg font-bold text-foreground">{t("common.ETB")} {Number(f.budget).toLocaleString()}</p>
                      <span className="text-xs text-muted-foreground">Fixed Price</span>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))
        )}
      </div>
    </div>
  );
}
