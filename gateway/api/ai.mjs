import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';
import { createLazyVercelRoute } from '../src/vercel-entrypoint.mjs';

export default createLazyVercelRoute({
  createApplication: () => createProductionNovaGatewayApplication(),
  selectHandler: (application) => application.aiHandler,
  unavailableBody: {
    requestId: 'unavailable',
    status: 'TEMPORARILY_UNAVAILABLE',
    answer: '',
    retryAfterSeconds: 0,
    remainingRequestHint: -1,
    quotaResetAtEpochMs: 0,
  },
});
