import { createPrivateKey, sign as signBytes } from 'node:crypto';

const GOOGLE_OAUTH_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const PLAY_INTEGRITY_SCOPE = 'https://www.googleapis.com/auth/playintegrity';
const JWT_TTL_SECONDS = 3600;
const CACHE_REFRESH_SKEW_MS = 60_000;
const DEFAULT_TIMEOUT_MS = 5_000;
const DEFAULT_MAX_RESPONSE_BYTES = 64 * 1024;

/**
 * Minimal server-only Google service-account OAuth provider.
 *
 * The implementation follows Google's JWT bearer flow and intentionally exposes only
 * getAccessToken()/invalidate() to the Play Integrity decoder. Private key material never appears
 * in safe summaries, errors or client responses.
 */
export class GoogleServiceAccountAccessTokenProvider {
  constructor({
    clientEmail,
    privateKey,
    privateKeyId = '',
    fetchImpl = globalThis.fetch,
    now = () => Date.now(),
    timeoutMs = DEFAULT_TIMEOUT_MS,
    tokenUrl = GOOGLE_OAUTH_TOKEN_URL,
    scope = PLAY_INTEGRITY_SCOPE,
    maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES,
  } = {}) {
    this.clientEmail = serviceAccountEmail(clientEmail);
    this.privateKeyId = optionalKeyId(privateKeyId);
    this.privateKey = parsePrivateKey(privateKey);
    if (typeof fetchImpl !== 'function') throw new Error('Google OAuth provider requires fetch');
    if (typeof now !== 'function') throw new Error('Google OAuth provider requires a clock');
    this.fetchImpl = fetchImpl;
    this.now = now;
    this.timeoutMs = positiveInteger(timeoutMs, 'timeoutMs');
    this.tokenUrl = fixedHttpsUrl(tokenUrl, 'tokenUrl');
    this.scope = requiredText(scope, 'scope', 500);
    this.maxResponseBytes = positiveInteger(maxResponseBytes, 'maxResponseBytes');
    this.cachedToken = '';
    this.cachedExpiresAtMs = 0;
    this.pending = null;
  }

  async getAccessToken({ forceRefresh = false } = {}) {
    const nowMs = safeNow(this.now());
    if (!forceRefresh && this.cachedToken
        && this.cachedExpiresAtMs - CACHE_REFRESH_SKEW_MS > nowMs) {
      return this.cachedToken;
    }
    if (!forceRefresh && this.pending) return this.pending;

    if (forceRefresh) this.invalidate();
    const operation = this.fetchFreshToken(nowMs);
    if (!forceRefresh) this.pending = operation;
    try {
      return await operation;
    } finally {
      if (this.pending === operation) this.pending = null;
    }
  }

  invalidate() {
    this.cachedToken = '';
    this.cachedExpiresAtMs = 0;
  }

  safeSummary() {
    return Object.freeze({
      auth: 'google-service-account-oauth2',
      scope: this.scope,
      cached: Boolean(this.cachedToken),
    });
  }

  async fetchFreshToken(nowMs) {
    const nowSeconds = Math.floor(nowMs / 1000);
    const header = {
      alg: 'RS256',
      typ: 'JWT',
      ...(this.privateKeyId ? { kid: this.privateKeyId } : {}),
    };
    const claims = {
      iss: this.clientEmail,
      scope: this.scope,
      aud: this.tokenUrl,
      iat: nowSeconds,
      exp: nowSeconds + JWT_TTL_SECONDS,
    };
    const encodedHeader = base64UrlJson(header);
    const encodedClaims = base64UrlJson(claims);
    const signingInput = `${encodedHeader}.${encodedClaims}`;
    const signature = signBytes('RSA-SHA256', Buffer.from(signingInput, 'utf8'), this.privateKey)
      .toString('base64url');
    const assertion = `${signingInput}.${signature}`;
    const body = new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }).toString();

    const payload = await fetchJsonStrict(this.fetchImpl, this.tokenUrl, {
      method: 'POST',
      redirect: 'manual',
      headers: {
        'content-type': 'application/x-www-form-urlencoded',
        accept: 'application/json',
        'accept-encoding': 'identity',
      },
      body,
    }, this.timeoutMs, this.maxResponseBytes, 'Google OAuth');

    const token = requiredText(payload?.access_token, 'Google OAuth access token', 8192);
    if (/\s/.test(token)) throw new Error('Google OAuth returned invalid access token');
    const expiresInSeconds = positiveInteger(payload?.expires_in, 'Google OAuth expires_in');
    this.cachedToken = token;
    this.cachedExpiresAtMs = nowMs + expiresInSeconds * 1000;
    return token;
  }
}

