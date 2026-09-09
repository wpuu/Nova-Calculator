import { createMacroProductionNovaGatewayApplication } from '../src/macro-production-application.mjs';
import { createLazyVercelRoute } from '../src/vercel-entrypoint.mjs';

export default createLazyVercelRoute({
  createApplication: () => createMacroProductionNovaGatewayApplication(),
  selectHandler: (application) => application.browserSessionHandler,
  unavailableBody: {
    status: 'TEMPORARILY_UNAVAILABLE',
    sessionToken: '',
    expiresAtEpochMs: 0,
  },
});
