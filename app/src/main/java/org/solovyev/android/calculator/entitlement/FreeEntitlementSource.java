package org.solovyev.android.calculator.entitlement;

/** Baseline source used until a real commercial entitlement source is connected. */
public final class FreeEntitlementSource implements EntitlementSource {
    @Override
    public EntitlementSnapshot load() {
        return EntitlementSnapshot.free("free-default", System.currentTimeMillis());
    }
}