export function googleServiceAccountAccessTokenProviderFromEnv(env = process.env, options = {}) {
  const privateKey = decodePrivateKeyFromEnv(env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64);
  return new GoogleServiceAccountAccessTokenProvider({
    clientEmail: env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL,
    privateKey,
    privateKeyId: env.NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_KEY_ID,
    fetchImpl: options.fetchImpl,
    now: options.now,
    timeoutMs: env.NOVA_GOOGLE_OAUTH_TIMEOUT_MS ?? options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
  });
}

export const GOOGLE_PLAY_INTEGRITY_SCOPE = PLAY_INTEGRITY_SCOPE;

async function fetchJsonStrict(fetchImpl, url, options, timeoutMs, maxResponseBytes, label) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  let response;
  try {
    try {
      response = await fetchImpl(url, { ...options, signal: controller.signal });
    } catch {
      throw new Error(`${label} unavailable`);
    }
  } finally {
    clearTimeout(timeout);
  }
  if (!response || response.status < 200 || response.status >= 300) {
    throw new Error(`${label} rejected request`);
  }
  const declaredLength = Number(response.headers?.get?.('content-length') ?? -1);
  if (Number.isFinite(declaredLength) && declaredLength > maxResponseBytes) {
    throw new Error(`${label} returned oversized response`);
  }
  let text;
  try {
    text = await response.text();
  } catch {
    throw new Error(`${label} returned unreadable response`);
  }
  if (new TextEncoder().encode(text).length > maxResponseBytes) {
    throw new Error(`${label} returned oversized response`);
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`${label} returned invalid JSON`);
  }
}

function decodePrivateKeyFromEnv(value) {
  const encoded = String(value ?? '').trim();
  if (!encoded || encoded.length > 64 * 1024 || !/^[A-Za-z0-9+/=]+$/.test(encoded)) {
    throw new Error('NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64 is invalid');
  }
  let decoded;
  try {
    decoded = Buffer.from(encoded, 'base64').toString('utf8').trim();
  } catch {
    throw new Error('NOVA_PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY_B64 is invalid');
  }
  return decoded;
}

function parsePrivateKey(value) {
  const pem = requiredText(value, 'service account private key', 32 * 1024);
  if (!/^-----BEGIN (?:RSA )?PRIVATE KEY-----/.test(pem)) {
    throw new Error('service account private key must be PEM');
  }
  try {
    const key = createPrivateKey(pem);
    if (key.asymmetricKeyType !== 'rsa' && key.asymmetricKeyType !== 'rsa-pss') {
      throw new Error('unsupported key type');
    }
    return key;
  } catch {
    throw new Error('service account private key is invalid');
  }
}

function serviceAccountEmail(value) {
  const email = requiredText(value, 'service account email', 320);
  if (!/^[^\s@]+@[^\s@]+\.iam\.gserviceaccount\.com$/.test(email)) {
    throw new Error('service account email is invalid');
  }
  return email;
}

function optionalKeyId(value) {
  const text = String(value ?? '').trim();
  if (!text) return '';
  if (!/^[A-Za-z0-9_-]{1,200}$/.test(text)) throw new Error('service account key id is invalid');
  return text;
}

function requiredText(value, name, maxLength) {
  const text = String(value ?? '').trim();
  if (!text || text.length > maxLength || /[\r\n]/.test(name === 'service account private key' ? '' : text)) {
    throw new Error(`${name} is invalid`);
  }
  return text;
}

function fixedHttpsUrl(value, name) {
  let parsed;
  try {
    parsed = new URL(String(value ?? '').trim());
  } catch {
    throw new Error(`${name} is invalid`);
  }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.hash) {
    throw new Error(`${name} must be HTTPS without credentials or fragment`);
  }
  return parsed.toString();
}

function base64UrlJson(value) {
  return Buffer.from(JSON.stringify(value), 'utf8').toString('base64url');
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}

function safeNow(value) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < 0) throw new Error('clock returned invalid time');
  return number;
}
