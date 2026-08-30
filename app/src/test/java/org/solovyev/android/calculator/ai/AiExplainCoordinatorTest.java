package org.solovyev.android.calculator.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AiExplainCoordinatorTest {

    @Test
    public void explainCarriesVerifiedCalculatorResultIntoGatewayRequest() {
        FakeClient client = new FakeClient();
        AiExplainCoordinator coordinator = new AiExplainCoordinator(client);
        RecordingListener listener = new RecordingListener();

        coordinator.explain("8536*0.85*1.13", "8200.328", "zh-CN", listener);

        assertNotNull(listener.started);
        assertEquals(AiOperation.EXPLAIN_CALCULATION, listener.started.getOperation());
        assertEquals("8536*0.85*1.13", listener.started.getExpression());
        assertEquals("8200.328", listener.started.getDeterministicResult());
        assertEquals("zh-CN", listener.started.getLocaleTag());
        assertEquals(listener.started.getRequestId(), client.request.getRequestId());
    }

    @Test
    public void staleResponseCannotOverwriteNewerCalculation() {
        FakeClient client = new FakeClient();
        AiExplainCoordinator coordinator = new AiExplainCoordinator(client);
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();

        coordinator.explain("1+1", "2", "en-US", first);
        AiGatewayClient.Callback firstCallback = client.callback;
        String firstId = client.request.getRequestId();

        coordinator.explain("2+2", "4", "en-US", second);
        AiGatewayClient.Callback secondCallback = client.callback;
        String secondId = client.request.getRequestId();

        firstCallback.onComplete(success(firstId, "old"));
        assertNull(first.finished);

        secondCallback.onComplete(success(secondId, "new"));
        assertEquals("new", second.finished.getAnswer());
    }

    @Test
    public void mismatchedResponseIdIsConvertedToTemporaryUnavailable() {
        FakeClient client = new FakeClient();
        AiExplainCoordinator coordinator = new AiExplainCoordinator(client);
        RecordingListener listener = new RecordingListener();

        coordinator.explain("2+2", "4", "en-US", listener);
        client.callback.onComplete(success("different-id", "wrong"));

        assertNotNull(listener.finished);
        assertEquals(listener.started.getRequestId(), listener.finished.getRequestId());
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, listener.finished.getStatus());
        assertEquals("", listener.finished.getAnswer());
    }

    @Test
    public void cancelSuppressesLateResponse() {
        FakeClient client = new FakeClient();
        AiExplainCoordinator coordinator = new AiExplainCoordinator(client);
        RecordingListener listener = new RecordingListener();

        coordinator.explain("2+2", "4", "en-US", listener);
        String requestId = client.request.getRequestId();
        coordinator.cancelCurrent();
        client.callback.onComplete(success(requestId, "late"));

        assertNull(listener.finished);
    }

    @Test
    public void synchronousTransportFailureBecomesTemporaryUnavailable() {
        AiGatewayClient failingClient = (request, callback) -> {
            throw new IllegalStateException("transport not configured");
        };
        AiExplainCoordinator coordinator = new AiExplainCoordinator(failingClient);
        RecordingListener listener = new RecordingListener();

        coordinator.explain("3+3", "6", "en-US", listener);

        assertNotNull(listener.finished);
        assertEquals(AiGatewayStatus.TEMPORARILY_UNAVAILABLE, listener.finished.getStatus());
        assertEquals(listener.started.getRequestId(), listener.finished.getRequestId());
    }

    private static AiGatewayResponse success(String requestId, String answer) {
        return new AiGatewayResponse(
                requestId,
                AiGatewayStatus.SUCCESS,
                answer,
                0L,
                -1,
                0L);
    }

    private static final class FakeClient implements AiGatewayClient {
        private AiGatewayRequest request;
        private Callback callback;

        @Override
        public void execute(AiGatewayRequest request, Callback callback) {
            this.request = request;
            this.callback = callback;
        }
    }

    private static final class RecordingListener implements AiExplainCoordinator.Listener {
        private AiGatewayRequest started;
        private AiGatewayResponse finished;

        @Override
        public void onStarted(AiGatewayRequest request) {
            started = request;
        }

        @Override
        public void onFinished(AiGatewayResponse response) {
            finished = response;
        }
    }
}
