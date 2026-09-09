import test from 'node:test';
import assert from 'node:assert/strict';

import {
  BrowserSessionService,
  BROWSER_SESSION_STATUS,
  createBrowserSessionFetchHandler,
} from '../src/browser-session.mjs';
import {
  GoogleBrowserAccessTokenVerifier,
} from '../src/google-browser-access-token-verifier.mjs';
import { NovaSessionTokenService } from '../src/session-token.mjs';

const NOW = 1_800_000_000_000;
const CLIENT_ID = 'chrome-client.apps.googleusercontent.com';
const SIGNING = 'browser-session-signing-secret-0123456789';
const SUBJECT = 'browser-session-subject-secret-0123456789';

function tokenService() {
  return new NovaSessionTokenService({
    secret: SIGNING,
    subjectSecret: SUBJECT,
    now: () => NOW,
  });
}

function googleResponse(overrides = {}, status = 200) {
  return new Response(JSON.stringify({
    aud: CLIENT_ID,
    azp: CLIENT_ID,
    sub: 'google-user-123',
    scope: 'openid',
    exp: Math.floor((NOW + 30 * 60 * 1000) / 1000),
    email: 'must-not-enter-nova@example.com',
    ...overrides,
  }), { status, headers: { 'content-type': 'application/json' } });
}

test('Google verifier accepts only configured audience/openid and returns minimum identity', async () => {
  const calls = [];
  const verifier = new GoogleBrowserAccessTokenVerifier({
    clientIds: [CLIENT_ID],
    now: () => NOW,
    fetchImpl: async (url, options) => {
      calls.push({ url: String(url), options });
      return googleResponse();
    },
  });
  const result = await verifier.verify({ accessToken: 'google-access-secret' });
  assert.deepEqual(result, {
    accepted: true,
    subjectId: 'google-user-123',
    expiresAtEpochMs: NOW + 30 * 60 * 1000,
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url.startsWith('https://oauth2.googleapis.com/tokeninfo?'), true);
  assert.equal(calls[0].options.redirect, 'error');
  assert.equal(Object.hasOwn(result, 'email'), false);
  assert.equal(Object.hasOwn(result, 'aud'), false);
});

test('Google verifier rejects wrong audience, azp, missing openid and expired tokens', async () => {
  for (const payload of [
    { aud: 'other.apps.googleusercontent.com' },
    { azp: 'other.apps.googleusercontent.com' },
    { scope: 'profile' },
    { exp: Math.floor((NOW + 10_000) / 1000) },
  ]) {
    const verifier = new GoogleBrowserAccessTokenVerifier({
      clientIds: [CLIENT_ID],
      now: () => NOW,
      fetchImpl: async () => googleResponse(payload),
    });
    assert.deepEqual(await verifier.verify({ accessToken: 'token' }), { accepted: false });
  }
});

test('Google verifier treats invalid token as rejection and upstream outage as temporary failure', async () => {
  const invalid = new GoogleBrowserAccessTokenVerifier({
    clientIds: [CLIENT_ID],
    now: () => NOW,
    fetchImpl: async () => googleResponse({}, 401),
  });
  assert.deepEqual(await invalid.verify({ accessToken: 'bad-token' }), { accepted: false });

  const unavailable = new GoogleBrowserAccessTokenVerifier({
    clientIds: [CLIENT_ID],
    now: () => NOW,
    fetchImpl: async () => googleResponse({}, 500),
  });
  await assert.rejects(() => unavailable.verify({ accessToken: 'token' }), /temporarily unavailable/);
});

test('browser session caps Nova TTL below Google token expiry and leaks no Google identity fields', async () => {
  const tokens = tokenService();
  const service = new BrowserSessionService({
    tokenService: tokens,
    identityVerifier: {
      verify: async () => ({
        accepted: true,
        subjectId: 'google-user-123',
        expiresAtEpochMs: NOW + 20 * 60 * 1000,
      }),
    },
    now: () => NOW,
    sessionTtlMs: 60 * 60 * 1000,
  });
  const issued = await service.issue({ accessToken: 'google-secret' });
  assert.equal(issued.status, BROWSER_SESSION_STATUS.SUCCESS);
  assert.equal(issued.expiresAtEpochMs, NOW + 19 * 60 * 1000 + 30 * 1000);
  const principal = tokens.verify(`Bearer ${issued.sessionToken}`);
  assert.equal(principal.sessionKind, 'account');
  assert.deepEqual(principal.entitlements, []);
  assert.equal(principal.subjectId.startsWith('acct_'), true);
  assert.equal(JSON.stringify(issued).includes('google-user-123'), false);
  assert.equal(JSON.stringify(issued).includes('google-secret'), false);
});

test('browser session rejects identity close to expiry and maps verifier outage to temporary unavailable', async () => {
  const tokens = tokenService();
  const expiring = new BrowserSessionService({
    tokenService: tokens,
    identityVerifier: { verify: async () => ({ accepted: true, subjectId: 'u', expiresAtEpochMs: NOW + 45_000 }) },
    now: () => NOW,
  });
  assert.equal((await expiring.issue({ accessToken: 'token' })).status, BROWSER_SESSION_STATUS.IDENTITY_REJECTED);

  const outage = new BrowserSessionService({
    tokenService: tokens,
    identityVerifier: { verify: async () => { throw new Error('google secret internal'); } },
    now: () => NOW,
  });
  assert.deepEqual(await outage.issue({ accessToken: 'token' }), {
    status: BROWSER_SESSION_STATUS.TEMPORARILY_UNAVAILABLE,
    sessionToken: '',
    expiresAtEpochMs: 0,
  });
});

test('browser session HTTP handler accepts accessToken only and never accepts privilege claims', async () => {
  let calls = 0;
  const handler = createBrowserSessionFetchHandler({
    service: {
      issue: async ({ accessToken }) => {
        calls += 1;
        assert.equal(accessToken, 'google-token');
        return { status: BROWSER_SESSION_STATUS.IDENTITY_REJECTED };
      },
    },
  });

  const extra = await handler(new Request('https://nova.example/api/browser-session', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ accessToken: 'google-token', entitlements: ['AI_PLUS'] }),
  }));
  assert.equal(extra.status, 400);
  assert.equal(calls, 0);

  const okShape = await handler(new Request('https://nova.example/api/browser-session', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ accessToken: 'google-token' }),
  }));
  assert.equal(okShape.status, 403);
  assert.equal(calls, 1);
  assert.deepEqual(await okShape.json(), {
    status: BROWSER_SESSION_STATUS.IDENTITY_REJECTED,
    sessionToken: '',
    expiresAtEpochMs: 0,
  });
});

test('custom short account TTL leaves existing default account TTL behavior intact', () => {
  const tokens = tokenService();
  const short = tokens.issueAccount({ accountId: 'google:u1', ttlMs: 10 * 60 * 1000 });
  const normal = tokens.issueAccount({ accountId: 'normal-account' });
  assert.equal(short.expiresAtEpochMs, NOW + 10 * 60 * 1000);
  assert.equal(normal.expiresAtEpochMs, NOW + 24 * 60 * 60 * 1000);
});
