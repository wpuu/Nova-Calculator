import { createHmac, timingSafeEqual } from 'node:crypto';

const TOKEN_PREFIX = 'nova1';
const DEFAULT_ANONYMOUS_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const DEFAULT_ACCOUNT_TTL_MS = 24 * 60 * 60 * 1000;
const DEFAULT_CLOCK_SKEW_MS = 60 * 1000;
const MAX_TOKEN_CHARS = 8192;
const MAX_PAYLOAD_BYTES = 4096;
const MIN_SECRET_CHARS = 32;
const KNOWN_ENTITLEMENTS = new Set(['PRO_LIFETIME', 'AI_PLUS']);

export const NOVA_SESSION_KIND = Object.freeze({
  ANONYMOUS: 'anonymous',
  ACCOUNT: 'account',
});

/**
 * Server-only Nova session signer/verifier.
 *
 * The first signing secret issues new tokens; every configured signing secret can verify tokens
 * so deployments can rotate signing material without instantly invalidating sessions. A separate
 * stable subject-derivation secret keeps quota subject ids unchanged during signing-key rotation.
 */
export class NovaSessionTokenService {
  constructor(options = {}) {
    this.secrets = normalizeSecrets(options.secrets ?? options.secret, 'session signing');
    this.subjectSecret = normalizeSecrets(
      options.subjectSecret ?? this.secrets[0],
      'subject derivation',
    )[0];
    this.now = typeof options.now === 'function' ? options.now : () => Date.now();
    this.issuer = boundedText(options.issuer ?? 'nova-calculator', 'issuer', 100);
    this.anonymousTtlMs = positiveInt(
      options.anonymousTtlMs ?? DEFAULT_ANONYMOUS_TTL_MS,
      'anonymousTtlMs',
    );
    this.accountTtlMs = positiveInt(options.accountTtlMs ?? DEFAULT_ACCOUNT_TTL_MS, 'accountTtlMs');
    this.clockSkewMs = nonNegativeInt(options.clockSkewMs ?? DEFAULT_CLOCK_SKEW_MS, 'clockSkewMs');
  }

  issueAnonymous({ installationId }) {
    const install = boundedText(installationId, 'installationId', 200);
    return this.issue({
      kind: NOVA_SESSION_KIND.ANONYMOUS,
      subjectId: pseudonymousSubject(this.subjectSecret, 'install', install),
      entitlements: [],
      ttlMs: this.anonymousTtlMs,
    });
  }

  issueAccount({ accountId, entitlements = [] }) {
    const account = boundedText(accountId, 'accountId', 200);
    return this.issue({
      kind: NOVA_SESSION_KIND.ACCOUNT,
      subjectId: pseudonymousSubject(this.subjectSecret, 'account', account),
      entitlements: normalizeEntitlements(entitlements, true),
      ttlMs: this.accountTtlMs,
    });
  }

  verify(authorization) {
    const token = bearerToken(authorization);
    if (!token) return null;
    return this.verifyToken(token);
  }

  verifyToken(token) {
    const text = typeof token === 'string' ? token.trim() : '';
    if (!text || text.length > MAX_TOKEN_CHARS) return null;
    const parts = text.split('.');
    if (parts.length !== 3 || parts[0] !== TOKEN_PREFIX) return null;

    const signingInput = `${parts[0]}.${parts[1]}`;
    let suppliedSignature;
    try {
      suppliedSignature = Buffer.from(parts[2], 'base64url');
    } catch {
      return null;
    }
    if (suppliedSignature.length !== 32 || !this.matchesAnySecret(signingInput, suppliedSignature)) {
      return null;
    }

    let payload;
    try {
      const payloadBytes = Buffer.from(parts[1], 'base64url');
      if (payloadBytes.length === 0 || payloadBytes.length > MAX_PAYLOAD_BYTES) return null;
      payload = JSON.parse(payloadBytes.toString('utf8'));
    } catch {
      return null;
    }

    return this.principalFromPayload(payload);
  }

  issue({ kind, subjectId, entitlements, ttlMs }) {
    const nowMs = finiteNow(this.now());
    const issuedAt = Math.floor(nowMs / 1000);
    const expiresAt = Math.floor((nowMs + ttlMs) / 1000);
    const payload = Object.freeze({
      v: 1,
      iss: this.issuer,
      kind,
      sub: subjectId,
      ent: entitlements,
      iat: issuedAt,
      exp: expiresAt,
    });
    const encodedPayload = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
    const signingInput = `${TOKEN_PREFIX}.${encodedPayload}`;
    const signature = sign(this.secrets[0], signingInput).toString('base64url');
    return Object.freeze({
      token: `${signingInput}.${signature}`,
      expiresAtEpochMs: expiresAt * 1000,
      principal: Object.freeze({ subjectId, entitlements: Object.freeze([...entitlements]), kind }),
    });
  }

