---
name: FreightLink route conventions
description: Backend route paths that differ from OpenAPI spec; public route auth handling.
---

## Rule
`GET /freight` and `GET /freight/:id` must use `optionalAuthenticate` (not `authenticate`) so unauthenticated users can browse the freight exchange.

**Why:** The landing page and freight list are public-facing; requiring auth breaks the browse flow.

**How to apply:** Import `optionalAuthenticate` from `authenticate.ts` and use it on all public GET routes.

## Frontend ↔ Backend path mapping
- Frontend: `GET /matching/freight/:id` → Backend: route alias added in matching.ts (also has `/freight/:id/matches` for authenticated use)
- Frontend: `GET /matching/price-prediction/:id` → Backend: route in matching.ts, derives price from freight record
- Frontend: `GET /applications/my` → Backend: alias added in applications.ts (also `/drivers/my-applications`)
- Frontend: `GET /applications/freight/:id` → Backend: alias added in applications.ts (also `/freight/:id/applications`)
- Frontend: `PATCH /applications/:id/accept` → Backend: alias added in applications.ts
- Frontend: `POST /applications` with `{freightId, proposedPrice, message}` → Backend: alias added (original was `POST /freight/:id/apply`)
- Frontend: `PATCH /users/:id` → Backend: added in users.ts (original only had `/users/me`)
- Frontend: `GET /users?limit=N` → Backend: added in users.ts (original was `/admin/users`)
