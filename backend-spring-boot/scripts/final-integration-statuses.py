"""Exercise operational gates on isolated audit records; restore company state."""
import json, runpy, time
from pathlib import Path
m=runpy.run_path(str(Path(__file__).with_name('final-integration-fixtures.py')))
api,sql,OUT=m['api'],m['sql'],m['OUT']
f=json.loads((OUT/'final-fixtures.json').read_text());cid=int(f['companyId']);base=f'/carrier-companies/{cid}'
def login(email):return api('POST','/auth/login',{'identifier':email,'password':f['password']})['token']
owner=login(f['users']['owner']['email']);manager=login(f['new']['email'])
original=sql(f"SELECT format('verification_status=%L,reviewed_by=%L,reviewed_at=%L,review_message=%L',verification_status,reviewed_by,reviewed_at,review_message) FROM carrier_companies WHERE id={cid}")
try:
    for status in ['DRAFT','PENDING','MORE_INFORMATION_REQUIRED','REJECTED']:
        # This is explicit test-state injection, not a claimed admin UI transition.
        review='reviewed_by=NULL,reviewed_at=NULL,review_message=NULL' if status in ('DRAFT','PENDING') else f"reviewed_by={int(f['users']['admin']['user']['id'])},reviewed_at=now(),review_message='Audit state gate'"
        sql(f"UPDATE carrier_companies SET verification_status='{status}',{review} WHERE id={cid}")
        api('POST',base+'/fleet/drivers',{'driverUserId':f['users']['driver']['user']['id']},manager,expected=(400,403))
        api('POST',base+'/team/invitations',{'email':f'gate-{time.time_ns()}@example.test','role':'DISPATCHER'},owner,expected=(400,403))
finally:sql(f"UPDATE carrier_companies SET {original} WHERE id={cid}")
api('GET',base+'/fleet/drivers',token=manager)
member=next(x for x in api('GET',base+'/team/members',token=owner)['content'] if x['role']=='FLEET_MANAGER')
removed=api('DELETE',base+f'/team/members/{member["id"]}',token=owner)
assert removed['status']=='REMOVED'
api('GET',base+'/fleet/drivers',token=manager,expected=(403,404))
api('POST',base+f'/team/members/{member["id"]}/activate',{},owner,expected=(400,))
assert not any(x['carrierCompanyId']==cid and x['status']=='ACTIVE' for x in api('GET','/company-memberships/me',token=manager)['content'])
api('GET','/auth/me',token=manager)
assert sql("SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='public' AND table_name='trips' AND column_name='trip_status'")=='40'
assert sql("SELECT count(*) FROM pg_class WHERE relname IN ('v_trip_details','v_trip_stop_details','v_system_health','v_recent_activity','mv_trip_performance_daily','mv_driver_performance') AND relkind IN ('v','m')")=='6'
assert sql("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL")=='72'
print('PASS all implemented company operational gates, REMOVED membership denial/account preservation, V72 length and all six reporting views retained. Audit Fleet Manager remains removed for this test.')
