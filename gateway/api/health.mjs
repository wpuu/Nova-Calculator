import { createProductionNovaGatewayApplication } from '../src/production-application.mjs';
import { createLazyVercelHealthRoute } from '../src/vercel-entrypoint.mjs';

export default createLazyVercelHealthRoute({
  createApplication: () => createProductionNovaGatewayApplication(),
});
