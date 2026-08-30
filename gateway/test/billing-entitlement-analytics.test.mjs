import assert from 'node:assert/strict';
import test from 'node:test';

import {
  BillingEntitlementService,
  NOVA_BILLING_STATUS,
} from '../src/billing-entitlement-service.mjs';
import {
  NOVA_ENTITLEMENT,
  NOVA_PLAY_PRODUCT,
  NOVA_PLAY_PRODUCT_TYPE,
} from '../src/google-play-purchase-verifier.mjs';

function createService(onVerifiedEntitlements) {
  return new BillingEntitlementService({
    authVerifier: {
      verify(value) {
        return value === 'Bearer test-session'
          ? { subjectId: 'anon_subject_1', entitlements: [] }
          : null;
      },
    },
    purchaseVerifier: {
      async verifyPurchase(purchase) {
        return {
          productId: purchase.productId,
          productType: purchase.productType,
          entitled: true,
          state: 'purchased',
        };
      },
    },
    issueEntitlementSession({ entitlements }) {
      return {
        token: `signed-${entitlements.join('+') || 'free'}`,
        expiresAtEpochMs: 1_900_000_000_000,
      };
    },
    onVerifiedEntitlements,
  });
}

function paidRequest() {
  return {
    purchases: [{
      productId: NOVA_PLAY_PRODUCT.PRO_LIFETIME,
      productType: NOVA_PLAY_PRODUCT_TYPE.INAPP,
      purchaseToken: 'test-purchase-token',
    }],
  };
}

test('verified paid entitlement invokes authoritative callback', async () => {
  const observed = [];
  const result = await createService(async (event) => observed.push(event)).execute({
    authorization: 'Bearer test-session',
    request: paidRequest(),
  });

  assert.equal(result.status, NOVA_BILLING_STATUS.SUCCESS);
  assert.deepEqual(result.entitlements, [NOVA_ENTITLEMENT.PRO_LIFETIME]);
  assert.deepEqual(observed, [{
    subjectId: 'anon_subject_1',
    entitlements: [NOVA_ENTITLEMENT.PRO_LIFETIME],
  }]);
});

test('analytics outage cannot turn a verified purchase into billing failure', async () => {
  const result = await createService(async () => {
    throw new Error('metrics unavailable');
  }).execute({
    authorization: 'Bearer test-session',
    request: paidRequest(),
  });

  assert.equal(result.status, NOVA_BILLING_STATUS.SUCCESS);
  assert.deepEqual(result.entitlements, [NOVA_ENTITLEMENT.PRO_LIFETIME]);
  assert.equal(result.sessionToken, 'signed-PRO_LIFETIME');
});

test('free authoritative snapshot does not emit a paid event', async () => {
  let calls = 0;
  const result = await createService(async () => { calls += 1; }).execute({
    authorization: 'Bearer test-session',
    request: { purchases: [] },
  });

  assert.equal(result.status, NOVA_BILLING_STATUS.SUCCESS);
  assert.deepEqual(result.entitlements, []);
  assert.equal(calls, 0);
});
