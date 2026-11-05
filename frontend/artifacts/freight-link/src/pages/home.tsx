import { Link } from "wouter";
import { Button } from "@/components/ui/button";
import {
  ArrowRight, BarChart3, Globe, ShieldCheck, Truck, Users,
  Zap, Package, TrendingUp, ChevronRight, Star, Clock,
  Route, CheckCircle2, Shield, CreditCard, Headphones, Award, MessageCircle
} from "lucide-react";
import { motion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { CinematicTransport } from "@/components/cinematic-transport";

function FadeIn({ children, delay = 0, className = "" }: { children: React.ReactNode; delay?: number; className?: string }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.7, delay, ease: [0.22, 1, 0.36, 1] }}
      className={className}
    >
      {children}
    </motion.div>
  );
}

export default function Home() {
  const heroRef = useRef(null);
  const { scrollYProgress } = useScroll({
    target: heroRef,
    offset: ["start start", "end start"],
  });
  const heroY = useTransform(scrollYProgress, [0, 1], [0, 150]);
  const heroOpacity = useTransform(scrollYProgress, [0, 0.8], [1, 0]);

  return (
    <div className="flex flex-col">
      {/* Hero Section - Full-bleed Addis map */}
      <section ref={heroRef} className="relative flex min-h-[100dvh] w-full items-end overflow-hidden bg-[#061321] text-white">
        <CinematicTransport className="pointer-events-none opacity-100" />
        <div className="pointer-events-none absolute inset-0 z-[1] bg-[linear-gradient(90deg,rgba(2,10,20,0.88)_0%,rgba(2,10,20,0.5)_42%,rgba(2,10,20,0.08)_80%),linear-gradient(0deg,rgba(2,10,20,0.92)_0%,rgba(2,10,20,0.04)_62%)]" />

        <motion.div style={{ y: heroY, opacity: heroOpacity }} className="relative z-10 w-full px-5 pb-16 pt-32 sm:px-8 sm:pb-20 md:px-12 lg:px-16">
          <div className="grid gap-12 items-end">
            {/* Left - Copy */}
            <div className="flex max-w-2xl flex-col gap-8">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6 }}
              >
                <p className="mb-3 text-xs font-semibold uppercase tracking-[0.24em] text-cyan-200/80">Ethiopia-wide freight network</p>
                <h1 className="mb-5 text-5xl font-black uppercase leading-[0.92] tracking-[-0.02em] text-white drop-shadow-[0_3px_8px_rgba(0,0,0,0.7)] sm:text-6xl md:text-7xl lg:text-[5rem]">
                  Ethio-<span className="text-cyan-300">Freight</span>
                </h1>
                <p className="max-w-lg text-base font-medium leading-relaxed text-white/85 drop-shadow-[0_2px_5px_rgba(0,0,0,0.8)] sm:text-lg">
                  Move freight across Ethiopia — from warehouse to warehouse, district to district, and city to city with verified local drivers.
                </p>
                <p className="mt-4 text-xs font-semibold uppercase tracking-[0.18em] text-white/60">Built for freight movement across Ethiopia</p>
              </motion.div>

              <motion.div
                className="flex flex-col gap-3 sm:flex-row"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                <Link href="/register">
                  <Button size="lg" className="h-12 rounded-lg bg-cyan-400 px-7 text-sm font-bold text-[#062033] shadow-lg shadow-cyan-950/30 hover:bg-cyan-300">
                    Get Started
                    <ArrowRight className="ml-2 h-5 w-5" />
                  </Button>
                </Link>
              </motion.div>

            </div>

          </div>
        </motion.div>

        <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-background to-transparent" />
      </section>

      {/* Freight capabilities */}
      <section className="relative z-10 w-full border-b border-slate-200 bg-white py-12 md:py-14">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3">
            {[
              { title: "Role-aware workflows", description: "Tools shaped around your account permissions.", icon: Users },
              { title: "Shipment coordination", description: "Manage freight activity through one workspace.", icon: Route },
              { title: "Company operations", description: "Coordinate fleet resources and assignments.", icon: Clock },
            ].map((feature, i) => (
              <FadeIn key={feature.title} delay={i * 0.1}>
                <div className="flex h-full items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white">
                    <feature.icon className="h-5 w-5 text-slate-600" />
                  </div>
                  <div>
                    <p className="font-semibold text-slate-900">{feature.title}</p>
                    <p className="mt-1 text-sm text-slate-500">{feature.description}</p>
                  </div>
                </div>
              </FadeIn>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="w-full border-b border-slate-200 bg-slate-50/80 py-20 md:py-24">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <FadeIn className="text-center mb-16 max-w-2xl mx-auto">
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-slate-900 mb-4">How Ethio-Freight Works</h2>
            <p className="text-lg text-slate-600">Three simple steps to get your cargo moving. Zero paperwork, instant matching.</p>
          </FadeIn>

          <div className="grid gap-5 md:grid-cols-3">
            {[
              {
                step: "01",
                icon: Package,
                title: "Post Your Load",
                desc: "Enter your cargo details, pickup and delivery locations. Our AI instantly estimates a fair market price.",
                color: "bg-emerald-100 text-emerald-600",
              },
              {
                step: "02",
                icon: Truck,
                title: "Get Matched",
                desc: "Verified drivers and fleet owners see your load and submit bids. Pick the best offer based on price, rating, and ETA.",
                color: "bg-blue-100 text-blue-600",
              },
              {
                step: "03",
                icon: Globe,
                title: "Track & Pay",
                desc: "Track your shipment live from pickup to delivery. Release payment securely via escrow when complete.",
                color: "bg-teal-100 text-teal-600",
              },
            ].map((item, i) => (
              <FadeIn key={i} delay={i * 0.15}>
                <div className="group relative rounded-xl border border-slate-200 bg-white p-7 shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-slate-300 hover:shadow-md">
                  <div className="absolute top-4 right-4 text-6xl font-extrabold text-slate-100 group-hover:text-slate-200 transition-colors">
                    {item.step}
                  </div>
                  <div className={`mb-6 flex h-12 w-12 items-center justify-center rounded-lg ${item.color} relative z-10`}>
                    <item.icon className="h-7 w-7" />
                  </div>
                  <h3 className="text-xl font-bold text-slate-900 mb-3 relative z-10">{item.title}</h3>
                  <p className="text-slate-600 leading-relaxed relative z-10">{item.desc}</p>
                </div>
              </FadeIn>
            ))}
          </div>
        </div>
      </section>

      {/* AI Features */}
      <section className="w-full border-b border-slate-200 bg-white py-20 md:py-24">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <FadeIn className="text-center mb-16 max-w-2xl mx-auto">
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-slate-900 mb-4">Built for Scale, Designed for Trust</h2>
            <p className="text-lg text-slate-600">Every feature is built to make logistics simpler, faster, and more transparent.</p>
          </FadeIn>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[
              { icon: BarChart3, title: "AI Price Prediction", desc: "Machine learning models analyze routes, fuel costs, and cargo types to recommend optimal pricing.", color: "bg-emerald-50 text-emerald-600" },
              { icon: ShieldCheck, title: "Verified Network", desc: "Every driver undergoes rigorous background checks, license verification, and vehicle inspections.", color: "bg-teal-50 text-teal-600" },
              { icon: Globe, title: "Live GPS Tracking", desc: "End-to-end visibility with real-time location, speed, and estimated arrival times for every shipment.", color: "bg-blue-50 text-blue-600" },
              { icon: Zap, title: "Instant Matching", desc: "Get matched with available drivers in under 60 seconds. No phone calls, no middlemen.", color: "bg-sky-50 text-sky-600" },
              { icon: Star, title: "Rating & Reviews", desc: "Transparent two-way rating system ensures accountability and quality on every delivery.", color: "bg-violet-50 text-violet-600" },
              { icon: TrendingUp, title: "Analytics Dashboard", desc: "Track your shipping costs, delivery times, and driver performance with detailed insights.", color: "bg-indigo-50 text-indigo-600" },
            ].map((feat, i) => (
              <FadeIn key={i} delay={i * 0.08}>
                <div className="rounded-xl border border-slate-200 bg-white p-6 transition-all duration-300 hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-sm group">
                  <div className={`mb-4 flex h-10 w-10 items-center justify-center rounded-lg ${feat.color}`}>
                    <feat.icon className="h-5 w-5" />
                  </div>
                  <h3 className="text-lg font-bold text-slate-900 mb-2">{feat.title}</h3>
                  <p className="text-sm text-slate-600 leading-relaxed">{feat.desc}</p>
                </div>
              </FadeIn>
            ))}
          </div>
        </div>
      </section>

      {/* Trust & Payment Section */}
      <section className="w-full border-b border-slate-200 bg-slate-50/80 py-20 md:py-24">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <div className="grid md:grid-cols-2 gap-12 items-center">
            <FadeIn>
              <div className="space-y-6">
                <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-slate-900">
                  Secure Payments &<br />Escrow Protection
                </h2>
                <p className="text-lg text-slate-600">
                  Your payment is held in a secure, audited escrow account. It only releases after the recipient confirms cargo integrity.
                </p>
                <div className="space-y-3">
                  {[
                    { icon: CreditCard, title: "Chapa", desc: "Cards, Apple Pay, Google Pay" },
                    { icon: CreditCard, title: "CBE Birr", desc: "Direct wallet-to-wallet transfer" },
                    { icon: CreditCard, title: "Telebirr", desc: "Mobile banking interface" },
                  ].map((item, i) => (
                    <div key={i} className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
                      <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
                        <item.icon className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <p className="font-semibold text-slate-900">{item.title}</p>
                        <p className="text-sm text-slate-500">{item.desc}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </FadeIn>
            <FadeIn delay={0.2}>
              <div className="relative overflow-hidden rounded-2xl border border-slate-700/50 bg-[#0c1e4a] p-8 text-white shadow-xl">
                <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_rgba(20,184,166,0.2)_0%,_transparent_60%)]" />
                <div className="relative z-10 space-y-6">
                  <div className="flex items-center gap-2 text-sm text-emerald-400">
                    <Shield className="h-4 w-4" />
                    <span className="font-semibold">Escrow Protected</span>
                  </div>
                  <div className="space-y-2">
                    <p className="text-sm text-slate-400">Private freight workspace</p>
                    <p className="text-xl font-bold">Details for authorized users</p>
                    <p className="text-sm leading-relaxed text-slate-300">
                      Sign in to access freight listings and shipment information.
                    </p>
                  </div>
                  <div className="grid grid-cols-2 gap-4 pt-4">
                    <div className="flex items-center gap-2 text-sm">
                      <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                      <span>PCI-DSS Compliant</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm">
                      <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                      <span>End-to-End Encrypted</span>
                    </div>
                  </div>
                </div>
              </div>
            </FadeIn>
          </div>
        </div>
      </section>

      {/* Role workflows */}
      <section className="relative w-full overflow-hidden bg-[#0c1e4a] py-20 text-white md:py-24">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_rgba(20,184,166,0.08)_0%,_transparent_50%)]" />
        <div className="container px-4 md:px-6 mx-auto max-w-6xl relative z-10">
          <FadeIn className="text-center mb-16">
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight mb-4">Tools for every freight role</h2>
            <p className="text-lg text-slate-400">Coordinate freight through role-aware, authenticated workspaces.</p>
          </FadeIn>

          <div className="grid md:grid-cols-3 gap-6">
            {[
              { title: "Shippers", description: "Create freight requests and coordinate shipments.", icon: Package },
              { title: "Drivers", description: "Manage assignments and track active shipments.", icon: Truck },
              { title: "Fleet owners", description: "Coordinate company drivers, vehicles and dispatch activity.", icon: Users },
            ].map((t, i) => (
              <FadeIn key={t.title} delay={i * 0.15}>
                <div className="h-full rounded-xl border border-white/10 bg-white/[0.06] p-6 transition-all duration-300 hover:bg-white/[0.1]">
                  <div className="mb-5 flex h-10 w-10 items-center justify-center rounded-lg bg-white/10">
                    <t.icon className="h-5 w-5 text-emerald-300" />
                  </div>
                  <h3 className="mb-2 text-lg font-semibold text-white">{t.title}</h3>
                  <p className="text-sm leading-relaxed text-slate-300">{t.description}</p>
                </div>
              </FadeIn>
            ))}
          </div>
        </div>
      </section>

      {/* Registration CTA */}
      <section className="w-full bg-white py-20 md:py-24">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <div className="mx-auto max-w-3xl">
            <FadeIn>
              <h2 className="text-3xl md:text-5xl font-extrabold tracking-tight text-slate-900 mb-6">
                Ready to Move Smarter?
              </h2>
              <p className="text-xl text-slate-600 mb-10 max-w-xl">
                Bring your freight coordination into one role-aware workspace.
              </p>
              <div className="flex flex-col sm:flex-row gap-4">
                <Link href="/register">
                  <Button size="lg" className="h-12 rounded-lg bg-primary px-7 text-sm font-bold text-white transition-all hover:bg-primary/90 hover:shadow-md hover:shadow-primary/20">
                    Create Free Account
                    <ChevronRight className="ml-2 h-5 w-5" />
                  </Button>
                </Link>
              </div>
              <p className="text-sm text-slate-500 mt-6">Free for shippers. No credit card required.</p>
            </FadeIn>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="w-full py-12 bg-[#0a1635] border-t border-white/5">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <div className="grid md:grid-cols-4 gap-8 mb-10">
            <div className="md:col-span-2">
              <div className="flex items-center gap-2 mb-4">
                <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center">
                  <Truck className="h-4 w-4 text-white" />
                </div>
                <span className="text-lg font-bold text-white">ETHIO-FREIGHT</span>
              </div>
              <p className="text-sm text-slate-400 max-w-sm">
                The AI-powered logistics platform connecting Ethiopia. Verified drivers, real-time tracking, and secure payments.
              </p>
            </div>
            <div>
              <p className="text-sm font-semibold text-white mb-4">Product</p>
              <div className="space-y-2 text-sm text-slate-400">
                <Link href="/drivers" className="block hover:text-white transition-colors">Drivers</Link>
                <Link href="/register" className="block hover:text-white transition-colors">Get Started</Link>
              </div>
            </div>
            <div>
              <p className="text-sm font-semibold text-white mb-4">Support</p>
              <div className="space-y-2 text-sm text-slate-400">
                <p className="flex items-center gap-2"><Headphones className="h-3 w-3" /> 24/7 Support</p>
                <p className="flex items-center gap-2"><Shield className="h-3 w-3" /> Privacy Policy</p>
                <p className="flex items-center gap-2"><Award className="h-3 w-3" /> Terms of Service</p>
              </div>
            </div>
          </div>
          <div className="flex flex-col md:flex-row items-center justify-between gap-6 border-t border-white/5 pt-6">
            <div className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-primary flex items-center justify-center">
                <Truck className="h-4 w-4 text-white" />
              </div>
              <span className="text-lg font-bold text-white">ETHIO-FREIGHT</span>
            </div>
            <div className="flex items-center gap-6 text-sm text-slate-400">
              <span>2026 ETHIO-FREIGHT LOGISTICS SOLUTIONS. ALL RIGHTS RESERVED.</span>
              <Link href="/login" className="hover:text-white transition-colors">Login</Link>
              <Link href="/register" className="hover:text-white transition-colors">Register</Link>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
