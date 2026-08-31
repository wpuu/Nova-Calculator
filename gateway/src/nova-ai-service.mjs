import { GatewayDispatchError } from './gateway-dispatcher.mjs';
import { REQUEST_PRIORITY } from './provider-key-pool.mjs';

export const NOVA_GATEWAY_STATUS = Object.freeze({
  SUCCESS: 'SUCCESS',
  AUTH_REQUIRED: 'AUTH_REQUIRED',
  QUOTA_EXHAUSTED: 'QUOTA_EXHAUSTED',
  RATE_LIMITED: 'RATE_LIMITED',
  INVALID_REQUEST: 'INVALID_REQUEST',
  TEMPORARILY_UNAVAILABLE: 'TEMPORARILY_UNAVAILABLE',
});

export const SERVER_ENTITLEMENT = Object.freeze({
  PRO_LIFETIME: 'PRO_LIFETIME',
  AI_PLUS: 'AI_PLUS',
});

export const QUOTA_DECISION = Object.freeze({
  ALLOWED: 'ALLOWED',
  QUOTA_EXHAUSTED: 'QUOTA_EXHAUSTED',
  RATE_LIMITED: 'RATE_LIMITED',
});

const EXPLAIN_OPERATION = 'EXPLAIN_CALCULATION';
const NATURAL_LANGUAGE_OPERATION = 'PARSE_NATURAL_LANGUAGE_CALCULATION';

/**
 * Server-authoritative orchestration for one Nova AI request.
 *
 * Android-side entitlement state is deliberately not an input to authorization or priority.
 * Natural-language parsing may return a candidate expression, never an authoritative numeric
 * result. Android evaluates that expression with the deterministic calculator engine.
 */
export class NovaAiService {
  constructor({ authVerifier, quotaLedger, dispatcher }) {
    if (!authVerifier || typeof authVerifier.verify !== 'function') {
      throw new Error('NovaAiService requires authVerifier.verify');
    }
    if (!quotaLedger || typeof quotaLedger.reserve !== 'function') {
      throw new Error('NovaAiService requires quotaLedger.reserve');
    }
    if (typeof quotaLedger.commit !== 'function' || typeof quotaLedger.release !== 'function') {
      throw new Error('NovaAiService requires quotaLedger commit/release');
    }
    if (!dispatcher || typeof dispatcher.dispatch !== 'function') {
      throw new Error('NovaAiService requires dispatcher.dispatch');
    }
    this.authVerifier = authVerifier;
    this.quotaLedger = quotaLedger;
    this.dispatcher = dispatcher;
  }

  async execute({ authorization, request }) {
    const validated = validateClientRequest(request);
    if (!validated.ok) return gatewayResponse(validated.requestId, NOVA_GATEWAY_STATUS.INVALID_REQUEST);

    let principal;
    try {
      principal = normalizePrincipal(await this.authVerifier.verify(authorization));
    } catch {
      principal = null;
    }
    if (!principal) {
      return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.AUTH_REQUIRED);
    }

    const priority = priorityForPrincipal(principal);
    let quota;
    try {
      quota = normalizeQuotaDecision(await this.quotaLedger.reserve({
        subjectId: principal.subjectId,
        priority,
        operation: validated.request.operation,
        requestId: validated.request.requestId,
      }));
    } catch {
      return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
    }

    if (quota.status === QUOTA_DECISION.QUOTA_EXHAUSTED) {
      return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.QUOTA_EXHAUSTED, {
        remainingRequestHint: quota.remainingRequestHint,
        quotaResetAtEpochMs: quota.quotaResetAtEpochMs,
      });
    }
    if (quota.status === QUOTA_DECISION.RATE_LIMITED) {
      return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.RATE_LIMITED, {
        retryAfterSeconds: quota.retryAfterSeconds,
        remainingRequestHint: quota.remainingRequestHint,
        quotaResetAtEpochMs: quota.quotaResetAtEpochMs,
      });
    }

    try {
      const providerResult = await this.dispatcher.dispatch(validated.request, priority);
      await settleQuietly(this.quotaLedger, 'commit', quota.reservationId);
      return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.SUCCESS, {
        answer: providerResult?.answer,
        candidateExpression: providerResult?.candidateExpression,
        remainingRequestHint: quota.remainingRequestHint,
        quotaResetAtEpochMs: quota.quotaResetAtEpochMs,
      });
    } catch (error) {
      await settleQuietly(this.quotaLedger, 'release', quota.reservationId);
      if (error instanceof GatewayDispatchError && error.code === 'PROVIDER_REQUEST_REJECTED') {
        return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.INVALID_REQUEST);
      }
      return gatewayResponse(validated.request.requestId, NOVA_GATEWAY_STATUS.TEMPORARILY_UNAVAILABLE);
    }
  }
}

