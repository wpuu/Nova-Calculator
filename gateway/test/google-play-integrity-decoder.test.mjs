import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify as verifySignature } from 'node:crypto';

import {
  GOOGLE_PLAY_INTEGRITY_SCOPE,
  GoogleServiceAccountAccessTokenProvider,
  googleServiceAccountAccessTokenProviderFromEnv,
} from '../src/google-service-account-token.mjs';
import {
  GooglePlayIntegrityDecoder,
  googlePlayIntegrityDecoderFromEnv,
} from '../src/google-play-integrity-decoder.mjs';

function testKeyPair() {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  return {
    privatePem: privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(),
    publicKey,
  };
}

test('Google service-account provider signs the documented JWT bearer assertion and caches access tokens', async () => {
  const keys = testKeyPair();
  const seen = [];
  let calls = 0;
  const provider = new GoogleServiceAccountAccessTokenProvider({
    clientEmail: 'nova-test@example-project.iam.gserviceaccount.com',
    privateKey: keys.privatePem,
    privateKeyId: 'kid_123',
    now: () => 1_725_000_000_000,
    fetchImpl: async (url, options) => {
      calls += 1;
      seen.push({ url, options });
      return fakeResponse(200, { access_token: 'access-token-1', expires_in: 3600 });
    },
  });

  assert.equal(await provider.getAccessToken(), 'access-token-1');
  assert.equal(await provider.getAccessToken(), 'access-token-1');
  assert.equal(calls, 1);
  assert.equal(seen[0].url, 'https://oauth2.googleapis.com/token');
  assert.equal(seen[0].options.redirect, 'manual');

  const form = new URLSearchParams(seen[0].options.body);
  assert.equal(form.get('grant_type'), 'urn:ietf:params:oauth:grant-type:jwt-bearer');
  const assertion = form.get('assertion');
  assert.ok(assertion);
  const parts = assertion.split('.');
  assert.equal(parts.length, 3);
  const header = JSON.parse(Buffer.from(parts[0], 'base64url').toString('utf8'));
  const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  assert.deepEqual(header, { alg: 'RS256', typ: 'JWT', kid: 'kid_123' });
  assert.equal(claims.iss, 'nova-test@example-project.iam.gserviceaccount.com');
  assert.equal(claims.scope, GOOGLE_PLAY_INTEGRITY_SCOPE);
  assert.equal(claims.aud, 'https://oauth2.googleapis.com/token');
  assert.equal(claims.exp - claims.iat, 3600);
  assert.equal(verifySignature(
    'RSA-SHA256',
    Buffer.from(`${parts[0]}.${parts[1]}`, 'utf8'),
    keys.publicKey,
    Buffer.from(parts[2], 'base64url'),
  ), true);
  assert.doesNotMatch(JSON.stringify(provider.safeSummary()), /access-token-1|PRIVATE KEY|kid_123/);
});

test('Google OAuth provider force refreshes, rejects redirects, and builds from base64 server env', async () => {
  const keys = testKeyPair();
  let calls = 0;
  const provider = googleServiceAccountAccessTokenProviderFromEnv({
    NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL: 'nova-test@example-project.iam.gserviceaccount.com',
    NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64: Buffer.from(keys.privatePem, 'utf8').toString('base64'),
  }, {
    now: () => 1_725_000_000_000,
    fetchImpl: async () => {
      calls += 1;
      return fakeResponse(200, { access_token: `token-${calls}`, expires_in: 3600 });
    },
  });

  assert.equal(await provider.getAccessToken(), 'token-1');
  assert.equal(await provider.getAccessToken({ forceRefresh: true }), 'token-2');
  assert.equal(calls, 2);

  const redirect = new GoogleServiceAccountAccessTokenProvider({
    clientEmail: 'nova-test@example-project.iam.gserviceaccount.com',
    privateKey: keys.privatePem,
    now: () => 1_725_000_000_000,
    fetchImpl: async () => fakeResponse(307, { access_token: 'leak', expires_in: 3600 }),
  });
  await assert.rejects(() => redirect.getAccessToken(), /rejected request/);
});

