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
  const { user, refreshMemberships } = useAuth();
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

  return <main className="container mx-auto max-w-4xl px-4 py-8 space-y-6">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><h1 className="text-2xl sm:text-3xl font-bold flex items-center gap-2"><Building2 className="shrink-0" />{!id ? "My companies" : isNew ? "Register your fleet company" : company?.companyName ?? "Company application"}</h1>
        <p className="mt-2 text-muted-foreground">Your owner account is ready. Company registration is a separate application.</p></div>
      <Link href={id ? "/fleet/companies" : "/dashboard"} className="text-sm underline">{id ? "All my companies" : "My dashboard"}</Link>
    </div>
    {error && <p role="alert" className="rounded-lg border border-destructive/30 p-4 text-destructive">{error}</p>}
    {notice && <p role="status" className="rounded-lg bg-muted p-4">{notice}</p>}
    {!id ? <>
      <Link href="/fleet/companies/new" className="inline-flex rounded-lg bg-primary px-4 py-3 font-medium text-primary-foreground">Register a company</Link>
      {companies.isLoading && <p>Loading your companies…</p>}
      {companies.isError && <div role="alert"><p>{companies.error.message}</p><Button variant="outline" onClick={() => companies.refetch()}>Retry</Button></div>}
      {companies.data?.content.length === 0 && <Card><CardContent className="pt-6"><h2 className="font-semibold">Register your first company</h2><p className="mt-2 text-muted-foreground">Enter company details, upload your business license, registration certificate and TIN certificate, then submit for admin review.</p></CardContent></Card>}
      {companies.data?.content.map(item => <Card key={item.id}><CardContent className="pt-6 flex flex-wrap justify-between gap-4"><div><h2 className="font-semibold">{item.companyName}</h2><Badge variant="outline" className="mt-2">{statusLabel[item.verificationStatus]}</Badge><p className="mt-2 text-sm text-muted-foreground">{item.city || "Company details incomplete"}</p></div><Link href={`/fleet/companies/${item.id}`} className="self-center underline">{["DRAFT", "MORE_INFORMATION_REQUIRED"].includes(item.verificationStatus) ? "Continue application" : "View application"}</Link></CardContent></Card>)}
      {companies.data && <div className="flex items-center gap-4"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><span>Page {page + 1}</span><Button variant="outline" disabled={companies.data.last} onClick={() => setPage(page + 1)}>Next</Button></div>}
    </> : <>
      {detail.isLoading && !isNew && <p>Loading application…</p>}
      {detail.isError && <div role="alert"><p>{detail.error.message}</p><Button onClick={() => detail.refetch()}>Retry</Button></div>}
      {(isNew || company) && <>
        <ol aria-label="Registration progress" className="grid grid-cols-3 gap-2 text-sm">
          {["Company details", "Documents", "Admin review"].map((label, i) => <li key={label} className={`rounded-lg border p-3 ${i + 1 === (editable ? step : 3) ? "border-primary bg-primary/5 font-semibold" : "text-muted-foreground"}`} aria-current={i + 1 === (editable ? step : 3) ? "step" : undefined}>{i + 1}. {label}</li>)}
        </ol>
        {company && <Card><CardContent className="pt-6 space-y-3"><Badge variant="outline">{statusLabel[company.verificationStatus]}</Badge><p>{statusHelp[company.verificationStatus]}</p>{company.reviewMessage && <p className="rounded-lg bg-muted p-3"><strong>Admin feedback: </strong>{company.reviewMessage}</p>}<Button variant="outline" disabled={busy} onClick={() => perform(async () => { await detail.refetch(); await docs.refetch(); })}>Refresh status</Button></CardContent></Card>}
        {editable && step === 1 ? <Card><CardHeader><CardTitle>1. Company details</CardTitle><p className="text-sm text-muted-foreground">You can save a draft with just a company name. All fields except legal name are required before submission.</p></CardHeader><CardContent>
          <form onSubmit={save} className="space-y-5"><div className="grid gap-4 sm:grid-cols-2">{companyFields.map(field => <div key={field.key} className={field.key === "businessAddress" ? "sm:col-span-2" : ""}><Label htmlFor={field.key}>{field.label}</Label><Input className="mt-1" id={field.key} value={form[field.key]} required={field.key === "companyName"} type={field.type ?? "text"} maxLength={field.max} pattern={field.pattern} title={field.key === "phone" ? "7–15 digits, optionally starting with +" : field.pattern ? "Use letters, digits, slashes or hyphens" : undefined} disabled={busy} onChange={e => setForm({ ...form, [field.key]: e.target.value })} /></div>)}</div>
            <Button disabled={busy}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}Save and continue</Button>
          </form>
        </CardContent></Card> : company && <Card><CardHeader><CardTitle>Company details</CardTitle></CardHeader><CardContent><dl className="grid gap-4 sm:grid-cols-2">{companyFields.map(field => <div key={field.key}><dt className="text-sm text-muted-foreground">{field.label}</dt><dd className="break-words">{company[field.key] || "Not provided"}</dd></div>)}</dl>{editable && <Button className="mt-4" variant="outline" disabled={busy} onClick={() => setStep(1)}>Edit company details</Button>}</CardContent></Card>}
        {company && (!editable || step === 2) && <Card><CardHeader><CardTitle>2. Company documents</CardTitle><p className="text-sm text-muted-foreground">Upload all three documents. PDF, JPEG or PNG; up to 5 MB each. Only you and the admin can access these files.</p></CardHeader><CardContent className="space-y-5">
          {docs.isLoading && <p>Loading documents…</p>}
          {docs.isError && <div role="alert"><p>Unable to load documents.</p><Button onClick={() => docs.refetch()}>Retry</Button></div>}
          {requiredCompanyDocs.map(({ type, label }) => { const doc = docs.data?.find(d => d.documentType === type); return <div key={type} className="rounded-lg border p-4 space-y-3"><h3 className="font-semibold flex gap-2 items-center">{doc ? <CheckCircle2 className="h-4 w-4 text-green-600" /> : <FileText className="h-4 w-4" />}{label}</h3>{doc ? <><p className="break-all text-sm">{doc.originalName}</p><Button variant="outline" size="sm" disabled={busy} onClick={() => perform(() => downloadCompanyDocument(company.id, doc))}>Download document</Button></> : <p className="text-sm text-muted-foreground">Required before submission</p>}{editable && <div><Label htmlFor={type}>{doc ? "Replace document" : "Upload document"}</Label><Input className="mt-1" id={type} type="file" accept=".pdf,.jpg,.jpeg,.png" disabled={busy || !docs.data} onChange={e => { const file = e.target.files?.[0]; e.target.value = ""; if (file) void upload(type, file); }} /></div>}</div>; })}
          {editable && <div className="border-t pt-4 space-y-3"><p className="text-sm">{completeDetails ? "Company details complete." : "Complete the missing company details before submitting."} {completeDocs ? "All required documents uploaded." : "Upload all three required documents."}</p><Button disabled={busy || !completeDetails || !completeDocs || docs.isError} onClick={submit}>{busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}{company.verificationStatus === "MORE_INFORMATION_REQUIRED" ? "Resubmit for review" : "Submit for review"}</Button><p className="text-sm text-muted-foreground">Submitting locks this application until the admin completes the review or requests changes.</p></div>}
        </CardContent></Card>}
        {company?.verificationStatus === "APPROVED" && company.ownerId === user?.id && <><Link className="underline" href={`/company/${company.id}`}>Open company dashboard</Link><CompanyTeam companyId={company.id} /></>}
      </>}
    </>}
  </main>;
}
