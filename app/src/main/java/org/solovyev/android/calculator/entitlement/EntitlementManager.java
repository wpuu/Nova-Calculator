package org.solovyev.android.calculator.entitlement;

/**
 * Single app-facing access point for commercial rights.
 *
 * Product UI and feature code should depend on this interface, never directly on a billing SDK.
 */
public interface EntitlementManager {
    EntitlementSnapshot getSnapshot();

    boolean has(Entitlement entitlement);

    /** Reload the latest cached state from the configured source. */
    void refresh();
}
