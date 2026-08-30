import {
  AnonymousSessionService,
  createAnonymousSessionFetchHandler,
} from './anonymous-session.mjs';
import {
  BillingEntitlementService,
  createBillingEntitlementFetchHandler,
} from './billing-entitlement-service.mjs';
import { createNovaFetchHandler } from './http-handler.mjs';
import { NovaAiService } from './nova-ai-service.mjs';
import { DailyQuotaLedger } from './quota-ledger.mjs';
import { quotaLimitsForPriority, quotaPolicyFromEnv } from './quota-policy.mjs';
import { REQUEST_PRIORITY } from './provider-key-pool.mjs';
import { createGatewayRuntime } from './runtime.mjs';
import { NOVA_SESSION_KIND, sessionTokenServiceFromEnv } from './session-token.mjs';

/**
 * Composes Nova's deployable server core without binding it to Vercel, Express or a database.
 *
 * Required deployment adapters:
 * - quotaStore: shared atomic persistent store implementing reserve/commit/release.
 * - installationProofVerifier: validates app/device proof before a free anonymous session is issued.
 *
 * Production may additionally inject keyPoolFactory so provider-key RPM/cooldown state is shared
 * across horizontally scaled gateway instances instead of remaining process-local.
 */
export function createNovaGatewayApplication(options = {}) {
  const env = options.env ?? process.env;
  const now = typeof options.now === 'function' ? options.now : () => Date.now();

  const providerRuntime = createGatewayRuntime(env, {
    fetchImpl: options.fetchImpl,
    now,
    keyPoolFactory: options.keyPoolFactory,
  });
  const sessionTokens = sessionTokenServiceFromEnv(env, { now });
  const quotaPolicy = quotaPolicyFromEnv(env);
  const freeLimits = quotaLimitsForPriority(quotaPolicy, REQUEST_PRIORITY.FREE);
  const proLimits = quotaLimitsForPriority(quotaPolicy, REQUEST_PRIORITY.PRO);
  const aiPlusLimits = quotaLimitsForPriority(quotaPolicy, REQUEST_PRIORITY.AI_PLUS);
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

  let billingHandler = null;
  if (options.purchaseVerifier) {
    const billingService = new BillingEntitlementService({
      authVerifier: Object.freeze({
        verify(authorization) {
          return sessionTokens.verify(authorization);
        },
      }),
      purchaseVerifier: options.purchaseVerifier,
      issueEntitlementSession({ subjectId, entitlements }) {
        // Keep the same pseudonymous subject that was established by the Play-Integrity-gated
        // anonymous session. ACCOUNT here means "server-authenticated entitlement session"; no
        // email/password registration is required for V1 Play purchases.
        return sessionTokens.issue({
          kind: NOVA_SESSION_KIND.ACCOUNT,
          subjectId,
          entitlements,
          ttlMs: sessionTokens.accountTtlMs,
        });
      },
    });
    billingHandler = createBillingEntitlementFetchHandler({ service: billingService });
  }

  return Object.freeze({
    aiHandler: createNovaFetchHandler({ service: aiService }),
    anonymousSessionHandler: createAnonymousSessionFetchHandler({ service: anonymousSessionService }),
    billingHandler,
    safeSummary: Object.freeze({
      ...providerRuntime.safeSummary,
      freeDailyLimit: freeLimits.dailyLimit,
      freeRpmLimit: freeLimits.rpmLimit,
      proDailyLimit: proLimits.dailyLimit,
      proRpmLimit: proLimits.rpmLimit,
      aiPlusDailyLimit: aiPlusLimits.dailyLimit,
      aiPlusRpmLimit: aiPlusLimits.rpmLimit,
      signedNovaSessions: true,
      proofGatedAnonymousSessions: true,
      serverVerifiedPlayBilling: Boolean(options.purchaseVerifier),
    }),
  });
}
