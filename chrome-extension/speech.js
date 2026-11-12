// Speech helpers: text-to-speech (read aloud) and speech-to-text (microphone).
// Both use the Web Speech API built into Chrome; no extra service is needed.

/** Picks the best voice: Moroccan Arabic, then any Arabic voice (or French for Latin-script Darija). */
function pickVoice(script) {
  const voices = speechSynthesis.getVoices();
  const prefs = script === "latin" ? ["fr-MA", "fr-FR", "fr"] : ["ar-MA", "ar-SA", "ar-EG", "ar"];
  for (const pref of prefs) {
    const v = voices.find((voice) => voice.lang.toLowerCase().startsWith(pref.toLowerCase()));
    if (v) return v;
  }
  return null;
}

// getVoices() is empty until the "voiceschanged" event fires the first time.
const voicesReady = new Promise((resolve) => {
  if (speechSynthesis.getVoices().length) return resolve();
  speechSynthesis.addEventListener("voiceschanged", () => resolve(), { once: true });
  setTimeout(resolve, 1500);
});

/** Reads `text` aloud. Resolves when speech ends. */
export async function speak(text, script = "arabic", { onStart, onEnd } = {}) {
  await voicesReady;
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  const voice = pickVoice(script);
  if (voice) utterance.voice = voice;
  utterance.lang = voice ? voice.lang : script === "latin" ? "fr-FR" : "ar-MA";
  utterance.rate = 0.95;

  return new Promise((resolve) => {
    utterance.onstart = () => onStart && onStart();
    utterance.onend = utterance.onerror = () => {
      if (onEnd) onEnd();
      resolve();
    };
    speechSynthesis.speak(utterance);
  });
}

export function stopSpeaking() {
  speechSynthesis.cancel();
}

export function hasArabicVoice() {
  return pickVoice("arabic") !== null;
}

const Recognition = self.SpeechRecognition || self.webkitSpeechRecognition;

export const speechRecognitionSupported = Boolean(Recognition);

/**
 * Listens to the microphone once and resolves with the transcript.
 * `onPartial` receives interim results so the text box fills while the user speaks.
 * Rejects with Error("not-allowed") when microphone permission is missing.
 */
export function listen(lang, { onPartial } = {}) {
  return new Promise((resolve, reject) => {
    if (!Recognition) return reject(new Error("unsupported"));
    const rec = new Recognition();
    rec.lang = lang;
    rec.interimResults = true;
    rec.continuous = false;
    rec.maxAlternatives = 1;

    let finalText = "";
    rec.onresult = (event) => {
      let interim = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const chunk = event.results[i][0].transcript;
        if (event.results[i].isFinal) finalText += chunk;
        else interim += chunk;
      }
      if (onPartial) onPartial((finalText + interim).trim());
    };
    rec.onerror = (event) => reject(new Error(event.error || "speech-error"));
    rec.onend = () => resolve(finalText.trim());
    listen.current = rec;
    rec.start();
  });
}

export function stopListening() {
  if (listen.current) listen.current.stop();
}
