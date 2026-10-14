---
name: FreightLink response shapes
description: API response envelope shapes that differ from naive expectations.
---

## Rule
Several routes return wrapped objects, not raw arrays. Always wrap in the expected envelope.

**Why:** Frontend queries destructure `{ vehicles }`, `{ applications }`, `{ matches }`, `{ freight, total }`, etc.

## Shapes
- `GET /vehicles/my` → `{ vehicles: Vehicle[] }` (not a plain array)
- `GET /applications/my`, `GET /applications/freight/:id`, `GET /drivers/my-applications` → `{ applications: Application[] }`
- `GET /matching/freight/:id` → `{ matches: MatchResult[] }` where each match has `driverId`, `score` (0–1), `rating`, `totalDeliveries`
- `GET /admin/stats` → includes both flat fields (`totalUsers`, `totalDrivers`) AND nested (`users.total`, `drivers.active`, `freight.posted`, `freight.completed`) for compatibility
- `GET /freight` → `{ freight: FreightRequest[], total: number }`
- `GET /users` → `{ users: User[], total: number }`
