import test from 'node:test';
import assert from 'node:assert/strict';

import { GatewayDispatchError } from '../src/gateway-dispatcher.mjs';
import {
  NOVA_GATEWAY_STATUS,
  NovaAiService,
  QUOTA_DECISION,
  SERVER_ENTITLEMENT,
} from '../src/nova-ai-service.mjs';
import { REQUEST_PRIORITY } from '../src/provider-key-pool.mjs';

function request(overrides = {}) {
  return {
    requestId: 'req-1',
    operation: 'EXPLAIN_CALCULATION',
    expression: '2+2',
    deterministicResult: '4',
    localeTag: 'zh-CN',
    ...overrides,
  };
}

function allowedQuota(overrides = {}) {
  return {
    status: QUOTA_DECISION.ALLOWED,
    reservationId: 'reservation-1',
    remainingRequestHint: 4,
    quotaResetAtEpochMs: 123456,
    ...overrides,
  };
}

function createService(options = {}) {
  const calls = {
    auth: [],
    reserve: [],
    commit: [],
    release: [],
    dispatch: [],
  };
  const principal = options.principal === undefined
    ? { subjectId: 'user-1', entitlements: [] }
    : options.principal;
  const quotaDecision = options.quotaDecision ?? allowedQuota();
  const dispatcherResult = options.dispatcherResult ?? { answer: '因为 2 加 2 等于 4。' };

  const service = new NovaAiService({
    authVerifier: {
      async verify(authorization) {
        calls.auth.push(authorization);
        if (options.authError) throw new Error('auth unavailable');
        return principal;
      },
    },
    quotaLedger: {
      async reserve(context) {
        calls.reserve.push(context);
        if (options.quotaError) throw new Error('quota unavailable');
        return quotaDecision;
      },
      async commit(id) {
        calls.commit.push(id);
        if (options.commitError) throw new Error('commit failed');
      },
      async release(id) {
        calls.release.push(id);
        if (options.releaseError) throw new Error('release failed');
      },
    },
    dispatcher: {
      async dispatch(aiRequest, priority) {
        calls.dispatch.push({ aiRequest, priority });
        if (options.dispatchError) throw options.dispatchError;
        return dispatcherResult;
      },
    },
  });

  return { service, calls };
}

test('invalid or failed server authentication stops before quota and provider', async () => {
  for (const options of [{ principal: null }, { authError: true }]) {
    const { service, calls } = createService(options);
    const result = await service.execute({ authorization: 'Bearer client-token', request: request() });

    assert.equal(result.status, NOVA_GATEWAY_STATUS.AUTH_REQUIRED);
    assert.equal(calls.reserve.length, 0);
    assert.equal(calls.dispatch.length, 0);
  }
});

test('server entitlements, not client claims, choose request priority', async () => {
  const cases = [
    { entitlements: [], expected: REQUEST_PRIORITY.FREE },
    { entitlements: [SERVER_ENTITLEMENT.PRO_LIFETIME], expected: REQUEST_PRIORITY.PRO },
    { entitlements: [SERVER_ENTITLEMENT.AI_PLUS], expected: REQUEST_PRIORITY.AI_PLUS },
    { entitlements: [SERVER_ENTITLEMENT.PRO_LIFETIME, SERVER_ENTITLEMENT.AI_PLUS], expected: REQUEST_PRIORITY.AI_PLUS },
  ];

  for (const item of cases) {
    const { service, calls } = createService({
      principal: { subjectId: 'user-1', entitlements: item.entitlements },
    });
    await service.execute({
      authorization: 'Bearer token',
      request: request({ entitlements: [SERVER_ENTITLEMENT.AI_PLUS] }),
    });

    assert.equal(calls.dispatch[0].priority, item.expected);
    assert.equal(calls.reserve[0].priority, item.expected);
  }
});

