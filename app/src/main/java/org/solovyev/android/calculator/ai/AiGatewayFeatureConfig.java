package org.solovyev.android.calculator.ai;

import javax.annotation.Nullable;

/** Controls whether Nova AI actions may be exposed by the UI. */
public final class AiGatewayFeatureConfig {

    @Nullable
    private final AiGatewayEndpoint endpoint;

    private AiGatewayFeatureConfig(@Nullable AiGatewayEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public static AiGatewayFeatureConfig fromNullableUrl(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return new AiGatewayFeatureConfig(null);
        }
        return new AiGatewayFeatureConfig(new AiGatewayEndpoint(value));
    }

    public boolean isEnabled() {
        return endpoint != null;
    }

    public AiGatewayEndpoint requireEndpoint() {
        if (endpoint == null) {
            throw new IllegalStateException("Nova AI gateway is not configured");
        }
        return endpoint;
    }
}
