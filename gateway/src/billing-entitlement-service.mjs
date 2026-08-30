import { entitlementsFromVerifiedPurchases } from './google-play-purchase-verifier.mjs';

const DEFAULT_MAX_BODY_BYTES = 24 * 1024;
const MAX_PURCHASES = 2;

export const NOVA_BILLING_STATUS = Object.freeze({
  SUCCESS: 'SUCCESS',
  AUTH_REQUIRED: 'AUTH_REQUIRED',
  INVALID_REQUEST: 'INVALID_REQUEST',
  TEMPORARILY_UNAVAILABLE: 'TEMPORARILY_UNAVAILABLE',
});

/**
 * Converts the complete Play purchase snapshot observed by the app into a fresh signed Nova
 * entitlement session. Verification is server-side; client purchase state is never authoritative.
 */
export class BillingEntitlementService {
  constructor({
    authVerifier,
    purchaseVerifier,
    issueEntitlementSession,
    onVerifiedEntitlements = null,
  } = {}) {
    if (!authVerifier || typeof authVerifier.verify !== 'function') {
      throw new Error('BillingEntitlementService requires authVerifier');
    }
    if (!purchaseVerifier || typeof purchaseVerifier.verifyPurchase !== 'function') {
      throw new Error('BillingEntitlementService requires purchaseVerifier');
    }
    if (typeof issueEntitlementSession !== 'function') {
      throw new Error('BillingEntitlementService requires issueEntitlementSession');
    }
    if (onVerifiedEntitlements != null && typeof onVerifiedEntitlements !== 'function') {
      throw new Error('onVerifiedEntitlements must be a function');
    }
    this.authVerifier = authVerifier;
    this.purchaseVerifier = purchaseVerifier;
    this.issueEntitlementSession = issueEntitlementSession;
    this.onVerifiedEntitlements = onVerifiedEntitlements;
  }

  async execute({ authorization, request } = {}) {
    const principal = this.authVerifier.verify(authorization);
    if (!principal) return failure(NOVA_BILLING_STATUS.AUTH_REQUIRED);

    const parsed = parseRequest(request);
    if (!parsed) return failure(NOVA_BILLING_STATUS.INVALID_REQUEST);

    try {
      const verified = await Promise.all(
        parsed.purchases.map((purchase) => this.purchaseVerifier.verifyPurchase(purchase)),
      );
      const entitlements = entitlementsFromVerifiedPurchases(verified);
      const issued = this.issueEntitlementSession({
        subjectId: principal.subjectId,
        entitlements,
      });
      if (!issued?.token || !Number.isFinite(issued?.expiresAtEpochMs)) {
        throw new Error('entitlement session issuer returned invalid result');
      }

      // Funnel measurement is server-authoritative: only a Google-verified paid entitlement can
      // reach this callback. Analytics failure must never revoke or delay an otherwise valid
      // purchase, so it is isolated from the billing result.
      if (entitlements.length > 0 && this.onVerifiedEntitlements) {
        try {
          await this.onVerifiedEntitlements({
            subjectId: principal.subjectId,
            entitlements: Object.freeze([...entitlements]),
          });
        } catch {
          // Billing success remains authoritative even when the analytics backend is unavailable.
        }
      }

      return Object.freeze({
        status: NOVA_BILLING_STATUS.SUCCESS,
        sessionToken: issued.token,
        expiresAtEpochMs: issued.expiresAtEpochMs,
        entitlements,
        products: Object.freeze(verified.map((item) => Object.freeze({
          productId: item.productId,
          entitled: Boolean(item.entitled),
          state: item.state,
        }))),
      });
    } catch {
      // Do not downgrade the client to Free on a transient Google/API failure. The caller keeps
      // its last valid signed Nova session and retries later.
      return failure(NOVA_BILLING_STATUS.TEMPORARILY_UNAVAILABLE);
    }
  }
}

