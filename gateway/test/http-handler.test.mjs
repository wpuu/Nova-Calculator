import test from 'node:test';
import assert from 'node:assert/strict';

import { createNovaFetchHandler } from '../src/http-handler.mjs';
import { NOVA_GATEWAY_STATUS } from '../src/nova-ai-service.mjs';

function validBody(overrides = {}) {
  return {
    requestId: 'req-1',
    operation: 'EXPLAIN_CALCULATION',
    expression: '2+2',
    deterministicResult: '4',
    localeTag: 'zh-CN',
    ...overrides,
  };
}

function request(body = validBody(), options = {}) {
  return new Request('https://nova.invalid/ai', {
    method: options.method ?? 'POST',
    headers: {
      'content-type': options.contentType ?? 'application/json',
      authorization: options.authorization ?? 'Bearer session-token',
      ...(options.headers ?? {}),
    },
    body: (options.method ?? 'POST') === 'GET'
      ? undefined
      : typeof body === 'string' ? body : JSON.stringify(body),
  });
}

function successResponse(overrides = {}) {
  return {
    requestId: 'req-1',
    status: NOVA_GATEWAY_STATUS.SUCCESS,
    answer: '解释结果',
    retryAfterSeconds: 0,
    remainingRequestHint: 4,
    quotaResetAtEpochMs: 123,
    ...overrides,
  };
}

test('POST JSON request passes only authorization and Nova request into service', async () => {
  const calls = [];
  const handler = createNovaFetchHandler({
    service: {
      async execute(input) {
        calls.push(input);
        return successResponse();
      },
    },
  });

  const response = await handler(request());
  assert.equal(response.status, 200);
  assert.deepEqual(calls, [{
    authorization: 'Bearer session-token',
    request: validBody(),
  }]);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.equal(response.headers.get('x-content-type-options'), 'nosniff');
  assert.deepEqual(await response.json(), successResponse());
});

test('method, content type, malformed JSON and oversized bodies fail before service', async () => {
  let called = 0;
  const handler = createNovaFetchHandler({
    maxBodyBytes: 64,
    service: {
      async execute() {
        called += 1;
        return successResponse();
      },
    },
  });

  const wrongMethod = await handler(request(null, { method: 'GET' }));
  assert.equal(wrongMethod.status, 405);
  assert.equal(wrongMethod.headers.get('allow'), 'POST');

  const wrongType = await handler(request('{}', { contentType: 'text/plain' }));
  assert.equal(wrongType.status, 415);

  const malformed = await handler(request('{not-json'));
  assert.equal(malformed.status, 400);

  const oversized = await handler(request(JSON.stringify({ requestId: 'r', value: 'x'.repeat(200) })));
  assert.equal(oversized.status, 413);
  assert.equal(called, 0);
});

test('Nova result states map to conservative HTTP statuses', async () => {
  const cases = [
    [NOVA_GATEWAY_STATUS.SUCCESS, 200],
    [NOVA_GATEWAY_STATUS.AUTH_REQUIRED, 401],
    [NOVA_GATEWAY_STATUS.QUOTA_EXHAUSTED, 429],
    [NOVA_GATEWAY_STATUS.RATE_LIMITED, 429],
    [NOVA_GATEWAY_STATUS.INVALID_REQUEST, 400],
    [NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE, 503],
  ];

  for (const [status, expectedHttp] of cases) {
    const handler = createNovaFetchHandler({
      service: { async execute() { return successResponse({ status, answer: 'internal answer' }); } },
    });
    const response = await handler(request());
    const body = await response.json();
    assert.equal(response.status, expectedHttp);
    assert.equal(body.status, status);
    if (status !== NOVA_GATEWAY_STATUS.SUCCESS) assert.equal(body.answer, '');
  }
});

test('unexpected service exceptions become generic 503 without leaking internal details', async () => {
  const handler = createNovaFetchHandler({
    service: {
      async execute() {
        throw new Error('secret provider host key-9 database internals');
      },
    },
  });

  const response = await handler(request(validBody({ requestId: 'client-42' })));
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.equal(body.requestId, 'client-42');
  assert.equal(body.status, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.equal(JSON.stringify(body).includes('provider'), false);
  assert.equal(JSON.stringify(body).includes('database'), false);
  assert.equal(JSON.stringify(body).includes('key-9'), false);
});

test('handler sanitizes malformed service response instead of forwarding arbitrary fields', async () => {
  const handler = createNovaFetchHandler({
    service: {
      async execute() {
        return {
          requestId: 'req-1',
          status: 'UNKNOWN_INTERNAL_STATUS',
          answer: 'should disappear',
          provider: 'private-upstream',
          apiKey: 'private-key',
        };
      },
    },
  });

  const response = await handler(request());
  assert.equal(response.status, 503);
  const body = await response.json();
  assert.equal(body.status, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.equal(body.answer, '');
  assert.equal(Object.hasOwn(body, 'provider'), false);
  assert.equal(Object.hasOwn(body, 'apiKey'), false);
});
