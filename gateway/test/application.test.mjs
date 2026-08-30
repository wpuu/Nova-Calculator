import test from 'node:test';
import assert from 'node:assert/strict';

import { createNovaGatewayApplication } from '../src/application.mjs';
import { QUOTA_DECISION } from '../src/nova-ai-service.mjs';
import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';

const SIGNING = 'signing-secret-a-0123456789-0123456789';
const SUBJECT = 'subject-secret-stable-0123456789-012345';

function env(overrides = {}) {
  return {
    NOVA_PROVIDER_BASE_URL: 'https://provider.invalid/v1',
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

function providerResponse(answer = '因为 2+2 等于 4。') {
  return new Response(JSON.stringify({
    choices: [{ message: { content: answer } }],
  }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

function quotaStore(calls) {
  return {
    async reserve(input) {
      calls.reserve.push(input);
      return { status: QUOTA_DECISION.ALLOWED, remainingRequestHint: 2 };
    },
    async commit(id) {
      calls.commit.push(id);
    },
    async release(id) {
      calls.release.push(id);
    },
  };
}

function proofVerifier() {
  return {
    async verify({ installationId, proof }) {
      return proof === 'valid-proof'
        ? { accepted: true, bindingId: `verified-binding:${installationId}` }
        : { accepted: false };
    },
  };
}

async function issueAnonymousToken(app) {
  const response = await app.anonymousSessionHandler(new Request('https://nova.invalid/session/anonymous', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ installationId: 'install-123', proof: 'valid-proof' }),
  }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.status, 'SUCCESS');
  return body.sessionToken;
}

function aiRequest(token, bodyOverrides = {}) {
  return new Request('https://nova.invalid/ai', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      requestId: 'req-1',
      operation: 'EXPLAIN_CALCULATION',
      expression: '2+2',
      deterministicResult: '4',
      localeTag: 'zh-CN',
      ...bodyOverrides,
    }),
  });
}

test('proof-gated anonymous session can call AI only with server-assigned FREE priority', async () => {
  const calls = { reserve: [], commit: [], release: [], provider: [] };
  const app = createNovaGatewayApplication({
    env: env(),
    quotaStore: quotaStore(calls),
    installationProofVerifier: proofVerifier(),
    now: () => 1_800_000_000_000,
    newReservationId: () => 'reservation-1',
    fetchImpl: async (url, options) => {
      calls.provider.push({ url, options });
      return providerResponse();
    },
  });

  const token = await issueAnonymousToken(app);
  const response = await app.aiHandler(aiRequest(token));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.status, 'SUCCESS');
  assert.equal(body.answer, '因为 2+2 等于 4。');

  assert.equal(calls.reserve.length, 1);
  assert.equal(calls.reserve[0].priority, REQUEST_PRIORITY.FREE);
  assert.equal(calls.reserve[0].dailyLimit, 3);
  assert.equal(calls.reserve[0].rpmLimit, 1);
  assert.deepEqual(calls.commit, ['reservation-1']);
  assert.equal(calls.provider.length, 1);
});

test('client privilege claims cannot upgrade an anonymous session', async () => {
  const calls = { reserve: [], commit: [], release: [], provider: [] };
  const app = createNovaGatewayApplication({
    env: env(),
    quotaStore: quotaStore(calls),
    installationProofVerifier: proofVerifier(),
    now: () => 1_800_000_000_000,
    newReservationId: () => 'reservation-2',
    fetchImpl: async () => providerResponse(),
  });

  const token = await issueAnonymousToken(app);
  const response = await app.aiHandler(aiRequest(token, {
    entitlements: ['AI_PLUS'],
    priority: 'AI_PLUS',
  }));
  assert.equal(response.status, 200);
  assert.equal(calls.reserve[0].priority, REQUEST_PRIORITY.FREE);
  assert.equal(calls.reserve[0].dailyLimit, 3);
  assert.equal(calls.reserve[0].rpmLimit, 1);
});

test('AI endpoint rejects missing or tampered Nova session before quota and provider usage', async () => {
  const calls = { reserve: [], commit: [], release: [], provider: [] };
  const app = createNovaGatewayApplication({
    env: env(),
    quotaStore: quotaStore(calls),
    installationProofVerifier: proofVerifier(),
    now: () => 1_800_000_000_000,
    newReservationId: () => 'reservation-3',
    fetchImpl: async () => {
      calls.provider.push(true);
      return providerResponse();
    },
  });

  const missing = await app.aiHandler(new Request('https://nova.invalid/ai', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      requestId: 'req-missing',
      operation: 'EXPLAIN_CALCULATION',
      expression: '2+2',
      deterministicResult: '4',
      localeTag: 'zh-CN',
    }),
  }));
  assert.equal(missing.status, 401);

  const token = await issueAnonymousToken(app);
  const tamperedToken = `${token.slice(0, -1)}${token.endsWith('A') ? 'B' : 'A'}`;
  const tampered = await app.aiHandler(aiRequest(tamperedToken));
  assert.equal(tampered.status, 401);
  assert.equal(calls.reserve.length, 0);
  assert.equal(calls.provider.length, 0);
});

test('safe application summary exposes capacity and quota numbers but no provider identity or secrets', () => {
  const calls = { reserve: [], commit: [], release: [] };
  const app = createNovaGatewayApplication({
    env: env(),
    quotaStore: quotaStore(calls),
    installationProofVerifier: proofVerifier(),
    now: () => 1_800_000_000_000,
    fetchImpl: async () => providerResponse(),
  });

  assert.equal(app.safeSummary.providerKeyCount, 2);
  assert.equal(app.safeSummary.freeDailyLimit, 3);
  assert.equal(app.safeSummary.aiPlusDailyLimit, 200);
  assert.equal(app.safeSummary.signedNovaSessions, true);
  assert.equal(app.safeSummary.proofGatedAnonymousSessions, true);

  const serialized = JSON.stringify(app.safeSummary);
  assert.equal(serialized.includes('provider-key-a'), false);
  assert.equal(serialized.includes('runtime-model'), false);
  assert.equal(serialized.includes('provider.invalid'), false);
  assert.equal(serialized.includes(SIGNING), false);
  assert.equal(serialized.includes(SUBJECT), false);
});

test('application construction fails closed when required shared deployment adapters are missing', () => {
  assert.throws(
    () => createNovaGatewayApplication({
      env: env(),
      installationProofVerifier: proofVerifier(),
      fetchImpl: async () => providerResponse(),
    }),
    /DailyQuotaLedger requires atomic store/,
  );

  assert.throws(
    () => createNovaGatewayApplication({
      env: env(),
      quotaStore: quotaStore({ reserve: [], commit: [], release: [] }),
      fetchImpl: async () => providerResponse(),
    }),
    /AnonymousSessionService requires installationProofVerifier.verify/,
  );
});
