import { googleServiceAccountAccessTokenProviderFromEnv } from './google-service-account-token.mjs';

const PLAY_INTEGRITY_BASE_URL = 'https://playintegrity.googleapis.com';
const DEFAULT_TIMEOUT_MS = 8_000;
const DEFAULT_MAX_RESPONSE_BYTES = 256 * 1024;
const MAX_INTEGRITY_TOKEN_CHARS = 12_000;

/**
 * Server-only adapter that asks Google Play to decrypt and verify an integrity token.
 *
 * OAuth credentials remain inside the injected accessTokenProvider. A single 401 triggers one
 * forced token refresh; all other transport/API failures fail closed without exposing Google
 * response bodies to clients or logs.
 */
export class GooglePlayIntegrityDecoder {
  constructor({
    packageName,
    accessTokenProvider,
    fetchImpl = globalThis.fetch,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    baseUrl = PLAY_INTEGRITY_BASE_URL,
    maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES,
  } = {}) {
    this.packageName = androidPackageName(packageName);
    if (!accessTokenProvider || typeof accessTokenProvider.getAccessToken !== 'function') {
      throw new Error('Google Play Integrity decoder requires accessTokenProvider');
    }
    if (typeof fetchImpl !== 'function') throw new Error('Google Play Integrity decoder requires fetch');
    this.accessTokenProvider = accessTokenProvider;
    this.fetchImpl = fetchImpl;
    this.timeoutMs = positiveInteger(timeoutMs, 'timeoutMs');
    this.baseUrl = fixedHttpsOrigin(baseUrl);
    this.maxResponseBytes = positiveInteger(maxResponseBytes, 'maxResponseBytes');
  }

  async decodeIntegrityToken(integrityToken) {
    const token = boundedToken(integrityToken);
    let accessToken = await this.accessTokenProvider.getAccessToken();
    let response = await this.callGoogle(token, accessToken);
    if (response.status === 401) {
      if (typeof this.accessTokenProvider.invalidate === 'function') {
        this.accessTokenProvider.invalidate();
      }
      accessToken = await this.accessTokenProvider.getAccessToken({ forceRefresh: true });
      response = await this.callGoogle(token, accessToken);
    }
    if (response.status < 200 || response.status >= 300) {
      throw new Error('Google Play Integrity decode rejected request');
    }
    return parseJsonResponse(response, this.maxResponseBytes);
  }

  safeSummary() {
    return Object.freeze({
      integrityDecoder: 'google-play-server-decode',
      packageName: this.packageName,
      timeoutMs: this.timeoutMs,
    });
  }

  async callGoogle(integrityToken, accessToken) {
    const bearer = boundedAccessToken(accessToken);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      try {
        return await this.fetchImpl(
          `${this.baseUrl}/v1/${encodeURIComponent(this.packageName)}:decodeIntegrityToken`,
          {
            method: 'POST',
            redirect: 'manual',
            headers: {
              authorization: `Bearer ${bearer}`,
              'content-type': 'application/json',
              accept: 'application/json',
              'accept-encoding': 'identity',
            },
            body: JSON.stringify({ integrity_token: integrityToken }),
            signal: controller.signal,
          },
        );
      } catch {
        throw new Error('Google Play Integrity decode unavailable');
      }
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function googlePlayIntegrityDecoderFromEnv(env = process.env, options = {}) {
  const accessTokenProvider = options.accessTokenProvider
    ?? googleServiceAccountAccessTokenProviderFromEnv(env, {
      fetchImpl: options.fetchImpl,
      now: options.now,
    });
  return new GooglePlayIntegrityDecoder({
    packageName: env.NOVA_ANDROID_PACKAGE_NAME,
    accessTokenProvider,
    fetchImpl: options.fetchImpl,
    timeoutMs: env.NOVA_PLAY_INTEGRITY_DECODE_TIMEOUT_MS ?? options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
  });
}

async function parseJsonResponse(response, maxResponseBytes) {
  if (!response) throw new Error('Google Play Integrity decode returned invalid response');
  const declaredLength = Number(response.headers?.get?.('content-length') ?? -1);
  if (Number.isFinite(declaredLength) && declaredLength > maxResponseBytes) {
    throw new Error('Google Play Integrity decode returned oversized response');
  }
  let text;
  try {
    text = await response.text();
  } catch {
    throw new Error('Google Play Integrity decode returned unreadable response');
  }
  if (new TextEncoder().encode(text).length > maxResponseBytes) {
    throw new Error('Google Play Integrity decode returned oversized response');
  }
  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new Error('Google Play Integrity decode returned invalid JSON');
  }
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('Google Play Integrity decode returned invalid payload');
  }
  return payload;
}

function androidPackageName(value) {
  const text = String(value ?? '').trim();
  if (!/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(text)
      || text.length > 255) {
    throw new Error('NOVA_ANDROID_PACKAGE_NAME is invalid');
  }
  return text;
}

function boundedToken(value) {
  const text = String(value ?? '').trim();
  if (!text || text.length > MAX_INTEGRITY_TOKEN_CHARS || /\s/.test(text)) {
    throw new Error('integrity token is invalid');
  }
  return text;
}

function boundedAccessToken(value) {
  const text = String(value ?? '').trim();
  if (!text || text.length > 8192 || /\s/.test(text)) {
    throw new Error('Google access token is invalid');
  }
  return text;
}

function fixedHttpsOrigin(value) {
  let parsed;
  try {
    parsed = new URL(String(value ?? '').trim());
  } catch {
    throw new Error('Google Play Integrity base URL is invalid');
  }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('Google Play Integrity base URL must be credential-free HTTPS');
  }
  parsed.pathname = parsed.pathname.replace(/\/+$/, '');
  return parsed.toString().replace(/\/$/, '');
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}
