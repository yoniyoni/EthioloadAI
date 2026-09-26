import { useEffect, useState } from "react";
import { Link } from "wouter";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";

type Preview = { companyName: string; role: string; email: string; expiresAt: string };
const KEY = "freightlink_pending_invitation";

export default function InvitationAccept() {
  const { isAuthenticated, isLoading, user, logout } = useAuth();
  const [token] = useState(() => {
    const incoming = new URLSearchParams(window.location.hash.slice(1)).get("token");
    if (incoming) {
      sessionStorage.setItem(KEY, incoming);
      window.history.replaceState(null, "", window.location.pathname);
    }
    return incoming ?? sessionStorage.getItem(KEY) ?? "";
  });
  const [preview, setPreview] = useState<Preview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [joined, setJoined] = useState(false);

  useEffect(() => {
    let active = true;
    if (!token) { setError("Open the invitation link from your email."); return; }
    api.post<Preview>("/company-invitations/validate", { token })
      .then(data => { if (active) setPreview(data); })
      .catch(() => {
        if (active) {
          setError("This invitation is unavailable or expired. Ask the company owner for a new invitation.");
          sessionStorage.removeItem(KEY);
        }
      });
    return () => { active = false; };
  }, [token]);

  async function accept() {
    setBusy(true);
    setError("");
    try {
      await api.post("/company-invitations/accept", { token });
      sessionStorage.removeItem(KEY);
      setJoined(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to accept invitation.");
    } finally { setBusy(false); }
  }

  const matchingEmail = user?.email?.trim().toLowerCase() === preview?.email.toLowerCase();
  return (
    <main className="mx-auto max-w-lg px-6 py-16 space-y-6">
      <h1 className="text-3xl font-bold">Company invitation</h1>
      {error && <p role="alert" className="text-destructive">{error}</p>}
      {joined ? <p role="status">You have joined {preview?.companyName}. Your company role is {preview?.role.replaceAll("_", " ")}.</p> :
        preview ? <>
          <h2 className="text-xl font-semibold">Join {preview.companyName}</h2>
          <p>You are invited as {preview.role.replaceAll("_", " ")} using {preview.email}.</p>
          <p className="text-sm text-muted-foreground">Expires {new Date(preview.expiresAt + "Z").toLocaleString()}.</p>
          {isLoading ? <p>Loading your account…</p> : isAuthenticated ? matchingEmail ?
            <Button disabled={busy} onClick={accept}>{busy ? "Joining…" : "Accept invitation"}</Button> :
            <div className="space-y-3"><p>Sign in using the invited email address.</p><Button onClick={logout}>Sign out</Button></div> :
            <div className="flex gap-4">
              <Link href="/login?invitation=1" className="underline">Sign in</Link>
              <Link href="/register?invitation=1" className="underline">Create account</Link>
            </div>}
        </> : !error && <p>Checking invitation…</p>}
    </main>
  );
}
