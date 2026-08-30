package org.solovyev.android.calculator.billing;

import androidx.annotation.Nullable;

import org.solovyev.android.calculator.entitlement.EntitlementSnapshot;

/** Result of refreshing server-verified Google Play entitlements. */
public final class NovaBillingEntitlementResult {

    public enum Status {
        SUCCESS,
        AUTH_REQUIRED,
        INVALID_REQUEST,
        TEMPORARILY_UNAVAILABLE
    }

    private final Status status;
    @Nullable
    private final EntitlementSnapshot snapshot;

    private NovaBillingEntitlementResult(Status status, @Nullable EntitlementSnapshot snapshot) {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        this.status = status;
        this.snapshot = snapshot;
    }

    public static NovaBillingEntitlementResult success(EntitlementSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        return new NovaBillingEntitlementResult(Status.SUCCESS, snapshot);
    }

    public static NovaBillingEntitlementResult failure(Status status) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("SUCCESS requires a snapshot");
        }
        return new NovaBillingEntitlementResult(status, null);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public EntitlementSnapshot getSnapshot() {
        return snapshot;
    }
}
