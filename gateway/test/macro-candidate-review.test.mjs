import assert from 'node:assert/strict';
import test from 'node:test';

import { ProviderInvocationError, PROVIDER_FAILURE_KIND } from '../src/gateway-dispatcher.mjs';
import { createMacroCandidateReviewFetchHandler } from '../src/macro-candidate-review-http-handler.mjs';
import {
  MacroCandidateReviewProvider,
  buildCandidateReviewMessages,
  normalizeCandidateReviewRequest,
  parseCandidateDecision,
} from '../src/macro-candidate-review-provider.mjs';
import { MacroCandidateReviewService } from '../src/macro-candidate-review-service.mjs';
import { NOVA_GATEWAY_STATUS, QUOTA_DECISION } from '../src/nova-ai-service.mjs';

function review(overrides = {}) {
  return {
    reviewId: 'review-1',
    index: 0,
    stepType: 'click',
    semanticActionId: 'shopify.export_orders',
    original: {
      semanticActionId: 'shopify.export_orders',
      role: 'button',
      names: ['Export orders'],
      context: ['Orders'],
      attrs: { 'data-action': 'export' },
      hrefPath: '',
      tag: 'button',
    },
    candidates: [
      { id: 'candidate_1', role: 'button', names: ['Import'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 54 },
      { id: 'candidate_2', role: 'button', names: ['Export orders'], context: ['Orders'], attrs: { 'data-action': 'export' }, hrefPath: '', tag: 'button', score: 61 },
    ],
    allowedCandidateIds: ['candidate_1', 'candidate_2'],
    policy: 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
    ...overrides,
  };
}

function request(overrides = {}) {
  return {
    requestId: 'req-1',
    operation: 'MACRO_CANDIDATE_REVIEW',
    review: review(),
    ...overrides,
  };
}

function providerHttp(content, status = 200) {
  return new Response(JSON.stringify({
    choices: [{ message: { content } }],
  }), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('provider sends bounded candidate data and accepts one listed candidate', async () => {
  let captured;
  const provider = new MacroCandidateReviewProvider({
    baseUrl: 'https://provider.example/v1',
    model: 'hidden-model',
    fetchImpl: async (url, options) => {
      captured = { url, options };
      return providerHttp(JSON.stringify({
        decision: 'SELECT',
        candidate_id: 'candidate_2',
        confidence: 0.93,
        reason: 'Candidate 2 uniquely matches the recorded export action.',
      }));
    },
  });

  const result = await provider.invoke({ request: request(), apiKey: 'secret-key' });
  assert.deepEqual(result, {
    decision: 'SELECT',
    candidateId: 'candidate_2',
    confidence: 0.93,
    reason: 'Candidate 2 uniquely matches the recorded export action.',
  });
  assert.equal(captured.url, 'https://provider.example/v1/chat/completions');
  assert.equal(captured.options.headers.authorization, 'Bearer secret-key');
  const body = JSON.parse(captured.options.body);
  assert.equal(body.temperature, 0);
  assert.equal(body.stream, false);
  assert(!captured.options.body.includes('secret-key'));
  assert(captured.options.body.includes('SELECT_LOCAL_CANDIDATE_OR_ABSTAIN'));
});

test('provider fails closed on invented candidate id', () => {
  const result = parseCandidateDecision(JSON.stringify({
    decision: 'SELECT',
    candidate_id: 'candidate_999',
    confidence: 1,
    reason: 'invented',
  }), ['candidate_1', 'candidate_2']);
  assert.equal(result.decision, 'ABSTAIN');
  assert.equal(result.candidateId, null);
  assert.equal(result.reason, 'CANDIDATE_NOT_ALLOWED');
});

test('provider fails closed when model emits selector field', () => {
  const result = parseCandidateDecision(JSON.stringify({
    decision: 'SELECT',
    candidate_id: 'candidate_1',
    confidence: 0.9,
    reason: 'match',
    selector: '#export',
  }), ['candidate_1']);
  assert.equal(result.decision, 'ABSTAIN');
  assert.equal(result.reason, 'FORBIDDEN_PROVIDER_OUTPUT_FIELD');
});

test('normalizer rebuilds attributes and strips executable selector-like keys', () => {
  const dirty = request({
    review: review({
      candidates: [
        { id: 'candidate_1', role: 'button', names: ['Export'], context: ['Orders'], attrs: { selector: '#x', xpath: '//x', id: 'safe-id' }, hrefPath: '', tag: 'button', score: 55 },
      ],
      allowedCandidateIds: ['candidate_1'],
    }),
  });
  const normalized = normalizeCandidateReviewRequest(dirty);
  assert.deepEqual(normalized.review.candidates[0].attrs, { id: 'safe-id' });
  const messages = buildCandidateReviewMessages(dirty);
  const serialized = JSON.stringify(messages);
  assert(!serialized.includes('"selector":"#x"'));
  assert(!serialized.includes('"xpath":"//x"'));
});

test('normalizer rejects allowedCandidateIds that do not exactly match candidates', () => {
  assert.throws(
    () => normalizeCandidateReviewRequest(request({ review: review({ allowedCandidateIds: ['candidate_999'] }) })),
    (error) => error instanceof ProviderInvocationError && error.kind === PROVIDER_FAILURE_KIND.REQUEST,
  );
});

test('service authenticates, reserves quota and returns listed candidate only', async () => {
  const calls = [];
  const service = new MacroCandidateReviewService({
    authVerifier: { verify: async () => ({ subjectId: 'subject-1', entitlements: ['AI_PLUS'] }) },
    quotaLedger: {
      reserve: async (ctx) => {
        calls.push(['reserve', ctx]);
        return { status: QUOTA_DECISION.ALLOWED, reservationId: 'r1', remainingRequestHint: 9 };
      },
      commit: async (id) => calls.push(['commit', id]),
      release: async (id) => calls.push(['release', id]),
    },
    dispatcher: {
      dispatch: async (normalized, priority) => {
        calls.push(['dispatch', normalized.operation, priority]);
        return { decision: 'SELECT', candidateId: 'candidate_2', confidence: 0.88, reason: 'match' };
      },
    },
  });

  const result = await service.execute({ authorization: 'Bearer session', request: request() });
  assert.equal(result.status, NOVA_GATEWAY_STATUS.SUCCESS);
  assert.equal(result.decision, 'SELECT');
  assert.equal(result.candidateId, 'candidate_2');
  assert(calls.some(([name]) => name === 'commit'));
  assert(!calls.some(([name]) => name === 'release'));
});

test('service independently rejects dispatcher candidate outside current allow-list', async () => {
  const service = new MacroCandidateReviewService({
    authVerifier: { verify: async () => ({ subjectId: 'subject-1', entitlements: [] }) },
    quotaLedger: {
      reserve: async () => ({ status: QUOTA_DECISION.ALLOWED, reservationId: 'r1' }),
      commit: async () => {},
      release: async () => {},
    },
    dispatcher: {
      dispatch: async () => ({ decision: 'SELECT', candidateId: 'candidate_999', confidence: 1, reason: 'bad adapter' }),
    },
  });
  const result = await service.execute({ authorization: 'Bearer session', request: request() });
  assert.equal(result.status, NOVA_GATEWAY_STATUS.SUCCESS);
  assert.equal(result.decision, 'ABSTAIN');
  assert.equal(result.candidateId, null);
  assert.equal(result.reason, 'SERVER_CANDIDATE_REJECTED');
});

test('service does not spend quota for unauthenticated request', async () => {
  let reserved = false;
  const service = new MacroCandidateReviewService({
    authVerifier: { verify: async () => null },
    quotaLedger: {
      reserve: async () => { reserved = true; throw new Error('must not reserve'); },
      commit: async () => {},
      release: async () => {},
    },
    dispatcher: { dispatch: async () => { throw new Error('must not dispatch'); } },
  });
  const result = await service.execute({ authorization: '', request: request() });
  assert.equal(result.status, NOVA_GATEWAY_STATUS.AUTH_REQUIRED);
  assert.equal(reserved, false);
});

test('HTTP handler exposes only Nova candidate decision fields', async () => {
  const handler = createMacroCandidateReviewFetchHandler({
    service: {
      execute: async () => ({
        requestId: 'req-1',
        status: NOVA_GATEWAY_STATUS.SUCCESS,
        decision: 'SELECT',
        candidateId: 'candidate_2',
        confidence: 0.77,
        reason: 'safe',
        provider: 'must-not-leak',
        model: 'must-not-leak',
        apiKey: 'must-not-leak',
      }),
    },
  });
  const response = await handler(new Request('https://nova.example/api/macro-candidate-review', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: 'Bearer session' },
    body: JSON.stringify(request()),
  }));
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const body = await response.json();
  assert.deepEqual(Object.keys(body).sort(), [
    'candidateId', 'confidence', 'decision', 'quotaResetAtEpochMs', 'reason',
    'remainingRequestHint', 'requestId', 'retryAfterSeconds', 'status',
  ].sort());
  assert.equal(body.candidateId, 'candidate_2');
  assert(!JSON.stringify(body).includes('must-not-leak'));
});

test('HTTP handler rejects oversized or non-JSON bodies before service execution', async () => {
  let calls = 0;
  const handler = createMacroCandidateReviewFetchHandler({
    service: { execute: async () => { calls += 1; return {}; } },
    maxBodyBytes: 32,
  });
  const badType = await handler(new Request('https://nova.example/api/macro-candidate-review', {
    method: 'POST',
    headers: { 'content-type': 'text/plain' },
    body: '{}',
  }));
  assert.equal(badType.status, 415);

  const large = await handler(new Request('https://nova.example/api/macro-candidate-review', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ x: 'x'.repeat(100) }),
  }));
  assert.equal(large.status, 413);
  assert.equal(calls, 0);
});
