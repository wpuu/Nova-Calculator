import {
  AnonymousSessionService,
  createAnonymousSessionFetchHandler,
} from './anonymous-session.mjs';
import { createNovaFetchHandler } from './http-handler.mjs';
import { NovaAiService } from './nova-ai-service.mjs';
import { DailyQuotaLedger } from './quota-ledger.mjs';
import { quotaPolicyFromEnv } from './quota-policy.mjs';
import { createGatewayRuntime } from './runtime.mjs';
import { sessionTokenServiceFromEnv } from './session-token.mjs';

/**
 * Composes Nova's deployable server core without binding it to Vercel, Express or a database.
 *
 * Required deployment adapters:
 * - quotaStore: shared atomic persistent store implementing reserve/commit/release.
 * - installationProofVerifier: validates app/device proof before a free anonymous session is issued.
 */
export function createNovaGatewayApplication(options = {}) {
  const env = options.env ?? process.env;
  const now = typeof options.now === 'function' ? options.now : () => Date.now();

  const providerRuntime = createGatewayRuntime(env, {
    fetchImpl: options.fetchImpl,
    now,
  });
  const sessionTokens = sessionTokenServiceFromEnv(env, { now });
  const quotaPolicy = quotaPolicyFromEnv(env);
  const quotaLedger = new DailyQuotaLedger({
    store: options.quotaStore,
    policy: quotaPolicy,
    now,
    newReservationId: options.newReservationId,
  });
  const aiService = new NovaAiService({
    authVerifier: Object.freeze({
      verify(authorization) {
        return sessionTokens.verify(authorization);
      },
    }),
    quotaLedger,
    dispatcher: providerRuntime.dispatcher,
  });
  const anonymousSessionService = new AnonymousSessionService({
    tokenService: sessionTokens,
    installationProofVerifier: options.installationProofVerifier,
  });

  return Object.freeze({
    aiHandler: createNovaFetchHandler({ service: aiService }),
    anonymousSessionHandler: createAnonymousSessionFetchHandler({ service: anonymousSessionService }),
    safeSummary: Object.freeze({
      ...providerRuntime.safeSummary,
      freeDailyLimit: quotaPolicy.FREE.dailyLimit,
      freeRpmLimit: quotaPolicy.FREE.rpmLimit,
      proDailyLimit: quotaPolicy.PRO.dailyLimit,
      proRpmLimit: quotaPolicy.PRO.rpmLimit,
      aiPlusDailyLimit: quotaPolicy.AI_PLUS.dailyLimit,
      aiPlusRpmLimit: quotaPolicy.AI_PLUS.rpmLimit,
      signedNovaSessions: true,
      proofGatedAnonymousSessions: true,
    }),
  });
}
