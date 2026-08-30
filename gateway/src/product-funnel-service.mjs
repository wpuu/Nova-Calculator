import { timingSafeEqual } from 'node:crypto';

import { NOVA_PRODUCT_EVENT } from './product-event-service.mjs';

const ENTITLEMENTS = Object.freeze(['FREE', 'PRO_LIFETIME', 'AI_PLUS']);
const FUNNEL_EVENTS = Object.freeze([
  NOVA_PRODUCT_EVENT.AUTOTAP_SETTINGS_OPENED,
  NOVA_PRODUCT_EVENT.AUTOTAP_DISCLOSURE_ACCEPTED,
  NOVA_PRODUCT_EVENT.AUTOTAP_ACCESSIBILITY_COMPLETED,
  NOVA_PRODUCT_EVENT.AUTOTAP_OVERLAY_READY,
  NOVA_PRODUCT_EVENT.AUTOTAP_FIRST_START,
  NOVA_PRODUCT_EVENT.AUTOTAP_STOP_VOLUME_DOWN,
  NOVA_PRODUCT_EVENT.AUTOTAP_RUN_FAILED,
  NOVA_PRODUCT_EVENT.AUTOTAP_PROFILE_SAVED,
  NOVA_PRODUCT_EVENT.AUTOTAP_PROFILE_LOADED,
  NOVA_PRODUCT_EVENT.AUTOTAP_SECOND_SESSION,
  NOVA_PRODUCT_EVENT.PRO_PAYWALL_VIEWED,
  NOVA_PRODUCT_EVENT.PRO_PURCHASE_STARTED,
  NOVA_PRODUCT_EVENT.PRO_PURCHASE_VERIFIED,
  NOVA_PRODUCT_EVENT.PURCHASE_RESTORE_SUCCESS,
  NOVA_PRODUCT_EVENT.PURCHASE_RESTORE_FAILED,
]);

/**
 * Admin-only aggregate reader. It returns no subject ids, event ids, Redis keys or raw payloads.
 */
export function createProductFunnelFetchHandler({
  store,
  adminToken,
  now = () => Date.now(),
} = {}) {
  if (!store || typeof store.readDaily !== 'function') {
    throw new Error('product funnel requires store.readDaily');
  }
  const expectedToken = validAdminToken(adminToken);
  if (typeof now !== 'function') throw new Error('product funnel requires clock');

  return async function handle(request) {
    if (!(request instanceof Request) || request.method !== 'GET') {
      return jsonResponse(405, { status: 'METHOD_NOT_ALLOWED' }, { allow: 'GET' });
    }
    if (!authorize(request.headers.get('authorization'), expectedToken)) {
      return jsonResponse(401, { status: 'AUTH_REQUIRED' });
    }

    let date;
    try {
      const url = new URL(request.url);
      date = normalizeDate(url.searchParams.get('date'), now());
    } catch {
      return jsonResponse(400, { status: 'INVALID_REQUEST' });
    }

    try {
      const dimensions = [];
      for (const event of FUNNEL_EVENTS) {
        for (const entitlement of ENTITLEMENTS) {
          dimensions.push(Object.freeze({ event, entitlement }));
        }
      }
      const rows = await store.readDaily(date, dimensions);
      return jsonResponse(200, {
        status: 'OK',
        date,
        rows,
      });
    } catch {
      return jsonResponse(503, { status: 'TEMPORARILY_UNAVAILABLE' });
    }
  };
}

function validAdminToken(value) {
  const token = String(value ?? '').trim();
  if (token.length < 32 || token.length > 256 || /\s/.test(token)) {
    throw new Error('NOVA_PRODUCT_ANALYTICS_ADMIN_TOKEN is invalid');
  }
  return token;
}

function authorize(header, expected) {
  const value = typeof header === 'string' ? header.trim() : '';
  if (!value.startsWith('Bearer ')) return false;
  const candidate = value.slice('Bearer '.length).trim();
  const left = Buffer.from(candidate);
  const right = Buffer.from(expected);
  return left.length === right.length && timingSafeEqual(left, right);
}

function normalizeDate(value, nowMs) {
  const candidate = String(value ?? '').trim();
  const text = candidate || new Date(nowMs).toISOString().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error('invalid date');
  const date = new Date(`${text}T00:00:00.000Z`);
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== text) {
    throw new Error('invalid date');
  }
  const today = new Date(nowMs).toISOString().slice(0, 10);
  if (text > today) throw new Error('future date is not allowed');
  return text;
}

function jsonResponse(status, body, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      ...extraHeaders,
    },
  });
}
