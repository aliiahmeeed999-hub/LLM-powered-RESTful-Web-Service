// REST client for the Darija Translator web service (shared by the side panel).

export const DEFAULT_SETTINGS = Object.freeze({
  serverUrl: "http://localhost:9080/translator/api",
  username: "ali",
  password: "darija2026",
  sourceLang: "auto",
  script: "arabic",          // "arabic" | "latin"
  speechLang: "en-US",       // language you speak into the mic
  autoTranslate: true,       // translate as soon as a selection arrives
  autoCopy: true,            // copy the translation to the clipboard
  autoSpeak: false,          // read the translation aloud automatically
});

export async function loadSettings() {
  const stored = await chrome.storage.local.get("settings");
  return { ...DEFAULT_SETTINGS, ...(stored.settings || {}) };
}

export async function saveSettings(settings) {
  await chrome.storage.local.set({ settings });
}

/** UTF-8 safe base64 (btoa alone breaks on non-Latin-1 characters). */
export function base64(text) {
  const bytes = new TextEncoder().encode(text);
  let binary = "";
  bytes.forEach((b) => { binary += String.fromCharCode(b); });
  return btoa(binary);
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function baseUrl(settings) {
  return settings.serverUrl.replace(/\/+$/, "");
}

/** GET /health (public). */
export async function checkHealth(settings, timeoutMs = 4000) {
  const res = await fetch(`${baseUrl(settings)}/health`, {
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new ApiError(res.status, `Health check failed (HTTP ${res.status})`);
  return res.json();
}

/** POST /translate (Basic auth) -> { translation, sourceLang, provider, model, cached, ... } */
export async function translate(settings, text, overrides = {}) {
  const body = {
    text,
    sourceLang: overrides.sourceLang ?? settings.sourceLang,
    script: overrides.script ?? settings.script,
  };

  let res;
  try {
    res = await fetch(`${baseUrl(settings)}/translate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Basic ${base64(`${settings.username}:${settings.password}`)}`,
      },
      body: JSON.stringify(body),
      credentials: "omit",
      signal: AbortSignal.timeout(90000),
    });
  } catch (err) {
    if (err.name === "TimeoutError") throw new ApiError(0, "The server took too long to answer.");
    throw new ApiError(0, `Cannot reach the server at ${settings.serverUrl}. Is it running?`);
  }

  let payload = null;
  try {
    payload = await res.json();
  } catch {
    /* non-JSON body */
  }

  if (!res.ok) {
    const fallback = {
      401: "Wrong username or password (check Settings).",
      403: "This user is not allowed to translate.",
      429: "The LLM quota is exhausted, try again in a moment.",
    }[res.status] || `Server error (HTTP ${res.status}).`;
    throw new ApiError(res.status, (payload && payload.message) || fallback);
  }
  return payload;
}
