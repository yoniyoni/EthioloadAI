import { useState } from "react";
import { Link, useLocation } from "wouter";
import { useAuth } from "@/contexts/auth-context";
import { useLanguage } from "@/lib/i18n/language-context";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Truck, Loader2, Eye, EyeOff, ArrowLeft, Globe, Moon, CheckCircle2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { motion } from "framer-motion";

export default function Login() {
  const { login } = useAuth();
  const { t } = useLanguage();
  const [, setLocation] = useLocation();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({ email: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [usePhone, setUsePhone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const data = await api.post<{ token: string; user: any }>("/auth/login", form);
      login(data.token, data.user);
      toast({ title: t("auth.loginTitle"), description: data.user.name });
      setLocation("/dashboard");
    } catch (err: any) {
      toast({ title: t("auth.login") + " " + t("common.error"), description: err.message, variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  const leftItems = [
    "Verified Drivers",
    "AI Matching",
    "Secure Payments",
    "Live Tracking",
  ];

  return (
    <div className="min-h-screen flex">
      {/* Left side - Brand & Visual */}
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
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-400/30 bg-emerald-400/10 px-4 py-1.5 text-sm font-semibold text-emerald-400 mb-6">
            <span className="flex h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
            Institutional Innovation
          </div>
          <h2 className="text-3xl font-bold mb-4 leading-tight">
            Welcome Back
          </h2>
          <p className="text-slate-400 text-lg leading-relaxed mb-8">
            Access your logistics dashboard to manage shipments and payments.
          </p>
          <div className="space-y-4">
            {leftItems.map((item, i) => (
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
          <div className="flex items-center justify-between mb-8">
            <Link href="/" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
              <ArrowLeft className="h-4 w-4" />
              {t("nav.backHome")}
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

          <div className="mb-8">
            <h1 className="text-2xl font-bold text-foreground mb-2">{t("auth.loginTitle")}</h1>
            <p className="text-muted-foreground">{t("auth.loginSubtitle")}</p>
          </div>

          {/* Toggle */}
          <div className="flex bg-muted rounded-lg p-1 mb-6">
            <button
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${!usePhone ? "bg-white text-foreground shadow-sm" : "text-muted-foreground"}`}
              onClick={() => setUsePhone(false)}
            >
              Email
            </button>
            <button
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${usePhone ? "bg-white text-foreground shadow-sm" : "text-muted-foreground"}`}
              onClick={() => setUsePhone(true)}
            >
              Phone
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {!usePhone ? (
              <div className="space-y-2">
                <Label htmlFor="email" className="text-sm font-medium">{t("auth.email")}</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="you@example.com"
                  value={form.email}
                  onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                  required
                  className="h-11 rounded-lg"
                />
              </div>
            ) : (
              <div className="space-y-2">
                <Label htmlFor="phone" className="text-sm font-medium">Phone Number</Label>
                <div className="flex">
                  <div className="flex items-center px-3 bg-muted border border-r-0 border-input rounded-l-lg text-sm text-muted-foreground">
                    +251
                  </div>
                  <Input
                    id="phone"
                    type="tel"
                    placeholder="911 234 567"
                    value={form.email}
                    onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                    className="h-11 rounded-l-none rounded-lg"
                  />
                </div>
              </div>
            )}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="password" className="text-sm font-medium">{t("auth.password")}</Label>
                <Link href="/" className="text-sm text-primary hover:underline">
                  Forgot Password?
                </Link>
              </div>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder={t("auth.password")}
                  value={form.password}
                  onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                  required
                  className="h-11 rounded-lg pr-10"
                />
                <button type="button" onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors">
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="remember" className="rounded border-input" />
              <Label htmlFor="remember" className="text-sm text-muted-foreground">Remember me</Label>
            </div>
            <Button type="submit" className="w-full h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white" disabled={loading}>
              {loading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />{t("common.loading")}</> : t("auth.signIn")}
            </Button>
          </form>

          {/* Social Login */}
          <div className="mt-8">
            <div className="relative mb-4">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-border" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-background px-2 text-muted-foreground">{t("auth.orContinueWith")}</span>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <button className="flex items-center justify-center gap-2 h-10 rounded-lg border border-border hover:bg-muted transition-colors text-sm">
                <svg className="h-4 w-4" viewBox="0 0 24 24"><path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/><path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
                Google
              </button>
              <button className="flex items-center justify-center gap-2 h-10 rounded-lg border border-border hover:bg-muted transition-colors text-sm">
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor"><path d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.74 1.18 0 2.21-.96 3.56-.82 1.51.13 2.54.74 3.28 1.88-2.85 1.76-2.35 5.86.22 6.88-.52 1.57-.84 3.12-1.84 4.29zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"/></svg>
                Apple
              </button>
            </div>
          </div>

          {/* Trust badges */}
          <div className="mt-8 grid grid-cols-3 gap-2 text-center">
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full bg-emerald-50 flex items-center justify-center">
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
              </div>
              <p className="text-xs text-muted-foreground">Verified Drivers</p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full bg-blue-50 flex items-center justify-center">
                <Truck className="h-4 w-4 text-blue-600" />
              </div>
              <p className="text-xs text-muted-foreground">AI Matching</p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full bg-teal-50 flex items-center justify-center">
                <CheckCircle2 className="h-4 w-4 text-teal-600" />
              </div>
              <p className="text-xs text-muted-foreground">Secure Payments</p>
            </div>
          </div>

          {/* Demo accounts */}
          <div className="mt-6 rounded-xl bg-muted border border-border p-4">
            <p className="text-xs font-semibold text-muted-foreground mb-2 uppercase tracking-wider">{t("auth.demoAccounts")}</p>
            <div className="space-y-1.5 text-sm">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-emerald-600 bg-emerald-100 px-2 py-0.5 rounded">ADMIN</span>
                <span className="text-muted-foreground font-mono text-xs">admin@freightlink.et / admin123</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-blue-600 bg-blue-100 px-2 py-0.5 rounded">SHIPPER</span>
                <span className="text-muted-foreground font-mono text-xs">tigist@shipper.et / shipper123</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-teal-600 bg-teal-100 px-2 py-0.5 rounded">DRIVER</span>
                <span className="text-muted-foreground font-mono text-xs">bekele@driver.et / driver123</span>
              </div>
            </div>
          </div>

          <p className="mt-6 text-center text-sm text-muted-foreground">
            {t("auth.noAccount")}{" "}
            <Link href="/register" className="font-semibold text-foreground hover:text-primary transition-colors">
              {t("auth.createOne")}
            </Link>
          </p>
        </motion.div>
      </div>
    </div>
  );
}
