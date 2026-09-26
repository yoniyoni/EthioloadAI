import { useQuery } from "@tanstack/react-query";
import { Link } from "wouter";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";

export default function Bids() {
  const { user } = useAuth();
  const isDriver = user?.role === "driver";
  const { data, isLoading, isError } = useQuery({
    queryKey: ["my-bids"],
    queryFn: () => api.get<{ content: any[] }>("/driver/bids"),
    enabled: isDriver,
  });
  const bids = Array.isArray(data?.content) ? data.content : (Array.isArray(data) ? data : []);

  if (!isDriver) {
    return <div className="container mx-auto max-w-4xl px-4 py-16 text-center"><p className="text-lg font-medium">Bids are available from the driver account.</p></div>;
  }

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div><h1 className="text-3xl font-bold tracking-tight">Bid History</h1><p className="text-muted-foreground mt-1">Your submitted freight bids</p></div>
        <Link href="/freight"><Button variant="outline" className="rounded-lg">Browse Freight</Button></Link>
      </div>
      {isLoading ? <Skeleton className="h-32 w-full rounded-xl" /> : isError ? <p className="text-sm text-destructive">Unable to load bids.</p> : bids.length === 0 ? <p className="text-muted-foreground py-16 text-center">No bids yet.</p> : (
        <div className="space-y-3">
          {bids.map((bid: any) => (
            <Card key={bid.id} className="rounded-xl border-border/60">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-base">
                      {bid.pickupLocation && bid.destination ? `${bid.pickupLocation} → ${bid.destination}` : (bid.vehicleType ?? "Vehicle")}
                      {bid.vehiclePlateNumber ? ` · ${bid.vehiclePlateNumber}` : ""}
                    </CardTitle>
                    {bid.pickupLocation && bid.destination && (
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {bid.vehicleType ?? "Vehicle"} {bid.materialType ? `· ${bid.materialType}` : ""} {bid.weightTons ? `· ${bid.weightTons}t` : ""}
                      </p>
                    )}
                  </div>
                  <Badge variant={bid.status === "countered" ? "secondary" : "outline"} className="capitalize">
                    {bid.status}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex items-center gap-3">
                  <div>
                    <span className="text-xs text-muted-foreground">Your Bid: </span>
                    <span className={`font-semibold ${bid.status === "countered" && bid.counterBy === "shipper" ? "line-through text-muted-foreground text-sm" : ""}`}>
                      ETB {Number(bid.amount).toLocaleString()}
                    </span>
                  </div>
                  {bid.status === "countered" && bid.counterAmount != null && (
                    <div className="px-2.5 py-1 rounded-md bg-purple-50 border border-purple-200 text-xs">
                      <span className="font-semibold text-purple-700">
                        {bid.counterBy === "shipper" ? "Shipper counter: " : "Your counter: "}
                      </span>
                      <span className="font-bold text-purple-800">
                        ETB {Number(bid.counterAmount).toLocaleString()}
                      </span>
                      {bid.counterNote && <span className="text-purple-600"> · "{bid.counterNote}"</span>}
                    </div>
                  )}
                </div>
                {bid.note && <p className="text-sm text-muted-foreground mt-1">{bid.note}</p>}
                <Link href={`/freight/${bid.freightId}`}>
                  <Button variant="link" className="px-0 mt-2">
                    {bid.status === "countered" && bid.counterBy === "shipper" ? "View freight & Respond" : "View freight"}
                  </Button>
                </Link>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
