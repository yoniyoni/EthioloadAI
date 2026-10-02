/** All clients authenticate and operate against the same Spring Boot API. */
const AUTH_STORAGE_KEY = "freightlink_auth";
export const API_BASE = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/+$/, "");

export function getToken(): string | null {
  try { return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) || "null")?.token ?? null; }
  catch { return null; }
}
export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}
async function request(path: string, options: RequestInit = {}): Promise<Response> {
  const publicRequest = ["/auth/login", "/auth/register", "/company-invitations/validate", "/company-invitations/register"].includes(path);
  const token = publicRequest ? null : getToken();
  const headers = new Headers(options.headers);
  if (!(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let response: Response;
  try { response = await fetch(`${API_BASE}${path}`, { ...options, headers }); }
  catch { throw new ApiError("Unable to connect to the server. Please try again.", 0); }
  if (!response.ok) {
    let message = response.status >= 500 ? "The server is unavailable. Please try again." : `Request failed (${response.status}).`;
    try { const data = await response.json(); if (response.status < 500 && typeof data.message === "string") message = data.message; } catch { /* Non-JSON proxy error. */ }
    // Invalid credentials stay on the login form. This API has no refresh endpoint.
    if (response.status === 401 && !publicRequest && token && token === getToken()) window.dispatchEvent(new CustomEvent("freightlink:unauthorized"));
    throw new ApiError(message, response.status);
  }
  return response;
}
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await request(path, options);
  const text = await response.text();
  return text ? JSON.parse(text) as T : undefined as T;
}
export const api = {
  get: <T>(path: string) => apiFetch<T>(path),
  post: <T>(path: string, body: unknown = {}) => apiFetch<T>(path, { method: "POST", body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) => apiFetch<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => apiFetch<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  del: <T>(path: string) => apiFetch<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, body: FormData) => apiFetch<T>(path, { method: "POST", body }),
  getBlob: async (path: string): Promise<Blob> => {
    const base = new URL(`${API_BASE}/`, window.location.origin);
    const target = new URL(path, base.origin);
    if (target.origin !== base.origin) throw new Error("Invalid download address.");
    const relative = target.pathname.startsWith(base.pathname) ? target.pathname.slice(base.pathname.length - 1) : target.pathname;
    return (await request(relative + target.search)).blob();
  },
};
export const auth = api;
