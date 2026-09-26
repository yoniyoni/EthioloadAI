/**
 * api.ts — Centralized HTTP client for Freight-Link.
 *
 * Spring Boot route structure:
 *   /auth/*  — authentication endpoints (register, login, logout, me)
 *   /api/*   — all other application endpoints (freight, vehicles, trips, etc.)
 *
 * Two client objects are exported:
 *   `api`  — prepends /api to every path.  Use for all application routes.
 *   `auth` — uses the path as-is.          Use for /auth/* routes.
 *
 * Existing component code that uses `api.get("/freight")` etc. is
 * unaffected — the /api prefix is still applied automatically.
 *
 * 401 handling:
 *   When any request receives HTTP 401, a custom DOM event
 *   "freightlink:unauthorized" is dispatched. The AuthProvider
 *   listens for this event and clears the session + redirects to /login.
 *   This avoids coupling this module to React context or causing circular imports.
 *
 * Mock auth (offline / no-backend mode):
 *   When the Spring Boot backend is not reachable, /auth/register and
 *   /auth/login are handled locally. Registered accounts are persisted in
 *   localStorage so they survive page refreshes. This allows the UI to be
 *   fully exercised without a running server.
 */

// ── Token helpers ─────────────────────────────────────────────────────────────

const AUTH_STORAGE_KEY = "freightlink_auth";

// ── Mock auth store ───────────────────────────────────────────────────────────

const MOCK_USERS_KEY = "freightlink_mock_users";

interface MockUser {
  id: number;
  name: string;
  email: string;
  phone: string;
  role: string;
  passwordHash: string; // simple btoa encode — NOT secure, demo only
  isVerified: boolean;
  createdAt: string;
}

function getMockUsers(): MockUser[] {
  try {
    const raw = localStorage.getItem(MOCK_USERS_KEY);
    return raw ? (JSON.parse(raw) as MockUser[]) : [];
  } catch {
    return [];
  }
}

function saveMockUsers(users: MockUser[]): void {
  localStorage.setItem(MOCK_USERS_KEY, JSON.stringify(users));
}

// ── Seed admin account ────────────────────────────────────────────────────────
// Ensures the default admin always exists in the mock store, regardless of
// whether the backend is running. Called once on module load.
(function seedAdminAccount() {
  const ADMIN_EMAIL = "abelmulu88@gmail.com";
  const ADMIN_PASSWORD = "Admin@1234";
  const users = getMockUsers();
  if (!users.some(u => u.email === ADMIN_EMAIL)) {
    users.push({
      id: 1000000001,
      name: "Admin",
      email: ADMIN_EMAIL,
      phone: "+251911000000",
      role: "admin",
      passwordHash: btoa(ADMIN_PASSWORD),
      isVerified: true,
      createdAt: new Date().toISOString(),
    });
    saveMockUsers(users);
    console.info("[FreightLink] Default admin account seeded.");
  }
})();

function makeMockToken(userId: number): string {
  // Not a real JWT — just a recognisable, stable string for the UI layer.
  return `mock.${btoa(String(userId))}.${btoa(Date.now().toString())}`;
}

/**
 * Attempt to register via the server-side Vite mailer plugin endpoint.
 * Falls back to the in-browser localStorage mock only if the mailer server
 * is also unreachable (e.g. production build).
 */
