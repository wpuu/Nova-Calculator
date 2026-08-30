import test from 'node:test';
import assert from 'node:assert/strict';

import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';
import { createLazyVercelHealthRoute, createLazyVercelRoute } from '../src/vercel-entrypoint.mjs';

const SIGNING = 'production-signing-secret-0123456789-abcdef';
const SUBJECT = 'production-subject-secret-0123456789-abcdef';

function productionEnv(overrides = {}) {
  return {
    VERCEL_ENV: 'preview',
    NOVA_ANDROID_PACKAGE_NAME: 'com.wpuu.novacalculator',
    NOVA_PROVIDER_BASE_URL: 'https://provider.example/v1',
    NOVA_PROVIDER_MODEL: 'runtime-model',
    NOVA_PROVIDER_KEYS: 'provider-key-a,provider-key-b',
    NOVA_PROVIDER_RPM_PER_KEY: '20',
    NOVA_SESSION_SIGNING_SECRETS: SIGNING,
    NOVA_SESSION_SUBJECT_SECRET: SUBJECT,
    NOVA_AI_FREE_DAILY_LIMIT: '3',
    NOVA_AI_FREE_RPM_LIMIT: '1',
    NOVA_AI_PRO_DAILY_LIMIT: '10',
    NOVA_AI_PRO_RPM_LIMIT: '3',
    NOVA_AI_PLUS_DAILY_LIMIT: '200',
    NOVA_AI_PLUS_RPM_LIMIT: '10',
    ...overrides,
  };
}

function proofVerifier() {
  return {
    async verify({ installationId, proof }) {
      return proof === 'proof-ok'
        ? { accepted: true, bindingId: `play:${installationId}` }
        : { accepted: false };
    },
  };
}

test('production composition shares Redis quota and provider capacity while keeping provider secrets out of Redis', async () => {
  const redisCalls = [];
  const providerCalls = [];
  const redisEvalClient = {
    async eval(script, keys, args) {
      redisCalls.push({ script, keys, args });
      if (script.includes('local reservationKey = KEYS[3]')) return ['ALLOWED', 2];
      if (script.includes('local bestIndex = 0')) return ['key-1', 15];
      if (script.includes("return redis.call('DEL', KEYS[1])")) return 1;
      throw new Error('unexpected Redis script in integration test');
    },
  };

  const app = createProductionNovaGatewayApplication({
    env: productionEnv(),
    now: () => 1_800_000_000_000,
    redisEvalClient,
    installationProofVerifier: proofVerifier(),
    fetchImpl: async (url, options) => {
      providerCalls.push({ url, options });
      return new Response(JSON.stringify({
        choices: [{ message: { content: '因为 7 × 6 = 42。' } }],
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    },
  });

  const sessionResponse = await app.anonymousSessionHandler(new Request('https://nova.example/api/session', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ installationId: 'install-prod-1', proof: 'proof-ok' }),
  }));
  assert.equal(sessionResponse.status, 200);
  const session = await sessionResponse.json();
  assert.equal(session.status, 'SUCCESS');
  assert.ok(session.sessionToken);

  const aiResponse = await app.aiHandler(new Request('https://nova.example/api/ai', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${session.sessionToken}`,
    },
    body: JSON.stringify({
      requestId: 'req-prod-1',
      operation: 'EXPLAIN_CALCULATION',
      expression: '7*6',
      deterministicResult: '42',
      localeTag: 'zh-CN',
    }),
  }));
  assert.equal(aiResponse.status, 200);
  const ai = await aiResponse.json();
  assert.equal(ai.status, 'SUCCESS');
  assert.equal(ai.answer, '因为 7 × 6 = 42。');

  assert.equal(providerCalls.length, 1);
  assert.equal(providerCalls[0].url, 'https://provider.example/v1/chat/completions');
  assert.equal(providerCalls[0].options.headers.authorization, 'Bearer provider-key-a');
  const redisSerialized = JSON.stringify(redisCalls);
  assert.equal(redisSerialized.includes('provider-key-a'), false);
  assert.equal(redisSerialized.includes('provider-key-b'), false);
  assert.ok(redisCalls.some((call) => call.script.includes('local reservationKey = KEYS[3]')));
  assert.ok(redisCalls.some((call) => call.script.includes('local bestIndex = 0')));
  assert.equal(app.safeSummary.sharedQuotaStore, true);
  assert.equal(app.safeSummary.sharedProviderCapacity, true);
  assert.equal(app.safeSummary.androidPackageName, 'com.wpuu.novacalculator');
  assert.equal(JSON.stringify(app.safeSummary).includes('provider.example'), false);
  assert.equal(JSON.stringify(app.safeSummary).includes('provider-key-a'), false);
});

test('production Vercel deployment refuses development Android identity', () => {
  assert.throws(
    () => createProductionNovaGatewayApplication({
      env: productionEnv({
        VERCEL_ENV: 'production',
        NOVA_ANDROID_PACKAGE_NAME: 'com.wpuu.novacalculator.dev',
      }),
      quotaStore: { reserve() {}, commit() {}, release() {} },
      keyPoolFactory: () => ({
        lease() {}, reportSuccess() {}, reportRateLimit() {}, reportFailure() {}, setEnabled() {},
      }),
      installationProofVerifier: proofVerifier(),
    }),
    /refuses a \.dev Android package/,
  );
});

test('lazy Vercel route constructs once and hides initialization errors behind fixed 503', async () => {
  let builds = 0;
  const route = createLazyVercelRoute({
    createApplication() {
      builds += 1;
      return { handler: async () => new Response('ok', { status: 200 }) };
    },
    selectHandler: (app) => app.handler,
    unavailableBody: { status: 'TEMPORARILY_UNAVAILABLE', secret: 'fixed-public-value' },
  });
  assert.equal((await route.fetch(new Request('https://nova.example/1'))).status, 200);
  assert.equal((await route.fetch(new Request('https://nova.example/2'))).status, 200);
  assert.equal(builds, 1);

  const failed = createLazyVercelRoute({
    createApplication() {
      throw new Error('NOVA_PROVIDER_KEYS contained server-secret-value');
    },
    selectHandler: () => null,
    unavailableBody: { status: 'TEMPORARILY_UNAVAILABLE' },
  });
  const response = await failed.fetch(new Request('https://nova.example/fail'));
  assert.equal(response.status, 503);
  const text = await response.text();
  assert.equal(text.includes('server-secret-value'), false);
  assert.equal(text.includes('NOVA_PROVIDER_KEYS'), false);
});

test('health route returns only coarse deployment state and never safeSummary details', async () => {
  const health = createLazyVercelHealthRoute({
    createApplication: () => ({ safeSummary: { providerKeyCount: 5, secret: 'never-return' } }),
  });
  const ok = await health.fetch(new Request('https://nova.example/api/health'));
  assert.equal(ok.status, 200);
  assert.deepEqual(await ok.json(), { status: 'OK' });

  const method = await health.fetch(new Request('https://nova.example/api/health', { method: 'POST' }));
  assert.equal(method.status, 405);
  assert.equal(method.headers.get('allow'), 'GET');
});
