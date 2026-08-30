package org.solovyev.android.calculator.ai;

/** Default commercial-build placeholder until a real installation proof adapter is wired. */
public final class DisabledInstallationProofProvider implements InstallationProofProvider {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getProof(String installationId) {
        return null;
    }
}
