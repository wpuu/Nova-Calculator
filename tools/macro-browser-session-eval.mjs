import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync('prototypes/nova-macro-mv3/ai-review-client.js', 'utf8');
const NOW = 1_800_000_000_000;
const SENDER = { url: 'chrome-extension://nova-test-extension/popup.html' };

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function createHarness({ gatewayOrigin = '', oauth2 = null } = {}) {
  const sessionData = {};
  const identityCalls = [];
  const removedTokens = [];
  const networkCalls = [];
  let googleToken = 'google-access-token-never-persist';
  let networkResponder = async () => new Response(JSON.stringify({
    status: 'SUCCESS',
    sessionToken: 'nova1.test.payload.signature-0123456789',
    expiresAtEpochMs: NOW + 30 * 60 * 1000,
  }), { status: 200, headers: { 'content-type': 'application/json' } });

  const chrome = {
    storage: {
      session: {
        async get(key) { return { [key]: clone(sessionData[key]) }; },
        async set(values) { Object.assign(sessionData, clone(values)); },
      },
    },
    runtime: {
      id: 'nova-test-extension',
      getManifest() { return { oauth2: clone(oauth2) }; },
    },
    identity: {
      async getAuthToken(details) {
        identityCalls.push(clone(details));
        return { token: googleToken };
      },
      async removeCachedAuthToken({ token }) {
        removedTokens.push(token);
      },
    },
  };

  const context = vm.createContext({
    console,
    structuredClone,
    URL,
    Date,
    setTimeout,
    clearTimeout,
    Promise,
    crypto: { randomUUID: () => 'uuid-test' },
  });
  vm.runInContext(source, context, { filename: 'ai-review-client.js' });

  const controller = context.NovaMacroAiReview.createController({
    chrome,
    runtimeConfig: { gatewayOrigin },
    now: () => NOW,
    newRequestId: () => 'request-test',
    getSession: async () => ({ mode: 'IDLE' }),
    reviewFromSession: () => null,
    selectRepairCandidate: async () => ({}),
    abstainRepair: async () => ({}),
    fetchImpl: async (url, options) => {
      networkCalls.push({
        url: String(url),
        options: clone({
          method: options?.method,
          headers: options?.headers,
          body: options?.body,
          cache: options?.cache,
        }),
      });
      return networkResponder(url, options);
    },
  });

  return {
    chrome,
    controller,
    sessionData,
    identityCalls,
    removedTokens,
    networkCalls,
    setGoogleToken(value) { googleToken = value; },
    setNetworkResponder(value) { networkResponder = value; },
  };
}

const tests = [];
function test(name, fn) { tests.push({ name, fn }); }

test('missing fixed Gateway origin fails before Google OAuth', async () => {
  const h = createHarness({
    oauth2: { client_id: 'client.apps.googleusercontent.com', scopes: ['openid'] },
  });
  const result = await h.controller.connectGoogle({}, SENDER);
  assert.deepEqual(result, { ok: false, error: 'GATEWAY_ORIGIN_NOT_CONFIGURED' });
  assert.equal(h.identityCalls.length, 0);
  assert.equal(h.networkCalls.length, 0);
});

test('missing or over-scoped OAuth manifest fails before Google OAuth', async () => {
  for (const oauth2 of [
    null,
    { client_id: '', scopes: ['openid'] },
    { client_id: 'client.apps.googleusercontent.com', scopes: ['openid', 'email'] },
    { client_id: 'client.apps.googleusercontent.com', scopes: ['email'] },
  ]) {
    const h = createHarness({ gatewayOrigin: 'https://gateway.example', oauth2 });
    const result = await h.controller.connectGoogle({}, SENDER);
    assert.equal(result.ok, false);
    assert.equal(result.error, 'GOOGLE_OAUTH_NOT_CONFIGURED');
    assert.equal(h.identityCalls.length, 0);
    assert.equal(h.networkCalls.length, 0);
  }
});

