import { BrowserSessionService, createBrowserSessionFetchHandler } from './browser-session.mjs';
import { googleBrowserAccessTokenVerifierFromEnv } from './google-browser-access-token-verifier.mjs';
import { createMacroCandidateReviewFetchHandler } from './macro-candidate-review-http-handler.mjs';
import { MacroCandidateReviewService } from './macro-candidate-review-service.mjs';
import { DailyQuotaLedger } from './quota-ledger.mjs';
import { quotaPolicyFromEnv } from './quota-policy.mjs';
import { RedisProviderKeyPool } from './redis-provider-key-pool.mjs';
import { redisQuotaStoreFromEnv } from './quota-store-runtime.mjs';
import { createGatewayRuntime } from './runtime.mjs';
import { sessionTokenServiceFromEnv } from './session-token.mjs';
import { upstashRedisEvalClientFromEnv } from './upstash-redis-eval-client.mjs';

/**
 * Minimal production composition for Chrome Macro.
 *
 * This intentionally excludes Android Play Integrity, Google Play Billing, product analytics,
 * and calculator AI routes. A browser-only Preview must not require or receive Android production
 * credentials merely to verify Google OAuth -> Nova session -> candidate-only Macro review.
 */
export function createMacroProductionNovaGatewayApplication(options = {}) {
  const env = options.env ?? process.env;
  const now = typeof options.now === 'function' ? options.now : () => Date.now();

  let redisEvalClient = options.redisEvalClient;
  if ((!options.quotaStore || !options.keyPoolFactory) && !redisEvalClient) {
    redisEvalClient = upstashRedisEvalClientFromEnv(env, {
      fetchImpl: options.fetchImpl,
      timeoutMs: options.redisTimeoutMs,
    });
  }

  const quotaStore = options.quotaStore ?? redisQuotaStoreFromEnv(env, {
    evalClient: redisEvalClient,
  });

  const keyPoolFactory = options.keyPoolFactory ?? ((poolOptions) => new RedisProviderKeyPool(
    poolOptions.keys,
    {
      evalClient: redisEvalClient,
      keyPrefix: env.NOVA_PROVIDER_REDIS_KEY_PREFIX || 'nova:provider:v1',
      paidReserveFraction: poolOptions.paidReserveFraction,
      cooldownOnFailureMs: poolOptions.cooldownOnFailureMs,
      maxFailuresBeforeCooldown: poolOptions.maxFailuresBeforeCooldown,
      now: poolOptions.now,
    },
  ));

  const providerRuntime = createGatewayRuntime(env, {
    fetchImpl: options.fetchImpl,
    now,
    keyPoolFactory,
  });
  const sessionTokens = sessionTokenServiceFromEnv(env, { now });
  const authVerifier = Object.freeze({
    verify(authorization) {
      return sessionTokens.verify(authorization);
    },
  });
  const quotaLedger = new DailyQuotaLedger({
    store: quotaStore,
    policy: quotaPolicyFromEnv(env),
    now,
    newReservationId: options.newReservationId,
  });
  const macroCandidateReviewService = new MacroCandidateReviewService({
    authVerifier,
    quotaLedger,
    dispatcher: providerRuntime.macroCandidateReviewDispatcher,
  });
  const macroCandidateReviewHandler = createMacroCandidateReviewFetchHandler({
    service: macroCandidateReviewService,
  });

  let browserIdentityVerifier = options.browserIdentityVerifier ?? null;
  if (!browserIdentityVerifier && String(env.NOVA_CHROME_OAUTH_CLIENT_IDS ?? '').trim()) {
    browserIdentityVerifier = googleBrowserAccessTokenVerifierFromEnv(env, {
      fetchImpl: options.fetchImpl,
      now,
    });
  }

  let browserSessionHandler = null;
  if (browserIdentityVerifier) {
    const browserSessionService = new BrowserSessionService({
      tokenService: sessionTokens,
      identityVerifier: browserIdentityVerifier,
      now,
      sessionTtlMs: env.NOVA_BROWSER_SESSION_TTL_MS ?? 60 * 60 * 1000,
    });
    browserSessionHandler = createBrowserSessionFetchHandler({ service: browserSessionService });
  }

  return Object.freeze({
    browserSessionHandler,
    macroCandidateReviewHandler,
    macroHealthHandler: createMacroHealthFetchHandler({ browserSessionHandler }),
    safeSummary: Object.freeze({
      deploymentComposition: 'macro-production-v1',
      signedNovaSessions: true,
      googleBrowserIdentityVerification: Boolean(browserIdentityVerifier),
      googleBrowserSessionExchange: Boolean(browserSessionHandler),
      boundedMacroCandidateReview: true,
      sharedQuotaStore: true,
      sharedProviderCapacity: true,
      rpmPerKey: providerRuntime.safeSummary.rpmPerKey,
      providerKeyCount: providerRuntime.safeSummary.providerKeyCount,
    }),
  });
}

function createMacroHealthFetchHandler({ browserSessionHandler }) {
  return async function handle(request) {
    if (request?.method?.toUpperCase() !== 'GET') {
      return new Response(JSON.stringify({ status: 'METHOD_NOT_ALLOWED' }), {
        status: 405,
        headers: { 'content-type': 'application/json; charset=utf-8', allow: 'GET', 'cache-control': 'no-store' },
      });
    }
    return new Response(JSON.stringify({
      status: 'OK',
      browserSessionConfigured: Boolean(browserSessionHandler),
      candidateReviewConfigured: true,
    }), {
      status: 200,
      headers: {
        'content-type': 'application/json; charset=utf-8',
        'cache-control': 'no-store',
        'x-content-type-options': 'nosniff',
      },
    });
  };
}
