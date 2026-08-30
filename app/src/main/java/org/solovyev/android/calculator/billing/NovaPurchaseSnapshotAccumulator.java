package org.solovyev.android.calculator.billing;

import androidx.annotation.Nullable;

import com.android.billingclient.api.Purchase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Joins Google Play's separate INAPP and SUBS restore queries into one authoritative snapshot.
 *
 * Billing returns those product types asynchronously. Nova must never treat either half as the
 * complete account state because doing so can temporarily remove the other entitlement. A failed
 * or superseded refresh emits nothing, so the app can keep its last still-valid signed session.
 */
final class NovaPurchaseSnapshotAccumulator {

    private long generation;
    @Nullable
    private List<Purchase> inApp;
    @Nullable
    private List<Purchase> subscriptions;
    private boolean emitted;

    synchronized long beginRefresh() {
        generation++;
        inApp = null;
        subscriptions = null;
        emitted = false;
        return generation;
    }

    @Nullable
    synchronized List<Purchase> accept(long candidateGeneration,
                                       boolean inAppResult,
                                       @Nullable List<Purchase> purchases) {
        if (candidateGeneration != generation || emitted) return null;
        final List<Purchase> safe = purchases == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(purchases));
        if (inAppResult) {
            inApp = safe;
        } else {
            subscriptions = safe;
        }
        if (inApp == null || subscriptions == null) return null;

        final ArrayList<Purchase> merged = new ArrayList<>(inApp.size() + subscriptions.size());
        merged.addAll(inApp);
        merged.addAll(subscriptions);
        emitted = true;
        return Collections.unmodifiableList(merged);
    }

    synchronized void fail(long candidateGeneration) {
        if (candidateGeneration != generation) return;
        // Bump the generation so a late callback from the other half cannot emit a partial result.
        generation++;
        inApp = null;
        subscriptions = null;
        emitted = false;
    }
}
