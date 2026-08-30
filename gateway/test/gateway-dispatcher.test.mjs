import test from 'node:test';
import assert from 'node:assert/strict';

import {
  GatewayDispatcher,
  GatewayDispatchError,
  PROVIDER_FAILURE_KIND,
  ProviderInvocationError,
} from '../src/gateway-dispatcher.mjs';
import { ProviderKeyPool, REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';

function twoKeyPool(options = {}) {
  return new ProviderKeyPool([
    { id: 'a', secret: 'secret-a', rpmLimit: 20 },
    { id: 'b', secret: 'secret-b', rpmLimit: 20 },
  ], { paidReserveFraction: 0, ...options });
}

test('429 cools the current key and retries the same request on a different key', async () => {
  let now = 0;
  const pool = twoKeyPool({ now: () => now });
  const calls = [];
  const dispatcher = new GatewayDispatcher({
    keyPool: pool,
    provider: {
      async invoke({ request, apiKey, keyId }) {
        calls.push({ request, apiKey, keyId });
        if (keyId === 'a') {
          throw ProviderInvocationError.fromHttpStatus(429, 'rate limited', { retryAfterMs: 5_000 });
        }
        return { text: 'ok', keyId };
      },
    },
  });

  const response = await dispatcher.dispatch({ expression: '1+1' }, REQUEST_PRIORITY.FREE);
  assert.deepEqual(response, { text: 'ok', keyId: 'b' });
  assert.deepEqual(calls.map((call) => call.keyId), ['a', 'b']);
  assert.deepEqual(calls.map((call) => call.request), [
    { expression: '1+1' },
    { expression: '1+1' },
  ]);

  const stateA = pool.snapshot().find((entry) => entry.id === 'a');
  assert.equal(stateA.cooldownUntilMs, 5_000);
  assert.equal(stateA.requestsInWindow, 20);

  now = 60_000;
  pool.setEnabled('b', false);
  assert.equal(pool.lease(REQUEST_PRIORITY.AI_PLUS).id, 'a');
});

test('credential rejection disables only the bad key and fails over', async () => {
  const pool = twoKeyPool();
  const calls = [];
  const dispatcher = new GatewayDispatcher({
    keyPool: pool,
    provider: {
      async invoke({ keyId }) {
        calls.push(keyId);
        if (keyId === 'a') throw ProviderInvocationError.fromHttpStatus(401, 'invalid credential');
        return 'ok';
      },
    },
  });

  assert.equal(await dispatcher.dispatch({ task: 'explain' }, REQUEST_PRIORITY.AI_PLUS), 'ok');
  assert.deepEqual(calls, ['a', 'b']);
  assert.equal(pool.snapshot().find((entry) => entry.id === 'a').enabled, false);
});

test('transient and unknown transport failures never retry the same key in one dispatch', async () => {
  const pool = new ProviderKeyPool([
    { id: 'a', secret: 'secret-a', rpmLimit: 20 },
    { id: 'b', secret: 'secret-b', rpmLimit: 20 },
    { id: 'c', secret: 'secret-c', rpmLimit: 20 },
  ], { paidReserveFraction: 0 });
  const calls = [];
  const dispatcher = new GatewayDispatcher({
    keyPool: pool,
    provider: {
      async invoke({ keyId }) {
        calls.push(keyId);
        if (keyId === 'a') {
          throw new ProviderInvocationError(PROVIDER_FAILURE_KIND.TRANSIENT, 'temporary outage');
        }
        if (keyId === 'b') throw new Error('socket reset');
        return 'ok';
      },
    },
  });

  assert.equal(await dispatcher.dispatch({ task: 'explain' }, REQUEST_PRIORITY.PRO), 'ok');
  assert.deepEqual(calls, ['a', 'b', 'c']);
  assert.equal(new Set(calls).size, calls.length);
});

test('request-specific 4xx failure is returned immediately without burning another key', async () => {
  const pool = twoKeyPool();
  const calls = [];
  const dispatcher = new GatewayDispatcher({
    keyPool: pool,
    provider: {
      async invoke({ keyId }) {
        calls.push(keyId);
        throw ProviderInvocationError.fromHttpStatus(422, 'unsupported request');
      },
    },
  });

  await assert.rejects(
    () => dispatcher.dispatch({ task: 'bad-input' }, REQUEST_PRIORITY.FREE),
    (error) => {
      assert.ok(error instanceof GatewayDispatchError);
      assert.equal(error.code, 'PROVIDER_REQUEST_REJECTED');
      assert.equal(error.status, 422);
      assert.deepEqual(error.attemptedKeyIds, ['a']);
      return true;
    },
  );
  assert.deepEqual(calls, ['a']);
  assert.equal(pool.snapshot().find((entry) => entry.id === 'b').requestsInWindow, 0);
});

test('exhausted failures return log-safe key ids and never raw secrets', async () => {
  const pool = twoKeyPool();
  const dispatcher = new GatewayDispatcher({
    keyPool: pool,
    provider: {
      async invoke() {
        throw new ProviderInvocationError(PROVIDER_FAILURE_KIND.TRANSIENT, 'offline');
      },
    },
  });

  await assert.rejects(
    () => dispatcher.dispatch({ task: 'explain' }, REQUEST_PRIORITY.FREE),
    (error) => {
      assert.equal(error.code, 'NO_PROVIDER_CAPACITY');
      assert.deepEqual(error.attemptedKeyIds, ['a', 'b']);
      const serialized = JSON.stringify(error);
      assert.equal(serialized.includes('secret-a'), false);
      assert.equal(serialized.includes('secret-b'), false);
      return true;
    },
  );
});

test('five independent 20 RPM keys reserve 20 of 100 slots for paid traffic', () => {
  const keys = Array.from({ length: 5 }, (_, index) => ({
    id: `key-${index + 1}`,
    secret: `secret-${index + 1}`,
    rpmLimit: 20,
  }));
  const pool = new ProviderKeyPool(keys, { paidReserveFraction: 0.2 });

  let freeLeases = 0;
  while (pool.lease(REQUEST_PRIORITY.FREE)) freeLeases += 1;
  assert.equal(freeLeases, 80);

  let paidLeases = 0;
  while (pool.lease(REQUEST_PRIORITY.AI_PLUS)) paidLeases += 1;
  assert.equal(paidLeases, 20);
  assert.equal(pool.lease(REQUEST_PRIORITY.PRO), null);
});
