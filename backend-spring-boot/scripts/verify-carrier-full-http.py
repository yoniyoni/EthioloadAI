"""Real loopback HTTP + PostgreSQL assertions. Run only from the isolated verifier.

No responses, passwords, mail bodies, or invitation tokens are printed.
"""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import urllib.error
import urllib.request

BASE = 'http://127.0.0.1:' + os.environ['VERIFICATION_PORT'] + '/api'
DB = os.environ['SPRING_DATASOURCE_URL'].split('/')[-1]
assert re.fullmatch(r'carrier_phase2_[a-f0-9]{16}', DB), 'Requires isolated verification database'
counts = {'http': 0, 'database': 0, 'assertions': 0}
password = secrets.token_urlsafe(30)
phone_sequence = 10
tokens = []

def check(condition, label):
    if not condition:
        raise AssertionError(label)
    counts['assertions'] += 1

def sql(query):
    result = subprocess.run([os.environ['VERIFICATION_PSQL'], '-X', '-w', '-h', os.environ['VERIFICATION_PGHOST'],
        '-p', os.environ['VERIFICATION_PGPORT'], '-U', os.environ['SPRING_DATASOURCE_USERNAME'], '-d', DB,
        '-v', 'ON_ERROR_STOP=1', '-At', '-c', query], capture_output=True, text=True)
    check(result.returncode == 0, 'SQL execution failed')
    counts['database'] += 1
    return result.stdout.strip()

def api(method, path, body=None, auth=None, expected=(200, 201, 204), content_type='application/json'):
    headers = {'Content-Type': content_type}
    if auth:
        headers['Authorization'] = 'Bearer ' + auth
    data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        if content_type == 'download' and response.status == 200:
            check(response.headers.get('Cache-Control') == 'no-store', 'Private document cache policy')
            check(response.headers.get('X-Content-Type-Options') == 'nosniff', 'Private document MIME policy')
            check('attachment' in response.headers.get('Content-Disposition', ''), 'Private document disposition')
        status, payload = response.status, response.read()
    counts['http'] += 1
    check(status in expected, f'{method} {path.split("?")[0]} returned {status}, expected {expected}')
    if content_type == 'download':
        return payload
    return json.loads(payload) if payload else None

def register(name):
    global phone_sequence
    phone_sequence += 1
    return api('POST', '/auth/register', {'fullName': name, 'email': name + '@example.test',
        'phone': '+25193300' + str(phone_sequence).zfill(4), 'password': password, 'role': 'shipper'})['token']

def state(table, ident, column, expected):
    check(sql(f'SELECT {column} FROM {table} WHERE id={int(ident)}') == expected, table + ' persisted ' + column)

def company(name, auth, number, decision='approve'):
    body = {'companyName': name, 'tinNumber': 'FULL-TIN-' + str(number),
        'businessRegistrationNumber': 'FULL-REG-' + str(number), 'phone': '+251911000123',
        'email': 'office@example.test', 'businessAddress': 'Bole Road', 'city': 'Addis Ababa'}
    result = api('POST', '/carrier-companies', body, auth)
    ident = result['id']; path = '/carrier-companies/' + str(ident)
    check(result['verificationStatus'] == 'DRAFT', 'Initial DRAFT')
    api('POST', '/admin' + path + '/approve', auth=admin, expected=(400,))
    body['city'] = 'Adama'
    api('PUT', path, body, auth)
    state('carrier_companies', ident, 'city', 'Adama')
    document_ids = []
    for kind in ['BUSINESS_LICENSE', 'BUSINESS_REGISTRATION', 'TIN_CERTIFICATE']:
        boundary = 'fullverificationboundary'
        data = ('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="../../test.pdf"\r\n'
            'Content-Type: application/pdf\r\n\r\n%PDF-1.4 full verification\r\n--' + boundary + '--\r\n').encode()
        doc = api('POST', path + '/documents?documentType=' + kind, data, auth,
                  content_type='multipart/form-data; boundary=' + boundary)
        check('storageKey' not in doc and doc['originalName'] == 'document.pdf', 'Private safe file metadata')
        document_ids.append(doc['id'])
    api('POST', path + '/submit', auth=auth)
    state('carrier_companies', ident, 'verification_status', 'PENDING')
    api('PUT', path, body, auth, expected=(400,))
    api('POST', '/admin' + path + '/approve', auth=auth, expected=(403,))
    pending = api('GET', '/admin/carrier-companies?status=PENDING', auth=admin)
    check(any(item['id'] == ident for item in pending['content']), 'Admin pending list')
    api('GET', '/admin' + path, auth=admin)
    api('GET', path + '/documents', auth=admin)
    file_path = path + '/documents/' + str(document_ids[0]) + '/file'
    for caller in (auth, admin):
        check(api('GET', file_path, auth=caller, content_type='download') == b'%PDF-1.4 full verification', 'Exact document bytes')
    if decision == 'approve':
        api('POST', '/admin' + path + '/request-information', {'message': 'Confirm address'}, admin)
        read = api('GET', path, auth=auth)
        check(read['reviewMessage'] == 'Confirm address', 'Owner sees information request')
        api('PUT', path, body, auth)
        api('POST', path + '/submit', auth=auth)
    api('POST', '/admin' + path + '/' + decision, {'message': 'Reviewed'}, admin)
    state('carrier_companies', ident, 'verification_status', 'APPROVED' if decision == 'approve' else 'REJECTED')
    api('POST', path + '/submit', auth=auth, expected=(400,))
    api('PUT', path, body, auth, expected=(400,))
    return ident, document_ids[0], body

