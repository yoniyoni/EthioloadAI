/**
 * vite-plugin-mailer.ts
 *
 * Vite dev-server plugin — shared server-side stores (accessible from any browser):
 *
 *  POST   /mailer/send-invitation          — Brevo SMTP email
 *  POST   /mailer/auth/register            — register user
 *  POST   /mailer/auth/login               — login user
 *  GET    /mailer/auth/me                  — get current user from token
 *  GET    /mailer/companies                — list all companies (admin)
 *  GET    /mailer/companies/owner/:userId  — list companies for a fleet owner
 *  GET    /mailer/companies/:id            — get single company
 *  POST   /mailer/companies               — create company
 *  PUT    /mailer/companies/:id            — update company
 *  POST   /mailer/companies/:id/submit     — submit for review
 *  POST   /mailer/companies/:id/approve    — admin approve
 *  POST   /mailer/companies/:id/reject     — admin reject
 *  POST   /mailer/companies/:id/request-information — admin request changes
 *  GET    /mailer/companies/:id/documents  — list docs for a company
 *  POST   /mailer/companies/:id/documents  — upload doc (JSON with base64)
 *
 * Stores are persisted to .mock-store.json so data survives Vite restarts.
 */

import type { Plugin } from "vite";
import type { IncomingMessage, ServerResponse } from "node:http";
import nodemailer from "nodemailer";
import fs     from "node:fs";
import path   from "node:path";
import crypto from "node:crypto";

// ── SMTP ──────────────────────────────────────────────────────────────────────
const SMTP_HOST  = "smtp-relay.brevo.com";
const SMTP_PORT  = 465;
const SMTP_USER  = process.env.SMTP_USER  ?? "b7fa0c001@smtp-brevo.com";
const SMTP_PASS  = process.env.SMTP_PASSWORD ?? "";   // set in backend-spring-boot/.env
const FROM_EMAIL = process.env.EMAIL_FROM ?? "abelmulu11online@gmail.com";
const FROM_NAME  = "Ethio-Freight";

const transporter = nodemailer.createTransport({
  host: SMTP_HOST, port: SMTP_PORT, secure: true,
  auth: { user: SMTP_USER, pass: SMTP_PASS },
});

// ── Types ─────────────────────────────────────────────────────────────────────
interface ServerUser {
  id: number; name: string; email: string; phone: string;
  role: string; passwordHash: string; isVerified: boolean; createdAt: string;
}
interface TokenRecord { token: string; userId: number; }
interface ServerCompany {
  id: number; ownerId: number; companyName: string; legalName: string | null;
  tinNumber: string | null; businessRegistrationNumber: string | null;
  phone: string | null; email: string | null; businessAddress: string | null;
  city: string | null; verificationStatus: string; reviewMessage: string | null;
  createdAt: string;
}
interface ServerCompanyDoc {
  id: number; companyId: number; documentType: string;
  originalName: string; verificationStatus: string;
  reviewMessage: string | null; dataUrl: string;
}

// ── Persistence ───────────────────────────────────────────────────────────────
const STORE_FILE = path.resolve(import.meta.dirname, ".mock-store.json");

let users:       ServerUser[]      = [];
let tokens:      TokenRecord[]     = [];
let companies:   ServerCompany[]   = [];
let companyDocs: ServerCompanyDoc[] = [];

function loadStore() {
  // Migrate from old .mock-users.json if present
  const OLD = path.resolve(import.meta.dirname, ".mock-users.json");
  if (!fs.existsSync(STORE_FILE) && fs.existsSync(OLD)) {
    try {
      const old = JSON.parse(fs.readFileSync(OLD, "utf8"));
      users  = old.users  ?? [];
      tokens = old.tokens ?? [];
    } catch { /* ignore */ }
  }
  if (fs.existsSync(STORE_FILE)) {
    try {
      const s = JSON.parse(fs.readFileSync(STORE_FILE, "utf8"));
      users       = s.users       ?? users;
      tokens      = s.tokens      ?? tokens;
      companies   = s.companies   ?? [];
      companyDocs = s.companyDocs ?? [];
    } catch { /* start fresh */ }
  }

  // Seed default admin
  if (!users.some(u => u.email === "abelmulu88@gmail.com")) {
    users.push({
      id: 1000000001, name: "Admin", email: "abelmulu88@gmail.com",
      phone: "+251911000000", role: "admin",
      passwordHash: hashPw("Admin@1234"), isVerified: true,
      createdAt: new Date().toISOString(),
    });
    console.info("[mock] Admin seeded.");
  }

  saveStore();
}

