package org.solovyev.android.calculator.billing;

import com.android.billingclient.api.Purchase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpNovaBillingEntitlementClientTest {

    @Test
    public void requestBodyContainsOnlyKnownLaunchProducts() throws Exception {
        Purchase pro = purchase("pro-token", NovaBillingProducts.PRO_LIFETIME);
        Purchase ai = purchase("ai-token", NovaBillingProducts.AI_PLUS);
        Purchase unrelated = purchase("other-token", "other_product");

        JSONObject body = new JSONObject(HttpNovaBillingEntitlementClient.requestBody(
                Arrays.asList(pro, ai, unrelated)));
        JSONArray purchases = body.getJSONArray("purchases");

        assertEquals(2, purchases.length());
        assertEquals(NovaBillingProducts.PRO_LIFETIME,
                purchases.getJSONObject(0).getString("productId"));
        assertEquals("inapp", purchases.getJSONObject(0).getString("productType"));
        assertEquals("pro-token", purchases.getJSONObject(0).getString("purchaseToken"));
        assertEquals(NovaBillingProducts.AI_PLUS,
                purchases.getJSONObject(1).getString("productId"));
        assertEquals("subs", purchases.getJSONObject(1).getString("productType"));
        assertEquals("ai-token", purchases.getJSONObject(1).getString("purchaseToken"));
    }

    @Test
    public void requestBodyDeduplicatesSameProductId() throws Exception {
        Purchase first = purchase("first-token", NovaBillingProducts.PRO_LIFETIME);
        Purchase duplicate = purchase("duplicate-token", NovaBillingProducts.PRO_LIFETIME);

        JSONObject body = new JSONObject(HttpNovaBillingEntitlementClient.requestBody(
                Arrays.asList(first, duplicate)));
        JSONArray purchases = body.getJSONArray("purchases");

        assertEquals(1, purchases.length());
        assertEquals("first-token", purchases.getJSONObject(0).getString("purchaseToken"));
    }

    @Test
    public void emptySnapshotRemainsAuthoritativeEmptyArray() throws Exception {
        JSONObject body = new JSONObject(HttpNovaBillingEntitlementClient.requestBody(
                Collections.emptyList()));
        assertEquals(0, body.getJSONArray("purchases").length());
    }

    private static Purchase purchase(String token, String... products) {
        Purchase purchase = mock(Purchase.class);
        when(purchase.getPurchaseToken()).thenReturn(token);
        when(purchase.getProducts()).thenReturn(Arrays.asList(products));
        return purchase;
    }
}
