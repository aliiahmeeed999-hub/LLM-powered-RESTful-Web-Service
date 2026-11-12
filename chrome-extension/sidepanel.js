import { loadSettings, saveSettings, translate, checkHealth, ApiError } from "./api.js";
import { speak, stopSpeaking, listen, stopListening, speechRecognitionSupported } from "./speech.js";

const $ = (id) => document.getElementById(id);
const els = {
  status: $("status"), statusText: $("statusText"),
  sourceLang: $("sourceLang"), input: $("input"), charCount: $("charCount"),
  clearBtn: $("clearBtn"), micBtn: $("micBtn"),
  translateBtn: $("translateBtn"), voiceBtn: $("voiceBtn"),
  resultCard: $("resultCard"), output: $("output"), meta: $("meta"),
  copyBtn: $("copyBtn"), speakBtn: $("speakBtn"), stopBtn: $("stopBtn"),
  error: $("error"), toast: $("toast"),
  history: $("history"), historyCount: $("historyCount"), clearHistoryBtn: $("clearHistoryBtn"),
  settingsBox: $("settingsBox"), settingsForm: $("settingsForm"), testBtn: $("testBtn"),
};

const HISTORY_MAX = 30;
let settings;
let lastResult = null;
let lastSelectionTs = 0;
let busy = false;

// ------------------------------------------------------------------ init

init();

async function init() {
  settings = await loadSettings();
  applySettingsToUi();
  bindEvents();
  renderHistory();
  refreshHealth();

  // A selection may already be waiting (the panel was opened by the context menu).
  const { pendingSelection } = await chrome.storage.session.get("pendingSelection");
  if (pendingSelection) receiveSelection(pendingSelection);

  // ...or arrive later while the panel is open.
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === "session" && changes.pendingSelection && changes.pendingSelection.newValue) {
      receiveSelection(changes.pendingSelection.newValue);
    }
  });

  if (!speechRecognitionSupported) {
    els.micBtn.hidden = true;
    els.voiceBtn.hidden = true;
  }
}

function applySettingsToUi() {
  els.sourceLang.value = settings.sourceLang;
  setScript(settings.script, false);
  const form = els.settingsForm.elements;
  form.serverUrl.value = settings.serverUrl;
  form.username.value = settings.username;
  form.password.value = settings.password;
  form.speechLang.value = settings.speechLang;
  form.autoTranslate.checked = settings.autoTranslate;
  form.autoCopy.checked = settings.autoCopy;
  form.autoSpeak.checked = settings.autoSpeak;
}

function bindEvents() {
  els.translateBtn.addEventListener("click", () => runTranslation());
  els.input.addEventListener("input", updateCharCount);
  els.input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      runTranslation();
    }
  });
  els.clearBtn.addEventListener("click", () => {
    els.input.value = "";
    updateCharCount();
    els.input.focus();
  });

  els.sourceLang.addEventListener("change", () => persist({ sourceLang: els.sourceLang.value }));
  document.querySelectorAll(".segmented button").forEach((btn) =>
    btn.addEventListener("click", () => {
      setScript(btn.dataset.script, true);
      if (els.input.value.trim() && lastResult) runTranslation();
    }));

  els.copyBtn.addEventListener("click", async () => {
    if (lastResult && (await copyToClipboard(lastResult.translation))) toast("Copied ✓");
  });
  els.speakBtn.addEventListener("click", () => lastResult && readAloud(lastResult.translation));
  els.stopBtn.addEventListener("click", () => stopSpeaking());

  els.micBtn.addEventListener("click", () => dictate(false));
  els.voiceBtn.addEventListener("click", () => dictate(true));

  els.clearHistoryBtn.addEventListener("click", async () => {
    await chrome.storage.local.set({ history: [] });
    renderHistory();
  });

  els.settingsForm.addEventListener("submit", onSaveSettings);
  els.testBtn.addEventListener("click", testConnection);
}

