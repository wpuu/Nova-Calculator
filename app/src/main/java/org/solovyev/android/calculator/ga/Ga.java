package org.solovyev.android.calculator.ga;

import android.app.Application;
import android.content.SharedPreferences;

import org.solovyev.android.calculator.Preferences;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Temporary privacy-safe analytics shim for the Nova commercial branch.
 *
 * The inherited Firebase project belongs to the Calculator++ lineage and must not receive
 * Nova telemetry. Product analytics will be reintroduced only with Nova-owned infrastructure
 * and event definitions that avoid logging users' raw calculator input.
 */
@Singleton
public final class Ga implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    public Ga(@Nonnull Application application, @Nonnull SharedPreferences preferences) {
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void onButtonPressed(@Nullable String text) {
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
    }

    public void reportInitially(@Nonnull SharedPreferences preferences) {
    }

    public void onFloatingCalculatorOpened() {
    }
}
