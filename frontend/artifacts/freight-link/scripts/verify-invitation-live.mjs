// Real browser forms with actual Spring responses and PostgreSQL persistence.
// Loopback proxy forwarding avoids changing the user's Vite configuration.
const base = `http://127.0.0.1:${process.env.VERIFICATION_PORT}/api`;
const company = Number(process.env.VERIFICATION_COMPANY);
const owner = process.env.VERIFICATION_OWNER_TOKEN;
if (!owner || !company) throw new Error("Run through the isolated full HTTP verifier");
const password = "Browser-" + crypto.randomUUID();
const targets = await (await fetch("http://127.0.0.1:9224/json")).json();
const ws = new WebSocket(targets.find(t => t.type === "page").webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
let sequence = 0, failure, accepted = 0;
const pending = new Map();
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence; pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function api(path, body, token) {
  const response = await fetch(base + path, { method: "POST", headers: {
    "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {})
  }, body: JSON.stringify(body) });
  if (!response.ok) throw new Error("Live browser setup HTTP " + response.status);
  return response.json();
}
async function forward(event) {
  const url = new URL(event.request.url);
  const path = url.pathname.replace(/^\/api/, "");
  const headers = { "Content-Type": "application/json" };
  const authKey = Object.keys(event.request.headers).find(k => k.toLowerCase() === "authorization");
  if (authKey) headers.Authorization = event.request.headers[authKey];
  const response = await fetch(base + path + url.search, {
    method: event.request.method, headers, body: event.request.postData
  });
  const body = await response.text();
  if (path === "/company-invitations/accept" && response.ok) {
    const member = JSON.parse(body);
    if (member.carrierCompanyId !== company || member.role !== "DISPATCHER") throw new Error("Live membership scope mismatch");
    accepted++;
  }
  await send("Fetch.fulfillRequest", { requestId: event.requestId, responseCode: response.status,
    responseHeaders: [{ name: "Content-Type", value: "application/json" }], body: Buffer.from(body).toString("base64") });
}
ws.onmessage = ({ data }) => {
  const event = JSON.parse(data);
  if (event.id) {
    const handler = pending.get(event.id); pending.delete(event.id);
    if (event.error) handler.reject(new Error("Browser command failed")); else handler.resolve(event.result);
  } else if (event.method === "Fetch.requestPaused") {
    forward(event.params).catch(error => { failure = error; });
  }
};
async function evaluate(expression) {
  const result = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error("Live browser evaluation failed");
  return result.result.value;
}
async function until(expression) {
  for (let attempt = 0; attempt < 200; attempt++) {
    if (failure) throw failure;
    if (await evaluate(`document.body !== null && (${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  throw new Error("Live browser assertion timed out");
}
async function fill(values) {
  await evaluate(`(() => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    for (const [id, value] of ${JSON.stringify(values)}) {
      const input = document.getElementById(id);
      setter.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true }));
    }
  })()`);
}
try {
  await send("Runtime.enable"); await send("Page.enable");
  await send("Page.navigate", { url: "http://localhost:5173/" });
  await until("location.origin === 'http://localhost:5173'");
  await send("Fetch.enable", { patterns: [{ urlPattern: "*/api/*" }, { urlPattern: "*/auth/*" }] });
  for (const flow of ["existing", "new"]) {
    const email = "live-browser-" + flow + "@example.test";
    if (flow === "existing") await api("/auth/register", { fullName: "Browser Existing", email,
      phone: "+251934000001", password, role: "shipper" });
    await api(`/carrier-companies/${company}/team/invitations`, { email, role: "DISPATCHER" }, owner);
    const mail = await (await fetch("http://127.0.0.1:12526/", { headers: {
      "X-Test-Key": process.env.TEST_MAIL_CAPTURE_SECRET
    } })).json();
    const raw = mail.latest.token;
    await evaluate("localStorage.clear(); sessionStorage.clear()");
    await send("Page.navigate", { url: "about:blank" });
    await send("Page.navigate", { url: "http://localhost:5173/invitations/accept#token=" + raw });
    await until("document.body.innerText.includes('ABC Logistics')");
    if (!await evaluate("location.hash === '' && document.body.innerText.includes('DISPATCHER')")) throw new Error("Live preview mismatch");
    if (flow === "existing") {
      await evaluate("document.querySelector('a[href=\"/login?invitation=1\"]').click()");
      await until("!!document.querySelector('#password')");
      await fill([["email", email], ["password", password]]);
    } else {
      await evaluate("document.querySelector('a[href=\"/register?invitation=1\"]').click()");
      await until("!!document.querySelector('#name')");
      await fill([["name", "Browser New"], ["phone", "934000002"]]);
      await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent.includes('Next Step')).click()");
      await until(`document.querySelector('#email')?.value === ${JSON.stringify(email)}`);
      if (!await evaluate("document.querySelector('#email').readOnly")) throw new Error("Invited email was not preserved");
      await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Next').click()");
      await until("!!document.querySelector('#password')");
      await fill([["password", password]]);
    }
    await evaluate("document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))");
    await until("document.body.innerText.includes('Accept invitation')");
    await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent === 'Accept invitation').click()");
    await until("document.body.innerText.includes('You have joined ABC Logistics')");
    if (!await evaluate("sessionStorage.getItem('freightlink_pending_invitation') === null")) throw new Error("Consumed token retained");
    console.log("PASS live browser: " + flow + " account authenticates, accepts, and receives server-defined membership");
  }
  if (accepted !== 2 || failure) throw new Error("Live acceptance count mismatch");
  console.log("Live browser checks: 2 passed, 0 failed");
} finally {
  await send("Fetch.disable");
  await evaluate("localStorage.clear(); sessionStorage.clear()");
  ws.close();
}
