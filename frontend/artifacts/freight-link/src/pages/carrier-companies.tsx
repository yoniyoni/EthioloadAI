import { useEffect, useState } from "react";
import { Link, useLocation } from "wouter";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Building2, CheckCircle2, Clock, FileText, Loader2, Mail, Send, UserCheck } from "lucide-react";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { CompanyTeam } from "@/components/company-team";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { blankCompany, companyFields, companyPayload, downloadCompanyDocument, requiredCompanyDocs, statusHelp, statusLabel, type Company, type CompanyDocument, type CompanyForm, type CompanyPage } from "@/lib/carrier-companies";

export default function CarrierCompanies({ id }: { id?: string }) {
  const { user, memberships, refreshMemberships } = useAuth();
  const { toast } = useToast();
  const [, navigate] = useLocation();
  const client = useQueryClient();
  const isNew = id === "new";
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<CompanyForm>({ ...blankCompany });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [step, setStep] = useState(1);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState("FLEET_MANAGER");
  const companies = useQuery({ queryKey: ["carrier-companies", user?.id, page], queryFn: () => api.get<CompanyPage>(`/carrier-companies/me?page=${page}&size=10`), enabled: !id });
  const detail = useQuery({ queryKey: ["carrier-company", user?.id, id], queryFn: () => api.get<Company>(`/carrier-companies/${id}`), enabled: !!id && !isNew, refetchOnWindowFocus: true,
    refetchInterval: query => query.state.data?.verificationStatus === "PENDING" ? 3000 : false });
  const docs = useQuery({ queryKey: ["carrier-company-documents", user?.id, id], queryFn: () => api.get<CompanyDocument[]>(`/carrier-companies/${id}/documents`), enabled: !!id && !isNew });
  const company = detail.data;
  const editable = isNew || company?.verificationStatus === "DRAFT" || company?.verificationStatus === "MORE_INFORMATION_REQUIRED";
  const completeDocs = requiredCompanyDocs.every(({ type }) => docs.data?.some(d => d.documentType === type));
  const completeDetails = companyFields.every(({ key }) => key === "legalName" || !!company?.[key]?.trim());
  useEffect(() => {
    setError(""); setNotice(""); setStep(isNew ? 1 : 2);
    if (isNew) setForm({ ...blankCompany });
  }, [id, isNew]);
  useEffect(() => {
    if (company) setForm(Object.fromEntries(Object.keys(blankCompany).map(key => [key, company[key as keyof CompanyForm] ?? ""])) as CompanyForm);
  }, [company]);

  async function perform(work: () => Promise<void>) {
    setBusy(true); setError(""); setNotice("");
    try { await work(); } catch (e) {
      const message = e instanceof Error ? e.message : "Unable to complete this action. Please retry.";
      setError(message); toast({ title: "Unable to continue", description: message, variant: "destructive" });
    }
    finally { setBusy(false); }
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    await perform(async () => {
      const saved = isNew ? await api.post<Company>("/carrier-companies", companyPayload(form)) : await api.put<Company>(`/carrier-companies/${id}`, companyPayload(form));
      if (isNew) await refreshMemberships();
      client.setQueryData(["carrier-company", user?.id, String(saved.id)], saved);
      await client.invalidateQueries({ queryKey: ["carrier-companies"] });
      setStep(2); setNotice("Company details saved. Next, upload your documents.");
      if (isNew) navigate(`/fleet/companies/${saved.id}`);
    });
  }
  async function upload(type: string, file: File) {
    await perform(async () => {
      if (file.size > 5 * 1024 * 1024 || !/\.(pdf|jpe?g|png)$/i.test(file.name)) throw new Error("Choose a PDF, JPEG, or PNG file no larger than 5 MB.");
      const data = new FormData(); data.append("documentType", type); data.append("file", file);
      await api.upload(`/carrier-companies/${id}/documents`, data);
      await docs.refetch(); setNotice("Document saved. Upload all three documents before submitting.");
    });
  }
  async function submit() {
    await perform(async () => {
      const saved = await api.post<Company>(`/carrier-companies/${id}/submit`, {});
      client.setQueryData(["carrier-company", user?.id, id], saved);
      await client.invalidateQueries({ queryKey: ["carrier-companies"] });
      setNotice("Application submitted. An admin will review your company and documents.");
    });
  }

  return <main className="container mx-auto max-w-5xl space-y-7 px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
    <header className="flex flex-col gap-5 border-b border-border/70 pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <div className="mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <Building2 className="h-4 w-4 text-primary" aria-hidden="true" />
          <span>{id ? "Company management" : "Fleet Owner"}</span>
        </div>
        <h1 className="break-words text-2xl font-bold tracking-tight sm:text-3xl">{!id ? "My companies" : isNew ? "Register your fleet company" : company?.companyName ?? "Company application"}</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">
          {!id
            ? "View your company applications or register a company to manage a fleet."
            : isNew
              ? "Enter your business details and submit the required documents for review."
              : "Review your application, documents, and current verification status."}
        </p>
      </div>
      <Link
        href={id ? "/fleet/companies" : "/dashboard"}
        className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-lg border border-border px-4 py-2 text-sm font-medium transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        {id ? "All my companies" : "Company workspaces"}
      </Link>
    </header>
    {error && <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">{error}</p>}
    {notice && <p role="status" className="rounded-lg border border-primary/20 bg-primary/5 p-4 text-sm text-foreground">{notice}</p>}
    {!id ? <>
      <section aria-labelledby="company-list-heading" className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 id="company-list-heading" className="text-lg font-semibold tracking-tight">Company applications</h2>
            <p className="mt-1 text-sm text-muted-foreground">Registration and review status for companies linked to your account.</p>
          </div>
          <Button asChild className="min-h-11 gap-2">
            <Link href="/fleet/companies/new"><Building2 className="h-4 w-4" aria-hidden="true" />Register a company</Link>
          </Button>
        </div>
        {companies.isLoading && (
          <div className="grid gap-3" aria-label="Loading companies">
            {[0, 1, 2].map(item => <Card key={item} className="animate-pulse"><CardContent className="space-y-3 p-5"><div className="h-5 w-1/3 rounded bg-muted" /><div className="h-4 w-1/2 rounded bg-muted" /></CardContent></Card>)}
          </div>
        )}
        {companies.isError && <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-destructive">{companies.error.message}</p><Button type="button" variant="outline" onClick={() => companies.refetch()}>Retry loading companies</Button></div>}
        {companies.data?.content.length === 0 && <Card className="border-dashed shadow-none"><CardContent className="flex flex-col items-start gap-4 p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6"><div><h3 className="font-semibold">No companies yet</h3><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">Register your first company, then provide its business license, registration certificate, and TIN certificate for review.</p></div><Button asChild variant="outline" className="min-h-11 shrink-0"><Link href="/fleet/companies/new">Start registration</Link></Button></CardContent></Card>}
        <div className="grid gap-3">
          {companies.data?.content.map(item => {
            const hasActiveMembership = memberships.some(membership =>
              membership.carrierCompanyId === item.id && membership.status === "ACTIVE",
            );
            return <Card key={item.id} className="shadow-sm transition-shadow hover:shadow-md"><CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:justify-between sm:p-5"><div className="min-w-0 space-y-2"><div className="flex flex-wrap items-center gap-2"><h3 className="break-words text-base font-semibold">{item.companyName}</h3><Badge variant="outline" className="whitespace-nowrap">{statusLabel[item.verificationStatus]}</Badge></div><p className="text-sm text-muted-foreground">{item.city || "Company details incomplete"}</p></div><div className="flex w-full shrink-0 flex-col gap-2 sm:w-auto sm:flex-row"><Button asChild variant="outline" className="min-h-11 w-full sm:w-auto"><Link href={`/fleet/companies/${item.id}`}>{["DRAFT", "MORE_INFORMATION_REQUIRED"].includes(item.verificationStatus) ? "Continue application" : "View application"}</Link></Button>{item.verificationStatus === "APPROVED" && hasActiveMembership && <Button asChild className="min-h-11 w-full sm:w-auto"><Link href={`/company/${item.id}`}>Open workspace</Link></Button>}</div></CardContent></Card>;
          })}
        </div>
        {companies.data && <nav aria-label="Company application pages" className="flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-4"><Button type="button" variant="outline" className="min-h-11" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><span className="text-sm text-muted-foreground" aria-live="polite">Page {page + 1}</span><Button type="button" variant="outline" className="min-h-11" disabled={companies.data.last} onClick={() => setPage(page + 1)}>Next</Button></nav>}
      </section>
    </> : <>
      {detail.isLoading && !isNew && <Card aria-label="Loading company application" className="animate-pulse shadow-none"><CardContent className="space-y-3 p-5"><div className="h-5 w-1/3 rounded bg-muted" /><div className="h-4 w-2/3 rounded bg-muted" /></CardContent></Card>}
      {detail.isError && <div role="alert" className="flex flex-col gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-destructive">{detail.error.message}</p><Button type="button" variant="outline" onClick={() => detail.refetch()}>Retry loading application</Button></div>}
      {(isNew || company) && <>
        <ol aria-label="Registration progress" className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
          {["Company details", "Documents", "Admin review"].map((label, i) => <li key={label} className={`flex min-h-12 items-center gap-3 rounded-lg border px-4 py-3 ${i + 1 === (editable ? step : 3) ? "border-primary/40 bg-primary/5 font-semibold text-foreground" : "border-border/70 text-muted-foreground"}`} aria-current={i + 1 === (editable ? step : 3) ? "step" : undefined}><span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs">{i + 1}</span>{label}</li>)}
        </ol>
        {company && <Card className="shadow-sm"><CardContent className="space-y-4 p-5 sm:p-6"><div className="flex flex-wrap items-center gap-2"><span className="text-sm font-medium text-muted-foreground">Application status</span><Badge variant="outline">{statusLabel[company.verificationStatus]}</Badge></div><p className="max-w-3xl text-sm leading-6 text-muted-foreground">{statusHelp[company.verificationStatus]}</p>{company.reviewMessage && <p className="rounded-lg border border-border/70 bg-muted/40 p-4 text-sm leading-6"><strong>Admin feedback: </strong>{company.reviewMessage}</p>}<Button type="button" variant="outline" className="min-h-11" disabled={busy} onClick={() => perform(async () => { await detail.refetch(); await docs.refetch(); })}>Refresh status</Button></CardContent></Card>}
        {editable && step === 1 ? <Card className="shadow-sm"><CardHeader className="space-y-2"><CardTitle className="text-lg">Company details</CardTitle><p className="text-sm leading-6 text-muted-foreground">Save a draft with a company name, then complete all other fields before submitting for review. Legal name is optional.</p></CardHeader><CardContent>
          <form onSubmit={save} className="space-y-6"><div className="grid gap-x-5 gap-y-4 sm:grid-cols-2">{companyFields.map(field => <div key={field.key} className={`space-y-1.5 ${field.key === "businessAddress" ? "sm:col-span-2" : ""}`}><Label htmlFor={field.key}>{field.label}</Label><Input className="min-h-11" id={field.key} value={form[field.key]} required={field.key === "companyName"} type={field.type ?? "text"} maxLength={field.max} pattern={field.pattern} title={field.key === "phone" ? "7–15 digits, optionally starting with +" : field.pattern ? "Use letters, digits, slashes or hyphens" : undefined} disabled={busy} onChange={e => setForm({ ...form, [field.key]: e.target.value })} /></div>)}</div>
            <Button type="submit" className="min-h-11" disabled={busy}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}Save and continue</Button>
          </form>
        </CardContent></Card> : company && <Card className="shadow-sm"><CardHeader><CardTitle className="text-lg">Company details</CardTitle></CardHeader><CardContent><dl className="grid gap-x-6 gap-y-5 sm:grid-cols-2">{companyFields.map(field => <div key={field.key} className="min-w-0 border-b border-border/60 pb-3"><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{field.label}</dt><dd className="mt-1 break-words text-sm font-medium">{company[field.key] || "Not provided"}</dd></div>)}</dl>{editable && <Button type="button" className="mt-5 min-h-11" variant="outline" disabled={busy} onClick={() => setStep(1)}>Edit company details</Button>}</CardContent></Card>}
        {company && (!editable || step === 2) && <Card className="shadow-sm"><CardHeader className="space-y-2"><CardTitle className="text-lg">Company documents</CardTitle><p className="text-sm leading-6 text-muted-foreground">Upload all three documents. PDF, JPEG, or PNG files up to 5 MB each. Only you and the admin can access these files.</p></CardHeader><CardContent className="space-y-4">
          {docs.isLoading && <div className="space-y-3" aria-label="Loading company documents">{requiredCompanyDocs.map(({ type }) => <div key={type} className="h-20 animate-pulse rounded-lg border bg-muted/30" />)}</div>}
          {docs.isError && <div role="alert" className="flex flex-col gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-4 sm:flex-row sm:items-center sm:justify-between"><p className="text-sm text-destructive">Unable to load documents.</p><Button type="button" variant="outline" onClick={() => docs.refetch()}>Retry loading documents</Button></div>}
          {requiredCompanyDocs.map(({ type, label }) => { const doc = docs.data?.find(d => d.documentType === type); return <div key={type} className="space-y-3 rounded-xl border border-border/70 p-4 sm:p-5"><div className="flex flex-wrap items-center gap-2">{doc ? <CheckCircle2 className="h-4 w-4 text-primary" aria-hidden="true" /> : <FileText className="h-4 w-4 text-muted-foreground" aria-hidden="true" />}<h3 className="font-medium">{label}</h3><Badge variant="outline">{doc ? "Uploaded" : "Required"}</Badge></div>{doc ? <><p className="break-all text-sm text-muted-foreground">{doc.originalName}</p><Button type="button" variant="outline" size="sm" className="min-h-10" disabled={busy} onClick={() => perform(() => downloadCompanyDocument(company.id, doc))}>Download document</Button></> : <p className="text-sm text-muted-foreground">Required before submission</p>}{editable && <div className="space-y-1.5"><Label htmlFor={type}>{doc ? "Replace document" : "Upload document"}</Label><Input className="min-h-11" id={type} type="file" accept=".pdf,.jpg,.jpeg,.png" disabled={busy || !docs.data} onChange={e => { const file = e.target.files?.[0]; e.target.value = ""; if (file) void upload(type, file); }} /></div>}</div>; })}
          {editable && <div className="space-y-3 rounded-xl border-t border-border/70 pt-5"><p className="text-sm leading-6">{completeDetails ? "Company details complete." : "Complete the missing company details before submitting."} {completeDocs ? "All required documents uploaded." : "Upload all three required documents."}</p><Button type="button" className="min-h-11" disabled={busy || !completeDetails || !completeDocs || docs.isError} onClick={submit}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}{company.verificationStatus === "MORE_INFORMATION_REQUIRED" ? "Resubmit for review" : "Submit for review"}</Button><p className="text-sm text-muted-foreground">Submitting locks this application until the admin completes the review or requests changes.</p></div>}
        </CardContent></Card>}
        {company?.verificationStatus === "APPROVED" && company.ownerId === user?.id && <section className="space-y-4 border-t border-border/70 pt-6"><div><h2 className="text-lg font-semibold">Company workspace</h2><p className="mt-1 text-sm text-muted-foreground">Open the operational workspace or manage your company team.</p></div><Button asChild className="min-h-11"><Link href={`/company/${company.id}`}>Open company workspace</Link></Button><CompanyTeam companyId={company.id} /></section>}
      </>}
    </>}
  </main>;
}
