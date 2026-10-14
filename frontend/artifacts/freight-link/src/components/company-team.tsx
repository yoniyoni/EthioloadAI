import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { UserPlus, UserRoundCheck, UserRoundX, UserMinus, RefreshCw } from "lucide-react";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";

type Member = { id: number; fullName: string; email: string; role: string; status: string };
type Page<T> = { content: T[] };

export function CompanyTeam({ companyId }: { companyId: number }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("FLEET_MANAGER");
  const [busyId, setBusyId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const base = `/carrier-companies/${companyId}/team`;
  const members = useQuery({
    queryKey: ["company-members", companyId],
    queryFn: () => api.get<Page<Member>>(`${base}/members?size=100`),
    refetchInterval: 10000,
  });

  async function perform(work: () => Promise<unknown>, success?: string, memberId?: number): Promise<boolean> {
    setBusy(true);
    setBusyId(memberId ?? null);
    setError("");
    setMessage("");
    try {
      await work();
      await members.refetch();
      if (success) setMessage(success);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to update the company team.");
      return false;
    } finally {
      setBusy(false);
      setBusyId(null);
    }
  }

  async function invite(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const invitedEmail = email.trim().toLowerCase();
    const sent = await perform(async () => {
      await api.post(`${base}/invitations`, { email: invitedEmail, role });
      setEmail("");
    });
    if (sent) {
      setMessage(`Invitation sent to ${invitedEmail}. They must accept it using this email address.`);
      setError("");
    }
  }

  function changeRole(member: Member, nextRole: string) {
    if (nextRole === member.role || !window.confirm(`Change ${member.fullName}'s role to ${nextRole.replaceAll("_", " ")}?`)) return;
    void perform(() => api.patch(`${base}/members/${member.id}/role`, { role: nextRole }), "Team role updated.", member.id);
  }

  function changeStatus(member: Member, action: "activate" | "deactivate" | "remove") {
    if (action !== "activate" && !window.confirm(action === "remove"
      ? `Remove ${member.fullName} from this company? They will need a new invitation to rejoin.`
      : `Pause ${member.fullName}'s access to this company?`)) return;
    const request = action === "remove"
      ? api.del(`${base}/members/${member.id}`)
      : api.post(`${base}/members/${member.id}/${action}`);
    void perform(() => request, action === "remove" ? "Member removed." : action === "activate" ? "Member access restored." : "Member access paused.", member.id);
  }

  const memberItems = members.data?.content ?? [];

  return (
    <Card className="rounded-2xl border-border/70 shadow-sm">
      <CardHeader className="space-y-1">
        <CardTitle className="flex items-center gap-2"><UserPlus className="h-5 w-5 text-primary" /> Company team</CardTitle>
        <CardDescription>Invite the people who run the fleet and dispatch shipments. You can update access here later.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <form onSubmit={invite} className="grid gap-3 rounded-xl bg-muted/40 p-4 sm:grid-cols-[minmax(0,1fr)_190px_auto] sm:items-end">
          <div className="space-y-2"><Label htmlFor="invite-email">Work email</Label><Input id="invite-email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="name@company.com" /></div>
          <div className="space-y-2"><Label htmlFor="invite-role">Company role</Label><select id="invite-role" className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" value={role} onChange={e => setRole(e.target.value)}><option value="FLEET_MANAGER">Fleet Manager</option><option value="DISPATCHER">Dispatcher</option></select></div>
          <Button disabled={busy} className="gap-2"><UserPlus className="h-4 w-4" /> Send invite</Button>
        </form>

        {(message || error) && <p role={error ? "alert" : "status"} className={`rounded-lg px-3 py-2 text-sm ${error ? "bg-destructive/10 text-destructive" : "bg-emerald-50 text-emerald-800"}`}>{error || message}</p>}

        <section className="space-y-3" aria-labelledby="team-members-heading">
          <div className="flex items-center justify-between"><div><h3 id="team-members-heading" className="font-semibold">Members</h3><p className="text-sm text-muted-foreground">{memberItems.length} listed</p></div><Button type="button" size="sm" variant="outline" disabled={members.isFetching} onClick={() => { void members.refetch(); }}><RefreshCw className={`mr-2 h-3.5 w-3.5 ${members.isFetching ? "animate-spin" : ""}`} /> Refresh</Button></div>
          {members.isError ? <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-sm text-destructive">{members.error.message}</p> : members.isLoading ? <p className="py-4 text-sm text-muted-foreground">Loading team members…</p> : memberItems.length === 0 ? <p className="rounded-xl border border-dashed p-5 text-sm text-muted-foreground">No team members yet. Invite a Fleet Manager or Dispatcher to get started.</p> :
            <div className="divide-y rounded-xl border">{memberItems.map(member => {
              const isOwner = member.role === "CARRIER_OWNER";
              const active = member.status === "ACTIVE";
              return <div key={member.id} className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
                <div className="min-w-0"><p className="truncate font-medium">{member.fullName} {isOwner && <Badge variant="secondary" className="ml-1">Owner</Badge>}</p><p className="truncate text-sm text-muted-foreground">{member.email}</p><Badge variant={active ? "default" : "outline"} className="mt-2">{member.status.replaceAll("_", " ")}</Badge></div>
                {!isOwner && member.status !== "REMOVED" && <div className="flex flex-wrap items-center gap-2">
                  <select aria-label={`Role for ${member.fullName}`} className="h-9 rounded-md border border-input bg-background px-2 text-sm" disabled={busy} value={member.role} onChange={e => changeRole(member, e.target.value)}><option value="FLEET_MANAGER">Fleet Manager</option><option value="DISPATCHER">Dispatcher</option></select>
                  {active ? <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => changeStatus(member, "deactivate")}><UserRoundX className="mr-1.5 h-4 w-4" /> Pause access</Button> : member.status === "INACTIVE" ? <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => changeStatus(member, "activate")}><UserRoundCheck className="mr-1.5 h-4 w-4" /> Restore</Button> : null}
                  {member.status !== "REMOVED" && <Button type="button" variant="ghost" size="sm" className="text-destructive hover:text-destructive" disabled={busy} onClick={() => changeStatus(member, "remove")}><UserMinus className="mr-1.5 h-4 w-4" /> Remove</Button>}
                  {busyId === member.id && <span className="sr-only" aria-live="polite">Updating member</span>}
                </div>}
                {member.status === "REMOVED" && <p className="text-sm text-muted-foreground">Removed · send a new invitation to restore company access.</p>}
              </div>;
            })}</div>}
        </section>

        <p className="text-sm text-muted-foreground">Invited staff receive an email link and appear here after they accept. Invite them again to replace an expired or revoked invitation.</p>
      </CardContent>
    </Card>
  );
}