// ---------------------------------------------------------- selection

function receiveSelection(selection) {
  if (!selection || !selection.text || selection.ts <= lastSelectionTs) return;
  lastSelectionTs = selection.ts;
  els.input.value = selection.text;        // the selected text is pasted automatically
  updateCharCount();
  if (settings.autoTranslate) runTranslation();
}

// ---------------------------------------------------------- translate

async function runTranslation(textOverride) {
  const text = (textOverride ?? els.input.value).trim();
  if (!text) {
    showError("Type, paste, speak or select some text first.");
    return null;
  }
  if (busy) return null;
  busy = true;
  hideError();
  els.translateBtn.disabled = true;
  els.resultCard.hidden = false;
  els.output.className = "output loading";
  els.output.dir = "ltr";
  els.output.textContent = "Translating…";
  els.meta.textContent = "";

  try {
    const result = await translate(settings, text);
    lastResult = result;
    showResult(result);
    addToHistory(result);
    if (settings.autoCopy) copyToClipboard(result.translation);
    if (settings.autoSpeak) readAloud(result.translation);
    setStatus(true, "Online");
    return result;
  } catch (err) {
    els.resultCard.hidden = true;
    showError(err instanceof ApiError ? err.message : `Unexpected error: ${err.message}`);
    if (err instanceof ApiError && err.status === 401) els.settingsBox.open = true;
    if (err instanceof ApiError && err.status === 0) setStatus(false, "Offline");
    return null;
  } finally {
    busy = false;
    els.translateBtn.disabled = false;
  }
}

function showResult(result) {
  els.output.className = "output";
  els.output.dir = result.script === "latin" ? "ltr" : "rtl";
  els.output.textContent = result.translation;
  const from = result.sourceLang === "auto" ? "auto-detected" : result.sourceLang;
  els.meta.textContent = `${from} · ${result.model}${result.cached ? " · cached" : ""} · ${result.elapsedMs} ms`;
}

// ------------------------------------------------------------- speech

async function readAloud(text) {
  els.stopBtn.hidden = false;
  await speak(text, settings.script, {
    onEnd: () => { els.stopBtn.hidden = true; },
  });
}

/** Microphone -> text box. With `voiceToVoice`, also translate and read the result aloud. */
async function dictate(voiceToVoice) {
  const button = voiceToVoice ? els.voiceBtn : els.micBtn;
  if (button.classList.contains("listening")) {
    stopListening();
    return;
  }
  hideError();
  stopSpeaking();
  button.classList.add("listening");
  try {
    const transcript = await listen(settings.speechLang, {
      onPartial: (partial) => {
        els.input.value = partial;
        updateCharCount();
      },
    });
    button.classList.remove("listening");
    if (!transcript) {
      showError("I didn't catch that - try again a bit closer to the microphone.");
      return;
    }
    els.input.value = transcript;
    updateCharCount();
    if (voiceToVoice) {
      const result = await runTranslation(transcript);
      if (result && !settings.autoSpeak) readAloud(result.translation);
    }
  } catch (err) {
    button.classList.remove("listening");
    if (err.message === "not-allowed" || err.message === "service-not-allowed") {
      showError("Microphone access is needed. A tab will open to grant it - then come back and try again.");
      chrome.tabs.create({ url: chrome.runtime.getURL("permission.html") });
    } else if (err.message === "no-speech") {
      showError("No speech detected.");
    } else if (err.message !== "aborted") {
      showError(`Speech recognition error: ${err.message}`);
    }
  }
}

// ------------------------------------------------------------ helpers

function setScript(script, save) {
  settings.script = script;
  document.querySelectorAll(".segmented button").forEach((btn) =>
    btn.setAttribute("aria-checked", String(btn.dataset.script === script)));
  if (save) persist({ script });
}

async function persist(partial) {
  settings = { ...settings, ...partial };
  await saveSettings(settings);
}

