package org.solovyev.android.calculator.billing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/** Guards the Play Console contract against accidental product-id drift. */
public class NovaBillingProductsTest {

    @Test
    public void launchProductIdsStayStable() {
        assertEquals("nova_pro_lifetime", NovaBillingProducts.PRO_LIFETIME);
        assertEquals("nova_ai_plus", NovaBillingProducts.AI_PLUS);
        assertNotEquals(NovaBillingProducts.PRO_LIFETIME, NovaBillingProducts.AI_PLUS);
    }

    @Test
    public void aiPlusBasePlanIdsStayStable() {
        assertEquals("monthly", NovaBillingProducts.AI_PLUS_MONTHLY_BASE_PLAN);
        assertEquals("annual", NovaBillingProducts.AI_PLUS_ANNUAL_BASE_PLAN);
        assertNotEquals(
                NovaBillingProducts.AI_PLUS_MONTHLY_BASE_PLAN,
                NovaBillingProducts.AI_PLUS_ANNUAL_BASE_PLAN);
    }
}
