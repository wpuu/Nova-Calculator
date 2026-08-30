package org.solovyev.android.calculator.billing;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import org.solovyev.android.calculator.analytics.NovaProductAnalytics;
import org.solovyev.android.calculator.entitlement.EntitlementManager;
import org.solovyev.android.calculator.entitlement.EntitlementSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * App-level commercial coordinator. Product UI talks to this class, never directly to BillingClient.
 *
 * Play purchase observations are serialized through one background worker before they reach Nova's
 * server-verification endpoint. This prevents overlapping restore attempts from racing session-token
 * writes. The coordinator never grants an entitlement from a Play callback alone.
 */
public final class NovaBillingCoordinator implements NovaBillingObserver {

    private final boolean enabled;
    @Nullable
    private final NovaPlayBillingClient playBillingClient;
    @Nullable
    private final NovaBillingEntitlementClient entitlementClient;
    private final EntitlementManager entitlementManager;
    private final NovaProductAnalytics productAnalytics;
    private final ExecutorService billingWorker;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean explicitRestorePending = new AtomicBoolean();
    private final Object catalogLock = new Object();
    private final Map<String, ProductDetails> catalog = new HashMap<>();

    private volatile boolean billingReady;
    private volatile int lastBillingResponseCode;
    private volatile String lastBillingDebugMessage = "";

    public NovaBillingCoordinator(Context context,
                                  @Nullable NovaBillingEntitlementClient entitlementClient,
                                  EntitlementManager entitlementManager) {
        this(context, entitlementClient, entitlementManager,
                NovaProductAnalytics.fromSessionEndpoint(null, null, null));
    }

