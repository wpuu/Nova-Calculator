const DEFAULT_MAX_BODY_BYTES = 8 * 1024;

export const NOVA_PRODUCT_EVENT_STATUS = Object.freeze({
  ACCEPTED: 'ACCEPTED',
  AUTH_REQUIRED: 'AUTH_REQUIRED',
  INVALID_REQUEST: 'INVALID_REQUEST',
  RATE_LIMITED: 'RATE_LIMITED',
  TEMPORARILY_UNAVAILABLE: 'TEMPORARILY_UNAVAILABLE',
});

export const NOVA_PRODUCT_EVENT = Object.freeze({
  APP_FIRST_OPEN: 'app_first_open',
  AUTOTAP_SETTINGS_OPENED: 'autotap_settings_opened',
  AUTOTAP_DISCLOSURE_ACCEPTED: 'autotap_disclosure_accepted',
  AUTOTAP_ACCESSIBILITY_COMPLETED: 'autotap_accessibility_completed',
  AUTOTAP_OVERLAY_READY: 'autotap_overlay_ready',
  AUTOTAP_FIRST_START: 'autotap_first_start',
  AUTOTAP_STOP_VOLUME_DOWN: 'autotap_stop_volume_down',
  AUTOTAP_RUN_FAILED: 'autotap_run_failed',
  AUTOTAP_PROFILE_SAVED: 'autotap_profile_saved',
  AUTOTAP_PROFILE_LOADED: 'autotap_profile_loaded',
  AUTOTAP_SECOND_SESSION: 'autotap_second_session',
  PRO_PAYWALL_VIEWED: 'pro_paywall_viewed',
  PRO_PURCHASE_STARTED: 'pro_purchase_started',
  PRO_PURCHASE_VERIFIED: 'pro_purchase_verified',
  PURCHASE_RESTORE_SUCCESS: 'purchase_restore_success',
  PURCHASE_RESTORE_FAILED: 'purchase_restore_failed',
});

const KNOWN_EVENTS = new Set(Object.values(NOVA_PRODUCT_EVENT));
const ENTRY_SOURCES = new Set(['main_menu', 'settings', 'profile_limit', 'other']);
const PAYWALL_SOURCES = new Set(['autotap', 'settings', 'profile_limit', 'other']);

/**
 * Accepts only a fixed, privacy-safe product analytics vocabulary. The authenticated Nova session
 * supplies the pseudonymous subject and paid entitlement; neither is trusted from the request.
 */
export class ProductEventService {
  constructor({ authVerifier, eventStore, now = () => Date.now() } = {}) {
    if (!authVerifier || typeof authVerifier.verify !== 'function') {
      throw new Error('ProductEventService requires authVerifier');
    }
    if (!eventStore || typeof eventStore.record !== 'function') {
      throw new Error('ProductEventService requires eventStore.record');
    }
    if (typeof now !== 'function') throw new Error('ProductEventService requires a clock');
    this.authVerifier = authVerifier;
    this.eventStore = eventStore;
    this.now = now;
  }

  async execute({ authorization, request } = {}) {
    const principal = this.authVerifier.verify(authorization);
    if (!principal) return result(NOVA_PRODUCT_EVENT_STATUS.AUTH_REQUIRED);

    const parsed = parseEvent(request, this.now());
    if (!parsed) return result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST);

    try {
      await this.eventStore.record(Object.freeze({
        ...parsed,
        subjectId: principal.subjectId,
        entitlement: entitlementFor(principal.entitlements),
        receivedAtEpochMs: finiteNow(this.now()),
      }));
      return result(NOVA_PRODUCT_EVENT_STATUS.ACCEPTED);
    } catch (error) {
      if (error?.code === 'PRODUCT_EVENT_RATE_LIMITED') {
        return result(NOVA_PRODUCT_EVENT_STATUS.RATE_LIMITED);
      }
      return result(NOVA_PRODUCT_EVENT_STATUS.TEMPORARILY_UNAVAILABLE);
    }
  }
}