test('Google Play Integrity decoder sends server token to the exact package endpoint and returns verdict JSON', async () => {
  let seen;
  const decoder = new GooglePlayIntegrityDecoder({
    packageName: 'com.wpuu.novacalculator',
    accessTokenProvider: { getAccessToken: async () => 'oauth-access' },
    fetchImpl: async (url, options) => {
      seen = { url, options };
      return fakeResponse(200, { tokenPayloadExternal: { requestDetails: { requestPackageName: 'com.wpuu.novacalculator' } } });
    },
  });

  const result = await decoder.decodeIntegrityToken('integrity-token-value');
  assert.equal(seen.url, 'https://playintegrity.googleapis.com/v1/com.wpuu.novacalculator:decodeIntegrityToken');
  assert.equal(seen.options.redirect, 'manual');
  assert.equal(seen.options.headers.authorization, 'Bearer oauth-access');
  assert.deepEqual(JSON.parse(seen.options.body), { integrity_token: 'integrity-token-value' });
  assert.equal(result.tokenPayloadExternal.requestDetails.requestPackageName, 'com.wpuu.novacalculator');
  assert.doesNotMatch(JSON.stringify(decoder.safeSummary()), /oauth-access|integrity-token-value/);
});

test('Google Play Integrity decoder refreshes once on 401 and otherwise fails closed', async () => {
  const tokenCalls = [];
  let invalidations = 0;
  let decodeCalls = 0;
  const decoder = new GooglePlayIntegrityDecoder({
    packageName: 'com.wpuu.novacalculator',
    accessTokenProvider: {
      async getAccessToken(options = {}) {
        tokenCalls.push(options);
        return options.forceRefresh ? 'fresh-token' : 'stale-token';
      },
      invalidate() { invalidations += 1; },
    },
    fetchImpl: async () => {
      decodeCalls += 1;
      if (decodeCalls === 1) return fakeResponse(401, { error: 'expired' });
      return fakeResponse(200, { tokenPayloadExternal: { ok: true } });
    },
  });

  const result = await decoder.decodeIntegrityToken('integrity-token-value');
  assert.equal(result.tokenPayloadExternal.ok, true);
  assert.equal(decodeCalls, 2);
  assert.equal(invalidations, 1);
  assert.equal(tokenCalls.length, 2);
  assert.equal(tokenCalls[1].forceRefresh, true);

  const forbidden = new GooglePlayIntegrityDecoder({
    packageName: 'com.wpuu.novacalculator',
    accessTokenProvider: { getAccessToken: async () => 'token' },
    fetchImpl: async () => fakeResponse(403, { error: 'forbidden detail' }),
  });
  await assert.rejects(() => forbidden.decodeIntegrityToken('integrity-token-value'), /rejected request/);
});

test('Play Integrity decoder env factory requires a real Android package and server-only credentials', () => {
  const keys = testKeyPair();
  const env = {
    NOVA_ANDROID_PACKAGE_NAME: 'com.wpuu.novacalculator',
    NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL: 'nova-test@example-project.iam.gserviceaccount.com',
    NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64: Buffer.from(keys.privatePem, 'utf8').toString('base64'),
  };
  const decoder = googlePlayIntegrityDecoderFromEnv(env, { fetchImpl: async () => fakeResponse(500, {}) });
  assert.equal(decoder.safeSummary().packageName, 'com.wpuu.novacalculator');
  assert.throws(() => googlePlayIntegrityDecoderFromEnv({ ...env, NOVA_ANDROID_PACKAGE_NAME: 'bad/package' }), /PACKAGE_NAME/);
});

function fakeResponse(status, payload, contentLength = null) {
  const text = JSON.stringify(payload);
  return {
    status,
    headers: {
      get(name) {
        return String(name).toLowerCase() === 'content-length' && contentLength !== null
          ? String(contentLength) : null;
      },
    },
    async text() { return text; },
  };
}
