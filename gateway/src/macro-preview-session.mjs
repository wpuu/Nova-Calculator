import { timingSafeEqual } from 'node:crypto';

const DEFAULT_TTL_MS = 10 * 60 * 1000;
const MAX_BODY_BYTES = 4096;

export class MacroPreviewSessionService {
  constructor({ tokenService, bootstrapSecret, environment, ttlMs = DEFAULT_TTL_MS }) {
    if (!tokenService || typeof tokenService.issueAccount !== 'function') {
      throw new Error('MacroPreviewSessionService requires tokenService.issueAccount');
    }
    this.enabled = String(environment ?? '').trim().toLowerCase() === 'preview';
    this.bootstrapSecret = normalizeSecret(bootstrapSecret, this.enabled);
    this.tokenService = tokenService;
    this.ttlMs = positiveInt(ttlMs, 'ttlMs');
  }

  issue({ bootstrapSecret }) {
    if (!this.enabled || !this.bootstrapSecret) return response('UNAVAILABLE');
    const supplied = safeSecret(bootstrapSecret);
    if (!supplied || !constantTimeEqual(this.bootstrapSecret, supplied)) return response('AUTH_REQUIRED');
    try {
      const issued = this.tokenService.issueAccount({
        accountId: 'macro-preview-smoke',
        entitlements: [],
        ttlMs: this.ttlMs,
      });
      return response('SUCCESS', {
        sessionToken: issued?.token,
        expiresAtEpochMs: issued?.expiresAtEpochMs,
      });
    } catch {
      return response('UNAVAILABLE');
    }
  }
}

export function createMacroPreviewSessionFetchHandler({ service }) {
  if (!service || typeof service.issue !== 'function') {
    throw new Error('createMacroPreviewSessionFetchHandler requires service.issue');
  }
  return async function handle(request) {
    if (!request || request.method?.toUpperCase() !== 'POST') {
      return json(405, response('INVALID_REQUEST'), { allow: 'POST' });
    }
    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) return json(415, response('INVALID_REQUEST'));
    const declaredLength = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) return json(413, response('INVALID_REQUEST'));

    let text;
    try { text = await request.text(); } catch { return json(400, response('INVALID_REQUEST')); }
    if (new TextEncoder().encode(text).byteLength > MAX_BODY_BYTES) return json(413, response('INVALID_REQUEST'));

    let body;
    try { body = JSON.parse(text); } catch { return json(400, response('INVALID_REQUEST')); }
    if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).length !== 1 || !safeSecret(body.bootstrapSecret)) {
      return json(400, response('INVALID_REQUEST'));
    }

    let result;
    try { result = service.issue({ bootstrapSecret: body.bootstrapSecret }); } catch { result = response('UNAVAILABLE'); }
    const status = result.status === 'SUCCESS' ? 200
      : result.status === 'AUTH_REQUIRED' ? 403
        : result.status === 'INVALID_REQUEST' ? 400 : 404;
    return json(status, result);
  };
}

function normalizeSecret(value, required) {
  const secret = safeSecret(value);
  if (!secret && required) throw new Error('NOVA_MACRO_PREVIEW_BOOTSTRAP_SECRET is required for Preview Macro session');
  return secret;
}

function safeSecret(value) {
  if (typeof value !== 'string') return '';
  const text = value.trim();
  return text.length >= 43 && text.length <= 256 ? text : '';
}

function constantTimeEqual(expected, supplied) {
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(supplied, 'utf8');
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

function response(status, options = {}) {
  const success = status === 'SUCCESS';
  return Object.freeze({
    status,
    sessionToken: success && typeof options.sessionToken === 'string' ? options.sessionToken : '',
    expiresAtEpochMs: success && Number.isFinite(Number(options.expiresAtEpochMs)) ? Number(options.expiresAtEpochMs) : 0,
  });
}

function json(status, payload, extraHeaders = {}) {
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

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0 || number > 60 * 60 * 1000) {
    throw new Error(`${name} must be a positive integer no greater than one hour`);
  }
  return number;
}
