import assert from 'node:assert/strict';
import test from 'node:test';

import {
  entitlementsFromVerifiedPurchases,
  GooglePlayPurchaseVerifier,
  NOVA_ENTITLEMENT,
  NOVA_PLAY_PRODUCT,
  NOVA_PLAY_PRODUCT_TYPE,
} from '../src/google-play-purchase-verifier.mjs';

function jsonResponse(status, payload) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function emptyResponse(status = 200) {
  return new Response('', { status });
}

function tokenProvider() {
  return {
    invalidations: 0,
    async getAccessToken() { return 'server-token'; },
    invalidate() { this.invalidations += 1; },
  };
}

test('verifies and acknowledges purchased Pro Lifetime', async () => {
  const calls = [];
  const verifier = new GooglePlayPurchaseVerifier({
    packageName: 'com.example.nova',
    accessTokenProvider: tokenProvider(),
    fetchImpl: async (url, options) => {
      calls.push({ url, method: options.method });
      if (options.method === 'GET') {
        return jsonResponse(200, {
          purchaseState: 0,
          acknowledgementState: 0,
          productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
        });
      }
      return emptyResponse();
    },
  });

  const result = await verifier.verifyPurchase({
    productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
    productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
    purchaseToken: 'purchase-token-1',
  });

  assert.equal(result.entitled, true);
  assert.equal(result.acknowledgedNow, true);
  assert.equal(result.entitlement, NOVA_ENTITLEMENT.PRO_LIFETIME);
  assert.equal(calls.length, 2);
  assert.match(calls[1].url, /:acknowledge$/);
});

test('does not grant pending or canceled one-time purchase', async () => {
  const verifier = new GooglePlayPurchaseVerifier({
    packageName: 'com.example.nova',
    accessTokenProvider: tokenProvider(),
    fetchImpl: async () => jsonResponse(200, {
      purchaseState: 2,
      acknowledgementState: 0,
      productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
    }),
  });

  const result = await verifier.verifyPurchase({
    productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
    productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
    purchaseToken: 'pending-token',
  });
  assert.equal(result.entitled, false);
  assert.equal(result.acknowledgedNow, false);
});

test('AI Plus active subscription grants AI Plus and Pro', async () => {
  let calls = 0;
  const verifier = new GooglePlayPurchaseVerifier({
    packageName: 'com.example.nova',
    accessTokenProvider: tokenProvider(),
    fetchImpl: async (_url, options) => {
      calls += 1;
      if (options.method === 'GET') {
        return jsonResponse(200, {
          subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
          acknowledgementState: 'ACKNOWLEDGEMENT_STATE_PENDING',
          lineItems: [{
            productId: NOVA_PLAY_PRODUCT.AI_PLUS,
            expiryTime: '2030-01-01T00:00:00Z',
          }],
        });
      }
      return emptyResponse();
    },
  });

  const verified = await verifier.verifyPurchase({
    productId: NOVA_PLAY_PRODUCT.AI_PLUS,
    productType: NOVA_PLAY_PRODUCT_TYPE.SUBS,
    purchaseToken: 'subscription-token',
  });
  const entitlements = entitlementsFromVerifiedPurchases([verified]);

  assert.equal(verified.entitled, true);
  assert.equal(verified.acknowledgedNow, true);
  assert.deepEqual(entitlements, [NOVA_ENTITLEMENT.AI_PLUS, NOVA_ENTITLEMENT.PRO_LIFETIME]);
  assert.equal(calls, 2);
});

test('canceled subscription remains entitled only through paid expiry', async () => {
  const now = Date.parse('2026-08-30T00:00:00Z');
  const makeVerifier = (expiryTime) => new GooglePlayPurchaseVerifier({
    packageName: 'com.example.nova',
    accessTokenProvider: tokenProvider(),
    now: () => now,
    fetchImpl: async () => jsonResponse(200, {
      subscriptionState: 'SUBSCRIPTION_STATE_CANCELED',
      acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
      lineItems: [{ productId: NOVA_PLAY_PRODUCT.AI_PLUS, expiryTime }],
    }),
  });

  const future = await makeVerifier('2026-09-30T00:00:00Z').verifyPurchase({
    productId: NOVA_PLAY_PRODUCT.AI_PLUS,
    productType: NOVA_PLAY_PRODUCT_TYPE.SUBS,
    purchaseToken: 'future-cancel-token',
  });
  const expired = await makeVerifier('2026-08-01T00:00:00Z').verifyPurchase({
    productId: NOVA_PLAY_PRODUCT.AI_PLUS,
    productType: NOVA_PLAY_PRODUCT_TYPE.SUBS,
    purchaseToken: 'expired-cancel-token',
  });

  assert.equal(future.entitled, true);
  assert.equal(expired.entitled, false);
});

test('rejects client-supplied unknown product ids before calling Google', async () => {
  let called = false;
  const verifier = new GooglePlayPurchaseVerifier({
    packageName: 'com.example.nova',
    accessTokenProvider: tokenProvider(),
    fetchImpl: async () => {
      called = true;
      return jsonResponse(200, {});
    },
  });

  await assert.rejects(
    verifier.verifyPurchase({
      productId: 'attacker_product',
      productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
      purchaseToken: 'token',
    }),
    /unknown Nova Play product/,
  );
  assert.equal(called, false);
});
