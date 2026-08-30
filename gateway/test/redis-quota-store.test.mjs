import test from 'node:test';
import assert from 'node:assert/strict';

import { QUOTA_DECISION } from '../src/nova-ai-service.mjs';
import { RedisQuotaStore, REDIS_QUOTA_SCRIPTS } from '../src/redis-quota-store.mjs';
import { redisQuotaStoreFromEnv } from '../src/quota-store-runtime.mjs';
import { UpstashRedisEvalClient } from '../src/upstash-redis-eval-client.mjs';

function reservationInput(overrides = {}) {
  return {
    reservationId: 'res-123',
    subjectId: 'anon_subject-123',
    dailyBucket: '1724976000000',
    minuteBucket: '1724976060000',
    dailyLimit: 3,
    rpmLimit: 1,
    nowMs: 1724976065000,
    quotaResetAtEpochMs: 1725062400000,
    minuteResetAtEpochMs: 1724976120000,
    ...overrides,
  };
}

test('Redis quota reserve uses one atomic EVAL with scoped daily, minute and reservation keys', async () => {
  const calls = [];
  const store = new RedisQuotaStore({
    evalClient: {
      async eval(script, keys, args) {
        calls.push({ script, keys, args });
        return ['ALLOWED', 2];
      },
    },
    keyPrefix: 'nova:test',
  });

  const outcome = await store.reserve(reservationInput());

  assert.deepEqual(outcome, {
    status: QUOTA_DECISION.ALLOWED,
    remainingRequestHint: 2,
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].script, REDIS_QUOTA_SCRIPTS.reserve);
  assert.deepEqual(calls[0].keys, [
    'nova:test:d:anon_subject-123:1724976000000',
    'nova:test:m:anon_subject-123:1724976060000',
    'nova:test:r:res-123',
  ]);
  assert.equal(calls[0].args[0], 3);
  assert.equal(calls[0].args[1], 1);
  assert.ok(calls[0].args[3] > reservationInput().quotaResetAtEpochMs);
  assert.ok(calls[0].args[4] > reservationInput().minuteResetAtEpochMs);
});

test('Redis quota reserve maps quota and minute limits and rejects duplicate reservations', async () => {
  for (const [redisStatus, expected] of [
    ['QUOTA_EXHAUSTED', QUOTA_DECISION.QUOTA_EXHAUSTED],
    ['RATE_LIMITED', QUOTA_DECISION.RATE_LIMITED],
  ]) {
    const store = new RedisQuotaStore({
      evalClient: { eval: async () => [redisStatus, 0] },
    });
    assert.equal((await store.reserve(reservationInput())).status, expected);
  }

  const duplicate = new RedisQuotaStore({
    evalClient: { eval: async () => ['DUPLICATE', -1] },
  });
  await assert.rejects(() => duplicate.reserve(reservationInput()), /duplicate quota reservation/i);
});

test('commit and release use isolated atomic scripts and opaque reservation ids', async () => {
  const calls = [];
  const store = new RedisQuotaStore({
    evalClient: {
      async eval(script, keys, args) {
        calls.push({ script, keys, args });
        return 1;
      },
    },
  });

  assert.equal(await store.commit('res-commit'), true);
  assert.equal(await store.release('res-release'), true);
  assert.equal(calls[0].script, REDIS_QUOTA_SCRIPTS.commit);
  assert.deepEqual(calls[0].keys, ['nova:quota:v1:r:res-commit']);
  assert.equal(calls[1].script, REDIS_QUOTA_SCRIPTS.release);
  assert.deepEqual(calls[1].keys, ['nova:quota:v1:r:res-release']);
  assert.match(REDIS_QUOTA_SCRIPTS.release, /DECR/);
  assert.doesNotMatch(REDIS_QUOTA_SCRIPTS.release, /minuteKey/);
});

test('quota key components and reset windows fail closed before Redis', async () => {
  let calls = 0;
  const store = new RedisQuotaStore({
    evalClient: { eval: async () => { calls += 1; return ['ALLOWED', 0]; } },
  });
  await assert.rejects(() => store.reserve(reservationInput({ subjectId: '../bad' })), /subjectId/);
  await assert.rejects(() => store.reserve(reservationInput({ quotaResetAtEpochMs: 1 })), /future/);
  assert.equal(calls, 0);
});

test('Upstash REST adapter posts EVAL as JSON without redirecting bearer credentials', async () => {
  let seen;
  const client = new UpstashRedisEvalClient({
    url: 'https://quota.example/',
    token: 'secret-token',
    fetchImpl: async (url, options) => {
      seen = { url, options };
      return fakeResponse(200, { result: ['ALLOWED', 2] });
    },
  });

  const result = await client.eval('return {ARGV[1]}', ['key-1'], ['value-1']);
  assert.deepEqual(result, ['ALLOWED', 2]);
  assert.equal(seen.url, 'https://quota.example/');
  assert.equal(seen.options.method, 'POST');
  assert.equal(seen.options.redirect, 'manual');
  assert.equal(seen.options.headers.Authorization, 'Bearer secret-token');
  const body = JSON.parse(seen.options.body);
  assert.equal(body[0], 'EVAL');
  assert.equal(body[2], '1');
  assert.equal(body[3], 'key-1');
  assert.equal(body[4], 'value-1');
  assert.doesNotMatch(JSON.stringify(client.safeSummary()), /secret-token|quota\.example/i);
});

test('Upstash REST adapter rejects redirects, backend errors and credential-bearing URLs', async () => {
  assert.throws(() => new UpstashRedisEvalClient({
    url: 'https://user:pass@quota.example/',
    token: 'token',
    fetchImpl: async () => fakeResponse(200, { result: 1 }),
  }), /credential-free HTTPS/);

  const redirect = new UpstashRedisEvalClient({
    url: 'https://quota.example/',
    token: 'token',
    fetchImpl: async () => fakeResponse(307, { result: 1 }),
  });
  await assert.rejects(() => redirect.eval('return 1'), /unavailable/);

  const redisError = new UpstashRedisEvalClient({
    url: 'https://quota.example/',
    token: 'token',
    fetchImpl: async () => fakeResponse(200, { error: 'ERR internal detail' }),
  });
  await assert.rejects(() => redisError.eval('return 1'), /rejected command/);
});

test('deployment factory creates a Redis quota store from server-only environment values', async () => {
  let authorization;
  const store = redisQuotaStoreFromEnv({
    NOVA_QUOTA_REDIS_REST_URL: 'https://quota.example/',
    NOVA_QUOTA_REDIS_REST_TOKEN: 'server-token',
    NOVA_QUOTA_REDIS_KEY_PREFIX: 'nova:prod',
  }, {
    fetchImpl: async (_url, options) => {
      authorization = options.headers.Authorization;
      return fakeResponse(200, { result: ['ALLOWED', 1] });
    },
  });

  const outcome = await store.reserve(reservationInput());
  assert.equal(outcome.status, QUOTA_DECISION.ALLOWED);
  assert.equal(authorization, 'Bearer server-token');
});

function fakeResponse(status, payload, contentLength = null) {
  const text = JSON.stringify(payload);
  return {
    status,
    headers: {
      get(name) {
        if (String(name).toLowerCase() === 'content-length' && contentLength !== null) {
          return String(contentLength);
        }
        return null;
      },
    },
    async text() {
      return text;
    },
  };
}
