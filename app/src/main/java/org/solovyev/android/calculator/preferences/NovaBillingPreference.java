package org.solovyev.android.calculator.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.solovyev.android.calculator.CalculatorApplication;
import org.solovyev.android.calculator.R;
import org.solovyev.android.calculator.analytics.NovaProductAnalytics;
import org.solovyev.android.calculator.billing.NovaBillingCoordinator;
import org.solovyev.android.calculator.billing.NovaBillingProducts;
import org.solovyev.android.calculator.entitlement.EntitlementSnapshot;

/** Minimal Google Play paywall action used by the dedicated Nova Pro & AI settings screen. */
public final class NovaBillingPreference extends Preference {

    public static final String KEY_STATUS = "nova.billing.status";
    public static final String KEY_PRO = "nova.billing.pro";
    public static final String KEY_AI_MONTHLY = "nova.billing.ai.monthly";
    public static final String KEY_AI_ANNUAL = "nova.billing.ai.annual";
    public static final String KEY_RESTORE = "nova.billing.restore";

    public NovaBillingPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
    }

    public NovaBillingPreference(Context context) {
        super(context);
        setPersistent(false);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        refreshDisplay();
    }

    @Override
    protected void onClick() {
        super.onClick();
        final NovaBillingCoordinator billing = billing();
        if (billing == null || !billing.isEnabled()) {
            Toast.makeText(getContext(), R.string.nova_billing_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        final EntitlementSnapshot entitlement = billing.getEntitlementSnapshot();
        final String key = getKey();
        try {
            if (KEY_PRO.equals(key)) {
                if (entitlement.hasProLifetime()) {
                    alreadyOwned();
                    return;
                }
                final Activity activity = activity(getContext());
                if (activity != null) {
                    analytics().proPurchaseStarted(NovaProductAnalytics.PaywallSource.OTHER);
                    billing.buyProLifetime(activity);
                }
            } else if (KEY_AI_MONTHLY.equals(key)) {
                if (entitlement.hasAiPlus()) {
                    alreadyOwned();
                    return;
                }
                final Activity activity = activity(getContext());
                if (activity != null) {
                    analytics().proPurchaseStarted(NovaProductAnalytics.PaywallSource.OTHER);
                    billing.buyAiPlusMonthly(activity);
                }
            } else if (KEY_AI_ANNUAL.equals(key)) {
                if (entitlement.hasAiPlus()) {
                    alreadyOwned();
                    return;
                }
                final Activity activity = activity(getContext());
                if (activity != null) {
                    analytics().proPurchaseStarted(NovaProductAnalytics.PaywallSource.OTHER);
                    billing.buyAiPlusAnnual(activity);
                }
            } else if (KEY_RESTORE.equals(key)) {
                billing.restorePurchases();
                Toast.makeText(getContext(), R.string.nova_billing_restore_started, Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), R.string.nova_billing_unavailable, Toast.LENGTH_SHORT).show();
        }
        refreshDisplay();
    }

    private void refreshDisplay() {
        final NovaBillingCoordinator billing = billing();
        final String key = getKey();
        if (KEY_STATUS.equals(key)) {
            setSelectable(false);
            if (billing == null) {
                setSummary(R.string.nova_billing_plan_free);
                return;
            }
            final EntitlementSnapshot entitlement = billing.getEntitlementSnapshot();
            if (entitlement.hasAiPlus()) {
                setSummary(R.string.nova_billing_plan_ai_plus);
            } else if (entitlement.hasProLifetime()) {
                setSummary(R.string.nova_billing_plan_pro);
            } else {
                setSummary(R.string.nova_billing_plan_free);
            }
            return;
        }

        if (billing == null || !billing.isEnabled()) {
            setEnabled(false);
            setSummary(R.string.nova_billing_unavailable);
            return;
        }
        setEnabled(true);
        final EntitlementSnapshot entitlement = billing.getEntitlementSnapshot();
        if (KEY_PRO.equals(key)) {
            if (entitlement.hasProLifetime()) {
                setSummary(R.string.nova_billing_owned);
            } else {
                setSummary(priceOrFallback(
                        billing.getProLifetimePriceLabel(),
                        R.string.nova_billing_buy_pro_summary));
            }
        } else if (KEY_AI_MONTHLY.equals(key)) {
            if (entitlement.hasAiPlus()) {
                setSummary(R.string.nova_billing_active);
            } else {
                setSummary(priceOrFallback(
                        billing.getAiPlusPriceLabel(NovaBillingProducts.AI_PLUS_MONTHLY_BASE_PLAN),
                        R.string.nova_billing_buy_ai_monthly_summary));
            }
        } else if (KEY_AI_ANNUAL.equals(key)) {
            if (entitlement.hasAiPlus()) {
                setSummary(R.string.nova_billing_active);
            } else {
                setSummary(priceOrFallback(
                        billing.getAiPlusPriceLabel(NovaBillingProducts.AI_PLUS_ANNUAL_BASE_PLAN),
                        R.string.nova_billing_buy_ai_annual_summary));
            }
        }
    }

    private CharSequence priceOrFallback(@Nullable String price, int fallbackSummary) {
        if (price == null || price.trim().isEmpty()) {
            return getContext().getString(R.string.nova_billing_loading_price);
        }
        return price + " · " + getContext().getString(fallbackSummary);
    }

    private void alreadyOwned() {
        Toast.makeText(getContext(), R.string.nova_billing_already_owned, Toast.LENGTH_SHORT).show();
    }

    private NovaProductAnalytics analytics() {
        final Context app = getContext().getApplicationContext();
        if (app instanceof CalculatorApplication) {
            try {
                return ((CalculatorApplication) app).getComponent().productAnalytics();
            } catch (RuntimeException ignored) {
            }
        }
        return NovaProductAnalytics.fromSessionEndpoint(null, null, null);
    }

    @Nullable
    private NovaBillingCoordinator billing() {
        final Context app = getContext().getApplicationContext();
        if (!(app instanceof CalculatorApplication)) return null;
        try {
            return ((CalculatorApplication) app).getComponent().billingCoordinator();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Activity activity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
