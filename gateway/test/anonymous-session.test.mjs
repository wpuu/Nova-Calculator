import test from 'node:test';
import assert from 'node:assert/strict';

import {
  ANONYMOUS_SESSION_STATUS,
  AnonymousSessionService,
  createAnonymousSessionFetchHandler,
} from '../src/anonymous-session.mjs';

function tokenService() {
  return {
    issueAnonymous({ installationId }) {
      return {
        token: `signed-for:${installationId}`,
        expiresAtEpochMs: 123456789,
      };
    },
  };
}

function proofVerifier(overrides = {}) {
  return {
    async verify(input) {
      if (overrides.error) throw new Error('proof backend unavailable');
      if (overrides.accepted === false) return { accepted: false };
      return {
        accepted: true,
        bindingId: overrides.bindingId ?? `verified:${input.installationId}`,
      };
    },
  };
}

function jsonRequest(body, options = {}) {
  return new Request('https://nova.invalid/session/anonymous', {
    method: options.method ?? 'POST',
    headers: {
      'content-type': options.contentType ?? 'application/json',
      ...(options.headers ?? {}),
    },
    body: options.method === 'GET' ? undefined : JSON.stringify(body),
  });
}

test('valid installation proof issues a Nova anonymous token bound to verified identity', async () => {
  const calls = [];
  const service = new AnonymousSessionService({
    tokenService: {
      issueAnonymous(input) {
        calls.push(input);
        return tokenService().issueAnonymous(input);
      },
    },
    installationProofVerifier: proofVerifier({ bindingId: 'integrity-binding-1' }),
  });

  const result = await service.issue({ installationId: 'install-1', proof: 'proof-1' });
  assert.equal(result.status, ANONYMOUS_SESSION_STATUS.SUCCESS);
  assert.equal(result.sessionToken, 'signed-for:integrity-binding-1');
  assert.deepEqual(calls, [{ installationId: 'integrity-binding-1' }]);
  assert.equal(JSON.stringify(result).includes('install-1'), false);
});

test('rejected or unavailable installation proof never signs a session token', async () => {
  let issued = 0;
  const signing = {
    issueAnonymous() {
      issued += 1;
      return { token: 'should-not-exist', expiresAtEpochMs: 1 };
    },
  };

  const rejected = new AnonymousSessionService({
    tokenService: signing,
    installationProofVerifier: proofVerifier({ accepted: false }),
  });
  const rejectedResult = await rejected.issue({ installationId: 'install', proof: 'proof' });
  assert.equal(rejectedResult.status, ANONYMOUS_SESSION_STATUS.PROOF_REJECTED);

  const unavailable = new AnonymousSessionService({
    tokenService: signing,
    installationProofVerifier: proofVerifier({ error: true }),
  });
  const unavailableResult = await unavailable.issue({ installationId: 'install', proof: 'proof' });
  assert.equal(unavailableResult.status, ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.equal(issued, 0);
});

test('anonymous endpoint accepts only installationId and proof and rejects client privilege claims', async () => {
  const service = new AnonymousSessionService({
    tokenService: tokenService(),
    installationProofVerifier: proofVerifier(),
  });
  const handler = createAnonymousSessionFetchHandler({ service });

  const ok = await handler(jsonRequest({ installationId: 'install-1', proof: 'proof-1' }));
  assert.equal(ok.status, 200);
  const okBody = await ok.json();
  assert.equal(okBody.status, ANONYMOUS_SESSION_STATUS.SUCCESS);
  assert.equal(typeof okBody.sessionToken, 'string');
  assert.equal(okBody.sessionToken.includes('AI_PLUS'), false);

  for (const body of [
    { installationId: 'install-1', proof: 'proof-1', entitlements: ['AI_PLUS'] },
    { installationId: 'install-1', proof: 'proof-1', priority: 'AI_PLUS' },
    { installationId: 'install-1' },
    { proof: 'proof-1' },
  ]) {
    const response = await handler(jsonRequest(body));
    assert.equal(response.status, 400);
    const payload = await response.json();
    assert.equal(payload.status, ANONYMOUS_SESSION_STATUS.INVALID_REQUEST);
    assert.equal(payload.sessionToken, '');
  }
});

test('anonymous endpoint enforces method, JSON content type and body size before proof verification', async () => {
  let verified = 0;
  const service = new AnonymousSessionService({
    tokenService: tokenService(),
    installationProofVerifier: {
      async verify(input) {
        verified += 1;
        return { accepted: true, bindingId: input.installationId };
      },
    },
  });
  const handler = createAnonymousSessionFetchHandler({ service, maxBodyBytes: 128 });

  const get = await handler(new Request('https://nova.invalid/session/anonymous', { method: 'GET' }));
  assert.equal(get.status, 405);
  assert.equal(get.headers.get('allow'), 'POST');

  const wrongType = await handler(jsonRequest(
    { installationId: 'install', proof: 'proof' },
    { contentType: 'text/plain' },
  ));
  assert.equal(wrongType.status, 415);

  const oversized = await handler(jsonRequest({
    installationId: 'install',
    proof: 'x'.repeat(300),
  }));
  assert.equal(oversized.status, 413);
  assert.equal(verified, 0);
});

test('anonymous endpoint maps proof rejection and verifier outage without leaking internals', async () => {
  const rejectedHandler = createAnonymousSessionFetchHandler({
    service: new AnonymousSessionService({
      tokenService: tokenService(),
      installationProofVerifier: proofVerifier({ accepted: false }),
    }),
  });
  const rejected = await rejectedHandler(jsonRequest({ installationId: 'i', proof: 'p' }));
  assert.equal(rejected.status, 403);
  assert.deepEqual(await rejected.json(), {
    status: ANONYMOUS_SESSION_STATUS.PROOF_REJECTED,
    sessionToken: '',
    expiresAtEpochMs: 0,
  });

  const unavailableHandler = createAnonymousSessionFetchHandler({
    service: new AnonymousSessionService({
      tokenService: tokenService(),
      installationProofVerifier: proofVerifier({ error: true }),
    }),
  });
  const unavailable = await unavailableHandler(jsonRequest({ installationId: 'i', proof: 'p' }));
  assert.equal(unavailable.status, 503);
  const payload = await unavailable.json();
  assert.equal(payload.status, ANONYMOUS_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
  assert.equal(JSON.stringify(payload).includes('proof backend unavailable'), false);
});