def capture():
    req = urllib.request.Request('http://127.0.0.1:12526/', headers={'X-Test-Key': os.environ['TEST_MAIL_CAPTURE_SECRET']})
    with urllib.request.urlopen(req, timeout=10) as response:
        return json.load(response)['latest']

def invite(cid, owner, email, role='FLEET_MANAGER'):
    result = api('POST', f'/carrier-companies/{cid}/team/invitations', {'email': email, 'role': role}, owner)
    mail = capture(); token = mail['token']; tokens.append(token)
    check(mail['recipient'] == email.strip().lower(), 'SMTP recipient')
    for part in ['Freight-Link', role.replace('_', ' '), 'Expires:', 'ignore this email', '/invitations/accept#token=']:
        check(part in mail['text'], 'SMTP content ' + part)
    check('Accept Invitation' in mail['html'], 'SMTP HTML link')
    check(mail['subject'].startswith("You're invited to join "), 'Professional subject')
    state('company_invitations', result['id'], 'token_hash', hashlib.sha256(token.encode()).hexdigest())
    state('company_invitations', result['id'], 'status', 'PENDING')
    state('company_invitations', result['id'], 'invited_role', role)
    state('company_invitations', result['id'], 'carrier_company_id', str(cid))
    check(sql(f"SELECT invited_by=(SELECT owner_id FROM carrier_companies WHERE id={cid}) AND expires_at>created_at FROM company_invitations WHERE id={result['id']}") == 't', 'Invitation actor and expiry')
    check(token not in json.dumps(result), 'Raw token absent from response')
    return result['id'], token

def validate(token, status=200):
    return api('POST', '/company-invitations/validate', {'token': token}, expected=(status,))

def accept(token, auth, expected=(200, 201)):
    return api('POST', '/company-invitations/accept', {'token': token, 'role': 'CARRIER_OWNER',
        'carrierCompanyId': 999999, 'email': 'attacker@example.test'}, auth, expected=expected)

