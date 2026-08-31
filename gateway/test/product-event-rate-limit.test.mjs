import assert from 'node:assert/strict';
import test from 'node:test';

import {
  NOVA_PRODUCT_EVENT_STATUS,
  ProductEventService,
  createProductEventFetchHandler,
} from '../src/product-event-service.mjs';
import {
  ProductEventRateLimitError,
  RedisProductEventStore,
} from '../src/redis-product-event-store.mjs';

const NOW = Date.UTC(2026, 7, 30, 16, 0, 0);

function requestBody() {
  return {
    eventId: 'evt_1234567890abcdef',
    event: 'autotap_profile_loaded',
    eventVersion: 1,
    occurredAtEpochMs: NOW,
    appVersion: '0.2.0-alpha01',
    sdk: 36,
    properties: {},
  };
}

test('Redis store sends subject-scoped daily rate key and configured limit atomically', async () => {
  const calls = [];
  const store = new RedisProductEventStore({
    dailySubjectLimit: 17,
    evalClient: {
      async eval(script, keys, args) {
        calls.push({ script, keys, args });
        return 1;
      },
    },
  });

  await store.record({
    ...requestBody(),
    subjectId: 'anon_abcdefghijklmnopqrstuvwxyz123456',
    entitlement: 'FREE',
    receivedAtEpochMs: NOW,
  });

  assert.equal(calls.length, 1);
  assert.match(calls[0].keys[3], /:rate:2026-08-30:anon_/);
  assert.equal(calls[0].args[4], 17);
  assert.match(calls[0].script, /SET.*NX.*EX/s);
  assert.match(calls[0].script, /INCR/);
});

test('Redis rate-limit sentinel becomes typed error', async () => {
  const store = new RedisProductEventStore({
    evalClient: { async eval() { return -1; } },
  });

  await assert.rejects(
    store.record({
      ...requestBody(),
      subjectId: 'anon_abcdefghijklmnopqrstuvwxyz123456',
      entitlement: 'FREE',
      receivedAtEpochMs: NOW,
    }),
    ProductEventRateLimitError,
  );
});

test('HTTP event endpoint maps subject rate limit to 429', async () => {
  const service = new ProductEventService({
    authVerifier: {
      verify() {
        return { subjectId: 'anon_abcdefghijklmnopqrstuvwxyz123456', entitlements: [] };
      },
    },
    eventStore: {
      async record() {
        throw new ProductEventRateLimitError();
      },
    },
    now: () => NOW,
  });
  const handler = createProductEventFetchHandler({ service });
  const response = await handler(new Request('https://nova.example/api/product-event', {
    method: 'POST',
    headers: {
      authorization: 'Bearer test-session',
      'content-type': 'application/json',
    },
    body: JSON.stringify(requestBody()),
  }));

  assert.equal(response.status, 429);
  assert.deepEqual(await response.json(), { status: NOVA_PRODUCT_EVENT_STATUS.RATE_LIMITED });
});
