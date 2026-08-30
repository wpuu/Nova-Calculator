package org.solovyev.android.calculator.entitlement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable snapshot of the commercial rights currently known by the app. */
public final class EntitlementSnapshot {

    private final EnumSet<Entitlement> entitlements;
    private final String source;
    private final long refreshedAtEpochMs;

    private EntitlementSnapshot(EnumSet<Entitlement> entitlements,
                                String source,
                                long refreshedAtEpochMs) {
        this.entitlements = entitlements.isEmpty()
                ? EnumSet.noneOf(Entitlement.class)
                : EnumSet.copyOf(entitlements);
        this.source = source == null ? "unknown" : source;
        this.refreshedAtEpochMs = refreshedAtEpochMs;
    }

    public static EntitlementSnapshot free(String source, long refreshedAtEpochMs) {
        return new EntitlementSnapshot(
                EnumSet.noneOf(Entitlement.class), source, refreshedAtEpochMs);
    }

    public static EntitlementSnapshot of(EnumSet<Entitlement> entitlements,
                                         String source,
                                         long refreshedAtEpochMs) {
        if (entitlements == null) {
            throw new IllegalArgumentException("entitlements must not be null");
        }
        return new EntitlementSnapshot(entitlements, source, refreshedAtEpochMs);
    }

    public boolean has(Entitlement entitlement) {
        return entitlement != null && entitlements.contains(entitlement);
    }

    public boolean hasProLifetime() {
        return has(Entitlement.PRO_LIFETIME);
    }

    public boolean hasAiPlus() {
        return has(Entitlement.AI_PLUS);
    }

    public Set<Entitlement> getEntitlements() {
        if (entitlements.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(entitlements));
    }

    public String getSource() {
        return source;
    }

    public long getRefreshedAtEpochMs() {
        return refreshedAtEpochMs;
    }
}
