import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import fs from 'node:fs';
import vm from 'node:vm';

const backgroundSource = fs.readFileSync('prototypes/nova-macro-mv3/background.js', 'utf8');
const aiReviewSource = fs.readFileSync('prototypes/nova-macro-mv3/ai-review-client.js', 'utf8');

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const clone = (value) => (value == null ? value : structuredClone(value));

function createHarness() {
  const sessionData = {};
  const localData = {};
  const runtimeListeners = [];
  const updatedListeners = [];
  const injected = [];
  const contentMessages = [];
  const gatewayCalls = [];

  let injectionAllowed = true;
  let contentAvailable = true;
  let currentUrl = 'https://admin.shopify.com/store/test/orders';
  let contentResponder = async (message) => {
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, recording: false, url: currentUrl };
    if (message.type === 'NOVA_SET_RECORDING') return { ok: true, recording: !!message.recording };
    if (message.type === 'NOVA_EXECUTE_STEP') {
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    return { ok: false, error: 'UNKNOWN_CONTENT_MESSAGE' };
  };
  let gatewayResponder = async () => new Response(JSON.stringify({
    status: 'SUCCESS',
    decision: 'ABSTAIN',
    candidateId: null,
    confidence: 0.5,
    reason: 'Default test abstain.',
  }), { status: 200, headers: { 'content-type': 'application/json' } });

  const scaledSetTimeout = (fn, delay = 0, ...args) => setTimeout(fn, Math.min(delay, 8), ...args);

  const chrome = {
    storage: {
      session: {
        async get(key) { return { [key]: clone(sessionData[key]) }; },
        async set(values) {
          await sleep(1);
          Object.assign(sessionData, clone(values));
        },
      },
      local: {
        async get(key) { return { [key]: clone(localData[key]) }; },
        async set(values) {
          await sleep(1);
          Object.assign(localData, clone(values));
        },
      },
    },
    scripting: {
      async executeScript(options) {
        if (!injectionAllowed) throw new Error('MISSING_HOST_PERMISSION');
        injected.push(clone(options));
        contentAvailable = true;
        return [];
      },
    },
    tabs: {
      async sendMessage(tabId, message) {
        contentMessages.push({ tabId, message: clone(message) });
        if (!contentAvailable) throw new Error('NO_RECEIVER');
        return contentResponder(clone(message), tabId);
      },
      onUpdated: {
        addListener(listener) { updatedListeners.push(listener); },
      },
    },
    runtime: {
      id: 'nova-test-extension',
      onMessage: {
        addListener(listener) { runtimeListeners.push(listener); },
      },
    },
  };

  const context = vm.createContext({
    chrome,
    console,
    structuredClone,
    setTimeout: scaledSetTimeout,
    clearTimeout,
    Promise,
    Date,
    URL,
    crypto: { randomUUID },
    fetch: async (url, options) => {
      gatewayCalls.push({ url, options: clone({
        method: options?.method,
        headers: options?.headers,
        body: options?.body,
        cache: options?.cache,
      }) });
      return gatewayResponder(url, options);
    },
  });
  context.importScripts = (...files) => {
    for (const file of files) {
      if (file !== 'ai-review-client.js') throw new Error(`Unexpected importScripts file: ${file}`);
      vm.runInContext(aiReviewSource, context, { filename: file });
    }
  };
  vm.runInContext(backgroundSource, context, { filename: 'background.js' });

  assert.equal(runtimeListeners.length, 1, 'background must register one runtime message listener');
  assert.equal(updatedListeners.length, 1, 'background must register one tab updated listener');

  const runtimeListener = runtimeListeners[0];
  const updatedListener = updatedListeners[0];
  const extensionSender = { url: 'chrome-extension://nova-test-extension/popup.html' };

  async function dispatch(message, sender = extensionSender) {
    return new Promise((resolve, reject) => {
      let settled = false;
      const sendResponse = (response) => {
        if (settled) return;
        settled = true;
        resolve(clone(response));
      };
      try {
        runtimeListener(clone(message), clone(sender), sendResponse);
      } catch (error) {
        reject(error);
      }
      setTimeout(() => {
        if (!settled) reject(new Error(`Timed out dispatching ${message.type}`));
      }, 1500);
    });
  }

  async function tabComplete(tabId) {
    updatedListener(tabId, { status: 'complete' }, { id: tabId });
    await sleep(20);
  }

  async function waitFor(predicate, timeoutMs = 1500) {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      const value = await predicate();
      if (value) return value;
      await sleep(5);
    }
    throw new Error('waitFor timeout');
  }

  return {
    sessionData,
    localData,
    injected,
    contentMessages,
    gatewayCalls,
    dispatch,
    tabComplete,
    waitFor,
    setInjectionAllowed(value) { injectionAllowed = value; },
    setContentAvailable(value) { contentAvailable = value; },
    setCurrentUrl(value) { currentUrl = value; },
    setContentResponder(responder) { contentResponder = responder; },
    setGatewayResponder(responder) { gatewayResponder = responder; },
  };
}

