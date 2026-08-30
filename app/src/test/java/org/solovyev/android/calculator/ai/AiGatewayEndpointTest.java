package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiGatewayEndpointTest {

    @Test
    public void acceptsOnlyHttpsNovaEndpointWithoutEmbeddedCredentials() {
        AiGatewayEndpoint endpoint = new AiGatewayEndpoint("https://api.nova.example/v1/ai");
        assertEquals("https", endpoint.getUrl().getProtocol());
        assertEquals("api.nova.example", endpoint.getUrl().getHost());
        assertEquals("https://api.nova.example/v1/ai", endpoint.toString());
    }

    @Test
    public void rejectsUnsafeOrAmbiguousEndpoints() {
        assertRejected("http://api.nova.example/v1/ai");
        assertRejected("https://user:pass@api.nova.example/v1/ai");
        assertRejected("https:///missing-host");
        assertRejected("https://api.nova.example/v1/ai#fragment");
        assertRejected(" ");
    }

    private static void assertRejected(String value) {
        boolean rejected = false;
        try {
            new AiGatewayEndpoint(value);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue("Expected endpoint rejection: " + value, rejected);
    }
}
