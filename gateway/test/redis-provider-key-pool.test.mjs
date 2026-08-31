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

test('429 atomically cools the key and saturates its current minute capacity', async () => {
  const calls = [];
  const nowMs = 1_725_000_005_000;
  const minuteStartMs = Math.floor(nowMs / 60_000) * 60_000;
  const pool = new RedisProviderKeyPool(keys(), {
    now: () => nowMs,
    evalClient: {
      async eval(script, redisKeys, args) {
        calls.push({ script, redisKeys, args });
        return [1, 20];
      },
    },
  });

  await pool.reportRateLimit('key-1', 5_000);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.reportRateLimit);
  assert.match(calls[0].redisKeys[0], /:cool:key-1$/);
  assert.match(calls[0].redisKeys[1], /:fail:key-1$/);
  assert.equal(calls[0].redisKeys[2], `nova:provider:v1:m:key-1:${minuteStartMs}`);
  assert.equal(calls[0].args[0], 5_000);
  assert.equal(calls[0].args[2], 20);
  assert.ok(calls[0].args[3] > nowMs);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportRateLimit, /usage < rpmLimit/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportRateLimit, /PEXPIREAT/);
});

test('credential rejection uses bounded shared quarantine and explicit re-enable clears it', async () => {
  const calls = [];
  const pool = new RedisProviderKeyPool(keys(), {
    credentialDisableMs: 3_600_000,
    evalClient: {
      async eval(script, redisKeys, args) {
        calls.push({ script, redisKeys, args });
        return 1;
      },
    },
  });

  await pool.setEnabled('key-2', false);
  await pool.setEnabled('key-2', true);

  assert.equal(calls[0].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.setEnabled);
  assert.deepEqual(calls[0].args, [0, 3_600_000]);
  assert.deepEqual(calls[1].args, [1, 3_600_000]);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.setEnabled, /'PX', disableMs/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.setEnabled, /DEL/);
  assert.equal(pool.safeSummary().credentialDisableMs, 3_600_000);
});

test('shared provider reports success and transient failure atomically', async () => {
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
  await pool.reportFailure('key-2');

  assert.equal(calls[0].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.reportSuccess);
  assert.equal(calls[1].script, REDIS_PROVIDER_CAPACITY_SCRIPTS.reportFailure);
  assert.deepEqual(calls[1].args.slice(0, 2), [4, 25_000]);
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

test('shared provider capacity scripts enforce minute accounting, cooldown and temporary disable state', () => {
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /INCR/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /PEXPIREAT/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /coolingDown/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.lease, /sharedDisabled/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportRateLimit, /rpmLimit/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportFailure, /threshold/);
  assert.match(REDIS_PROVIDER_CAPACITY_SCRIPTS.setEnabled, /disableMs/);
  assert.doesNotMatch(REDIS_PROVIDER_CAPACITY_SCRIPTS.reportSuccess, /cool/);
});

test('invalid credential quarantine duration fails closed before Redis', () => {
  assert.throws(
    () => new RedisProviderKeyPool(keys(), {
      credentialDisableMs: 0,
      evalClient: { eval: async () => 1 },
    }),
    /credentialDisableMs must be positive/,
  );
});
