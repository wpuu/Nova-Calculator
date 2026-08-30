package org.solovyev.android.calculator.ai;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.StandardIntegrityException;
import com.google.android.play.core.integrity.StandardIntegrityManager;
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Production installation-proof adapter backed by the Play Integrity Standard API.
 *
 * Standard integrity requests support Nova's existing Android 5.0+ baseline. The Cloud project
 * number is public configuration, not a credential. Google service-account credentials and token
 * decoding remain server-side only.
 *
 * This provider is called only from Nova's background executor. The prepared token provider is
 * cached and refreshed once if Google reports INTEGRITY_TOKEN_PROVIDER_INVALID.
 */
public final class PlayIntegrityInstallationProofProvider implements InstallationProofProvider {

    private static final long PREPARE_TIMEOUT_SECONDS = 60L;
    private static final long REQUEST_TIMEOUT_SECONDS = 20L;

    private final long cloudProjectNumber;
    private final StandardIntegrityManager manager;
    private StandardIntegrityManager.StandardIntegrityTokenProvider tokenProvider;

    public PlayIntegrityInstallationProofProvider(Context context, long cloudProjectNumber) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (cloudProjectNumber <= 0L) {
            throw new IllegalArgumentException("cloudProjectNumber must be positive");
        }
        this.cloudProjectNumber = cloudProjectNumber;
        this.manager = IntegrityManagerFactory.createStandard(context.getApplicationContext());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public synchronized String getProof(String installationId) {
        if (!isAvailable()) return null;
        final String requestHash;
        try {
            requestHash = PlayIntegrityRequestHash.forAnonymousSession(installationId);
        } catch (RuntimeException e) {
            return null;
        }

        try {
            return requestProof(requestHash, false);
        } catch (ProviderInvalidException ignored) {
            tokenProvider = null;
            try {
                return requestProof(requestHash, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String requestProof(String requestHash, boolean afterRefresh)
            throws ExecutionException, InterruptedException, TimeoutException, ProviderInvalidException {
        final StandardIntegrityManager.StandardIntegrityTokenProvider provider = ensureTokenProvider();
        try {
            final StandardIntegrityManager.StandardIntegrityToken response = Tasks.await(
                    provider.request(StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                            .setRequestHash(requestHash)
                            .build()),
                    REQUEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
            if (response == null || response.token() == null || response.token().trim().isEmpty()) {
                return null;
            }
            return response.token().trim();
        } catch (ExecutionException e) {
            if (!afterRefresh && isProviderInvalid(e.getCause())) {
                throw new ProviderInvalidException();
            }
            throw e;
        }
    }

    private StandardIntegrityManager.StandardIntegrityTokenProvider ensureTokenProvider()
            throws ExecutionException, InterruptedException, TimeoutException {
        if (tokenProvider != null) return tokenProvider;
        tokenProvider = Tasks.await(
                manager.prepareIntegrityToken(
                        StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                                .setCloudProjectNumber(cloudProjectNumber)
                                .build()),
                PREPARE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
        return tokenProvider;
    }

    private static boolean isProviderInvalid(Throwable error) {
        return error instanceof StandardIntegrityException
                && ((StandardIntegrityException) error).getErrorCode()
                == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID;
    }

    private static final class ProviderInvalidException extends Exception {
    }
}