try:
    api('POST', '/carrier-companies', {'companyName': 'Unauthorized'}, expected=(401,))
    owner_a, owner_b = register('full-owner-a'), register('full-owner-b')
    register('full-admin')
    sql("UPDATE users SET role='admin' WHERE email='full-admin@example.test'")
    admin = api('POST', '/auth/login', {'identifier': 'full-admin@example.test', 'password': password})['token']
    admin_company = api('POST', '/carrier-companies', {'companyName': 'Admin owned'}, admin)
    api('POST', f'/admin/carrier-companies/{admin_company["id"]}/approve', auth=admin, expected=(403,))
    a, adoc, abody = company('ABC Logistics', owner_a, 1)
    b, bdoc, bbody = company('XYZ Transport', owner_b, 2)
    company('Rejected Transport', owner_a, 3, 'reject')
    for field in ['tinNumber', 'businessRegistrationNumber']:
        api('POST', '/carrier-companies', {'companyName': 'Duplicate', field: abody[field]}, owner_a, expected=(409,))
    api('GET', f'/carrier-companies/{a}/documents/{bdoc}/file', auth=owner_a, expected=(404,))
    for cid, doc, outsider, body in [(a, adoc, owner_b, abody), (b, bdoc, owner_a, bbody)]:
        path = f'/carrier-companies/{cid}'
        for method, suffix, payload, expected in [('GET', '', None, 404), ('PUT', '', body, 404),
                ('GET', '/documents', None, 404), ('GET', f'/documents/{doc}/file', None, 404),
                ('GET', '/team/members', None, 403), ('GET', '/team/invitations', None, 403)]:
            api(method, path + suffix, payload, outsider, expected=(expected,))
    members = api('GET', f'/carrier-companies/{a}/team/members', auth=owner_a)['content']
    check(len(members) == 1 and members[0]['role'] == 'CARRIER_OWNER', 'Exactly one owner membership')
    owner_member = members[0]['id']
    for method, suffix, payload in [('DELETE', '', None), ('POST', '/deactivate', None), ('PATCH', '/role', {'role': 'DISPATCHER'})]:
        api(method, f'/carrier-companies/{a}/team/members/{owner_member}' + suffix, payload, owner_a, expected=(400,))
    existing = register('full-existing')
    invitation, raw = invite(a, owner_a, 'FULL-EXISTING@example.test')
    check(validate(raw)['companyName'] == 'ABC Logistics', 'Correct invitation preview company')
    validate(secrets.token_urlsafe(32), 400)
    validate(('A' if raw[0] != 'A' else 'B') + raw[1:], 400)
    accept(raw, owner_b, (403,))
    joined = accept(raw, existing)
    check(joined['role'] == 'FLEET_MANAGER' and joined['carrierCompanyId'] == a, 'Acceptance ignores role/company/email tampering')
    state('company_invitations', invitation, 'status', 'ACCEPTED')
    accept(raw, existing, (400,)); validate(raw, 400)
    new_inv, new_raw = invite(b, owner_b, 'full-new@example.test', 'DISPATCHER')
    validate(new_raw)
    new_user = register('full-new')
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: accept(new_raw, new_user, (200, 201, 400)), range(2)))
    check(sum(result.get('role') == 'DISPATCHER' for result in results) == 1, 'Concurrent acceptance has exactly one winner')
    bmember = next(result['id'] for result in results if result.get('role') == 'DISPATCHER')
    state('company_invitations', new_inv, 'status', 'ACCEPTED')
    for cid, own, other, member in [(a, owner_a, owner_b, joined['id']), (b, owner_b, owner_a, bmember)]:
        team = f'/carrier-companies/{cid}/team'
        inv, token = invite(cid, own, f'pending-{cid}@example.test', 'DISPATCHER')
        for method, suffix, body in [('POST', f'/invitations/{inv}/revoke', None), ('POST', f'/invitations/{inv}/resend', None),
            ('PATCH', f'/members/{member}/role', {'role': 'FLEET_MANAGER'}), ('POST', f'/members/{member}/deactivate', None),
            ('DELETE', f'/members/{member}', None)]:
            api(method, team + suffix, body, other, expected=(403,))
        replacement = api('POST', team + f'/invitations/{inv}/resend', auth=own)
        replacement_token = capture()['token']; tokens.append(replacement_token)
        validate(token, 400); accept(token, existing, (400,)); validate(replacement_token)
        api('POST', team + f'/invitations/{replacement["id"]}/revoke', auth=own)
        state('company_invitations', replacement['id'], 'status', 'REVOKED')
        validate(replacement_token, 400)
        accept(replacement_token, existing, (400,))
        expired_id, expired_token = invite(cid, own, f'expired-{cid}@example.test')
        sql(f"UPDATE company_invitations SET created_at=CURRENT_TIMESTAMP-INTERVAL '2 days',expires_at=CURRENT_TIMESTAMP-INTERVAL '1 day' WHERE id={expired_id}")
        validate(expired_token, 400)
        accept(expired_token, existing, (400,))
    path = f'/carrier-companies/{a}/team/members/{joined["id"]}'
    api('PATCH', path + '/role', {'role': 'CARRIER_OWNER'}, existing, expected=(403,))
    api('PATCH', path + '/role', {'role': 'DISPATCHER'}, existing, expected=(403,))
    for role in ['DISPATCHER', 'FLEET_MANAGER']:
        api('PATCH', path + '/role', {'role': role}, owner_a)
        state('company_memberships', joined['id'], 'role', role)
    for method, suffix, status in [('POST', '/deactivate', 'INACTIVE'), ('POST', '/activate', 'ACTIVE'), ('DELETE', '', 'REMOVED')]:
        api(method, path + suffix, auth=owner_a)
        state('company_memberships', joined['id'], 'status', status)
        api('POST', '/auth/login', {'identifier': 'full-existing@example.test', 'password': password})
        check(sql("SELECT is_active FROM users WHERE email='full-existing@example.test'") == 't', 'Membership changes preserve active user')
    browser_env = dict(os.environ, VERIFICATION_OWNER_TOKEN=owner_a, VERIFICATION_COMPANY=str(a))
    browser = subprocess.run(['node', '../frontend/artifacts/freight-link/scripts/verify-invitation-live.mjs'],
                             env=browser_env, capture_output=True, text=True, timeout=180)
    Path('full-verification-live-browser.log').write_text(browser.stdout + browser.stderr)
    check(browser.returncode == 0, 'Live browser workflow; inspect full-verification-live-browser.log')
    check(sql("SELECT count(*) FROM company_memberships m JOIN users u ON u.id=m.user_id WHERE u.email IN ('live-browser-existing@example.test','live-browser-new@example.test') AND m.status='ACTIVE' AND m.role='DISPATCHER' AND m.carrier_company_id=" + str(a)) == '2', 'Both browser memberships persisted')
    check(sql("SELECT count(*) FROM company_invitations WHERE invited_email IN ('live-browser-existing@example.test','live-browser-new@example.test') AND status='ACCEPTED' AND accepted_at IS NOT NULL") == '2', 'Both browser invitations consumed')
    audits = [
        'SELECT count(*) FROM carrier_companies c LEFT JOIN users u ON u.id=c.owner_id WHERE u.id IS NULL',
        'SELECT count(*) FROM carrier_company_documents d LEFT JOIN carrier_companies c ON c.id=d.carrier_company_id WHERE c.id IS NULL',
        'SELECT count(*) FROM company_memberships m LEFT JOIN users u ON u.id=m.user_id LEFT JOIN carrier_companies c ON c.id=m.carrier_company_id WHERE u.id IS NULL OR c.id IS NULL',
        'SELECT count(*) FROM company_invitations i LEFT JOIN carrier_companies c ON c.id=i.carrier_company_id LEFT JOIN users u ON u.id=i.invited_by WHERE c.id IS NULL OR u.id IS NULL',
        'SELECT count(*) FROM (SELECT carrier_company_id,user_id FROM company_memberships GROUP BY 1,2 HAVING count(*)>1) x',
        'SELECT count(*) FROM (SELECT token_hash FROM company_invitations GROUP BY 1 HAVING count(*)>1) x',
        "SELECT count(*) FROM company_invitations WHERE (status='ACCEPTED')<>(accepted_at IS NOT NULL AND accepted_by IS NOT NULL)",
        "SELECT count(*) FROM company_invitations WHERE (status='REVOKED')<>(revoked_at IS NOT NULL AND revoked_by IS NOT NULL)",
        "SELECT count(*) FROM carrier_companies c WHERE (SELECT count(*) FROM company_memberships m WHERE m.carrier_company_id=c.id AND m.user_id=c.owner_id AND m.role='CARRIER_OWNER' AND m.status='ACTIVE')<>1",
        "SELECT count(*) FROM company_memberships m JOIN carrier_companies c ON c.id=m.carrier_company_id WHERE m.role='CARRIER_OWNER' AND m.user_id<>c.owner_id",
        "SELECT count(*) FROM flyway_schema_history WHERE success=false",
    ]
    for query in audits:
        check(sql(query) == '0', 'Database integrity audit')
    check(sql("SELECT count(*) FROM flyway_schema_history WHERE version IN ('57','58','59','60') AND success") == '4', 'V57-V60 applied')
    log = Path('phase2-startup.log').read_text(errors='replace')
    check(all(token not in log for token in tokens), 'No invitation token leakage in application log')
    print('PASS full HTTP verification: ' + json.dumps(counts, sort_keys=True))
    Path('full-verification-http-results.json').write_text(json.dumps(counts, indent=2))
finally:
    # Delete only files referenced by this randomly named verification database.
    root = Path('uploads').resolve()
    for key in sql('SELECT storage_key FROM carrier_company_documents').splitlines():
        check(bool(re.fullmatch(r'carrier-companies/[0-9]+/[a-f0-9-]+\.pdf', key)), 'Safe cleanup key')
        file = (root / key).resolve()
        check(root in file.parents, 'Cleanup stays inside uploads')
        file.unlink(missing_ok=True)
