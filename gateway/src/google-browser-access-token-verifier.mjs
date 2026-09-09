const DEFAULT_TIMEOUT_MS = 5_000;
const MAX_ACCESS_TOKEN_CHARS = 8_192;
const MAX_SUBJECT_CHARS = 255;
const CLOCK_SKEW_MS = 30_000;
const TOKENINFO_ENDPOINT = 'https://oauth2.googleapis.com/tokeninfo';

/**
 * Verifies a Chrome-obtained Google OAuth access token against Google's fixed
 * tokeninfo endpoint and returns only the minimum stable identity needed by Nova.
 * Email/profile data is intentionally ignored even if Google returns it.
 */
export class GoogleBrowserAccessTokenVerifier {
  constructor(options = {}) {
    this.clientIds = normalizeClientIds(options.clientIds);
    this.fetchImpl = options.fetchImpl ?? globalThis.fetch;
    if (typeof this.fetchImpl !== 'function') throw new Error('Google browser verifier requires fetch');
    this.timeoutMs = positiveInt(options.timeoutMs ?? DEFAULT_TIMEOUT_MS, 'timeoutMs');
    this.now = typeof options.now === 'function' ? options.now : () => Date.now();
  }

  async verify({ accessToken }) {
    const token = safeText(accessToken, MAX_ACCESS_TOKEN_CHARS);
    if (!token) return rejected();

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    let response;
    try {
      const url = new URL(TOKENINFO_ENDPOINT);
      url.searchParams.set('access_token', token);
      try {
        response = await this.fetchImpl(url, {
          method: 'GET',
          redirect: 'error',
          headers: { accept: 'application/json' },
          signal: controller.signal,
        });
      } catch (error) {
        throw new Error(error?.name === 'AbortError'
          ? 'Google token verification timed out'
          : 'Google token verification transport failed');
      }

      if (!response || typeof response.ok !== 'boolean') {
        throw new Error('Google token verification returned an invalid response');
      }
      if (!response.ok) {
        if ([400, 401, 403].includes(Number(response.status))) return rejected();
        throw new Error('Google token verification is temporarily unavailable');
      }

      let payload;
      try {
        payload = await response.json();
      } catch {
        throw new Error('Google token verification returned invalid JSON');
      }
      return normalizeVerification(payload, this.clientIds, this.now());
    } finally {
      clearTimeout(timer);
    }
  }
}

export function googleBrowserAccessTokenVerifierFromEnv(env = process.env, options = {}) {
  const raw = String(env.NOVA_CHROME_OAUTH_CLIENT_IDS ?? '').trim();
  if (!raw) throw new Error('NOVA_CHROME_OAUTH_CLIENT_IDS is required');
  return new GoogleBrowserAccessTokenVerifier({
    ...options,
    clientIds: raw.split(/[\n,;]+/).map((value) => value.trim()).filter(Boolean),
    timeoutMs: env.NOVA_CHROME_OAUTH_VERIFY_TIMEOUT_MS ?? options.timeoutMs,
  });
}

function normalizeVerification(payload, clientIds, nowMs) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return rejected();
  const audience = safeText(payload.aud, 500);
  const authorizedParty = safeText(payload.azp, 500);
  if (!audience || !clientIds.has(audience)) return rejected();
  if (authorizedParty && !clientIds.has(authorizedParty)) return rejected();

  const subjectId = safeText(payload.sub, MAX_SUBJECT_CHARS);
  if (!subjectId) return rejected();

  const scopes = new Set(String(payload.scope ?? '').split(/\s+/).map((value) => value.trim()).filter(Boolean));
  if (!scopes.has('openid')) return rejected();

  const expiresAtEpochMs = expiryEpochMs(payload, nowMs);
  if (!expiresAtEpochMs || expiresAtEpochMs <= nowMs + CLOCK_SKEW_MS) return rejected();

  return Object.freeze({
    accepted: true,
    subjectId,
    expiresAtEpochMs,
  });
}

function expiryEpochMs(payload, nowMs) {
  const expSeconds = Number(payload?.exp);
  if (Number.isFinite(expSeconds) && expSeconds > 0) return Math.floor(expSeconds * 1000);
  const expiresInSeconds = Number(payload?.expires_in);
  if (Number.isFinite(expiresInSeconds) && expiresInSeconds > 0) {
    return Math.floor(nowMs + expiresInSeconds * 1000);
  }
  return 0;
}

function normalizeClientIds(value) {
  const list = Array.isArray(value) ? value : [value];
  const normalized = list.map((item) => safeText(item, 500)).filter(Boolean);
  if (normalized.length === 0) throw new Error('Google browser verifier requires at least one OAuth client id');
  return new Set(normalized);
}

function rejected() {
  return Object.freeze({ accepted: false });
}

function safeText(value, maxLength) {
  if (typeof value !== 'string') return '';
  const text = value.trim();
  return text && text.length <= maxLength ? text : '';
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw new Error(`${name} must be a positive integer`);
  return number;
}
