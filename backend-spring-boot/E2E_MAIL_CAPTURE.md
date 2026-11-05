# Isolated E2E invitation mail capture

The `e2e` profile cannot use an external SMTP provider. Its fixed mail host is
`ethioloadai-e2e-mail-capture:12525`, SMTP authentication and TLS are disabled,
and the startup guard accepts capture mode only when the explicit
`e2e.mail.capture.enabled` opt-in is true and all mail settings match the
approved sink. The opt-in defaults to false. Normal E2E configuration still
requires blank SMTP credentials and a blank invitation sender. Capture mode
requires blank credentials and the fixed sender
`ethioloadai-test@example.test`.

The sink accepts only the exact synthetic recipient domain `example.test`.
It rejects SMTP AUTH and does not contain a forwarding SMTP client. The
container has no SMTP host port published and is attached only to the existing
internal E2E Docker network. SMTP listens on `0.0.0.0:12525` inside the
container. The authenticated capture API listens only on container loopback
`127.0.0.1:12526`; it has no published or exposed Docker port. The API reads
the key from the read-only `/run/secrets/test-mail-capture-key` secret. Store
the key as `test-mail-capture-key` in the protected host E2E secrets directory
and set `TEST_MAIL_CAPTURE_SECRET_FILE` for Compose. Host verification scripts
use a fixed, identity-checked `docker exec` command; the key is read inside the
container by `/app/capture-invitation-smtp-client.py` and is never passed
through host arguments or environment variables. The host calls Docker with
argument arrays (without shell interpolation), captures output in memory, and
sanitizes failures. Treat these scripts as privileged host operations because
Docker exec access can control the container. Do not mount the Docker socket
in the capture container. Never place the key in frontend source, browser
code, logs, or command output.

## E2E address and port ownership

The approved network observed during the configuration review is
`ethioloadai-stage3a-e2e-net-20261101`, ID
`a7321f4f4d63c0d57d279865ea2094b137237588291a53e024a5c323c977b344`,
subnet `172.26.0.0/16`, gateway `172.26.0.1`, and `Internal=true`. Its IPAM
configuration has no auxiliary addresses. The only active endpoints during
review were the database at `.2` and original backend at `.3`; a read-only
inspection of existing container endpoint records found no `.4` assignment.
The deployment preflight must repeat those checks and verify `.4` remains free.

| E2E address | Owner | Allocation |
| --- | --- | --- |
| `172.26.0.2` | PostgreSQL (`ethioloadai-e2e-db`) | Existing fixed endpoint |
| `172.26.0.3` | Spring backend | Fixed IP and MAC `82:c1:56:8b:59:3b` |
| `172.26.0.4` | SMTP capture | Explicit `ipv4_address` in Compose |
| Dynamic E2E address | Forwarding helper | Docker-assigned after backend starts |

Compose reserves capture at `.4`; it cannot dynamically take the backend's
`.3` endpoint. The helper is started only after the backend has claimed `.3`.
Before deployment, verify the subnet contains each address, no auxiliary
address reserves `.4`, no active or saved container endpoint uses `.4`, and
the database/backend endpoints are still exactly `.2`/`.3`.

| Host-side port | Owner | Binding |
| --- | --- | --- |
| `127.0.0.1:8080` | Host forwarding shim only | Loopback listener |
| Container `backend:8080` | Spring backend | Internal E2E network; no host `ports` mapping |
| `127.0.0.1:5173` | Vite, when separately running | Loopback only; proxies API requests to the shim |
| Container `mail-capture:12525` | SMTP sink | Internal E2E network; no host-published port |
| Container `127.0.0.1:12526` | Capture HTTP API | Capture-container loopback only; no published/exposed port |

The original backend retains its existing `127.0.0.1:8080` binding as a
rollback setting. Stop it and release its E2E endpoint before cutover; stop
the host shim before restoring the original container so that only one
process owns the host port at a time. Never add a Docker `ports` mapping for
the replacement backend or either support container.

## Host-local backend forwarding

The backend and Go forwarder are attached only to the existing internal E2E
network. Neither service publishes a host port, and the forwarder has no
secrets, volumes, or Docker socket. Its static Go binary accepts only `serve`,
`probe`, and `forward` modes; forwarding always dials the compile-time
constant `172.26.0.3:8080`. The Dockerfile has no destination build argument
or linker override. It relays raw TCP bytes bidirectionally and propagates a
client half-close to the backend.

The host-side `backend-spring-boot/scripts/e2e-host-forwarder.mjs` process is
the only host-facing component. It binds only to `127.0.0.1:8080`, admits at
most 16 simultaneous connections, and closes idle connections after 60
seconds. For each connection it checks the exact helper container ID, Compose
project/service/one-off labels, exact helper name, that both containers have
only the configured network, that the network is internal, and that the
backend is the expected Compose service at `172.26.0.3`. It then runs only
`docker exec --interactive <verified-id> /e2e-tcp-forwarder forward` using an
argument array. It has no client input for Docker commands, container names,
or forwarding destinations. Docker exec is privileged host access: keep this
process local to the test operator, do not expose it on non-loopback
interfaces, and never mount the Docker socket in a container.

