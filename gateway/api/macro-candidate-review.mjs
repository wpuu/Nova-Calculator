import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';
import { createLazyVercelRoute } from '../src/vercel-entrypoint.mjs';

export default createLazyVercelRoute({
  createApplication: () => createProductionNovaGatewayApplication(),
  selectHandler: (application) => application.macroCandidateReviewHandler,
  unavailableBody: {
    requestId: 'unavailable',
    status: 'TEMPORARILY_UNAVAILABLE',
    decision: null,
    candidateId: null,
    confidence: 0,
    reason: '',
    retryAfterSeconds: 0,
    remainingRequestHint: -1,
    quotaResetAtEpochMs: 0,
  },
});
