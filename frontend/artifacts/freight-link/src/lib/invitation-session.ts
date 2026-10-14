const KEY = "freightlink_pending_invitation";
const DEADLINE = `${KEY}_deadline`;
export function clearInvitation() {
  sessionStorage.removeItem(KEY); sessionStorage.removeItem(DEADLINE);
}
export function pendingInvitation(): string {
  const token = sessionStorage.getItem(KEY) || "";
  const deadline = Number(sessionStorage.getItem(DEADLINE));
  if (token && (!deadline || Date.now() >= deadline)) { clearInvitation(); return ""; }
  return token;
}
export function captureInvitation(): string {
  const incoming = new URLSearchParams(location.hash.slice(1)).get("token") || new URLSearchParams(location.search).get("token");
  if (incoming) {
    sessionStorage.setItem(KEY, incoming);
    sessionStorage.setItem(DEADLINE, String(Date.now() + 60 * 60 * 1000));
    history.replaceState(null, "", location.pathname);
    return incoming;
  }
  return pendingInvitation();
}
