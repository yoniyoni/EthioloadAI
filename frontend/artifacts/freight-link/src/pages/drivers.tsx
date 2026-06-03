import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Star, Truck, CheckCircle, MapPin, Search, Shield, Navigation, ChevronRight } from "lucide-react";
import { useState } from "react";
import { Input } from "@/components/ui/input";

const STATUS_COLORS: Record<string, string> = {
  active: "bg-emerald-50 text-emerald-700 border-emerald-200",
  approved: "bg-blue-50 text-blue-700 border-blue-200",
  under_review: "bg-sky-50 text-sky-700 border-sky-200",
  submitted: "bg-purple-50 text-purple-700 border-purple-200",
  suspended: "bg-red-50 text-red-700 border-red-200",
};

export default function Drivers() {
  const [statusFilter, setStatusFilter] = useState("active");
  const [search, setSearch] = useState("");

  const { data, isLoading } = useQuery({
    queryKey: ["drivers", statusFilter],
    queryFn: () => api.get<{ drivers: any[]; total: number }>(`/drivers?status=${statusFilter}&limit=50`),
  });

  const drivers = (data?.drivers ?? []).filter((d: any) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return d.user?.name?.toLowerCase().includes(q) || d.user?.address?.toLowerCase().includes(q);
  });

  return (
    <div className="container mx-auto max-w-6xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">Driver Network</h1>
        <p className="text-muted-foreground mt-1">Browse verified truck drivers across Ethiopia</p>
      </div>

      <div className="flex gap-3 mb-6">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by name or location…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="pl-9 rounded-lg"
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[160px] rounded-lg">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="active">Active</SelectItem>
            <SelectItem value="approved">Approved</SelectItem>
            <SelectItem value="under_review">Under Review</SelectItem>
            <SelectItem value="submitted">Submitted</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <p className="text-sm text-muted-foreground mb-4">
        {isLoading ? "Loading…" : `${drivers.length} driver${drivers.length !== 1 ? "s" : ""}`}
      </p>

      <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
        {isLoading ? (
          Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-52 rounded-xl" />)
        ) : drivers.length === 0 ? (
          <div className="col-span-3 text-center py-20 text-muted-foreground">
            <Truck className="h-12 w-12 mx-auto mb-3 opacity-40" />
            <p>No drivers found</p>
          </div>
        ) : (
          drivers.map((d: any) => (
            <Card key={d.id} className="hover:shadow-md transition-shadow border-border/60 rounded-xl">
              <CardContent className="pt-5">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="h-12 w-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0">
                      <span className="text-sm font-bold text-primary">
                        {d.user?.name?.split(" ").map((n: string) => n[0]).join("") ?? "D"}
                      </span>
                    </div>
                    <div>
                      <p className="font-semibold text-sm">{d.user?.name ?? `Driver #${d.id}`}</p>
                      {d.user?.address && (
                        <p className="text-xs text-muted-foreground flex items-center gap-1">
                          <MapPin className="h-3 w-3" /> {d.user.address}
                        </p>
                      )}
                    </div>
                  </div>
                  <Badge className={`text-xs border ${STATUS_COLORS[d.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                    {d.status}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="flex items-center gap-1.5 text-muted-foreground">
                    <Star className="h-3.5 w-3.5 text-amber-500 fill-amber-500" />
                    <span>{d.rating > 0 ? d.rating.toFixed(1) : "No rating"}</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-muted-foreground">
                    <CheckCircle className="h-3.5 w-3.5 text-emerald-600" />
                    <span>{d.totalDeliveries} deliveries</span>
                  </div>
                  {d.yearsExperience && (
                    <div className="text-muted-foreground flex items-center gap-1">
                      <Shield className="h-3.5 w-3.5" />
                      {d.yearsExperience} yrs experience
                    </div>
                  )}
                  {d.successRate > 0 && (
                    <div className="text-muted-foreground flex items-center gap-1">
                      <Navigation className="h-3.5 w-3.5" />
                      {d.successRate?.toFixed(0)}% success rate
                    </div>
                  )}
                </div>

                {/* Vehicles */}
                {d.vehicles && d.vehicles.length > 0 && (
                  <div className="mt-3 pt-3 border-t space-y-1">
                    {d.vehicles.slice(0, 2).map((v: any) => (
                      <div key={v.id} className="flex items-center gap-2 text-xs text-muted-foreground">
                        <Truck className="h-3 w-3" />
                        <span className="capitalize">{v.truckType?.replace(/_/g, " ")}</span>
                        <span>·</span>
                        <span>{v.plateNumber}</span>
                        <span>·</span>
                        <span>{v.capacityTons}t</span>
                      </div>
                    ))}
                  </div>
                )}

                <div className="mt-3 pt-2 flex items-center gap-1.5 text-xs">
                  <div className={`h-2 w-2 rounded-full ${d.isAvailable ? "bg-emerald-500 animate-pulse" : "bg-gray-300"}`} />
                  <span className={d.isAvailable ? "text-emerald-600 font-medium" : "text-muted-foreground"}>
                    {d.isAvailable ? "Available now" : "Unavailable"}
                  </span>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
