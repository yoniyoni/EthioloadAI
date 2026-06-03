import { Link } from "wouter";
import { Button } from "@/components/ui/button";
import {
  ArrowRight, BarChart3, Globe, ShieldCheck, Truck, Users,
  Zap, MapPin, Package, TrendingUp, ChevronRight, Star, Clock,
  Route, CheckCircle2, Shield, CreditCard, Headphones, Award, MessageCircle
} from "lucide-react";
import { motion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";

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

function FloatingCard({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <motion.div
      className={`bg-white/90 backdrop-blur-xl rounded-2xl border border-white/20 shadow-xl ${className}`}
      whileHover={{ y: -4, scale: 1.02 }}
      transition={{ type: "spring", stiffness: 300, damping: 20 }}
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
      {/* Hero Section - Navy Gradient */}
      <section ref={heroRef} className="relative w-full min-h-[90vh] overflow-hidden bg-[#0c1e4a] text-white">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_rgba(20,184,166,0.15)_0%,_transparent_60%)]" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_left,_rgba(59,130,246,0.1)_0%,_transparent_50%)]" />

        <motion.div style={{ y: heroY, opacity: heroOpacity }} className="relative z-10 container px-4 md:px-6 mx-auto max-w-6xl pt-20 md:pt-28">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* Left - Copy */}
            <div className="flex flex-col gap-8">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6 }}
              >
                <div className="inline-flex items-center gap-2 rounded-full border border-emerald-400/30 bg-emerald-400/10 px-4 py-1.5 text-sm font-semibold text-emerald-400 mb-6">
                  <span className="flex h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                  Next-Gen Logistics Hub
                </div>
                <h1 className="text-4xl sm:text-5xl md:text-6xl lg:text-[3.5rem] font-extrabold tracking-tight leading-[1.1] mb-6">
                  Smart Freight.
                  <br />
                  Smart Transport.
                  <br />
                  <span className="text-emerald-400">Smart Ethiopia.</span>
                </h1>
                <p className="text-lg md:text-xl text-slate-300 max-w-xl leading-relaxed">
                  Revolutionizing Ethiopian logistics through AI-driven matching and secure, seamless payments for the modern enterprise.
                </p>
              </motion.div>

              <motion.div
                className="flex flex-col sm:flex-row gap-4"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.6, delay: 0.2 }}
              >
                <Link href="/register">
                  <Button size="lg" className="h-14 px-8 text-base bg-white text-[#0c1e4a] hover:bg-slate-100 font-bold rounded-xl border-none transition-all hover:shadow-lg hover:shadow-white/10">
                    Get Started
                    <ArrowRight className="ml-2 h-5 w-5" />
                  </Button>
                </Link>
                <Link href="/freight">
                  <Button size="lg" variant="outline" className="h-14 px-8 text-base border-slate-600 text-white hover:bg-slate-800 hover:text-white rounded-xl transition-all">
                    Browse Loads
                  </Button>
                </Link>
              </motion.div>

              <motion.div
                className="flex items-center gap-6 text-sm text-slate-400"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.4 }}
              >
                <div className="flex -space-x-2">
                  {[1,2,3,4].map(i => (
                    <div key={i} className="h-8 w-8 rounded-full bg-slate-600 border-2 border-[#0c1e4a] flex items-center justify-center text-xs font-bold text-slate-300">
                      {String.fromCharCode(64 + i)}
                    </div>
                  ))}
                </div>
                <p>Trusted by 2,400+ shippers</p>
              </motion.div>
            </div>

            {/* Right - Visual Cards */}
            <motion.div
              className="relative hidden lg:block"
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ duration: 0.8, delay: 0.3 }}
            >
              <div className="relative h-[480px] w-full">
                <FloatingCard className="absolute top-0 left-0 w-72 p-5 z-20">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="h-10 w-10 rounded-lg bg-emerald-100 flex items-center justify-center">
                      <Truck className="h-5 w-5 text-emerald-600" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-slate-900">Active Load</p>
                      <p className="text-xs text-slate-500">Addis Ababa — Hawassa</p>
                    </div>
                  </div>
                  <div className="space-y-3">
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">Cargo</span>
                      <span className="font-medium text-slate-900">Electronics</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">Weight</span>
                      <span className="font-medium text-slate-900">12,500 kg</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-slate-500">Price</span>
                      <span className="font-medium text-emerald-600">ETB 45,000</span>
                    </div>
                    <div className="h-2 bg-slate-100 rounded-full mt-2">
                      <div className="h-full w-2/3 bg-emerald-500 rounded-full" />
                    </div>
                    <p className="text-xs text-slate-500 text-center">In transit — 67% complete</p>
                  </div>
                </FloatingCard>

                <FloatingCard className="absolute top-8 right-0 w-56 p-5 z-10">
                  <div className="flex items-center gap-2 mb-3">
                    <TrendingUp className="h-4 w-4 text-emerald-500" />
                    <span className="text-xs font-semibold text-emerald-600">+34% this month</span>
                  </div>
                  <p className="text-3xl font-bold text-slate-900 mb-1">ETB 1.2M</p>
                  <p className="text-xs text-slate-500">Total freight value matched</p>
                </FloatingCard>

                <FloatingCard className="absolute bottom-16 left-12 w-64 p-4 z-30">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-slate-100 flex items-center justify-center text-sm font-bold text-slate-600">
                      BG
                    </div>
                    <div className="flex-1">
                      <p className="text-sm font-semibold text-slate-900">Bekele Girma</p>
                      <div className="flex items-center gap-1 text-xs text-slate-500">
                        <Star className="h-3 w-3 text-amber-500 fill-amber-500" />
                        <span className="text-amber-600 font-medium">4.9</span>
                        <span>• 127 deliveries</span>
                      </div>
                    </div>
                    <div className="h-8 w-8 rounded-full bg-emerald-100 flex items-center justify-center">
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    </div>
                  </div>
                </FloatingCard>

                <motion.div
                  className="absolute bottom-4 right-8 h-12 w-12 rounded-xl bg-slate-900/80 backdrop-blur border border-slate-700 flex items-center justify-center"
                  animate={{ y: [0, -8, 0] }}
                  transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
                >
                  <MapPin className="h-5 w-5 text-emerald-400" />
                </motion.div>
                <motion.div
                  className="absolute top-32 left-8 h-10 w-10 rounded-lg bg-slate-900/80 backdrop-blur border border-slate-700 flex items-center justify-center"
                  animate={{ y: [0, -6, 0] }}
                  transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut", delay: 0.5 }}
                >
                  <Zap className="h-4 w-4 text-emerald-400" />
                </motion.div>
              </div>
            </motion.div>
          </div>
        </motion.div>

        <div className="absolute bottom-0 left-0 right-0 h-32 bg-gradient-to-t from-background to-transparent" />
      </section>

      {/* Stats Bar */}
      <section className="relative z-10 w-full bg-white py-14 border-b border-slate-100">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            {[
              { value: "12,000+", label: "Verified Drivers", icon: Users },
              { value: "4.8M", label: "Tons Moved", icon: Package },
              { value: "850+", label: "Active Routes", icon: Route },
              { value: "99.2%", label: "On-Time Delivery", icon: Clock },
            ].map((stat, i) => (
              <FadeIn key={i} delay={i * 0.1}>
                <div className="flex flex-col items-center">
                  <div className="h-10 w-10 rounded-lg bg-slate-50 flex items-center justify-center mb-3">
                    <stat.icon className="h-5 w-5 text-slate-600" />
                  </div>
                  <span className="text-3xl md:text-4xl font-extrabold text-slate-900 tracking-tight">{stat.value}</span>
                  <span className="text-sm font-medium text-slate-500 mt-1">{stat.label}</span>
                </div>
              </FadeIn>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="w-full py-24 bg-slate-50">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <FadeIn className="text-center mb-16 max-w-2xl mx-auto">
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-slate-900 mb-4">How Ethio-Freight Works</h2>
            <p className="text-lg text-slate-600">Three simple steps to get your cargo moving. Zero paperwork, instant matching.</p>
          </FadeIn>

          <div className="grid md:grid-cols-3 gap-8">
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
                <div className="relative bg-white p-8 rounded-2xl border border-slate-100 shadow-sm hover:shadow-lg transition-all duration-300 group">
                  <div className="absolute top-4 right-4 text-6xl font-extrabold text-slate-100 group-hover:text-slate-200 transition-colors">
                    {item.step}
                  </div>
                  <div className={`h-14 w-14 rounded-xl ${item.color} flex items-center justify-center mb-6 relative z-10`}>
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
      <section className="w-full py-24 bg-white">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <FadeIn className="text-center mb-16 max-w-2xl mx-auto">
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight text-slate-900 mb-4">Built for Scale, Designed for Trust</h2>
            <p className="text-lg text-slate-600">Every feature is built to make logistics simpler, faster, and more transparent.</p>
          </FadeIn>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              { icon: BarChart3, title: "AI Price Prediction", desc: "Machine learning models analyze routes, fuel costs, and cargo types to recommend optimal pricing.", color: "bg-emerald-50 text-emerald-600" },
              { icon: ShieldCheck, title: "Verified Network", desc: "Every driver undergoes rigorous background checks, license verification, and vehicle inspections.", color: "bg-teal-50 text-teal-600" },
              { icon: Globe, title: "Live GPS Tracking", desc: "End-to-end visibility with real-time location, speed, and estimated arrival times for every shipment.", color: "bg-blue-50 text-blue-600" },
              { icon: Zap, title: "Instant Matching", desc: "Get matched with available drivers in under 60 seconds. No phone calls, no middlemen.", color: "bg-sky-50 text-sky-600" },
              { icon: Star, title: "Rating & Reviews", desc: "Transparent two-way rating system ensures accountability and quality on every delivery.", color: "bg-violet-50 text-violet-600" },
              { icon: TrendingUp, title: "Analytics Dashboard", desc: "Track your shipping costs, delivery times, and driver performance with detailed insights.", color: "bg-indigo-50 text-indigo-600" },
            ].map((feat, i) => (
              <FadeIn key={i} delay={i * 0.08}>
                <div className="p-6 rounded-xl border border-slate-100 hover:border-emerald-200/60 hover:shadow-md transition-all duration-300 group">
                  <div className={`h-11 w-11 rounded-lg ${feat.color} flex items-center justify-center mb-4 group-hover:scale-110 transition-transform`}>
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
      <section className="w-full py-24 bg-slate-50">
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
                <div className="space-y-4">
                  {[
                    { icon: CreditCard, title: "Chapa", desc: "Cards, Apple Pay, Google Pay" },
                    { icon: CreditCard, title: "CBE Birr", desc: "Direct wallet-to-wallet transfer" },
                    { icon: CreditCard, title: "Telebirr", desc: "Mobile banking interface" },
                  ].map((item, i) => (
                    <div key={i} className="flex items-center gap-4 p-4 bg-white rounded-xl border border-slate-100">
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
              <div className="bg-[#0c1e4a] rounded-3xl p-8 text-white relative overflow-hidden">
                <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_rgba(20,184,166,0.2)_0%,_transparent_60%)]" />
                <div className="relative z-10 space-y-6">
                  <div className="flex items-center gap-2 text-sm text-emerald-400">
                    <Shield className="h-4 w-4" />
                    <span className="font-semibold">Escrow Protected</span>
                  </div>
                  <div className="space-y-2">
                    <p className="text-sm text-slate-400">Shipment ID</p>
                    <p className="text-2xl font-bold">#EF-99281-AD</p>
                  </div>
                  <div className="space-y-2">
                    <p className="text-sm text-slate-400">Total Amount</p>
                    <p className="text-4xl font-bold">ETB 42,500.00</p>
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

      {/* Testimonials */}
      <section className="w-full py-24 bg-[#0c1e4a] text-white relative overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_rgba(20,184,166,0.08)_0%,_transparent_50%)]" />
        <div className="container px-4 md:px-6 mx-auto max-w-6xl relative z-10">
          <FadeIn className="text-center mb-16">
            <h2 className="text-3xl md:text-4xl font-extrabold tracking-tight mb-4">Loved by Shippers Across Ethiopia</h2>
            <p className="text-lg text-slate-400">From small businesses to enterprise logistics teams.</p>
          </FadeIn>

          <div className="grid md:grid-cols-3 gap-6">
            {[
              { name: "Alemu Tadesse", role: "Logistics Manager", company: "Addis Trading Co.", quote: "Ethio-Freight cut our shipping costs by 23% and we always know exactly where our cargo is. Game changer.", rating: 5 },
              { name: "Tigist Kebede", role: "Operations Director", company: "AgroFresh Ethiopia", quote: "We move 200 tons of produce weekly. The instant matching and verified drivers give us total peace of mind.", rating: 5 },
              { name: "Dawit Mekonnen", role: "Fleet Owner", company: "Dawit Transports", quote: "As a fleet owner, Ethio-Freight keeps my 12 trucks busy. The payment system is fast and reliable.", rating: 5 },
            ].map((t, i) => (
              <FadeIn key={i} delay={i * 0.15}>
                <div className="bg-white/5 backdrop-blur-sm border border-white/10 rounded-xl p-6 hover:bg-white/10 transition-all duration-300">
                  <div className="flex gap-1 mb-4">
                    {Array(t.rating).fill(0).map((_, j) => (
                      <Star key={j} className="h-4 w-4 text-emerald-400 fill-emerald-400" />
                    ))}
                  </div>
                  <p className="text-slate-300 leading-relaxed mb-6 italic">"{t.quote}"</p>
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-emerald-500/20 flex items-center justify-center text-sm font-bold text-emerald-400">
                      {t.name.split(" ").map(n => n[0]).join("")}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-white">{t.name}</p>
                      <p className="text-xs text-slate-400">{t.role}, {t.company}</p>
                    </div>
                  </div>
                </div>
              </FadeIn>
            ))}
          </div>
        </div>
      </section>

      {/* Live Activity / CTA */}
      <section className="w-full py-24 bg-white">
        <div className="container px-4 md:px-6 mx-auto max-w-6xl">
          <div className="grid md:grid-cols-2 gap-12 items-center">
            <FadeIn>
              <h2 className="text-3xl md:text-5xl font-extrabold tracking-tight text-slate-900 mb-6">
                Ready to Move Smarter?
              </h2>
              <p className="text-xl text-slate-600 mb-10 max-w-xl">
                Join thousands of shippers and drivers who are already saving time and money with Ethio-Freight.
              </p>
              <div className="flex flex-col sm:flex-row gap-4">
                <Link href="/register">
                  <Button size="lg" className="h-14 px-10 text-base bg-primary text-white hover:bg-primary/90 font-bold rounded-xl border-none transition-all hover:shadow-lg hover:shadow-primary/20">
                    Create Free Account
                    <ChevronRight className="ml-2 h-5 w-5" />
                  </Button>
                </Link>
                <Link href="/freight">
                  <Button size="lg" variant="outline" className="h-14 px-10 text-base border-slate-300 text-slate-700 hover:bg-slate-100 rounded-xl">
                    Explore Loads
                  </Button>
                </Link>
              </div>
              <p className="text-sm text-slate-500 mt-6">Free for shippers. No credit card required.</p>
            </FadeIn>
            <FadeIn delay={0.2}>
              <div className="space-y-4">
                <div className="bg-slate-50 rounded-2xl p-6 border border-slate-100">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="h-3 w-3 rounded-full bg-emerald-500 animate-pulse" />
                    <p className="text-sm font-semibold text-slate-900">Live Freight Activity</p>
                  </div>
                  <div className="space-y-3">
                    {[
                      { from: "Addis Ababa", to: "Dire Dawa", type: "Industrial Cement", price: "18,500", status: "In Transit" },
                      { from: "Hawassa", to: "Addis Ababa", type: "Coffee Export", price: "15,900", status: "Active" },
                      { from: "Bahir Dar", to: "Mekelle", type: "Machinery", price: "21,000", status: "Matched" },
                    ].map((load, i) => (
                      <div key={i} className="flex items-center justify-between p-3 bg-white rounded-lg border border-slate-100">
                        <div>
                          <p className="text-sm font-medium text-slate-900">{load.from} → {load.to}</p>
                          <p className="text-xs text-slate-500">{load.type}</p>
                        </div>
                        <div className="text-right">
                          <p className="text-sm font-semibold text-slate-900">ETB {load.price}</p>
                          <p className="text-xs text-emerald-600">{load.status}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
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
                <Link href="/freight" className="block hover:text-white transition-colors">Freight Marketplace</Link>
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
