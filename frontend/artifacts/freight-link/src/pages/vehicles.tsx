import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { Truck, Plus, Loader2, Scale, Fuel, CheckCircle2, XCircle } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";

const TRUCK_TYPES = ["pickup", "light_truck", "medium_truck", "heavy_truck", "tanker", "refrigerated", "flatbed", "tipper"];

export default function Vehicles() {
  const { toast } = useToast();
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({
    truckType: "medium_truck", plateNumber: "",
    capacityTons: "", volumeM3: "", fuelType: "diesel", currentCity: "Addis Ababa",
  });

  const { data, isLoading } = useQuery({
    queryKey: ["my-vehicles"],
    queryFn: () => api.get<{ vehicles: any[] }>("/my-vehicles"),
  });

  const createMutation = useMutation({
    mutationFn: () => api.post("/vehicles", {
      truck_type:   form.truckType,
      plate_number: form.plateNumber,
      capacity:     Number(form.capacityTons),
      current_city: form.currentCity,
    }),
    onSuccess: () => {
      toast({ title: "Vehicle added!" });
      qc.invalidateQueries({ queryKey: ["my-vehicles"] });
      setOpen(false);
      setForm({ truckType: "medium_truck", plateNumber: "", capacityTons: "", volumeM3: "", fuelType: "diesel", currentCity: "Addis Ababa" });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const vehicles = data?.vehicles ?? [];
  const set = (k: string, v: string) => setForm(f => ({ ...f, [k]: v }));

  return (
    <div className="container mx-auto max-w-4xl px-4 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">My Vehicles</h1>
          <p className="text-muted-foreground mt-1">Manage your registered trucks</p>
        </div>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button className="gap-2 rounded-lg bg-primary hover:bg-primary/90"><Plus className="h-4 w-4" /> Add Vehicle</Button>
          </DialogTrigger>
          <DialogContent className="rounded-xl">
            <DialogHeader>
              <DialogTitle>Add New Vehicle</DialogTitle>
            </DialogHeader>
            <div className="space-y-4 pt-2">
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Truck Type</Label>
                <Select value={form.truckType} onValueChange={v => set("truckType", v)}>
                  <SelectTrigger className="rounded-lg"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {TRUCK_TYPES.map(t => (
                      <SelectItem key={t} value={t}>{t.replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase())}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Plate Number *</Label>
                <Input placeholder="AA-12345" value={form.plateNumber}
                  onChange={e => set("plateNumber", e.target.value)} className="rounded-lg" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">Capacity (tons) *</Label>
                  <Input type="number" step="0.1" placeholder="e.g. 20" value={form.capacityTons}
                    onChange={e => set("capacityTons", e.target.value)} className="rounded-lg" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-sm font-medium">Volume (m³)</Label>
                  <Input type="number" step="0.1" placeholder="Optional" value={form.volumeM3}
                    onChange={e => set("volumeM3", e.target.value)} className="rounded-lg" />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">Current City *</Label>
                <Input placeholder="Addis Ababa" value={form.currentCity}
                  onChange={e => set("currentCity", e.target.value)} className="rounded-lg" />
              </div>
              <Button className="w-full rounded-lg" onClick={() => createMutation.mutate()}
                disabled={!form.plateNumber || !form.capacityTons || !form.currentCity || createMutation.isPending}>
                {createMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : null}
                Add Vehicle
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {isLoading ? (
        <div className="grid md:grid-cols-2 gap-4">
          {Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-48 w-full rounded-xl" />)}
        </div>
      ) : vehicles.length === 0 ? (
        <div className="text-center py-24">
          <div className="h-16 w-16 rounded-xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
            <Truck className="h-8 w-8 text-primary" />
          </div>
          <p className="text-lg font-medium">No vehicles registered</p>
          <p className="text-sm text-muted-foreground mb-4">Add your first truck to start receiving freight</p>
          <Button onClick={() => setOpen(true)} className="gap-2 rounded-lg bg-primary hover:bg-primary/90">
            <Plus className="h-4 w-4" /> Add Your First Vehicle
          </Button>
        </div>
      ) : (
        <div className="grid md:grid-cols-2 gap-4">
          {vehicles.map((v: any) => (
            <Card key={v.id} className="hover:shadow-md transition-shadow border-border/60 rounded-xl">
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
                      <Truck className="h-5 w-5 text-primary" />
                    </div>
                    <div>
                      <CardTitle className="text-base">{v.plateNumber}</CardTitle>
                      <p className="text-xs text-muted-foreground capitalize">
                        {v.truckType?.replace(/_/g, " ")}
                      </p>
                    </div>
                  </div>
                  {v.isAvailable ? (
                    <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200 gap-1">
                      <CheckCircle2 className="h-3 w-3" /> Available
                    </Badge>
                  ) : (
                    <Badge variant="outline" className="text-muted-foreground gap-1">
                      <XCircle className="h-3 w-3" /> Unavailable
                    </Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="flex items-center gap-4 text-sm">
                  <span className="flex items-center gap-1.5 text-muted-foreground">
                    <Scale className="h-4 w-4" /> {v.capacityTons}t capacity
                  </span>
                  {v.volumeM3 && (
                    <span className="text-muted-foreground">{v.volumeM3}m³</span>
                  )}
                </div>
                {v.currentCity && (
                  <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                    <Fuel className="h-4 w-4" />
                    <span>{v.currentCity}</span>
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
