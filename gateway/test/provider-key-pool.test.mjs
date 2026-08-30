import test from 'node:test';
import assert from 'node:assert/strict';

import { ProviderKeyPool, REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';
import { PriorityRequestQueue } from '../src/priority-request-queue.mjs';
import { parseProviderKeys } from '../src/provider-key-config.mjs';

test('balances leases across independently limited keys', () => {
  let now = 0;
  const pool = new ProviderKeyPool([
    { id: 'a', secret: 'secret-a', rpmLimit: 10 },
    { id: 'b', secret: 'secret-b', rpmLimit: 10 },
  ], { now: () => now, paidReserveFraction: 0 });

  assert.equal(pool.lease().id, 'a');
  now += 1;
  assert.equal(pool.lease().id, 'b');
});

test('enforces per-key RPM window and resets after one minute', () => {
  let now = 0;
  const pool = new ProviderKeyPool([
    { id: 'a', secret: 'secret-a', rpmLimit: 2 },
  ], { now: () => now, paidReserveFraction: 0 });

  assert.ok(pool.lease());
  assert.ok(pool.lease());
  assert.equal(pool.lease(), null);

  now = 60_000;
  assert.ok(pool.lease());
});

test('free traffic cannot consume paid reserve capacity', () => {
  const pool = new ProviderKeyPool([
    { id: 'a', secret: 'secret-a', rpmLimit: 10 },
  ], { paidReserveFraction: 0.2 });

  for (let i = 0; i < 8; i += 1) {
    assert.ok(pool.lease(REQUEST_PRIORITY.FREE));
  }
  assert.equal(pool.lease(REQUEST_PRIORITY.FREE), null);

  assert.ok(pool.lease(REQUEST_PRIORITY.PRO));
  assert.ok(pool.lease(REQUEST_PRIORITY.AI_PLUS));
  assert.equal(pool.lease(REQUEST_PRIORITY.AI_PLUS), null);
});

test('rate-limited key is removed until its capacity window recovers', () => {
  let now = 0;
  const pool = new ProviderKeyPool([
    { id: 'a', secret: 'secret-a', rpmLimit: 10 },
    { id: 'b', secret: 'secret-b', rpmLimit: 10 },
  ], { now: () => now, paidReserveFraction: 0 });

  const first = pool.lease(REQUEST_PRIORITY.AI_PLUS);
  assert.equal(first.id, 'a');
  pool.reportRateLimit(first.id, 5_000);

  assert.equal(pool.lease(REQUEST_PRIORITY.AI_PLUS).id, 'b');
  now = 60_000;
  assert.equal(pool.lease(REQUEST_PRIORITY.AI_PLUS).id, 'a');
});

test('snapshot is safe for logs and never exposes raw secrets', () => {
  const pool = new ProviderKeyPool([
    { id: 'a', secret: 'do-not-log-me', rpmLimit: 20 },
  ]);

  const snapshot = pool.snapshot();
  assert.equal(snapshot.length, 1);
  assert.equal(snapshot[0].id, 'a');
  assert.equal(Object.hasOwn(snapshot[0], 'secret'), false);
  assert.equal(JSON.stringify(snapshot).includes('do-not-log-me'), false);
});

test('priority queue always drains AI Plus, then Pro, then Free', () => {
  const queue = new PriorityRequestQueue();
  queue.enqueue(REQUEST_PRIORITY.FREE, 'free-1');
  queue.enqueue(REQUEST_PRIORITY.PRO, 'pro-1');
  queue.enqueue(REQUEST_PRIORITY.AI_PLUS, 'plus-1');
  queue.enqueue(REQUEST_PRIORITY.AI_PLUS, 'plus-2');

  assert.deepEqual(queue.dequeue(), { priority: REQUEST_PRIORITY.AI_PLUS, value: 'plus-1' });
  assert.deepEqual(queue.dequeue(), { priority: REQUEST_PRIORITY.AI_PLUS, value: 'plus-2' });
  assert.deepEqual(queue.dequeue(), { priority: REQUEST_PRIORITY.PRO, value: 'pro-1' });
  assert.deepEqual(queue.dequeue(), { priority: REQUEST_PRIORITY.FREE, value: 'free-1' });
  assert.equal(queue.dequeue(), null);
});

test('provider key config accepts comma/newline lists and de-duplicates values', () => {
  const keys = parseProviderKeys('k1, k2\nk1', 20);
  assert.deepEqual(keys, [
    { id: 'key-1', secret: 'k1', rpmLimit: 20 },
    { id: 'key-2', secret: 'k2', rpmLimit: 20 },
  ]);
});
