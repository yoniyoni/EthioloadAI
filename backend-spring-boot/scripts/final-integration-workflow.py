"""Real PostgreSQL audit continuation; only uses the unique audit fixture IDs."""
import runpy,json,time,datetime
from pathlib import Path
from e2e_mail_capture import capture_cursor, captured_message
m=runpy.run_path(str(Path(__file__).with_name('final-integration-fixtures.py')))
api,sql,OUT=m['api'],m['sql'],m['OUT']
f=json.loads((OUT/'final-fixtures.json').read_text());cid=f['companyId'];base=f'/carrier-companies/{cid}'
f.get('new',{}).pop('token',None)
new_invitation_token=captured_message(0,f['new']['email'])['token']
for name,u in f['users'].items():
    u.update(api('POST','/auth/login',{'identifier':u['email'],'password':f['password']}))
owner=f['users']['owner']['token'];admin=f['users']['admin']['token'];shipper=f['users']['shipper']['token'];driver=f['users']['driver']['token'];dispatcher=f['users']['dispatcher']['token']
manager=api('POST','/auth/login',{'identifier':f['new']['email'],'password':f['password']})['token']
for token,role in [(manager,'FLEET_MANAGER'),(dispatcher,'DISPATCHER')]:
    members=api('GET','/company-memberships/me',token=token)['content']
    assert any(x['carrierCompanyId']==cid and x['role']==role for x in members)
assert len(api('GET',base+'/team/members',token=owner)['content'])==3
api('POST','/company-invitations/accept',{'token':new_invitation_token},manager,expected=(400,))
api('POST','/company-invitations/validate',{'token':'x'*43},expected=(400,))
edge_email=f'edge-{time.time_ns()}@example.test'
cursor=capture_cursor();edge={'invitation':api('POST',base+'/team/invitations',{'email':edge_email,'role':'DISPATCHER'},owner)};original=captured_message(cursor,edge_email)['token']
cursor=capture_cursor();replacement=api('POST',base+f'/team/invitations/{edge["invitation"]["id"]}/resend',{},owner);replacementToken=captured_message(cursor,edge_email)['token']
api('POST','/company-invitations/validate',{'token':original},expected=(400,))
api('POST',base+f'/team/invitations/{replacement["id"]}/revoke',{},owner)
api('POST','/company-invitations/validate',{'token':replacementToken},expected=(400,))
expired_email='expired-'+f['stamp']+'@example.test'
cursor=capture_cursor();expired=api('POST',base+'/team/invitations',{'email':expired_email,'role':'DISPATCHER'},owner);expiredToken=captured_message(cursor,expired_email)['token']
sql(f"UPDATE company_invitations SET created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' WHERE id={int(expired['id'])} AND carrier_company_id={int(cid)}")
api('POST','/company-invitations/validate',{'token':expiredToken},expected=(400,))
api('POST',base+'/team/invitations',{'email':f['new']['email'],'role':'DISPATCHER'},owner,expected=(409,))
member=next(x for x in api('GET',base+'/team/members',token=owner)['content'] if x['role']=='FLEET_MANAGER')
api('POST',base+f'/team/members/{member["id"]}/deactivate',{},owner)
api('GET',base+'/fleet/drivers',token=manager,expected=(403,404))
api('POST',base+f'/team/members/{member["id"]}/activate',{},owner)
api('GET',base+'/fleet/drivers',token=manager)
api('GET',base+'/fleet/drivers',token=shipper,expected=(403,404))
# Isolated driver KYC prerequisite: driver-document approval is separately covered by backend tests.
driverId=int(f['users']['driver']['user']['id'])
sql(f'UPDATE users SET verification_status=true,is_active=true WHERE id={driverId}')
cd=next((d for d in api('GET',base+'/fleet/drivers',token=manager)['content'] if d['driverUserId']==driverId),None) or api('POST',base+'/fleet/drivers',{'driverUserId':driverId},manager)
vehicle=next((v for v in api('GET',base+'/fleet/vehicles',token=manager)['content'] if v['plateNumber']=='AUD-'+f['stamp']),None) or api('POST',base+'/fleet/vehicles',{'truckType':'medium_truck','plateNumber':'AUD-'+f['stamp'],'capacity':10,'currentCity':'Addis Ababa','vehicleCategory':'HEAVY'},manager)
vid=vehicle['id']
if vehicle.get('approvalStatus')!='APPROVED':api('PATCH',f'/vehicles/{vid}/approval-status',{'status':'APPROVED'},admin)
if not any(a.get('status')=='ACTIVE' and a['vehicleId']==vid for a in api('GET',base+'/fleet/assignments',token=manager)['content']):
    api('POST',base+'/fleet/assignments',{'carrierDriverId':cd['id'],'vehicleId':vid},manager)
freight=api('POST','/freight',{'pickupLocation':'Addis Ababa','destination':'Adama','pickupLatitude':9.03,'pickupLongitude':38.74,'destinationLatitude':8.54,'destinationLongitude':39.27,'materialType':'general','weightTons':2,'budget':12000,'distanceKm':100,'priceType':'negotiable','serviceType':'intercity','urgencyLevel':'normal'},shipper)
fid=freight['id'];offer=api('POST',base+f'/freight/{fid}/offers',{'amount':11000},dispatcher)
api('POST',f'/freight/{fid}/carrier-offers/{offer["id"]}/counter',{'amount':10500},shipper)
api('POST',base+f'/offers/{offer["id"]}/accept',{},dispatcher)
dispatch=api('POST',base+f'/freight/{fid}/dispatch',{'driverUserId':driverId,'vehicleId':vid},dispatcher)
f.update({'vehicleId':vid,'carrierDriverId':cd['id'],'freightId':fid,'offerId':offer['id'],'dispatchId':dispatch['id']})
(OUT/'final-fixtures.json').write_text(json.dumps(f))
assert any(x['id']==dispatch['id'] for x in api('GET','/driver/assignments',token=driver))
print('PASS invitation states, membership deactivation/reactivation, tenant denial, fleet, negotiation and dispatch. Driver assignment ready for Flutter acceptance.')
