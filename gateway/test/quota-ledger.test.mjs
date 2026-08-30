import test from 'node:test';
import assert from 'node:assert/strict';

import { QUOTA_DECISION } from '../src/nova-ai-service.mjs';
import { DailyQuotaLedger } from '../src/quota-ledger.mjs';
import {
  DEFAULT_AI_QUOTA_LIMITS,
  quotaLimitsForPriority,
  quotaPolicyFromEnv,
} from '../src/quota-policy.mjs';
import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';

test('default policy keeps free usage small and gives paid tiers larger allowances', () => {
  assert.deepEqual(DEFAULT_AI_QUOTA_LIMITS[REQUEST_PRIORITY.FREE], { dailyLimit: 3, rpmLimit: 1 });
  assert.deepEqual(DEFAULT_AI_QUOTA_LIMITS[REQUEST_PRIORITY.PRO], { dailyLimit: 10, rpmLimit: 3 });
  assert.deepEqual(DEFAULT_AI_QUOTA_LIMITS[REQUEST_PRIORITY.AI_PLUS], { dailyLimit: 200, rpmLimit: 10 });
});

test('quota policy can be changed entirely at deploy time without changing the APK', () => {
  const policy = quotaPolicyFromEnv({
    NOVA_AI_FREE_DAILY_LIMIT: '2',
    NOVA_AI_FREE_RPM_LIMIT: '1',
    NOVA_AI_PRO_DAILY_LIMIT: '15',
    NOVA_AI_PRO_RPM_LIMIT: '4',
    NOVA_AI_PLUS_DAILY_LIMIT: '500',
    NOVA_AI_PLUS_RPM_LIMIT: '12',
  });

  assert.deepEqual(quotaLimitsForPriority(policy, REQUEST_PRIORITY.FREE), { dailyLimit: 2, rpmLimit: 1 });
  assert.deepEqual(quotaLimitsForPriority(policy, REQUEST_PRIORITY.PRO), { dailyLimit: 15, rpmLimit: 4 });
  assert.deepEqual(quotaLimitsForPriority(policy, REQUEST_PRIORITY.AI_PLUS), { dailyLimit: 500, rpmLimit: 12 });
});

test('invalid deploy-time quota values fail closed during startup', () => {
  assert.throws(
    () => quotaPolicyFromEnv({ NOVA_AI_FREE_DAILY_LIMIT: '0' }),
    /positive integer/,
  );
  assert.throws(
    () => quotaPolicyFromEnv({ NOVA_AI_PLUS_RPM_LIMIT: '3.5' }),
    /positive integer/,
  );
});

test('ledger passes tier limits and fixed UTC buckets to the atomic store', async () => {
  const calls = [];
  const store = fakeStore(calls, { status: QUOTA_DECISION.ALLOWED, remainingRequestHint: 199 });
  const nowMs = Date.UTC(2026, 7, 30, 7, 48, 23, 456);
  const ledger = new DailyQuotaLedger({
    store,
    now: () => nowMs,
    newReservationId: () => 'reservation-1',
  });

  const decision = await ledger.reserve({
    subjectId: 'user-1',
    priority: REQUEST_PRIORITY.AI_PLUS,
    operation: 'EXPLAIN_CALCULATION',
    requestId: 'req-1',
  });

  assert.equal(calls.length, 1);
  const request = calls[0];
  assert.equal(request.reservationId, 'reservation-1');
  assert.equal(request.subjectId, 'user-1');
  assert.equal(request.dailyLimit, 200);
  assert.equal(request.rpmLimit, 10);
  assert.equal(request.dailyBucket, String(Date.UTC(2026, 7, 30, 0, 0, 0, 0)));
  assert.equal(request.minuteBucket, String(Date.UTC(2026, 7, 30, 7, 48, 0, 0)));
  assert.equal(decision.status, QUOTA_DECISION.ALLOWED);
  assert.equal(decision.reservationId, 'reservation-1');
  assert.equal(decision.remainingRequestHint, 199);
  assert.equal(decision.quotaResetAtEpochMs, Date.UTC(2026, 7, 31, 0, 0, 0, 0));
});

test('daily exhaustion and per-user RPM limits map to Nova service decisions', async () => {
  let outcome = { status: QUOTA_DECISION.QUOTA_EXHAUSTED, remainingRequestHint: 0 };
  const store = fakeStore([], () => outcome);
  const nowMs = Date.UTC(2026, 7, 30, 7, 48, 59, 500);
  const ledger = new DailyQuotaLedger({
    store,
    now: () => nowMs,
    newReservationId: () => 'reservation-x',
  });

  let decision = await ledger.reserve({ subjectId: 'free-user', priority: REQUEST_PRIORITY.FREE });
  assert.equal(decision.status, QUOTA_DECISION.QUOTA_EXHAUSTED);
  assert.equal(decision.remainingRequestHint, 0);

  outcome = { status: QUOTA_DECISION.RATE_LIMITED, remainingRequestHint: 2 };
  decision = await ledger.reserve({ subjectId: 'plus-user', priority: REQUEST_PRIORITY.AI_PLUS });
  assert.equal(decision.status, QUOTA_DECISION.RATE_LIMITED);
  assert.equal(decision.retryAfterSeconds, 1);
  assert.equal(decision.remainingRequestHint, 2);
});

test('commit and release delegate only opaque reservation ids to the shared store', async () => {
  const committed = [];
  const released = [];
  const store = {
    async reserve() {
      return { status: QUOTA_DECISION.ALLOWED, remainingRequestHint: 2 };
    },
    async commit(id) {
      committed.push(id);
    },
    async release(id) {
      released.push(id);
    },
  };
  const ledger = new DailyQuotaLedger({ store, newReservationId: () => 'r-1' });

  await ledger.commit('r-1');
  await ledger.release('r-2');

  assert.deepEqual(committed, ['r-1']);
  assert.deepEqual(released, ['r-2']);
});

test('invalid store outcomes are rejected instead of silently granting AI usage', async () => {
  const ledger = new DailyQuotaLedger({
    store: fakeStore([], { status: 'UNKNOWN' }),
    newReservationId: () => 'r-invalid',
  });

  await assert.rejects(
    () => ledger.reserve({ subjectId: 'user', priority: REQUEST_PRIORITY.FREE }),
    /invalid status/,
  );
});

function fakeStore(calls, outcomeOrFactory) {
  return {
    async reserve(input) {
      calls.push(input);
      return typeof outcomeOrFactory === 'function' ? outcomeOrFactory(input) : outcomeOrFactory;
    },
    async commit() {},
    async release() {},
  };
}
