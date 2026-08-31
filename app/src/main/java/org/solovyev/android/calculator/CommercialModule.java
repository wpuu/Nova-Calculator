package org.solovyev.android.calculator;

import android.app.Application;

import org.solovyev.android.calculator.ai.AiSessionTokenProvider;
import org.solovyev.android.calculator.ai.AiSessionTokenStore;
import org.solovyev.android.calculator.analytics.NovaProductAnalytics;
import org.solovyev.android.calculator.billing.HttpNovaBillingEntitlementClient;
import org.solovyev.android.calculator.billing.NovaBillingCoordinator;
import org.solovyev.android.calculator.billing.NovaBillingEndpoint;
import org.solovyev.android.calculator.billing.NovaBillingEntitlementClient;
import org.solovyev.android.calculator.entitlement.DefaultEntitlementManager;
import org.solovyev.android.calculator.entitlement.EntitlementManager;
import org.solovyev.android.calculator.entitlement.NovaSessionEntitlementSource;

import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/** Commercial-only dependency graph kept separate from the legacy calculator core. */
@Module
public final class CommercialModule {

    @Provides
    @Singleton
    EntitlementManager provideEntitlementManager(AiSessionTokenStore tokenStore) {
        return new DefaultEntitlementManager(new NovaSessionEntitlementSource(tokenStore));
    }

    @Provides
    @Singleton
    NovaProductAnalytics provideNovaProductAnalytics(
            AiSessionTokenProvider sessionTokenProvider,
            @Named(AppModule.THREAD_BACKGROUND) Executor background) {
        // Product events live on the same Nova-owned origin as the anonymous-session route.
        // A missing/invalid session endpoint makes analytics a no-op rather than weakening auth.
        return NovaProductAnalytics.fromSessionEndpoint(
                BuildConfig.NOVA_ANONYMOUS_SESSION_URL,
                sessionTokenProvider,
                background);
    }

    @Provides
    @Singleton
    NovaBillingCoordinator provideNovaBillingCoordinator(
            Application application,
            AiSessionTokenProvider sessionTokenProvider,
            AiSessionTokenStore tokenStore,
            EntitlementManager entitlementManager,
            NovaProductAnalytics productAnalytics) {
        final String url = BuildConfig.NOVA_BILLING_URL == null
                ? ""
                : BuildConfig.NOVA_BILLING_URL.trim();
        if (url.isEmpty()) {
            // Development and source builds stay safely Free until the Nova-owned billing route
            // is explicitly injected. Google Play purchase UI is not started in this state.
            return new NovaBillingCoordinator(
                    application, null, entitlementManager, productAnalytics);
        }
        try {
            final NovaBillingEntitlementClient entitlementClient =
                    new HttpNovaBillingEntitlementClient(
                            new NovaBillingEndpoint(url),
                            sessionTokenProvider,
                            tokenStore);
            return new NovaBillingCoordinator(
                    application, entitlementClient, entitlementManager, productAnalytics);
        } catch (RuntimeException e) {
            // Invalid commercial endpoint configuration must fail closed rather than exposing a
            // purchase UI that cannot verify or restore the resulting entitlement.
            return new NovaBillingCoordinator(
                    application, null, entitlementManager, productAnalytics);
        }
    }
}
