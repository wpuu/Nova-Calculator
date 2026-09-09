import test from 'node:test';
import assert from 'node:assert/strict';

import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';
import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';

const NOW = 1_800_000_000_000;
const CLIENT_ID = 'chrome-client.apps.googleusercontent.com';

function env() {
  return {
    VERCEL_ENV: 'preview',
    NOVA_ANDROID_PACKAGE_NAME: 'com.wpuu.novacalculator',
    NOVA_PROVIDER_BASE_URL: 'https://provider.invalid/v1',
    NOVA_PROVIDER_MODEL: 'runtime-model',
    NOVA_PROVIDER_KEYS: 'unit-key',
    NOVA_PROVIDER_RPM_PER_KEY: '12',
    NOVA_SESSION_SIGNING_SECRETS: 'browser-macro-signing-material-0123456789012345',
    NOVA_SESSION_SUBJECT_SECRET: 'browser-macro-subject-material-0123456789012345',
    NOVA_CHROME_OAUTH_CLIENT_IDS: CLIENT_ID,
    NOVA_AI_FREE_DAILY_LIMIT: '3',
    NOVA_AI_FREE_RPM_LIMIT: '1',
    NOVA_AI_PRO_DAILY_LIMIT: '10',
    NOVA_AI_PRO_RPM_LIMIT: '3',
    NOVA_AI_PLUS_DAILY_LIMIT: '200',
    NOVA_AI_PLUS_RPM_LIMIT: '10',
  };
}

function reviewRequest() {
  return {
    requestId: 'browser-macro-1',
    operation: 'MACRO_CANDIDATE_REVIEW',
    review: {
      reviewId: 'review-browser-1',
      index: 0,
      stepType: 'click',
      semanticActionId: 'shopify.export_orders',
      original: {
        semanticActionId: 'shopify.export_orders',
        role: 'button',
        names: ['Export orders'],
        context: ['Orders'],
        attrs: {},
        hrefPath: '',
        tag: 'button',
      },
      candidates: [
        { id: 'candidate_1', role: 'button', names: ['Import'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 51 },
        { id: 'candidate_2', role: 'button', names: ['Export orders'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 63 },
      ],
      allowedCandidateIds: ['candidate_1', 'candidate_2'],
      policy: 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
    },
  };
}

test('browser identity exchanges to Nova session and reaches bounded Macro review as FREE principal', async () => {
  const quotaCalls = [];
  let providerCallCount = 0;
  const app = createProductionNovaGatewayApplication({
    env: env(),
    now: () => NOW,
    installationProofVerifier: { verify: async () => ({ accepted: true, bindingId: 'unused' }) },
    productEventStore: null,
    quotaStore: {
      async reserve(input) {
        quotaCalls.push(input);
        return { status: 'ALLOWED', reservationId: 'r1', remainingRequestHint: 2 };
      },
      async commit() {},
      async release() {},
    },
    keyPoolFactory({ keys }) {
      return {
        async lease() { return { id: keys[0].id, secret: keys[0].secret }; },
        async reportSuccess() {},
        async reportRateLimit() {},
        async reportFailure() {},
        async setEnabled() {},
      };
    },
    fetchImpl: async (url) => {
      const target = String(url);
      if (target.startsWith('https://oauth2.googleapis.com/tokeninfo?')) {
        return new Response(JSON.stringify({
          aud: CLIENT_ID,
          azp: CLIENT_ID,
          sub: 'browser-user',
          scope: 'openid',
          exp: Math.floor((NOW + 30 * 60 * 1000) / 1000),
        }), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      if (target === 'https://provider.invalid/v1/chat/completions') {
        providerCallCount += 1;
        return new Response(JSON.stringify({
          choices: [{ message: { content: JSON.stringify({
            decision: 'SELECT',
            candidate_id: 'candidate_2',
            confidence: 0.94,
            reason: 'Unique match.',
          }) } }],
        }), { status: 200, headers: { 'content-type': 'application/json' } });
      }
      throw new Error(`unexpected fetch target: ${target}`);
    },
  });

  const browserResponse = await app.browserSessionHandler(new Request('https://nova.example/api/browser-session', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ accessToken: 't' }),
  }));
  assert.equal(browserResponse.status, 200);
  const browserSession = await browserResponse.json();
  assert.equal(browserSession.status, 'SUCCESS');
  assert.equal(browserSession.sessionToken.startsWith('nova1.'), true);

  const macroResponse = await app.macroCandidateReviewHandler(new Request('https://nova.example/api/macro-candidate-review', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${browserSession.sessionToken}`,
    },
    body: JSON.stringify(reviewRequest()),
  }));
  assert.equal(macroResponse.status, 200);
  const macro = await macroResponse.json();
  assert.equal(macro.status, 'SUCCESS');
  assert.equal(macro.decision, 'SELECT');
  assert.equal(macro.candidateId, 'candidate_2');

  assert.equal(quotaCalls.length, 1);
  assert.equal(quotaCalls[0].priority, REQUEST_PRIORITY.FREE);
  assert.equal(quotaCalls[0].operation, 'MACRO_CANDIDATE_REVIEW');
  assert.equal(providerCallCount, 1);
});
