import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const backgroundSource = fs.readFileSync(
  'prototypes/nova-macro-mv3/background.js',
  'utf8',
);

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function clone(value) {
  return value == null ? value : structuredClone(value);
}

function createHarness() {
  const sessionData = {};
  const localData = {};
  const runtimeListeners = [];
  const updatedListeners = [];
  const injected = [];
  const contentMessages = [];

  let injectionAllowed = true;
  let contentAvailable = true;
  let currentUrl = 'https://admin.shopify.com/store/test/orders';
  let contentResponder = async (message) => {
    if (message.type === 'NOVA_CONTENT_STATE') {
      return { ok: true, recording: false, url: currentUrl };
    }
    if (message.type === 'NOVA_SET_RECORDING') return { ok: true, recording: !!message.recording };
    if (message.type === 'NOVA_EXECUTE_STEP') {
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    return { ok: false, error: 'UNKNOWN_CONTENT_MESSAGE' };
  };

  const scaledSetTimeout = (fn, delay = 0, ...args) => setTimeout(fn, Math.min(delay, 8), ...args);

  const chrome = {
    storage: {
      session: {
        async get(key) {
          return { [key]: clone(sessionData[key]) };
        },
        async set(values) {
          await sleep(1);
          Object.assign(sessionData, clone(values));
        },
      },
      local: {
        async get(key) {
          return { [key]: clone(localData[key]) };
        },
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
        addListener(listener) {
          updatedListeners.push(listener);
        },
      },
    },
    runtime: {
      onMessage: {
        addListener(listener) {
          runtimeListeners.push(listener);
        },
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
  });
  vm.runInContext(backgroundSource, context, { filename: 'background.js' });

  assert.equal(runtimeListeners.length, 1, 'background must register one runtime message listener');
  assert.equal(updatedListeners.length, 1, 'background must register one tab updated listener');

  const runtimeListener = runtimeListeners[0];
  const updatedListener = updatedListeners[0];

  async function dispatch(message, sender = {}) {
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
      }, 1000);
    });
  }

  async function tabComplete(tabId) {
    updatedListener(tabId, { status: 'complete' }, { id: tabId });
    await sleep(20);
  }

  async function waitFor(predicate, timeoutMs = 1000) {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      const value = await predicate();
      if (value) return value;
      await sleep(5);
    }
    throw new Error('waitFor timeout');
  }

  return {
    chrome,
    sessionData,
    localData,
    injected,
    contentMessages,
    dispatch,
    tabComplete,
    waitFor,
    setInjectionAllowed(value) { injectionAllowed = value; },
    setContentAvailable(value) { contentAvailable = value; },
    setCurrentUrl(value) { currentUrl = value; },
    setContentResponder(responder) { contentResponder = responder; },
  };
}

const tests = [];
function test(name, fn) {
  tests.push({ name, fn });
}

async function getSession(h) {
  return h.dispatch({ type: 'NOVA_GET_SESSION' });
}

test('serializes rapid recorded steps and Stop waits for writes', async () => {
  const h = createHarness();
  await h.dispatch({
    type: 'NOVA_START_RECORDING',
    tabId: 7,
    originPattern: 'https://admin.shopify.com/*',
  });

  const writes = Array.from({ length: 40 }, (_, index) =>
    h.dispatch(
      { type: 'NOVA_RECORD_STEP', step: { type: 'click', id: index } },
      { tab: { id: 7 } },
    ),
  );
  const stop = h.dispatch({ type: 'NOVA_STOP_RECORDING' });
  await Promise.all([...writes, stop]);

  assert.equal(h.localData.novaMacroPocLast.length, 40);
  assert.deepEqual(
    h.localData.novaMacroPocLast.map((step) => step.id),
    Array.from({ length: 40 }, (_, index) => index),
  );
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
  await h.dispatch(
    { type: 'NOVA_RECORD_STEP', step: { type: 'click', id: 'before' } },
    { tab: { id: 5 } },
  );
  h.setContentAvailable(false);
  await h.tabComplete(5);
  await h.dispatch(
    { type: 'NOVA_RECORD_STEP', step: { type: 'click', id: 'after' } },
    { tab: { id: 5 } },
  );
  await h.dispatch({ type: 'NOVA_STOP_RECORDING' });
  assert.deepEqual(h.localData.novaMacroPocLast.map((step) => step.id), ['before', 'after']);
});

test('replay executes normal steps exactly once and completes', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [
    { type: 'click', id: 'a' },
    { type: 'click', id: 'b' },
    { type: 'click', id: 'c' },
  ];
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

test('AI_REVIEW is terminal and later steps do not execute', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [{ id: 'ambiguous' }, { id: 'must-not-run' }];
  const executed = [];
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      return { ok: false, status: 'AI_REVIEW', index: message.index };
    }
    return { ok: true, url: 'https://a.test/' };
  });
  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId: 9 });
  await h.waitFor(async () => (await getSession(h)).mode === 'AI_REVIEW');
  assert.deepEqual(executed, [0]);
});

test('navigation step cannot race the next step on the old document', async () => {
  const h = createHarness();
  h.localData.novaMacroPocLast = [{ id: 'navigate' }, { id: 'after-nav' }];
  const executed = [];
  h.setCurrentUrl('https://a.test/orders');
  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      if (message.index === 0) {
        return {
          ok: true,
          status: 'CLICKED',
          index: 0,
          mayNavigate: true,
          urlBefore: 'https://a.test/orders',
        };
      }
      return { ok: true, status: 'CLICKED', index: 1, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') {
      return { ok: true, url: 'https://a.test/orders' };
    }
    return { ok: true };
  });

  await h.dispatch({ type: 'NOVA_START_REPLAY', tabId: 10 });
  await h.waitFor(() => executed.length >= 1);
  await sleep(40);
  assert.deepEqual(executed, [0], 'next step must not run while URL/document is unchanged');

  h.setContentResponder(async (message) => {
    if (message.type === 'NOVA_EXECUTE_STEP') {
      executed.push(message.index);
      return { ok: true, status: 'CLICKED', index: message.index, mayNavigate: false };
    }
    if (message.type === 'NOVA_CONTENT_STATE') {
      return { ok: true, url: 'https://a.test/orders/1001' };
    }
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
      if (message.index === 0) {
        return {
          ok: true,
          status: 'CLICKED',
          index: 0,
          mayNavigate: true,
          urlBefore: 'https://a.test/start',
        };
      }
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
  const paused = await getSession(h);
  assert.equal(paused.needsSiteAccess, true);
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
  await h.dispatch({
    type: 'NOVA_RESUME_CURRENT',
    tabId: 12,
    originPattern: 'https://b.test/*',
  });
  await h.waitFor(async () => (await getSession(h)).mode === 'COMPLETED');
  assert.deepEqual(executed, [0, 1]);
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
