import assert from 'node:assert/strict';
import test from 'node:test';

import { createProductFunnelFetchHandler } from '../src/product-funnel-service.mjs';
import { RedisProductEventStore } from '../src/redis-product-event-store.mjs';

const ADMIN = 'test_admin_token_abcdefghijklmnopqrstuvwxyz';
const NOW = Date.UTC(2026, 7, 30, 16, 0, 0);

test('aggregate funnel requires admin bearer token and never exposes subjects', async () => {
  const handler = createProductFunnelFetchHandler({
    store: {
      async readDaily(date, dimensions) {
        assert.equal(date, '2026-08-30');
        return dimensions.map((item, index) => ({
          ...item,
          count: index === 0 ? 12 : 0,
          unique: index === 0 ? 9 : 0,
        }));
      },
    },
    adminToken: ADMIN,
    now: () => NOW,
  });

  const unauthorized = await handler(new Request(
    'https://nova.example/api/product-funnel?date=2026-08-30'));
  assert.equal(unauthorized.status, 401);

  const response = await handler(new Request(
    'https://nova.example/api/product-funnel?date=2026-08-30', {
      headers: { authorization: `Bearer ${ADMIN}` },
    }));
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.status, 'OK');
  assert.equal(body.date, '2026-08-30');
  assert.equal(body.rows[0].count, 12);
  assert.equal(body.rows[0].unique, 9);
  const serialized = JSON.stringify(body);
  assert.equal(serialized.includes('subjectId'), false);
  assert.equal(serialized.includes('eventId'), false);
  assert.equal(serialized.includes('anon_'), false);
});

test('aggregate funnel rejects future dates and non-GET methods', async () => {
  const handler = createProductFunnelFetchHandler({
    store: { async readDaily() { return []; } },
    adminToken: ADMIN,
    now: () => NOW,
  });

  const future = await handler(new Request(
    'https://nova.example/api/product-funnel?date=2026-09-01', {
      headers: { authorization: `Bearer ${ADMIN}` },
    }));
  assert.equal(future.status, 400);

  const post = await handler(new Request('https://nova.example/api/product-funnel', {
    method: 'POST',
    headers: { authorization: `Bearer ${ADMIN}` },
  }));
  assert.equal(post.status, 405);
});

test('Redis aggregate reader maps HGETALL counts and HLL uniques to fixed dimensions', async () => {
  const calls = [];
  const store = new RedisProductEventStore({
    evalClient: {
      async eval(script, keys, args) {
        calls.push({ script, keys, args });
        return [
          ['autotap_settings_opened|FREE', '14', 'pro_purchase_verified|PRO_LIFETIME', '2'],
          ['11', '2', '0'],
        ];
      },
    },
  });

  const rows = await store.readDaily('2026-08-30', [
    { event: 'autotap_settings_opened', entitlement: 'FREE' },
    { event: 'pro_purchase_verified', entitlement: 'PRO_LIFETIME' },
    { event: 'pro_purchase_verified', entitlement: 'AI_PLUS' },
  ]);

  assert.deepEqual(rows, [
    { event: 'autotap_settings_opened', entitlement: 'FREE', count: 14, unique: 11 },
    { event: 'pro_purchase_verified', entitlement: 'PRO_LIFETIME', count: 2, unique: 2 },
    { event: 'pro_purchase_verified', entitlement: 'AI_PLUS', count: 0, unique: 0 },
  ]);
  assert.equal(calls.length, 1);
  assert.match(calls[0].keys[0], /:count:2026-08-30$/);
  assert.equal(calls[0].keys.length, 4);
  assert.deepEqual(calls[0].args, []);
});
