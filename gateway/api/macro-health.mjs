import { createMacroProductionNovaGatewayApplication } from '../src/macro-production-application.mjs';
import { createLazyVercelRoute } from '../src/vercel-entrypoint.mjs';

export default createLazyVercelRoute({
  createApplication: () => createMacroProductionNovaGatewayApplication(),
  selectHandler: (application) => application.macroHealthHandler,
  unavailableBody: {
    status: 'TEMPORARILY_UNAVAILABLE',
    browserSessionConfigured: false,
    candidateReviewConfigured: false,
  },
});
