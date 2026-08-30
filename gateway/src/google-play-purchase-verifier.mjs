const ANDROID_PUBLISHER_ORIGIN = 'https://androidpublisher.googleapis.com';
const DEFAULT_TIMEOUT_MS = 7_000;
const DEFAULT_MAX_RESPONSE_BYTES = 128 * 1024;
const MAX_PURCHASE_TOKEN_CHARS = 8192;

export const NOVA_PLAY_PRODUCT = Object.freeze({
  PRO_LIFETIME: 'nova_pro_lifetime',
  AI_PLUS: 'nova_ai_plus',
});

export const NOVA_PLAY_PRODUCT_TYPE = Object.freeze({
  INAPP: 'inapp',
  SUBS: 'subs',
});

export const NOVA_ENTITLEMENT = Object.freeze({
  PRO_LIFETIME: 'PRO_LIFETIME',
  AI_PLUS: 'AI_PLUS',
});

/**
 * Verifies Google Play purchases on Nova's server and acknowledges valid new purchases.
 *
 * The client never decides entitlement. Only these exact launch product ids are accepted, and all
 * tokens are checked directly against the Google Play Developer API before an entitlement is
 * returned.
 */
export class GooglePlayPurchaseVerifier {
  constructor({
    packageName,
    accessTokenProvider,
    fetchImpl = globalThis.fetch,
    now = () => Date.now(),
    timeoutMs = DEFAULT_TIMEOUT_MS,
    maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES,
  } = {}) {
    this.packageName = androidPackage(packageName);
    if (!accessTokenProvider || typeof accessTokenProvider.getAccessToken !== 'function') {
      throw new Error('GooglePlayPurchaseVerifier requires accessTokenProvider');
    }
    if (typeof fetchImpl !== 'function') throw new Error('GooglePlayPurchaseVerifier requires fetch');
    if (typeof now !== 'function') throw new Error('GooglePlayPurchaseVerifier requires a clock');
    this.accessTokenProvider = accessTokenProvider;
    this.fetchImpl = fetchImpl;
    this.now = now;
    this.timeoutMs = positiveInteger(timeoutMs, 'timeoutMs');
    this.maxResponseBytes = positiveInteger(maxResponseBytes, 'maxResponseBytes');
  }

  async verifyPurchase({ productId, productType, purchaseToken } = {}) {
    const product = knownProduct(productId, productType);
    const token = safePurchaseToken(purchaseToken);
    if (product.productType === NOVA_PLAY_PRODUCT_TYPE.INAPP) {
      return this.verifyOneTime(product, token);
    }
    return this.verifySubscription(product, token);
  }

  async verifyOneTime(product, token) {
    const path = `/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}`
      + `/purchases/products/${encodeURIComponent(product.productId)}`
      + `/tokens/${encodeURIComponent(token)}`;
    const payload = await this.googleJson(path, { method: 'GET' });

    const purchased = Number(payload?.purchaseState) === 0;
    if (payload?.productId && payload.productId !== product.productId) {
      throw new Error('Google Play returned mismatched one-time product');
    }
    if (!purchased) {
      return frozenResult(product, false, false, 'not-purchased');
    }

    const needsAcknowledgement = Number(payload?.acknowledgementState) === 0;
    if (needsAcknowledgement) {
      await this.acknowledgeOneTime(product.productId, token);
    }
    return frozenResult(product, true, needsAcknowledgement, 'purchased');
  }

  async verifySubscription(product, token) {
    const path = `/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}`
      + `/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`;
    const payload = await this.googleJson(path, { method: 'GET' });

    const matchingItems = Array.isArray(payload?.lineItems)
      ? payload.lineItems.filter((item) => item?.productId === product.productId)
      : [];
    if (matchingItems.length === 0) {
      throw new Error('Google Play returned subscription without expected product');
    }

    const state = String(payload?.subscriptionState ?? '').trim();
    const entitled = subscriptionEntitled(state, matchingItems, finiteNow(this.now()));
    if (!entitled) {
      return frozenResult(product, false, false, state || 'unknown');
    }

    const needsAcknowledgement = payload?.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING';
    if (needsAcknowledgement) {
      await this.acknowledgeSubscription(product.productId, token);
    }
    return frozenResult(product, true, needsAcknowledgement, state);
  }

  async acknowledgeOneTime(productId, token) {
    const path = `/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}`
      + `/purchases/products/${encodeURIComponent(productId)}`
      + `/tokens/${encodeURIComponent(token)}:acknowledge`;
    await this.googleEmpty(path, { method: 'POST', body: '{}' });
  }

  async acknowledgeSubscription(productId, token) {
    const path = `/androidpublisher/v3/applications/${encodeURIComponent(this.packageName)}`
      + `/purchases/subscriptions/${encodeURIComponent(productId)}`
      + `/tokens/${encodeURIComponent(token)}:acknowledge`;
    await this.googleEmpty(path, { method: 'POST', body: '{}' });
  }

  async googleJson(path, options) {
    return this.googleRequest(path, options, true);
  }

