import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "wouter";
import { useAuth } from "@/contexts/auth-context";
import { auth, api } from "@/lib/api";
import { isValidPhoneIdentifier, normalizeEthiopianPhone } from "@/lib/auth-identifiers";
import { pendingInvitation } from "@/lib/invitation-session";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Truck, Loader2, Eye, EyeOff, ArrowLeft, CheckCircle2, Shield,
  Headphones, Globe, Moon, Upload, AlertTriangle, FileText, Clock,
} from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { motion } from "framer-motion";

// ── Driver document types (must match Spring DriverDocument.DocumentType enum) ──
const REQUIRED_DOCUMENTS = [
  ["LICENSE",              "Driver's License"],
  ["NATIONAL_ID",          "National ID / Kebele ID"],
  ["VEHICLE_REGISTRATION", "Vehicle Registration"],
  ["INSURANCE",            "Insurance Certificate"],
  ["TIN",                  "TIN Certificate"],
] as const;

// ── Inline document upload card ───────────────────────────────────────────────
function DocUploadCard({
  docType,
  label,
  doc,
  busy,
  onUpload,
}: {
  docType: string;
  label: string;
  doc: any | undefined;
  busy: boolean;
  onUpload: (type: string, file: File) => void;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const status = String(doc?.status ?? "NOT_UPLOADED").toUpperCase();
  const uploaded = status !== "NOT_UPLOADED";

  return (
    <Card className={`rounded-xl border-border/60 ${uploaded ? "border-emerald-300 bg-emerald-50/40" : ""}`}>
      <CardHeader className="pb-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            {uploaded && <CheckCircle2 className="h-4 w-4 text-emerald-600" />}
            {label}
          </CardTitle>
          <Badge variant="outline" className={`text-xs ${uploaded ? "border-emerald-300 text-emerald-700" : ""}`}>
            {status === "NOT_UPLOADED" ? "Required" : status}
          </Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-2">
        {doc?.originalName && (
          <p className="text-xs text-muted-foreground flex items-center gap-1">
            <FileText className="h-3 w-3" /> {doc.originalName}
          </p>
        )}
        {doc?.rejectionReason && (
          <p className="text-xs text-destructive">Reason: {doc.rejectionReason}</p>
        )}
        <input
          ref={inputRef}
          className="hidden"
          type="file"
          accept=".jpg,.jpeg,.png,.pdf"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) onUpload(docType, file);
            e.currentTarget.value = "";
          }}
        />
        <Button
          type="button"
          variant={uploaded ? "outline" : "default"}
          size="sm"
          disabled={busy}
          onClick={() => inputRef.current?.click()}
          className="h-8 text-xs"
        >
          {busy ? (
            <Loader2 className="mr-2 h-3 w-3 animate-spin" />
          ) : (
            <Upload className="mr-2 h-3 w-3" />
          )}
          {uploaded ? "Replace" : "Upload"}
        </Button>
      </CardContent>
    </Card>
  );
}

// ── Pending verification landing screen ───────────────────────────────────────
function PendingVerificationScreen() {
  const [, setLocation] = useLocation();
  return (
    <motion.div
      className="text-center space-y-6"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <div className="mx-auto h-20 w-20 rounded-full bg-amber-100 flex items-center justify-center">
        <Clock className="h-10 w-10 text-amber-600" />
      </div>
      <div>
        <h2 className="text-xl font-bold text-foreground">Application submitted</h2>
        <p className="text-muted-foreground mt-2 text-sm leading-relaxed max-w-xs mx-auto">
          All 5 required documents have been submitted. An admin will review your
          application and activate your account.
        </p>
      </div>
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 text-left space-y-1">
        <p className="font-semibold">What happens next?</p>
        <ul className="list-disc list-inside space-y-1 text-xs">
          <li>Admin reviews your submitted documents</li>
          <li>You will be notified when your account is approved</li>
          <li>Once approved, you can log in and start bidding</li>
        </ul>
      </div>
      <p className="text-xs text-muted-foreground">
        Already approved?{" "}
        <button
          type="button"
          className="font-semibold text-foreground hover:text-primary underline"
          onClick={() => setLocation("/login")}
        >
          Sign in
        </button>
      </p>
    </motion.div>
  );
}

