import { useState, useMemo } from "react";
import { useLocation } from "wouter";
import { useMutation, useQueryClient, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useLanguage } from "@/lib/i18n/language-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { Skeleton } from "@/components/ui/skeleton";
import { Loader2, Package, ArrowLeft, Brain, Truck, Banknote, Sparkles, Zap, MapPin, Navigation } from "lucide-react";
import { Link } from "wouter";

const CARGO_TYPES = ["grain", "cement", "construction", "perishables", "electronics", "livestock", "fuel", "general", "other"];

const ETHIOPIAN_CITIES = [
  "Addis Ababa", "Adama", "Dire Dawa", "Gondar", "Bahir Dar",
  "Mekelle", "Hawassa", "Jimma", "Dessie", "Nekemte",
  "Harar", "Asela", "Shashemene", "Axum", "Sodo"
];

export default function FreightNew() {
  const [, setLocation] = useLocation();
  const { t } = useLanguage();
  const { toast } = useToast();
  const qc = useQueryClient();

  const [form, setForm] = useState({
    pickupLocation: "", deliveryLocation: "",
    cargoType: "general", cargoDescription: "",
    weightTons: "", volumeM3: "",
    budget: "", deadline: "",
    pickupLatitude: "", pickupLongitude: "",
    deliveryLatitude: "", deliveryLongitude: "",
    distanceKm: "",
  });

  const mutation = useMutation({
    mutationFn: () => api.post<any>("/freight", {
      pickup_location: form.pickupLocation,
      destination:     form.deliveryLocation,
      material_type:   form.cargoType,
      weight:          Number(form.weightTons),
      budget:          Number(form.budget),
      urgency_level:   "normal",
    }),
    onSuccess: (data: any) => {
      toast({ title: t("freight.create.post"), description: t("common.success") });
      qc.invalidateQueries({ queryKey: ["freight"] });
      const id = data?.data?.id ?? data?.id;
      setLocation(id ? `/freight/${id}` : "/freight");
    },
    onError: (err: any) => toast({ title: t("common.error"), description: err.message, variant: "destructive" }),
  });

  const set = (k: string, v: string) => setForm(f => ({ ...f, [k]: v }));

  const canPredict = useMemo(() => form.weightTons && form.distanceKm, [form.weightTons, form.distanceKm]);

  const { data: aiPrice, isLoading: priceLoading } = useQuery({
    queryKey: ["ai-price", form.weightTons, form.distanceKm, form.cargoType],
    queryFn: () => api.get<any>(`/ai/price-prediction?weight=${Number(form.weightTons)}&distance_km=${Number(form.distanceKm)}&cargo_type=${form.cargoType}`),
    enabled: !!canPredict,
  });

  const { data: aiVehicle, isLoading: vehicleLoading } = useQuery({
    queryKey: ["ai-vehicle", form.weightTons, form.cargoType, form.distanceKm],
    queryFn: () => api.get<any>(`/ai/vehicle-recommendation?weight=${Number(form.weightTons)}&cargo_type=${form.cargoType}&distance_km=${Number(form.distanceKm) || 300}`),
    enabled: !!canPredict,
  });

  return (
    <div className="container mx-auto max-w-2xl px-4 py-8">
      <Link href="/freight">
        <Button variant="ghost" className="gap-2 mb-6 -ml-2 rounded-lg">
          <ArrowLeft className="h-4 w-4" /> {t("nav.back")}
        </Button>
      </Link>

      <Card className="border-border/60 rounded-xl shadow-sm">
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
              <Package className="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle className="text-xl">{t("freight.create.title")}</CardTitle>
              <p className="text-sm text-muted-foreground">{t("freight.create.subtitle")}</p>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={e => { e.preventDefault(); mutation.mutate(); }}
            className="space-y-5"
          >
            {/* Route */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Pickup Location *</Label>
                <Input
                  placeholder="Addis Ababa, Merkato"
                  value={form.pickupLocation}
                  onChange={e => set("pickupLocation", e.target.value)}
                  required
                  className="rounded-lg"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Delivery Location *</Label>
                <Input
                  placeholder="Gondar, Amhara"
                  value={form.deliveryLocation}
                  onChange={e => set("deliveryLocation", e.target.value)}
                  required
                  className="rounded-lg"
                />
              </div>
            </div>

            {/* Distance */}
            <div className="space-y-1.5">
              <Label className="text-sm font-medium">Estimated Distance (km)</Label>
              <Input
                type="number"
                placeholder="e.g. 740"
                value={form.distanceKm}
                onChange={e => set("distanceKm", e.target.value)}
                className="rounded-lg"
              />
            </div>

            {/* Cargo */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Cargo Type *</Label>
                <Select value={form.cargoType} onValueChange={v => set("cargoType", v)}>
                  <SelectTrigger className="rounded-lg">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CARGO_TYPES.map(t => (
                      <SelectItem key={t} value={t}>{t.charAt(0).toUpperCase() + t.slice(1)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Weight (tons) *</Label>
                <Input
                  type="number"
                  step="0.1"
                  placeholder="e.g. 20"
                  value={form.weightTons}
                  onChange={e => set("weightTons", e.target.value)}
                  required
                  className="rounded-lg"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-sm font-medium">Volume (m³)</Label>
              <Input
                type="number"
                step="0.1"
                placeholder="Optional"
                value={form.volumeM3}
                onChange={e => set("volumeM3", e.target.value)}
                className="rounded-lg"
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-sm font-medium">Cargo Description</Label>
              <Textarea
                placeholder="Describe the cargo in detail (e.g. 20 tons of teff grain in sacks)…"
                value={form.cargoDescription}
                onChange={e => set("cargoDescription", e.target.value)}
                rows={3}
                className="rounded-lg"
              />
            </div>

            {/* AI Insights Panel */}
            {canPredict && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* AI Price Prediction */}
                <Card className="bg-gradient-to-br from-emerald-50 to-white border-emerald-200 rounded-xl">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                      <Sparkles className="h-4 w-4 text-emerald-600" />
                      AI Price Prediction
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    {priceLoading ? (
                      <Skeleton className="h-12 w-full rounded-lg" />
                    ) : aiPrice?.prediction ? (
                      <div className="space-y-2">
                        <div className="text-center">
                          <p className="text-2xl font-bold text-emerald-700">
                            ETB {Number(aiPrice.prediction.recommendedPrice).toLocaleString()}
                          </p>
                          <p className="text-xs text-muted-foreground">Recommended market price</p>
                        </div>
                        <div className="space-y-1 text-xs">
                          <div className="flex justify-between">
                            <span className="text-muted-foreground">Min</span>
                            <span className="font-medium">ETB {Number(aiPrice.prediction.minPrice).toLocaleString()}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-muted-foreground">Max</span>
                            <span className="font-medium">ETB {Number(aiPrice.prediction.maxPrice).toLocaleString()}</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-muted-foreground">Confidence</span>
                            <span className="font-medium">{Math.round((aiPrice.prediction.confidence || 0) * 100)}%</span>
                          </div>
                          <div className="flex justify-between">
                            <span className="text-muted-foreground">Model</span>
                            <span className="font-medium capitalize">{aiPrice.prediction.model}</span>
                          </div>
                        </div>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="w-full mt-1 rounded-lg"
                          onClick={() => {
                            set("budget", String(aiPrice.prediction.recommendedPrice));
                            toast({ title: "Budget updated", description: `Set to AI recommended ETB ${Number(aiPrice.prediction.recommendedPrice).toLocaleString()}` });
                          }}
                        >
                          <Banknote className="h-3 w-3 mr-1" />
                          Use this price
                        </Button>
                      </div>
                    ) : (
                      <p className="text-xs text-muted-foreground">No prediction available</p>
                    )}
                  </CardContent>
                </Card>

                {/* AI Vehicle Recommendation */}
                <Card className="bg-gradient-to-br from-primary/5 to-white border-primary/20 rounded-xl">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                      <Truck className="h-4 w-4 text-primary" />
                      AI Vehicle Recommendation
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    {vehicleLoading ? (
                      <Skeleton className="h-12 w-full rounded-lg" />
                    ) : aiVehicle?.recommendation ? (
                      <div className="space-y-2">
                        <div className="text-center">
                          <p className="text-lg font-bold text-primary">
                            {aiVehicle.recommendation.truckType}
                          </p>
                          <p className="text-xs text-muted-foreground">{aiVehicle.recommendation.capacityRange}</p>
                        </div>
                        <p className="text-xs text-muted-foreground">{aiVehicle.recommendation.reason}</p>
                        <div className="flex flex-wrap gap-1">
                          {(aiVehicle.recommendation.features || []).slice(0, 3).map((f: string) => (
                            <span key={f} className="text-xs bg-primary/10 text-primary px-1.5 py-0.5 rounded">
                              {f}
                            </span>
                          ))}
                        </div>
                        <div className="flex justify-between text-xs">
                          <span className="text-muted-foreground">Risk</span>
                          <span className={`font-medium capitalize ${
                            aiVehicle.recommendation.riskLevel === "low" ? "text-emerald-600" :
                            aiVehicle.recommendation.riskLevel === "medium" ? "text-sky-600" : "text-red-600"
                          }`}>{aiVehicle.recommendation.riskLevel}</span>
                        </div>
                      </div>
                    ) : (
                      <p className="text-xs text-muted-foreground">No recommendation available</p>
                    )}
                  </CardContent>
                </Card>
              </div>
            )}

            {/* Budget & Deadline */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Budget (ETB) *</Label>
                <Input
                  type="number"
                  placeholder="e.g. 85000"
                  value={form.budget}
                  onChange={e => set("budget", e.target.value)}
                  required
                  className="rounded-lg"
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Deadline</Label>
                <Input
                  type="date"
                  value={form.deadline}
                  onChange={e => set("deadline", e.target.value)}
                  min={new Date().toISOString().split("T")[0]}
                  className="rounded-lg"
                />
              </div>
            </div>

            <Button type="submit" className="w-full h-12 rounded-lg font-semibold bg-primary hover:bg-primary/90" size="lg" disabled={mutation.isPending}>
              {mutation.isPending
                ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Posting…</>
                : "Post Freight"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
