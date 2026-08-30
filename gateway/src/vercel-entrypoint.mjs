/**
 * Small Vercel Web-Handler adapter with lazy application construction.
 *
 * Deployment configuration errors are deliberately converted to a fixed 503 response. The route
 * never serializes thrown errors because those may mention server-only environment variable names
 * or infrastructure details.
 */
export function createLazyVercelRoute({
  createApplication,
  selectHandler,
  unavailableBody,
} = {}) {
  if (typeof createApplication !== 'function') throw new Error('createApplication is required');
  if (typeof selectHandler !== 'function') throw new Error('selectHandler is required');
  const safeUnavailable = sanitizeUnavailableBody(unavailableBody);
  let application = null;

  return Object.freeze({
    async fetch(request) {
      try {
        if (!application) application = createApplication();
        const handler = selectHandler(application);
        if (typeof handler !== 'function') throw new Error('deployment handler is unavailable');
        return await handler(request);
      } catch {
        return jsonResponse(safeUnavailable, 503);
      }
    },
  });
}

export function createLazyVercelHealthRoute({ createApplication } = {}) {
  if (typeof createApplication !== 'function') throw new Error('createApplication is required');
  let application = null;
  return Object.freeze({
    async fetch(request) {
      if (!(request instanceof Request) || request.method !== 'GET') {
        return jsonResponse({ status: 'METHOD_NOT_ALLOWED' }, 405, { allow: 'GET' });
      }
      try {
        if (!application) application = createApplication();
        if (!application?.safeSummary) throw new Error('application unavailable');
        return jsonResponse({ status: 'OK' }, 200);
      } catch {
        return jsonResponse({ status: 'UNAVAILABLE' }, 503);
      }
    },
  });
}

function sanitizeUnavailableBody(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return Object.freeze({ status: 'TEMPORARILY_UNAVAILABLE' });
  }
  return Object.freeze(JSON.parse(JSON.stringify(value)));
}

function jsonResponse(body, status, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      ...extraHeaders,
    },
  });
}
