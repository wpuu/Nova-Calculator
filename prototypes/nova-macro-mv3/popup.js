const output = document.getElementById('output');

async function activeTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) throw new Error('No active tab.');
  return tab;
}

async function ensureInjected(tabId) {
  await chrome.scripting.executeScript({ target: { tabId }, files: ['semantic-matcher.js'] });
  await chrome.scripting.executeScript({ target: { tabId }, files: ['content.js'] });
}

async function invoke(method) {
  try {
    output.textContent = 'Working…';
    const tab = await activeTab();
    await ensureInjected(tab.id);
    const [result] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: async (methodName) => {
        if (!globalThis.NovaMacroPoc?.[methodName]) throw new Error(`Missing method: ${methodName}`);
        return await globalThis.NovaMacroPoc[methodName]();
      },
      args: [method],
    });
    output.textContent = JSON.stringify(result?.result ?? null, null, 2);
  } catch (error) {
    output.textContent = `ERROR: ${error?.message || error}`;
  }
}

document.getElementById('start').addEventListener('click', () => invoke('startRecording'));
document.getElementById('stop').addEventListener('click', () => invoke('stopRecording'));
document.getElementById('replay').addEventListener('click', () => invoke('replayLast'));
document.getElementById('state').addEventListener('click', () => invoke('getState'));
