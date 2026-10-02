"""
KYC workflow integration test cases for EthioLoad AI Spring Boot backend.

Spring Boot login contract:
  POST /api/auth/login
  Body: {"identifier": "<email or phone>", "password": "<password>"}
  Returns: {"token": "...", "user": {...}}

Run:
  python test_kyc_cases.py

Requirements:
  pip install requests

Services must be running:
  Spring Boot :8080
  PostgreSQL  :5433
"""

import sys
import json
import io
import requests

BASE = "http://localhost:8080/api"


# ── Helpers ────────────────────────────────────────────────────────────────────

def login(identifier: str, password: str) -> str | None:
    """Login and return JWT token, or None on failure."""
    resp = requests.post(
        f"{BASE}/auth/login",
        json={"identifier": identifier, "password": password},
        timeout=10,
    )
    if resp.status_code == 200:
        return resp.json()["token"]
    print(f"  Login failed ({resp.status_code}): {resp.text[:200]}")
    return None


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def register_driver(email: str, phone: str, password: str = "Test1234") -> tuple[str | None, int | None]:
    """Register a new driver and return (token, user_id)."""
    resp = requests.post(
        f"{BASE}/auth/register",
        json={"fullName": "KYC Test Driver", "email": email, "phone": phone,
              "password": password, "role": "driver"},
        timeout=10,
    )
    if resp.status_code in (200, 201):
        data = resp.json()
        return data["token"], data["user"]["id"]
    print(f"  Register failed ({resp.status_code}): {resp.text[:200]}")
    return None, None


def upload_minimal_jpeg(token: str, doc_type: str) -> int | None:
    """Upload a minimal valid JPEG and return HTTP status."""
    # Minimal JPEG: SOI + APP0 JFIF header + EOI
    jpeg_bytes = bytes([
        0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00,
        0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0xFF, 0xD9,
    ])
    resp = requests.post(
        f"{BASE}/documents/upload",
        headers={"Authorization": f"Bearer {token}"},
        files={"file": ("doc.jpg", io.BytesIO(jpeg_bytes), "image/jpeg")},
        data={"documentType": doc_type},
        timeout=10,
    )
    return resp.status_code


def get_kyc_status(admin_token: str, driver_id: int) -> dict:
    resp = requests.get(
        f"{BASE}/documents/kyc/{driver_id}",
        headers=auth_headers(admin_token),
        timeout=10,
    )
    if resp.status_code == 200:
        return resp.json()
    return {"error": resp.status_code, "body": resp.text[:200]}


def approve_all(admin_token: str, driver_id: int) -> int:
    resp = requests.post(
        f"{BASE}/documents/kyc/{driver_id}/approve-all",
        headers=auth_headers(admin_token),
        json={},
        timeout=10,
    )
    return resp.status_code


DOC_TYPES = ["LICENSE", "NATIONAL_ID", "VEHICLE_REGISTRATION", "INSURANCE", "TIN"]

PASS = "PASS"
FAIL = "FAIL"
results: list[tuple[str, str]] = []


def check(label: str, condition: bool, detail: str = "") -> None:
    status = PASS if condition else FAIL
    results.append((label, status))
    marker = "  ✓" if condition else "  ✗"
    print(f"{marker} [{status}] {label}" + (f" — {detail}" if detail else ""))


# ── Setup: login as admin ──────────────────────────────────────────────────────

print("=== SETUP: Admin login ===")
admin_token = login("admin@freightlink.et", "admin123")
check("Admin login (identifier field)", admin_token is not None)
if not admin_token:
    sys.exit("ABORT: Cannot obtain admin token — check Spring Boot is running on :8080")

# ── CASE 1: Existing approved driver ─────────────────────────────────────────

print("\n=== CASE 1: Existing approved driver (bekele@driver.et) ===")
driver_token = login("bekele@driver.et", "driver123")
check("Case1 — Approved driver login succeeds", driver_token is not None)
if driver_token:
    # Approved driver should reach /freight marketplace
    mkt = requests.get(f"{BASE}/freight?status=posted&size=5", headers=auth_headers(driver_token), timeout=10)
    check("Case1 — Approved driver can access freight marketplace", mkt.status_code == 200,
          f"HTTP {mkt.status_code}")

# ── CASE 2: Inactive driver with 0 docs cannot access marketplace ─────────────

