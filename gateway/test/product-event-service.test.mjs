import assert from 'node:assert/strict';
import test from 'node:test';

import {
  NOVA_PRODUCT_EVENT_STATUS,
  ProductEventService,
  createProductEventFetchHandler,
} from '../src/product-event-service.mjs';
import { RedisProductEventStore } from '../src/redis-product-event-store.mjs';

const NOW = Date.UTC(2026, 7, 30, 16, 0, 0);

function serviceWith(store, principal = {
  subjectId: 'anon_abcdefghijklmnopqrstuvwxyz123456',
  entitlements: [],
}) {
  return new ProductEventService({
    authVerifier: { verify: (authorization) => authorization === 'Bearer good' ? principal : null },
    eventStore: store,
    now: () => NOW,
  });
}

function validEvent(overrides = {}) {
  return {
    eventId: 'evt_1234567890abcdef',
    event: 'autotap_settings_opened',
    eventVersion: 1,
    occurredAtEpochMs: NOW,
    appVersion: '0.2.0-alpha01',
    sdk: 36,
    properties: { source: 'main_menu' },
    ...overrides,
  };
}

test('authenticated event derives entitlement from signed principal and records no client identity', async () => {
  const recorded = [];
  const service = serviceWith({ record: async (event) => recorded.push(event) }, {
    subjectId: 'anon_abcdefghijklmnopqrstuvwxyz123456',
    entitlements: ['PRO_LIFETIME'],
  });

  const result = await service.execute({ authorization: 'Bearer good', request: validEvent() });

  assert.equal(result.status, NOVA_PRODUCT_EVENT_STATUS.ACCEPTED);
  assert.equal(recorded.length, 1);
  assert.equal(recorded[0].entitlement, 'PRO_LIFETIME');
  assert.equal(recorded[0].subjectId, 'anon_abcdefghijklmnopqrstuvwxyz123456');
  assert.deepEqual(recorded[0].properties, { source: 'main_menu' });
});

test('arbitrary coordinates raw text and unknown top-level identity fields are rejected', async () => {
  const store = { record: async () => { throw new Error('must not record'); } };
  const service = serviceWith(store);

  for (const request of [
    validEvent({ properties: { source: 'main_menu', x: 100, y: 200 } }),
    validEvent({ properties: { source: 'main_menu', rawText: '2+2' } }),
    { ...validEvent(), advertisingId: 'should-never-be-accepted' },
  ]) {
    const result = await service.execute({ authorization: 'Bearer good', request });
    assert.equal(result.status, NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST);
  }
});

test('run failure accepts only bounded diagnostic code and manufacturer', async () => {
  const recorded = [];
  const service = serviceWith({ record: async (event) => recorded.push(event) });
  const result = await service.execute({
    authorization: 'Bearer good',
    request: validEvent({
      event: 'autotap_run_failed',
      properties: { failureCode: 7, manufacturer: 'Google' },
    }),
  });

  assert.equal(result.status, NOVA_PRODUCT_EVENT_STATUS.ACCEPTED);
  assert.deepEqual(recorded[0].properties, { failureCode: 7, manufacturer: 'Google' });
});

test('fetch handler requires auth and application/json', async () => {
  const handler = createProductEventFetchHandler({
    service: serviceWith({ record: async () => {} }),
  });

  const unauthenticated = await handler(new Request('https://nova.example/api/product-event', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(validEvent()),
  }));
  assert.equal(unauthenticated.status, 401);

  const wrongType = await handler(new Request('https://nova.example/api/product-event', {
    method: 'POST',
    headers: { authorization: 'Bearer good', 'content-type': 'text/plain' },
    body: JSON.stringify(validEvent()),
  }));
  assert.equal(wrongType.status, 415);
});

test('Redis store deduplicates by event id and stores only aggregate keys plus pseudonymous subject', async () => {
  const calls = [];
  const store = new RedisProductEventStore({
    evalClient: {
      async eval(script, keys, args) {
        calls.push({ script, keys, args });
        return 1;
      },
    },
  });

  const accepted = await store.record({
    ...validEvent(),
    subjectId: 'anon_abcdefghijklmnopqrstuvwxyz123456',
    entitlement: 'FREE',
    receivedAtEpochMs: NOW,
  });

  assert.equal(accepted, true);
  assert.equal(calls.length, 1);
  assert.match(calls[0].keys[0], /:dedupe:evt_1234567890abcdef$/);
  assert.match(calls[0].keys[1], /:count:2026-08-30$/);
  assert.match(calls[0].keys[2], /:unique:2026-08-30:autotap_settings_opened:FREE$/);
  assert.equal(calls[0].args[0], 'anon_abcdefghijklmnopqrstuvwxyz123456');
  assert.equal(calls[0].args[1], 'autotap_settings_opened|FREE');
  const serialized = JSON.stringify(calls[0]);
  assert.equal(serialized.includes('2+2'), false);
  assert.equal(serialized.includes('screenshot'), false);
  assert.equal(serialized.includes('coordinates'), false);
});
