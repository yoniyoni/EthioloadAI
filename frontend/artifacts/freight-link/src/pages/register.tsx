import { useState } from "react";
import { Link, useLocation } from "wouter";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Truck, Loader2, Eye, EyeOff, ArrowLeft, CheckCircle2, Shield, Headphones, Clock, Globe, Moon } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { motion } from "framer-motion";

export default function Register() {
  const { login } = useAuth();
  const [, setLocation] = useLocation();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({ name: "", email: "", phone: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [step, setStep] = useState(0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const data = await api.post<{ token: string; user: any }>("/auth/register", {
        ...form,
        role: "shipper",
      });
      login(data.token, data.user);
      toast({ title: "Welcome aboard!", description: "Your shipper account is ready." });
      setLocation("/dashboard");
    } catch (err: any) {
      toast({ title: "Registration failed", description: err.message, variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  const steps = [
    { title: "Personal Info", label: "Step 1 of 5" },
    { title: "Contact", label: "Step 2 of 5" },
    { title: "Password", label: "Step 3 of 5" },
  ];

  const nextStep = () => setStep(s => Math.min(s + 1, 2));
  const prevStep = () => setStep(s => Math.max(s - 1, 0));

  return (
    <div className="min-h-screen flex">
      {/* Left side - Brand */}
      <div className="hidden lg:flex lg:w-1/2 bg-[#0c1e4a] relative overflow-hidden items-center justify-center">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_rgba(20,184,166,0.12)_0%,_transparent_60%)]" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_left,_rgba(59,130,246,0.1)_0%,_transparent_50%)]" />
        <div className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage: `linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px),
              linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)`,
            backgroundSize: '60px 60px'
          }}
        />
        <motion.div
          className="relative z-10 text-white px-12 max-w-lg"
          initial={{ opacity: 0, x: -30 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.6 }}
        >
          <div className="flex items-center gap-3 mb-8">
            <div className="h-12 w-12 rounded-xl bg-white flex items-center justify-center">
              <Truck className="h-6 w-6 text-[#0c1e4a]" />
            </div>
            <span className="text-2xl font-bold">ETHIO-FREIGHT</span>
          </div>
          <h2 className="text-3xl font-bold mb-4 leading-tight">
            Let's get started
          </h2>
          <p className="text-slate-400 text-lg leading-relaxed mb-8">
            Provide your personal details to secure your account.
          </p>
          <div className="space-y-4">
            {[
              "Secure SSL encryption",
              "GDPR Compliant",
              "24/7 Priority Support",
            ].map((item, i) => (
              <div key={i} className="flex items-center gap-3">
                <div className="h-6 w-6 rounded-full bg-emerald-400/20 flex items-center justify-center">
                  <CheckCircle2 className="h-3 w-3 text-emerald-400" />
                </div>
                <span className="text-slate-300 text-sm">{item}</span>
              </div>
            ))}
          </div>
        </motion.div>
      </div>

      {/* Right side - Form */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 bg-background">
        <motion.div
          className="w-full max-w-md"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <div className="flex items-center justify-between mb-6">
            <Link href="/" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
              <ArrowLeft className="h-4 w-4" />
              Back to home
            </Link>
            <div className="flex items-center gap-2">
              <button className="p-2 rounded-lg border border-border hover:bg-muted transition-colors">
                <Globe className="h-4 w-4" />
              </button>
              <button className="p-2 rounded-lg border border-border hover:bg-muted transition-colors">
                <Moon className="h-4 w-4" />
              </button>
            </div>
          </div>

          <div className="mb-6">
            <div className="flex items-center justify-between mb-2">
              <h1 className="text-xl font-bold text-foreground">{steps[step].title}</h1>
              <span className="text-sm text-muted-foreground">{steps[step].label}</span>
            </div>
            {/* Progress bar */}
            <div className="h-2 bg-muted rounded-full overflow-hidden">
              <div className="h-full bg-primary rounded-full transition-all" style={{ width: `${((step + 1) / 3) * 100}%` }} />
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {step === 0 && (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="name" className="text-sm font-medium">Full Name</Label>
                  <Input
                    id="name"
                    placeholder="e.g. Abebe Balcha"
                    value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                    required
                    className="h-11 rounded-lg"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="phone" className="text-sm font-medium">Phone Number</Label>
                  <div className="flex">
                    <div className="flex items-center px-3 bg-muted border border-r-0 border-input rounded-l-lg text-sm text-muted-foreground">
                      +251
                    </div>
                    <Input
                      id="phone"
                      type="tel"
                      placeholder="911 223 344"
                      value={form.phone}
                      onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
                      required
                      className="h-11 rounded-l-none rounded-lg"
                    />
                  </div>
                </div>
                <Button type="button" className="w-full h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white" onClick={nextStep}>
                  Next Step <ArrowLeft className="ml-2 h-4 w-4 rotate-180" />
                </Button>
              </div>
            )}

            {step === 1 && (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="email" className="text-sm font-medium">Email Address</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="name@company.com"
                    value={form.email}
                    onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                    required
                    className="h-11 rounded-lg"
                  />
                </div>
                <div className="flex gap-2">
                  <Button type="button" variant="outline" className="flex-1 h-11 rounded-lg" onClick={prevStep}>
                    <ArrowLeft className="mr-2 h-4 w-4" /> Back
                  </Button>
                  <Button type="button" className="flex-1 h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white" onClick={nextStep}>
                    Next <ArrowLeft className="ml-2 h-4 w-4 rotate-180" />
                  </Button>
                </div>
              </div>
            )}

            {step === 2 && (
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="password" className="text-sm font-medium">Password</Label>
                  <div className="relative">
                    <Input
                      id="password"
                      type={showPassword ? "text" : "password"}
                      placeholder="Create a strong password"
                      value={form.password}
                      onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                      required
                      minLength={6}
                      className="h-11 rounded-lg pr-10"
                    />
                    <button type="button" onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors">
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">Use 8+ characters with a mix of letters, numbers & symbols.</p>
                </div>
                <div className="flex gap-2">
                  <Button type="button" variant="outline" className="flex-1 h-11 rounded-lg" onClick={prevStep}>
                    <ArrowLeft className="mr-2 h-4 w-4" /> Back
                  </Button>
                  <Button type="submit" className="flex-1 h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white" disabled={loading}>
                    {loading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Creating…</> : "Create Account"}
                  </Button>
                </div>
              </div>
            )}
          </form>

          {/* Trust badges */}
          <div className="mt-8 grid grid-cols-3 gap-2 text-center">
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full bg-emerald-50 flex items-center justify-center">
                <Shield className="h-4 w-4 text-emerald-600" />
              </div>
              <p className="text-xs text-muted-foreground">Secure SSL</p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full bg-blue-50 flex items-center justify-center">
                <CheckCircle2 className="h-4 w-4 text-blue-600" />
              </div>
              <p className="text-xs text-muted-foreground">GDPR Compliant</p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full bg-teal-50 flex items-center justify-center">
                <Headphones className="h-4 w-4 text-teal-600" />
              </div>
              <p className="text-xs text-muted-foreground">24/7 Support</p>
            </div>
          </div>

          <div className="mt-6 rounded-xl bg-muted border border-border p-4">
            <div className="flex items-start gap-3">
              <div className="h-6 w-6 rounded-full bg-emerald-100 flex items-center justify-center mt-0.5 flex-shrink-0">
                <Truck className="h-3 w-3 text-emerald-600" />
              </div>
              <div>
                <p className="text-sm font-semibold text-foreground">Driver or Fleet Owner?</p>
                <p className="text-sm text-muted-foreground mt-0.5">
                  Registration is by admin invitation only. Contact{" "}
                  <a href="mailto:admin@ethiofreight.et" className="text-primary font-medium hover:underline">admin@ethiofreight.et</a>
                </p>
              </div>
            </div>
          </div>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            Already have an account?{" "}
            <Link href="/login" className="font-semibold text-foreground hover:text-primary transition-colors">
              Sign in
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
