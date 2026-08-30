package org.solovyev.android.calculator.billing;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Thin Google Play Billing 9 client for Nova's first commercial release.
 *
 * This class deliberately does not grant entitlements. It discovers products, launches Play's
 * purchase UI, and reports a complete INAPP + SUBS purchase snapshot to the application. A secure
 * Nova backend must verify every purchase token before the entitlement layer is changed.
 */
public final class NovaPlayBillingClient implements PurchasesUpdatedListener {

    public static final String SOURCE_RESTORE_COMPLETE = "restore-complete";

    private final BillingClient billingClient;
    private final NovaBillingObserver observer;
    private final NovaPurchaseSnapshotAccumulator purchaseAccumulator =
            new NovaPurchaseSnapshotAccumulator();
    private boolean connectionStarted;

    public NovaPlayBillingClient(Context context, NovaBillingObserver observer) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (observer == null) throw new IllegalArgumentException("observer must not be null");
        this.observer = observer;
        this.billingClient = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build())
                .enableAutoServiceReconnection()
                .build();
    }

    /** Starts the single app-level Play Billing connection. Safe to call repeatedly. */
    public synchronized void connect() {
        if (billingClient.isReady()) {
            observer.onBillingReady();
            refreshPurchases();
            refreshCatalog();
            return;
        }
        if (connectionStarted) return;
        connectionStarted = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                synchronized (NovaPlayBillingClient.this) {
                    connectionStarted = false;
                }
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    observer.onBillingReady();
                    refreshPurchases();
                    refreshCatalog();
                } else {
                    reportUnavailable(billingResult);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                synchronized (NovaPlayBillingClient.this) {
                    connectionStarted = false;
                }
                // Billing 9 automatic service reconnection is enabled. Do not manually spin a
                // reconnect loop here; the next API call can reconnect internally.
            }
        });
    }

    public void close() {
        synchronized (this) {
            connectionStarted = false;
        }
        billingClient.endConnection();
    }

    /** Refresh localized prices/offers for both launch products. */
    public void refreshCatalog() {
        querySingleProduct(NovaBillingProducts.PRO_LIFETIME, BillingClient.ProductType.INAPP,
                new ProductQueryCallback() {
                    @Override
                    public void onResult(@Nullable ProductDetails details) {
                        if (details != null) {
                            observer.onCatalogUpdated(Collections.singletonList(details));
                        }
                    }
                });
        querySingleProduct(NovaBillingProducts.AI_PLUS, BillingClient.ProductType.SUBS,
                new ProductQueryCallback() {
                    @Override
                    public void onResult(@Nullable ProductDetails details) {
                        if (details != null) {
                            observer.onCatalogUpdated(Collections.singletonList(details));
                        }
                    }
                });
    }

    /**
     * Restores the complete purchase state owned by the current Play account.
     *
     * Google Play queries INAPP and SUBS separately. Nova only emits after both queries succeed;
     * a failed half emits nothing so callers retain the last still-valid server-signed session.
     */
    public void refreshPurchases() {
        final long generation = purchaseAccumulator.beginRefresh();
        queryPurchases(BillingClient.ProductType.INAPP, false, true, generation);
        queryPurchases(BillingClient.ProductType.SUBS, true, false, generation);
    }

    public void launchProLifetime(final Activity activity) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        querySingleProduct(NovaBillingProducts.PRO_LIFETIME, BillingClient.ProductType.INAPP,
                new ProductQueryCallback() {
                    @Override
                    public void onResult(@Nullable ProductDetails details) {
                        if (details == null) return;
                        BillingFlowParams.ProductDetailsParams.Builder productBuilder =
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(details);
                        String offerToken = firstOneTimeOfferToken(details);
                        if (offerToken != null && !offerToken.isEmpty()) {
                            productBuilder.setOfferToken(offerToken);
                        }
                        launch(activity, productBuilder.build());
                    }
                });
    }

    public void launchAiPlus(final Activity activity, final String basePlanId) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        if (!NovaBillingProducts.AI_PLUS_MONTHLY_BASE_PLAN.equals(basePlanId)
                && !NovaBillingProducts.AI_PLUS_ANNUAL_BASE_PLAN.equals(basePlanId)) {
            throw new IllegalArgumentException("Unknown AI Plus base plan: " + basePlanId);
        }
        querySingleProduct(NovaBillingProducts.AI_PLUS, BillingClient.ProductType.SUBS,
                new ProductQueryCallback() {
                    @Override
                    public void onResult(@Nullable ProductDetails details) {
                        if (details == null) return;
                        ProductDetails.SubscriptionOfferDetails offer =
                                findSubscriptionOffer(details, basePlanId);
                        if (offer == null) {
                            observer.onBillingUnavailable(
                                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                                    "No eligible " + basePlanId + " AI Plus offer");
                            return;
                        }
                        BillingFlowParams.ProductDetailsParams params =
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(details)
                                        .setOfferToken(offer.getOfferToken())
                                        .build();
                        launch(activity, params);
                    }
                });
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        int code = billingResult.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Purchase callbacks contain only the transaction(s) that just changed. Re-query both
            // product types so the server always receives one complete authoritative snapshot.
            refreshPurchases();
            return;
        }
        if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refreshPurchases();
            return;
        }
        if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            return;
        }
        reportUnavailable(billingResult);
    }

    private void queryPurchases(String productType,
                                boolean includeSuspended,
                                boolean inAppResult,
                                long generation) {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .includeSuspendedSubscriptions(includeSuspended)
                .build();
        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                final List<Purchase> complete =
                        purchaseAccumulator.accept(generation, inAppResult, purchases);
                if (complete != null) {
                    observer.onPurchasesObserved(complete, SOURCE_RESTORE_COMPLETE);
                }
            } else {
                purchaseAccumulator.fail(generation);
                reportUnavailable(billingResult);
            }
        });
    }

    private void querySingleProduct(String productId, String productType,
                                    final ProductQueryCallback callback) {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();
        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult billingResult,
                                                 QueryProductDetailsResult result) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    reportUnavailable(billingResult);
                    callback.onResult(null);
                    return;
                }
                List<ProductDetails> details = result.getProductDetailsList();
                callback.onResult(details == null || details.isEmpty() ? null : details.get(0));
            }
        });
    }

    private void launch(Activity activity, BillingFlowParams.ProductDetailsParams productParams) {
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            reportUnavailable(result);
        }
    }

    @Nullable
    private static String firstOneTimeOfferToken(ProductDetails details) {
        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                details.getOneTimePurchaseOfferDetailsList();
        if (offers == null || offers.isEmpty()) return null;
        return offers.get(0).getOfferToken();
    }

    @Nullable
    static ProductDetails.SubscriptionOfferDetails findSubscriptionOffer(
            ProductDetails details, String basePlanId) {
        List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
        if (offers == null) return null;

        ProductDetails.SubscriptionOfferDetails fallback = null;
        for (ProductDetails.SubscriptionOfferDetails offer : offers) {
            if (!basePlanId.equals(offer.getBasePlanId())) continue;
            // Prefer the regular base plan. Eligible introductory/promotional offers can be
            // selected deliberately later instead of silently changing commercial behavior.
            if (offer.getOfferId() == null) return offer;
            if (fallback == null) fallback = offer;
        }
        return fallback;
    }

    private void reportUnavailable(BillingResult billingResult) {
        observer.onBillingUnavailable(
                billingResult.getResponseCode(), billingResult.getDebugMessage());
    }

    private interface ProductQueryCallback {
        void onResult(@Nullable ProductDetails details);
    }
}