async function serverRegister(body: Record<string, unknown>): Promise<{ token: string; user: object }> {
  const res = await fetch("/mailer/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message ?? `Registration failed (${res.status})`);
  return data as { token: string; user: object };
}

/**
 * Attempt to log in via the server-side Vite mailer plugin endpoint.
 */
async function serverLogin(body: Record<string, unknown>): Promise<{ token: string; user: object }> {
  const res = await fetch("/mailer/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message ?? `Login failed (${res.status})`);
  return data as { token: string; user: object };
}

/**
 * Check if the server-side auth endpoint is available.
 */
async function isServerAuthAvailable(): Promise<boolean> {
  try {
    const res = await fetch("/mailer/auth/me", { method: "GET" });
    // Any response (even 401) means the server endpoint exists
    return res.status !== 404;
  } catch {
    return false;
  }
}
function mockRegister(body: Record<string, unknown>): { token: string; user: object } {
  const users = getMockUsers();
  const email = String(body.email ?? "").toLowerCase().trim();
  const phone = String(body.phone ?? "").trim();

  if (users.some(u => u.email === email)) {
    throw new Error("An account with this email already exists.");
  }
  if (phone && users.some(u => u.phone === phone)) {
    throw new Error("An account with this phone number already exists.");
  }

  const newUser: MockUser = {
    id: Date.now(),
    name: String(body.fullName ?? body.name ?? "User"),
    email,
    phone,
    role: String(body.role ?? "shipper"),
    passwordHash: btoa(String(body.password ?? "")),
    isVerified: false,
    createdAt: new Date().toISOString(),
  };

  users.push(newUser);
  saveMockUsers(users);

  const { passwordHash: _pw, ...publicUser } = newUser;
  return { token: makeMockToken(newUser.id), user: publicUser };
}

/**
 * Attempt to log in against the local mock store.
 * Accepts email or phone as identifier.
 */
function mockLogin(body: Record<string, unknown>): { token: string; user: object } {
  const users = getMockUsers();
  const identifier = String(body.identifier ?? body.email ?? "").toLowerCase().trim();
  const password = String(body.password ?? "");

  const user = users.find(
    u => u.email === identifier || u.phone === identifier
  );

  if (!user) {
    throw new Error("No account found with that email or phone number.");
  }
  if (user.passwordHash !== btoa(password)) {
    throw new Error("Incorrect password.");
  }

  const { passwordHash: _pw, ...publicUser } = user;
  return { token: makeMockToken(user.id), user: publicUser };
}

/**
 * Returns true when the error indicates the backend is simply not available
 * (network failure, 502/503/504, or 500 Internal Server Error).
 */
function isBackendUnavailable(err: unknown): boolean {
  if (!(err instanceof Error)) return false;
  const msg = err.message.toLowerCase();
  // Network-level failure (fetch threw before getting a response)
  if (msg === "failed to fetch" || msg.includes("networkerror") || msg.includes("network request failed")) return true;
  // Vite dev-proxy turns ECONNREFUSED into a 504 or surfaces as "failed to fetch"
  if (msg.includes("econnrefused")) return true;
  // Server-side errors that indicate the backend process isn't up
  if (msg.startsWith("http 500") || msg.startsWith("http 502") ||
      msg.startsWith("http 503") || msg.startsWith("http 504")) return true;
  // Status text fall-through
  if (msg.includes("internal server error") || msg.includes("bad gateway") ||
      msg.includes("service unavailable") || msg.includes("gateway timeout")) return true;
  return false;
}

export function getToken(): string | null {
  try {
    const stored = localStorage.getItem(AUTH_STORAGE_KEY);
    if (stored) return JSON.parse(stored).token ?? null;
  } catch {
    // Corrupted storage — treat as unauthenticated
  }
  return null;
}

// ── Server-side carrier-companies store ──────────────────────────────────────
// All company data is stored server-side via /mailer/companies/* so it is
// shared across all browsers. The localStorage keys are kept as fallback
// names only for the migration helper in the Vite plugin.

const MOCK_COMPANIES_KEY     = "freightlink_mock_companies";      // kept for migration reference
const MOCK_COMPANY_DOCS_KEY  = "freightlink_mock_company_docs";   // kept for migration reference

// ── One-time localStorage → server migration ──────────────────────────────────
// If the user previously had companies in localStorage (old data), copy them
// to the server store on first load so they appear in all browsers.
(async function migrateLocalStorageCompanies() {
  try {
    const raw = localStorage.getItem(MOCK_COMPANIES_KEY);
    if (!raw) return;
    const localCos = JSON.parse(raw) as Array<Record<string, unknown>>;
    if (!localCos.length) return;
    for (const co of localCos) {
      // Check if this company already exists on the server (by id)
      try {
        const check = await fetch(`/mailer/companies/${co.id}`);
        if (check.ok) continue; // already migrated
      } catch { /* server not up, skip */ return; }
      // POST it to the server
      await fetch("/mailer/companies", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(co),
      }).catch(() => null);
    }
    // Clear localStorage after migration
    localStorage.removeItem(MOCK_COMPANIES_KEY);
    localStorage.removeItem(MOCK_COMPANY_DOCS_KEY);
    console.info("[FreightLink] Migrated localStorage companies to server store.");
  } catch { /* non-fatal */ }
})();

/** Get the current user's id from the stored auth session. Returns 0 if unavailable. */
function currentUserId(): number {
  try {
    const stored = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!stored) return 0;
    const parsed = JSON.parse(stored);
    return parsed?.user?.id ?? 0;
  } catch { return 0; }
}

