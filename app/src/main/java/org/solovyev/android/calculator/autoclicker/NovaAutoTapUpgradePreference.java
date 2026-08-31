package org.solovyev.android.calculator.autoclicker;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.solovyev.android.calculator.CalculatorApplication;
import org.solovyev.android.calculator.R;
import org.solovyev.android.calculator.analytics.NovaProductAnalytics;
import org.solovyev.android.calculator.preferences.PreferencesActivity;

/** Opens the existing Nova commercial paywall from the AutoTap conversion context. */
public final class NovaAutoTapUpgradePreference extends Preference {

    public NovaAutoTapUpgradePreference(@NonNull Context context) {
        super(context);
    }

    public NovaAutoTapUpgradePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NovaAutoTapUpgradePreference(@NonNull Context context,
                                        @Nullable AttributeSet attrs,
                                        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        super.onClick();
        try {
            final Context app = getContext().getApplicationContext();
            if (app instanceof CalculatorApplication) {
                ((CalculatorApplication) app).getComponent().productAnalytics()
                        .proPaywallViewed(NovaProductAnalytics.PaywallSource.AUTOTAP);
            }
        } catch (RuntimeException ignored) {
        }
        final Intent intent = PreferencesActivity.makeIntent(
                getContext(), R.xml.preferences_billing, R.string.nova_billing_title);
        getContext().startActivity(intent);
    }
}
