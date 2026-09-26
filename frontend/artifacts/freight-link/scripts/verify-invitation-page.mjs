// Browser-only integration checks using synthetic invitations and intercepted API responses.
// Start an isolated Chrome with --headless=new --remote-debugging-port=9224 first.
const targets = await (await fetch("http://127.0.0.1:9224/json")).json();
const target = targets.find(item => item.type === "page");
if (!target) throw new Error("No isolated browser page is available");
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
let sequence = 0;
const pending = new Map();
let invalidState = "";
let accepted = false;
let failure;
let browserErrors = 0;
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    pending.set(id, { resolve, reject });
    ws.send(JSON.stringify({ id, method, params }));
  });
}
async function intercept(params) {
  const path = new URL(params.request.url).pathname;
  let response;
  let status = 200;
  if (path.endsWith("/validate")) {
    status = invalidState ? 400 : 200;
    response = invalidState ? { code: invalidState, message: "Unavailable" } :
      { valid: true, companyName: "Browser Test Logistics", role: "DISPATCHER", email: "browser@example.test", expiresAt: "2026-12-01T12:00:00" };
  } else if (path.endsWith("/accept")) {
    const body = JSON.parse(params.request.postData);
    if (Object.keys(body).join(",") !== "token") failure = new Error("Acceptance submitted client-chosen fields");
    accepted = true;
    response = { id: 1, carrierCompanyId: 2, role: "DISPATCHER", status: "ACTIVE" };
  } else if (path.endsWith("/auth/login") || path.endsWith("/auth/register")) {
    if (path.endsWith("/auth/register") && JSON.parse(params.request.postData).email !== "browser@example.test") {
      failure = new Error("Registration did not preserve invited email");
    }
    if (path.endsWith("/auth/register") && JSON.parse(params.request.postData).phone !== "+251933123456") {
      failure = new Error("Registration did not include the displayed phone country code");
    }
    response = { token: "synthetic-browser-session", user: { id: 5, full_name: "Browser Employee", email: "browser@example.test", role: "SHIPPER" } };
  } else {
    response = { notifications: [], data: [], unreadCount: 0, unread_count: 0, count: 0 };
  }
  await send("Fetch.fulfillRequest", { requestId: params.requestId, responseCode: status,
    responseHeaders: [{ name: "Content-Type", value: "application/json" }],
    body: Buffer.from(JSON.stringify(response)).toString("base64") });
}
ws.onmessage = ({ data }) => {
  const message = JSON.parse(data);
  if (message.id) {
    const handler = pending.get(message.id);
    pending.delete(message.id);
    if (message.error) handler.reject(new Error("Browser command failed"));
    else handler.resolve(message.result);
  } else if (message.method === "Fetch.requestPaused") {
    intercept(message.params).catch(error => { failure = error; });
  } else if (message.method === "Runtime.exceptionThrown") {
    browserErrors++;
  }
};
async function evaluate(expression) {
  const result = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error("Browser evaluation failed");
  return result.result.value;
}
async function until(expression) {
  for (let attempt = 0; attempt < 200; attempt++) {
    if (failure) throw failure;
    if (await evaluate(`document.body !== null && (${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  throw new Error("Browser assertion timed out");
}
try {
  await send("Runtime.enable");
  await send("Page.enable");
  await send("Page.navigate", { url: "http://localhost:5173/" });
  await until("location.origin === 'http://localhost:5173'");
  await evaluate("localStorage.clear(); sessionStorage.clear()");
  await send("Fetch.enable", { patterns: [
    { urlPattern: "*/api/*" }, { urlPattern: "*auth/login*" }, { urlPattern: "*auth/register*" }
  ] });
  await send("Page.navigate", { url: "http://localhost:5173/invitations/accept" });
  await until("document.body.innerText.includes('Open the invitation link')");
  console.log("PASS: missing-token page explains how to continue");

  const raw = "A".repeat(43); // synthetic and never logged
  await send("Page.navigate", { url: "about:blank" });
  await send("Page.navigate", { url: "http://localhost:5173/invitations/accept#token=" + raw });
  await until("document.body.innerText.includes('Browser Test Logistics')");
  if (!await evaluate("location.hash === '' && !!sessionStorage.getItem('freightlink_pending_invitation')")) {
    throw new Error("Token was not removed from the address bar");
  }
  await evaluate("document.querySelector('a[href=\"/login?invitation=1\"]').click()");
  await until("!!document.querySelector('#password')");
  await evaluate(`(() => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    for (const [id, value] of [['email', 'browser@example.test'], ['password', 'browser-only-password']]) {
      const input = document.getElementById(id);
      setter.call(input, value);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
  })()`);
  await evaluate("document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))");
  await until("document.body.innerText.includes('Accept invitation')");
  await evaluate("[...document.querySelectorAll('button')].find(button => button.textContent === 'Accept invitation').click()");
  await until("document.body.innerText.includes('You have joined Browser Test Logistics')");
  if (!accepted || !await evaluate("sessionStorage.getItem('freightlink_pending_invitation') === null")) {
    throw new Error("Acceptance or token cleanup failed");
  }
  console.log("PASS: email link, token stripping, login return, explicit acceptance, and token cleanup");

  for (const state of ["INVITATION_EXPIRED", "INVITATION_REVOKED", "INVITATION_ACCEPTED", "INVALID_INVITATION_TOKEN"]) {
    invalidState = state;
    await evaluate("localStorage.clear(); sessionStorage.clear()");
    await send("Page.navigate", { url: "about:blank" });
    await send("Page.navigate", { url: "http://localhost:5173/invitations/accept#token=" + "B".repeat(43) });
    await until("document.body.innerText.includes('unavailable or expired')");
    if (!await evaluate("sessionStorage.getItem('freightlink_pending_invitation') === null && ![...document.querySelectorAll('button')].some(b => b.textContent === 'Accept invitation')")) {
      throw new Error("Unavailable invitation retained acceptance or token");
    }
    console.log("PASS: " + state + " shows a safe message and clears the token");
  }
  invalidState = "";
  accepted = false;
  await send("Page.navigate", { url: "about:blank" });
  await send("Page.navigate", { url: "http://localhost:5173/invitations/accept#token=" + "C".repeat(43) });
  await until("document.body.innerText.includes('Browser Test Logistics')");
  if (!await evaluate("document.body.innerText.includes('DISPATCHER') && document.querySelectorAll('main input, main select').length === 0")) {
    throw new Error("Invitation role/company is missing or editable");
  }
  await evaluate("document.querySelector('a[href=\"/register?invitation=1\"]').click()");
  await until("!!document.querySelector('#name')");
  await evaluate(`(() => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    for (const [id, value] of [['name', 'Browser Employee'], ['phone', '933123456']]) {
      const input = document.getElementById(id);
      setter.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true }));
    }
  })()`);
  await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent.includes('Next Step')).click()");
  await until("document.querySelector('#email')?.value === 'browser@example.test'");
  if (!await evaluate("document.querySelector('#email').readOnly")) throw new Error("Invited email is editable");
  await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Next').click()");
  await until("!!document.querySelector('#password')");
  await evaluate(`(() => {
    const input = document.getElementById('password');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, 'browser-only-password');
    input.dispatchEvent(new Event('input', { bubbles: true }));
  })()`);
  await evaluate("document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))");
  await until("document.body.innerText.includes('Accept invitation')");
  await evaluate("[...document.querySelectorAll('button')].find(b => b.textContent === 'Accept invitation').click()");
  await until("document.body.innerText.includes('You have joined Browser Test Logistics')");
  if (!accepted || !await evaluate("sessionStorage.getItem('freightlink_pending_invitation') === null")) throw new Error("Registration acceptance failed");
  console.log("PASS: registration preserves invited email, returns to invitation, and accepts fixed role/company");
  if (browserErrors !== 0) throw new Error("Unexpected browser runtime errors");
  if (failure) throw failure;
  console.log("Browser checks: 7 passed, 0 failed");
} finally {
  await send("Fetch.disable");
  ws.close();
}
