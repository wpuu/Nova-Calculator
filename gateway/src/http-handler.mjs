import { NOVA_GATEWAY_STATUS } from './nova-ai-service.mjs';

const DEFAULT_MAX_BODY_BYTES = 16 * 1024;

/** Framework-neutral Fetch API handler for Nova's public AI endpoint. */
export function createNovaFetchHandler({ service, maxBodyBytes = DEFAULT_MAX_BODY_BYTES }) {
  if (!service || typeof service.execute !== 'function') {
    throw new Error('createNovaFetchHandler requires service.execute');
  }
  const bodyLimit = positiveInt(maxBodyBytes, 'maxBodyBytes');

  return async function handle(request) {
    if (!request || typeof request.method !== 'string') {
      return jsonResponse(400, invalidResponse('invalid'));
    }
    if (request.method.toUpperCase() !== 'POST') {
      return jsonResponse(405, invalidResponse('invalid'), { allow: 'POST' });
    }

    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) {
      return jsonResponse(415, invalidResponse('invalid'));
    }

    const declaredLength = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declaredLength) && declaredLength > bodyLimit) {
      return jsonResponse(413, invalidResponse('invalid'));
    }

    let text;
    try {
      text = await request.text();
    } catch {
      return jsonResponse(400, invalidResponse('invalid'));
    }
    if (byteLength(text) > bodyLimit) {
      return jsonResponse(413, invalidResponse('invalid'));
    }

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return jsonResponse(400, invalidResponse('invalid'));
    }

    const requestId = safeRequestId(body?.requestId);
    try {
      const result = await service.execute({
        authorization: request.headers?.get?.('authorization') ?? '',
        request: body,
      });
      return jsonResponse(httpStatusFor(result?.status), sanitizeServiceResponse(result, requestId));
    } catch {
      return jsonResponse(503, unavailableResponse(requestId));
    }
  };
}

function sanitizeServiceResponse(result, fallbackRequestId) {
  const status = Object.values(NOVA_GATEWAY_STATUS).includes(result?.status)
    ? result.status
    : NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE;
  const response = {
    requestId: safeRequestId(result?.requestId) || fallbackRequestId,
    status,
    answer: status === NOVA_GATEWAY_STATUS.SUCCESS && typeof result?.answer === 'string'
      ? result.answer
      : '',
    retryAfterSeconds: nonNegative(result?.retryAfterSeconds),
    remainingRequestHint: integerHint(result?.remainingRequestHint),
    quotaResetAtEpochMs: nonNegative(result?.quotaResetAtEpochMs),
  };
  if (status === NOVA_GATEWAY_STATUS.SUCCESS && typeof result?.candidateExpression === 'string') {
    const candidate = result.candidateExpression.trim();
    if (candidate) response.candidateExpression = candidate.slice(0, 1024);
  }
  return response;
}

function invalidResponse(requestId) {
  return {
    requestId,
    status: NOVA_GATEWAY_STATUS.INVALID_REQUEST,
    answer: '',
    retryAfterSeconds: 0,
    remainingRequestHint: -1,
    quotaResetAtEpochMs: 0,
  };
}

function unavailableResponse(requestId) {
  return {
    requestId,
    status: NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE,
    answer: '',
    retryAfterSeconds: 0,
    remainingRequestHint: -1,
    quotaResetAtEpochMs: 0,
  };
}

function httpStatusFor(status) {
  switch (status) {
    case NOVA_GATEWAY_STATUS.SUCCESS:
      return 200;
    case NOVA_GATEWAY_STATUS.AUTH_REQUIRED:
      return 401;
    case NOVA_GATEWAY_STATUS.QUOTA_EXHAUSTED:
    case NOVA_GATEWAY_STATUS.RATE_LIMITED:
      return 429;
    case NOVA_GATEWAY_STATUS.INVALID_REQUEST:
      return 400;
    case NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE:
    default:
      return 503;
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

function nonNegative(value) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, number) : 0;
}

function integerHint(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= -1 ? number : -1;
}

function byteLength(value) {
  return new TextEncoder().encode(value).byteLength;
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) throw new Error(`${name} must be a positive integer`);
  return number;
}
