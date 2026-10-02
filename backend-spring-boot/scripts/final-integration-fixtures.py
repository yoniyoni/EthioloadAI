"""Create isolated audit records in the configured development database; never print secrets."""
import json, os, re, secrets, subprocess, time, urllib.request, urllib.error
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'local-run-checks'
BASE='http://127.0.0.1:8080/api'
settings={}
for line in (ROOT/'backend-spring-boot/.env').read_text().splitlines():
    if re.match(r'^[A-Z_]+=',line):
        k,v=line.split('=',1);settings[k]=v.strip().strip('\"\'')
match=re.match(r'jdbc:postgresql://([^:]+):(\d+)/([^?]+)',settings['SPRING_DATASOURCE_URL'])
host,port,db=match.groups()
def sql(query):
    env={**os.environ,'PGPASSWORD':settings['SPRING_DATASOURCE_PASSWORD']}
    r=subprocess.run([r'C:\Program Files\PostgreSQL\18\bin\psql.exe','-X','-w','-h',host,'-p',port,'-U',settings['SPRING_DATASOURCE_USERNAME'],'-d',db,'-At','-v','ON_ERROR_STOP=1','-c',query],env=env,capture_output=True,text=True)
    if r.returncode: raise RuntimeError('Audit SQL failed')
    return r.stdout.strip()
def api(method,path,body=None,token=None,expected=(200,201,204),content_type='application/json'):
    headers={'Content-Type':content_type}
    if token:headers['Authorization']='Bearer '+token
    data=body if isinstance(body,bytes) else json.dumps(body).encode() if body is not None else None
    try:r=urllib.request.urlopen(urllib.request.Request(BASE+path,data=data,headers=headers,method=method),timeout=30)
    except urllib.error.HTTPError as e:r=e
    payload=r.read()
    if r.status not in expected:
        parsed=json.loads(payload) if payload.startswith(b'{') else {}
        raise AssertionError(f'{method} {path.split("?")[0]} status {r.status}, expected {expected}; code={parsed.get("code")}; fields={list((parsed.get("errors") or {}).keys())}')
    return json.loads(payload) if payload else None
def capture():
    key=(OUT/'final-mail-key').read_text().strip()
    req=urllib.request.Request('http://127.0.0.1:12526/',headers={'X-Test-Key':key})
    return json.load(urllib.request.urlopen(req))['latest']
if __name__=='__main__':
    stamp=str(int(time.time()))
    password=secrets.token_urlsafe(22)+'aA1!'
    fixtures={'password':password,'stamp':stamp,'users':{}}
    for n,(name,role) in enumerate([('owner','fleet_owner'),('admin','shipper'),('dispatcher','shipper'),('wrong','shipper'),('driver','driver'),('shipper','shipper')]):
        email=f'audit-{stamp}-{name}@example.test'
        result=api('POST','/auth/register',{'fullName':f'Audit {name}','email':email,'phone':'+2519'+str((int(stamp)*10+n)%100000000).zfill(8),'password':password,'role':role})
        fixtures['users'][name]={'email':email,**result}
    admin=fixtures['users']['admin']
    sql(f"UPDATE users SET role='admin' WHERE id={int(admin['user']['id'])}")
    admin.update(api('POST','/auth/login',{'identifier':admin['email'],'password':password}))
    owner=fixtures['users']['owner']['token']
    body={'companyName':'Audit Carrier '+stamp,'tinNumber':'AUDIT-'+stamp,'businessRegistrationNumber':'AUDIT-REG-'+stamp,'phone':'+251911234567','email':f'office-{stamp}@example.test','businessAddress':'Audit Road','city':'Addis Ababa'}
    company=api('POST','/carrier-companies',body,owner)
    cid=company['id'];fixtures['companyId']=cid;path=f'/carrier-companies/{cid}'
    assert company['verificationStatus']=='DRAFT'
    api('POST',path+'/team/invitations',{'email':f'early-{stamp}@example.test','role':'DISPATCHER'},owner,expected=(400,))
    for kind in ['BUSINESS_LICENSE','BUSINESS_REGISTRATION','TIN_CERTIFICATE']:
        boundary='auditboundary'
        data=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="audit.pdf"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4 audit fixture\r\n--'+boundary+'--\r\n').encode()
        api('POST',path+'/documents?documentType='+kind,data,owner,content_type='multipart/form-data; boundary='+boundary)
    assert api('POST',path+'/submit',{},owner)['verificationStatus']=='PENDING'
    assert api('POST','/admin'+path+'/request-information',{'message':'Audit correction request'},admin['token'])['verificationStatus']=='MORE_INFORMATION_REQUIRED'
    api('PUT',path,body,owner);api('POST',path+'/submit',{},owner)
    assert api('POST','/admin'+path+'/approve',{},admin['token'])['verificationStatus']=='APPROVED'
    for name,role in [('new','FLEET_MANAGER'),('dispatcher','DISPATCHER'),('wrong_target','FLEET_MANAGER')]:
        email=fixtures['users'][name]['email'] if name in fixtures['users'] else f'audit-{stamp}-{name}@example.test'
        invitation=api('POST',path+'/team/invitations',{'email':email,'role':role},owner)
        mail=capture();assert mail['token'] and 'http://localhost:5174/invitations/accept#token=' in mail['text']
        preview=api('POST','/company-invitations/validate',{'token':mail['token']})
        assert preview['email']==email and preview['role']==role
        fixtures[name]={'email':email,'invitation':invitation,'token':mail['token']}
    api('POST','/company-invitations/accept',{'token':fixtures['wrong_target']['token']},fixtures['users']['wrong']['token'],expected=(403,))
    latest=sql('SELECT max(version::int) FROM flyway_schema_history WHERE success=true AND version IS NOT NULL')
    fixtures['flywayVersion']=latest
    (OUT/'final-fixtures.json').write_text(json.dumps(fixtures))
    print('PASS PostgreSQL fixtures: owner registration, DRAFT/PENDING/MORE_INFORMATION_REQUIRED/APPROVED, documents, local SMTP link, invitation previews, wrong-user denial. Flyway '+latest)