const tests = [];
function test(name, fn) { tests.push({ name, fn }); }
async function getSession(h) { return h.dispatch({ type: 'NOVA_GET_SESSION' }); }

function reviewPayload() {
  return {
    reviewId: 'review-test-1',
    index: 0,
    stepType: 'click',
    semanticActionId: 'shopify.export_orders',
    original: {
      semanticActionId: 'shopify.export_orders', role: 'button', names: ['Export orders'],
      context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button',
    },
    candidates: [
      { id: 'candidate_1', role: 'button', names: ['Import'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 50 },
      { id: 'candidate_2', role: 'button', names: ['Export orders'], context: ['Orders'], attrs: {}, hrefPath: '', tag: 'button', score: 61 },
    ],
    allowedCandidateIds: ['candidate_1', 'candidate_2'],
    policy: 'SELECT_LISTED_CANDIDATE_OR_ABSTAIN',
  };
}

async function configureGateway(h) {
  const result = await h.dispatch({
    type: 'NOVA_SET_GATEWAY_SESSION',
    endpoint: 'https://gateway.test/api/macro-candidate-review',
    sessionToken: 'nova-session-token-test-0123456789',
    expiresAtEpochMs: Date.now() + 60_000,
  });
  assert.equal(result.ok, true);
}

async function enterAiReview(h, tabId = 30) {
  h.localData.novaMacroPocLast = [{ id: 'ambiguous' }, { id: 'after-ai' }];
  const executed = [];
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      if (message.index === 0) {
        return { ok: false, status: 'AI_REVIEW', index: 0, review: reviewPayload() };
      }
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    if (message.type === 'NOVA_APPLY_REVIEW_CHOICE') {
      return { ok: true, status: 'CLICKED', index: 0, mayNavigate: false, selectedCandidateId: message.candidateId };
    }
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, url: 'https://admin.shopify.com/store/test/orders' };
    return { ok: true };
  });
  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId });
  await h.waitFor(async () => (await getSession(h)).mode === 'AI_REVIEW');
  return executed;
}

test('serializes rapid recorded steps and Stop waits for writes', async () => {
  const h = createHarness();
  await h.dispatch({ type: 'NOVA_START_RECORDING', tabId: 7, originPattern: 'https://admin.shopify.com/*' });
  const writes = Array.from({ length: 40 }, (_, index) => h.dispatch(
    { type: 'NOVA_RECORD_STEP', step: { type: 'click', id: index } },
    { tab: { id: 7 } },
  ));
  await Promise.all([...writes, h.dispatch({ type: 'NOVA_STOP_RECORDING' })]);
  assert.equal(h.localData.novaMacroPocLast.length, 40);
  assert.deepEqual(h.localData.novaMacroPocLast.map((step) => step.id), Array.from({ length: 40 }, (_, i) => i));
});

test('ignores recorded steps from a different tab', async () => {
  const h = createHarness();
  await h.dispatch({ type: 'NOVA_START_RECORDING', tabId: 3, originPattern: 'https://a.test/*' });
  const ignored = await h.dispatch(
    { type: 'NOVA_RECORD_STEP', step: { type: 'click', id: 'wrong-tab' } },
    { tab: { id: 4 } },
  );
  assert.equal(ignored.ignored, true);
  await h.dispatch({ type: 'NOVA_STOP_RECORDING' });
  assert.equal(h.localData.novaMacroPocLast.length, 0);
});

