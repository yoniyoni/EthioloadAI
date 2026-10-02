import { useEffect, useState } from "react";
import { useLocation } from "wouter";
import { useAuth, type User } from "@/contexts/auth-context";
import { api, auth, ApiError } from "@/lib/api";
import { captureInvitation, clearInvitation } from "@/lib/invitation-session";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type Preview = { companyName: string; role: string; email: string; expiresAt: string; accountExists: boolean };

export default function InvitationAccept() {
  const { isAuthenticated, isLoading, user, login, refreshMemberships } = useAuth();
  const [, navigate] = useLocation();
  const [token, setToken] = useState(captureInvitation);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [joined, setJoined] = useState(false);
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  useEffect(() => {
    const capture = () => { setToken(captureInvitation()); setPreview(null); setError(""); setJoined(false); setPassword(""); };
    window.addEventListener("hashchange", capture);
    return () => window.removeEventListener("hashchange", capture);
  }, []);

  useEffect(() => {
    let active = true;
    if (!token) { setError("Open the invitation link from your email."); return; }
    api.post<Preview>("/company-invitations/validate", { token })
      .then(data => { if (active) setPreview(data); })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Unable to validate the invitation.");
          if (err instanceof ApiError && [400, 404, 409].includes(err.status)) clearInvitation();
        }
      });
    return () => { active = false; };
  }, [token]);

  async function accept(signIn = false) {
    setBusy(true);
    setError("");
    try {
      let member: {carrierCompanyId: number};
      if (signIn && preview && !preview.accountExists) {
        if (password !== confirmPassword) throw new Error("Passwords do not match.");
        const result = await api.post<{session: {token: string; user: User}; membership: {carrierCompanyId: number}}>(
          "/company-invitations/register", {token, fullName, phone, password});
        login(result.session.token, result.session.user);
        setPassword("");
        setConfirmPassword("");
        member = result.membership;
      } else {
      if (signIn && preview) {
        const session = await auth.post<{ token: string; user: User }>("/auth/login", {
          identifier: preview.email,
          password,
        });
        login(session.token, session.user);
        setPassword("");
      }
      member = await api.post<{carrierCompanyId: number}>("/company-invitations/accept", { token });
      }
      clearInvitation();
      setJoined(true);
      await refreshMemberships();
      navigate(`/company/${member.carrierCompanyId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to accept invitation.");
    } finally { setBusy(false); }
  }

  const matchingEmail = user?.email?.trim().toLowerCase() === preview?.email.trim().toLowerCase();
  return (
    <main className="mx-auto max-w-lg px-6 py-16 space-y-6">
      <h1 className="text-3xl font-bold">Company invitation</h1>
      {error && <p role="alert" className="text-destructive">{error}</p>}
      {joined ? <p role="status">You have joined {preview?.companyName}. Your company role is {preview?.role.replaceAll("_", " ")}.</p> :
        preview ? <>
          <h2 className="text-xl font-semibold">Join {preview.companyName}</h2>
          <p>You are invited as {preview.role.replaceAll("_", " ")} using {preview.email}.</p>
          <p className="text-sm text-muted-foreground">Expires {new Date(/[Z+-]\d*:?\d*$/.test(preview.expiresAt) ? preview.expiresAt : preview.expiresAt + "Z").toLocaleString()}.</p>
          {isLoading ? <p>Loading your account...</p> : isAuthenticated && matchingEmail ?
            <Button disabled={busy} onClick={() => void accept()}>{busy ? "Joining..." : "Accept invitation"}</Button> :
            <form className="space-y-4" onSubmit={event => { event.preventDefault(); void accept(true); }}>
              {isAuthenticated && <p className="text-sm text-muted-foreground">You are currently signed in as {user?.email}. Continue below to switch to the invited account.</p>}
              <div className="space-y-2">
                <Label htmlFor="invited-email">Invited email</Label>
                <Input id="invited-email" type="email" autoComplete="username" value={preview.email} readOnly />
              </div>
              {!preview.accountExists && <>
                <p>Create your account to join {preview.companyName} as {preview.role.replaceAll("_", " ")}.</p>
                <div className="space-y-2">
                  <Label htmlFor="invitation-name">Full name</Label>
                  <Input id="invitation-name" autoComplete="name" value={fullName} onChange={event => setFullName(event.target.value)} required maxLength={255} disabled={busy} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="invitation-phone">Phone number</Label>
                  <Input id="invitation-phone" type="tel" autoComplete="tel" placeholder="+251911234567" pattern="\+?[0-9]{10,15}" value={phone} onChange={event => setPhone(event.target.value)} required disabled={busy} />
                </div>
              </>}
              <div className="space-y-2">
                <Label htmlFor="invitation-password">{preview.accountExists ? "Password" : "Choose a password"}</Label>
                <Input id="invitation-password" type="password" autoComplete={preview.accountExists ? "current-password" : "new-password"} value={password}
                  minLength={preview.accountExists ? undefined : 6} maxLength={preview.accountExists ? undefined : 72}
                  onChange={event => setPassword(event.target.value)} required disabled={busy} />
                <p className="text-sm text-muted-foreground">{preview.accountExists ? "This email already has an account. Enter its existing password to join." : "Use at least 6 characters. You will be signed in automatically."}</p>
              </div>
              {!preview.accountExists && <div className="space-y-2">
                <Label htmlFor="invitation-confirm-password">Confirm password</Label>
                <Input id="invitation-confirm-password" type="password" autoComplete="new-password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} required disabled={busy} />
              </div>}
              <Button type="submit" disabled={busy}>{busy ? "Joining..." : preview.accountExists ? "Sign in and accept invitation" : "Create account and join company"}</Button>
            </form>}
        </> : !error && <p>Checking invitation…</p>}
    </main>
  );
}
