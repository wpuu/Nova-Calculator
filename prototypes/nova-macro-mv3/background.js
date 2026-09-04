'use strict';

const SESSION_KEY = 'novaMacroPocSession';
const SAVED_MACRO_KEY = 'novaMacroPocLast';

const emptySession = () => ({
  mode: 'IDLE',
  tabId: null,
  originPattern: null,
  steps: [],
  replayIndex: 0,
  replayInFlight: false,
  replayWaitingForDocument: false,
  lastResult: null,
  error: null,
  updatedAt: Date.now(),
});

async function getSession() {
  const stored = await chrome.storage.session.get(SESSION_KEY);
  return stored[SESSION_KEY] || emptySession();
}

async function setSession(next) {
  next.updatedAt = Date.now();
  await chrome.storage.session.set({ [SESSION_KEY]: next });
  return next;
}

async function patchSession(patch) {
  const current = await getSession();
  return setSession({ ...current, ...patch });
}

async function injectRuntime(tabId) {
  await chrome.scripting.executeScript({
    target: { tabId },
    files: ['semantic-matcher.js', 'content.js'],
  });
}

async function sendToTab(tabId, message) {
  try {
    return await chrome.tabs.sendMessage(tabId, message);
  } catch (firstError) {
    await injectRuntime(tabId);
    return chrome.tabs.sendMessage(tabId, message);
  }
}

async function startRecording({ tabId, originPattern }) {
  const session = await setSession({
    ...emptySession(),
    mode: 'RECORDING',
    tabId,
    originPattern,
  });
  await injectRuntime(tabId);
  await sendToTab(tabId, { type: 'NOVA_SET_RECORDING', recording: true });
  return session;
}

async function appendStep(step, sender) {
  const session = await getSession();
  if (session.mode !== 'RECORDING') return { ok: false, ignored: true };
  if (sender?.tab?.id !== session.tabId) return { ok: false, ignored: true };
  const steps = [...session.steps, step];
  await patchSession({ steps });
  return { ok: true, count: steps.length };
}

async function stopRecording() {
  const session = await getSession();
  if (session.tabId != null) {
    try {
      await sendToTab(session.tabId, { type: 'NOVA_SET_RECORDING', recording: false });
    } catch {
      // Page may be navigating; background state is authoritative.
    }
  }
  await chrome.storage.local.set({ [SAVED_MACRO_KEY]: session.steps || [] });
  return patchSession({
    mode: 'SAVED',
    replayIndex: 0,
    replayInFlight: false,
    replayWaitingForDocument: false,
  });
}

async function finishReplay(status, lastResult = null) {
  return patchSession({
    mode: status,
    replayInFlight: false,
    replayWaitingForDocument: false,
    lastResult,
  });
}

async function runReplayStep() {
  let session = await getSession();
  if (session.mode !== 'REPLAYING' || session.replayInFlight || session.tabId == null) return;

  if (session.replayIndex >= (session.steps || []).length) {
    await finishReplay('COMPLETED', { ok: true, status: 'COMPLETED' });
    return;
  }

  const index = session.replayIndex;
  const step = session.steps[index];
  session = await patchSession({ replayInFlight: true, replayWaitingForDocument: false });

  let result;
  try {
    result = await sendToTab(session.tabId, {
      type: 'NOVA_EXECUTE_STEP',
      step,
      index,
    });
  } catch (error) {
    await patchSession({
      replayInFlight: false,
      replayWaitingForDocument: true,
      error: error?.message || String(error),
    });
    return;
  }

  if (!result?.ok) {
    const terminal = result?.status || 'ERROR';
    await finishReplay(terminal, result || { ok: false, status: terminal });
    return;
  }

  await patchSession({
    replayIndex: index + 1,
    replayInFlight: false,
    replayWaitingForDocument: !!result.mayNavigate,
    lastResult: result,
    error: null,
  });

  if (result.mayNavigate) {
    setTimeout(() => {
      runReplayStep().catch(() => {});
    }, 700);
    return;
  }

  setTimeout(() => {
    runReplayStep().catch(() => {});
  }, 180);
}

async function startReplay({ tabId }) {
  const stored = await chrome.storage.local.get(SAVED_MACRO_KEY);
  const steps = stored[SAVED_MACRO_KEY] || [];
  const session = await setSession({
    ...emptySession(),
    mode: 'REPLAYING',
    tabId,
    steps,
    replayIndex: 0,
  });
  await injectRuntime(tabId);
  setTimeout(() => runReplayStep().catch(() => {}), 0);
  return session;
}

async function resumeAfterNavigation(tabId) {
  const session = await getSession();
  if (session.tabId !== tabId) return session;

  if (session.mode === 'RECORDING') {
    try {
      await injectRuntime(tabId);
      await sendToTab(tabId, { type: 'NOVA_SET_RECORDING', recording: true });
      return patchSession({ error: null });
    } catch (error) {
      return patchSession({ error: error?.message || String(error) });
    }
  }

  if (session.mode === 'REPLAYING') {
    await patchSession({ replayInFlight: false, replayWaitingForDocument: false });
    try {
      await injectRuntime(tabId);
      await runReplayStep();
      return getSession();
    } catch (error) {
      return patchSession({
        replayInFlight: false,
        replayWaitingForDocument: true,
        error: error?.message || String(error),
      });
    }
  }

  return session;
}

async function resumeCurrent({ tabId, originPattern }) {
  const session = await getSession();
  if (!['RECORDING', 'REPLAYING'].includes(session.mode)) {
    return { ok: false, error: 'NO_ACTIVE_MACRO_SESSION' };
  }
  await patchSession({ tabId, originPattern, error: null });
  return resumeAfterNavigation(tabId);
}

chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status !== 'complete') return;
  resumeAfterNavigation(tabId).catch(() => {});
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  (async () => {
    switch (message?.type) {
      case 'NOVA_START_RECORDING':
        return startRecording(message);
      case 'NOVA_RECORD_STEP':
        return appendStep(message.step, sender);
      case 'NOVA_STOP_RECORDING':
        return stopRecording();
      case 'NOVA_START_REPLAY':
        return startReplay(message);
      case 'NOVA_RESUME_CURRENT':
        return resumeCurrent(message);
      case 'NOVA_GET_SESSION':
        return getSession();
      case 'NOVA_CLEAR_SESSION':
        return setSession(emptySession());
      default:
        return { ok: false, error: 'UNKNOWN_MESSAGE' };
    }
  })().then(sendResponse).catch((error) => {
    sendResponse({ ok: false, error: error?.message || String(error) });
  });
  return true;
});
