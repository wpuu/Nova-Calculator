const output = document.getElementById('output');

async function activeTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) throw new Error('No active tab.');
  return tab;
}

function originPatternFor(url) {
  const parsed = new URL(url);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('Nova Macro can only run on http/https pages.');
  }
  return `${parsed.origin}/*`;
}

async function ensureCurrentSitePermission(tab) {
  const originPattern = originPatternFor(tab.url);
  const already = await chrome.permissions.contains({ origins: [originPattern] });
  if (already) return originPattern;
  const granted = await chrome.permissions.request({ origins: [originPattern] });
  if (!granted) throw new Error(`Site access was not granted for ${originPattern}`);
  return originPattern;
}

async function callBackground(message) {
  const response = await chrome.runtime.sendMessage(message);
  if (response?.ok === false && response?.error) throw new Error(response.error);
  return response;
}

function show(value) {
  output.textContent = JSON.stringify(value ?? null, null, 2);
}

async function withUiLock(task) {
  const buttons = [...document.querySelectorAll('button')];
  buttons.forEach((button) => { button.disabled = true; });
  output.textContent = 'Working…';
  try {
    show(await task());
  } catch (error) {
    output.textContent = `ERROR: ${error?.message || error}`;
  } finally {
    buttons.forEach((button) => { button.disabled = false; });
  }
}

document.getElementById('start').addEventListener('click', () => withUiLock(async () => {
  const tab = await activeTab();
  const originPattern = await ensureCurrentSitePermission(tab);
  return callBackground({
    type: 'NOVA_START_RECORDING',
    tabId: tab.id,
    originPattern,
  });
}));

document.getElementById('stop').addEventListener('click', () => withUiLock(async () => {
  return callBackground({ type: 'NOVA_STOP_RECORDING' });
}));

document.getElementById('replay').addEventListener('click', () => withUiLock(async () => {
  const tab = await activeTab();
  await ensureCurrentSitePermission(tab);
  return callBackground({ type: 'NOVA_START_REPLAY', tabId: tab.id });
}));

document.getElementById('grant').addEventListener('click', () => withUiLock(async () => {
  const tab = await activeTab();
  const originPattern = await ensureCurrentSitePermission(tab);
  return callBackground({
    type: 'NOVA_RESUME_CURRENT',
    tabId: tab.id,
    originPattern,
  });
}));

document.getElementById('state').addEventListener('click', () => withUiLock(async () => {
  return callBackground({ type: 'NOVA_GET_SESSION' });
}));
