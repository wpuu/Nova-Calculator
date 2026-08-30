const DEFAULT_TIMEOUT_MS = 5_000;
const DEFAULT_MAX_RESPONSE_BYTES = 64 * 1024;

/**
 * Minimal Upstash Redis REST client exposing only EVAL for Nova quota accounting.
 *
 * Credentials stay server-side. Redirects are disabled so the bearer token is never forwarded to
 * another origin. The rest of the quota system depends only on eval(script, keys, args).
 */
export class UpstashRedisEvalClient {
  constructor({
    url,
    token,
    fetchImpl = globalThis.fetch,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES,
  } = {}) {
    this.url = normalizeEndpoint(url);
    this.token = normalizeToken(token);
    if (typeof fetchImpl !== 'function') throw new Error('Upstash Redis client requires fetch');
    this.fetchImpl = fetchImpl;
    this.timeoutMs = positiveInteger(timeoutMs, 'timeoutMs');
    this.maxResponseBytes = positiveInteger(maxResponseBytes, 'maxResponseBytes');
  }

  async eval(script, keys = [], args = []) {
    const source = String(script ?? '');
    if (!source.trim()) throw new Error('Redis EVAL script must not be blank');
    if (!Array.isArray(keys) || !Array.isArray(args)) {
      throw new Error('Redis EVAL keys and args must be arrays');
    }
    const command = [
      'EVAL',
      source,
      String(keys.length),
      ...keys.map(commandValue),
      ...args.map(commandValue),
    ];

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      let response;
      try {
        response = await this.fetchImpl(this.url, {
          method: 'POST',
          redirect: 'manual',
          headers: {
            Authorization: `Bearer ${this.token}`,
            'Content-Type': 'application/json',
            Accept: 'application/json',
            'Accept-Encoding': 'identity',
          },
          body: JSON.stringify(command),
          signal: controller.signal,
        });
      } catch {
        throw new Error('Redis quota backend unavailable');
      }

      if (!response || response.status < 200 || response.status >= 300) {
        throw new Error('Redis quota backend unavailable');
      }
      const declaredLength = Number(response.headers?.get?.('content-length') ?? -1);
      if (Number.isFinite(declaredLength) && declaredLength > this.maxResponseBytes) {
        throw new Error('Redis quota backend returned oversized response');
      }

      let text;
      try {
        text = await response.text();
      } catch {
        throw new Error('Redis quota backend returned unreadable response');
      }
      if (new TextEncoder().encode(text).length > this.maxResponseBytes) {
        throw new Error('Redis quota backend returned oversized response');
      }

      let payload;
      try {
        payload = JSON.parse(text);
      } catch {
        throw new Error('Redis quota backend returned invalid JSON');
      }
      if (!payload || typeof payload !== 'object' || payload.error) {
        throw new Error('Redis quota backend rejected command');
      }
      if (!Object.prototype.hasOwnProperty.call(payload, 'result')) {
        throw new Error('Redis quota backend returned invalid result');
      }
      return payload.result;
    } finally {
      clearTimeout(timeout);
    }
  }

  safeSummary() {
    return Object.freeze({
      quotaStore: 'redis-eval-rest',
      timeoutMs: this.timeoutMs,
    });
  }
}

export function upstashRedisEvalClientFromEnv(env = process.env, options = {}) {
  return new UpstashRedisEvalClient({
    url: env.NOVA_QUOTA_REDIS_REST_URL,
    token: env.NOVA_QUOTA_REDIS_REST_TOKEN,
    fetchImpl: options.fetchImpl,
    timeoutMs: env.NOVA_QUOTA_REDIS_TIMEOUT_MS ?? options.timeoutMs ?? DEFAULT_TIMEOUT_MS,
  });
}

function normalizeEndpoint(value) {
  let parsed;
  try {
    parsed = new URL(String(value ?? '').trim());
  } catch {
    throw new Error('Redis quota REST URL is invalid');
  }
  if (parsed.protocol !== 'https:' || !parsed.hostname || parsed.username || parsed.password
      || parsed.search || parsed.hash) {
    throw new Error('Redis quota REST URL must be a credential-free HTTPS endpoint');
  }
  parsed.pathname = parsed.pathname.replace(/\/+$/, '') || '/';
  return parsed.toString();
}

function normalizeToken(value) {
  const token = String(value ?? '').trim();
  if (!token || token.length > 4096 || /[\r\n]/.test(token)) {
    throw new Error('Redis quota REST token is invalid');
  }
  return token;
}

function commandValue(value) {
  if (value === null || value === undefined) return '';
  return String(value);
}

function positiveInteger(value, name) {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) {
    throw new Error(`${name} must be a positive safe integer`);
  }
  return number;
}
