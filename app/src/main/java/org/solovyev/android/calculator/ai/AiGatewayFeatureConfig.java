package org.solovyev.android.calculator.ai;

import javax.annotation.Nullable;

/** Controls whether Nova AI actions may be exposed by the UI. */
public final class AiGatewayFeatureConfig {

    @Nullable
    private final AiGatewayEndpoint gatewayEndpoint;
    @Nullable
    private final AiSessionEndpoint sessionEndpoint;
    private final boolean installationProofAvailable;

    private AiGatewayFeatureConfig(@Nullable AiGatewayEndpoint gatewayEndpoint,
                                   @Nullable AiSessionEndpoint sessionEndpoint,
                                   boolean installationProofAvailable) {
        this.gatewayEndpoint = gatewayEndpoint;
        this.sessionEndpoint = sessionEndpoint;
        this.installationProofAvailable = installationProofAvailable;
    }

    public static AiGatewayFeatureConfig fromNullableUrls(@Nullable String gatewayUrl,
                                                          @Nullable String sessionUrl,
                                                          boolean installationProofAvailable) {
        final AiGatewayEndpoint gateway = blank(gatewayUrl)
                ? null
                : new AiGatewayEndpoint(gatewayUrl);
        final AiSessionEndpoint session = blank(sessionUrl)
                ? null
                : new AiSessionEndpoint(sessionUrl);
        return new AiGatewayFeatureConfig(gateway, session, installationProofAvailable);
    }

    public boolean isEnabled() {
        return gatewayEndpoint != null && sessionEndpoint != null && installationProofAvailable;
    }

    public AiGatewayEndpoint requireEndpoint() {
        if (!isEnabled()) {
            throw new IllegalStateException("Nova AI gateway is not fully configured");
        }
        return gatewayEndpoint;
    }

    public AiSessionEndpoint requireSessionEndpoint() {
        if (!isEnabled()) {
            throw new IllegalStateException("Nova AI session flow is not fully configured");
        }
        return sessionEndpoint;
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
