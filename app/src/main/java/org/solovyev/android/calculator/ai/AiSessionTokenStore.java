package org.solovyev.android.calculator.ai;

/** App-private cache for a short-lived Nova session token. */
public interface AiSessionTokenStore {

    Snapshot load();

    void save(String token, long expiresAtEpochMs);

    void clear();

    final class Snapshot {
        private final String token;
        private final long expiresAtEpochMs;

        public Snapshot(String token, long expiresAtEpochMs) {
            this.token = token;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresAtEpochMs() {
            return expiresAtEpochMs;
        }
    }
}
