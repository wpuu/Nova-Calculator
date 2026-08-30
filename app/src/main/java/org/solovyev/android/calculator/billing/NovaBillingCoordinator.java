package org.solovyev.android.calculator.billing;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

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
    private final ExecutorService billingWorker;
    private final AtomicBoolean started = new AtomicBoolean();
    private final Object catalogLock = new Object();
    private final Map<String, ProductDetails> catalog = new HashMap<>();

    private volatile boolean billingReady;
    private volatile int lastBillingResponseCode;
    private volatile String lastBillingDebugMessage = "";

    public NovaBillingCoordinator(Context context,
                                  @Nullable NovaBillingEntitlementClient entitlementClient,
                                  EntitlementManager entitlementManager) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (entitlementManager == null) {
            throw new IllegalArgumentException("entitlementManager must not be null");
        }
        this.entitlementClient = entitlementClient;
        this.entitlementManager = entitlementManager;
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

    /** Connects once and restores existing Google Play purchases. */
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

    public void restorePurchases() {
        if (!enabled || playBillingClient == null) return;
        playBillingClient.refreshPurchases();
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
        billingWorker.execute(new Runnable() {
            @Override
            public void run() {
                final NovaBillingEntitlementResult result = entitlementClient.refresh(purchases);
                if (result != null
                        && result.getStatus() == NovaBillingEntitlementResult.Status.SUCCESS) {
                    // HttpNovaBillingEntitlementClient persisted the fresh server-issued session.
                    // Reload the app-facing cache only after that server-authoritative success.
                    entitlementManager.refresh();
                }
            }
        });
    }

    private void requireEnabled() {
        if (!enabled || playBillingClient == null) {
            throw new IllegalStateException("Nova billing is not configured");
        }
    }
}
