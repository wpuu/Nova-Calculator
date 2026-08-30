package org.solovyev.android.calculator.autoclicker;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import org.solovyev.android.calculator.CalculatorApplication;
import org.solovyev.android.calculator.R;

/**
 * Prominent in-app disclosure gate for Nova AutoTap's AccessibilityService capability.
 *
 * The disclosure is shown before the existing settings listener turns on AutoTap or opens the
 * Android accessibility settings screen. Consent is versioned so materially changed behavior can
 * require a new acknowledgement in a future release.
 */
public final class AutoClickerConsentSwitchPreference extends SwitchPreferenceCompat {

    static final String CONSENT_KEY = "auto_clicker_accessibility_disclosure_v1";

    public AutoClickerConsentSwitchPreference(@NonNull Context context) {
        super(context);
    }

    public AutoClickerConsentSwitchPreference(@NonNull Context context,
                                              @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoClickerConsentSwitchPreference(@NonNull Context context,
                                              @Nullable AttributeSet attrs,
                                              int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        // Turning an already-active switch off must never be blocked by a dialog.
        if (isChecked() || hasDisclosureConsent()) {
            super.onClick();
            return;
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.auto_clicker_disclosure_title)
                .setMessage(R.string.auto_clicker_disclosure_message)
                .setPositiveButton(R.string.auto_clicker_disclosure_continue, (dialog, which) -> {
                    preferences().edit().putBoolean(CONSENT_KEY, true).apply();
                    trackDisclosureAccepted();
                    // Continue through SwitchPreferenceCompat so the existing listener still owns
                    // user intent, reconciliation, and navigation to Android accessibility settings.
                    AutoClickerConsentSwitchPreference.super.onClick();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    boolean hasDisclosureConsent() {
        return preferences().getBoolean(CONSENT_KEY, false);
    }

    private void trackDisclosureAccepted() {
        try {
            Context app = getContext().getApplicationContext();
            if (app instanceof CalculatorApplication) {
                ((CalculatorApplication) app).getComponent().productAnalytics()
                        .autoTapDisclosureAccepted();
            }
        } catch (RuntimeException ignored) {
        }
    }

    @NonNull
    private SharedPreferences preferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }
}