export function createProductEventFetchHandler({
  service,
  maxBodyBytes = DEFAULT_MAX_BODY_BYTES,
} = {}) {
  if (!service || typeof service.execute !== 'function') {
    throw new Error('createProductEventFetchHandler requires service.execute');
  }
  const bodyLimit = positiveInteger(maxBodyBytes, 'maxBodyBytes');

  return async function handle(request) {
    if (!request || typeof request.method !== 'string') {
      return jsonResponse(400, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST));
    }
    if (request.method.toUpperCase() !== 'POST') {
      return jsonResponse(405, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST), { allow: 'POST' });
    }
    const contentType = request.headers?.get?.('content-type') ?? '';
    if (!/^application\/json(?:\s*;|$)/i.test(contentType)) {
      return jsonResponse(415, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST));
    }
    const declared = Number(request.headers?.get?.('content-length'));
    if (Number.isFinite(declared) && declared > bodyLimit) {
      return jsonResponse(413, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST));
    }

    let text;
    try {
      text = await request.text();
    } catch {
      return jsonResponse(400, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST));
    }
    if (new TextEncoder().encode(text).byteLength > bodyLimit) {
      return jsonResponse(413, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST));
    }

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return jsonResponse(400, result(NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST));
    }

    let response;
    try {
      response = await service.execute({
        authorization: request.headers?.get?.('authorization') ?? '',
        request: body,
      });
    } catch {
      response = result(NOVA_PRODUCT_EVENT_STATUS.TEMPORARILY_UNAVAILABLE);
    }
    return jsonResponse(statusCode(response.status), response);
  };
}

function parseEvent(value, nowMs) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const allowedTopLevel = new Set([
    'eventId', 'event', 'eventVersion', 'occurredAtEpochMs', 'appVersion', 'sdk', 'properties',
  ]);
  if (Object.keys(value).some((key) => !allowedTopLevel.has(key))) return null;

  const eventId = boundedText(value.eventId, 80);
  const event = boundedText(value.event, 80);
  const appVersion = boundedText(value.appVersion, 50);
  const eventVersion = Number(value.eventVersion);
  const occurredAtEpochMs = Number(value.occurredAtEpochMs);
  const sdk = Number(value.sdk);
  if (!/^[A-Za-z0-9_-]{16,80}$/.test(eventId)
      || !KNOWN_EVENTS.has(event)
      || eventVersion !== 1
      || !appVersion
      || !Number.isSafeInteger(sdk) || sdk < 21 || sdk > 100
      || !Number.isFinite(occurredAtEpochMs) || occurredAtEpochMs < 0) {
    return null;
  }

  const serverNow = finiteNow(nowMs);
  // Bad device clocks must not create arbitrary Redis date buckets.
  const maxSkewMs = 7 * 24 * 60 * 60 * 1000;
  if (Math.abs(occurredAtEpochMs - serverNow) > maxSkewMs) return null;

  const properties = parseProperties(event, value.properties ?? {});
  if (properties == null) return null;
  return Object.freeze({
    eventId,
    event,
    eventVersion,
    occurredAtEpochMs,
    appVersion,
    sdk,
    properties,
  });
}

function parseProperties(event, value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const keys = Object.keys(value);

  if (event === NOVA_PRODUCT_EVENT.AUTOTAP_SETTINGS_OPENED) {
    if (keys.some((key) => key !== 'source')) return null;
    const source = boundedText(value.source, 30);
    return source && ENTRY_SOURCES.has(source) ? Object.freeze({ source }) : null;
  }

  if (event === NOVA_PRODUCT_EVENT.PRO_PAYWALL_VIEWED
      || event === NOVA_PRODUCT_EVENT.PRO_PURCHASE_STARTED) {
    if (keys.some((key) => key !== 'source')) return null;
    const source = boundedText(value.source, 30);
    return source && PAYWALL_SOURCES.has(source) ? Object.freeze({ source }) : null;
  }

  if (event === NOVA_PRODUCT_EVENT.AUTOTAP_RUN_FAILED) {
    if (keys.some((key) => key !== 'failureCode' && key !== 'manufacturer')) return null;
    const failureCode = Number(value.failureCode);
    const manufacturer = boundedText(value.manufacturer, 40);
    if (!Number.isSafeInteger(failureCode) || failureCode < 1 || failureCode > 99
        || !manufacturer || !/^[\p{L}\p{N} ._-]+$/u.test(manufacturer)) return null;
    return Object.freeze({ failureCode, manufacturer });
  }

  // Every other V1 event intentionally carries no arbitrary properties.
  return keys.length === 0 ? Object.freeze({}) : null;
}

function entitlementFor(entitlements) {
  const values = Array.isArray(entitlements) ? entitlements : [];
  if (values.includes('AI_PLUS')) return 'AI_PLUS';
  if (values.includes('PRO_LIFETIME')) return 'PRO_LIFETIME';
  return 'FREE';
}

function result(status) {
  return Object.freeze({ status });
}

function statusCode(status) {
  switch (status) {
    case NOVA_PRODUCT_EVENT_STATUS.ACCEPTED: return 202;
    case NOVA_PRODUCT_EVENT_STATUS.AUTH_REQUIRED: return 401;
    case NOVA_PRODUCT_EVENT_STATUS.INVALID_REQUEST: return 400;
    case NOVA_PRODUCT_EVENT_STATUS.RATE_LIMITED: return 429;
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

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}

function finiteNow(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) throw new Error('clock returned an invalid time');
  return number;
}