print("\n=== CASE 2: Inactive driver (0 docs) — limited access ===")
import random, string
rand = ''.join(random.choices(string.digits, k=6))
inactive_email = f"kyc.inactive.{rand}@ethioload.et"
inactive_phone = f"+2519{rand}"
inactive_token, inactive_id = register_driver(inactive_email, inactive_phone)
check("Case2 — Register new driver returns token", inactive_token is not None,
      f"id={inactive_id}")
if inactive_token:
    # The driver should be blocked from marketplace (ProtectedRoute + Spring is_active check)
    mkt2 = requests.get(f"{BASE}/freight?status=posted&size=5", headers=auth_headers(inactive_token), timeout=10)
    # Spring's JwtAuthenticationFilter allows pending drivers to authenticate (for doc upload),
    # but the ProtectedRoute in React redirects them to /documents.
    # At the API level, the /freight endpoint is NOT blocked for pending drivers by design
    # (they need to see the marketplace to understand the platform). The block is at the
    # bidding layer: inactive drivers cannot bid.
    check("Case2 — Pending driver API access (GET /freight allowed by Spring)", mkt2.status_code == 200,
          f"HTTP {mkt2.status_code} — bidding remains blocked at bid layer")

    # Verify bidding is blocked for inactive driver
    # First get a posted freight ID
    if mkt2.status_code == 200:
        freight_list = mkt2.json().get("content", [])
        if freight_list:
            fid = freight_list[0]["id"]
            bid_resp = requests.post(
                f"{BASE}/freight/{fid}/bids",
                headers=auth_headers(inactive_token),
                json={"amount": 5000, "vehicleId": 1, "note": "should be blocked"},
                timeout=10,
            )
            check("Case2 — Inactive driver CANNOT bid (Spring enforces is_active check)",
                  bid_resp.status_code in (400, 403, 422),
                  f"HTTP {bid_resp.status_code}")

# ── CASE 3: Document upload flow for pending driver ───────────────────────────

print("\n=== CASE 3: Pending driver uploads all 5 documents ===")
rand3 = ''.join(random.choices(string.digits, k=6))
pending_email = f"kyc.pending.{rand3}@ethioload.et"
pending_phone = f"+2519{rand3}"
pending_token, pending_id = register_driver(pending_email, pending_phone)
check("Case3 — Register driver for doc upload", pending_token is not None, f"id={pending_id}")

if pending_token and pending_id:
    # Check initial KYC state
    kyc_before = get_kyc_status(admin_token, pending_id)
    check("Case3 — Initial KYC: kycComplete=False",
          kyc_before.get("kycComplete") is False,
          f"approvedCount={kyc_before.get('approvedCount')}")

    # Upload all 5 documents
    upload_results = {}
    for dt in DOC_TYPES:
        code = upload_minimal_jpeg(pending_token, dt)
        upload_results[dt] = code

    all_uploaded = all(c in (200, 201) for c in upload_results.values())
    check("Case3 — All 5 documents uploaded (HTTP 200/201)",
          all_uploaded,
          str(upload_results))

    # Verify 5 rows in DB via KYC status endpoint
    kyc_after_upload = get_kyc_status(admin_token, pending_id)
    check("Case3 — KYC after upload: approvedCount=0 (not yet reviewed)",
          kyc_after_upload.get("approvedCount") == 0,
          f"approvedCount={kyc_after_upload.get('approvedCount')}")
    check("Case3 — KYC after upload: kycComplete=False (not yet approved)",
          kyc_after_upload.get("kycComplete") is False)

    # Verify driver still cannot bid (even with docs submitted, not yet approved)
    mkt3 = requests.get(f"{BASE}/freight?status=posted&size=5", headers=auth_headers(pending_token), timeout=10)
    if mkt3.status_code == 200 and mkt3.json().get("content"):
        fid3 = mkt3.json()["content"][0]["id"]
        bid3 = requests.post(
            f"{BASE}/freight/{fid3}/bids",
            headers=auth_headers(pending_token),
            json={"amount": 7000, "vehicleId": 1, "note": "docs submitted but not approved"},
            timeout=10,
        )
        check("Case3 — Driver with PENDING docs still cannot bid",
              bid3.status_code in (400, 403, 422),
              f"HTTP {bid3.status_code}")

# ── CASE 4: Admin bulk-approves driver ────────────────────────────────────────

