// Real browser checks against the fixtures produced by the Flutter live test.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const f=JSON.parse(fs.readFileSync(process.env.AUDIT_FIXTURES || '../../../local-run-checks/final-fixtures.json','utf8'));
const origin=process.env.WEB_ORIGIN || 'http://localhost:5174';
const targets=await(await fetch('http://127.0.0.1:9226/json')).json();
const ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise(r=>ws.onopen=r);
let id=0;const pending=new Map();const network=[];
ws.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id);}if(m.method==='Network.responseReceived'){const r=m.params.response;const path=new URL(r.url).pathname;if(path.startsWith('/api/'))network.push({path,status:r.status});}};
const send=(method,params={})=>new Promise(r=>{pending.set(++id,r);ws.send(JSON.stringify({id,method,params}));});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert.ok(!r.result?.exceptionDetails,'Browser expression failed');return r.result?.result?.value;}
async function wait(expression,label){for(let i=0;i<100;i++){if(await evaluate(expression))return;await pause(200);}throw new Error(label);}
async function click(text){assert.ok(await evaluate(`(()=>{const e=[...document.querySelectorAll('button,a')].find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e)return false;e.click();return true;})()`),'Missing control '+text);}
async function fill(selector,value){await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));})()`);}
async function login(name){
  await send('Page.navigate',{url:'about:blank'});await pause(200);
  await send('Storage.clearDataForOrigin',{origin,storageTypes:'all'});
  await send('Page.navigate',{url:origin+'/login'});
  await wait("!!document.querySelector('input[type=password]')",'Login form');
  await fill('input:not([type=password])',f.users[name].email);await fill('input[type=password]',f.password);
  await evaluate("document.querySelector('form').requestSubmit()");
  await wait("location.pathname!='/login'",'Authenticated dashboard');
}
await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
try {
  await login('shipper');
  await send('Page.navigate',{url:origin+`/tracking/${f.freightId}`});
  await wait("document.body.innerText.includes('Review proof of delivery')",'Tracking links to submitted proof');
  await click('Review proof of delivery');
  await wait("document.body.innerText.includes('Verify Delivery')",'Shipper verification controls');
  await click('View / Download Proof File');await pause(1200);
  assert.ok(network.some(n=>n.path===`/api/proof-of-delivery/${f.proofId}/download`&&n.status===200),'Authenticated proof download');
  await click('Verify Delivery');
  await wait("document.body.innerText.includes('VERIFIED') || document.body.innerText.includes('Delivery Verified')",'Delivery verification');
  await login('owner');
  await send('Page.navigate',{url:origin+`/company/${f.companyId}`});
  await wait("document.body.innerText.includes('Settlements')",'Company settlement navigation');
  await click('Settlements');
  await wait("document.body.innerText.includes('PENDING') && document.body.innerText.includes('10,500')",'Settlement gross and status');
  console.log(JSON.stringify({result:'PASS',checks:['shipper login','tracking-to-proof','authenticated download','verification','owner login','company-to-settlement','settlement amount'],network}));
} catch(e){console.log(JSON.stringify({result:'FAIL',step:e.message,path:await evaluate('location.pathname'),text:await evaluate('document.body.innerText.slice(-1800)'),network}));process.exitCode=1;}
finally{ws.close();}
