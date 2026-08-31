import test from 'node:test';
import assert from 'node:assert/strict';

import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';
import { createGatewayRuntime } from '../src/runtime.mjs';

function env(overrides = {}) {
  return {
    NOVA_PROVIDER_BASE_URL: 'https://runtime-provider.invalid/v1',
    NOVA_PROVIDER_MODEL: 'runtime-model',
    NOVA_PROVIDER_KEYS: 'secret-a,secret-b',
    NOVA_PROVIDER_RPM_PER_KEY: '20',
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

test('safe runtime summary never exposes provider identity or raw credentials', () => {
  const runtime = createGatewayRuntime(env(), {
    fetchImpl: async () => {
      throw new Error('unused');
    },
  });

  assert.deepEqual(runtime.safeSummary, {
    providerKeyCount: 2,
    rpmPerKey: 20,
    paidReserveFraction: 0.2,
    providerTimeoutMs: 15_000,
    maxTokens: 800,
    sharedProviderCapacity: false,
  });

  const serialized = JSON.stringify(runtime.safeSummary);
  assert.equal(serialized.includes('runtime-provider'), false);
  assert.equal(serialized.includes('runtime-model'), false);
  assert.equal(serialized.includes('secret-a'), false);
  assert.equal(serialized.includes('secret-b'), false);
});

test('runtime can inject an asynchronous shared provider pool without exposing secrets', async () => {
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
  const runtime = createGatewayRuntime(env({ NOVA_PROVIDER_CREDENTIAL_DISABLE_MS: '3600000' }), {
    keyPoolFactory(options) {
      factoryCalls.push(options);
      return sharedPool;
    },
    fetchImpl: async () => ({
      ok: true,
      status: 200,
      headers: { get: () => null },
      async json() {
        return { choices: [{ message: { content: '共享容量正常' } }] };
      },
    }),
  });

  const result = await runtime.dispatcher.dispatch({
    operation: 'EXPLAIN_CALCULATION',
    expression: '1+2',
    deterministicResult: '3',
    localeTag: 'zh-CN',
  }, REQUEST_PRIORITY.AI_PLUS);

  assert.equal(result.answer, '共享容量正常');
  assert.equal(factoryCalls.length, 1);
  assert.equal(factoryCalls[0].keys.length, 2);
  assert.equal(factoryCalls[0].keys[0].secret, 'secret-a');
  assert.equal(factoryCalls[0].credentialDisableMs, 3_600_000);
  assert.equal(runtime.safeSummary.sharedProviderCapacity, true);
  assert.equal(leases[0].priority, REQUEST_PRIORITY.AI_PLUS);
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
    () => createGatewayRuntime(env({ NOVA_PAID_RESERVE_FRACTION: '1' })),
    />= 0 and < 1/,
  );
  assert.throws(
    () => createGatewayRuntime(env({ NOVA_PROVIDER_CREDENTIAL_DISABLE_MS: '0' })),
    /NOVA_PROVIDER_CREDENTIAL_DISABLE_MS must be a positive integer/,
  );
});
