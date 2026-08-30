package org.solovyev.android.calculator.ai;

import com.google.common.io.BaseEncoding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable content binding for Nova anonymous-session Play Integrity requests. */
public final class PlayIntegrityRequestHash {

    private static final String DOMAIN = "nova-anonymous-session-v1";

    private PlayIntegrityRequestHash() {
    }

    public static String forAnonymousSession(String installationId) {
        if (installationId == null || installationId.trim().isEmpty() || installationId.trim().length() > 200) {
            throw new IllegalArgumentException("installationId is invalid");
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] value = (DOMAIN + "\n" + installationId.trim()).getBytes(StandardCharsets.UTF_8);
            return BaseEncoding.base64Url().omitPadding().encode(digest.digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
