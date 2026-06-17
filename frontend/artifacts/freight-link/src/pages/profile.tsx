import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import { User, Mail, Phone, Building2, MapPin, Loader2, Save, Shield } from "lucide-react";

export default function Profile() {
  const { user, login, token } = useAuth();
  const { toast } = useToast();
  const qc = useQueryClient();

  const [form, setForm] = useState({
    name: user?.name ?? "",
    phone: user?.phone ?? "",
    address: user?.address ?? "",
    businessName: user?.businessName ?? "",
  });

  const mutation = useMutation({
    mutationFn: () => api.patch<any>("/me", {
      name:          form.name,
      phone:         form.phone,
      address:       form.address || null,
      business_name: form.businessName || null,
    }),
    onSuccess: (updated) => {
      toast({ title: "Profile updated!" });
      if (token) login(token, { ...user!, ...updated });
      qc.invalidateQueries({ queryKey: ["me"] });
    },
    onError: (err: any) => toast({ title: "Failed", description: err.message, variant: "destructive" }),
  });

  const set = (k: string, v: string) => setForm(f => ({ ...f, [k]: v }));

  const ROLE_COLORS: Record<string, string> = {
    admin: "bg-red-50 text-red-700 border-red-200",
    shipper: "bg-blue-50 text-blue-700 border-blue-200",
    driver: "bg-emerald-50 text-emerald-700 border-emerald-200",
    fleet_owner: "bg-purple-50 text-purple-700 border-purple-200",
  };

  return (
    <div className="container mx-auto max-w-2xl px-4 py-8">
      <div className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight">My Profile</h1>
        <p className="text-muted-foreground mt-1">Manage your account details</p>
      </div>

      <div className="space-y-6">
        {/* Account summary */}
        <Card className="border-border/60 rounded-xl overflow-hidden">
          <div className="bg-gradient-to-r from-[#0c1e4a] to-[#1a3a6e] px-6 py-5">
            <div className="flex items-center gap-4">
              <div className="h-16 w-16 rounded-full bg-white/10 flex items-center justify-center ring-4 ring-white/5">
                <span className="text-2xl font-bold text-white">{user?.name?.charAt(0)}</span>
              </div>
              <div>
                <p className="text-xl font-bold text-white">{user?.name}</p>
                <p className="text-sm text-slate-300 flex items-center gap-1">
                  <Mail className="h-3 w-3" /> {user?.email}
                </p>
                <div className="flex items-center gap-2 mt-1">
                  <Badge className={`border ${ROLE_COLORS[user?.role ?? ""] ?? "bg-white/10 text-white border-white/20"}`}>
                    {user?.role}
                  </Badge>
                  {user?.isVerified && (
                    <Badge className="bg-emerald-50 text-emerald-700 border-emerald-200 gap-1">
                      <Shield className="h-3 w-3" /> Verified
                    </Badge>
                  )}
                </div>
              </div>
            </div>
          </div>
        </Card>

        {/* Edit form */}
        <Card className="border-border/60 rounded-xl">
          <CardHeader>
            <CardTitle className="text-base">Edit Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1.5">
              <Label className="flex items-center gap-1.5 text-sm font-medium"><User className="h-4 w-4" /> Full Name</Label>
              <Input value={form.name} onChange={e => set("name", e.target.value)} className="rounded-lg" />
            </div>
            <div className="space-y-1.5">
              <Label className="flex items-center gap-1.5 text-sm font-medium"><Phone className="h-4 w-4" /> Phone</Label>
              <Input value={form.phone} onChange={e => set("phone", e.target.value)} className="rounded-lg" />
            </div>
            <div className="space-y-1.5">
              <Label className="flex items-center gap-1.5 text-sm font-medium"><MapPin className="h-4 w-4" /> Address</Label>
              <Input placeholder="City, Region" value={form.address} onChange={e => set("address", e.target.value)} className="rounded-lg" />
            </div>
            {(user?.role === "shipper" || user?.role === "fleet_owner") && (
              <div className="space-y-1.5">
                <Label className="flex items-center gap-1.5 text-sm font-medium"><Building2 className="h-4 w-4" /> Business Name</Label>
                <Input placeholder="Company or trading name" value={form.businessName}
                  onChange={e => set("businessName", e.target.value)} className="rounded-lg" />
              </div>
            )}
            <Button onClick={() => mutation.mutate()} disabled={mutation.isPending} className="gap-2 rounded-lg">
              {mutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Save Changes
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
