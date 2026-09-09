import { NOVA_GATEWAY_STATUS } from './nova-ai-service.mjs';

const DEFAULT_MAX_BODY_BYTES = 24 * 1024;

export function createMacroCandidateReviewFetchHandler({ service, maxBodyBytes = DEFAULT_MAX_BODY_BYTES }) {
  if (!service || typeof service.execute !== 'function') throw new Error('candidate review handler requires service.execute');
  const bodyLimit = positiveInt(maxBodyBytes, 'maxBodyBytes');

  return async function handle(request) {
    if (!request || typeof request.method !== 'string') return jsonResponse(400, invalidResponse('invalid'));
    if (request.method.toUpperCase() !== 'POST') return jsonResponse(405, invalidResponse('invalid'), { allow: 'POST' });

    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) return jsonResponse(415, invalidResponse('invalid'));

    const declaredLength = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declaredLength) && declaredLength > bodyLimit) return jsonResponse(413, invalidResponse('invalid'));

    let text;
    try { text = await request.text(); } catch { return jsonResponse(400, invalidResponse('invalid')); }
    if (new TextEncoder().encode(text).byteLength > bodyLimit) return jsonResponse(413, invalidResponse('invalid'));

    let body;
    try { body = JSON.parse(text); } catch { return jsonResponse(400, invalidResponse('invalid')); }
    const requestId = safeRequestId(body?.requestId);

    try {
      const result = await service.execute({
        authorization: request.headers?.get?.('authorization') ?? '',
        request: body,
      });
      return jsonResponse(httpStatusFor(result?.status), sanitize(result, requestId));
    } catch {
      return jsonResponse(503, unavailableResponse(requestId));
    }
  };
}

function sanitize(result, fallbackRequestId) {
  const status = Object.values(NOVA_GATEWAY_STATUS).includes(result?.status)
    ? result.status
    : NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE;
  const success = status === NOVA_GATEWAY_STATUS.SUCCESS;
  const decision = success && ['SELECT', 'ABSTAIN'].includes(result?.decision) ? result.decision : success ? 'ABSTAIN' : null;
  const candidateId = decision === 'SELECT' && /^candidate_\d+$/.test(String(result?.candidateId ?? ''))
    ? String(result.candidateId)
    : null;
  return {
    requestId: safeRequestId(result?.requestId) || fallbackRequestId,
    status,
    decision: success && decision === 'SELECT' && !candidateId ? 'ABSTAIN' : decision,
    candidateId,
    confidence: success ? boundedConfidence(result?.confidence) : 0,
    reason: success && typeof result?.reason === 'string' ? result.reason.slice(0, 600) : '',
    retryAfterSeconds: nonNegative(result?.retryAfterSeconds),
    remainingRequestHint: integerHint(result?.remainingRequestHint),
    quotaResetAtEpochMs: nonNegative(result?.quotaResetAtEpochMs),
  };
}

function invalidResponse(requestId) {
  return {
    requestId,
    status: NOVA_GATEWAY_STATUS.INVALID_REQUEST,
    decision: null,
    candidateId: null,
    confidence: 0,
    reason: '',
    retryAfterSeconds: 0,
    remainingRequestHint: -1,
    quotaResetAtEpochMs: 0,
  };
}

function unavailableResponse(requestId) {
  return { ...invalidResponse(requestId), status: NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE };
}

function httpStatusFor(status) {
  switch (status) {
    case NOVA_GATEWAY_STATUS.SUCCESS: return 200;
    case NOVA_GATEWAY_STATUS.AUTH_REQUIRED: return 401;
    case NOVA_GATEWAY_STATUS.QUOTA_EXHAUSTED:
    case NOVA_GATEWAY_STATUS.RATE_LIMITED: return 429;
    case NOVA_GATEWAY_STATUS.INVALID_REQUEST: return 400;
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

function safeRequestId(value) {
  return typeof value === 'string' && value.trim() ? value.trim().slice(0, 200) : 'invalid';
}
function boundedConfidence(value) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(0, Math.min(1, n)) : 0;
}
function nonNegative(value) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(0, n) : 0;
}
function integerHint(value) {
  const n = Number(value);
  return Number.isInteger(n) && n >= -1 ? n : -1;
}
function positiveInt(value, name) {
  const n = Number(value);
  if (!Number.isInteger(n) || n <= 0) throw new Error(`${name} must be a positive integer`);
  return n;
}
