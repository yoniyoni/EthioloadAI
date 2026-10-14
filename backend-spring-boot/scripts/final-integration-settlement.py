"""Verify real delivery/settlement state after Flutter upload and browser approval."""
import json, runpy
from decimal import Decimal
from pathlib import Path
m=runpy.run_path(str(Path(__file__).with_name('final-integration-fixtures.py')))
api,sql,OUT=m['api'],m['sql'],m['OUT']
f=json.loads((OUT/'final-fixtures.json').read_text())
tokens={name:api('POST','/auth/login',{'identifier':u['email'],'password':f['password']})['token'] for name,u in f['users'].items()}
trip,proof,booking=f['tripId'],f['proofId'],f['bookingId']
assert api('GET',f'/trips/{trip}',token=tokens['shipper'])['tripStatus'].upper()=='COMPLETED'
assert api('GET',f'/proof-of-delivery/{proof}',token=tokens['shipper'])['status']=='VERIFIED'
api('GET',f'/proof-of-delivery/{proof}/download',token=tokens['wrong'],expected=(403,404))
api('POST',f'/proof-of-delivery/{proof}/verify',{'action':'VERIFY'},tokens['wrong'],expected=(403,404))
api('GET',f'/trips/{trip}/location',token=tokens['shipper'])
api('GET',f'/trips/{trip}/location',token=tokens['wrong'],expected=(403,404))
s=api('GET',f'/settlements/booking/{booking}',token=tokens['owner']);sid=s['id']
assert Decimal(str(s['grossAmount']))==Decimal('10500')
assert Decimal(str(s['grossAmount']))-Decimal(str(s['commissionAmount']))==Decimal(str(s['netAmount']))
assert s['carrierCompanyId']==f['companyId'] and s['status']=='PENDING'
api('GET',f'/settlements/{sid}',token=tokens['wrong'],expected=(403,404))
api('POST',f'/settlements/{sid}/process',{},tokens['owner'],expected=(403,))
api('POST',f'/proof-of-delivery/{proof}/verify',{'action':'VERIFY'},tokens['shipper'],expected=(200,400,409))
assert sql(f'SELECT count(*) FROM carrier_settlements WHERE booking_id={int(booking)}')=='1'
for action,body,status in [('block',{'reason':'Audit lifecycle'},'BLOCKED'),('unblock',{},'PENDING'),('process',{},'PROCESSING'),('fail',{'reason':'Audit simulated processing failure'},'FAILED'),('retry',{},'PENDING'),('process',{},'PROCESSING'),('pay',{'externalTxId':'AUDIT-'+f['stamp'],'paymentReference':'LOCAL-AUDIT'},'PAID')]:
    result=api('POST',f'/settlements/{sid}/{action}',body,tokens['admin'])
    assert result['status']==status, (action,result['status'])
    assert result['grossAmount']==s['grossAmount'] and result['netAmount']==s['netAmount']
f['settlementId']=sid
(OUT/'final-fixtures.json').write_text(json.dumps(f))
print('PASS delivery completion, tracking authorization, private proof, settlement uniqueness, gross=10500, commission/net consistency, BLOCKED/PENDING/PROCESSING/FAILED/retry/PAID. Payment is a local ledger transition, not an external transfer.')
