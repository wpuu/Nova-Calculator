package org.solovyev.android.calculator.billing;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.List;

/**
 * UI/application boundary for Play Billing events.
 *
 * Purchase callbacks are observations only. Callers must send PURCHASED purchase tokens to
 * Nova's backend and wait for server verification before granting PRO_LIFETIME or AI_PLUS.
 */
public interface NovaBillingObserver {

    void onBillingReady();

    void onBillingUnavailable(int responseCode, String debugMessage);

    void onCatalogUpdated(List<ProductDetails> productDetails);

    void onPurchasesObserved(List<Purchase> purchases, String source);
}