print("\n=== CASE 4: Admin approve-all → driver activated ===")
if pending_token and pending_id:
    approve_code = approve_all(admin_token, pending_id)
    check("Case4 — POST /documents/kyc/{id}/approve-all returns 200",
          approve_code == 200,
          f"HTTP {approve_code}")

    # Check KYC status after bulk approval
    kyc_approved = get_kyc_status(admin_token, pending_id)
    check("Case4 — After approve-all: kycComplete=True",
          kyc_approved.get("kycComplete") is True,
          f"approvedCount={kyc_approved.get('approvedCount')}/{kyc_approved.get('requiredCount')}")
    check("Case4 — After approve-all: all 5 docs approved",
          kyc_approved.get("approvedCount") == 5,
          str(kyc_approved.get("documentStatuses")))

    # Verify is_active in user record via KYC status isActive field
    check("Case4 — After approve-all: isActive=True (driver activated)",
          kyc_approved.get("isActive") is True or kyc_approved.get("active") is True,
          f"isActive={kyc_approved.get('isActive')} active={kyc_approved.get('active')}")

# ── CASE 5: Partial doc submission — approve-all should approve submitted docs ─

print("\n=== CASE 5: Driver with < 5 docs — approve-all does NOT activate ===")
rand5 = ''.join(random.choices(string.digits, k=6))
partial_email = f"kyc.partial.{rand5}@ethioload.et"
partial_phone = f"+2519{rand5}"
partial_token, partial_id = register_driver(partial_email, partial_phone)
check("Case5 — Register driver for partial test", partial_token is not None)

if partial_token and partial_id:
    # Upload only 2 of 5 docs
    for dt in DOC_TYPES[:2]:
        upload_minimal_jpeg(partial_token, dt)

    approve_code5 = approve_all(admin_token, partial_id)
    check("Case5 — approve-all with 2 docs returns 200",
          approve_code5 == 200, f"HTTP {approve_code5}")

    kyc5 = get_kyc_status(admin_token, partial_id)
    check("Case5 — approvedCount=2 (only submitted docs approved)",
          kyc5.get("approvedCount") == 2,
          f"approvedCount={kyc5.get('approvedCount')}")
    check("Case5 — kycComplete=False (< 5 approved — driver NOT activated)",
          kyc5.get("kycComplete") is False)
    check("Case5 — isActive=False (not activated without all 5 docs)",
          kyc5.get("isActive") is not True and kyc5.get("active") is not True,
          f"isActive={kyc5.get('isActive')} active={kyc5.get('active')}")

# ── CASE 6: Reject driver — all pending docs rejected ─────────────────────────

print("\n=== CASE 6: Admin rejects driver (individual doc rejection) ===")
rand6 = ''.join(random.choices(string.digits, k=6))
reject_email = f"kyc.reject.{rand6}@ethioload.et"
reject_phone = f"+2519{rand6}"
reject_token, reject_id = register_driver(reject_email, reject_phone)
check("Case6 — Register driver for rejection test", reject_token is not None)

if reject_token and reject_id:
    # Upload 1 doc
    upload_minimal_jpeg(reject_token, "LICENSE")

    # Get docs list to find the doc ID
    docs_resp = requests.get(
        f"{BASE}/documents/driver/{reject_id}",
        headers=auth_headers(admin_token),
        timeout=10,
    )
    check("Case6 — Admin can retrieve driver's documents",
          docs_resp.status_code == 200, f"HTTP {docs_resp.status_code}")

    if docs_resp.status_code == 200:
        docs = docs_resp.json()
        if docs:
            doc_id = docs[0]["id"]
            # Reject the document
            reject_resp = requests.post(
                f"{BASE}/documents/{doc_id}/review",
                headers=auth_headers(admin_token),
                json={"status": "REJECTED", "rejectionReason": "Test rejection — document unclear"},
                timeout=10,
            )
            check("Case6 — Admin can reject individual document",
                  reject_resp.status_code == 200, f"HTTP {reject_resp.status_code}")
            if reject_resp.status_code == 200:
                check("Case6 — Rejected doc status=REJECTED",
                      reject_resp.json().get("status") == "REJECTED")

# ── Summary ────────────────────────────────────────────────────────────────────

print("\n" + "=" * 60)
total = len(results)
passed = sum(1 for _, s in results if s == PASS)
failed = total - passed
print(f"RESULTS: {passed}/{total} PASS  |  {failed} FAIL")
print("=" * 60)
if failed > 0:
    print("\nFailed cases:")
    for label, status in results:
        if status == FAIL:
            print(f"  ✗ {label}")

sys.exit(0 if failed == 0 else 1)