Before binding, and again before each forwarded connection, the shim checks
the approved E2E network ID
`a7321f4f4d63c0d57d279865ea2094b137237588291a53e024a5c323c977b344` and
`Internal=true`; the expected database container ID/name, running state,
network attachment, `172.26.0.2` address, and `ethioloadai-e2e-db` alias; the
backend's E2E-only membership, `172.26.0.3` address, and MAC
`82:c1:56:8b:59:3b`; and the helper's exact ID/name/Compose labels and E2E-only
membership. It also rejects published ports on the backend or helper. Any
mismatch or Docker inspection error prevents the listener from starting or
causes the incoming connection to be closed. The approved database container
ID is `bacc65efd564d7f248056ae29417ae48bbfbe9f68d441576c7abb65ac4f22583`.

On Ctrl+C or SIGTERM, the shim stops accepting connections, terminates each
Docker exec child, waits for each child's `close` event (escalating after five
seconds if needed), and waits for active sockets to close before exiting.

The forwarder container is limited to 128 MiB and 256 PIDs; the host shim
limits active connections to 16. A sustained disposable test with 16
connections across three rounds peaked at 18.86 MiB and 88 PIDs in the
helper. After client disconnects and shutdown, helper usage returned to
2.43 MiB and 7 PIDs (the `serve` process only); graceful shim shutdown also
waited for all 16 Docker exec children to close. An earlier sample showed
18.69 MiB and 91 PIDs, and the host Node process used about 36.66 MiB RSS in
that sample. The 256-PID cap leaves headroom over both helper samples; the
concurrency cap remains 16. An earlier 64-PID cap prevented some concurrent
Docker exec processes from starting. These are observed test-environment
measurements, not guaranteed production capacity; monitor both limits under
the expected browser workload.

After the backend and forwarder are healthy and their exact runtime identities
and network endpoints pass preflight, start the host shim in a
dedicated terminal, using the same Compose project and verified E2E network:

```powershell
$helperId = docker compose -p ethioloadai-e2e-mail-capture `
  -f docker/docker-compose.e2e-mail-capture.yml ps -q forwarder
node backend-spring-boot/scripts/e2e-host-forwarder.mjs `
  --project ethioloadai-e2e-mail-capture `
  --network ethioloadai-stage3a-e2e-net-20261101 `
  --helper-id $helperId
```

Keep the terminal open for the browser test; press Ctrl+C to stop the shim.
Verify the listener is `127.0.0.1:8080`, not `0.0.0.0:8080`. Vite continues
to proxy `/api` and `/auth` to `http://127.0.0.1:8080`.

## Required network arrangement

The deployment file `docker/docker-compose.e2e-mail-capture.yml` describes the
mail sink and an E2E backend. It uses an already existing external network; it
does not create or mark that network internal. Before any separately approved
deployment, inspect the network and require:

- The configured `E2E_NETWORK_NAME` identifies the approved E2E network.
- Docker reports `Internal=true`.
- The existing database is attached to that network under the DNS alias
  `ethioloadai-e2e-db`.
- The address plan is database `.2`, backend `.3`, and capture `.4`; `.4` is
  valid and free at preflight, and no auxiliary address reserves it.
- The backend, capture sink, and forwarding helper attach only to that E2E
  network. Do not attach any of them to the application network.
- Compose explicitly pins the capture service to `172.26.0.4`; it must never
  acquire `.3` dynamically. The backend remains pinned to `.3`, and the
  forwarding helper starts only after the backend.
- The backend endpoint is `172.26.0.3` with MAC `82:c1:56:8b:59:3b`.
- Do not publish backend or helper ports from Docker. No Compose service
  owns host port 8080; only the host shim binds `127.0.0.1:8080`. Vite's
  existing API proxy targets that loopback listener.
- The capture service has no host-published ports, exposes only SMTP port
  12525 to the E2E network, and binds its authenticated HTTP API to container
  loopback only. Its capture-key secret mount is read-only.
- The forwarding helper has no host-published ports, secrets, volumes, Docker
  socket, or network attachment other than the internal E2E network.

The compose file requires an immutable/verified backend image reference,
distinct E2E runtime and migration database users, and protected paths for the
JWT, database, migration, and capture-key files. It does not configure or mount
Brevo credentials. Database identity, network internal status, secret ACLs,
port availability, and backend image digest must be checked before using it.
Do not run Compose as part of source verification or without separate
deployment authorization.
The database container was observed running, with no Docker healthcheck
configured. Do not equate its running state with database readiness; use the
approved read-only database readiness check during deployment preflight.