export function createBillingEntitlementFetchHandler({
  service,
  maxBodyBytes = DEFAULT_MAX_BODY_BYTES,
} = {}) {
  if (!service || typeof service.execute !== 'function') {
    throw new Error('createBillingEntitlementFetchHandler requires service.execute');
  }
  const bodyLimit = positiveInteger(maxBodyBytes, 'maxBodyBytes');

  return async function handle(request) {
    if (!request || typeof request.method !== 'string') {
      return jsonResponse(400, failure(NOVA_BILLING_STATUS.INVALID_REQUEST));
    }
    if (request.method.toUpperCase() !== 'POST') {
      return jsonResponse(405, failure(NOVA_BILLING_STATUS.INVALID_REQUEST), { allow: 'POST' });
    }
    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) {
      return jsonResponse(415, failure(NOVA_BILLING_STATUS.INVALID_REQUEST));
    }
    const declared = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declared) && declared > bodyLimit) {
      return jsonResponse(413, failure(NOVA_BILLING_STATUS.INVALID_REQUEST));
    }

    let text;
    try {
      text = await request.text();
    } catch {
      return jsonResponse(400, failure(NOVA_BILLING_STATUS.INVALID_REQUEST));
    }
    if (new TextEncoder().encode(text).byteLength > bodyLimit) {
      return jsonResponse(413, failure(NOVA_BILLING_STATUS.INVALID_REQUEST));
    }

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return jsonResponse(400, failure(NOVA_BILLING_STATUS.INVALID_REQUEST));
    }

    let result;
    try {
      result = await service.execute({
        authorization: request.headers?.get?.('authorization') ?? '',
        request: body,
      });
    } catch {
      result = failure(NOVA_BILLING_STATUS.TEMPORARILY_UNAVAILABLE);
    }
    return jsonResponse(statusCode(result.status), sanitizeResponse(result));
  };
}

function parseRequest(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  if (!Array.isArray(value.purchases) || value.purchases.length > MAX_PURCHASES) return null;

  const purchases = [];
  const productIds = new Set();
  for (const raw of value.purchases) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null;
    const productId = boundedText(raw.productId, 100);
    const productType = boundedText(raw.productType, 20).toLowerCase();
    const purchaseToken = boundedToken(raw.purchaseToken);
    if (!productId || !productType || !purchaseToken || productIds.has(productId)) return null;
    productIds.add(productId);
    purchases.push(Object.freeze({ productId, productType, purchaseToken }));
  }
  return Object.freeze({ purchases: Object.freeze(purchases) });
}

function sanitizeResponse(result) {
  const status = Object.values(NOVA_BILLING_STATUS).includes(result?.status)
    ? result.status
    : NOVA_BILLING_STATUS.TEMPORARILY_UNAVAILABLE;
  if (status !== NOVA_BILLING_STATUS.SUCCESS) return failure(status);
  return {
    status,
    sessionToken: typeof result.sessionToken === 'string' ? result.sessionToken : '',
    expiresAtEpochMs: Number.isFinite(result.expiresAtEpochMs) ? result.expiresAtEpochMs : 0,
    entitlements: Array.isArray(result.entitlements) ? [...result.entitlements] : [],
    products: Array.isArray(result.products) ? result.products.map((item) => ({
      productId: boundedText(item?.productId, 100),
      entitled: Boolean(item?.entitled),
      state: boundedText(item?.state, 100),
    })) : [],
  };
}

function failure(status) {
  return Object.freeze({
    status,
    sessionToken: '',
    expiresAtEpochMs: 0,
    entitlements: Object.freeze([]),
    products: Object.freeze([]),
  });
}

function statusCode(status) {
  switch (status) {
    case NOVA_BILLING_STATUS.SUCCESS: return 200;
    case NOVA_BILLING_STATUS.AUTH_REQUIRED: return 401;
    case NOVA_BILLING_STATUS.INVALID_REQUEST: return 400;
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

function boundedText(value, maxLength) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text && text.length <= maxLength && !/[\r\n]/.test(text) ? text : '';
}

function boundedToken(value) {
  const text = boundedText(value, 8192);
  return text && !/\s/.test(text) ? text : '';
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}
