import assert from 'node:assert/strict';
import test from 'node:test';

import {
  BillingEntitlementService,
  createBillingEntitlementFetchHandler,
  NOVA_BILLING_STATUS,
} from '../src/billing-entitlement-service.mjs';
import {
  NOVA_ENTITLEMENT,
  NOVA_PLAY_PRODUCT,
  NOVA_PLAY_PRODUCT_TYPE,
} from '../src/google-play-purchase-verifier.mjs';

function service({ verifyPurchase } = {}) {
  return new BillingEntitlementService({
    authVerifier: {
      verify(value) {
        return value === 'Bearer valid-session'
          ? { subjectId: 'anon_subject_1', entitlements: [] }
          : null;
      },
    },
    purchaseVerifier: {
      verifyPurchase: verifyPurchase ?? (async (purchase) => ({
        productId: purchase.productId,
        productType: purchase.productType,
        entitled: true,
        state: 'purchased',
      })),
    },
    issueEntitlementSession({ subjectId, entitlements }) {
      assert.equal(subjectId, 'anon_subject_1');
      return {
        token: `signed-${entitlements.join('+') || 'free'}`,
        expiresAtEpochMs: 1_900_000_000_000,
      };
    },
  });
}

test('requires an existing Nova session before verifying purchases', async () => {
  const result = await service().execute({
    authorization: '',
    request: { purchases: [] },
  });
  assert.equal(result.status, NOVA_BILLING_STATUS.AUTH_REQUIRED);
  assert.equal(result.sessionToken, '');
});

test('full verified snapshot issues AI Plus plus Pro session', async () => {
  const result = await service().execute({
    authorization: 'Bearer valid-session',
    request: {
      purchases: [{
        productId: NOVA_PLAY_PRODUCT.AI_PLUS,
        productType: NOVA_PLAY_PRODUCT_TYPE.SUBS,
        purchaseToken: 'sub-token',
      }],
    },
  });

  assert.equal(result.status, NOVA_BILLING_STATUS.SUCCESS);
  assert.deepEqual(result.entitlements, [NOVA_ENTITLEMENT.AI_PLUS, NOVA_ENTITLEMENT.PRO_LIFETIME]);
  assert.equal(result.sessionToken, 'signed-AI_PLUS+PRO_LIFETIME');
});

test('empty authoritative Play snapshot issues Free entitlement session', async () => {
  const result = await service().execute({
    authorization: 'Bearer valid-session',
    request: { purchases: [] },
  });
  assert.equal(result.status, NOVA_BILLING_STATUS.SUCCESS);
  assert.deepEqual(result.entitlements, []);
  assert.equal(result.sessionToken, 'signed-free');
});

test('transient verification failure does not issue a downgrade token', async () => {
  const result = await service({
    verifyPurchase: async () => { throw new Error('Google unavailable'); },
  }).execute({
    authorization: 'Bearer valid-session',
    request: {
      purchases: [{
        productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
        productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
        purchaseToken: 'pro-token',
      }],
    },
  });

  assert.equal(result.status, NOVA_BILLING_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.equal(result.sessionToken, '');
});

test('HTTP handler rejects duplicate product ids and never echoes purchase tokens', async () => {
  const handler = createBillingEntitlementFetchHandler({ service: service() });
  const secretToken = 'secret-purchase-token-never-echo';
  const response = await handler(new Request('https://nova.example/api/billing', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: 'Bearer valid-session',
    },
    body: JSON.stringify({
      purchases: [
        {
          productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
          productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
          purchaseToken: secretToken,
        },
        {
          productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
          productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
          purchaseToken: 'second-token',
        },
      ],
    }),
  }));

  assert.equal(response.status, 400);
  const text = await response.text();
  assert.equal(text.includes(secretToken), false);
});
