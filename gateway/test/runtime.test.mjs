import test from 'node:test';
import assert from 'node:assert/strict';

import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';
import { createGatewayRuntime } from '../src/runtime.mjs';

function env(overrides = {}) {
  return {
    NOVA_PROVIDER_BASE_URL: 'https://runtime-provider.invalid/v1',
    NOVA_PROVIDER_MODEL: 'runtime-model',
    NOVA_PROVIDER_KEYS: 'secret-a,secret-b',
    NOVA_PROVIDER_RPM_PER_KEY: '12',
    NOVA_PAID_RESERVE_FRACTION: '0.2',
    ...overrides,
  };
}

test('runtime wires environment-only provider config into a working dispatcher', async () => {
  const calls = [];
  const runtime = createGatewayRuntime(env(), {
    fetchImpl: async (url, options) => {
      calls.push({ url, options });
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async json() {
          return { choices: [{ message: { content: '解释完成' } }] };
        },
      };
    },
  });

  const result = await runtime.dispatcher.dispatch({
    operation: 'EXPLAIN_CALCULATION',
    expression: '2+2',
    deterministicResult: '4',
    localeTag: 'zh-CN',
  }, REQUEST_PRIORITY.FREE);

  assert.deepEqual(result, { answer: '解释完成' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://runtime-provider.invalid/v1/chat/completions');
});

test('runtime wires bounded Macro reviewer through its own dispatcher', async () => {
  const calls = [];
  const runtime = createGatewayRuntime(env(), {
    fetchImpl: async (url, options) => {
      calls.push({ url, options });
      return new Response(JSON.stringify({
        choices: [{ message: { content: JSON.stringify({
          decision: 'SELECT',
          candidate_id: 'candidate_2',
          confidence: 0.91,
          reason: 'Unique semantic match.',
        }) } }],
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    },
  });

  const result = await runtime.macroCandidateReviewDispatcher.dispatch({
    requestId: 'macro-1',
    operation: 'MACRO_CANDIDATE_REVIEW',
    review: {
      reviewId: 'review-1',
      index: 0,
      stepType: 'click',
      semanticActionId: 'shopify.export_orders',
      original: { role: 'button', names: ['Export orders'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button' },
      candidates: [
        { id: 'candidate_1', role: 'button', names: ['Import'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 50 },
        { id: 'candidate_2', role: 'button', names: ['Export orders'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 61 },
      ],
      allowedCandidateIds: ['candidate_1', 'candidate_2'],
      policy: 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
    },
  }, REQUEST_PRIORITY.FREE);

  assert.equal(result.decision, 'SELECT');
  assert.equal(result.candidateId, 'candidate_2');
  assert.equal(calls.length, 1);
  const body = JSON.parse(calls[0].options.body);
  assert.equal(body.temperature, 0);
  assert.equal(body.max_tokens, 400);
  assert.equal(body.messages[0].content.includes('cannot create selectors'), true);
});

test('safe runtime summary never exposes provider identity or raw credentials', () => {
  const runtime = createGatewayRuntime(env(), {
    fetchImpl: async () => {
      throw new Error('unused');
    },
  });

  assert.deepEqual(runtime.safeSummary, {
    providerKeyCount: 2,
    rpmPerKey: 12,
    paidReserveFraction: 0.2,
    providerTimeoutMs: 15_000,
    maxTokens: 800,
    macroMaxTokens: 400,
    sharedProviderCapacity: false,
    macroCandidateReviewSharesProviderCapacity: true,
  });

  const serialized = JSON.stringify(runtime.safeSummary);
  assert.equal(serialized.includes('runtime-provider'), false);
  assert.equal(serialized.includes('runtime-model'), false);
  assert.equal(serialized.includes('secret-a'), false);
  assert.equal(serialized.includes('secret-b'), false);
});

test('calculation and Macro dispatchers consume the same shared provider pool', async () => {
  const factoryCalls = [];
  const leases = [];
  const sharedPool = {
    async lease(priority, options) {
      leases.push({ priority, options });
      return { id: 'key-1', secret: 'secret-a' };
    },
    async reportSuccess() {},
    async reportRateLimit() {},
    async reportFailure() {},
    async setEnabled() {},
  };
  const runtime = createGatewayRuntime(env(), {
    keyPoolFactory(options) {
      factoryCalls.push(options);
      return sharedPool;
    },
    fetchImpl: async (_url, options) => {
      const payload = JSON.parse(options.body);
      const isMacro = payload.messages?.[0]?.content?.includes('Nova Macro candidate reviewer');
      return new Response(JSON.stringify({
        choices: [{ message: { content: isMacro
          ? JSON.stringify({ decision: 'ABSTAIN', candidate_id: null, confidence: 0.4, reason: 'Ambiguous.' })
          : '共享容量正常' } }],
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    },
  });

  const calculation = await runtime.dispatcher.dispatch({
    operation: 'EXPLAIN_CALCULATION',
    expression: '1+2',
    deterministicResult: '3',
    localeTag: 'zh-CN',
  }, REQUEST_PRIORITY.AI_PLUS);
  assert.equal(calculation.answer, '共享容量正常');

  const macro = await runtime.macroCandidateReviewDispatcher.dispatch({
    requestId: 'macro-shared-1',
    operation: 'MACRO_CANDIDATE_REVIEW',
    review: {
      reviewId: 'review-shared-1',
      index: 1,
      stepType: 'click',
      semanticActionId: 'shopify.export_orders',
      original: { role: 'button', names: ['Export orders'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button' },
      candidates: [
        { id: 'candidate_1', role: 'button', names: ['Export'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 55 },
      ],
      allowedCandidateIds: ['candidate_1'],
      policy: 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
    },
  }, REQUEST_PRIORITY.AI_PLUS);

  assert.equal(macro.decision, 'ABSTAIN');
  assert.equal(factoryCalls.length, 1);
  assert.equal(factoryCalls[0].keys.length, 2);
  assert.equal(factoryCalls[0].keys[0].secret, 'secret-a');
  assert.equal(runtime.safeSummary.sharedProviderCapacity, true);
  assert.equal(leases.length, 2);
  assert.equal(leases[0].priority, REQUEST_PRIORITY.AI_PLUS);
  assert.equal(leases[1].priority, REQUEST_PRIORITY.AI_PLUS);
  assert.equal(JSON.stringify(runtime.safeSummary).includes('secret-a'), false);
});

test('runtime rejects missing provider secrets and invalid capacity settings', () => {
  assert.throws(
    () => createGatewayRuntime(env({ NOVA_PROVIDER_KEYS: '' })),
    /NOVA_PROVIDER_KEYS is required/,
  );
  assert.throws(
    () => createGatewayRuntime(env({ NOVA_PROVIDER_RPM_PER_KEY: '0' })),
    /positive integer/,
  );
  assert.throws(
    () => createGatewayRuntime(env({ NOVA_PROVIDER_RPM_PER_KEY: '16' })),
    /between 1 and 15/,
  );
  assert.throws(
    () => createGatewayRuntime(env({ NOVA_PAID_RESERVE_FRACTION: '1' })),
    />= 0 and < 1/,
  );
  assert.throws(
    () => createGatewayRuntime(env({ NOVA_MACRO_PROVIDER_MAX_TOKENS: '0' })),
    /positive integer/,
  );
});
