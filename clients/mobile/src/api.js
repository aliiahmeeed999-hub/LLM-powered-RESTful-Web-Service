// REST client for the Darija Translator web service (React Native / Expo).

import { Platform } from "react-native";

// Android emulators reach the host machine through 10.0.2.2; iOS simulators through localhost.
// On a real phone, use your computer's LAN IP (e.g. http://192.168.1.20:9080/translator/api).
export const DEFAULT_SETTINGS = {
  serverUrl:
    Platform.OS === "android"
      ? "http://10.0.2.2:9080/translator/api"
      : "http://localhost:9080/translator/api",
  username: "ali",
  password: "darija2026",
  script: "arabic",
  sourceLang: "auto",
};

const B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/** Encodes a string as UTF-8 bytes (no dependency on TextEncoder). */
function utf8Bytes(text) {
  const bytes = [];
  for (const ch of text) {
    const cp = ch.codePointAt(0);
    if (cp < 0x80) bytes.push(cp);
    else if (cp < 0x800) bytes.push(0xc0 | (cp >> 6), 0x80 | (cp & 63));
    else if (cp < 0x10000) bytes.push(0xe0 | (cp >> 12), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63));
    else bytes.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 63), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63));
  }
  return bytes;
}

/** UTF-8 safe base64 encoder (does not depend on btoa being available). */
export function base64(text) {
  const bytes = utf8Bytes(text);
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const rest = bytes.length - i;
    const n = (bytes[i] << 16) | ((rest > 1 ? bytes[i + 1] : 0) << 8) | (rest > 2 ? bytes[i + 2] : 0);
    out += B64[(n >> 18) & 63] + B64[(n >> 12) & 63];
    out += rest > 1 ? B64[(n >> 6) & 63] : "=";
    out += rest > 2 ? B64[n & 63] : "=";
  }
  return out;
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function withTimeout(ms) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ms);
  return { signal: controller.signal, done: () => clearTimeout(timer) };
}

const base = (settings) => settings.serverUrl.trim().replace(/\/+$/, "");

export async function checkHealth(settings) {
  const t = withTimeout(5000);
  try {
    const res = await fetch(`${base(settings)}/health`, { signal: t.signal });
    if (!res.ok) throw new ApiError(res.status, `HTTP ${res.status}`);
    return await res.json();
  } finally {
    t.done();
  }
}

export async function translate(settings, text) {
  const body = { text, script: settings.script };
  if (settings.sourceLang && settings.sourceLang !== "auto") body.sourceLang = settings.sourceLang;

  const t = withTimeout(90000);
  let res;
  try {
    res = await fetch(`${base(settings)}/translate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Basic ${base64(`${settings.username}:${settings.password}`)}`,
      },
      body: JSON.stringify(body),
      signal: t.signal,
    });
  } catch (err) {
    t.done();
    if (err.name === "AbortError") throw new ApiError(0, "The server took too long to answer.");
    throw new ApiError(0, `Cannot reach ${settings.serverUrl}. Check the URL in Settings and that your phone is on the same Wi-Fi.`);
  }
  t.done();

  let payload = null;
  try {
    payload = await res.json();
  } catch {
    // non-JSON error body
  }
  if (!res.ok) {
    const message =
      res.status === 401
        ? "Wrong username or password."
        : (payload && payload.message) || `Server error (HTTP ${res.status}).`;
    throw new ApiError(res.status, message);
  }
  return payload;
}