## Authorized deployment procedure

Do not run this procedure until deployment is separately authorized. The
currently observed network is `ethioloadai-stage3a-e2e-net-20261101`
(`a7321f4f4d63c0d57d279865ea2094b137237588291a53e024a5c323c977b344`), with
`Internal=true`, subnet `172.26.0.0/16`, and the database at `172.26.0.2`
under alias `ethioloadai-e2e-db` (container
`ethioloadai-stage3a-e2e-db-20261101`,
`bacc65efd564d7f248056ae29417ae48bbfbe9f68d441576c7abb65ac4f22583`).
Recheck those facts immediately before a deployment.

The original backend currently has these endpoints:

- E2E: `172.26.0.3`, MAC `82:c1:56:8b:59:3b`.
- Application network `ethioloadAI-stage3j-app-net-20261101`
  (`dd1245bc152b971e69eaa3e5d3dcf9d63980ed7e943bde3a16605fffeed69718`):
  `172.27.0.2`, MAC `ba:74:2f:8c:02:d2`.

Before cutover, save the original backend container/image identity, endpoint
settings, mounts, environment, and port binding. Do not print secret values.
Leave all existing secrets mounted on the original container for rollback.
Do not mount or use `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`,
`SMTP_SECURE`, `SMTP_STARTTLS`, `EMAIL_FROM`, or any Brevo/provider SMTP
credentials in the capture deployment.

1. Verify the Docker Engine API is at least v1.54; network ID, subnet,
   gateway, `Internal=true`, IPAM auxiliary addresses, active endpoints, and
   saved static assignments; database container ID, readiness and
   `ethioloadai-e2e-db` alias; approved backend image digest; protected
   secret files; and availability of host ports 8080 and 5173. Confirm `.4`
   is valid and unassigned. Check Compose syntax using the exact deployment
   environment. Require the Compose capture address `.4`, backend address
   `.3`, database address `.2`, no backend/support `ports` mappings, and
   unchanged secret/capture restrictions.
2. Save the original backend container ID and complete endpoint/mount/port
   configuration. Stop only `ethioloadai-stage3l-spring-e2e`. Leave its
   application-network endpoint attached and unchanged. Disconnect only its
   E2E endpoint, because the replacement must claim the same `172.26.0.3`
   address and MAC:

   ```powershell
   docker stop ethioloadai-stage3l-spring-e2e
   docker network disconnect ethioloadai-stage3a-e2e-net-20261101 ethioloadai-stage3l-spring-e2e
   ```

3. Create, but do not start, the Compose containers under a unique project
   name. The required image, secret paths, and network name must already be
   set to the verified values in the local deployment environment:

   ```powershell
   docker compose -p ethioloadai-e2e-mail-capture -f docker/docker-compose.e2e-mail-capture.yml create mail-capture backend forwarder
   ```

   Inspect the stopped replacement and require exactly one network (the
   approved E2E network), `172.26.0.3`, MAC `82:c1:56:8b:59:3b`, and no
   published port. Require the capture service to have exactly one network,
   explicit IP `172.26.0.4`, no published ports, SMTP exposure only on the
   E2E network, and API bind address `127.0.0.1:12526` inside the container.
   Confirm `/run/secrets/test-mail-capture-key` is mounted read-only. Require the
   forwarder to have exactly one network, no published ports, secrets, or
   mounts, and the Compose project/service labels expected by the host shim.
   The database
   alias stays on its existing endpoint; do not recreate or edit that
   endpoint.
4. Start the capture service and wait until healthy at `.4`; then start the
   replacement backend at `.3` and verify its IP/MAC. Start the forwarder only
   after the backend owns `.3`. Start each container exactly once, only after
   the stopped replacement's image, endpoints, secrets, and network
   configuration have passed inspection:

   ```powershell
   docker compose -p ethioloadai-e2e-mail-capture -f docker/docker-compose.e2e-mail-capture.yml start mail-capture
   docker compose -p ethioloadai-e2e-mail-capture -f docker/docker-compose.e2e-mail-capture.yml start backend
   docker compose -p ethioloadai-e2e-mail-capture -f docker/docker-compose.e2e-mail-capture.yml start forwarder
   ```

   Verify the capture address remains `.4`, the backend and helper remain
   attached only to E2E, startup health and Flyway state, and helper health.
   The backend has no host-published port; only after those checks start the
   host shim as documented above. It must verify the exact approved network
   and database identities, database alias, backend IP/MAC, helper
   ID/name/labels, and network membership before binding to loopback. If any
   check fails, stop and roll
   back; do not bypass the guard. Then verify the loopback-only listener, host
   access, and Vite proxy to the backend. The capture healthcheck must
   authenticate to `/cursor` over container loopback; verify host retrieval
   only through the supplied Docker exec helper. Vite's `dev` script
   now binds to `127.0.0.1`, restricts Host headers to `localhost` and
   `127.0.0.1`, preserves port 5173, and keeps the existing `/api` and `/auth`
   proxy to `127.0.0.1:8080`. Flutter's host-loopback route is unchanged. The
   Docker and Vite configs establish intended bindings, but only live host
   verification proves reachability.
