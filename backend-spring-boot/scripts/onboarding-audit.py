"""Real-stack onboarding audit. Only mutate records created by this audit."""
import runpy,json,sys,urllib.request,re
from pathlib import Path
m=runpy.run_path(str(Path(__file__).with_name('final-integration-fixtures.py')))
api,sql,OUT=m['api'],m['sql'],m['OUT']
path=OUT/'onboarding-fixtures.json'
f=json.loads(path.read_text())
mode=sys.argv[1] if len(sys.argv)>1 else 'setup'
def save():path.write_text(json.dumps(f))
def login(name):
    u=f['users'][name]
    u['session']=api('POST','/auth/login',{'identifier':u['email'],'password':f['password']})
    return u['session']['token']
def capture():
    key=(OUT/'onboarding-mail-key').read_text().strip()
    return json.load(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:12526/',headers={'X-Test-Key':key})))['latest']
if mode=='setup':
    if 'admin' not in f['users']:
        address='onboarding-'+f['stamp']+'-admin@example.test'
        result=api('POST','/auth/register',{'fullName':'Onboarding Audit Admin','email':address,'phone':'+2518'+str(int(f['stamp'])%100000000).zfill(8),'password':f['password'],'role':'shipper'})
        f['users']['admin']={'email':address,'session':result}
        uid=int(result['user']['id'])
        sql(f"UPDATE users SET role='admin' WHERE id={uid}")
        login('admin');save()
    settings=m['settings']
    summary={'frontend':settings.get('FRONTEND_BASE_URL'),'smtpHost':settings.get('SMTP_HOST'),'smtpConfigured':all(settings.get(k) for k in ['SMTP_HOST','SMTP_USER','SMTP_PASSWORD','EMAIL_FROM']),'database':m['db'],'flyway':sql('SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL')}
    (OUT/'onboarding-config.json').write_text(json.dumps(summary,indent=2))
    print('PASS isolated admin fixture provisioned; configuration summary saved without secrets')
elif mode=='snapshot':
    cid=int(f['ABC']['id']);xid=int(f['XYZ']['id'])
    rows=json.loads(sql(f"SELECT coalesce(json_agg(t),'[]') FROM (SELECT u.id AS user_id,c.id AS company_id,m.role,m.status,i.status AS invitation_status,i.accepted_at IS NOT NULL AS accepted_at_set,length(i.token_hash)=64 AS hash_only,count(*) OVER(PARTITION BY m.carrier_company_id,m.user_id) AS membership_count FROM company_memberships m JOIN users u ON u.id=m.user_id JOIN carrier_companies c ON c.id=m.carrier_company_id JOIN company_invitations i ON i.accepted_by=u.id AND i.carrier_company_id=c.id WHERE c.id IN ({cid},{xid}) AND i.status='ACCEPTED') t"))
    assert len(rows)>=4 and all(r['membership_count']==1 and r['status']=='ACTIVE' and r['accepted_at_set'] and r['hash_only'] for r in rows)
    for name in ['manager','dispatcher','newdispatcher']:
        if name not in f['users']:continue
        token=login(name)
        members=api('GET','/company-memberships/me',token=token)['content']
        assert any(m['carrierCompanyId']==cid and m['role']==f[name]['role'] for m in members)
    result={'result':'PASS','flyway':sql('SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL'),'postgres':rows}
    (OUT/'onboarding-post-restart-postgres.json').write_text(json.dumps(result,indent=2));save();print(json.dumps(result))
elif mode=='verify':
    import time,hashlib
    checks=[]
    owner,admin,manager,dispatcher,xyzOwner=[login(n) for n in ['owner','admin','manager','dispatcher','xyzOwner']]
    cid=int(f['ABC']['id']);xid=int(f['XYZ']['id']);base=f'/carrier-companies/{cid}'
    def register(name,role='shipper'):
        if name not in f['users']:
            address=f"onboarding-{f['stamp']}-{name}@example.test"
            phone='+2517'+str((int(f['stamp'])+len(f['users']))%100000000).zfill(8)
            session=api('POST','/auth/register',{'fullName':'Onboarding '+name,'phone':phone,'email':address,'password':f['password'],'role':role})
            f['users'][name]={'email':address,'session':session};save()
        return login(name)
    def issue(company,token,name,role='DISPATCHER'):
        address=f['users'].get(name,{}).get('email',f"onboarding-{f['stamp']}-{name}@example.test").lower()
        inv=api('POST',f'/carrier-companies/{company}/team/invitations',{'email':address,'role':role},token)
        mail=capture();assert mail['recipient']==address
        raw=mail['token'];assert len(raw)==43
        f.setdefault('edgeTokens',[]).append(raw);save()
        return inv,raw,address
    # New employees in XYZ prove both companies are isolated in both directions.
    for name,role in [('xyzManager','FLEET_MANAGER'),('xyzDispatcher','DISPATCHER')]:
        if name not in f['users']:
            inv,raw,address=issue(xid,xyzOwner,name,role)
            payload={'token':raw,'fullName':'Onboarding '+name,'phone':'+2517'+str((int(f['stamp'])+len(f['users']))%100000000).zfill(8),'password':f['password'],'email':'tampered@example.test','role':'CARRIER_OWNER','carrierCompanyId':cid}
            result=api('POST','/company-invitations/register',payload)
            assert result['membership']['role']==role and result['membership']['carrierCompanyId']==xid
            assert result['session']['user']['email']==address
            f['users'][name]={'email':address,'session':result['session']};save()
    checks.append('New-user registration derives email/company/role from token; ignores tampered owner role and company body')
    for name,own,other,expectedRole in [('manager',cid,xid,'FLEET_MANAGER'),('dispatcher',cid,xid,'DISPATCHER'),('xyzManager',xid,cid,'FLEET_MANAGER'),('xyzDispatcher',xid,cid,'DISPATCHER')]:
        token=login(name)
        member=api('GET','/company-memberships/me',token=token)['content']
        assert len(member)==1 and member[0]['carrierCompanyId']==own and member[0]['role']==expectedRole
        for suffix in ['', '/documents','/fleet/drivers','/fleet/vehicles','/fleet/assignments','/awarded-freights','/dispatches','/offers']:
            api('GET',f'/carrier-companies/{own}'+suffix,token=token)
            api('GET',f'/carrier-companies/{other}'+suffix,token=token,expected=(403,404))
        api('GET',f'/carrier-companies/{own}/team/members',token=token,expected=(403,404))
        api('POST',f'/carrier-companies/{own}/team/invitations',{'email':'denied@example.test','role':'DISPATCHER'},token,expected=(403,404))
        api('GET','/admin/carrier-companies',token=token,expected=(403,))
        api('POST',f'/carrier-companies/{other}/fleet/vehicles',{'plateNumber':'DENIED','truckType':'medium_truck','capacity':2},token,expected=(403,404))
    checks.append('Both roles in ABC and XYZ: own resources readable; cross-company reads/writes, owner team, admin denied')
    documents=api('GET',base+'/documents',token=owner)
    assert len(documents)==3
    # Binary document requests: do not feed them into the JSON-only API helper.
    for token,expected in [(owner,200),(admin,200),(login('xyzManager'),404)]:
        req=urllib.request.Request(m['BASE']+base+f"/documents/{documents[0]['id']}/file",headers={'Authorization':'Bearer '+token})
        try:r=urllib.request.urlopen(req)
        except urllib.error.HTTPError as e:r=e
        assert r.status==expected
        if expected==200:assert r.read().startswith(b'%PDF-')
    checks.append('Company documents stored and downloadable by owner/admin; unrelated company denied')
    for name in ['manager','dispatcher']:
        raw=f[name]['url'].split('#token=')[1]
        api('POST','/company-invitations/accept',{'token':raw},login(name),expected=(400,))
    api('POST',base+'/team/invitations',{'email':f['users']['manager']['email'],'role':'FLEET_MANAGER'},owner,expected=(409,))
    api('POST',base+'/team/invitations',{'email':'denied@example.test','role':'CARRIER_OWNER'},owner,expected=(400,))
    wrong=register('wrong');inv,raw,address=issue(cid,owner,'alice')
    api('POST','/company-invitations/accept',{'token':raw},wrong,expected=(403,))
    assert not api('GET','/company-memberships/me',token=wrong)['content']
    replacement=api('POST',base+f"/team/invitations/{inv['id']}/resend",{},owner);new=capture()['token'];f['edgeTokens'].append(new);save()
    api('POST','/company-invitations/validate',{'token':raw},expected=(400,))
    api('POST','/company-invitations/validate',{'token':new})
    api('POST',base+f"/team/invitations/{replacement['id']}/revoke",{},owner)
    api('POST','/company-invitations/validate',{'token':new},expected=(400,))
    api('POST','/company-invitations/accept',{'token':new},wrong,expected=(400,))
    expired,expiredRaw,_=issue(cid,owner,'expired')
    sql(f"UPDATE company_invitations SET created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' WHERE id={int(expired['id'])} AND carrier_company_id={cid}")
    api('POST','/company-invitations/validate',{'token':expiredRaw},expected=(400,))
    api('POST','/company-invitations/accept',{'token':expiredRaw},wrong,expected=(400,))
    checks.append('Wrong email, duplicate active member, owner-role invitation, accepted reuse, expired/revoked tokens denied; resend replaces old token')
    # Test all non-approved statuses through real lifecycle endpoints.
    for decision in ['request-information','reject']:
        application=api('POST','/carrier-companies',{'companyName':f"Onboarding {decision} {f['stamp']}",'tinNumber':f"ON-{decision}-{f['stamp']}",'businessRegistrationNumber':f"REG-{decision}-{f['stamp']}",'phone':'+251911234567','email':'audit@example.test','businessAddress':'Audit','city':'Addis Ababa'},owner)
        aid=application['id'];f.setdefault('statusCompanyIds',[]).append(aid);save();ab=f'/carrier-companies/{aid}'
        for kind in ['BUSINESS_LICENSE','BUSINESS_REGISTRATION','TIN_CERTIFICATE']:
            boundary='onboardingAuditBoundary'
            content=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="audit.pdf"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4 audit\r\n--'+boundary+'--\r\n').encode()
            api('POST',ab+'/documents?documentType='+kind,content,owner,content_type='multipart/form-data; boundary='+boundary)
        api('POST',ab+'/submit',{},owner)
        reviewed=api('POST','/admin'+ab+'/'+decision,{'message':'Controlled onboarding audit'},admin)
        assert reviewed['verificationStatus']==('REJECTED' if decision=='reject' else 'MORE_INFORMATION_REQUIRED')
        api('POST',ab+'/team/invitations',{'email':'denied@example.test','role':'DISPATCHER'},owner,expected=(400,))
    checks.append('Admin request-information/reject persist; invitations rejected for both non-approved states; DRAFT/PENDING covered in browser')
    driver=register('driver','driver');driverId=int(f['users']['driver']['session']['user']['id'])
    # Driver KYC is a fixture prerequisite, not part of company onboarding under audit.
    sql(f'UPDATE users SET verification_status=true,is_active=true WHERE id={driverId}')
    cd=api('POST',base+'/fleet/drivers',{'driverUserId':driverId},manager)
    vehicle=api('POST',base+'/fleet/vehicles',{'truckType':'medium_truck','plateNumber':'ON-'+f['stamp'],'capacity':10,'currentCity':'Addis Ababa','vehicleCategory':'HEAVY'},manager)
    vid=vehicle['id'];api('PATCH',f'/vehicles/{vid}/approval-status',{'status':'APPROVED'},admin)
    assignment=api('POST',base+'/fleet/assignments',{'carrierDriverId':cd['id'],'vehicleId':vid},manager)
    api('POST',base+'/fleet/vehicles',{'truckType':'medium_truck','plateNumber':'DENIED','capacity':10},dispatcher,expected=(403,))
    shipper=register('customer')
    freight=api('POST','/freight',{'pickupLocation':'Addis Ababa','destination':'Adama','pickupLatitude':9.03,'pickupLongitude':38.74,'destinationLatitude':8.54,'destinationLongitude':39.27,'materialType':'general','weightTons':2,'budget':12000,'distanceKm':100,'priceType':'negotiable','serviceType':'intercity','urgencyLevel':'normal'},shipper)
    fid=freight['id'];offer=api('POST',base+f'/freight/{fid}/offers',{'amount':11000},dispatcher)
    api('POST',f'/freight/{fid}/carrier-offers/{offer["id"]}/counter',{'amount':10500},shipper)
    api('POST',base+f'/offers/{offer["id"]}/accept',{},dispatcher)
    dispatch=api('POST',base+f'/freight/{fid}/dispatch',{'driverUserId':driverId,'vehicleId':vid},dispatcher)
    f['operations']={'driverId':driverId,'carrierDriverId':cd['id'],'vehicleId':vid,'freightId':fid,'offerId':offer['id'],'dispatchId':dispatch['id']};save()
    assert any(a['id']==dispatch['id'] for a in api('GET',base+'/dispatches',token=dispatcher)['content'])
    checks.append('Fleet Manager adds driver/vehicle/assignment; Dispatcher cannot write fleet, can negotiate award and dispatch its resources')
    rows=json.loads(sql(f"SELECT coalesce(json_agg(t),'[]') FROM (SELECT u.id AS user_id,c.id AS company_id,m.role,m.status,i.status AS invitation_status,i.accepted_at IS NOT NULL AS accepted_at_set,(length(i.token_hash)=64) AS hash_only,count(*) OVER(PARTITION BY m.carrier_company_id,m.user_id) AS membership_count FROM company_memberships m JOIN users u ON u.id=m.user_id JOIN carrier_companies c ON c.id=m.carrier_company_id JOIN company_invitations i ON i.accepted_by=u.id AND i.carrier_company_id=c.id WHERE c.id IN ({cid},{xid}) AND i.status='ACCEPTED') t"))
    assert len(rows)==4 and all(r['membership_count']==1 and r['status']=='ACTIVE' and r['accepted_at_set'] and r['hash_only'] for r in rows)
    checks.append('PostgreSQL: four accepted employee invitations, one ACTIVE company membership each, accepted_at set, SHA-256 token hashes only')
    knownTokens=f['edgeTokens']+[f[n]['url'].split('#token=')[1] for n in ['manager','dispatcher']]
    log=(OUT/'onboarding-application.log').read_text(errors='replace')
    assert not any(raw in log for raw in knownTokens)
    checks.append('Runtime application log contains no tested raw invitation tokens')
    result={'result':'PASS','checks':checks,'postgres':rows,'companyIds':[cid,xid]}
    (OUT/'onboarding-real-api-results.json').write_text(json.dumps(result,indent=2));save()
    print(json.dumps(result))
