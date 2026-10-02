import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

type Invitation = { id: number; invitedEmail: string; invitedRole: string; status: string };
type Member = { id: number; fullName: string; email: string; role: string; status: string };
export function CompanyTeam({ companyId }: { companyId: number }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("FLEET_MANAGER");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const base = `/carrier-companies/${companyId}/team`;
  const invitations = useQuery({ queryKey: ["company-invitations", companyId], queryFn: () => api.get<{content: Invitation[]}>(`${base}/invitations?size=100`), refetchInterval: 10000 });
  const members = useQuery({ queryKey: ["company-members", companyId], queryFn: () => api.get<{content: Member[]}>(`${base}/members?size=100`), refetchInterval: 5000 });
  async function perform(work: () => Promise<unknown>) {
    setBusy(true); setError(""); setMessage("");
    try { await work(); await Promise.all([invitations.refetch(), members.refetch()]); }
    catch(e) { setError(e instanceof Error ? e.message : "Unable to update the team."); }
    finally { setBusy(false); }
  }
  return <Card><CardHeader><CardTitle>Company team</CardTitle></CardHeader><CardContent className="space-y-4">
    <form className="space-y-3" onSubmit={e => { e.preventDefault(); void perform(async () => {
      await api.post(`${base}/invitations`, {email: email.trim().toLowerCase(), role});
      setEmail(""); setMessage("Invitation sent. The employee must accept using the invited email address.");
    }); }}>
      <Label htmlFor="invite-email">Employee email</Label><Input id="invite-email" type="email" required value={email} onChange={e => setEmail(e.target.value)} />
      <Label htmlFor="invite-role">Company role</Label><select id="invite-role" className="block border rounded p-2" value={role} onChange={e => setRole(e.target.value)}><option value="FLEET_MANAGER">Fleet Manager</option><option value="DISPATCHER">Dispatcher</option></select>
      <Button disabled={busy}>Send invitation</Button>
    </form>
    {message && <p role="status">{message}</p>}
    {(error || invitations.error || members.error) && <p role="alert">{error || (invitations.error || members.error)?.message}</p>}
    <h3 className="font-semibold">Invitations</h3>
    {invitations.data?.content.map(i => <div className="border rounded p-3 space-x-3" key={i.id}><span>{i.invitedEmail} · {i.invitedRole.replaceAll("_", " ")} · {i.status}</span>
      {["PENDING", "EXPIRED"].includes(i.status) && <Button variant="outline" disabled={busy} onClick={() => void perform(() => api.post(`${base}/invitations/${i.id}/resend`))}>Resend</Button>}
      {i.status === "PENDING" && <Button variant="outline" disabled={busy} onClick={() => void perform(() => api.post(`${base}/invitations/${i.id}/revoke`))}>Revoke</Button>}
    </div>)}
    <h3 className="font-semibold">Members</h3>
    {members.data?.content.map(m => <div className="border rounded p-3" key={m.id}>{m.fullName} · {m.email} · {m.role.replaceAll("_", " ")} · {m.status}</div>)}
  </CardContent></Card>;
}
