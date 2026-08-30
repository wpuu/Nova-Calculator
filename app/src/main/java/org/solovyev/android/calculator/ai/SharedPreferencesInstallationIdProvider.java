package org.solovyev.android.calculator.ai;

import android.content.SharedPreferences;

import java.util.UUID;

/** Persists a random app-local identifier; it is intentionally unrelated to device hardware ids. */
public final class SharedPreferencesInstallationIdProvider implements InstallationIdProvider {

    private static final String KEY_INSTALLATION_ID = "installation_id";
    private final SharedPreferences preferences;

    public SharedPreferencesInstallationIdProvider(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences must not be null");
        this.preferences = preferences;
    }

    @Override
    public synchronized String getInstallationId() {
        final String existing = normalize(preferences.getString(KEY_INSTALLATION_ID, null));
        if (existing != null) return existing;

        final String created = UUID.randomUUID().toString();
        if (!preferences.edit().putString(KEY_INSTALLATION_ID, created).commit()) {
            throw new IllegalStateException("failed to persist Nova installation id");
        }
        return created;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        final String text = value.trim();
        if (text.length() < 16 || text.length() > 200
                || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return null;
        }
        return text;
    }
}