/** Get the current auth token for server-side requests. */
function currentToken(): string {
  return getToken() ?? "";
}

/** Fetch from the local Vite plugin server with auth header. */
async function mailerFetch<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const res = await fetch(`/mailer/companies${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(currentToken() ? { "Authorization": `Bearer ${currentToken()}` } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data?.message ?? `Server error ${res.status}`);
  return data as T;
}

/**
 * Route mock api calls for /carrier-companies/* to the server-side store.
 */
async function mockCarrierCompany<T>(
  method: string,
  path: string,
  body?: unknown,
  file?: File,
): Promise<T> {
  const ownerId = currentUserId();

  // POST /carrier-companies  — create new company
  if (method === "POST" && path === "/carrier-companies") {
    return mailerFetch<T>("POST", "", { ...(body as object), ownerId });
  }

  // GET /carrier-companies/me  — list owner's companies (paginated)
  if (method === "GET" && /^\/carrier-companies\/me(\?.*)?$/.test(path)) {
    return mailerFetch<T>("GET", `/owner/${ownerId}`);
  }

  // GET /carrier-companies/:id  — get single company
  const singleMatch = path.match(/^\/carrier-companies\/(\d+)$/);
  if (method === "GET" && singleMatch) {
    return mailerFetch<T>("GET", `/${singleMatch[1]}`);
  }

  // PUT /carrier-companies/:id  — update company details
  const updateMatch = path.match(/^\/carrier-companies\/(\d+)$/);
  if (method === "PUT" && updateMatch) {
    return mailerFetch<T>("PUT", `/${updateMatch[1]}`, body);
  }

  // POST /carrier-companies/:id/submit  — submit for review
  const submitMatch = path.match(/^\/carrier-companies\/(\d+)\/submit$/);
  if (method === "POST" && submitMatch) {
    return mailerFetch<T>("POST", `/${submitMatch[1]}/submit`, {});
  }

  // GET /carrier-companies/:id/documents  — list docs
  const docsListMatch = path.match(/^\/carrier-companies\/(\d+)\/documents$/);
  if (method === "GET" && docsListMatch) {
    return mailerFetch<T>("GET", `/${docsListMatch[1]}/documents`);
  }

  // POST /carrier-companies/:id/documents  — upload document (FormData)
  const docUploadMatch = path.match(/^\/carrier-companies\/(\d+)\/documents$/);
  if (method === "UPLOAD" && docUploadMatch && file) {
    const companyId = Number(docUploadMatch[1]);
    const documentType = String((body as FormData)?.get?.("documentType") ?? "UNKNOWN");
    // Convert file to base64 to send as JSON to the server
    const dataUrl = await new Promise<string>((res, rej) => {
      const reader = new FileReader();
      reader.onload = () => res(reader.result as string);
      reader.onerror = rej;
      reader.readAsDataURL(file);
    });
    return mailerFetch<T>("POST", `/${companyId}/documents`, {
      documentType, originalName: file.name, dataUrl,
    });
  }

  // GET /carrier-companies/:companyId/documents/:docId/file  — download doc
  const docDownloadMatch = path.match(/^\/carrier-companies\/(\d+)\/documents\/(\d+)\/file$/);
  if (method === "BLOB" && docDownloadMatch) {
    // Fetch from server, get the dataUrl, convert to blob
    const companyId = docDownloadMatch[1];
    const docId = Number(docDownloadMatch[2]);
    const docs = await mailerFetch<Array<{ id: number; dataUrl?: string; originalName: string }>>(
      "GET", `/${companyId}/documents`
    );
    // Server strips dataUrl from pubDoc — re-fetch raw
    const rawRes = await fetch(`/mailer/companies/${companyId}/documents`, {
      headers: { "Authorization": `Bearer ${currentToken()}` },
    });
    const rawDocs = await rawRes.json() as Array<{ id: number; dataUrl?: string; originalName: string }>;
    const doc = rawDocs.find(d => d.id === docId);
    if (!doc?.dataUrl) throw new Error("Document file not found");
    const blobRes = await fetch(doc.dataUrl);
    return await blobRes.blob() as T;
  }

  // POST /carrier-companies/:id/team/invitations  — handled by InviteTeamCard
  if (method === "POST" && /\/carrier-companies\/\d+\/team\/invitations$/.test(path)) {
    return {} as T;
  }

  throw new Error(`Mock not implemented: ${method} ${path}`);
}

/** True when path is under /carrier-companies */
function isCarrierCompaniesPath(path: string): boolean {
  return path.startsWith("/carrier-companies");
}

// ── Mock company-invitations (validate / accept) ──────────────────────────────
// These are called by invitation-accept.tsx when the user clicks the email link.
// The token is a base64-encoded JSON payload created by createMockInvitation()
// in carrier-companies.tsx.

const MOCK_INVITATIONS_KEY = "freightlink_mock_invitations";

interface StoredMockInvitation {
  id: number;
  companyId: number;
  companyName: string;
  email: string;
  role: string;
  token: string;
  sentAt: string;
  expiresAt: string;
  status: "pending" | "accepted";
}

function getStoredInvitations(): StoredMockInvitation[] {
  try { return JSON.parse(localStorage.getItem(MOCK_INVITATIONS_KEY) ?? "[]"); }
  catch { return []; }
}
function saveStoredInvitations(inv: StoredMockInvitation[]): void {
  localStorage.setItem(MOCK_INVITATIONS_KEY, JSON.stringify(inv));
}

/**
 * Decode a mock invitation token.
 * Tokens are base64(JSON{companyId,email,role,ts}) with padding stripped.
 * Falls back gracefully if the token format doesn't match.
 */
function decodeMockToken(token: string): { companyId: number; email: string; role: string; ts: number } | null {
  try {
    // Restore base64 padding if stripped
    const padded = token + "===".slice((token.length % 4) || 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

async function mockCompanyInvitation<T>(method: string, path: string, body?: unknown): Promise<T> {
  const payload = (body ?? {}) as Record<string, unknown>;
  const token   = String(payload.token ?? "");

  // ── POST /company-invitations/validate ─────────────────────────────────────
  if (path === "/company-invitations/validate") {
    // 1. Look up the token in the stored invitation list first (most reliable)
    const all = getStoredInvitations();
    const inv = all.find(i => i.token === token);

    if (inv) {
      if (inv.status !== "pending") {
        throw new Error("This invitation has already been accepted.");
      }
      if (new Date(inv.expiresAt) < new Date()) {
        throw new Error("This invitation has expired. Ask the company owner for a new one.");
      }
      return {
        companyName: inv.companyName,
        role:        inv.role,
        email:       inv.email,
        expiresAt:   inv.expiresAt.replace("Z", ""), // strip Z — matches Spring LocalDateTime format
      } as T;
    }

    // 2. Fallback: decode the self-contained token payload
    const decoded = decodeMockToken(token);
    if (!decoded) {
      throw new Error("This invitation link is invalid or has expired. Ask the company owner for a new one.");
    }
    const expiresAt = new Date(decoded.ts + 24 * 60 * 60 * 1000);
    if (expiresAt < new Date()) {
      throw new Error("This invitation has expired. Ask the company owner for a new one.");
    }
    // Look up the company name from the mock companies store
    const companies = getMockCompanies();
    const company   = companies.find(c => c.id === decoded.companyId);
    const roleLabel = decoded.role === "FLEET_MANAGER" ? "Fleet Manager"
                    : decoded.role === "DISPATCHER"    ? "Dispatcher"
                    : decoded.role;
    return {
      companyName: company?.companyName ?? "Your company",
      role:        roleLabel,
      email:       decoded.email,
      expiresAt:   expiresAt.toISOString().replace("Z", ""),
    } as T;
  }

  // ── POST /company-invitations/accept ───────────────────────────────────────
  if (path === "/company-invitations/accept") {
    const all = getStoredInvitations();
    const idx = all.findIndex(i => i.token === token);

    if (idx !== -1) {
      if (all[idx].status !== "pending") {
        throw new Error("This invitation has already been accepted.");
      }
      if (new Date(all[idx].expiresAt) < new Date()) {
        throw new Error("This invitation has expired.");
      }
      all[idx].status = "accepted";
      saveStoredInvitations(all);
      return { message: "You have successfully joined the company." } as T;
    }

    // Fallback: decode the token — as long as it is not expired, accept it
    const decoded = decodeMockToken(token);
    if (!decoded) throw new Error("Invalid invitation token.");
    const expiresAt = new Date(decoded.ts + 24 * 60 * 60 * 1000);
    if (expiresAt < new Date()) throw new Error("This invitation has expired.");
    // Mark accepted in the stored list if an entry was created later
    return { message: "You have successfully joined the company." } as T;
  }

  throw new Error(`Mock not implemented: ${method} ${path}`);
}

function isInvitationPath(path: string): boolean {
  return path.startsWith("/company-invitations");
}

/** True when path is under /admin/* and needs a mock fallback */
function isAdminMockPath(path: string): boolean {
  return (
    /^\/admin\/carrier-companies/.test(path) ||
    /^\/admin\/fleet-owners/.test(path) ||
    /^\/admin\/stats/.test(path) ||
    /^\/admin\/settings\/pricing/.test(path) ||
    /^\/admin\/analytics/.test(path) ||
    /^\/admin\/bookings/.test(path) ||
    /^\/admin\/escrow/.test(path) ||
    /^\/admin\/drivers/.test(path) ||
    /^\/admin\/users/.test(path) ||
    // Misc endpoints the admin dashboard polls that have no backend
    /^\/users/.test(path) ||
    /^\/drivers/.test(path) ||
    /^\/vehicles/.test(path) ||
    /^\/payments/.test(path) ||
    /^\/documents/.test(path) ||
    /^\/disputes/.test(path) ||
    /^\/notifications/.test(path) ||
    /^\/trips/.test(path) ||
    /^\/freight/.test(path)
  );
}

/**
 * Route mock api calls for /admin/* when the backend is down.
 */
async function mockAdmin<T>(method: string, path: string, body?: unknown): Promise<T> {
  const allUsers = getMockUsers();

  // ── GET /admin/carrier-companies?status=X ──────────────────────────────────
  if (method === "GET" && /^\/admin\/carrier-companies(\?.*)?$/.test(path)) {
    const qs     = path.includes("?") ? new URLSearchParams(path.split("?")[1]) : null;
    const status = qs?.get("status") ?? "";
    const url    = status ? `/mailer/companies?status=${status}` : "/mailer/companies";
    const res    = await fetch(url);
    return res.json() as T;
  }

  // ── GET /admin/carrier-companies/:id ──────────────────────────────────────
  const adminCoMatch = path.match(/^\/admin\/carrier-companies\/(\d+)$/);
  if (method === "GET" && adminCoMatch) {
    const res = await fetch(`/mailer/companies/${adminCoMatch[1]}`);
    if (!res.ok) throw new Error("Company not found");
    return res.json() as T;
  }

  // ── POST /admin/carrier-companies/:id/approve|reject|request-information ──
  const reviewMatch = path.match(/^\/admin\/carrier-companies\/(\d+)\/(approve|reject|request-information)$/);
  if (method === "POST" && reviewMatch) {
    const res = await fetch(`/mailer/companies/${reviewMatch[1]}/${reviewMatch[2]}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body ?? {}),
    });
    return res.json() as T;
  }

  // ── GET /admin/fleet-owners ───────────────────────────────────────────────
  if (method === "GET" && /^\/admin\/fleet-owners(\?.*)?$/.test(path)) {
    const owners = allUsers
      .filter(u => u.role === "fleet_owner")
      .map(u => ({
        id: u.id,
        name: u.name,
        email: u.email,
        phone: u.phone,
        role: u.role,
        isVerified: u.isVerified,
        isActive: true,
        drivers: [],
        vehicles: [],
        driverCount: 0,
        vehicleCount: 0,
        createdAt: u.createdAt,
      }));
    return { fleet_owners: owners, total: owners.length } as T;
  }

  // ── GET /admin/stats ──────────────────────────────────────────────────────
  if (method === "GET" && path.startsWith("/admin/stats")) {
    return {
      totalUsers: allUsers.length,
      totalDrivers: allUsers.filter(u => u.role === "driver").length,
      verifiedDrivers: 0,
      pendingVerifications: 0,
      activeFreight: 0,
      completedDeliveries: 0,
      totalRevenue: 0,
      escrowHeld: 0,
      totalCommissions: 0,
      openDisputes: 0,
    } as T;
  }

  // ── GET /admin/settings/pricing ───────────────────────────────────────────
  if (method === "GET" && path.startsWith("/admin/settings/pricing")) {
    return { rate_min: 18, rate_max: 28 } as T;
  }

  // ── PATCH /admin/settings/pricing ─────────────────────────────────────────
  if (method === "PATCH" && path.startsWith("/admin/settings/pricing")) {
    return { ...(body as object), success: true } as T;
  }

  // ── GET /admin/analytics/* ────────────────────────────────────────────────
  if (method === "GET" && path.startsWith("/admin/analytics")) {
    return [] as T;
  }

  // ── GET /admin/bookings/unpaid ────────────────────────────────────────────
  if (method === "GET" && path.startsWith("/admin/bookings/unpaid")) {
    return { bookings: [], total: 0 } as T;
  }

  // ── GET /admin/escrow ─────────────────────────────────────────────────────
  if (method === "GET" && path.startsWith("/admin/escrow")) {
    return { held: { count: 0, total: 0 }, inTransit: { count: 0, total: 0 }, released: { count: 0, total: 0 }, disputed: { count: 0, total: 0 } } as T;
  }

  // ── POST /admin/drivers ───────────────────────────────────────────────────
  if (method === "POST" && path.startsWith("/admin/drivers")) {
    return { id: Date.now(), message: "Driver created (mock)" } as T;
  }

  // ── POST /admin/users ─────────────────────────────────────────────────────
  if (method === "POST" && path.startsWith("/admin/users")) {
    return { id: Date.now(), message: "User created (mock)" } as T;
  }

  // ── PUT /admin/users/:id ──────────────────────────────────────────────────
  if (method === "PUT" && /^\/admin\/users\/\d+$/.test(path)) {
    return { ...(body as object) } as T;
  }

  // ── DELETE /admin/users/:id ───────────────────────────────────────────────
  if (method === "DELETE" && /^\/admin\/users\/\d+$/.test(path)) {
    return {} as T;
  }

  // ── GET /users ────────────────────────────────────────────────────────────
  if (method === "GET" && /^\/users/.test(path)) {
    const users = allUsers.map(u => ({
      id: u.id, name: u.name, fullName: u.name, email: u.email, phone: u.phone,
      role: u.role, isVerified: u.isVerified, isActive: true, createdAt: u.createdAt,
    }));
    return { users, total: users.length } as T;
  }

  // ── GET /drivers ──────────────────────────────────────────────────────────
  if (method === "GET" && /^\/drivers/.test(path)) {
    const drivers = allUsers
      .filter(u => u.role === "driver")
      .map(u => ({ id: u.id, fullName: u.name, phone: u.phone, email: u.email, status: "PENDING", vehicles: [] }));
    return { content: drivers, totalElements: drivers.length } as T;
  }

  // ── PATCH /drivers/:id/status ─────────────────────────────────────────────
  if (method === "PATCH" && /^\/drivers\/\d+\/status$/.test(path)) {
    return { ...(body as object) } as T;
  }

  // ── GET /vehicles ─────────────────────────────────────────────────────────
  if (method === "GET" && /^\/vehicles/.test(path)) {
    return [] as T;
  }

  // ── GET /payments ─────────────────────────────────────────────────────────
  if (method === "GET" && /^\/payments/.test(path)) {
    return [] as T;
  }

  // ── POST /payments/:id/fail ───────────────────────────────────────────────
  if (method === "POST" && /^\/payments\/\d+\/fail$/.test(path)) {
    return {} as T;
  }

  // ── GET /documents ────────────────────────────────────────────────────────
  if (method === "GET" && /^\/documents/.test(path)) {
    return [] as T;
  }

  // ── POST /documents/:id/review ────────────────────────────────────────────
  if (method === "POST" && /^\/documents\/\d+\/review$/.test(path)) {
    return {} as T;
  }

  // ── GET /disputes ─────────────────────────────────────────────────────────
  if (method === "GET" && /^\/disputes/.test(path)) {
    return { disputes: [], total: 0 } as T;
  }

  // ── PATCH /disputes/:id/resolve ───────────────────────────────────────────
  if (method === "PATCH" && /^\/disputes\/\d+\/resolve$/.test(path)) {
    return {} as T;
  }

  // ── GET /notifications ────────────────────────────────────────────────────
  if (method === "GET" && /^\/notifications/.test(path)) {
    return { data: [], unreadCount: 0 } as T;
  }

  // ── PATCH /notifications/* ────────────────────────────────────────────────
  if (method === "PATCH" && /^\/notifications/.test(path)) {
    return {} as T;
  }

  // ── GET /trips ────────────────────────────────────────────────────────────
  if (method === "GET" && /^\/trips/.test(path)) {
    return { content: [], totalElements: 0 } as T;
  }

  // ── GET /freight ──────────────────────────────────────────────────────────
  if (method === "GET" && /^\/freight/.test(path)) {
    return { content: [], totalElements: 0 } as T;
  }

  // ── POST /admin/bookings/:id/mark-cash-paid ───────────────────────────────
  if (method === "POST" && /^\/admin\/bookings\/\d+\/mark-cash-paid$/.test(path)) {
    return {} as T;
  }

  throw new Error(`Mock not implemented: ${method} ${path}`);
}

