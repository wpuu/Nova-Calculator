import test from 'node:test';
import assert from 'node:assert/strict';

import { createNovaGatewayApplication } from '../src/application.mjs';
import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';

const NOW = 1_800_000_000_000;
const CLIENT_ID = 'chrome-client.apps.googleusercontent.com';

function env(overrides = {}) {
  return {
    VERCEL_ENV: 'preview',
    NOVA_ANDROID_PACKAGE_NAME: 'com.wpuu.novacalculator',
    NOVA_PROVIDER_BASE_URL: 'https://provider.invalid/v1',
    NOVA_PROVIDER_MODEL: 'runtime-model',
    NOVA_PROVIDER_KEYS: 'provider-key-a',
    NOVA_PROVIDER_RPM_PER_KEY: '12',
    NOVA_SESSION_SIGNING_SECRETS: 'browser-composition-signing-secret-0123456789',
    NOVA_SESSION_SUBJECT_SECRET: 'browser-composition-subject-secret-0123456789',
    NOVA_AI_FREE_DAILY_LIMIT: '3',
    NOVA_AI_FREE_RPM_LIMIT: '1',
    NOVA_AI_PRO_DAILY_LIMIT: '10',
    NOVA_AI_PRO_RPM_LIMIT: '3',
    NOVA_AI_PLUS_DAILY_LIMIT: '200',
    NOVA_AI_PLUS_RPM_LIMIT: '10',
    ...overrides,
  };
}

function quotaStore() {
  return {
    async reserve() { return { status: 'ALLOWED', reservationId: 'r1' }; },
    async commit() {},
    async release() {},
  };
}

function installationProofVerifier() {
  return { verify: async () => ({ accepted: true, bindingId: 'install:test' }) };
}

function keyPoolFactory({ keys }) {
  return {
    async lease() { return { id: keys[0].id, secret: keys[0].secret }; },
    async reportSuccess() {},
    async reportRateLimit() {},
    async reportFailure() {},
    async setEnabled() {},
  };
}

test('core leaves browser route disabled when browser identity verifier is absent', () => {
  const app = createNovaGatewayApplication({
    env: env(),
    quotaStore: quotaStore(),
    installationProofVerifier: installationProofVerifier(),
    keyPoolFactory,
    fetchImpl: async () => { throw new Error('unused'); },
    now: () => NOW,
  });
  assert.equal(app.browserSessionHandler, null);
  assert.equal(app.safeSummary.googleBrowserSessionExchange, false);
  assert.equal(typeof app.anonymousSessionHandler, 'function');
  assert.equal(typeof app.macroCandidateReviewHandler, 'function');
});

test('production enables browser exchange only when Chrome OAuth client ids are configured', async () => {
  const calls = [];
  const app = createProductionNovaGatewayApplication({
    env: env({ NOVA_CHROME_OAUTH_CLIENT_IDS: CLIENT_ID }),
    quotaStore: quotaStore(),
    keyPoolFactory,
    installationProofVerifier: installationProofVerifier(),
    productEventStore: null,
    now: () => NOW,
    fetchImpl: async (url) => {
      calls.push(String(url));
      if (String(url).startsWith('https://oauth2.googleapis.com/tokeninfo?')) {
        return new Response(JSON.stringify({
          aud: CLIENT_ID,
          azp: CLIENT_ID,
          sub: 'google-subject-7',
          scope: 'openid',
          exp: Math.floor((NOW + 30 * 60 * 1000) / 1000),
          email: 'ignored@example.com',
        }), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      throw new Error('unexpected upstream call');
    },
  });

  assert.equal(typeof app.browserSessionHandler, 'function');
  assert.equal(app.safeSummary.googleBrowserSessionExchange, true);
  assert.equal(app.safeSummary.googleBrowserIdentityVerification, true);
  const response = await app.browserSessionHandler(new Request('https://nova.example/api/browser-session', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ accessToken: 'google-access-token' }),
  }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.status, 'SUCCESS');
  assert.equal(typeof body.sessionToken, 'string');
  assert.equal(body.sessionToken.startsWith('nova1.'), true);
  assert.equal(JSON.stringify(body).includes('google-subject-7'), false);
  assert.equal(JSON.stringify(body).includes('ignored@example.com'), false);
  assert.equal(JSON.stringify(app.safeSummary).includes(CLIENT_ID), false);
  assert.equal(calls.length, 1);
});

test('production without Chrome OAuth config keeps Android and Macro services available', () => {
  const app = createProductionNovaGatewayApplication({
    env: env(),
    quotaStore: quotaStore(),
    keyPoolFactory,
    installationProofVerifier: installationProofVerifier(),
    productEventStore: null,
    now: () => NOW,
    fetchImpl: async () => { throw new Error('unused'); },
  });
  assert.equal(app.browserSessionHandler, null);
  assert.equal(app.safeSummary.googleBrowserIdentityVerification, false);
  assert.equal(typeof app.anonymousSessionHandler, 'function');
  assert.equal(typeof app.aiHandler, 'function');
  assert.equal(typeof app.macroCandidateReviewHandler, 'function');
});
