export const REQUEST_PRIORITY = Object.freeze({
  FREE: 'free',
  PRO: 'pro',
  AI_PLUS: 'ai_plus',
});

const WINDOW_MS = 60_000;

/**
 * Provider-neutral pool for legitimately independent upstream API keys.
 *
 * Raw secrets are returned only by lease() to the server-side provider adapter and are
 * deliberately omitted from snapshots/log-friendly state.
 */
export class ProviderKeyPool {
  constructor(keys, options = {}) {
    if (!Array.isArray(keys) || keys.length === 0) {
      throw new Error('ProviderKeyPool requires at least one key');
    }

    this.now = options.now ?? (() => Date.now());
    this.paidReserveFraction = clampFraction(options.paidReserveFraction ?? 0.2);
    this.cooldownOnFailureMs = positiveInt(options.cooldownOnFailureMs ?? 30_000, 'cooldownOnFailureMs');
    this.maxFailuresBeforeCooldown = positiveInt(options.maxFailuresBeforeCooldown ?? 3, 'maxFailuresBeforeCooldown');

    const ids = new Set();
    const startedAt = this.now();
    this.keys = keys.map((key, index) => {
      const id = String(key.id ?? `key-${index + 1}`).trim();
      const secret = String(key.secret ?? '').trim();
      const rpmLimit = positiveInt(key.rpmLimit ?? 20, `rpmLimit(${id})`);
      if (!id) throw new Error('key id must not be blank');
      if (!secret) throw new Error(`secret(${id}) must not be blank`);
      if (ids.has(id)) throw new Error(`duplicate key id: ${id}`);
      ids.add(id);
      return {
        id,
        secret,
        rpmLimit,
        enabled: key.enabled !== false,
        windowStartMs: startedAt,
        requestsInWindow: 0,
        cooldownUntilMs: 0,
        consecutiveFailures: 0,
        lastUsedAtMs: 0,
      };
    });
  }

  lease(priority = REQUEST_PRIORITY.FREE, options = {}) {
    assertPriority(priority);
    const excludedIds = normalizeExcludedIds(options.excludeIds);
    const now = this.now();
    for (const key of this.keys) this.#refreshWindow(key, now);

    const eligible = this.keys.filter((key) => (
      !excludedIds.has(key.id) && this.#isEligible(key, priority, now)
    ));
    if (eligible.length === 0) return null;

    eligible.sort((a, b) => {
      const utilizationA = a.requestsInWindow / a.rpmLimit;
      const utilizationB = b.requestsInWindow / b.rpmLimit;
      if (utilizationA !== utilizationB) return utilizationA - utilizationB;
      if (a.consecutiveFailures !== b.consecutiveFailures) {
        return a.consecutiveFailures - b.consecutiveFailures;
      }
      if (a.lastUsedAtMs !== b.lastUsedAtMs) return a.lastUsedAtMs - b.lastUsedAtMs;
      return a.id.localeCompare(b.id);
    });

    const selected = eligible[0];
    selected.requestsInWindow += 1;
    selected.lastUsedAtMs = now;
    return Object.freeze({ id: selected.id, secret: selected.secret });
  }

  reportSuccess(id) {
    const key = this.#requireKey(id);
    key.consecutiveFailures = 0;
  }

  reportRateLimit(id, retryAfterMs = WINDOW_MS) {
    const key = this.#requireKey(id);
    const now = this.now();
    key.cooldownUntilMs = Math.max(
      key.cooldownUntilMs,
      now + Math.max(1_000, Number.isFinite(retryAfterMs) ? retryAfterMs : WINDOW_MS),
    );
    key.requestsInWindow = key.rpmLimit;
    key.consecutiveFailures += 1;
  }

  reportFailure(id) {
    const key = this.#requireKey(id);
    key.consecutiveFailures += 1;
    if (key.consecutiveFailures >= this.maxFailuresBeforeCooldown) {
      key.cooldownUntilMs = Math.max(key.cooldownUntilMs, this.now() + this.cooldownOnFailureMs);
    }
  }

  setEnabled(id, enabled) {
    this.#requireKey(id).enabled = Boolean(enabled);
  }

  snapshot() {
    const now = this.now();
    return this.keys.map((key) => {
      this.#refreshWindow(key, now);
      return Object.freeze({
        id: key.id,
        rpmLimit: key.rpmLimit,
        enabled: key.enabled,
        requestsInWindow: key.requestsInWindow,
        remainingInWindow: Math.max(0, key.rpmLimit - key.requestsInWindow),
        cooldownUntilMs: key.cooldownUntilMs,
        consecutiveFailures: key.consecutiveFailures,
        lastUsedAtMs: key.lastUsedAtMs,
      });
    });
  }

  #isEligible(key, priority, now) {
    if (!key.enabled || key.cooldownUntilMs > now) return false;

    const ceiling = priority === REQUEST_PRIORITY.FREE
      ? Math.max(0, Math.floor(key.rpmLimit * (1 - this.paidReserveFraction)))
      : key.rpmLimit;

    return key.requestsInWindow < ceiling;
  }

  #refreshWindow(key, now) {
    if (now - key.windowStartMs >= WINDOW_MS) {
      key.windowStartMs = now;
      key.requestsInWindow = 0;
    }
  }

  #requireKey(id) {
    const key = this.keys.find((candidate) => candidate.id === id);
    if (!key) throw new Error(`unknown key id: ${id}`);
    return key;
  }
}

function normalizeExcludedIds(value) {
  if (value == null) return new Set();
  if (value instanceof Set) return value;
  if (Array.isArray(value)) return new Set(value.map(String));
  throw new Error('excludeIds must be an array or Set');
}

function assertPriority(priority) {
  if (!Object.values(REQUEST_PRIORITY).includes(priority)) {
    throw new Error(`unknown request priority: ${priority}`);
  }
}

function positiveInt(value, name) {
  const number = Number(value);
  if (!Number.isInteger(number) || number <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return number;
}

function clampFraction(value) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0 || number >= 1) {
    throw new Error('paidReserveFraction must be >= 0 and < 1');
  }
  return number;
}
