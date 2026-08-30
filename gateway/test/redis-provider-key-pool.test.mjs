import test from 'node:test';
import assert from 'node:assert/strict';

import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';
import {
  REDIS_PROVIDER_CAPACITY_SCRIPTS,
  RedisProviderKeyPool,
} from '../src/redis-provider-key-pool.mjs';

function keys() {
  return [
    { id: 'key-1', secret: 'provider-secret-one', rpmLimit: 20 },
    { id: 'key-2', secret: 'provider-secret-two', rpmLimit: 10 },
  ];
}

test('shared provider pool leases by opaque id and never sends raw provider secrets to Redis', async () => {
  const calls = [];
  const pool = new RedisProviderKeyPool(keys(), {
    keyPrefix: 'nova:test:provider',
    paidReserveFraction: 0.2,
    now: () => 1_725_000_005_000,
    evalClient: {
      async eval(script, redisKeys, args) {
        calls.push({ script, redisKeys, args });
        return ['key-2', 8];
      },
    },
  });

  const lease = await pool.lease(REQUEST_PRIORITY.FREE, { excludeIds: ['key-1'] });
  assert.deepEqual(lease, { id: 'key-2', secret: 'provider-secret-two' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.lease);
  assert.equal(calls[0].args[0], 2);
  assert.equal(calls[0].args[1], 1);
  assert.equal(calls[0].args[2], 0.2);
  assert.deepEqual(calls[0].args.slice(4), [
    'key-1', 20, 1, 1,
    'key-2', 10, 1, 0,
  ]);
  const serialized = JSON.stringify(calls);
  assert.equal(serialized.includes('provider-secret-one'), false);
  assert.equal(serialized.includes('provider-secret-two'), false);
  assert.match(calls[0].redisKeys[0], /nova:test:provider:m:key-1:/);
  assert.match(calls[0].redisKeys[3], /nova:test:provider:disabled:key-1$/);
});

test('shared provider pool returns null when Redis reports no globally eligible capacity', async () => {
  const pool = new RedisProviderKeyPool(keys(), {
    evalClient: { eval: async () => ['', -1] },
  });
  assert.equal(await pool.lease(REQUEST_PRIORITY.AI_PLUS), null);
});

test('shared provider reports success, rate limit, transient failure and credential disable atomically', async () => {
  const calls = [];
  const pool = new RedisProviderKeyPool(keys(), {
    maxFailuresBeforeCooldown: 4,
    cooldownOnFailureMs: 25_000,
    evalClient: {
      async eval(script, redisKeys, args) {
        calls.push({ script, redisKeys, args });
        return 1;
      },
    },
  });

  await pool.reportSuccess('key-1');
  await pool.reportRateLimit('key-1', 45_000);
  await pool.reportFailure('key-2');
  await pool.setEnabled('key-2', false);
  await pool.setEnabled('key-2', true);

  assert.equal(calls[0].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.reportSuccess);
  assert.equal(calls[1].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.reportRateLimit);
  assert.equal(calls[1].args[0], 45_000);
  assert.equal(calls[2].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.reportFailure);
  assert.deepEqual(calls[2].args.slice(0, 2), [4, 25_000]);
  assert.equal(calls[3].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.setEnabled);
  assert.equal(calls[3].args[0], 0);
  assert.equal(calls[4].args[0], 1);
});

test('shared provider pool rejects forged Redis selections and invalid key ids', async () => {
  const forged = new RedisProviderKeyPool(keys(), {
    evalClient: { eval: async () => ['key-unknown', 1] },
  });
  await assert.rejects(() => forged.lease(REQUEST_PRIORITY.PRO), /unknown or ineligible/);

  const excluded = new RedisProviderKeyPool(keys(), {
    evalClient: { eval: async () => ['key-1', 1] },
  });
  await assert.rejects(
    () => excluded.lease(REQUEST_PRIORITY.PRO, { excludeIds: ['key-1'] }),
    /unknown or ineligible/,
  );
  await assert.rejects(() => excluded.reportFailure('missing'), /unknown key id/);
});

test('shared provider capacity scripts enforce minute accounting, cooldown and persistent disable state', () => {
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /INCR/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /PEXPIREAT/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /coolingDown/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /sharedDisabled/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportRateLimit, /PTTL/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportFailure, /threshold/);
  assert.doesNotMatch(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportSuccess, /cool/);
});