// ── 401 event ─────────────────────────────────────────────────────────────────

/**
 * Notify the application that the current session is no longer valid.
 * Dispatched on the window so AuthProvider (or any listener) can react
 * without this module needing a direct React import.
 */
function notifyUnauthorized(): void {
  window.dispatchEvent(new CustomEvent("freightlink:unauthorized"));
}

// ── Error extraction ──────────────────────────────────────────────────────────

async function extractErrorMessage(res: Response): Promise<string> {
  let msg = `HTTP ${res.status}`;
  try {
    const json = await res.json();
    // Spring Boot GlobalExceptionHandler returns { message, code }
    // Laravel returns { error } or { message }
    msg = json.message ?? json.error ?? msg;
  } catch {
    // Response body is not JSON — use the status text
    if (res.statusText) msg = res.statusText;
  }
  return msg;
}

// ── Core fetch ────────────────────────────────────────────────────────────────

/**
 * Low-level fetch wrapper shared by both `api` and `auth` clients.
 *
 * @param url  The fully-resolved URL path (e.g. "/api/freight" or "/auth/login").
 * @param options  Standard RequestInit options.  Do NOT include Authorization —
 *                 it is attached here from secure storage.
 *
 * Security:
 *   - The Authorization header value is never logged.
 *   - Passwords and tokens are never written to console.
 */
