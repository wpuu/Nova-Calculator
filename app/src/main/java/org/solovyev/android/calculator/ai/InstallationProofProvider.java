package org.solovyev.android.calculator.ai;

/**
 * Supplies an app/device installation proof used only to obtain a Nova anonymous session.
 *
 * Implementations must never return upstream provider credentials. A production implementation
 * may use a platform integrity service; the rest of Nova depends only on this provider-neutral seam.
 */
public interface InstallationProofProvider {

    /** Returns true only when this build can obtain a production-acceptable installation proof. */
    boolean isAvailable();

    /**
     * Returns a fresh proof bound to the supplied installation id, or {@code null} on failure.
     * This may perform blocking work and must only be called from Nova's background executor.
     */
    String getProof(String installationId);
}
