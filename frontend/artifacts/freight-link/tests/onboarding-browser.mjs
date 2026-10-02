// Real React + Spring Boot + PostgreSQL + loopback SMTP. Run from repository root.
// Fixture/session files are kept under ignored local-run-checks; no tokens are printed.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const mode=process.argv[2] || 'baseline';
const root='local-run-checks/';
const fixturePath=root+'onboarding-fixtures.json';
const origin='http://localhost:5174';
const stamp=String(Date.now());
if(mode==='init' && fs.existsSync(fixturePath))fs.copyFileSync(fixturePath,root+'onboarding-fixtures-archive-'+stamp+'.json');
const f=mode!=='init' && fs.existsSync(fixturePath)?JSON.parse(fs.readFileSync(fixturePath)): {stamp,password:crypto.randomBytes(18).toString('base64url')+'aA1!',users:{}};
const save=()=>fs.writeFileSync(fixturePath,JSON.stringify(f));
const targets=await(await fetch('http://127.0.0.1:9232/json')).json();
const ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise(r=>ws.onopen=r);
let serial=0;const pending=new Map();const network=[];const checks=[];
let holdMemberships=false;let failMemberships=false;const heldMemberships=[];
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++serial;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
ws.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(new Error(m.error.message)):p.resolve(m.result);}if(m.method==='Network.responseReceived'){const r=m.params.response;const path=new URL(r.url).pathname;if(path.startsWith('/api/'))network.push({path,status:r.status});}if(m.method==='Page.javascriptDialogOpening')void send('Page.handleJavaScriptDialog',{accept:true});if(m.method==='Fetch.requestPaused'){if(holdMemberships)heldMemberships.push(m.params.requestId);else if(failMemberships)void send('Fetch.fulfillRequest',{requestId:m.params.requestId,responseCode:503,body:Buffer.from('{}').toString('base64')});else void send('Fetch.continueRequest',{requestId:m.params.requestId});}};
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert.ok(!r.exceptionDetails,'Browser expression failed');return r.result.value;}
async function wait(expression,label){for(let i=0;i<150;i++){if(await evaluate(expression))return;await pause(100);}throw new Error(label || expression);}
async function go(path){await send('Page.navigate',{url:path.startsWith('http')?path:origin+path});await pause(400);await wait("document.readyState==='complete'");}
async function fill(selector,value){await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});if(!e)throw Error('Missing input');const proto=e.tagName==='SELECT'?HTMLSelectElement.prototype:e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);}
async function click(text){assert.ok(await evaluate(`(()=>{const e=[...document.querySelectorAll('button,a')].find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e || e.disabled)return false;e.click();return true;})()`),'Missing/enabled control '+text);}
async function clear(){
 await go('/dashboard');
 if(await evaluate("!!localStorage.getItem('freightlink_auth')")){
  await wait("!!document.querySelector('button:has(svg.lucide-log-out)')",'Logout control');
  await evaluate("document.querySelector('button:has(svg.lucide-log-out)').click()");
  await wait("!localStorage.getItem('freightlink_auth')");await pause(300);
 }
 await go('/login');await evaluate('sessionStorage.clear()');
}
async function login(name){await clear();await wait("!!document.querySelector('#email')");await fill('#email',f.users[name].email);await fill('#password',f.password);await evaluate("document.querySelector('form').requestSubmit()");await wait("location.pathname !== '/login'",'Login '+name);f.users[name].session=await evaluate("JSON.parse(localStorage.getItem('freightlink_auth'))");save();}
async function register(name,role='shipper') {
 await clear();await go('/register');await wait("!!document.querySelector('#name')");
 const email=`onboarding-${f.stamp}-${name}@example.test`;
 const phone='+2519'+String((Number(f.stamp)+Object.keys(f.users).length)%100000000).padStart(8,'0');
 await fill('#name','Onboarding '+name);await fill('#phone',phone);await fill('#role',role);await click('Next Step');await wait("!!document.querySelector('#email')");await fill('#email',email);await click('Next');await wait("!!document.querySelector('#password')");await fill('#password',f.password);await click('Create Account');await wait("location.pathname!=='/register'",'Registration '+name);
 f.users[name]={email,phone,session:await evaluate("JSON.parse(localStorage.getItem('freightlink_auth'))")};save();checks.push('Browser registration '+name);
}
async function api(path,body,method=body===undefined?'GET':'POST') {return evaluate(`(async()=>{const a=JSON.parse(localStorage.getItem('freightlink_auth')||'null');const r=await fetch('/api'+${JSON.stringify(path)},{method:${JSON.stringify(method)},headers:{'Content-Type':'application/json',...(a?{Authorization:'Bearer '+a.token}:{})},${body===undefined?'':`body:JSON.stringify(${JSON.stringify(body)}),`}});const text=await r.text();return {status:r.status,data:text?JSON.parse(text):null};})()`);}
async function company(name) {
 if(!f[name]?.id) {
 await go('/fleet/companies/new');await wait("!!document.querySelector('#companyName')",'Company registration accessible');
 for(const [key,value]of Object.entries({companyName:`Onboarding ${name} ${f.stamp}`,tinNumber:`ON-${name}-${f.stamp}`,businessRegistrationNumber:`ONREG-${name}-${f.stamp}`,phone:'+251911234567',email:`office-${name}-${f.stamp}@example.test`,businessAddress:'Audit Street',city:'Addis Ababa'}))await fill('#'+key,value);
 await click('Save and continue');await wait("/^\\/fleet\\/companies\\/\\d+$/.test(location.pathname)");const id=Number((await evaluate('location.pathname')).split('/').at(-1));f[name]={id};save();
 }
 const id=f[name].id;await go(`/fleet/companies/${id}`);
 assert.equal((await api(`/carrier-companies/${id}/submit`,{})).status,400);
 assert.equal((await api(`/carrier-companies/${id}/team/invitations`,{email:`early-${f.stamp}@example.test`,role:'DISPATCHER'})).status,400);
 for(const type of ['BUSINESS_LICENSE','BUSINESS_REGISTRATION','TIN_CERTIFICATE']){
  await wait(`!!document.querySelector('#${type}') && !document.querySelector('#${type}').disabled`);
  await evaluate(`(()=>{const transfer=new DataTransfer();transfer.items.add(new File([${JSON.stringify('%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF')}],'audit.pdf',{type:'application/pdf'}));const input=document.querySelector('#${type}');input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));})()`);
  await wait(`document.querySelector('#${type}').parentElement.textContent.includes('Replace document') && !document.querySelector('#${type}').disabled`,'Upload '+type);
 }
 await click('Submit for review');await wait("document.body.innerText.includes('Under review')");
 assert.equal((await api(`/carrier-companies/${id}/team/invitations`,{email:`early-${f.stamp}@example.test`,role:'DISPATCHER'})).status,400);
 checks.push('Company '+name+' DRAFT, three document uploads, PENDING; early invitations denied');return id;
}
async function approve(id,name){await login('admin');await go('/admin/companies');await wait(`document.body.innerText.includes('Onboarding ${name} ${f.stamp}')`);await evaluate(`([...document.querySelectorAll('button')].find(e=>e.textContent.includes('Onboarding ${name} ${f.stamp}'))).click()`);await wait("[...document.querySelectorAll('button')].some(e=>e.textContent==='Approve company' && !e.disabled)");await click('Download document');await click('Approve company');await wait("document.body.innerText.includes('Review saved')");checks.push('Admin browser document download and approval '+name);}
async function invite(name,role){await login('owner');await go(`/company/${f.ABC.id}`);await wait("!!document.querySelector('#invite-email')");const email=f.users[name]?.email || `onboarding-${f.stamp}-${name}@example.test`;await fill('#invite-email',email);await fill('#invite-role',role);await click('Send invitation');await wait("document.body.innerText.includes('Invitation sent.')");const key=fs.readFileSync(root+'onboarding-mail-key','utf8').trim();const mail=(await(await fetch('http://127.0.0.1:12526/',{headers:{'X-Test-Key':key}})).json()).latest;assert.equal(mail.recipient,email);assert.ok(mail.subject.includes('Freight-Link'));assert.ok(mail.text.includes(role.replaceAll('_',' ')));const url=mail.text.match(/http[^\s]+\/invitations\/accept#token=[A-Za-z0-9_-]{43}/)?.[0];assert.ok(url?.startsWith(origin));f[name]={email,role,url};save();checks.push('Browser invitation and captured SMTP '+name);}
async function accept(name,isNew){await clear();await go(f[name].url);await wait("!!document.querySelector('#invitation-password')");assert.equal(await evaluate("document.querySelector('#invited-email').value"),f[name].email);assert.equal(await evaluate("document.querySelector('#invited-email').readOnly"),true);assert.equal(await evaluate('location.hash'),'');await send('Page.reload');await pause(400);await wait("!!document.querySelector('#invitation-password')");if(isNew){await fill('#invitation-name','Onboarding '+name);const phone='+2519'+String((Number(f.stamp)+Object.keys(f.users).length)%100000000).padStart(8,'0');await fill('#invitation-phone',phone);await fill('#invitation-confirm-password',f.password);f.users[name]={email:f[name].email,phone};}await fill('#invitation-password',f.password);await evaluate("document.querySelector('main form').requestSubmit()");await wait(`location.pathname==='/company/${f.ABC.id}' && document.body.innerText.includes('${f[name].role.replaceAll('_',' ')}')`,'Invitation accepted '+name);f.users[name].session=await evaluate("JSON.parse(localStorage.getItem('freightlink_auth'))");save();assert.equal(await evaluate("sessionStorage.getItem('freightlink_pending_invitation')"),null);await send('Page.reload');await pause(400);await wait(`document.body.innerText.includes('${f[name].role.replaceAll('_',' ')}')`);await login(name);await wait(`document.body.innerText.includes('${f[name].role.replaceAll('_',' ')}')`,'Normal login resolves membership '+name);checks.push('Browser '+name+' '+(isNew?'account creation':'existing login')+', automatic acceptance, refresh, fresh login');}
await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
try {
 if(mode==='init') {save();checks.push('Fresh isolated audit fixture initialized');}
 else if(mode==='baseline') {
  if(!f.users.owner)await register('owner');await login('owner');await go('/fleet/companies/new');await pause(1500);
  const result={path:await evaluate('location.pathname'),form:await evaluate("!!document.querySelector('#companyName')"),role:f.users.owner.session.user.role};
  checks.push({baselineCompanyRoute:result});
  assert.equal(result.form,false,'Expected to reproduce ordinary-account route block');
 } else if(mode==='full') {
  if(!f.users.owner)await register('owner');await login('owner');await company('ABC');await approve(f.ABC.id,'ABC');
  await register('dispatcher');await invite('manager','FLEET_MANAGER');await accept('manager',true);
  await invite('dispatcher','DISPATCHER');await accept('dispatcher',false);
  await login('owner');await go(`/fleet/companies/${f.ABC.id}`);await wait("document.body.innerText.includes('Approved')");
  await register('xyzOwner');await company('XYZ');await approve(f.XYZ.id,'XYZ');
 } else if(mode==='approval-refresh') {
  await login('owner');const name='POLL'+Date.now();const id=await company(name);
  const session=await(await fetch(origin+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({identifier:f.users.admin.email,password:f.password})})).json();
  const review=await fetch(origin+`/api/admin/carrier-companies/${id}/approve`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+session.token},body:'{}'});
  assert.equal(review.status,200);
  await wait("document.body.innerText.includes('Approved') && !!document.querySelector('#invite-email')",'Owner sees approval without manual reload');
  checks.push('Owner pending application automatically reflects admin approval and unlocks invitations');
 } else if(mode==='new-dispatcher') {
  await invite('newdispatcher','DISPATCHER');await accept('newdispatcher',true);
 } else if(mode==='roles') {
  await login('manager');await wait("document.body.innerText.includes('FLEET MANAGER')");
  await wait(`document.body.innerText.includes('ON-${f.stamp}')`);
  assert.equal(await evaluate("!!document.querySelector('#invite-email')"),false);
  assert.equal(await evaluate("[...document.querySelectorAll('button')].some(e=>e.textContent==='Register vehicle')"),true);
  await send('Fetch.enable',{patterns:[{urlPattern:origin+'/api/company-memberships/me*'}]});holdMemberships=true;
  await send('Page.reload');await pause(800);
  assert.ok(heldMemberships.length>0);
  assert.equal(await evaluate("document.body.innerText.includes('Company access unavailable') || document.body.innerText.includes('Post Freight') || document.body.innerText.includes('Register vehicle')"),false);
  holdMemberships=false;for(const requestId of heldMemberships.splice(0))await send('Fetch.continueRequest',{requestId});
  await wait("document.body.innerText.includes('FLEET MANAGER')");
  failMemberships=true;await send('Page.reload');await pause(400);await wait("document.body.innerText.includes('Unable to load company access')");
  failMemberships=false;await click('Retry company access');await wait("document.body.innerText.includes('FLEET MANAGER')");
  await fill('input[name=plateNumber]','PRESERVE-ON-FOCUS');await evaluate("window.dispatchEvent(new Event('focus'))");await pause(500);
  assert.equal(await evaluate("document.querySelector('input[name=plateNumber]').value"),'PRESERVE-ON-FOCUS');
  await send('Fetch.disable');
  await go(`/company/${f.XYZ.id}`);await wait("!!document.querySelector('[role=alert]')");assert.equal(await evaluate("document.body.innerText.includes('Onboarding XYZ')"),false);
  await login('dispatcher');await wait("document.body.innerText.includes('DISPATCHER')");
  assert.equal(await evaluate("[...document.querySelectorAll('button')].some(e=>['Register vehicle','Add driver','Assign vehicle'].includes(e.textContent))"),false);
  assert.equal(await evaluate("!!document.querySelector('#invite-email')"),false);
  await click('Dispatch');await wait("document.body.innerText.includes('Carrier Dispatch') && document.body.innerText.includes('Adama')");
  checks.push('Real Fleet Manager/Dispatcher resource UI and permissions; delayed membership gate; failure/retry; focus preserves form; other company denied');
 } else if(mode==='employees') {await accept('manager',true);await invite('dispatcher','DISPATCHER');await accept('dispatcher',false);await register('xyzOwner');await company('XYZ');await approve(f.XYZ.id,'XYZ');} else if(mode==='restart') {for(const name of ['manager','dispatcher']){await login(name);await wait(`document.body.innerText.includes('${f[name].role.replaceAll('_',' ')}')`);await send('Page.reload');await pause(400);await wait(`document.body.innerText.includes('${f[name].role.replaceAll('_',' ')}')`);}checks.push('Post-backend-restart browser login and refresh for both roles');}
 console.log(JSON.stringify({result:'PASS',mode,checks}));
} catch(e){console.log(JSON.stringify({result:'FAIL',mode,error:e.message,path:await evaluate('location.pathname'),text:(await evaluate('document.body.innerText')).slice(-1600)}));process.exitCode=1;}
finally{fs.writeFileSync(root+`onboarding-browser-${mode}.json`,JSON.stringify({checks,network},null,2));ws.close();}
