package org.solovyev.android.calculator.analytics;

import android.os.Build;

import org.json.JSONObject;
import org.solovyev.android.calculator.BuildConfig;
import org.solovyev.android.calculator.ai.AiSessionTokenProvider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Nova-owned, best-effort product funnel client.
 *
 * No public API accepts arbitrary property maps. That is deliberate: call sites can emit only the
 * small allowlisted event shapes implemented here, so calculator text, screenshots, tap
 * coordinates and target-app content cannot accidentally be attached to analytics requests.
 */
public final class NovaProductAnalytics {

    public enum EntrySource {
        MAIN_MENU("main_menu"),
        SETTINGS("settings"),
        PROFILE_LIMIT("profile_limit"),
        OTHER("other");

        final String wireValue;

        EntrySource(String wireValue) {
            this.wireValue = wireValue;
        }
    }

    public enum PaywallSource {
        AUTOTAP("autotap"),
        SETTINGS("settings"),
        PROFILE_LIMIT("profile_limit"),
        OTHER("other");

        final String wireValue;

        PaywallSource(String wireValue) {
            this.wireValue = wireValue;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 4_000;
    private static final int READ_TIMEOUT_MS = 4_000;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024;

    private final URL endpoint;
    private final AiSessionTokenProvider tokenProvider;
    private final Executor background;

    public NovaProductAnalytics(URL endpoint,
                                AiSessionTokenProvider tokenProvider,
                                Executor background) {
        this.endpoint = endpoint;
        this.tokenProvider = tokenProvider;
        this.background = background;
    }

    public static NovaProductAnalytics fromSessionEndpoint(String sessionEndpoint,
                                                            AiSessionTokenProvider tokenProvider,
                                                            Executor background) {
        return new NovaProductAnalytics(deriveProductEventEndpoint(sessionEndpoint),
                tokenProvider, background);
    }

    public boolean isEnabled() {
        return endpoint != null && tokenProvider != null && background != null;
    }

    public void appFirstOpen() {
        submit(simple("app_first_open"));
    }

    public void autoTapSettingsOpened(EntrySource source) {
        submit(withProperty("autotap_settings_opened", "source",
                source == null ? EntrySource.OTHER.wireValue : source.wireValue));
    }

    public void autoTapDisclosureAccepted() {
        submit(simple("autotap_disclosure_accepted"));
    }

    public void autoTapAccessibilityCompleted() {
        submit(simple("autotap_accessibility_completed"));
    }

    public void autoTapOverlayReady() {
        submit(simple("autotap_overlay_ready"));
    }

    public void autoTapFirstStart() {
        submit(simple("autotap_first_start"));
    }

    public void autoTapStoppedByVolumeDown() {
        submit(simple("autotap_stop_volume_down"));
    }

    public void autoTapRunFailed(int failureCode) {
        if (failureCode < 1 || failureCode > 99) return;
        try {
            JSONObject properties = new JSONObject();
            properties.put("failureCode", failureCode);
            properties.put("manufacturer", boundedManufacturer(Build.MANUFACTURER));
            submit(request("autotap_run_failed", properties));
        } catch (Exception ignored) {
        }
    }

    public void autoTapProfileSaved() {
        submit(simple("autotap_profile_saved"));
    }

    public void autoTapProfileLoaded() {
        submit(simple("autotap_profile_loaded"));
    }

    public void autoTapSecondSession() {
        submit(simple("autotap_second_session"));
    }

    public void proPaywallViewed(PaywallSource source) {
        submit(withProperty("pro_paywall_viewed", "source",
                source == null ? PaywallSource.OTHER.wireValue : source.wireValue));
    }

    public void proPurchaseStarted(PaywallSource source) {
        submit(withProperty("pro_purchase_started", "source",
                source == null ? PaywallSource.OTHER.wireValue : source.wireValue));
    }

    public void purchaseRestoreSuccess() {
        submit(simple("purchase_restore_success"));
    }

    public void purchaseRestoreFailed() {
        submit(simple("purchase_restore_failed"));
    }

    private JSONObject simple(String event) {
        return request(event, new JSONObject());
    }

    private JSONObject withProperty(String event, String key, String value) {
        try {
            JSONObject properties = new JSONObject();
            properties.put(key, value);
            return request(event, properties);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject request(String event, JSONObject properties) {
        try {
            JSONObject body = new JSONObject();
            body.put("eventId", UUID.randomUUID().toString().replace("-", ""));
            body.put("event", event);
            body.put("eventVersion", 1);
            body.put("occurredAtEpochMs", System.currentTimeMillis());
            body.put("appVersion", BuildConfig.VERSION_NAME);
            body.put("sdk", Build.VERSION.SDK_INT);
            body.put("properties", properties == null ? new JSONObject() : properties);
            return body;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void submit(JSONObject body) {
        if (!isEnabled() || body == null) return;
        final String serialized = body.toString();
        try {
            background.execute(() -> send(serialized));
        } catch (RuntimeException ignored) {
            // Analytics must never interfere with the product path.
        }
    }

    private void send(String body) {
        HttpURLConnection connection = null;
        try {
            final String token = tokenProvider.getSessionToken();
            if (token == null || token.trim().isEmpty()) return;

            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            connection.getOutputStream().write(bytes);

            final int status = connection.getResponseCode();
            // Drain only a tiny response so pooled HTTP resources can be released. No response
            // content is trusted or used to alter product behavior.
            InputStream stream = status >= 200 && status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            drainBounded(stream);
        } catch (Exception ignored) {
            // Product analytics is best-effort by design.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void drainBounded(InputStream stream) {
        if (stream == null) return;
        try (InputStream input = stream; ByteArrayOutputStream sink = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) break;
                sink.write(buffer, 0, read);
            }
        } catch (Exception ignored) {
        }
    }

    static URL deriveProductEventEndpoint(String sessionEndpoint) {
        if (sessionEndpoint == null || sessionEndpoint.trim().isEmpty()) return null;
        try {
            URI session = new URI(sessionEndpoint.trim());
            if (!"https".equalsIgnoreCase(session.getScheme())
                    || session.getHost() == null
                    || session.getUserInfo() != null
                    || session.getFragment() != null) {
                return null;
            }
            URI event = new URI("https", null, session.getHost(), session.getPort(),
                    "/api/product-event", null, null);
            return event.toURL();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String boundedManufacturer(String value) {
        String text = value == null ? "Unknown" : value.trim();
        text = text.replaceAll("[^\\p{L}\\p{N} ._-]", "");
        if (text.isEmpty()) text = "Unknown";
        return text.length() <= 40 ? text : text.substring(0, 40);
    }
}