function saveStore() {
  try { fs.writeFileSync(STORE_FILE, JSON.stringify({ users, tokens, companies, companyDocs }, null, 2)); }
  catch { /* non-fatal */ }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function hashPw(pw: string) { return crypto.createHash("sha256").update(pw).digest("hex"); }
function makeToken(userId: number): string {
  const t = `mock.${userId}.${Date.now()}.${crypto.randomBytes(8).toString("hex")}`;
  tokens.push({ token: t, userId });
  saveStore();
  return t;
}
function pubUser(u: ServerUser) { const { passwordHash: _, ...p } = u; return p; }
function pubDoc(d: ServerCompanyDoc) { const { dataUrl: _, ...p } = d; return p; }

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((res, rej) => {
    const c: Buffer[] = [];
    req.on("data", (b: Buffer) => c.push(b));
    req.on("end",  () => res(Buffer.concat(c).toString("utf8")));
    req.on("error", rej);
  });
}
function json(res: ServerResponse, status: number, data: unknown) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    "Content-Type":                "application/json",
    "Content-Length":              Buffer.byteLength(body),
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods":"GET, POST, PUT, PATCH, DELETE, OPTIONS",
    "Access-Control-Allow-Headers":"Content-Type, Authorization",
  });
  res.end(body);
}
function tokenUser(req: IncomingMessage): ServerUser | null {
  const h = (req.headers["authorization"] ?? "") as string;
  const t = h.startsWith("Bearer ") ? h.slice(7) : "";
  const r = tokens.find(x => x.token === t);
  return r ? (users.find(u => u.id === r.userId) ?? null) : null;
}

// ── Auth ──────────────────────────────────────────────────────────────────────
function handleRegister(res: ServerResponse, b: Record<string, unknown>) {
  const email = String(b.email ?? "").toLowerCase().trim();
  const phone = String(b.phone ?? "").trim();
  const name  = String(b.fullName ?? b.name ?? "User");
  const pw    = String(b.password ?? "");
  const role  = String(b.role ?? "shipper").toLowerCase();
  if (!email || !pw) return json(res, 400, { message: "Email and password are required." });
  if (users.some(u => u.email === email)) return json(res, 409, { message: "An account with this email already exists." });
  if (phone && users.some(u => u.phone === phone)) return json(res, 409, { message: "An account with this phone number already exists." });
  const user: ServerUser = { id: Date.now(), name, email, phone, role, passwordHash: hashPw(pw), isVerified: false, createdAt: new Date().toISOString() };
  users.push(user);
  saveStore();
  console.info(`[mock-auth] Registered: ${email} (${role})`);
  json(res, 201, { token: makeToken(user.id), user: pubUser(user) });
}
function handleLogin(res: ServerResponse, b: Record<string, unknown>) {
  const id = String(b.identifier ?? b.email ?? "").toLowerCase().trim();
  const pw = String(b.password ?? "");
  const user = users.find(u => u.email === id || u.phone === id);
  if (!user) return json(res, 401, { message: "No account found with that email or phone number." });
  if (user.passwordHash !== hashPw(pw)) return json(res, 401, { message: "Incorrect password." });
  console.info(`[mock-auth] Login: ${id}`);
  json(res, 200, { token: makeToken(user.id), user: pubUser(user) });
}
function handleMe(res: ServerResponse, req: IncomingMessage) {
  const user = tokenUser(req);
  if (!user) return json(res, 401, { message: "Invalid or expired token." });
  json(res, 200, pubUser(user));
}