export function priorityForPrincipal(principal) {
  const entitlements = new Set(principal?.entitlements ?? []);
  if (entitlements.has(SERVER_ENTITLEMENT.AI_PLUS)) return REQUEST_PRIORITY.AI_PLUS;
  if (entitlements.has(SERVER_ENTITLEMENT.PRO_LIFETIME)) return REQUEST_PRIORITY.PRO;
  return REQUEST_PRIORITY.FREE;
}

function validateClientRequest(request) {
  const requestId = safeText(request?.requestId);
  if (!requestId) return { ok: false, requestId: 'invalid' };

  if (request?.operation === EXPLAIN_OPERATION) {
    const expression = safeText(request.expression);
    const deterministicResult = safeText(request.deterministicResult);
    if (!expression || !deterministicResult || expression.length > 4096 || deterministicResult.length > 1024) {
      return { ok: false, requestId };
    }
    return {
      ok: true,
      request: Object.freeze({
        requestId,
        operation: EXPLAIN_OPERATION,
        expression,
        deterministicResult,
        localeTag: boundedLocale(request.localeTag),
      }),
    };
  }

  if (request?.operation === NATURAL_LANGUAGE_OPERATION) {
    const naturalLanguageQuery = safeText(request.naturalLanguageQuery);
    if (!naturalLanguageQuery || naturalLanguageQuery.length > 2000) {
      return { ok: false, requestId };
    }
    return {
      ok: true,
      request: Object.freeze({
        requestId,
        operation: NATURAL_LANGUAGE_OPERATION,
        naturalLanguageQuery,
        localeTag: boundedLocale(request.localeTag),
      }),
    };
  }

  return { ok: false, requestId };
}

function boundedLocale(value) {
  const locale = safeText(value) || 'und';
  return locale.length <= 64 ? locale : 'und';
}

function normalizePrincipal(principal) {
  const subjectId = safeText(principal?.subjectId);
  if (!subjectId) return null;
  const entitlements = principal.entitlements instanceof Set
    ? [...principal.entitlements]
    : Array.isArray(principal.entitlements) ? principal.entitlements : [];
  return Object.freeze({ subjectId, entitlements: Object.freeze(entitlements.map(String)) });
}

function normalizeQuotaDecision(decision) {
  if (!decision || !Object.values(QUOTA_DECISION).includes(decision.status)) {
    throw new Error('invalid quota decision');
  }

  const common = {
    remainingRequestHint: integerHint(decision.remainingRequestHint),
    quotaResetAtEpochMs: nonNegativeNumber(decision.quotaResetAtEpochMs),
    retryAfterSeconds: nonNegativeNumber(decision.retryAfterSeconds),
  };

  if (decision.status === QUOTA_DECISION.ALLOWED) {
    const reservationId = safeText(decision.reservationId);
    if (!reservationId) throw new Error('allowed quota decision requires reservationId');
    return Object.freeze({ ...common, status: decision.status, reservationId });
  }
  return Object.freeze({ ...common, status: decision.status });
}

async function settleQuietly(ledger, method, reservationId) {
  try {
    await ledger[method](reservationId);
  } catch {
    // A reservation already protected capacity. Accounting reconciliation belongs to the ledger.
  }
}

function gatewayResponse(requestId, status, options = {}) {
  const response = {
    requestId,
    status,
    answer: status === NOVA_GATEWAY_STATUS.SUCCESS ? safeText(options.answer) || '' : '',
    retryAfterSeconds: nonNegativeNumber(options.retryAfterSeconds),
    remainingRequestHint: integerHint(options.remainingRequestHint),
    quotaResetAtEpochMs: nonNegativeNumber(options.quotaResetAtEpochMs),
  };
  const candidateExpression = status === NOVA_GATEWAY_STATUS.SUCCESS
    ? safeText(options.candidateExpression)
    : '';
  if (candidateExpression) {
    response.candidateExpression = candidateExpression.slice(0, 1024);
  }
  return Object.freeze(response);
}

function safeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function nonNegativeNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, number) : 0;
}

function integerHint(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= -1 ? number : -1;
}