  matchesAnySecret(signingInput, suppliedSignature) {
    let matched = false;
    for (const secret of this.secrets) {
      const expected = sign(secret, signingInput);
      // Check every configured secret so verification does not reveal which rotation key matched.
      if (timingSafeEqual(expected, suppliedSignature)) matched = true;
    }
    return matched;
  }

  principalFromPayload(payload) {
    if (!payload || payload.v !== 1 || payload.iss !== this.issuer) return null;
    if (!Object.values(NOVA_SESSION_KIND).includes(payload.kind)) return null;
    const subjectId = safeBoundedText(payload.sub, 100);
    if (!subjectId) return null;
    const entitlements = normalizeEntitlements(payload.ent, false);
    if (entitlements == null) return null;

    const issuedAt = Number(payload.iat);
    const expiresAt = Number(payload.exp);
    if (!Number.isInteger(issuedAt) || !Number.isInteger(expiresAt) || expiresAt <= issuedAt) return null;
    const nowSeconds = finiteNow(this.now()) / 1000;
    const skewSeconds = this.clockSkewMs / 1000;
    if (issuedAt > nowSeconds + skewSeconds || expiresAt <= nowSeconds - skewSeconds) return null;

    if (payload.kind === NOVA_SESSION_KIND.ANONYMOUS && entitlements.length !== 0) return null;
    return Object.freeze({
      subjectId,
      entitlements: Object.freeze(entitlements),
      sessionKind: payload.kind,
      expiresAtEpochMs: expiresAt * 1000,
    });
  }
}

export function sessionTokenServiceFromEnv(env = process.env, options = {}) {
  const rawSigning = String(env.NOVA_SESSION_SIGNING_SECRETS ?? '').trim();
  if (!rawSigning) throw new Error('NOVA_SESSION_SIGNING_SECRETS is required');
  const subjectSecret = String(env.NOVA_SESSION_SUBJECT_SECRET ?? '').trim();
  if (!subjectSecret) throw new Error('NOVA_SESSION_SUBJECT_SECRET is required');
  return new NovaSessionTokenService({
    ...options,
    secrets: rawSigning.split(',').map((value) => value.trim()).filter(Boolean),
    subjectSecret,
    anonymousTtlMs: env.NOVA_ANONYMOUS_SESSION_TTL_MS ?? options.anonymousTtlMs,
    accountTtlMs: env.NOVA_ACCOUNT_SESSION_TTL_MS ?? options.accountTtlMs,
  });
}

function bearerToken(authorization) {
  if (typeof authorization !== 'string' || authorization.length > MAX_TOKEN_CHARS + 20) return null;
  const match = /^Bearer\s+([^\s]+)$/i.exec(authorization.trim());
  return match ? match[1] : null;
}

function pseudonymousSubject(secret, namespace, sourceId) {
  const digest = createHmac('sha256', secret)
    .update(`nova-subject:${namespace}:`)
    .update(sourceId)
    .digest('base64url')
    .slice(0, 32);
  return `${namespace === 'install' ? 'anon' : 'acct'}_${digest}`;
}

function sign(secret, input) {
  return createHmac('sha256', secret).update(input).digest();
}

function normalizeSecrets(value, label) {
  const list = Array.isArray(value) ? value : [value];
  const normalized = list.map((item) => String(item ?? '').trim()).filter(Boolean);
  if (normalized.length === 0) throw new Error(`at least one Nova ${label} secret is required`);
  for (const secret of normalized) {
    if (secret.length < MIN_SECRET_CHARS) {
      throw new Error(`Nova ${label} secrets must be at least ${MIN_SECRET_CHARS} characters`);
    }
  }
  return Object.freeze(normalized);
}

function normalizeEntitlements(value, throwOnUnknown) {
  if (!Array.isArray(value)) {
    if (throwOnUnknown) throw new Error('entitlements must be an array');
    return null;
  }
  const result = [];
  for (const raw of value) {
    const entitlement = String(raw ?? '').trim();
    if (!KNOWN_ENTITLEMENTS.has(entitlement)) {
      if (throwOnUnknown) throw new Error(`unknown Nova entitlement: ${entitlement}`);
      return null;
    }
    if (!result.includes(entitlement)) result.push(entitlement);
  }
  return result.sort();
}

function boundedText(value, name, maxLength) {
  const text = safeBoundedText(value, maxLength);
  if (!text) throw new Error(`${name} must be a non-blank string no longer than ${maxLength} characters`);
  return text;
}

function safeBoundedText(value, maxLength) {
  if (typeof value !== 'string') return '';
  const text = value.trim();
  return text && text.length <= maxLength ? text : '';
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw new Error(`${name} must be a positive integer`);
  return number;
}

function nonNegativeInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 0) throw new Error(`${name} must be a non-negative integer`);
  return number;
}

function finiteNow(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) throw new Error('clock returned an invalid time');
  return number;
}