// ── Companies ─────────────────────────────────────────────────────────────────
function handleCompanies(method: string, url: string, res: ServerResponse, req: IncomingMessage, rawBody: string) {
  const caller = tokenUser(req);

  // GET /mailer/companies — all companies (admin dashboard)
  if (method === "GET" && url === "/mailer/companies") {
    const qs   = new URLSearchParams(url.includes("?") ? url.split("?")[1] : "");
    const status = qs.get("status") ?? "";
    const list = status ? companies.filter(c => c.verificationStatus === status) : companies;
    return json(res, 200, { content: list, last: true, totalElements: list.length });
  }

  // GET /mailer/companies/owner/:userId — fleet owner's companies
  const ownerMatch = url.match(/^\/mailer\/companies\/owner\/(\d+)/);
  if (method === "GET" && ownerMatch) {
    const ownerId = Number(ownerMatch[1]);
    const list = companies.filter(c => c.ownerId === ownerId);
    return json(res, 200, { content: list, last: true, totalElements: list.length });
  }

  // GET /mailer/companies/:id
  const idMatch = url.match(/^\/mailer\/companies\/(\d+)$/);
  if (method === "GET" && idMatch) {
    const co = companies.find(c => c.id === Number(idMatch[1]));
    if (!co) return json(res, 404, { message: "Company not found" });
    return json(res, 200, co);
  }

  // POST /mailer/companies — create
  if (method === "POST" && url === "/mailer/companies") {
    const p = JSON.parse(rawBody || "{}") as Record<string, unknown>;
    const ownerId = caller?.id ?? Number(p.ownerId ?? 0);
    const co: ServerCompany = {
      id: Date.now(), ownerId,
      companyName: String(p.companyName ?? ""),
      legalName: p.legalName ? String(p.legalName) : null,
      tinNumber: p.tinNumber ? String(p.tinNumber) : null,
      businessRegistrationNumber: p.businessRegistrationNumber ? String(p.businessRegistrationNumber) : null,
      phone: p.phone ? String(p.phone) : null,
      email: p.email ? String(p.email) : null,
      businessAddress: p.businessAddress ? String(p.businessAddress) : null,
      city: p.city ? String(p.city) : null,
      verificationStatus: "DRAFT", reviewMessage: null,
      createdAt: new Date().toISOString(),
    };
    companies.push(co);
    saveStore();
    console.info(`[mock-co] Created: ${co.companyName} for owner ${ownerId}`);
    return json(res, 201, co);
  }

  // PUT /mailer/companies/:id — update
  const putMatch = url.match(/^\/mailer\/companies\/(\d+)$/);
  if (method === "PUT" && putMatch) {
    const idx = companies.findIndex(c => c.id === Number(putMatch[1]));
    if (idx === -1) return json(res, 404, { message: "Company not found" });
    const p = JSON.parse(rawBody || "{}") as Record<string, unknown>;
    companies[idx] = { ...companies[idx], ...p, id: companies[idx].id, ownerId: companies[idx].ownerId };
    saveStore();
    return json(res, 200, companies[idx]);
  }

  // POST /mailer/companies/:id/submit
  const submitMatch = url.match(/^\/mailer\/companies\/(\d+)\/submit$/);
  if (method === "POST" && submitMatch) {
    const idx = companies.findIndex(c => c.id === Number(submitMatch[1]));
    if (idx === -1) return json(res, 404, { message: "Company not found" });
    companies[idx].verificationStatus = "PENDING";
    saveStore();
    return json(res, 200, companies[idx]);
  }

  // POST /mailer/companies/:id/approve|reject|request-information
  const reviewMatch = url.match(/^\/mailer\/companies\/(\d+)\/(approve|reject|request-information)$/);
  if (method === "POST" && reviewMatch) {
    const idx = companies.findIndex(c => c.id === Number(reviewMatch[1]));
    if (idx === -1) return json(res, 404, { message: "Company not found" });
    const p = JSON.parse(rawBody || "{}") as Record<string, unknown>;
    companies[idx].verificationStatus =
      reviewMatch[2] === "approve" ? "APPROVED" :
      reviewMatch[2] === "reject"  ? "REJECTED" : "MORE_INFORMATION_REQUIRED";
    companies[idx].reviewMessage = p.message ? String(p.message) : null;
    saveStore();
    return json(res, 200, companies[idx]);
  }

  // GET /mailer/companies/:id/documents
  const docsMatch = url.match(/^\/mailer\/companies\/(\d+)\/documents$/);
  if (method === "GET" && docsMatch) {
    const cid = Number(docsMatch[1]);
    return json(res, 200, companyDocs.filter(d => d.companyId === cid).map(pubDoc));
  }

  // POST /mailer/companies/:id/documents (JSON with base64 dataUrl)
  const docUpMatch = url.match(/^\/mailer\/companies\/(\d+)\/documents$/);
  if (method === "POST" && docUpMatch) {
    const cid = Number(docUpMatch[1]);
    const p = JSON.parse(rawBody || "{}") as Record<string, unknown>;
    const existing = companyDocs.findIndex(d => d.companyId === cid && d.documentType === String(p.documentType));
    const doc: ServerCompanyDoc = {
      id: Date.now(), companyId: cid,
      documentType: String(p.documentType ?? "UNKNOWN"),
      originalName: String(p.originalName ?? "document"),
      verificationStatus: "DRAFT", reviewMessage: null,
      dataUrl: String(p.dataUrl ?? ""),
    };
    if (existing >= 0) companyDocs[existing] = doc; else companyDocs.push(doc);
    saveStore();
    return json(res, 201, pubDoc(doc));
  }

  json(res, 404, { message: "Not found" });
}

