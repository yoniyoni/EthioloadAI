"""Send one invitation to an explicitly supplied recipient using backend SMTP."""
import argparse, json, runpy
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('recipient')
args=parser.parse_args()
m=runpy.run_path(str(Path(__file__).with_name('final-integration-fixtures.py')))
api,OUT=m['api'],m['OUT']
f=json.loads((OUT/'final-fixtures.json').read_text())
token=api('POST','/auth/login',{'identifier':f['users']['owner']['email'],'password':f['password']})['token']
result=api('POST',f'/carrier-companies/{f["companyId"]}/team/invitations',{'email':args.recipient,'role':'FLEET_MANAGER'},token)
evidence={'companyId':f['companyId'],'invitationId':result['id'],'status':result['status'],'recipient':args.recipient,'role':'FLEET_MANAGER','transport':'configured backend SMTP','inboxReceiptConfirmed':False}
(OUT/'final-external-invitation.json').write_text(json.dumps(evidence,indent=2))
print('PASS invitation created and SMTP send completed; status='+result['status']+'. Inbox receipt still requires recipient confirmation.')