  async googleEmpty(path, options) {
    await this.googleRequest(path, options, false);
  }

  async googleRequest(path, options, expectJson) {
    let token = await this.accessTokenProvider.getAccessToken();
    let response = await this.fetchGoogle(path, options, token);
    if (response?.status === 401 && typeof this.accessTokenProvider.invalidate === 'function') {
      this.accessTokenProvider.invalidate();
      token = await this.accessTokenProvider.getAccessToken({ forceRefresh: true });
      response = await this.fetchGoogle(path, options, token);
    }
    if (!response || response.status < 200 || response.status >= 300) {
      throw new Error('Google Play purchase API rejected request');
    }
    if (!expectJson) return null;
    return readJsonBounded(response, this.maxResponseBytes);
  }

  async fetchGoogle(path, options, accessToken) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      try {
        return await this.fetchImpl(`${ANDROID_PUBLISHER_ORIGIN}${path}`, {
          ...options,
          redirect: 'manual',
          signal: controller.signal,
          headers: {
            authorization: `Bearer ${accessToken}`,
            accept: 'application/json',
            'content-type': 'application/json; charset=utf-8',
            'accept-encoding': 'identity',
            ...(options?.headers ?? {}),
          },
        });
      } catch {
        throw new Error('Google Play purchase API unavailable');
      }
    } finally {
      clearTimeout(timeout);
    }
  }
}

export function entitlementsFromVerifiedPurchases(results) {
  const entitlements = new Set();
  for (const result of Array.isArray(results) ? results : []) {
    if (!result?.entitled) continue;
    if (result.productId === NOVA_PLAY_PRODUCT.PRO_LIFETIME) {
      entitlements.add(NOVA_ENTITLEMENT.PRO_LIFETIME);
    } else if (result.productId === NOVA_PLAY_PRODUCT.AI_PLUS) {
      // AI Plus intentionally includes Pro so the paywall remains a simple Free / Pro / AI Plus
      // ladder instead of forcing subscribers to make a second purchase.
      entitlements.add(NOVA_ENTITLEMENT.PRO_LIFETIME);
      entitlements.add(NOVA_ENTITLEMENT.AI_PLUS);
    }
  }
  return Object.freeze([...entitlements].sort());
}

function knownProduct(productId, productType) {
  const id = String(productId ?? '').trim();
  const type = String(productType ?? '').trim().toLowerCase();
  if (id === NOVA_PLAY_PRODUCT.PRO_LIFETIME && type === NOVA_PLAY_PRODUCT_TYPE.INAPP) {
    return Object.freeze({
      productId: id,
      productType: type,
      entitlement: NOVA_ENTITLEMENT.PRO_LIFETIME,
    });
  }
  if (id === NOVA_PLAY_PRODUCT.AI_PLUS && type === NOVA_PLAY_PRODUCT_TYPE.SUBS) {
    return Object.freeze({
      productId: id,
      productType: type,
      entitlement: NOVA_ENTITLEMENT.AI_PLUS,
    });
  }
  throw new Error('unknown Nova Play product');
}

function subscriptionEntitled(state, matchingItems, nowMs) {
  if (state === 'SUBSCRIPTION_STATE_ACTIVE' || state === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD') {
    return true;
  }
  if (state !== 'SUBSCRIPTION_STATE_CANCELED') return false;
  return matchingItems.some((item) => {
    const expiresAt = Date.parse(String(item?.expiryTime ?? ''));
    return Number.isFinite(expiresAt) && expiresAt > nowMs;
  });
}

function frozenResult(product, entitled, acknowledgedNow, state) {
  return Object.freeze({
    productId: product.productId,
    productType: product.productType,
    entitlement: product.entitlement,
    entitled: Boolean(entitled),
    acknowledgedNow: Boolean(acknowledgedNow),
    state: String(state ?? '').slice(0, 100),
  });
}

async function readJsonBounded(response, maxBytes) {
  const declared = Number(response.headers?.get?.('content-length') ?? -1);
  if (Number.isFinite(declared) && declared > maxBytes) {
    throw new Error('Google Play purchase API returned oversized response');
  }
  let text;
  try {
    text = await response.text();
  } catch {
    throw new Error('Google Play purchase API returned unreadable response');
  }
  if (new TextEncoder().encode(text).byteLength > maxBytes) {
    throw new Error('Google Play purchase API returned oversized response');
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('Google Play purchase API returned invalid JSON');
  }
}

function safePurchaseToken(value) {
  const token = String(value ?? '').trim();
  if (!token || token.length > MAX_PURCHASE_TOKEN_CHARS || /[\r\n\s]/.test(token)) {
    throw new Error('purchaseToken is invalid');
  }
  return token;
}

function androidPackage(value) {
  const text = String(value ?? '').trim();
  if (!/^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$/.test(text)
      || text.length > 255) {
    throw new Error('Android package name is invalid');
  }
  return text;
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${name} must be positive`);
  return number;
}

function finiteNow(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) throw new Error('clock returned invalid time');
  return number;
}
