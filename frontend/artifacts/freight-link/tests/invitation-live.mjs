// Run against the local PostgreSQL fixtures and an isolated Chrome CDP session.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const fixturePath=process.env.AUDIT_FIXTURES || '../../../local-run-checks/final-fixtures.json';
const f=JSON.parse(fs.readFileSync(fixturePath,'utf8'));
const origin=process.env.WEB_ORIGIN || 'http://localhost:5174';
const targets=await(await fetch('http://127.0.0.1:9226/json')).json();
const ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise(r=>ws.onopen=r);
let serial=0;const pending=new Map();const network=[];let checks=0;
ws.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id);}if(m.method==='Network.responseReceived'){const r=m.params.response;const path=new URL(r.url).pathname;if(path.startsWith('/api/'))network.push({path,status:r.status});}};
const send=(method,params={})=>new Promise(r=>{pending.set(++serial,r);ws.send(JSON.stringify({id:serial,method,params}));});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert.ok(!r.result?.exceptionDetails,'Browser expression failed');return r.result?.result?.value;}
async function wait(expression,label){for(let i=0;i<80;i++){if(await evaluate(expression)){checks++;return;}await pause(200);}throw new Error(label);}
async function click(text){assert.ok(await evaluate(`(()=>{const e=[...document.querySelectorAll('button,a')].find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e)return false;e.click();return true;})()`),'Missing control '+text);await pause(300);}
async function fill(selector,value){await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));})()`);}
async function fresh(url){await send('Page.navigate',{url:'about:blank'});await pause(250);await send('Storage.clearDataForOrigin',{origin,storageTypes:'all'});await send('Page.navigate',{url});await pause(1000);}
await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
try {
  await fresh(origin+'/invitations/accept#token='+f.new.token);
  await wait("document.body.innerText.includes('Join Audit Carrier')",'New invitation preview');
  await wait("location.hash === ''",'Invitation token removed from address');
  await send('Page.reload');await pause(1200);await wait("document.body.innerText.includes('Join Audit Carrier') && document.body.innerText.includes('Create account')",'Refresh preserved invitation');
  await click('Create account');await wait("!!document.querySelector('#name')",'Register route');
  await fill('#name','Audit Fleet Manager');await fill('#phone','9'+String(Number(f.stamp)%10000000).padStart(8,'0'));
  await click('Next Step');await wait("!!document.querySelector('#email')",'Contact step');
  assert.equal(await evaluate("document.querySelector('#email').value"),f.new.email);assert.equal(await evaluate("document.querySelector('#email').readOnly"),true);checks+=2;
  await click('Next');await fill('#password',f.password);await click('Create Account');
  await wait("location.pathname==='/invitations/accept' && [...document.querySelectorAll('button')].some(e=>e.textContent==='Accept invitation')",'Registration returned to invitation');
  await click('Accept invitation');await wait(`location.pathname==='/company/${f.companyId}' && document.body.innerText.includes('FLEET MANAGER')`,'New fleet manager workspace');
  assert.equal(await evaluate("sessionStorage.getItem('freightlink_pending_invitation')"),null);checks++;
  await fresh(origin+'/invitations/accept?token='+encodeURIComponent(f.dispatcher.token));
  await wait("document.body.innerText.includes('DISPATCHER')",'Existing invitation preview');
  await click('Sign in');await wait("!!document.querySelector('input[type=password]')",'Login route');
  await fill('input:not([type=password])',f.dispatcher.email);await fill('input[type=password]',f.password);
  await evaluate("document.querySelector('form').requestSubmit()");
  await wait("location.pathname==='/invitations/accept' && [...document.querySelectorAll('button')].some(e=>e.textContent==='Accept invitation')",'Login preserved invitation');
  await click('Accept invitation');await wait(`location.pathname==='/company/${f.companyId}' && document.body.innerText.includes('DISPATCHER')`,'Dispatcher workspace');
  await fresh(origin+'/invitations/accept#token='+f.wrong_target.token);
  await wait("document.body.innerText.includes('Join Audit Carrier')",'Wrong-user preview');
  await click('Sign in');await wait("!!document.querySelector('input[type=password]')",'Wrong user login');
  await fill('input:not([type=password])',f.users.wrong.email);await fill('input[type=password]',f.password);await evaluate("document.querySelector('form').requestSubmit()");
  await wait("document.body.innerText.includes('Sign in using the invited email address.')",'Wrong user rejected');
  assert.equal(await evaluate("[...document.querySelectorAll('button')].some(e=>e.textContent==='Accept invitation')"),false);checks++;
  console.log(JSON.stringify({result:'PASS',checks,network}));
} catch(e) {console.log(JSON.stringify({result:'FAIL',step:e.message,checks,path:await evaluate('location.pathname'),text:await evaluate('document.body.innerText.slice(-1500)'),network}));process.exitCode=1;}
finally{ws.close();}
