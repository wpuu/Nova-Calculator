package org.solovyev.android.calculator.autoclicker;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists deterministic AutoTap setups without coupling the click engine to billing/UI code.
 *
 * A profile is intentionally limited to the settings the runtime already understands. Loading a
 * profile therefore cannot introduce a new automation capability: it only restores interval,
 * duration and normalized overlay positions previously chosen by the user.
 */
public final class AutoClickerProfileStore {

    static final String KEY_PROFILES = "auto_clicker_profiles_v1";
    static final String KEY_INTERVAL = "auto_clicker_interval";
    static final String KEY_DURATION = "auto_clicker_duration";
    static final String KEY_POSITIONS = "auto_clicker_positions";
    static final String KEY_FLOATING_POSITION = "auto_clicker_floating_position";

    public static final int FREE_PROFILE_LIMIT = 1;
    public static final int PRO_PROFILE_LIMIT = 20;

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_NAME_LENGTH = 40;

    private final SharedPreferences preferences;

    public AutoClickerProfileStore(@NonNull SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @NonNull
    public List<Profile> list() {
        final String raw = preferences.getString(KEY_PROFILES, "");
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        try {
            final JSONObject root = new JSONObject(raw);
            if (root.optInt("v", -1) != FORMAT_VERSION) return Collections.emptyList();
            final JSONArray array = root.optJSONArray("profiles");
            if (array == null) return Collections.emptyList();
            final List<Profile> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                final JSONObject object = array.optJSONObject(i);
                final Profile profile = object == null ? null : Profile.fromJson(object);
                if (profile != null) result.add(profile);
            }
            return Collections.unmodifiableList(result);
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
    }

    @NonNull
    public Profile saveCurrent(@NonNull String requestedName, int profileLimit)
            throws ProfileLimitReachedException {
        final String name = normalizeName(requestedName);
        final int safeLimit = Math.max(1, Math.min(PRO_PROFILE_LIMIT, profileLimit));
        final List<Profile> profiles = new ArrayList<>(list());

        int existingIndex = -1;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name.equalsIgnoreCase(name)) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex < 0 && profiles.size() >= safeLimit) {
            throw new ProfileLimitReachedException(safeLimit);
        }

        final Profile profile = new Profile(
                name,
                preferences.getString(KEY_INTERVAL, "40"),
                preferences.getString(KEY_DURATION, "60"),
                preferences.getString(KEY_POSITIONS, ""),
                preferences.getString(KEY_FLOATING_POSITION, ""),
                System.currentTimeMillis());

        if (existingIndex >= 0) {
            profiles.set(existingIndex, profile);
        } else {
            profiles.add(profile);
        }
        persist(profiles);
        return profile;
    }

    public boolean apply(@NonNull String name) {
        final Profile profile = find(name);
        if (profile == null) return false;
        preferences.edit()
                .putString(KEY_INTERVAL, profile.interval)
                .putString(KEY_DURATION, profile.duration)
                .putString(KEY_POSITIONS, profile.positions)
                .putString(KEY_FLOATING_POSITION, profile.floatingPosition)
                .apply();
        return true;
    }

    public boolean delete(@NonNull String name) {
        final List<Profile> profiles = new ArrayList<>(list());
        boolean removed = false;
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).name.equalsIgnoreCase(name.trim())) {
                profiles.remove(i);
                removed = true;
            }
        }
        if (removed) persist(profiles);
        return removed;
    }

    @Nullable
    public Profile find(@NonNull String name) {
        for (Profile profile : list()) {
            if (profile.name.equalsIgnoreCase(name.trim())) return profile;
        }
        return null;
    }

    private void persist(@NonNull List<Profile> profiles) {
        try {
            final JSONObject root = new JSONObject();
            root.put("v", FORMAT_VERSION);
            final JSONArray array = new JSONArray();
            for (Profile profile : profiles) array.put(profile.toJson());
            root.put("profiles", array);
            preferences.edit().putString(KEY_PROFILES, root.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to serialize AutoTap profiles", e);
        }
    }

    @NonNull
    private static String normalizeName(@NonNull String raw) {
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) name = "Setup";
        if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH).trim();
        return name;
    }

    public static final class Profile {
        public final String name;
        public final String interval;
        public final String duration;
        public final String positions;
        public final String floatingPosition;
        public final long updatedAtEpochMs;

        Profile(String name,
                @Nullable String interval,
                @Nullable String duration,
                @Nullable String positions,
                @Nullable String floatingPosition,
                long updatedAtEpochMs) {
            this.name = name;
            this.interval = interval == null ? "40" : interval;
            this.duration = duration == null ? "60" : duration;
            this.positions = positions == null ? "" : positions;
            this.floatingPosition = floatingPosition == null ? "" : floatingPosition;
            this.updatedAtEpochMs = updatedAtEpochMs;
        }

        JSONObject toJson() throws JSONException {
            final JSONObject object = new JSONObject();
            object.put("name", name);
            object.put("interval", interval);
            object.put("duration", duration);
            object.put("positions", positions);
            object.put("floatingPosition", floatingPosition);
            object.put("updatedAt", updatedAtEpochMs);
            return object;
        }

        @Nullable
        static Profile fromJson(@NonNull JSONObject object) {
            final String name = object.optString("name", "").trim();
            if (name.isEmpty()) return null;
            return new Profile(
                    name,
                    object.optString("interval", "40"),
                    object.optString("duration", "60"),
                    object.optString("positions", ""),
                    object.optString("floatingPosition", ""),
                    object.optLong("updatedAt", 0L));
        }
    }

    public static final class ProfileLimitReachedException extends Exception {
        public final int limit;

        ProfileLimitReachedException(int limit) {
            super("AutoTap profile limit reached: " + limit);
            this.limit = limit;
        }
    }
}
