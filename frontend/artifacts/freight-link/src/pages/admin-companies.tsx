import { useState } from "react";
import { Link } from "wouter";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { companyFields, downloadCompanyDocument, statusLabel, type Company, type CompanyDocument, type CompanyPage, type CompanyStatus } from "@/lib/carrier-companies";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";

export default function AdminCompanies() {
  const { user } = useAuth();
  const client = useQueryClient();
  const [filter, setFilter] = useState("PENDING");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const list = useQuery({ queryKey: ["admin-companies", user?.id, filter, page], queryFn: () => api.get<CompanyPage>(`/admin/carrier-companies?page=${page}&size=10${filter ? `&status=${filter}` : ""}`) });
  const detail = useQuery({ queryKey: ["admin-company", user?.id, selected], queryFn: () => api.get<Company>(`/admin/carrier-companies/${selected}`), enabled: selected !== null });
  const docs = useQuery({ queryKey: ["admin-company-docs", user?.id, selected], queryFn: () => api.get<CompanyDocument[]>(`/carrier-companies/${selected}/documents`), enabled: selected !== null });
  const company = detail.data;
  async function review(action: string) {
    if (action !== "approve" && !message.trim()) { setError("Write feedback for the owner before continuing."); return; }
    if (!window.confirm(action === "reject" ? "Reject this application? Rejection is final. Use Request changes if the owner should correct it." : action === "approve" ? "Approve this company and its submitted documents?" : "Return this application to the owner for corrections?")) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await api.post(`/admin/carrier-companies/${selected}/${action}`, action === "approve" ? {} : { message: message.trim() });
      await Promise.all([client.invalidateQueries({ queryKey: ["admin-companies"] }), detail.refetch(), docs.refetch()]);
      setMessage(""); setNotice("Review saved. The owner can see the updated status and feedback.");
    } catch (e) { setError(e instanceof Error ? e.message : "Review failed."); }
    finally { setBusy(false); }
  }
  return <main className="container mx-auto max-w-5xl px-4 py-8 space-y-6">
    <div className="flex flex-wrap justify-between gap-3"><div><h1 className="text-3xl font-bold">Company reviews</h1><p className="mt-2 text-muted-foreground">Check company details and each document before making a decision.</p></div><Link href="/admin" className="underline">Admin dashboard</Link></div>
    {error && <p role="alert" className="text-destructive">{error}</p>}{notice && <p role="status">{notice}</p>}
    <div className="flex flex-wrap items-center gap-3"><Label htmlFor="status-filter">Application status</Label><select id="status-filter" className="rounded-lg border bg-background p-2" value={filter} onChange={e => { setFilter(e.target.value); setPage(0); }}><option value="">All statuses</option>{Object.entries(statusLabel).map(([key, label]) => <option value={key} key={key}>{label}</option>)}</select><Button variant="outline" onClick={() => list.refetch()}>Refresh applications</Button></div>
    {list.isLoading && <p>Loading applications…</p>}{list.isError && <p role="alert">{list.error.message}</p>}
    {list.data?.content.length === 0 && <p>No applications with this status.</p>}
    <div className="grid gap-3 sm:grid-cols-2">{list.data?.content.map(item => <button key={item.id} className={`rounded-lg border p-4 text-left ${item.id === selected ? "border-primary bg-primary/5" : ""}`} onClick={() => { setSelected(item.id); setMessage(""); setError(""); setNotice(""); }}><span className="block font-semibold">{item.companyName}</span><span className="text-sm">{statusLabel[item.verificationStatus]} · {item.city}</span></button>)}</div>
    {list.data && <div className="flex gap-4 items-center"><Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button><span>Page {page + 1}</span><Button variant="outline" disabled={list.data.last} onClick={() => setPage(page + 1)}>Next</Button></div>}
    {detail.isLoading && selected && <p>Loading application…</p>}{detail.isError && <p role="alert">{detail.error.message}</p>}
    {company && <Card><CardHeader><CardTitle>{company.companyName}</CardTitle><p>{statusLabel[company.verificationStatus as CompanyStatus]}</p></CardHeader><CardContent className="space-y-6">
      <dl className="grid gap-4 sm:grid-cols-2">{companyFields.map(field => <div key={field.key}><dt className="text-sm text-muted-foreground">{field.label}</dt><dd className="break-words">{company[field.key] || "Not provided"}</dd></div>)}</dl>
      {company.reviewMessage && <p><strong>Previous feedback: </strong>{company.reviewMessage}</p>}
      <div><h3 className="font-semibold mb-3">Private company documents</h3>{docs.isLoading && <p>Loading documents…</p>}{docs.isError && <div role="alert"><p>Could not load documents.</p><Button onClick={() => docs.refetch()}>Retry documents</Button></div>}{docs.data?.map(doc => <div key={doc.id} className="border-t py-3 flex flex-wrap justify-between gap-2"><span className="break-all">{doc.documentType.replaceAll("_", " ")} — {doc.originalName}</span><Button variant="outline" size="sm" onClick={() => downloadCompanyDocument(company.id, doc).catch(e => setError(e.message))}>Download document</Button></div>)}</div>
      {company.ownerId === user?.id ? <p>You cannot review your own company. Another admin must review it.</p> : company.verificationStatus === "PENDING" && <div className="space-y-3 border-t pt-4"><Label htmlFor="review-message">Feedback for the owner (required for changes or rejection)</Label><textarea id="review-message" maxLength={2000} className="min-h-24 w-full rounded-lg border bg-background p-3" value={message} disabled={busy} onChange={e => setMessage(e.target.value)} /><div className="flex flex-wrap gap-3"><Button disabled={busy || !docs.data || docs.isError} onClick={() => review("approve")}>Approve company</Button><Button variant="outline" disabled={busy || !message.trim()} onClick={() => review("request-information")}>Request changes</Button><Button variant="destructive" disabled={busy || !message.trim()} onClick={() => review("reject")}>Reject application</Button></div></div>}
    </CardContent></Card>}
  </main>;
}