// ── Plugin ────────────────────────────────────────────────────────────────────
export function mailerPlugin(): Plugin {
  loadStore();

  return {
    name:  "vite-plugin-mailer",
    apply: "serve",

    configureServer(server) {

      // Unified middleware — handles all /mailer/* routes
      server.middlewares.use(async (req: IncomingMessage, res: ServerResponse, next: () => void) => {
        const url = req.url ?? "";
        if (!url.startsWith("/mailer/")) return next();

        // Handle CORS preflight from Flutter web (port 8765 → port 5173)
        if (req.method === "OPTIONS") {
          res.writeHead(204, {
            "Access-Control-Allow-Origin":  "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type, Authorization",
            "Access-Control-Max-Age":       "86400",
            "Content-Length":               "0",
          });
          res.end();
          return;
        }

        const method = req.method ?? "GET";
        let rawBody = "";
        try { if (method === "POST" || method === "PUT") rawBody = await readBody(req); } catch { /* ignore */ }

        // ── Email ──────────────────────────────────────────────────────────
        if (url === "/mailer/send-invitation" && method === "POST") {
          let p: Record<string, unknown>;
          try { p = JSON.parse(rawBody); } catch { return json(res, 400, { error: "Invalid JSON" }); }
          if (!p.to || !p.subject) return json(res, 400, { error: "Missing fields: to, subject" });
          try {
            const info = await transporter.sendMail({
              from: `"${FROM_NAME}" <${FROM_EMAIL}>`,
              to: String(p.to), subject: String(p.subject),
              text: String(p.textContent ?? ""), html: String(p.htmlContent ?? ""),
            });
            console.info(`[mailer] ✓ Sent to ${p.to} — ${info.messageId}`);
            return json(res, 200, { messageId: info.messageId, accepted: info.accepted });
          } catch (e: any) {
            console.error("[mailer] ✗", e?.message);
            return json(res, 500, { error: e?.message ?? "SMTP failed" });
          }
        }

        // ── Auth ───────────────────────────────────────────────────────────
        if (url === "/mailer/auth/register" && method === "POST") {
          try { handleRegister(res, JSON.parse(rawBody)); } catch { json(res, 400, { message: "Invalid JSON" }); }
          return;
        }
        if (url === "/mailer/auth/login" && method === "POST") {
          try { handleLogin(res, JSON.parse(rawBody)); } catch { json(res, 400, { message: "Invalid JSON" }); }
          return;
        }
        if (url === "/mailer/auth/me" && method === "GET") { handleMe(res, req); return; }

        // ── Companies ──────────────────────────────────────────────────────
        if (url.startsWith("/mailer/companies")) {
          try { handleCompanies(method, url, res, req, rawBody); } catch (e: any) { json(res, 500, { message: e?.message }); }
          return;
        }

        next();
      });
    },
  };
}
