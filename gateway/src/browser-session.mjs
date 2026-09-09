const DEFAULT_MAX_BODY_BYTES = 12 * 1024;
const DEFAULT_SESSION_TTL_MS = 60 * 60 * 1000;
const MIN_REMAINING_IDENTITY_MS = 60 * 1000;
const EXPIRY_GUARD_MS = 30 * 1000;
const MAX_ACCESS_TOKEN_CHARS = 8_192;

export const BROWSER_SESSION_STATUS = Object.freeze({
  SUCCESS: 'SUCCESS',
  INVALID_REQUEST: 'INVALID_REQUEST',
  IDENTITY_REJECTED: 'IDENTITY_REJECTED',
  TEMPORARILY_UNAVAILABLE: 'TEMPORARILY_UNAVAILABLE',
});

/**
 * Exchanges a verified Google browser identity for a short-lived Nova session.
 * Google access tokens never become Nova session claims and are not returned.
 */
export class BrowserSessionService {
  constructor({ tokenService, identityVerifier, now, sessionTtlMs = DEFAULT_SESSION_TTL_MS }) {
    if (!tokenService || typeof tokenService.issueAccount !== 'function') {
      throw new Error('BrowserSessionService requires tokenService.issueAccount');
    }
    if (!identityVerifier || typeof identityVerifier.verify !== 'function') {
      throw new Error('BrowserSessionService requires identityVerifier.verify');
    }
    this.tokenService = tokenService;
    this.identityVerifier = identityVerifier;
    this.now = typeof now === 'function' ? now : () => Date.now();
    this.sessionTtlMs = positiveInt(sessionTtlMs, 'sessionTtlMs');
  }

  async issue({ accessToken }) {
    const token = safeText(accessToken, MAX_ACCESS_TOKEN_CHARS);
    if (!token) return result(BROWSER_SESSION_STATUS.INVALID_REQUEST);

    let verification;
    try {
      verification = await this.identityVerifier.verify({ accessToken: token });
    } catch {
      return result(BROWSER_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
    }
    if (!verification?.accepted) return result(BROWSER_SESSION_STATUS.IDENTITY_REJECTED);

    const subjectId = safeText(verification.subjectId, 255);
    const identityExpiresAt = nonNegative(verification.expiresAtEpochMs);
    const nowMs = finiteNow(this.now());
    const remaining = identityExpiresAt - nowMs;
    if (!subjectId || remaining < MIN_REMAINING_IDENTITY_MS) {
      return result(BROWSER_SESSION_STATUS.IDENTITY_REJECTED);
    }

    const ttlMs = Math.min(this.sessionTtlMs, remaining - EXPIRY_GUARD_MS);
    if (ttlMs <= 0) return result(BROWSER_SESSION_STATUS.IDENTITY_REJECTED);

    try {
      const issued = this.tokenService.issueAccount({
        accountId: `google:${subjectId}`,
        entitlements: [],
        ttlMs,
      });
      return result(BROWSER_SESSION_STATUS.SUCCESS, {
        sessionToken: issued?.token,
        expiresAtEpochMs: issued?.expiresAtEpochMs,
      });
    } catch {
      return result(BROWSER_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
    }
  }
}

export function createBrowserSessionFetchHandler({ service, maxBodyBytes = DEFAULT_MAX_BODY_BYTES }) {
  if (!service || typeof service.issue !== 'function') {
    throw new Error('createBrowserSessionFetchHandler requires service.issue');
  }
  const bodyLimit = positiveInt(maxBodyBytes, 'maxBodyBytes');

  return async function handle(request) {
    if (!request || typeof request.method !== 'string') {
      return jsonResponse(400, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }
    if (request.method.toUpperCase() !== 'POST') {
      return jsonResponse(405, result(BROWSER_SESSION_STATUS.INVALID_REQUEST), { allow: 'POST' });
    }
    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) {
      return jsonResponse(415, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }

    const declaredLength = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declaredLength) && declaredLength > bodyLimit) {
      return jsonResponse(413, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }

    let text;
    try { text = await request.text(); } catch {
      return jsonResponse(400, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }
    if (new TextEncoder().encode(text).byteLength > bodyLimit) {
      return jsonResponse(413, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }

    let body;
    try { body = JSON.parse(text); } catch {
      return jsonResponse(400, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }
    if (!isStrictBody(body)) {
      return jsonResponse(400, result(BROWSER_SESSION_STATUS.INVALID_REQUEST));
    }

    let issued;
    try { issued = await service.issue({ accessToken: body.accessToken }); } catch {
      issued = result(BROWSER_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
    }
    const sanitized = sanitize(issued);
    return jsonResponse(httpStatusFor(sanitized.status), sanitized);
  };
}

function isStrictBody(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return false;
  const keys = Object.keys(body);
  return keys.length === 1 && keys[0] === 'accessToken'
    && Boolean(safeText(body.accessToken, MAX_ACCESS_TOKEN_CHARS));
}

function result(status, options = {}) {
  const success = status === BROWSER_SESSION_STATUS.SUCCESS;
  return Object.freeze({
    status,
    sessionToken: success && typeof options.sessionToken === 'string' ? options.sessionToken : '',
    expiresAtEpochMs: success ? nonNegative(options.expiresAtEpochMs) : 0,
  });
}

function sanitize(value) {
  const status = Object.values(BROWSER_SESSION_STATUS).includes(value?.status)
    ? value.status : BROWSER_SESSION_STATUS.TEMPORARILY_UNAVAILABLE;
  if (status !== BROWSER_SESSION_STATUS.SUCCESS) return result(status);
  const token = safeText(value?.sessionToken, 8192);
  const expiresAtEpochMs = nonNegative(value?.expiresAtEpochMs);
  if (!token || expiresAtEpochMs <= 0) return result(BROWSER_SESSION_STATUS.TEMPORARILY_UNAVAILABLE);
  return result(status, { sessionToken: token, expiresAtEpochMs });
}

function httpStatusFor(status) {
  switch (status) {
    case BROWSER_SESSION_STATUS.SUCCESS: return 200;
    case BROWSER_SESSION_STATUS.INVALID_REQUEST: return 400;
    case BROWSER_SESSION_STATUS.IDENTITY_REJECTED: return 403;
    default: return 503;
  }
}

function jsonResponse(status, payload, extraHeaders = {}) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      ...extraHeaders,
    },
  });
}

function safeText(value, maxLength) {
  if (typeof value !== 'string') return '';
  const text = value.trim();
  return text && text.length <= maxLength ? text : '';
}

function nonNegative(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : 0;
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw new Error(`${name} must be a positive integer`);
  return number;
}

function finiteNow(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) throw new Error('clock returned invalid time');
  return number;
}