5. In the browser, log in as the Fleet Owner, create a new invitation to a
   synthetic `@example.test` recipient, and confirm the success state. Record
   Use the approved host verification script to save the capture cursor before
   sending. It retrieves through `docker exec`, matches the exact synthetic
   envelope recipient and cursor, and requires exactly one new message. Verify
   its recipient, company, role, expiry, and acceptance URL in memory. Follow
   the acceptance URL without writing its token into a fixture or log. Do not
   use real recipients or send any external mail.

### Rollback

First stop the host shim with Ctrl+C and confirm port 8080 is released. Then
stop and remove only the replacement backend, helper, and capture containers:

```powershell
docker compose -p ethioloadai-e2e-mail-capture -f docker/docker-compose.e2e-mail-capture.yml rm --stop --force forwarder backend mail-capture
```

Verify the replacement endpoints and host-port reservations have been released. Leave
the existing database, its alias, networks, volumes, and unrelated containers
untouched. The original backend's application-network endpoint should still
be attached with its original configuration. Reattach the original backend to
the E2E network using a verified Docker Engine API procedure (API v1.54 or
later), not `docker network connect --mac-address`. The connect request must
identify the original container and include an endpoint config with
`IPAMConfig.IPv4Address` set to `172.26.0.3` and `MacAddress` set to
`82:c1:56:8b:59:3b`. Inspect and confirm the endpoint configuration before
restarting the original backend. Restore its saved image/configuration,
environment, read-only secret mounts, and complete saved port configuration,
including the original `127.0.0.1:8080` to container `8080/tcp` binding.
Then verify both original endpoints (E2E `.3` / MAC
`82:c1:56:8b:59:3b`; application `172.27.0.2` / MAC
`ba:74:2f:8c:02:d2`), the loopback host binding, and backend health. The
replacement must never publish host port 8080, so restoring the original
binding cannot conflict once the shim and replacement are stopped.

If the exact original E2E endpoint cannot be restored and verified, do not
report rollback as successful.

The browser does not need a Docker-network route. The E2E profile uses local
upload storage and disables AI and external routing integrations; verify those
assumptions if new E2E service dependencies are added.

## Message retrieval

The authenticated capture API is available only on container loopback:

- `GET http://127.0.0.1:12526/cursor` — current numeric cursor.
- `GET http://127.0.0.1:12526/?after=<cursor>` — captured messages newer than
  the cursor, including unique message IDs, numeric cursors, envelope
  recipients, headers, text/HTML bodies, and extracted test invitation token.

Both require the `X-Test-Key` header. Host scripts call these routes using a
fixed `docker exec ethioloadai-e2e-mail-capture ...` operation. It verifies
that the exact named Compose service is running, reads the key inside the
container, and returns only the API response to the calling process. It does
not invoke a shell or put the key in Docker arguments/environment. The API
port is not published or exposed to other containers, and the capture
container does not receive Docker control access.

Verification scripts save the numeric cursor before each invitation or
resend, then require exactly one message matching the expected envelope
recipient and cursor. This prevents stale or ambiguous invitations from
supplying a token. Captured invitation bodies and tokens are sensitive even
though they are synthetic; keep them only in process memory and do not print,
log, hash, or persist the raw token. The SMTP sink itself remains restricted
to `example.test`, rejects AUTH, and has no forwarding implementation.

## Verification expectations

Before any authorized deployment, test the Python sink independently on
loopback: accepted `@example.test` recipients are captured, external recipient
domains and AUTH are rejected, message IDs are unique, cursors exclude earlier
messages, and invalid API keys receive 403. Backend unit tests cover the
default-off guard, exact sink configuration, SMTP template contents, and
sanitized send failures. Transaction tests verify that successful email
service completion commits the invitation and that send failure rolls back,
including preserving the previous pending token on failed resend. These
transaction tests mock the email-service boundary; the separate Python tests
exercise the capture sink protocol. A live backend-to-sink-to-database
integration test remains part of deployment verification.

Browser verification should use a new synthetic `@example.test` recipient.
Send an invitation through the Fleet Owner UI, verify the success state and
the new captured message by cursor, follow its acceptance link, and confirm the
resulting company membership and role. Do not use real email addresses. The
capture feature does not alter ADMIN bootstrap, Fleet Owner authorization, or
datasource selection; their existing tests remain required.
