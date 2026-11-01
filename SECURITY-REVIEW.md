# Milestone 4 Backend Security Review

Read-only review of the company-scoped booking, trip, dispatch, freight, and offer behavior. No tests, builds, database operations, migrations, Spring Boot, HTTP requests, or services were run.

## Findings

| # | Severity | File | Lines | Vulnerability | Confidence |
|---|----------|------|-------|---------------|------------|
| 1 | 🟡 MEDIUM | `FreightServiceImpl.java` | 353 | Dispatcher can read awarded freight's agreed price through the generic freight detail route. | 9/10 |
| 2 | 🟡 MEDIUM | `DispatchServiceImpl.java` | 143 | Idempotent dispatch-create retry returns the agreed amount without applying the caller's role-based redaction. | 9/10 |

### 1. Dispatcher financial visibility through freight detail

- **Location:** `FreightServiceImpl.getFreightView`, line 353.
- **Behavior:** An active member of the awarded company passes `isActiveCompanyMember`, which includes dispatchers, then the service calls `FreightMapper.toOperationalResponse(freight, award, activeDispatch)`. That overload defaults financial inclusion to `true`.
- **Scenario:** A dispatcher requests `GET /freight/{id}` for freight awarded to their company and receives the agreed price, despite other company-facing projections hiding financial fields from dispatchers.
- **Policy:** Violates the approved rule limiting financial information to company owners and fleet managers.
- **Smallest correction:** Pass `hasFinancialCompanyRole(award.getCarrierCompany().getId(), caller)` to the financial-aware mapper overload.

### 2. Dispatcher financial visibility on dispatch retry

- **Location:** `DispatchServiceImpl.createDispatch`, line 143.
- **Behavior:** When an active dispatch matches the request, the idempotent retry returns `DispatchAssignmentResponse.from(existing)`. That factory includes agreed amount unconditionally. Fresh create and cancellation paths use a role-aware company projection.
- **Scenario:** A dispatcher repeats a matching create request and receives the dispatch's agreed amount through the retry response.
- **Policy:** Violates the approved rule limiting financial information to owners and fleet managers.
- **Smallest correction:** Use `DispatchAssignmentResponse.fromCompany(existing, canViewFinancials(ctx.role()))`.

## Policy requirements demonstrably satisfied

- New company booking and trip list/detail services resolve an active membership against the requested company, and their specifications/detail lookups constrain the record to that company.
- Booking and trip reads apply the configured carrier history window. Trip list status/type filters and validated sort fields are applied server-side; page sizes are capped.
- Dispatch create/cancel controller checks require an operational role and approved company. The service repeats role and approval checks. Creation verifies the award belongs to the path company and checks driver/vehicle company ownership; cancellation loads by both dispatch and company.
- Company booking/trip projections redact financial values for dispatchers, and company trip projection removes driver and shipper phone numbers.
- Dispatcher offer response/history amounts and awarded-freight summary amounts are redacted in their company-scoped service paths. Existing shipper/admin projections retain their existing information.

## Compatibility risks and ambiguities

- Company booking/trip and operational dispatch responses expose names, driver identifiers, and vehicle plates to active company members. The approved policy explicitly prohibits phone numbers and unnecessary personal data for dispatchers, but does not define whether these identifiers/names/plates are allowed. Confirm intended dispatcher detail before expanding frontend display.
- Offer browsing can return competitor summary amounts to owners/fleet managers. The policy grants those roles financial visibility but does not specify whether that extends to competing companies' offer values. Confirm this is intended.
- Dispatcher offer negotiation remains permitted while monetary response and history values are redacted. This prevents reading the returned amount directly, but the dispatcher must still choose and submit a counter-offer amount. That is not itself a response-data leak; whether dispatchers should hold negotiation authority is a business-policy ambiguity, since the approved policy does not prohibit it.
- Generic freight projections and shared DTOs are role-dependent; existing driver/shipper paths generally retain their prior projection. The two findings above are exceptions to the dispatcher financial redaction.
- Company-scoped booking/trip UI integration remains unverified. No frontend endpoints or workflows were reviewed in this backend-only review, and no tests/builds were run.

## Frontend integration readiness

**Not ready for financial-sensitive frontend integration until findings 1 and 2 are corrected and covered by tests.** Other company-scoped operational views may be integrated only with the known role-specific projection behavior and the noted unresolved visibility decisions.
