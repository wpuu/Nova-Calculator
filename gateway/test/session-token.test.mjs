import test from 'node:test';
import assert from 'node:assert/strict';

import {
  NOVA_SESSION_KIND,
  NovaSessionTokenService,
  sessionTokenServiceFromEnv,
} from '../src/session-token.mjs';

const SIGNING_A = 'signing-secret-a-0123456789-0123456789';
const SIGNING_B = 'signing-secret-b-0123456789-0123456789';
const SUBJECT_SECRET = 'subject-secret-stable-0123456789-012345';

function service(options = {}) {
  return new NovaSessionTokenService({
    secrets: options.secrets ?? [SIGNING_A],
    subjectSecret: options.subjectSecret ?? SUBJECT_SECRET,
    now: options.now ?? (() => 1_800_000_000_000),
    anonymousTtlMs: options.anonymousTtlMs ?? 60_000,
    accountTtlMs: options.accountTtlMs ?? 120_000,
    clockSkewMs: options.clockSkewMs ?? 0,
  });
}

test('anonymous session is signed, pseudonymous and carries no paid entitlement', () => {
  const tokens = service();
  const issued = tokens.issueAnonymous({ installationId: 'install-1234567890' });
  const principal = tokens.verify(`Bearer ${issued.token}`);

  assert.equal(principal.sessionKind, NOVA_SESSION_KIND.ANONYMOUS);
  assert.match(principal.subjectId, /^anon_[A-Za-z0-9_-]{32}$/);
  assert.deepEqual(principal.entitlements, []);
  assert.equal(issued.token.includes('install-1234567890'), false);
  assert.equal(JSON.stringify(issued).includes(SIGNING_A), false);
});

test('account session carries only server-issued known entitlements', () => {
  const tokens = service();
  const issued = tokens.issueAccount({
    accountId: 'internal-account-42',
    entitlements: ['AI_PLUS', 'PRO_LIFETIME', 'AI_PLUS'],
  });
  const principal = tokens.verify(`bearer ${issued.token}`);

  assert.equal(principal.sessionKind, NOVA_SESSION_KIND.ACCOUNT);
  assert.match(principal.subjectId, /^acct_[A-Za-z0-9_-]{32}$/);
  assert.deepEqual(principal.entitlements, ['AI_PLUS', 'PRO_LIFETIME']);
  assert.equal(issued.token.includes('internal-account-42'), false);
  assert.throws(
    () => tokens.issueAccount({ accountId: 'a', entitlements: ['CLIENT_CLAIMED_PREMIUM'] }),
    /unknown Nova entitlement/,
  );
});

test('signature tampering and malformed authorization fail closed', () => {
  const tokens = service();
  const issued = tokens.issueAnonymous({ installationId: 'install-abc' }).token;
  const last = issued.endsWith('A') ? 'B' : 'A';
  const tampered = `${issued.slice(0, -1)}${last}`;

  assert.equal(tokens.verify(`Bearer ${tampered}`), null);
  assert.equal(tokens.verify(issued), null);
  assert.equal(tokens.verify('Basic anything'), null);
  assert.equal(tokens.verify('Bearer one two'), null);
});

test('expired or not-yet-valid sessions fail closed', () => {
  let now = 2_000_000_000_000;
  const issuer = service({ now: () => now, anonymousTtlMs: 10_000, clockSkewMs: 0 });
  const token = issuer.issueAnonymous({ installationId: 'install-time' }).token;
  assert.notEqual(issuer.verify(`Bearer ${token}`), null);

  now += 11_000;
  assert.equal(issuer.verify(`Bearer ${token}`), null);

  const futureIssuer = service({ now: () => now + 60_000, anonymousTtlMs: 10_000, clockSkewMs: 0 });
  const futureToken = futureIssuer.issueAnonymous({ installationId: 'install-future' }).token;
  assert.equal(issuer.verify(`Bearer ${futureToken}`), null);
});

test('signing-key rotation preserves old sessions and stable quota subject ids', () => {
  const oldService = service({ secrets: [SIGNING_A] });
  const oldIssued = oldService.issueAnonymous({ installationId: 'stable-install' });

  const rotated = service({ secrets: [SIGNING_B, SIGNING_A] });
  const oldPrincipal = rotated.verify(`Bearer ${oldIssued.token}`);
  const newIssued = rotated.issueAnonymous({ installationId: 'stable-install' });
  const newPrincipal = rotated.verify(`Bearer ${newIssued.token}`);

  assert.equal(oldPrincipal.subjectId, newPrincipal.subjectId);
  assert.notEqual(oldIssued.token, newIssued.token);
  assert.equal(oldService.verify(`Bearer ${newIssued.token}`), null);
});

test('changing the stable subject secret changes quota identity independently of signing rotation', () => {
  const first = service().issueAnonymous({ installationId: 'same-install' }).principal.subjectId;
  const second = service({
    subjectSecret: 'different-subject-secret-0123456789-012345',
  }).issueAnonymous({ installationId: 'same-install' }).principal.subjectId;
  assert.notEqual(first, second);
});

test('environment configuration requires separate signing and subject secrets', () => {
  assert.throws(() => sessionTokenServiceFromEnv({}), /NOVA_SESSION_SIGNING_SECRETS is required/);
  assert.throws(
    () => sessionTokenServiceFromEnv({ NOVA_SESSION_SIGNING_SECRETS: SIGNING_A }),
    /NOVA_SESSION_SUBJECT_SECRET is required/,
  );

  const configured = sessionTokenServiceFromEnv({
    NOVA_SESSION_SIGNING_SECRETS: `${SIGNING_B},${SIGNING_A}`,
    NOVA_SESSION_SUBJECT_SECRET: SUBJECT_SECRET,
    NOVA_ANONYMOUS_SESSION_TTL_MS: '60000',
    NOVA_ACCOUNT_SESSION_TTL_MS: '120000',
  }, { now: () => 1_800_000_000_000, clockSkewMs: 0 });

  const issued = configured.issueAnonymous({ installationId: 'env-install' });
  assert.notEqual(configured.verify(`Bearer ${issued.token}`), null);
});
