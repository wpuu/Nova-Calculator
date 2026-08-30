package org.solovyev.android.calculator.billing;

import com.android.billingclient.api.Purchase;

import java.util.List;

/** Exchanges the current Google Play purchase snapshot for server-verified Nova entitlements. */
public interface NovaBillingEntitlementClient {
    NovaBillingEntitlementResult refresh(List<Purchase> purchases);
}
