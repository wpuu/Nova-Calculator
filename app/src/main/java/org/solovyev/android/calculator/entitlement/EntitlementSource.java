package org.solovyev.android.calculator.entitlement;

/**
 * Supplies a cached entitlement snapshot.
 *
 * A future Play Billing / account implementation may keep this cache fresh asynchronously;
 * the rest of the app stays independent of the payment SDK.
 */
public interface EntitlementSource {
    EntitlementSnapshot load();
}
