// Browser regression with isolated API responses; does not consume real invitations.
import assert from 'node:assert/strict';
const origin = 'http://localhost:5174';
const targets = await (await fetch('http://127.0.0.1:9231/json')).json();
const ws = new WebSocket(targets.find(t => t.type === 'page').webSocketDebuggerUrl);
await new Promise(resolve => ws.onopen = resolve);
let sequence = 0;
const pending = new Map();
const requests = [];
let joined = false;
let expired = false;
let accountExists = true;
let staleRequestId;
const admin = {id: 1, name: 'Admin', email: 'admin@example.test', role: 'admin'};
const invited = {id: 2, name: 'Invited Manager', email: 'manager@example.test', role: 'shipper'};
const member = {id: 3, carrierCompanyId: 42, role: 'FLEET_MANAGER', status: 'ACTIVE'};
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence; pending.set(id, {resolve, reject});
    ws.send(JSON.stringify({id, method, params}));
  });
}
ws.onmessage = async ({data}) => {
  const message = JSON.parse(data);
  if (message.id) {
    const task = pending.get(message.id); pending.delete(message.id);
    if (message.error) task.reject(new Error(message.error.message)); else task.resolve(message.result);
  }
  if (message.method !== 'Fetch.requestPaused') return;
  const {requestId, request} = message.params;
  const path = new URL(request.url).pathname;
  if (path.endsWith('/stale-session-check')) { staleRequestId = requestId; return; }
  requests.push({path, ...request});
  let body = {}, status = 200;
  if (path.endsWith('/auth/me')) body = admin;
  else if (path.endsWith('/validate')) {
    body = expired ? {message: 'Invitation has expired'} : {companyName: 'Test Carrier', email: invited.email, role: member.role, expiresAt: '2099-01-01T00:00:00Z', accountExists};
    if (expired) status = 400;
  } else if (path.endsWith('/auth/login')) {
    const credentials = JSON.parse(request.postData);
    if (credentials.identifier !== invited.email || credentials.password !== 'correct-password') {status = 401; body = {message: 'Invalid credentials'};}
    else body = {token: 'invited-session', user: invited};
  } else if (path.endsWith('/company-invitations/register')) {
    const payload = JSON.parse(request.postData);
    assert.equal(payload.fullName, 'New Employee');
    assert.equal(payload.phone, '+251911234567');
    assert.equal(payload.password, 'new-password');
    assert.equal(payload.role, undefined);
    assert.equal(payload.email, undefined);
    assert.equal(Object.keys(request.headers).some(key => key.toLowerCase() === 'authorization'), false);
    joined = true;
    body = {session: {token: 'invited-session', user: invited}, membership: member};
  } else if (path.endsWith('/accept')) {
    assert.equal(Object.entries(request.headers).find(([key]) => key.toLowerCase() === 'authorization')?.[1], 'Bearer invited-session');
    joined = true; body = member;
  } else if (path.endsWith('/company-memberships/me')) body = {content: joined ? [member] : []};
  else if (path.endsWith('/carrier-companies/42')) body = {id: 42, companyName: 'Test Carrier', verificationStatus: 'APPROVED'};
  else body = {content: []};
  await send('Fetch.fulfillRequest', {requestId, responseCode: status, responseHeaders: [{name: 'Content-Type', value: 'application/json'}], body: Buffer.from(JSON.stringify(body)).toString('base64')});
};
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
  assert.ok(!result.exceptionDetails, 'Browser evaluation failed');
  return result.result.value;
}
async function wait(expression) {
  for (let i = 0; i < 100; i++) {
    if (await evaluate(expression)) return;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Timed out: ' + expression);
}
async function submit(password) {
  await evaluate(`(() => { const input = document.querySelector('#invitation-password'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(password)}); input.dispatchEvent(new Event('input', {bubbles:true})); })()`);
  await evaluate("document.querySelector('main form').requestSubmit()");
}
try {
  await send('Runtime.enable'); await send('Page.enable');
  await send('Fetch.enable', {patterns: [{urlPattern: origin + '/api/*'}]});
  await send('Page.navigate', {url: origin});
  await wait("location.origin === 'http://localhost:5174'");
  await evaluate(`localStorage.setItem('freightlink_auth', ${JSON.stringify(JSON.stringify({token: 'admin-session', user: admin}))})`);
  await send('Page.navigate', {url: origin + '/invitations/accept#token=test-invitation'});
  await wait("!!document.querySelector('#invitation-password')");
  assert.equal(await evaluate("document.querySelector('#invited-email').value"), invited.email);
  assert.equal(await evaluate("document.querySelector('#invited-email').readOnly"), true);
  assert.equal(await evaluate("location.hash"), '');
  await submit('wrong-password');
  await wait("document.querySelector('[role=alert]')?.textContent.includes('Invalid credentials')");
  assert.equal(joined, false);
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('freightlink_auth')).token"), 'admin-session');
  await submit('correct-password');
  await wait("location.pathname === '/company/42' && document.body.innerText.includes('FLEET MANAGER')");
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('freightlink_auth')).user.email"), invited.email);
  assert.equal(await evaluate("sessionStorage.getItem('freightlink_pending_invitation')"), null);
  assert.equal(requests.filter(r => r.path.endsWith('/accept')).length, 1);
  await evaluate("localStorage.clear(); sessionStorage.clear()");
  await send('Page.navigate', {url: origin + '/invitations/accept#token=second-invitation'});
  await wait("!!document.querySelector('#invitation-password')");
  accountExists = false;
  joined = false;
  await send('Page.navigate', {url: origin + '/invitations/accept#token=new-account-invitation'});
  await wait("!!document.querySelector('#invitation-name')");
  async function fill(selector, value) {
    await evaluate(`(() => {const input = document.querySelector(${JSON.stringify(selector)}); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event('input', {bubbles:true}));})()`);
  }
  await fill('#invitation-name', 'New Employee');
  await fill('#invitation-phone', '+251911234567');
  await fill('#invitation-confirm-password', 'different-password');
  await submit('new-password');
  await wait("document.body.innerText.includes('Passwords do not match')");
  assert.equal(joined, false);
  await fill('#invitation-confirm-password', 'new-password');
  await submit('new-password');
  await wait("location.pathname === '/company/42' && document.body.innerText.includes('FLEET MANAGER')");
  assert.equal(requests.filter(r => r.path.endsWith('/company-invitations/register')).length, 1);
  await evaluate("import('/src/lib/api.ts').then(({api}) => {window.staleCheck = api.get('/stale-session-check').catch(() => 'denied');})");
  for (let i=0; i<50 && !staleRequestId; i++) await new Promise(resolve => setTimeout(resolve, 100));
  assert.ok(staleRequestId);
  await evaluate("localStorage.setItem('freightlink_auth', JSON.stringify({...JSON.parse(localStorage.getItem('freightlink_auth')),token:'newer-session'}))");
  await send('Fetch.fulfillRequest', {requestId:staleRequestId,responseCode:401,body:Buffer.from('{}').toString('base64')});
  await evaluate('window.staleCheck');
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('freightlink_auth')).token"), 'newer-session');
  assert.equal(await evaluate('location.pathname'), '/company/42');
  expired = true;
  await send('Page.navigate', {url: origin + '/invitations/accept#token=expired-invitation'});
  await wait("document.body.innerText.includes('Invitation has expired')");
  assert.equal(await evaluate("document.querySelector('#invitation-password')"), null);
  console.log('PASS: existing-account switch, invalid-password retry, inline registration, password confirmation, automatic company dashboard, fixed invitation identity/role, stale-session 401 isolation, expired invitation.');
} finally { ws.close(); }