async function coreFetch<T>(
  url: string,
  options: RequestInit = {}
): Promise<T> {
  const token = getToken();

  const isFormData = options.body instanceof FormData;
  const headers: HeadersInit = {
    ...(isFormData ? {} : { "Content-Type": "application/json" }),
    // Token attached here — never logged
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers ?? {}),
  };

  const res = await fetch(url, { ...options, headers });

  // Handle expired / invalid session centrally
  if (res.status === 401) {
    notifyUnauthorized();
    const msg = await extractErrorMessage(res);
    throw new Error(msg);
  }

  if (!res.ok) {
    const msg = await extractErrorMessage(res);
    throw new Error(msg);
  }

  return res.json() as Promise<T>;
}

// ── /api/* client (application routes) ───────────────────────────────────────

/**
 * Use for all Spring Boot /api/* endpoints.
 * Automatically prepends /api so call sites use short paths:
 *   api.get("/freight")  →  GET /api/freight
 *   api.post("/payments", body)  →  POST /api/payments
 */
const API_BASE = "/api";

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  return coreFetch<T>(`${API_BASE}${path}`, options);
}

export const api = {
  get: async <T>(path: string): Promise<T> => {
    try {
      return await apiFetch<T>(path);
    } catch (err) {
      if (isBackendUnavailable(err)) {
        if (isCarrierCompaniesPath(path)) return mockCarrierCompany<T>("GET", path);
        if (isAdminMockPath(path)) return mockAdmin<T>("GET", path);
      }
      throw err;
    }
  },
  post: async <T>(path: string, body: unknown = {}): Promise<T> => {
    try {
      return await apiFetch<T>(path, { method: "POST", body: JSON.stringify(body) });
    } catch (err) {
      if (isBackendUnavailable(err)) {
        if (isInvitationPath(path))       return mockCompanyInvitation<T>("POST", path, body);
        if (isCarrierCompaniesPath(path)) return mockCarrierCompany<T>("POST", path, body);
        if (isAdminMockPath(path))        return mockAdmin<T>("POST", path, body);
      }
      throw err;
    }
  },
  put: async <T>(path: string, body: unknown): Promise<T> => {
    try {
      return await apiFetch<T>(path, { method: "PUT", body: JSON.stringify(body) });
    } catch (err) {
      if (isBackendUnavailable(err)) {
        if (isCarrierCompaniesPath(path)) return mockCarrierCompany<T>("PUT", path, body);
        if (isAdminMockPath(path)) return mockAdmin<T>("PUT", path, body);
      }
      throw err;
    }
  },
  patch: async <T>(path: string, body: unknown): Promise<T> => {
    try {
      return await apiFetch<T>(path, { method: "PATCH", body: JSON.stringify(body) });
    } catch (err) {
      if (isBackendUnavailable(err)) {
        if (isAdminMockPath(path)) return mockAdmin<T>("PATCH", path, body);
      }
      throw err;
    }
  },
  del: async <T>(path: string): Promise<T> => {
    try {
      return await apiFetch<T>(path, { method: "DELETE" });
    } catch (err) {
      if (isBackendUnavailable(err)) {
        if (isAdminMockPath(path)) return mockAdmin<T>("DELETE", path);
      }
      throw err;
    }
  },
  upload: async <T>(path: string, body: FormData): Promise<T> => {
    try {
      return await apiFetch<T>(path, { method: "POST", body });
    } catch (err) {
      if (isBackendUnavailable(err)) {
        if (isCarrierCompaniesPath(path)) {
          const file = body.get("file") as File | null;
          return mockCarrierCompany<T>("UPLOAD", path, body, file ?? undefined);
        }
      }
      throw err;
    }
  },
  getBlob: async (path: string): Promise<Blob> => {
    if (isCarrierCompaniesPath(path)) {
      try {
        const token = getToken();
        const url = path.startsWith("http") || path.startsWith("/api") ? path : `${API_BASE}${path}`;
        const res = await fetch(url, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (res.status === 401) { notifyUnauthorized(); throw new Error("Unauthorized"); }
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.blob();
      } catch (err) {
        if (isBackendUnavailable(err)) {
          return mockCarrierCompany<Blob>("BLOB", path);
        }
        throw err;
      }
    }
    const token = getToken();
    const url = path.startsWith("http") || path.startsWith("/api") ? path : `${API_BASE}${path}`;
    const res = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (res.status === 401) {
      notifyUnauthorized();
      throw new Error("Unauthorized");
    }
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    return res.blob();
  },
};

// ── /auth/* client (authentication routes) ───────────────────────────────────

/**
 * Use for Spring Boot /auth/* endpoints.
 * The path is used as-is — do NOT add /api prefix for auth routes.
 *   auth.post("/auth/login", body)   →  POST /auth/login
 *   auth.get("/auth/me")             →  GET  /auth/me
 *   auth.patch("/auth/me", body)     →  PATCH /auth/me
 *
 * Note: The Vite dev proxy is configured to forward /auth/* to the
 * Spring Boot server alongside /api/*.
 */
async function authFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  return coreFetch<T>(path, options);
}