test('explicit connect exchanges Google token once and persists only Nova session', async () => {
  const h = createHarness({
    gatewayOrigin: 'https://gateway.example/',
    oauth2: { client_id: 'client.apps.googleusercontent.com', scopes: ['openid'] },
  });
  const result = await h.controller.connectGoogle({}, SENDER);
  assert.equal(result.ok, true);
  assert.equal(result.configured, true);
  assert.equal(result.endpoint, 'https://gateway.example/api/macro-candidate-review');
  assert.equal(h.identityCalls.length, 1);
  assert.deepEqual(h.identityCalls[0], { interactive: true, scopes: ['openid'] });
  assert.equal(h.networkCalls.length, 1);
  assert.equal(h.networkCalls[0].url, 'https://gateway.example/api/browser-session');
  const requestBody = JSON.parse(h.networkCalls[0].options.body);
  assert.deepEqual(requestBody, { accessToken: 'google-access-token-never-persist' });
  const persisted = JSON.stringify(h.sessionData);
  assert.equal(persisted.includes('google-access-token-never-persist'), false);
  assert.equal(persisted.includes('nova1.test.payload.signature-0123456789'), true);
  assert.equal(h.removedTokens.length, 0);
});

test('rejected Google identity clears Chrome token cache and never stores Nova session', async () => {
  const h = createHarness({
    gatewayOrigin: 'https://gateway.example',
    oauth2: { client_id: 'client.apps.googleusercontent.com', scopes: ['openid'] },
  });
  h.setNetworkResponder(async () => new Response(JSON.stringify({
    status: 'IDENTITY_REJECTED', sessionToken: '', expiresAtEpochMs: 0,
  }), { status: 403, headers: { 'content-type': 'application/json' } }));
  const result = await h.controller.connectGoogle({}, SENDER);
  assert.equal(result.ok, false);
  assert.equal(result.error, 'BROWSER_SESSION_IDENTITY_REJECTED');
  assert.deepEqual(h.removedTokens, ['google-access-token-never-persist']);
  assert.equal(JSON.stringify(h.sessionData).includes('novaMacroGatewaySession'), false);
});

test('content/tab sender cannot trigger interactive OAuth', async () => {
  const h = createHarness({
    gatewayOrigin: 'https://gateway.example',
    oauth2: { client_id: 'client.apps.googleusercontent.com', scopes: ['openid'] },
  });
  const result = await h.controller.connectGoogle({}, { tab: { id: 5 }, url: 'https://shop.example/' });
  assert.deepEqual(result, { ok: false, error: 'UNTRUSTED_BROWSER_SESSION_SENDER' });
  assert.equal(h.identityCalls.length, 0);
  assert.equal(h.networkCalls.length, 0);
});

test('global single-flight prevents simultaneous browser bootstrap requests', async () => {
  const h = createHarness({
    gatewayOrigin: 'https://gateway.example',
    oauth2: { client_id: 'client.apps.googleusercontent.com', scopes: ['openid'] },
  });
  let release;
  h.setNetworkResponder(() => new Promise((resolve) => { release = resolve; }));
  const first = h.controller.connectGoogle({}, SENDER);
  while (h.networkCalls.length < 1) await new Promise((resolve) => setTimeout(resolve, 1));
  const second = await h.controller.connectGoogle({}, SENDER);
  assert.deepEqual(second, { ok: false, error: 'AI_REVIEW_IN_FLIGHT' });
  assert.equal(h.identityCalls.length, 1);
  assert.equal(h.networkCalls.length, 1);
  release(new Response(JSON.stringify({
    status: 'SUCCESS',
    sessionToken: 'nova1.test.payload.signature-0123456789',
    expiresAtEpochMs: NOW + 10 * 60 * 1000,
  }), { status: 200, headers: { 'content-type': 'application/json' } }));
  assert.equal((await first).ok, true);
});

let passed = 0;
for (const { name, fn } of tests) {
  try {
    await fn();
    passed += 1;
    console.log(`PASS\t${name}`);
  } catch (error) {
    console.error(`FAIL\t${name}\n${error.stack || error}`);
  }
}
console.log(`RESULT ${passed}/${tests.length}`);
if (passed !== tests.length) process.exitCode = 1;
