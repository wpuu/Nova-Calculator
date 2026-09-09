'use strict';

const SESSION_KEY = 'novaMacroPocSession';
const SAVED_MACRO_KEY = 'novaMacroPocLast';
let stepWriteQueue = Promise.resolve();
let navigationProbeTimer = null;

const emptySession = () => ({
  mode: 'IDLE',
  tabId: null,
  originPattern: null,
  steps: [],
  replayIndex: 0,
  replayInFlight: false,
  replayWaitingForDocument: false,
  waitingFromUrl: null,
  needsSiteAccess: false,
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

function clearNavigationProbe() {
  if (navigationProbeTimer != null) {
    clearTimeout(navigationProbeTimer);
    navigationProbeTimer = null;
  }
}

async function injectRuntime(tabId) {
  await chrome.scripting.executeScript({
    target: { tabId },
    files: ['semantic-matcher.js', 'content.js'],
  });
}

async function sendToTab(tabId, message) {
  // Never auto-retry a message that may have side effects. The caller must
  // explicitly inject first and then send once. If delivery becomes uncertain,
  // pause fail-closed instead of risking a duplicate click/input.
  return chrome.tabs.sendMessage(tabId, message);
}

async function startRecording({ tabId, originPattern }) {
  clearNavigationProbe();
  stepWriteQueue = Promise.resolve();
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

function appendStep(step, sender) {
  const operation = stepWriteQueue.then(async () => {
    const session = await getSession();
    if (session.mode !== 'RECORDING') return { ok: false, ignored: true };
    if (sender?.tab?.id !== session.tabId) return { ok: false, ignored: true };
    const steps = [...session.steps, step];
    await patchSession({ steps, error: null });
    return { ok: true, count: steps.length };
  });
  stepWriteQueue = operation.catch(() => {});
  return operation;
}

async function stopRecording() {
  clearNavigationProbe();
  await stepWriteQueue;
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
    waitingFromUrl: null,
    needsSiteAccess: false,
  });
}

async function finishReplay(status, lastResult = null) {
  clearNavigationProbe();
  return patchSession({
    mode: status,
    replayInFlight: false,
    replayWaitingForDocument: false,
    waitingFromUrl: null,
    needsSiteAccess: false,
    lastResult,
  });
}

function scheduleNavigationProbe(delayMs = 250) {
  clearNavigationProbe();
  navigationProbeTimer = setTimeout(() => {
    navigationProbeTimer = null;
    probeNavigationProgress().catch(() => {});
  }, delayMs);
}

async function probeNavigationProgress() {
  const session = await getSession();
  if (
    session.mode !== 'REPLAYING' ||
    !session.replayWaitingForDocument ||
    session.tabId == null
  ) return;

  try {
    const state = await chrome.tabs.sendMessage(session.tabId, { type: 'NOVA_CONTENT_STATE' });
    const currentUrl = state?.url || null;
    if (currentUrl && session.waitingFromUrl && currentUrl !== session.waitingFromUrl) {
      await patchSession({
        replayWaitingForDocument: false,
        waitingFromUrl: null,
        replayInFlight: false,
        error: null,
      });
      await runReplayStep();
      return;
    }
  } catch {
    // A full navigation often destroys the old content context. tabs.onUpdated
    // will resume after the new document reaches complete.
  }

  scheduleNavigationProbe(250);
}

async function continueAfterSuccessfulStep(session, index, result) {
  if (result.mayNavigate) {
    await patchSession({
      mode: 'REPLAYING',
      replayIndex: index + 1,
      replayInFlight: false,
      replayWaitingForDocument: true,
      waitingFromUrl: result.urlBefore || null,
      needsSiteAccess: false,
      lastResult: result,
      error: null,
    });
    scheduleNavigationProbe(250);
    return getSession();
  }

  await patchSession({
    mode: 'REPLAYING',
    replayIndex: index + 1,
    replayInFlight: false,
    replayWaitingForDocument: false,
    waitingFromUrl: null,
    needsSiteAccess: false,
    lastResult: result,
    error: null,
  });

  setTimeout(() => {
    runReplayStep().catch(() => {});
  }, 180);
  return getSession();
}

async function runReplayStep() {
  let session = await getSession();
  if (
    session.mode !== 'REPLAYING' ||
    session.replayInFlight ||
    session.replayWaitingForDocument ||
    session.tabId == null
  ) return;

  if (session.replayIndex >= (session.steps || []).length) {
    await finishReplay('COMPLETED', { ok: true, status: 'COMPLETED' });
    return;
  }

  const index = session.replayIndex;
  const step = session.steps[index];
  session = await patchSession({
    replayInFlight: true,
    needsSiteAccess: false,
  });

  let result;
  try {
    // Content runtime is injected at replay start and after each navigation.
    // Delivery here is deliberately at-most-once.
    result = await sendToTab(session.tabId, {
      type: 'NOVA_EXECUTE_STEP',
      step,
      index,
    });
  } catch (error) {
    await patchSession({
      replayInFlight: false,
      replayWaitingForDocument: false,
      needsSiteAccess: true,
      error: error?.message || String(error),
    });
    return;
  }

  if (!result?.ok) {
    const terminal = result?.status || 'ERROR';
    await finishReplay(terminal, result || { ok: false, status: terminal });
    return;
  }

  await continueAfterSuccessfulStep(session, index, result);
}

async function startReplay({ tabId }) {
  clearNavigationProbe();
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
      return patchSession({ error: null, needsSiteAccess: false });
    } catch (error) {
      return patchSession({
        error: error?.message || String(error),
        needsSiteAccess: true,
      });
    }
  }

  if (session.mode === 'REPLAYING') {
    if (!session.replayWaitingForDocument && !session.needsSiteAccess) return session;

    clearNavigationProbe();
    try {
      await injectRuntime(tabId);
      await patchSession({
        replayInFlight: false,
        replayWaitingForDocument: false,
        waitingFromUrl: null,
        needsSiteAccess: false,
        error: null,
      });
      await runReplayStep();
      return getSession();
    } catch (error) {
      return patchSession({
        replayInFlight: false,
        replayWaitingForDocument: false,
        needsSiteAccess: true,
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

  await patchSession({
    tabId,
    originPattern,
    error: null,
  });
  return resumeAfterNavigation(tabId);
}

function reviewFromSession(session) {
  return session?.lastResult?.status === 'AI_REVIEW'
    ? session.lastResult.review || null
    : null;
}

async function selectRepairCandidate({ reviewId, candidateId }) {
  const session = await getSession();
  const review = reviewFromSession(session);
  if (session.mode !== 'AI_REVIEW' || !review) {
    return { ok: false, error: 'NO_PENDING_AI_REVIEW' };
  }
  if (review.reviewId !== reviewId) {
    return { ok: false, error: 'STALE_AI_REVIEW' };
  }
  if (!Array.isArray(review.allowedCandidateIds) || !review.allowedCandidateIds.includes(candidateId)) {
    return { ok: false, error: 'INVALID_AI_CANDIDATE' };
  }
  if (session.tabId == null) return { ok: false, error: 'NO_ACTIVE_TAB' };

  const index = session.replayIndex;
  let result;
  try {
    // The model never supplies a selector. It may only echo a candidate ID that
    // the local content runtime minted for the current document and review.
    result = await sendToTab(session.tabId, {
      type: 'NOVA_APPLY_REVIEW_CHOICE',
      reviewId,
      candidateId,
    });
  } catch (error) {
    return finishReplay('ABSTAIN', {
      ok: false,
      status: 'STALE_AI_REVIEW',
      index,
      error: error?.message || String(error),
    });
  }

  if (!result?.ok) {
    const terminal = result?.status === 'REQUIRES_CONFIRMATION'
      ? 'REQUIRES_CONFIRMATION'
      : 'ABSTAIN';
    return finishReplay(terminal, result || { ok: false, status: terminal, index });
  }

  return continueAfterSuccessfulStep(session, index, result);
}

async function abstainRepair({ reviewId }) {
  const session = await getSession();
  const review = reviewFromSession(session);
  if (session.mode !== 'AI_REVIEW' || !review) {
    return { ok: false, error: 'NO_PENDING_AI_REVIEW' };
  }
  if (review.reviewId !== reviewId) {
    return { ok: false, error: 'STALE_AI_REVIEW' };
  }
  return finishReplay('ABSTAIN', {
    ok: false,
    status: 'ABSTAIN',
    index: session.replayIndex,
    reviewId,
  });
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
      case 'NOVA_SELECT_REPAIR_CANDIDATE':
        return selectRepairCandidate(message);
      case 'NOVA_ABSTAIN_REPAIR':
        return abstainRepair(message);
      case 'NOVA_GET_SESSION':
        return getSession();
      case 'NOVA_CLEAR_SESSION':
        clearNavigationProbe();
        return setSession(emptySession());
      default:
        return { ok: false, error: 'UNKNOWN_MESSAGE' };
    }
  })().then(sendResponse).catch((error) => {
    sendResponse({ ok: false, error: error?.message || String(error) });
  });
  return true;
});