test('quota exhausted and per-user rate limit return Nova-level states before provider dispatch', async () => {
  const exhausted = createService({
    quotaDecision: {
      status: QUOTA_DECISION.QUOTA_EXHAUSTED,
      remainingRequestHint: 0,
      quotaResetAtEpochMs: 5000,
    },
  });
  const exhaustedResult = await exhausted.service.execute({ authorization: 'token', request: request() });
  assert.equal(exhaustedResult.status, NOVA_GATEWAY_STATUS.QUOTA_EXHAUSTED);
  assert.equal(exhaustedResult.remainingRequestHint, 0);
  assert.equal(exhaustedResult.quotaResetAtEpochMs, 5000);
  assert.equal(exhausted.calls.dispatch.length, 0);

  const limited = createService({
    quotaDecision: {
      status: QUOTA_DECISION.RATE_LIMITED,
      retryAfterSeconds: 9,
      remainingRequestHint: 3,
      quotaResetAtEpochMs: 9000,
    },
  });
  const limitedResult = await limited.service.execute({ authorization: 'token', request: request() });
  assert.equal(limitedResult.status, NOVA_GATEWAY_STATUS.RATE_LIMITED);
  assert.equal(limitedResult.retryAfterSeconds, 9);
  assert.equal(limited.calls.dispatch.length, 0);
});

test('successful AI answer commits the reserved quota exactly once', async () => {
  const { service, calls } = createService();
  const result = await service.execute({ authorization: 'token', request: request() });

  assert.equal(result.status, NOVA_GATEWAY_STATUS.SUCCESS);
  assert.equal(result.answer, '因为 2 加 2 等于 4。');
  assert.equal(result.remainingRequestHint, 4);
  assert.deepEqual(calls.commit, ['reservation-1']);
  assert.deepEqual(calls.release, []);
  assert.equal(calls.dispatch.length, 1);
  assert.deepEqual(calls.dispatch[0].aiRequest, request());
});

test('provider capacity failure releases quota and never exposes key ids', async () => {
  const { service, calls } = createService({
    dispatchError: new GatewayDispatchError(
      'NO_PROVIDER_CAPACITY',
      'all provider credentials failed',
      { attemptedKeyIds: ['key-1', 'key-2'] },
    ),
  });
  const result = await service.execute({ authorization: 'token', request: request() });

  assert.equal(result.status, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.deepEqual(calls.release, ['reservation-1']);
  assert.equal(JSON.stringify(result).includes('key-1'), false);
  assert.equal(JSON.stringify(result).includes('provider credentials'), false);
});

test('provider request rejection releases quota and becomes INVALID_REQUEST', async () => {
  const { service, calls } = createService({
    dispatchError: new GatewayDispatchError(
      'PROVIDER_REQUEST_REJECTED',
      'provider rejected request',
      { status: 422, attemptedKeyIds: ['key-1'] },
    ),
  });
  const result = await service.execute({ authorization: 'token', request: request() });

  assert.equal(result.status, NOVA_GATEWAY_STATUS.INVALID_REQUEST);
  assert.deepEqual(calls.release, ['reservation-1']);
  assert.equal(result.answer, '');
});

test('invalid client request fails before authentication and preserves a valid request id when possible', async () => {
  const { service, calls } = createService();
  const badOperation = await service.execute({
    authorization: 'token',
    request: request({ operation: 'DO_SOMETHING_ELSE' }),
  });
  assert.equal(badOperation.status, NOVA_GATEWAY_STATUS.INVALID_REQUEST);
  assert.equal(badOperation.requestId, 'req-1');
  assert.equal(calls.auth.length, 0);

  const missingId = await service.execute({ authorization: 'token', request: request({ requestId: '' }) });
  assert.equal(missingId.status, NOVA_GATEWAY_STATUS.INVALID_REQUEST);
  assert.equal(missingId.requestId, 'invalid');
});

test('accounting settlement failures do not discard a successful provider answer', async () => {
  const { service, calls } = createService({ commitError: true });
  const result = await service.execute({ authorization: 'token', request: request() });

  assert.equal(result.status, NOVA_GATEWAY_STATUS.SUCCESS);
  assert.equal(result.answer, '因为 2 加 2 等于 4。');
  assert.deepEqual(calls.commit, ['reservation-1']);
});

test('quota backend failure fails closed before consuming upstream capacity', async () => {
  const { service, calls } = createService({ quotaError: true });
  const result = await service.execute({ authorization: 'token', request: request() });

  assert.equal(result.status, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.equal(calls.dispatch.length, 0);
});
