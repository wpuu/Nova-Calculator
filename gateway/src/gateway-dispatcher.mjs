export const PROVIDER_FAILURE_KIND = Object.freeze({
  RATE_LIMIT: 'rate_limit',
  CREDENTIAL: 'credential',
  TRANSIENT: 'transient',
  REQUEST: 'request',
});

/**
 * Provider adapters should normalize upstream failures into this class before they reach
 * GatewayDispatcher. No raw API key or provider secret should ever be attached to the error.
 */
export class ProviderInvocationError extends Error {
  constructor(kind, message, options = {}) {
    super(message || kind);
    this.name = 'ProviderInvocationError';
    if (!Object.values(PROVIDER_FAILURE_KIND).includes(kind)) {
      throw new Error(`unknown provider failure kind: ${kind}`);
    }
    this.kind = kind;
    this.status = Number.isInteger(options.status) ? options.status : null;
    this.retryAfterMs = Number.isFinite(options.retryAfterMs) ? Math.max(0, options.retryAfterMs) : null;
  }

  static fromHttpStatus(status, message = `provider HTTP ${status}`, options = {}) {
    const code = Number(status);
    if (code === 429) {
      return new ProviderInvocationError(PROVIDER_FAILURE_KIND.RATE_LIMIT, message, {
        status: code,
        retryAfterMs: options.retryAfterMs,
      });
    }
    if (code === 401 || code === 403) {
      return new ProviderInvocationError(PROVIDER_FAILURE_KIND.CREDENTIAL, message, { status: code });
    }
    if (code === 408 || code === 425 || code >= 500) {
      return new ProviderInvocationError(PROVIDER_FAILURE_KIND.TRANSIENT, message, { status: code });
    }
    return new ProviderInvocationError(PROVIDER_FAILURE_KIND.REQUEST, message, { status: code });
  }
}

export class GatewayDispatchError extends Error {
  constructor(code, message, options = {}) {
    super(message);
    this.name = 'GatewayDispatchError';
    this.code = code;
    this.status = Number.isInteger(options.status) ? options.status : null;
    this.attemptedKeyIds = Object.freeze([...(options.attemptedKeyIds ?? [])]);
  }
}

/**
 * Dispatches one logical Nova AI request across independently limited provider credentials.
 *
 * keyPool methods may be synchronous (single-process tests/local use) or asynchronous (shared
 * Redis production capacity). Raw provider keys remain inside the server process.
 */
export class GatewayDispatcher {
  constructor({ keyPool, provider }) {
    if (!keyPool || typeof keyPool.lease !== 'function') {
      throw new Error('GatewayDispatcher requires a keyPool');
    }
    for (const method of ['reportSuccess', 'reportRateLimit', 'reportFailure', 'setEnabled']) {
      if (typeof keyPool[method] !== 'function') {
        throw new Error(`GatewayDispatcher keyPool requires ${method}`);
      }
    }
    if (!provider || typeof provider.invoke !== 'function') {
      throw new Error('GatewayDispatcher requires provider.invoke');
    }
    this.keyPool = keyPool;
    this.provider = provider;
  }

  async dispatch(request, priority) {
    const attempted = new Set();

    while (true) {
      const lease = await this.keyPool.lease(priority, { excludeIds: attempted });
      if (!lease) {
        throw new GatewayDispatchError(
          'NO_PROVIDER_CAPACITY',
          attempted.size === 0
            ? 'No provider capacity is currently available'
            : 'All eligible provider credentials failed for this request',
          { attemptedKeyIds: attempted },
        );
      }

      attempted.add(lease.id);

      try {
        const response = await this.provider.invoke({
          request,
          apiKey: lease.secret,
          keyId: lease.id,
        });
        await reportSuccessQuietly(this.keyPool, lease.id);
        return response;
      } catch (error) {
        if (!(error instanceof ProviderInvocationError)) {
          await this.keyPool.reportFailure(lease.id);
          continue;
        }

        switch (error.kind) {
          case PROVIDER_FAILURE_KIND.RATE_LIMIT:
            await this.keyPool.reportRateLimit(lease.id, error.retryAfterMs ?? undefined);
            continue;
          case PROVIDER_FAILURE_KIND.CREDENTIAL:
            await this.keyPool.setEnabled(lease.id, false);
            continue;
          case PROVIDER_FAILURE_KIND.TRANSIENT:
            await this.keyPool.reportFailure(lease.id);
            continue;
          case PROVIDER_FAILURE_KIND.REQUEST:
            throw new GatewayDispatchError(
              'PROVIDER_REQUEST_REJECTED',
              error.message,
              { status: error.status, attemptedKeyIds: attempted },
            );
          default:
            throw new Error(`unhandled provider failure kind: ${error.kind}`);
        }
      }
    }
  }
}

async function reportSuccessQuietly(keyPool, id) {
  try {
    await keyPool.reportSuccess(id);
  } catch {
    // The provider request already succeeded. A failure-counter reset must never turn a valid
    // answer into a client-visible 503 and trigger duplicate upstream work.
  }
}