function updateCharCount() {
  els.charCount.textContent = `${els.input.value.length} / 5000`;
}

async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // The panel may not have focus; clipboardWrite permission allows execCommand instead.
    const area = document.createElement("textarea");
    area.value = text;
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand("copy");
    area.remove();
    return ok;
  }
}

function showError(message) {
  els.error.textContent = message;
  els.error.hidden = false;
}

function hideError() {
  els.error.hidden = true;
}

let toastTimer;
function toast(message) {
  els.toast.textContent = message;
  els.toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { els.toast.hidden = true; }, 1600);
}

function setStatus(up, text) {
  els.status.classList.toggle("up", up);
  els.status.classList.toggle("down", !up);
  els.statusText.textContent = text;
}

async function refreshHealth() {
  try {
    const health = await checkHealth(settings);
    setStatus(true, health.llmConfigured ? "Online" : "No LLM key");
    els.status.title = `${health.provider} / ${health.model}`;
    return health;
  } catch {
    setStatus(false, "Offline");
    return null;
  }
}

// ------------------------------------------------------------ history

async function addToHistory(result) {
  const { history = [] } = await chrome.storage.local.get("history");
  const entry = { src: result.original, dst: result.translation, script: result.script, ts: Date.now() };
  const next = [entry, ...history.filter((h) => !(h.src === entry.src && h.script === entry.script))]
    .slice(0, HISTORY_MAX);
  await chrome.storage.local.set({ history: next });
  renderHistory(next);
}

async function renderHistory(list) {
  const history = list || (await chrome.storage.local.get("history")).history || [];
  els.historyCount.textContent = history.length ? `(${history.length})` : "";
  els.history.replaceChildren(...history.map((h) => {
    const li = document.createElement("li");
    const src = document.createElement("div");
    src.className = "src";
    src.textContent = h.src;
    const dst = document.createElement("div");
    dst.className = "dst";
    dst.dir = h.script === "latin" ? "ltr" : "rtl";
    dst.textContent = h.dst;
    li.append(src, dst);
    li.title = "Click to reuse";
    li.addEventListener("click", () => {
      els.input.value = h.src;
      updateCharCount();
      lastResult = { translation: h.dst, script: h.script };
      els.resultCard.hidden = false;
      els.output.className = "output";
      els.output.dir = dst.dir;
      els.output.textContent = h.dst;
      els.meta.textContent = "from history";
    });
    return li;
  }));
}

// ----------------------------------------------------------- settings

async function onSaveSettings(event) {
  event.preventDefault();
  const form = els.settingsForm.elements;
  const serverUrl = form.serverUrl.value.trim().replace(/\/+$/, "");

  // Hosts other than localhost need an extra (optional) host permission.
  // permissions.request() must run inside the click, so it comes first.
  let origin;
  try {
    origin = new URL(serverUrl).origin + "/*";
  } catch {
    showError("The server URL is not valid.");
    return;
  }
  const granted = await chrome.permissions.request({ origins: [origin] }).catch(() => false);
  if (!granted) {
    showError(`Chrome did not grant access to ${origin}. The extension cannot call that server.`);
    return;
  }

  await persist({
    serverUrl,
    username: form.username.value.trim(),
    password: form.password.value,
    speechLang: form.speechLang.value,
    autoTranslate: form.autoTranslate.checked,
    autoCopy: form.autoCopy.checked,
    autoSpeak: form.autoSpeak.checked,
  });
  hideError();
  toast("Settings saved");
  refreshHealth();
}

async function testConnection() {
  hideError();
  const health = await refreshHealth();
  if (!health) {
    showError(`Cannot reach ${settings.serverUrl}/health`);
    return;
  }
  try {
    await translate(settings, "Hello", { sourceLang: "en" });
    toast(`Connected as ${settings.username} ✓ (${health.provider})`);
  } catch (err) {
    showError(err.message);
  }
}
