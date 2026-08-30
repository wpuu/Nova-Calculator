package org.solovyev.android.calculator.billing;

/**
 * Stable Google Play product identifiers for Nova's first commercial launch.
 *
 * Prices are configured in Play Console and deliberately do not live in the APK.
 * AI Plus uses one subscription product with monthly/annual base plans so pricing,
 * trials and regional offers can change without an app release.
 */
public final class NovaBillingProducts {

    public static final String PRO_LIFETIME = "nova_pro_lifetime";
    public static final String AI_PLUS = "nova_ai_plus";

    public static final String AI_PLUS_MONTHLY_BASE_PLAN = "monthly";
    public static final String AI_PLUS_ANNUAL_BASE_PLAN = "annual";

    private NovaBillingProducts() {
    }
}