    public NovaBillingCoordinator(Context context,
                                  @Nullable NovaBillingEntitlementClient entitlementClient,
                                  EntitlementManager entitlementManager,
                                  NovaProductAnalytics productAnalytics) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (entitlementManager == null) {
            throw new IllegalArgumentException("entitlementManager must not be null");
        }
        if (productAnalytics == null) {
            throw new IllegalArgumentException("productAnalytics must not be null");
        }
        this.entitlementClient = entitlementClient;
        this.entitlementManager = entitlementManager;
        this.productAnalytics = productAnalytics;
        this.enabled = entitlementClient != null;
        this.playBillingClient = enabled
                ? new NovaPlayBillingClient(context.getApplicationContext(), this)
                : null;
        this.billingWorker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                final Thread thread = new Thread(runnable, "NovaBilling");
                thread.setDaemon(false);
                return thread;
            }
        });
    }

    /** Connects once and restores existing Google Play purchases without counting a user restore. */
    public void start() {
        entitlementManager.refresh();
        if (!enabled || !started.compareAndSet(false, true)) return;
        playBillingClient.connect();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isBillingReady() {
        return billingReady;
    }

    public EntitlementSnapshot getEntitlementSnapshot() {
        return entitlementManager.getSnapshot();
    }

    @Nullable
    public ProductDetails getProductDetails(String productId) {
        if (productId == null) return null;
        synchronized (catalogLock) {
            return catalog.get(productId);
        }
    }

    public Map<String, ProductDetails> getCatalogSnapshot() {
        synchronized (catalogLock) {
            return Collections.unmodifiableMap(new HashMap<>(catalog));
        }
    }

    @Nullable
    public String getProLifetimePriceLabel() {
        final ProductDetails details = getProductDetails(NovaBillingProducts.PRO_LIFETIME);
        if (details == null) return null;
        final List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                details.getOneTimePurchaseOfferDetailsList();
        if (offers == null || offers.isEmpty()) return null;
        return offers.get(0).getFormattedPrice();
    }

    @Nullable
    public String getAiPlusPriceLabel(String basePlanId) {
        final ProductDetails details = getProductDetails(NovaBillingProducts.AI_PLUS);
        if (details == null) return null;
        final ProductDetails.SubscriptionOfferDetails offer =
                findSubscriptionOffer(details, basePlanId);
        if (offer == null || offer.getPricingPhases() == null) return null;
        final List<ProductDetails.PricingPhase> phases =
                offer.getPricingPhases().getPricingPhaseList();
        if (phases == null || phases.isEmpty()) return null;
        // The last phase is the ongoing recurring price after any intro/trial phase.
        return phases.get(phases.size() - 1).getFormattedPrice();
    }

    public void restorePurchases() {
        if (!enabled || playBillingClient == null) return;
        explicitRestorePending.set(true);
        try {
            playBillingClient.refreshPurchases();
        } catch (RuntimeException error) {
            if (explicitRestorePending.getAndSet(false)) {
                productAnalytics.purchaseRestoreFailed();
            }
            throw error;
        }
    }

    public void buyProLifetime(Activity activity) {
        requireEnabled();
        playBillingClient.launchProLifetime(activity);
    }

    public void buyAiPlusMonthly(Activity activity) {
        requireEnabled();
        playBillingClient.launchAiPlus(
                activity, NovaBillingProducts.AI_PLUS_MONTHLY_BASE_PLAN);
    }

    public void buyAiPlusAnnual(Activity activity) {
        requireEnabled();
        playBillingClient.launchAiPlus(
                activity, NovaBillingProducts.AI_PLUS_ANNUAL_BASE_PLAN);
    }

    public int getLastBillingResponseCode() {
        return lastBillingResponseCode;
    }

    public String getLastBillingDebugMessage() {
        return lastBillingDebugMessage;
    }

    @Override
    public void onBillingReady() {
        billingReady = true;
        lastBillingDebugMessage = "";
    }

    @Override
    public void onBillingUnavailable(int responseCode, String debugMessage) {
        lastBillingResponseCode = responseCode;
        lastBillingDebugMessage = debugMessage == null ? "" : debugMessage;
        if (explicitRestorePending.getAndSet(false)) {
            productAnalytics.purchaseRestoreFailed();
        }
    }

    @Override
    public void onCatalogUpdated(List<ProductDetails> productDetails) {
        if (productDetails == null) return;
        synchronized (catalogLock) {
            for (ProductDetails details : productDetails) {
                if (details == null) continue;
                final String productId = details.getProductId();
                if (NovaBillingProducts.PRO_LIFETIME.equals(productId)
                        || NovaBillingProducts.AI_PLUS.equals(productId)) {
                    catalog.put(productId, details);
                }
            }
        }
    }

    @Override
    public void onPurchasesObserved(final List<Purchase> purchases, String source) {
        if (!enabled || entitlementClient == null || purchases == null) return;
        final boolean explicitRestore = NovaPlayBillingClient.SOURCE_RESTORE_COMPLETE.equals(source)
                && explicitRestorePending.getAndSet(false);
        billingWorker.execute(new Runnable() {
            @Override
            public void run() {
                final NovaBillingEntitlementResult result = entitlementClient.refresh(purchases);
                if (result != null
                        && result.getStatus() == NovaBillingEntitlementResult.Status.SUCCESS) {
                    // HttpNovaBillingEntitlementClient persisted the fresh server-issued session.
                    // Reload the app-facing cache only after that server-authoritative success.
                    entitlementManager.refresh();
                    if (explicitRestore) productAnalytics.purchaseRestoreSuccess();
                } else if (explicitRestore) {
                    productAnalytics.purchaseRestoreFailed();
                }
            }
        });
    }

    @Nullable
    private static ProductDetails.SubscriptionOfferDetails findSubscriptionOffer(
            ProductDetails details, String basePlanId) {
        final List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
        if (offers == null) return null;
        ProductDetails.SubscriptionOfferDetails fallback = null;
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            if (!basePlanId.equals(offer.getBasePlanId())) continue;
            if (offer.getOfferId() == null) return offer;
            if (fallback == null) fallback = offer;
        }
        return fallback;
    }

    private void requireEnabled() {
        if (!enabled || playBillingClient == null) {
            throw new IllegalStateException("Nova billing is not configured");
        }
    }
}
