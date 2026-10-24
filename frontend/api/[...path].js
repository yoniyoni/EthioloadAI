const { isIP } = require("node:net");
const { Readable } = require("node:stream");

const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "host",
  "content-length",
  "forwarded",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-port",
  "x-forwarded-proto",
  "x-real-ip",
  "x-vercel-forwarded-for",
  "x-vercel-id",
  "x-ethioload-proxy-secret",
  "x-trusted-client-ip",
]);

const ALLOWED_METHODS = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]);

function getBackendOrigin(value) {
  if (!value) return null;

  try {
    const url = new URL(value);
    if (
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      url.pathname !== "/" ||
      url.search ||
      url.hash
    ) {
      return null;
    }
    return url.origin;
  } catch {
    return null;
  }
}

function getClientIp(headers) {
  if (typeof headers["x-vercel-id"] !== "string" || !headers["x-vercel-id"]) return null;
  const value = headers["x-vercel-forwarded-for"];
  if (typeof value !== "string" || value.includes(",")) return null;
  return isIP(value.trim()) ? value.trim() : null;
}

function getRequestHeaders(headers, connectionHeader) {
  const blocked = new Set(HOP_BY_HOP_HEADERS);
  for (const header of (connectionHeader || "").split(",")) {
    const name = header.trim().toLowerCase();
    if (name) blocked.add(name);
  }

  const forwarded = new Headers();
  for (const [name, value] of Object.entries(headers)) {
    if (!blocked.has(name.toLowerCase()) && value !== undefined) {
      forwarded.set(name, Array.isArray(value) ? value.join(", ") : value);
    }
  }
  return forwarded;
}

function setResponseHeaders(response, headers, backendOrigin) {
  const blocked = new Set(HOP_BY_HOP_HEADERS);
  for (const [name, value] of headers) {
    if (blocked.has(name.toLowerCase())) continue;
    if (name.toLowerCase() === "location") {
      try {
        const location = new URL(value, backendOrigin);
        if (location.origin === backendOrigin) {
          response.setHeader(name, `${location.pathname}${location.search}${location.hash}`);
          continue;
        }
      } catch {
        response.statusCode = 502;
        continue;
      }
    }
    response.setHeader(name, value);
  }
  if (typeof headers.getSetCookie === "function") {
    const cookies = headers.getSetCookie();
    if (cookies.length) response.setHeader("set-cookie", cookies);
  }
}

module.exports = async function proxy(request, response) {
  if (!ALLOWED_METHODS.has(request.method)) {
    response.statusCode = 405;
    response.setHeader("Allow", [...ALLOWED_METHODS].join(", "));
    response.end();
    return;
  }

  const backendOrigin = getBackendOrigin(process.env.SPRING_BACKEND_ORIGIN);
  const secret = process.env.SPRING_PROXY_SECRET;
  if (!backendOrigin || !secret || Buffer.byteLength(secret, "utf8") < 32) {
    response.statusCode = 503;
    response.end();
    return;
  }

  let requestUrl;
  try {
    requestUrl = new URL(request.url, "https://vercel.invalid");
  } catch {
    response.statusCode = 400;
    response.end();
    return;
  }

  if (
    !requestUrl.pathname.startsWith("/api/") ||
    requestUrl.pathname.startsWith("/api/api/")
  ) {
    response.statusCode = 400;
    response.end();
    return;
  }

  const headers = getRequestHeaders(
    request.headers,
    request.headers.connection,
  );
  const clientIp = getClientIp(request.headers);
  if (!clientIp) {
    response.statusCode = 400;
    response.end();
    return;
  }

  headers.set("x-ethioload-proxy-secret", secret);
  headers.set("x-trusted-client-ip", clientIp);

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  let upstream;
  try {
    upstream = await fetch(`${backendOrigin}${requestUrl.pathname}${requestUrl.search}`, {
      method: request.method,
      headers,
      ...(hasBody ? { body: request, duplex: "half" } : {}),
      redirect: "manual",
    });
  } catch {
    response.statusCode = 502;
    response.end();
    return;
  }

  response.statusCode = upstream.status;
  setResponseHeaders(response, upstream.headers, backendOrigin);
  if (!upstream.body || request.method === "HEAD") {
    response.end();
    return;
  }

  Readable.fromWeb(upstream.body).on("error", () => response.destroy()).pipe(response);
};
