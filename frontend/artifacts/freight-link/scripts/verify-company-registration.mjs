// Runs against an isolated Chrome on port 9225. API responses are synthetic;
// no accounts, documents, review decisions or emails are created on the server.
import { writeFile } from 'node:fs/promises';
const origin = 'http://localhost:5173';
const targets = await (await fetch('http://127.0.0.1:9225/json')).json();
const target = targets.find(item => item.type === 'page');
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
let sequence = 0, failure, browserErrors = 0;
const pending = new Map();
const owner = { id: 41, name: 'UI Test Owner', email: 'owner@example.test', role: 'fleet_owner' };
const admin = { id: 99, name: 'UI Test Admin', email: 'admin@example.test', role: 'admin' };
let company;
let docs = [];
let invitationCalls = 0, documentUploads = 0;
const blank = { companyName: '', legalName: '', tinNumber: '', businessRegistrationNumber: '', phone: '', email: '', businessAddress: '', city: '' };
const complete = { companyName: 'UI Test Logistics', legalName: 'UI Test Logistics PLC', tinNumber: 'TIN-UI-123', businessRegistrationNumber: 'REG-UI-123', phone: '+251911223344', email: 'office@example.test', businessAddress: 'Bole Road', city: 'Addis Ababa' };
function send(method, params = {}) { return new Promise((resolve, reject) => { const id = ++sequence; pending.set(id, { resolve, reject }); ws.send(JSON.stringify({ id, method, params })); }); }
function check(condition, label) { if (!condition) throw new Error(label); }
async function intercept(params) {
  const { pathname: path } = new URL(params.request.url);
  const method = params.request.method;
  const body = () => JSON.parse(params.request.postData || '{}');
  let response = { content: [], data: [], notifications: [], unreadCount: 0, count: 0 }, status = 200;
  if (path === '/auth/register') {
    check(body().role === 'fleet_owner', 'Signup must retain fleet owner role');
    response = { token: 'synthetic-ui-session', user: owner };
  } else if (path === '/api/carrier-companies' && method === 'POST') {
    check(!('ownerId' in body()), 'Client must not choose the company owner');
    company = { ...blank, ...body(), id: 51, ownerId: owner.id, verificationStatus: 'DRAFT' };
    response = company; status = 201;
  } else if (path === '/api/carrier-companies/me' || path === '/api/admin/carrier-companies') {
    response = { content: company ? [company] : [], last: true, totalElements: company ? 1 : 0 };
  } else if (/\/documents\/\d+\/file$/.test(path)) {
    response = 'synthetic download';
  } else if (path === '/api/carrier-companies/51/documents') {
    if (method === 'POST') {
      const type = ['BUSINESS_LICENSE', 'BUSINESS_REGISTRATION', 'TIN_CERTIFICATE'][documentUploads++];
      check(params.request.headers['Content-Type']?.includes('multipart/form-data') || params.request.headers['content-type']?.includes('multipart/form-data'), 'Uploads must use multipart');
      const document = { id: docs.length + 1, documentType: type, originalName: 'company-document.pdf', verificationStatus: 'DRAFT' };
      docs = [...docs.filter(d => d.documentType !== type), document]; response = document; status = 201;
    } else response = docs;
  } else if (path === '/api/carrier-companies/51/submit') {
    check(docs.length === 3 && company.city, 'Incomplete application was submitted');
    company.verificationStatus = 'PENDING'; response = company;
  } else if (path === '/api/carrier-companies/51/team/invitations') {
    invitationCalls++; status = 503; response = { message: 'Invitation email could not be sent. Please try again later.' };
  } else if (/\/admin\/carrier-companies\/51\/(approve|request-information|reject)$/.test(path)) {
    const action = path.split('/').at(-1);
    if (action !== 'approve') check(!!body().message?.trim(), 'Admin review requires feedback');
    company.verificationStatus = { approve: 'APPROVED', 'request-information': 'MORE_INFORMATION_REQUIRED', reject: 'REJECTED' }[action];
    company.reviewMessage = action === 'approve' ? null : body().message; response = company;
  } else if (path === '/api/carrier-companies/51' || path === '/api/admin/carrier-companies/51') {
    if (method === 'PUT') { check(Object.keys(body()).length === 8, 'PUT must preserve every editable field'); company = { ...company, ...body() }; }
    response = company;
  }
  await send('Fetch.fulfillRequest', { requestId: params.requestId, responseCode: status, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body: Buffer.from(JSON.stringify(response)).toString('base64') });
}
ws.onmessage = ({ data }) => {
  const message = JSON.parse(data);
  if (message.id) { const entry = pending.get(message.id); pending.delete(message.id); message.error ? entry.reject(new Error(JSON.stringify(message.error))) : entry.resolve(message.result); }
  else if (message.method === 'Fetch.requestPaused') intercept(message.params).catch(error => { failure = error; });
  else if (message.method === 'Runtime.exceptionThrown') { browserErrors++; }
  else if (message.method === 'Page.javascriptDialogOpening') send('Page.handleJavaScriptDialog', { accept: true });
};
async function evaluate(expression) { const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }); if (result.exceptionDetails) throw new Error('Browser evaluation failed: ' + expression.slice(0, 100)); return result.result.value; }
async function until(expression) { for (let i = 0; i < 200; i++) { if (failure) throw failure; if (await evaluate(expression)) return; await new Promise(resolve => setTimeout(resolve, 200)); } throw new Error('Timed out: ' + expression); }
async function textVisible(text) { await until(`document.body.innerText.includes(${JSON.stringify(text)})`); }
async function navigate(path) { await send('Page.navigate', { url: origin + path }); }
async function fill(id, value) { await evaluate(`(() => { const field = document.getElementById(${JSON.stringify(id)}); const prototype = field.tagName === 'SELECT' ? HTMLSelectElement.prototype : field.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(prototype, 'value').set.call(field, ${JSON.stringify(value)}); field.dispatchEvent(new Event(field.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); })()`); }
async function click(text) { await evaluate(`[...document.querySelectorAll('button')].find(button => button.textContent.trim() === ${JSON.stringify(text)}).click()`); }
async function session(user) { await evaluate(`localStorage.setItem('freightlink_auth', JSON.stringify({ token: 'synthetic-ui-session', user: ${JSON.stringify(user)} })); sessionStorage.clear()`); }
async function screenshot(name) { const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }); await writeFile(new URL(`../../../../local-run-checks/${name}.png`, import.meta.url), Buffer.from(shot.data, 'base64')); }
try {
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });
  await navigate('/'); await until(`location.origin === ${JSON.stringify(origin)}`); await evaluate('localStorage.clear(); sessionStorage.clear()');
  await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/*' }, { urlPattern: '*/auth/*' }] });
  await navigate('/register'); await until("!!document.getElementById('role')");
  await fill('name', 'UI Test Owner'); await fill('phone', '911223344'); await fill('role', 'fleet_owner');
  await textVisible('First create your owner account'); await click('Next Step'); await until("!!document.getElementById('email')");
  await fill('email', owner.email); await click('Next'); await until("!!document.getElementById('password')");
  await fill('password', 'Synthetic-only-123!'); await click('Create Account');
  await textVisible('Register your fleet company'); check(await evaluate("location.pathname === '/fleet/companies/new'"), 'Fleet signup must open company application');
  console.log('PASS: Fleet Owner signup explains and opens company registration');
  await fill('companyName', complete.companyName); await click('Save and continue');
  await textVisible('2. Company documents'); check(company.verificationStatus === 'DRAFT', 'Company draft not created');
  check(await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent === 'Submit for review').disabled"), 'Incomplete submission should be disabled');
  await navigate('/fleet/companies'); await textVisible('Continue application');
  await evaluate("document.querySelector('a[href=\"/fleet/companies/51\"]').click()"); await textVisible('Edit company details');
  await click('Edit company details'); await until("!!document.getElementById('companyName')");
  for (const [key, value] of Object.entries(complete)) await fill(key, value);
  check(await evaluate("document.querySelector('form').checkValidity()"), 'Valid company details must pass browser validation');
  await screenshot('company-registration-desktop'); await click('Save and continue'); await textVisible('2. Company documents');
  console.log('PASS: draft save, resume, complete-details validation, and PUT field preservation');
  for (const type of ['BUSINESS_LICENSE', 'BUSINESS_REGISTRATION', 'TIN_CERTIFICATE']) {
    await until(`!document.getElementById('${type}').disabled`);
    await evaluate(`(() => { const input = document.getElementById('${type}'); const transfer = new DataTransfer(); transfer.items.add(new File(['%PDF-1.4 synthetic'], 'company-document.pdf', { type: 'application/pdf' })); input.files = transfer.files; input.dispatchEvent(new Event('change', { bubbles: true })); })()`);
    await until(`document.querySelectorAll('button').length > 0 && !document.getElementById('${type}').disabled`);
  }
  await until("![...document.querySelectorAll('button')].find(b => b.textContent === 'Submit for review').disabled");
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  check(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'Phone layout must not overflow');
  await screenshot('company-registration-phone'); await click('Submit for review'); await textVisible('Under review');
  check(await evaluate("!document.querySelector('input[type=file]')"), 'Pending documents should be locked');
  console.log('PASS: multipart uploads, phone layout, submission, and pending edit lock');
  await session(admin); await navigate('/admin/companies'); await textVisible(complete.companyName); await click(complete.companyName + 'Under review · Addis Ababa').catch(async () => { await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.includes('UI Test Logistics')).click()`); });
  await textVisible('Request changes'); check(await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent === 'Request changes').disabled"), 'Corrections require a review message');
  await fill('review-message', 'Please confirm the business address.'); await click('Request changes'); await textVisible('Review saved');
  await session(owner); await navigate('/fleet/companies/51'); await textVisible('Please confirm the business address.');
  await click('Edit company details'); await fill('businessAddress', 'Updated Bole Road'); await click('Save and continue'); await textVisible('Resubmit for review'); await click('Resubmit for review'); await textVisible('Under review');
  console.log('PASS: admin correction feedback, owner edits, and resubmission');
  await session(admin); await navigate('/admin/companies'); await textVisible(complete.companyName); await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.includes('UI Test Logistics')).click()`); await textVisible('Approve company'); await click('Approve company'); await textVisible('Review saved');
  await session(owner); await navigate('/fleet/companies/51'); await textVisible('Invite your team');
  await fill('invite-email', 'employee@example.test'); await click('Send invitation'); await textVisible('Invitation email could not be sent');
  check(invitationCalls === 1, 'Invitation must only be sent once after an explicit click');
  console.log('PASS: admin approval enables invitations; SMTP failure remains visible');
  company.verificationStatus = 'REJECTED'; company.reviewMessage = 'Invalid registration certificate.';
  await navigate('/fleet/companies/51'); await textVisible('cannot be edited'); check(await evaluate("!document.querySelector('input[type=file]') && !document.querySelector('#invite-email')"), 'Rejected applications must be locked');
  console.log('PASS: rejected applications show feedback and stay locked');
  check(browserErrors === 0, 'Unexpected browser runtime errors');
  if (failure) throw failure;
  console.log('Company browser checks: 6 passed; no real emails sent.');
} finally { await send('Fetch.disable'); ws.close(); }