// ── Main register page ────────────────────────────────────────────────────────
export default function Register() {
  const { login } = useAuth();
  const [, setLocation] = useLocation();
  const { toast } = useToast();

  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState({ name: "", email: "", phone: "", password: "", role: "shipper" });
  const [phoneError, setPhoneError] = useState("");
  const [emailError, setEmailError] = useState("");
  const emailInput = useRef<HTMLInputElement>(null);
  const [invitedEmail, setInvitedEmail] = useState("");
  useEffect(() => {
    const token = pendingInvitation();
    if (!token) return;
    let active = true;
    api.post<{ email: string }>("/company-invitations/validate", { token })
      .then(preview => {
        if (!active) return;
        setInvitedEmail(preview.email);
        setForm(current => ({ ...current, email: preview.email }));
      })
      .catch(() => { /* The invitation page explains invalid or expired links. */ });
    return () => { active = false; };
  }, []);
  const [showPassword, setShowPassword] = useState(false);

  // step 0–2 = account wizard; step 3 = driver documents; step 4 = submission complete
  const [step, setStep] = useState(0);

  // Driver document state
  const [docs, setDocs] = useState<Record<string, any>>({});
  const [busyDocType, setBusyDocType] = useState<string | null>(null);
  const [docError, setDocError] = useState<string | null>(null);

  // ── Account creation (step 2 submit) ──────────────────────────────────────
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const data = await auth.post<{ token: string; user: any }>("/auth/register", {
        fullName: form.name,
        email: form.email,
        phone: normalizeEthiopianPhone(form.phone),
        password: form.password,
        role: pendingInvitation() ? "shipper" : form.role,
      });
      login(data.token, data.user);
      const role = (data.user.role ?? form.role).toLowerCase();
      toast({ title: "Account created!", description: "Welcome aboard." });

      if (pendingInvitation()) {
        setLocation("/invitations/accept");
      } else if (role === "driver") {
        // Driver must complete document upload before entering the platform.
        setStep(3);
      } else if (role === "admin") {
        setLocation("/admin");
      } else if (role === "fleet_owner") {
        setLocation("/fleet/companies/new");
      } else {
        setLocation("/dashboard");
      }
    } catch (err: any) {
      toast({ title: "Registration failed", description: err.message, variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  // ── Document upload (step 3) ───────────────────────────────────────────────
  const handleDocUpload = async (docType: string, file: File) => {
    setDocError(null);
    if (file.size > 5 * 1024 * 1024) {
      setDocError("Maximum file size is 5 MB.");
      return;
    }
    if (!["application/pdf", "image/jpeg", "image/png"].includes(file.type)) {
      setDocError("Only PDF, JPG, and PNG files are accepted.");
      return;
    }
    setBusyDocType(docType);
    try {
      const formData = new FormData();
      formData.append("documentType", docType);
      formData.append("file", file);
      const uploaded: any = await api.upload("/documents/upload", formData);
      const type = String(uploaded?.documentType ?? docType).toUpperCase();
      setDocs(prev => ({ ...prev, [type]: uploaded }));
      toast({ title: "Document uploaded", description: `${docType} submitted for review.` });
    } catch (err: any) {
      setDocError(err.message ?? "Upload failed.");
      toast({ title: "Upload failed", description: err.message, variant: "destructive" });
    } finally {
      setBusyDocType(null);
    }
  };

  const uploadedCount = REQUIRED_DOCUMENTS.filter(([t]) => docs[t]).length;
  const allUploaded = uploadedCount === REQUIRED_DOCUMENTS.length;

  // ── Step metadata ─────────────────────────────────────────────────────────
  const isDriver = form.role === "driver";
  const totalSteps = isDriver ? 4 : 3;
  const progressStep = step === 3 ? 4 : step === 4 ? 4 : step + 1;
  const stepTitles = ["Personal Info", "Contact", "Password", "Driver Documents", "Submitted"];
  const stepLabels = [
    `Step 1 of ${totalSteps}`,
    `Step 2 of ${totalSteps}`,
    `Step 3 of ${totalSteps}`,
    `Step 4 of ${totalSteps}`,
    `Step 4 of ${totalSteps}`,
  ];

  const nextStep = () => {
    if (step === 0) {
      if (!form.name.trim()) {
        toast({ title: "Complete your personal details", description: "Full name is required.", variant: "destructive" });
        return;
      }
      if (!form.phone.trim()) {
        setPhoneError("Phone is required");
        return;
      }
      if (!isValidPhoneIdentifier(form.phone)) {
        setPhoneError("Phone number must be valid");
        return;
      }
      setPhoneError("");
    }
    if (step === 1) {
      if (!form.email.trim()) {
        setEmailError("Email is required");
        emailInput.current?.focus();
        return;
      }
      if (!emailInput.current?.checkValidity()) {
        setEmailError("Email must be valid");
        emailInput.current?.focus();
        return;
      }
      setEmailError("");
    }
    setStep(s => Math.min(s + 1, 2));
  };
  const prevStep = () => setStep(s => Math.max(s - 1, 0));

  return (
    <div className="min-h-screen flex">
      {/* Left side — Brand panel */}
      <div className="hidden lg:flex lg:w-1/2 bg-[#0c1e4a] relative overflow-hidden items-center justify-center">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_rgba(20,184,166,0.12)_0%,_transparent_60%)]" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_left,_rgba(59,130,246,0.1)_0%,_transparent_50%)]" />
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage: `linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px),
              linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)`,
            backgroundSize: "60px 60px",
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
          <h2 className="text-3xl font-bold mb-4 leading-tight">Let's get started</h2>
          <p className="text-slate-400 text-lg leading-relaxed mb-8">
            {step === 3
              ? "Upload your required documents to complete your driver application."
              : step === 4
              ? "Your application is under review."
              : "Provide your personal details to secure your account."}
          </p>
          <div className="space-y-4">
            {["Secure SSL encryption", "GDPR Compliant", "24/7 Priority Support"].map((item, i) => (
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

      {/* Right side — Form */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 bg-background">
        <motion.div
          className="w-full max-w-md"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          {/* Top nav — hide on pending screen */}
          {step < 4 && (
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
          )}

          {/* Progress header — hide on pending screen */}
          {step < 4 && (
            <div className="mb-6">
              <div className="flex items-center justify-between mb-2">
                <h1 className="text-xl font-bold text-foreground">{stepTitles[step]}</h1>
                <span className="text-sm text-muted-foreground">{stepLabels[step]}</span>
              </div>
              <div className="h-2 bg-muted rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary rounded-full transition-all"
                  style={{ width: `${(progressStep / totalSteps) * 100}%` }}
                />
              </div>
            </div>
          )}

          {/* ── Step 4: Pending verification ── */}
          {step === 4 && <PendingVerificationScreen />}

          {/* ── Steps 0–2: account creation wizard ── */}
          {step < 3 && (
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
                        aria-invalid={!!phoneError}
                        aria-describedby={phoneError ? "phone-error" : undefined}
                        onChange={e => {
                          setForm(f => ({ ...f, phone: e.target.value }));
                          setPhoneError("");
                        }}
                        required
                        className="h-11 rounded-l-none rounded-lg"
                      />
                    </div>
                    {phoneError && <p id="phone-error" className="text-sm text-destructive">{phoneError}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="role" className="text-sm font-medium">Account Type</Label>
                    <select
                      id="role"
                      disabled={!!invitedEmail}
                      value={form.role}
                      onChange={e => setForm(f => ({ ...f, role: e.target.value }))}
                      className="flex h-11 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm"
                    >
                      <option value="shipper">Shipper</option>
                      <option value="driver">Driver</option>
                      <option value="fleet_owner">Fleet Owner</option>
                    </select>
                  </div>
                  {form.role === "fleet_owner" && <p className="rounded-lg bg-primary/5 p-3 text-sm">First create your owner account. Next, register your company and upload its business license, registration certificate and TIN certificate for admin approval.</p>}
                  {form.role === "driver" && (
                    <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800 flex items-start gap-2">
                      <AlertTriangle className="h-3.5 w-3.5 mt-0.5 shrink-0" />
                      You must upload all 5 required documents before your driver account is submitted for review.
                    </div>
                  )}
                  <Button
                    type="button"
                    className="w-full h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white"
                    onClick={nextStep}
                  >
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
                      ref={emailInput}
                      value={form.email}
                      readOnly={!!invitedEmail}
                      aria-invalid={!!emailError}
                      aria-describedby={emailError ? "email-error" : undefined}
                      onChange={e => {
                        setForm(f => ({ ...f, email: e.target.value }));
                        setEmailError("");
                      }}
                      required
                      className="h-11 rounded-lg"
                    />
                    {emailError && <p id="email-error" className="text-sm text-destructive">{emailError}</p>}
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
                      <button
                        type="button"
                        onClick={() => setShowPassword(v => !v)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                      >
                        {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Use 8+ characters with a mix of letters, numbers &amp; symbols.
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <Button type="button" variant="outline" className="flex-1 h-11 rounded-lg" onClick={prevStep}>
                      <ArrowLeft className="mr-2 h-4 w-4" /> Back
                    </Button>
                    <Button
                      type="submit"
                      className="flex-1 h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white"
                      disabled={loading}
                    >
                      {loading ? (
                        <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Creating…</>
                      ) : form.role === "driver" ? (
                        "Next: Documents"
                      ) : (
                        "Create Account"
                      )}
                    </Button>
                  </div>
                </div>
              )}
            </form>
          )}

          {/* ── Step 3: Driver document upload (required, cannot be skipped) ── */}
          {step === 3 && (
            <div className="space-y-4">
              <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 flex items-start gap-3">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                <p>
                  Upload all 5 required documents below to complete your application.
                  Your account will remain inactive until an admin approves your submission.
                </p>
              </div>

              <p className="text-sm text-muted-foreground">
                <span className={allUploaded ? "text-emerald-700 font-semibold" : ""}>
                  {uploadedCount}/5 documents uploaded.
                </span>
                {" "}Accepted formats: JPG, PNG, PDF. Maximum 5 MB each.
              </p>

              <div className="grid gap-3">
                {REQUIRED_DOCUMENTS.map(([type, label]) => (
                  <DocUploadCard
                    key={type}
                    docType={type}
                    label={label}
                    doc={docs[type]}
                    busy={busyDocType === type}
                    onUpload={handleDocUpload}
                  />
                ))}
              </div>

              {docError && (
                <p className="text-sm text-destructive">{docError}</p>
              )}

              <div className="pt-2">
                <Button
                  type="button"
                  className="w-full h-11 rounded-lg font-semibold bg-primary hover:bg-primary/90 text-white"
                  disabled={!allUploaded}
                  onClick={() => setStep(4)}
                >
                  <CheckCircle2 className="mr-2 h-4 w-4" />
                  {allUploaded
                    ? "Submit Application"
                    : `Upload remaining ${5 - uploadedCount} document${5 - uploadedCount !== 1 ? "s" : ""}`}
                </Button>
                {!allUploaded && (
                  <p className="text-xs text-muted-foreground text-center mt-2">
                    All 5 documents are required before submitting.
                  </p>
                )}
              </div>
            </div>
          )}

          {/* Trust badges — hide on document and pending screens */}
          {step < 3 && (
            <>
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
                      {form.role === "driver"
                        ? "You must upload all 5 required documents before submitting your application."
                        : "Fleet owners create an account, register a company, upload three company documents, and submit for admin approval."}
                    </p>
                  </div>
                </div>
              </div>
            </>
          )}

          {step < 3 && (
            <p className="mt-6 text-center text-sm text-muted-foreground">
              Already have an account?{" "}
              <Link href="/login" className="font-semibold text-foreground hover:text-primary transition-colors">
                Sign in
              </Link>
            </p>
          )}
        </motion.div>
      </div>
    </div>
  );
}
