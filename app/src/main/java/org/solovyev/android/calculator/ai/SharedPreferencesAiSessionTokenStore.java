package org.solovyev.android.calculator.ai;

import android.content.SharedPreferences;

/** App-private persistence for short-lived Nova session tokens. */
public final class SharedPreferencesAiSessionTokenStore implements AiSessionTokenStore {

    private static final String KEY_TOKEN = "token";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private final SharedPreferences preferences;

    public SharedPreferencesAiSessionTokenStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences must not be null");
        this.preferences = preferences;
    }

    @Override
    public Snapshot load() {
        return new Snapshot(
                preferences.getString(KEY_TOKEN, null),
                preferences.getLong(KEY_EXPIRES_AT, 0L));
    }

    @Override
    public void save(String token, long expiresAtEpochMs) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (expiresAtEpochMs <= 0L) {
            throw new IllegalArgumentException("expiresAtEpochMs must be positive");
        }
        if (!preferences.edit()
                .putString(KEY_TOKEN, token.trim())
                .putLong(KEY_EXPIRES_AT, expiresAtEpochMs)
                .commit()) {
            throw new IllegalStateException("failed to persist Nova session token");
        }
    }

    @Override
    public void clear() {
        if (!preferences.edit().remove(KEY_TOKEN).remove(KEY_EXPIRES_AT).commit()) {
            throw new IllegalStateException("failed to clear Nova session token");
        }
    }
}