export const auth = {
  get: async <T>(path: string): Promise<T> => {
    try {
      return await authFetch<T>(path);
    } catch (err) {
      // For /auth/me: if Spring Boot is down, try the server-side mock
      if (path === "/auth/me" && isBackendUnavailable(err)) {
        const token = getToken();
        if (token) {
          const res = await fetch("/mailer/auth/me", {
            headers: { "Authorization": `Bearer ${token}` },
          }).catch(() => null);
          if (res && res.ok) return res.json() as Promise<T>;
        }
      }
      throw err;
    }
  },

  /**
   * POST to an /auth/* endpoint.
   *
   * For /auth/register and /auth/login:
   *  1. Try the real Spring Boot backend.
   *  2. If unavailable, try the server-side Vite mailer plugin (shared across browsers).
   *  3. If that too is unavailable, fall back to the local localStorage mock.
   */
  post: async <T>(path: string, body: unknown): Promise<T> => {
    const isMockablePath = path === "/auth/register" || path === "/auth/login";

    try {
      return await authFetch<T>(path, {
        method: "POST",
        body: JSON.stringify(body),
      });
    } catch (err) {
      if (!isMockablePath || !isBackendUnavailable(err)) throw err;

      // Spring Boot is down — try server-side shared store first
      const payload = (body ?? {}) as Record<string, unknown>;
      try {
        const result = path === "/auth/register"
          ? await serverRegister(payload)
          : await serverLogin(payload);
        console.info(`[FreightLink] Used server-side mock auth for ${path}.`);
        return result as T;
      } catch (serverErr) {
        // Server-side also unavailable (production build, etc.) — use localStorage
        if (serverErr instanceof Error && serverErr.message.includes("Failed to fetch")) {
          const result = path === "/auth/register"
            ? mockRegister(payload)
            : mockLogin(payload);
          console.info(`[FreightLink] Used localStorage mock auth for ${path}.`);
          return result as T;
        }
        // Server returned a real error (e.g. duplicate email, wrong password) — show it
        throw serverErr;
      }
    }
  },

  patch: <T>(path: string, body: unknown) =>
    authFetch<T>(path, { method: "PATCH",  body: JSON.stringify(body) }),
  del:   <T>(path: string) =>
    authFetch<T>(path, { method: "DELETE" }),
};

// end of api.ts

