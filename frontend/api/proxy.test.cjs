const assert = require("node:assert/strict");
const { Readable, Writable } = require("node:stream");
const test = require("node:test");
const proxy = require("./[...path].js");

function createResponse() {
  const chunks = [];
  const response = new Writable({
    write(chunk, _encoding, callback) {
      chunks.push(Buffer.from(chunk));
      callback();
    },
  });
  response.headers = {};
  response.setHeader = (name, value) => {
    response.headers[name.toLowerCase()] = value;
  };
  response.end = (chunk) => {
    return Writable.prototype.end.call(response, chunk);
  };
  response.destroy = () => {
    response.finished = true;
  };
  response.bodyText = () => Buffer.concat(chunks).toString("utf8");
  return response;
}

function createRequest({ url = "/api/auth/login?next=home", method = "POST", headers = {}, body = "" } = {}) {
  const request = Readable.from(body ? [Buffer.from(body)] : []);
  request.url = url;
  request.method = method;
  request.headers = headers;
  return request;
}

async function invoke(request) {
  const response = createResponse();
  await proxy(request, response);
  if (!response.writableFinished) {
    await new Promise((resolve) => response.once("finish", resolve));
  }
  return response;
}

test("forwards path, query, method, body, and authorization without trusting client secret/IP headers", async () => {
  const originalFetch = global.fetch;
  const previousOrigin = process.env.SPRING_BACKEND_ORIGIN;
  const previousSecret = process.env.SPRING_PROXY_SECRET;
  process.env.SPRING_BACKEND_ORIGIN = "https://backend.example.test";
  process.env.SPRING_PROXY_SECRET = "s".repeat(32);

  let observed;
  global.fetch = async (url, options) => {
    let body = "";
    if (options.body) {
      for await (const chunk of options.body) body += chunk.toString("utf8");
    }
    observed = { url, options, body };
    return new Response("upstream response", {
      status: 201,
      headers: {
        "content-type": "application/json",
        "location": "https://backend.example.test/api/files/42",
        "x-result": "ok",
      },
    });
  };

  try {
    const response = await invoke(createRequest({
      headers: {
        authorization: "Bearer client-token",
        "content-type": "application/json",
        "x-forwarded-for": "203.0.113.9",
        "x-real-ip": "198.51.100.77",
        forwarded: "for=198.51.100.77",
        "x-vercel-forwarded-for": "203.0.113.18",
        "x-vercel-id": "iad1::request-id",
        "x-ethioload-proxy-secret": "attacker-value",
        "x-trusted-client-ip": "198.51.100.77",
      },
      body: '{"email":"person@example.test"}',
    }));

    assert.equal(observed.url, "https://backend.example.test/api/auth/login?next=home");
    assert.equal(observed.options.method, "POST");
    assert.equal(observed.body, '{"email":"person@example.test"}');
    assert.equal(observed.options.headers.get("authorization"), "Bearer client-token");
    assert.equal(observed.options.headers.get("x-ethioload-proxy-secret"), "s".repeat(32));
    assert.equal(observed.options.headers.get("x-trusted-client-ip"), "203.0.113.18");
    assert.equal(observed.options.headers.has("x-real-ip"), false);
    assert.equal(observed.options.headers.has("forwarded"), false);
    assert.equal(observed.options.headers.has("x-vercel-forwarded-for"), false);
    assert.equal(observed.options.headers.has("x-vercel-id"), false);
    assert.equal(observed.options.headers.has("x-forwarded-for"), false);
    assert.equal(response.statusCode, 201);
    assert.equal(response.headers["x-result"], "ok");
    assert.equal(response.headers.location, "/api/files/42");
    assert.equal(response.bodyText(), "upstream response");
  } finally {
    global.fetch = originalFetch;
    if (previousOrigin === undefined) delete process.env.SPRING_BACKEND_ORIGIN;
    else process.env.SPRING_BACKEND_ORIGIN = previousOrigin;
    if (previousSecret === undefined) delete process.env.SPRING_PROXY_SECRET;
    else process.env.SPRING_PROXY_SECRET = previousSecret;
  }
});

test("rejects missing client IP and duplicate API context paths", async () => {
  const previousOrigin = process.env.SPRING_BACKEND_ORIGIN;
  const previousSecret = process.env.SPRING_PROXY_SECRET;
  process.env.SPRING_BACKEND_ORIGIN = "https://backend.example.test";
  process.env.SPRING_PROXY_SECRET = "s".repeat(32);

  try {
    const responseMissingIp = await invoke(createRequest({ headers: {} }));
    const responseSpoofedHeaders = await invoke(createRequest({
      headers: {
        "x-forwarded-for": "198.51.100.77",
        "x-real-ip": "198.51.100.77",
        "x-ethioload-proxy-secret": "attacker-value",
        "x-trusted-client-ip": "198.51.100.77",
      },
    }));
    const responseDuplicatePath = await invoke(createRequest({
      url: "/api/api/auth/login",
      headers: {
        "x-vercel-id": "iad1::request-id",
        "x-vercel-forwarded-for": "203.0.113.9",
      },
    }));

    assert.equal(responseMissingIp.statusCode, 400);
    assert.equal(responseSpoofedHeaders.statusCode, 400);
    assert.equal(responseDuplicatePath.statusCode, 400);
  } finally {
    if (previousOrigin === undefined) delete process.env.SPRING_BACKEND_ORIGIN;
    else process.env.SPRING_BACKEND_ORIGIN = previousOrigin;
    if (previousSecret === undefined) delete process.env.SPRING_PROXY_SECRET;
    else process.env.SPRING_PROXY_SECRET = previousSecret;
  }
});

test("fails closed when proxy configuration is missing or malformed", async () => {
  const previousOrigin = process.env.SPRING_BACKEND_ORIGIN;
  const previousSecret = process.env.SPRING_PROXY_SECRET;
  delete process.env.SPRING_BACKEND_ORIGIN;
  delete process.env.SPRING_PROXY_SECRET;

  try {
    const response = await invoke(createRequest({
      headers: { "x-forwarded-for": "203.0.113.9" },
    }));
    assert.equal(response.statusCode, 503);
  } finally {
    if (previousOrigin === undefined) delete process.env.SPRING_BACKEND_ORIGIN;
    else process.env.SPRING_BACKEND_ORIGIN = previousOrigin;
    if (previousSecret === undefined) delete process.env.SPRING_PROXY_SECRET;
    else process.env.SPRING_PROXY_SECRET = previousSecret;
  }
});
