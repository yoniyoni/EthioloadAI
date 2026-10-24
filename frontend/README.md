# feright

## Vercel-to-Render HTTP proxy

The Vercel Function in `api/[...path].js` forwards same-origin `/api/...` REST
requests to the fixed HTTPS origin in `SPRING_BACKEND_ORIGIN`. Configure
`SPRING_PROXY_SECRET` as a server-side Vercel environment variable and set the
same secret in the Render service; it must contain at least 32 UTF-8 bytes.
Never expose it through a `VITE_` variable. Keep the production frontend API
base same-origin (`/api`) so requests do not bypass the function.

The function requires Vercel's `X-Vercel-Id` marker and takes the client
address only from Vercel's `X-Vercel-Forwarded-For` header; ordinary
`X-Forwarded-For`, `Forwarded`, and `X-Real-IP` values are ignored and removed.
This depends on Vercel's documented edge behavior and has not been verified in
a deployed request. The Vercel Hobby WAF rate-limit pricing and
included allowance still require account-level confirmation before public
deployment.

Spring's production guard rejects REST requests without the shared secret.
Only `/api/actuator/health` and `/api/ws/**` bypass that guard: health is needed
for provider health checks, while WebSockets remain a separate direct Render
connection protected by the existing JWT handshake. This does not protect the
Render WebSocket origin with the proxy secret.

For a mobile release using the HTTP proxy, build with
`--dart-define=API_BASE_URL=https://<vercel-host>/api` and
`--dart-define=WEBSOCKET_BASE_URL=https://<render-host>/api`. Existing mobile
builds configured to call Render directly will be rejected by the production
REST guard and must be rebuilt. The React build similarly needs
`VITE_WEBSOCKET_BASE_URL=https://<render-host>/api` for its direct WebSocket
connection; WebSocket proxying is not implemented.