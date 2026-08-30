import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';
import { createLazyVercelRoute } from '../src/vercel-entrypoint.mjs';

export default createLazyVercelRoute({
  createApplication: () => createProductionNovaGatewayApplication(),
  selectHandler: (application) => application.billingHandler,
  unavailableBody: {
    status: 'TEMPORARILY_UNAVAILABLE',
    sessionToken: '',
    expiresAtEpochMs: 0,
    entitlements: [],
    products: [],
  },
});
