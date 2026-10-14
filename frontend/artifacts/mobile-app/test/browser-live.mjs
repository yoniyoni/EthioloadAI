// Built Flutter web app + real Spring backend, including browser secure storage.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const f=JSON.parse(fs.readFileSync('../../../local-run-checks/final-fixtures.json','utf8'));
const origin='http://localhost:8765';
const targets=await(await fetch('http://127.0.0.1:9226/json')).json();
const ws=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);
await new Promise(r=>ws.onopen=r);
let id=0;const pending=new Map();const network=[];
ws.onmessage=({data})=>{const m=JSON.parse(data);if(m.id){pending.get(m.id)?.(m);pending.delete(m.id);}if(m.method==='Network.responseReceived'){const r=m.params.response;const path=new URL(r.url).pathname;if(path.startsWith('/api/'))network.push({path,status:r.status});}};
const send=(method,params={})=>new Promise(r=>{pending.set(++id,r);ws.send(JSON.stringify({id,method,params}));});
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function evaluate(expression){const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});assert.ok(!r.result?.exceptionDetails,'Browser expression failed');return r.result?.result?.value;}
async function wait(expression,label){for(let i=0;i<140;i++){await evaluate("document.querySelector('flt-semantics-placeholder')?.click()");if(await evaluate(expression))return;await pause(250);}throw new Error(label);}
async function click(text){assert.ok(await evaluate(`(()=>{const e=[...document.querySelectorAll('flt-semantics[role=button]')].find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e)return false;e.click();return true;})()`),'Missing control '+text);}
await send('Runtime.enable');await send('Page.enable');await send('Network.enable');
try {
  await send('Page.navigate',{url:'about:blank'});await pause(200);
  await send('Storage.clearDataForOrigin',{origin,storageTypes:'all'});
  await send('Page.navigate',{url:origin});
  await wait("document.body.innerText.includes('Already have an account?')",'Landing screen');
  await click('Already have an account?');
  await wait("document.querySelectorAll('input').length===2",'Login inputs');
  for(const [index,text] of [f.users.driver.email,f.password].entries()){
    const box=await evaluate(`(()=>{const r=document.querySelectorAll('input')[${index}].getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
    await send('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,...box});
    await send('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,...box});
    await pause(150);await send('Input.insertText',{text});await pause(150);
  }
  await click('Login');
  await wait("location.hash.includes('/driver') && !location.hash.includes('documents')",'Driver login navigation');
  const restoredBefore=network.filter(n=>n.path==='/api/auth/me'&&n.status===200).length;
  await send('Page.reload');
  for(let attempt=0;attempt<140 && network.filter(n=>n.path==='/api/auth/me'&&n.status===200).length===restoredBefore;attempt++) await pause(250);
  assert.ok(network.filter(n=>n.path==='/api/auth/me'&&n.status===200).length>restoredBefore,'Reload revalidated saved JWT');
  await wait("location.hash.includes('/driver') && !location.hash.includes('documents') && !!document.querySelector('flutter-view')",'Restored driver session');
  await evaluate("location.hash='/driver/assignments'");
  await wait("document.body.innerText.includes('Booking #') && document.body.innerText.includes('confirmed')",'Own dispatch inbox');
  await evaluate(`location.hash='/driver/assignments/${f.dispatchId}'`);
  await wait("document.body.innerText.includes('Open Trip')",'Assignment detail trip link');
  await click('Open Trip');
  await wait("document.body.innerText.includes('Delivery Completed')",'Persisted completed trip');
  assert.ok(network.some(n=>n.path==='/api/auth/login'&&n.status===200));
  assert.ok(network.some(n=>n.path==='/api/auth/me'&&n.status===200));
  console.log(JSON.stringify({result:'PASS',checks:['Flutter web login form','browser secure storage/session reload','own dispatch inbox','assignment trip navigation','completed delivery'],network}));
} catch(e){console.log(JSON.stringify({result:'FAIL',step:e.message,path:await evaluate('location.hash'),text:await evaluate('document.body.innerText.slice(-1800)'),network}));process.exitCode=1;}
finally{ws.close();}
