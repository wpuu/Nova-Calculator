package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiGatewayFeatureConfigTest {

    @Test
    public void missingGatewayOrSessionUrlKeepsAiHidden() {
        assertFalse(AiGatewayFeatureConfig.fromNullableUrls(null, null, true).isEnabled());
        assertFalse(AiGatewayFeatureConfig.fromNullableUrls("https://nova.example/ai", "", true).isEnabled());
        assertFalse(AiGatewayFeatureConfig.fromNullableUrls("", "https://nova.example/session", true).isEnabled());
    }

    @Test
    public void validNovaEndpointsStillRequireInstallationProofCapability() {
        AiGatewayFeatureConfig disabled = AiGatewayFeatureConfig.fromNullableUrls(
                "https://nova.example/ai",
                "https://nova.example/session/anonymous",
                false);
        assertFalse(disabled.isEnabled());

        AiGatewayFeatureConfig enabled = AiGatewayFeatureConfig.fromNullableUrls(
                "https://nova.example/ai",
                "https://nova.example/session/anonymous",
                true);
        assertTrue(enabled.isEnabled());
        assertTrue(enabled.requireEndpoint().toString().startsWith("https://nova.example/"));
        assertTrue(enabled.requireSessionEndpoint().toString().startsWith("https://nova.example/"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void insecureGatewayUrlIsRejected() {
        AiGatewayFeatureConfig.fromNullableUrls(
                "http://nova.example/ai",
                "https://nova.example/session/anonymous",
                true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void insecureSessionUrlIsRejected() {
        AiGatewayFeatureConfig.fromNullableUrls(
                "https://nova.example/ai",
                "http://nova.example/session/anonymous",
                true);
    }

    @Test(expected = IllegalStateException.class)
    public void disabledConfigHasNoUsableGatewayEndpoint() {
        AiGatewayFeatureConfig.fromNullableUrls(
                "https://nova.example/ai",
                "https://nova.example/session/anonymous",
                false).requireEndpoint();
    }

    @Test(expected = IllegalStateException.class)
    public void disabledConfigHasNoUsableSessionEndpoint() {
        AiGatewayFeatureConfig.fromNullableUrls(
                "https://nova.example/ai",
                "https://nova.example/session/anonymous",
                false).requireSessionEndpoint();
    }
}