test('recording survives a same-tab document navigation', async () => {
  const h = createHarness();
  await h.dispatch({ type: 'NOVA_START_RECORDING', tabId: 5, originPattern: 'https://a.test/*' });
  await h.dispatch({ type: 'NOVA_RECORD_STEP', step: { type: 'click', id: 'before' } }, { tab: { id: 5 } });
  h.setContentAvailable(false);
  await h.tabComplete(5);
  await h.dispatch({ type: 'NOVA_RECORD_STEP', step: { type: 'click', id: 'after' } }, { tab: { id: 5 } });
  await h.dispatch({ type: 'NOVA_STOP_RECORDING' });
  assert.deepEqual(h.localData.novaMacroPocLast.map((step) => step.id), ['before', 'after']);
});

test('replay executes normal steps exactly once and completes', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];
  const executed = [];
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, url: 'https://a.test/' };
    return { ok: true };
  });
  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId: 8 });
  await h.waitFor(async () => (await getSession(h)).mode === 'COMPLETED');
  assert.deepEqual(executed, [0, 1, 2]);
});

test('AI_REVIEW is terminal until an explicit extension-UI decision', async () => {
  const h = createHarness();
  const executed = await enterAiReview(h, 9);
  await sleep(30);
  assert.deepEqual(executed, [0]);
  assert.equal((await getSession(h)).mode, 'AI_REVIEW');
});

test('navigation step cannot race the next step on the old document', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [{ id: 'navigate' }, { id: 'after-nav' }];
  const executed = [];
  h.setCurrentUrl('https://a.test/orders');
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      if (message.index === 0) return { ok: true, status: 'CLICKED', index: 0, mayNavigate: true, urlBefore: 'https://a.test/orders' };
      return { ok: true, status: 'CLICKED', index: 1, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, url: 'https://a.test/orders' };
    return { ok: true };
  });
  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId: 10 });
  await h.waitFor(() => executed.length >= 1);
  await sleep(40);
  assert.deepEqual(executed, [0]);
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, url: 'https://a.test/orders/1001' };
    return { ok: true };
  });
  await h.waitFor(() => executed.includes(1));
  await h.waitFor(async () => (await getSession(h)).mode === 'COMPLETED');
  assert.deepEqual(executed, [0, 1]);
});

test('unrelated tab complete event does not duplicate an in-flight replay step', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [{ id: 'slow' }];
  let resolveStep;
  let executeCount = 0;
  h.setContentResponder((message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executeCount += 1;
      return new Promise((resolve) => { resolveStep = resolve; });
    }
    return Promise.resolve({ ok: true, url: 'https://a.test/' });
  });
  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId: 11 });
  await h.waitFor(() => executeCount === 1);
  await h.tabComplete(11);
  assert.equal(executeCount, 1);
  resolveStep({ ok: true, status: 'CLICKED', index: 0, mayNavigate: false });
  await h.waitFor(async () => (await getSession(h)).mode === 'COMPLETED');
  assert.equal(executeCount, 1);
});

test('missing site access pauses after navigation and explicit resume continues', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [{ id: 'navigate' }, { id: 'after-grant' }];
  const executed = [];
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      if (message.index === 0) return { ok: true, status: 'CLICKED', index: 0, mayNavigate: true, urlBefore: 'https://a.test/start' };
      return { ok: true, status: 'CLICKED', index: 1, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, url: 'https://a.test/start' };
    return { ok: true };
  });
  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId: 12 });
  await h.waitFor(() => executed.length === 1);
  h.setContentAvailable(false);
  h.setInjectionAllowed(false);
  await h.tabComplete(12);
  assert.equal((await getSession(h)).needsSiteAccess, true);
  assert.deepEqual(executed, [0]);
  h.setInjectionAllowed(true);
  h.setContentAvailable(false);
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') return { ok: true, url: 'https://b.test/next' };
    return { ok: true };
  });
  await h.dispatch({ type: 'NOVA_RESUME_CURRENT', tabId: 12, originPattern: 'https://b.test/*' });
  await h.waitFor(async () => (await getSession(h)).mode === 'COMPLETED');
  assert.deepEqual(executed, [0, 1]);
});

