package org.solovyev.android.calculator.entitlement;

/** Default thread-safe entitlement state holder used by product code. */
public final class DefaultEntitlementManager implements EntitlementManager {

    private final EntitlementSource source;
    private volatile EntitlementSnapshot snapshot;

    public DefaultEntitlementManager() {
        this(new FreeEntitlementSource());
    }

    public DefaultEntitlementManager(EntitlementSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        this.source = source;
        this.snapshot = safeLoad(source);
    }

    @Override
    public EntitlementSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public boolean has(Entitlement entitlement) {
        return snapshot.has(entitlement);
    }

    @Override
    public void refresh() {
        snapshot = safeLoad(source);
    }

    private static EntitlementSnapshot safeLoad(EntitlementSource source) {
        EntitlementSnapshot loaded = source.load();
        if (loaded != null) {
            return loaded;
        }
        return EntitlementSnapshot.free("source-null-fallback", System.currentTimeMillis());
    }
}
