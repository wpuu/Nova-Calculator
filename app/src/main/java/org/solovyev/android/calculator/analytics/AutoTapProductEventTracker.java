package org.solovyev.android.calculator.analytics;

import android.content.SharedPreferences;

import org.solovyev.android.calculator.Preferences;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Observes the existing authoritative AutoTap SharedPreferences state instead of adding analytics
 * branches to the AccessibilityService gesture engine.
 *
 * `auto_clicker_enabled=true` is written only after the service has actually attached both target
 * circles and the floating status view. That makes it a safe source for overlay-ready activation.
 */
@Singleton
public final class AutoTapProductEventTracker
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String KEY_ACCESSIBILITY_COMPLETED_RECORDED =
            "nova.analytics.autotap_accessibility_completed_v1";
    private static final String KEY_EVER_OVERLAY_READY =
            "nova.analytics.autotap_ever_overlay_ready_v1";
    private static final String KEY_SECOND_SESSION_RECORDED =
            "nova.analytics.autotap_second_session_v1";

    private final SharedPreferences preferences;
    private final NovaProductAnalytics analytics;
    private boolean started;
    private boolean observedReadyThisProcess;

    @Inject
    public AutoTapProductEventTracker(SharedPreferences preferences,
                                      NovaProductAnalytics analytics) {
        this.preferences = preferences;
        this.analytics = analytics;
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        preferences.registerOnSharedPreferenceChangeListener(this);
        if (Preferences.AutoClicker.enabled.getPreference(preferences)) {
            onOverlayReady();
        }
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (Preferences.AutoClicker.enabled.getKey().equals(key)) {
            if (Preferences.AutoClicker.enabled.getPreference(sharedPreferences)) {
                onOverlayReady();
            }
            return;
        }
        if (Preferences.AutoClicker.lastFailure.getKey().equals(key)) {
            final int failureCode = parseFailure(
                    Preferences.AutoClicker.lastFailure.getPreference(sharedPreferences));
            if (failureCode > 0) analytics.autoTapRunFailed(failureCode);
        }
    }

    private synchronized void onOverlayReady() {
        if (!preferences.getBoolean(KEY_ACCESSIBILITY_COMPLETED_RECORDED, false)) {
            preferences.edit().putBoolean(KEY_ACCESSIBILITY_COMPLETED_RECORDED, true).apply();
            analytics.autoTapAccessibilityCompleted();
        }

        analytics.autoTapOverlayReady();

        if (!observedReadyThisProcess) {
            observedReadyThisProcess = true;
            final boolean existedBeforeProcess = preferences.getBoolean(KEY_EVER_OVERLAY_READY, false);
            if (existedBeforeProcess
                    && !preferences.getBoolean(KEY_SECOND_SESSION_RECORDED, false)) {
                preferences.edit().putBoolean(KEY_SECOND_SESSION_RECORDED, true).apply();
                analytics.autoTapSecondSession();
            }
            if (!existedBeforeProcess) {
                preferences.edit().putBoolean(KEY_EVER_OVERLAY_READY, true).apply();
            }
        }
    }

    private static int parseFailure(String value) {
        try {
            final int code = Integer.parseInt(value == null ? "" : value.trim());
            return code >= 1 && code <= 99 ? code : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