test('Gateway SELECT can only apply a listed local candidate and replay continues once', async () => {
  const h = createHarness();
  const executed = await enterAiReview(h, 31);
  const applied = [];
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_APPLY_REVIEW_CHOICE') {
      applied.push(message.candidateId);
      return { ok: true, status: 'CLICKED', index: 0, mayNavigate: false, selectedCandidateId: message.candidateId };
    }
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    return { ok: true, url: 'https://admin.shopify.com/store/test/orders' };
  });
  await configureGateway(h);
  h.setGatewayResponder(async () => new Response(JSON.stringify({
    status: 'SUCCESS', decision: 'SELECT', candidateId: 'candidate_2', confidence: 0.92, reason: 'Unique match.',
  }), { status: 200, headers: { 'content-type': 'application/json' } }));
  const result = await h.dispatch({ type: 'NOVA_RUN_AI_REVIEW', reviewId: 'review-test-1' });
  assert.equal(result.ok, true);
  assert.equal(result.gatewayDecision, 'SELECT');
  assert.deepEqual(applied, ['candidate_2']);
  await h.waitFor(async () => (await getSession(h)).mode === 'COMPLETED');
  assert.deepEqual(executed, [0, 1]);
  assert.equal(h.gatewayCalls.length, 1);
  const outbound = JSON.parse(h.gatewayCalls[0].options.body);
  assert.deepEqual(outbound.review.allowedCandidateIds, ['candidate_1', 'candidate_2']);
  assert.equal(JSON.stringify(outbound).includes('selector'), false);
});

test('Gateway invented candidate fails closed to ABSTAIN without DOM execution', async () => {
  const h = createHarness();
  await enterAiReview(h, 32);
  await configureGateway(h);
  h.setGatewayResponder(async () => new Response(JSON.stringify({
    status: 'SUCCESS', decision: 'SELECT', candidateId: 'candidate_999', confidence: 1, reason: 'bad',
  }), { status: 200, headers: { 'content-type': 'application/json' } }));
  const beforeApply = h.contentMessages.filter((entry) => entry.message.type === 'NOVA_APPLY_REVIEW_CHOICE').length;
  const result = await h.dispatch({ type: 'NOVA_RUN_AI_REVIEW', reviewId: 'review-test-1' });
  assert.equal(result.ok, true);
  assert.equal(result.gatewayDecision, 'ABSTAIN');
  assert.equal((await getSession(h)).mode, 'ABSTAIN');
  const afterApply = h.contentMessages.filter((entry) => entry.message.type === 'NOVA_APPLY_REVIEW_CHOICE').length;
  assert.equal(afterApply, beforeApply);
});

test('concurrent AI review requests are global single-flight and send only one Gateway request', async () => {
  const h = createHarness();
  await enterAiReview(h, 33);
  await configureGateway(h);
  let resolveGateway;
  h.setGatewayResponder(() => new Promise((resolve) => { resolveGateway = resolve; }));
  const first = h.dispatch({ type: 'NOVA_RUN_AI_REVIEW', reviewId: 'review-test-1' });
  await h.waitFor(() => h.gatewayCalls.length === 1);
  const second = await h.dispatch({ type: 'NOVA_RUN_AI_REVIEW', reviewId: 'review-test-1' });
  assert.equal(second.ok, false);
  assert.equal(second.error, 'AI_REVIEW_IN_FLIGHT');
  assert.equal(h.gatewayCalls.length, 1);
  resolveGateway(new Response(JSON.stringify({
    status: 'SUCCESS', decision: 'ABSTAIN', candidateId: null, confidence: 0.4, reason: 'Ambiguous.',
  }), { status: 200, headers: { 'content-type': 'application/json' } }));
  const firstResult = await first;
  assert.equal(firstResult.ok, true);
  assert.equal(firstResult.gatewayDecision, 'ABSTAIN');
  assert.equal(h.gatewayCalls.length, 1);
});

let passed = 0;
for (const { name, fn } of tests) {
  try {
    await fn();
    passed += 1;
    console.log(`PASS\t${name}`);
  } catch (error) {
    console.error(`FAIL\t${name}\n${error.stack || error}`);
  }
}
console.log(`RESULT ${passed}/${tests.length}`);
if (passed !== tests.length) process.exitCode = 1;
