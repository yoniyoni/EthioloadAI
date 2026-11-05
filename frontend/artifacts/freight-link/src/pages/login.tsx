import { useState } from "react";
import { Link, useLocation, useSearch } from "wouter";
import { useAuth } from "@/contexts/auth-context";
import { useLanguage } from "@/lib/i18n/language-context";
import { auth } from "@/lib/api";
import { getPostLoginDestination } from "@/lib/auth-redirect";
import { pendingInvitation } from "@/lib/invitation-session";
import { getLoginIdentifier, isValidPhoneIdentifier } from "@/lib/auth-identifiers";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Truck, Loader2, Eye, EyeOff, ArrowLeft, CheckCircle2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { motion } from "framer-motion";

export default function Login() {
  const { login } = useAuth();
  const { t } = useLanguage();
  const [, setLocation] = useLocation();
  const search = useSearch();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({ identifier: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [usePhone, setUsePhone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const identifier = getLoginIdentifier(form.identifier, usePhone);
    if (usePhone && !isValidPhoneIdentifier(form.identifier)) {
      toast({ title: "Invalid phone number", description: "Phone number must be valid.", variant: "destructive" });
      return;
    }
    setLoading(true);
    try {
      const data = await auth.post<{ token: string; user: any }>("/auth/login", { ...form, identifier });
      login(data.token, data.user);
      toast({ title: t("auth.loginTitle"), description: data.user.full_name ?? data.user.name });
      const role = (data.user.role ?? "").toLowerCase();
      setLocation(getPostLoginDestination(search, role, Boolean(pendingInvitation())));
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
      {/* Left side — brand panel */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden items-center justify-center"
        style={{ background: '#0F3D1A' }}
      >
        {/* Subtle dot grid overlay */}
        <div
          className="absolute inset-0 opacity-[0.06]"
          style={{
            backgroundImage: `radial-gradient(circle, rgba(255,255,255,0.8) 1px, transparent 1px)`,
            backgroundSize: '32px 32px',
          }}
        />
        {/* Amber glow top-right */}
        <div
          className="absolute top-0 right-0 w-96 h-96 rounded-full opacity-10 pointer-events-none"
          style={{ background: 'radial-gradient(circle, #F59E0B, transparent 70%)', transform: 'translate(30%, -30%)' }}
        />

        <motion.div
          className="relative z-10 text-white px-12 max-w-lg"
          initial={{ opacity: 0, x: -30 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.6 }}
        >
          {/* Logo */}
          <div className="flex items-center gap-3 mb-8">
            <div
              className="h-12 w-12 rounded-xl flex items-center justify-center"
              style={{ background: '#F59E0B' }}
            >
              <Truck className="h-6 w-6 text-white" />
            </div>
            <div>
              <span className="text-2xl font-bold block leading-none">EthioLoad AI</span>
              <span className="text-xs font-medium" style={{ color: '#F59E0B' }}>
                ምርጥ ጭነት — ምርጥ ዋጋ
              </span>
            </div>
          </div>

          {/* Badge */}
          <div
            className="inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-semibold mb-6"
            style={{
              border: '1px solid rgba(245,158,11,0.3)',
              background: 'rgba(245,158,11,0.1)',
              color: '#F59E0B',
            }}
          >
            <span
              className="flex h-2 w-2 rounded-full animate-pulse"
              style={{ background: '#F59E0B' }}
            />
            AI-Powered Logistics
          </div>

          <h2 className="text-3xl font-bold mb-4 leading-tight">Welcome Back</h2>
          <p className="text-lg leading-relaxed mb-8" style={{ color: 'rgba(255,255,255,0.6)' }}>
            Access your logistics dashboard to manage shipments and payments.
          </p>

          <div className="space-y-4">
            {leftItems.map((item, i) => (
              <div key={i} className="flex items-center gap-3">
                <div
                  className="h-6 w-6 rounded-full flex items-center justify-center shrink-0"
                  style={{ background: 'rgba(245,158,11,0.2)' }}
                >
                  <CheckCircle2 className="h-3 w-3" style={{ color: '#F59E0B' }} />
                </div>
                <span className="text-sm" style={{ color: 'rgba(255,255,255,0.75)' }}>{item}</span>
              </div>
            ))}
          </div>
        </motion.div>
      </div>

      {/* Right side - Form (unchanged) */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 bg-background">
        <motion.div
          className="w-full max-w-md"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <div className="mb-8 flex items-center">
            <Link href="/" className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
              <ArrowLeft className="h-4 w-4" />
              {t("nav.backHome")}
            </Link>
          </div>

          <div className="mb-8">
            <h1 className="text-2xl font-bold text-foreground mb-2">{t("auth.loginTitle")}</h1>
            <p className="text-muted-foreground">{t("auth.loginSubtitle")}</p>
          </div>

          {/* Toggle */}
          <div role="group" aria-label="Sign-in method" className="flex bg-muted rounded-lg p-1 mb-6">
            <button
              type="button"
              aria-pressed={!usePhone}
              className={`flex-1 rounded-md py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${!usePhone ? "bg-white text-foreground shadow-sm" : "text-muted-foreground"}`}
              onClick={() => setUsePhone(false)}
            >
              Email
            </button>
            <button
              type="button"
              aria-pressed={usePhone}
              className={`flex-1 rounded-md py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${usePhone ? "bg-white text-foreground shadow-sm" : "text-muted-foreground"}`}
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
                  value={form.identifier}
                  onChange={e => setForm(f => ({ ...f, identifier: e.target.value }))}
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
                    value={form.identifier}
                    onChange={e => setForm(f => ({ ...f, identifier: e.target.value }))}
                    required
                    className="h-11 rounded-l-none rounded-lg"
                  />
                </div>
              </div>
            )}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="password" className="text-sm font-medium">{t("auth.password")}</Label>
                <span className="text-sm text-muted-foreground">Password recovery is not currently available.</span>
              </div>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder={t("auth.password")}
                  value={form.password}
                  onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                  required
                  className="h-11 rounded-lg pr-12"
                />
                <div className="absolute right-2 top-1/2 z-10 -translate-y-1/2">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    aria-controls="password"
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword(!showPassword)}
                    className="text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </Button>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="remember" className="rounded border-input" />
              <Label htmlFor="remember" className="text-sm text-muted-foreground">Remember me</Label>
            </div>
            <Button type="submit" className="h-11 w-full rounded-lg font-semibold" disabled={loading}>
              {loading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />{t("common.loading")}</> : t("auth.signIn")}
            </Button>
          </form>

          {/* Trust badges */}
          <div className="mt-8 grid grid-cols-3 gap-2 text-center">
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full flex items-center justify-center"
                style={{ background: 'rgba(15,61,26,0.1)' }}>
                <CheckCircle2 className="h-4 w-4" style={{ color: '#0F3D1A' }} />
              </div>
              <p className="text-xs text-muted-foreground">Verified Drivers</p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full flex items-center justify-center"
                style={{ background: 'rgba(245,158,11,0.1)' }}>
                <Truck className="h-4 w-4" style={{ color: '#F59E0B' }} />
              </div>
              <p className="text-xs text-muted-foreground">AI Matching</p>
            </div>
            <div className="flex flex-col items-center gap-1">
              <div className="h-8 w-8 rounded-full flex items-center justify-center"
                style={{ background: 'rgba(15,61,26,0.1)' }}>
                <CheckCircle2 className="h-4 w-4" style={{ color: '#0F3D1A' }} />
              </div>
              <p className="text-xs text-muted-foreground">Secure Payments</p>
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
