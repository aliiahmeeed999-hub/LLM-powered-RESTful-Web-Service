import { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from "react-native";
import { StatusBar } from "expo-status-bar";
import * as Clipboard from "expo-clipboard";
import * as Speech from "expo-speech";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { ApiError, DEFAULT_SETTINGS, checkHealth, translate } from "./src/api";

const RED = "#c1272d";
const GREEN = "#006233";
const LANGS = [
  { code: "auto", label: "Auto" },
  { code: "en", label: "English" },
  { code: "fr", label: "Français" },
  { code: "es", label: "Español" },
  { code: "ar", label: "العربية" },
];
const SETTINGS_KEY = "darija.settings";
const HISTORY_KEY = "darija.history";

export default function App() {
  const dark = useColorScheme() === "dark";
  const c = dark ? darkColors : lightColors;

  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [text, setText] = useState("");
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [online, setOnline] = useState(null);
  const [history, setHistory] = useState([]);
  const [showSettings, setShowSettings] = useState(false);
  const [notice, setNotice] = useState(null);

  // ---------------------------------------------------------- persistence
  useEffect(() => {
    (async () => {
      const [s, h] = await Promise.all([AsyncStorage.getItem(SETTINGS_KEY), AsyncStorage.getItem(HISTORY_KEY)]);
      const loaded = s ? { ...DEFAULT_SETTINGS, ...JSON.parse(s) } : DEFAULT_SETTINGS;
      setSettings(loaded);
      if (h) setHistory(JSON.parse(h));
      refreshHealth(loaded);
    })();
  }, []);

  const updateSettings = useCallback(async (patch) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch };
      AsyncStorage.setItem(SETTINGS_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  async function refreshHealth(s = settings) {
    try {
      await checkHealth(s);
      setOnline(true);
    } catch {
      setOnline(false);
    }
  }

  function flash(message) {
    setNotice(message);
    setTimeout(() => setNotice(null), 1500);
  }

  // ------------------------------------------------------------- actions
  async function onTranslate(input = text, overrides = {}) {
    const value = input.trim();
    if (!value) {
      setError("Type or paste something to translate.");
      return;
    }
    const effective = { ...settings, ...overrides };
    setError(null);
    setLoading(true);
    Speech.stop();
    try {
      const r = await translate(effective, value);
      setResult(r);
      setOnline(true);
      const entry = { src: r.original, dst: r.translation, script: r.script, ts: Date.now() };
      const next = [entry, ...history.filter((h) => !(h.src === entry.src && h.script === entry.script))].slice(0, 30);
      setHistory(next);
      AsyncStorage.setItem(HISTORY_KEY, JSON.stringify(next));
    } catch (e) {
      setResult(null);
      setError(e instanceof ApiError ? e.message : String(e));
      if (e instanceof ApiError && e.status === 0) setOnline(false);
    } finally {
      setLoading(false);
    }
  }

  async function pasteAndTranslate() {
    const clip = (await Clipboard.getStringAsync()).trim();
    if (!clip) {
      flash("Clipboard is empty");
      return;
    }
    setText(clip);
    onTranslate(clip);
  }

  async function copyResult() {
    if (!result) return;
    await Clipboard.setStringAsync(result.translation);
    flash("Copied ✓");
  }

  async function readAloud() {
    if (!result) return;
    if (speaking) {
      Speech.stop();
      setSpeaking(false);
      return;
    }
    const voices = await Speech.getAvailableVoicesAsync().catch(() => []);
    const latin = result.script === "latin";
    const prefs = latin ? ["fr"] : ["ar-MA", "ar"];
    const voice = prefs.map((p) => voices.find((v) => v.language && v.language.startsWith(p))).find(Boolean);
    setSpeaking(true);
    Speech.speak(result.translation, {
      language: voice ? voice.language : latin ? "fr-FR" : "ar",
      voice: voice ? voice.identifier : undefined,
      rate: 0.95,
      onDone: () => setSpeaking(false),
      onStopped: () => setSpeaking(false),
      onError: () => setSpeaking(false),
    });
  }

  function changeScript(script) {
    updateSettings({ script });
    if (result && text.trim()) onTranslate(text, { script });
  }

  // ---------------------------------------------------------------- UI
  return (
    <KeyboardAvoidingView style={[styles.flex, { backgroundColor: c.bg }]}
      behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <StatusBar style={dark ? "light" : "dark"} />

      <View style={[styles.header, { backgroundColor: c.card }]}>
        <View>
          <Text style={[styles.title, { color: c.text }]}>Darija Translator</Text>
          <Text style={[styles.subtitle, { color: c.muted }]}>Any language → الدارجة المغربية</Text>
        </View>
        <Pressable onPress={() => setShowSettings(true)} style={styles.headerRight} accessibilityLabel="Settings">
          <View style={[styles.dot, { backgroundColor: online == null ? "#aaa" : online ? GREEN : RED }]} />
          <Text style={{ fontSize: 22 }}>⚙️</Text>
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
        <View style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
            {LANGS.map((l) => (
              <Chip key={l.code} label={l.label} active={settings.sourceLang === l.code}
                onPress={() => updateSettings({ sourceLang: l.code })} c={c} />
            ))}
          </ScrollView>

          <TextInput
            style={[styles.input, { color: c.text, backgroundColor: c.bg, borderColor: c.border }]}
            placeholder="Type or paste text to translate…"
            placeholderTextColor={c.muted}
            multiline
            maxLength={5000}
            value={text}
            onChangeText={setText}
          />

          <View style={styles.row}>
            <Segment options={[["arabic", "عربية"], ["latin", "Latin"]]} value={settings.script}
              onChange={changeScript} c={c} />
            <Text style={{ color: c.muted, fontSize: 12 }}>{text.length}/5000</Text>
          </View>

          <View style={styles.row}>
            <Button label={loading ? "Translating…" : "Translate"} onPress={() => onTranslate()} primary
              disabled={loading} style={styles.flex} />
            <Button label="📋 Paste" onPress={pasteAndTranslate} c={c} disabled={loading} />
            {text ? <Button label="✕" onPress={() => { setText(""); setResult(null); }} c={c} /> : null}
          </View>
        </View>

        {error ? (
          <View style={[styles.error, { borderColor: RED }]}>
            <Text style={{ color: c.text }}>{error}</Text>
            {error.includes("reach") || error.includes("password") ? (
              <Pressable onPress={() => setShowSettings(true)}>
                <Text style={{ color: RED, marginTop: 6, fontWeight: "600" }}>Open settings →</Text>
              </Pressable>
            ) : null}
          </View>
        ) : null}

        {loading ? <ActivityIndicator color={RED} style={{ marginVertical: 12 }} /> : null}

        {result && !loading ? (
          <View style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]}>
            <Text style={{ color: GREEN, fontWeight: "700" }}>Darija</Text>
            <Text selectable style={[styles.output, { color: c.text,
              textAlign: result.script === "latin" ? "left" : "right",
              writingDirection: result.script === "latin" ? "ltr" : "rtl" }]}>
              {result.translation}
            </Text>
            <Text style={{ color: c.muted, fontSize: 12 }}>
              {result.sourceLang === "auto" ? "auto-detected" : result.sourceLang} · {result.model} · {result.elapsedMs} ms
              {result.cached ? " · cached" : ""}
            </Text>
            <View style={styles.row}>
              <Button label="📋 Copy" onPress={copyResult} c={c} />
              <Button label={speaking ? "⏹ Stop" : "🔊 Read aloud"} onPress={readAloud} c={c} />
            </View>
          </View>
        ) : null}

        {history.length ? (
          <View style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]}>
            <View style={styles.row}>
              <Text style={{ color: c.text, fontWeight: "700", flex: 1 }}>History</Text>
              <Pressable onPress={() => { setHistory([]); AsyncStorage.removeItem(HISTORY_KEY); }}>
                <Text style={{ color: RED }}>Clear</Text>
              </Pressable>
            </View>
            <FlatList
              scrollEnabled={false}
              data={history}
              keyExtractor={(h) => String(h.ts)}
              renderItem={({ item }) => (
                <Pressable style={[styles.historyItem, { borderColor: c.border }]}
                  onPress={() => {
                    setText(item.src);
                    setResult({ original: item.src, translation: item.dst, script: item.script,
                      sourceLang: "history", model: "saved", elapsedMs: 0, cached: true });
                  }}>
                  <Text numberOfLines={1} style={{ color: c.muted, fontSize: 12 }}>{item.src}</Text>
                  <Text style={{ color: c.text, fontSize: 16,
                    textAlign: item.script === "latin" ? "left" : "right" }}>{item.dst}</Text>
                </Pressable>
              )}
            />
          </View>
        ) : null}
      </ScrollView>

      {notice ? (
        <View style={styles.toast}><Text style={{ color: "#fff" }}>{notice}</Text></View>
      ) : null}

      <SettingsModal visible={showSettings} settings={settings} c={c}
        onClose={() => setShowSettings(false)}
        onSave={(s) => { updateSettings(s); setShowSettings(false); refreshHealth({ ...settings, ...s }); flash("Saved"); }} />
    </KeyboardAvoidingView>
  );
}

// -------------------------------------------------------------- components

function Button({ label, onPress, primary, disabled, style, c }) {
  return (
    <Pressable onPress={onPress} disabled={disabled}
      style={({ pressed }) => [styles.button,
        primary ? { backgroundColor: RED } : { borderWidth: 1, borderColor: c ? c.border : "#ddd" },
        { opacity: disabled ? 0.6 : pressed ? 0.8 : 1 }, style]}>
      <Text style={{ color: primary ? "#fff" : c ? c.text : "#000", fontWeight: primary ? "700" : "500", textAlign: "center" }}>
        {label}
      </Text>
    </Pressable>
  );
}

function Chip({ label, active, onPress, c }) {
  return (
    <Pressable onPress={onPress}
      style={[styles.chip, { borderColor: active ? RED : c.border, backgroundColor: active ? RED : "transparent" }]}>
      <Text style={{ color: active ? "#fff" : c.text, fontSize: 13 }}>{label}</Text>
    </Pressable>
  );
}

function Segment({ options, value, onChange, c }) {
  return (
    <View style={[styles.segment, { borderColor: c.border }]}>
      {options.map(([key, label]) => (
        <Pressable key={key} onPress={() => onChange(key)}
          style={[styles.segmentItem, value === key && { backgroundColor: GREEN }]}>
          <Text style={{ color: value === key ? "#fff" : c.text }}>{label}</Text>
        </Pressable>
      ))}
    </View>
  );
}

function SettingsModal({ visible, settings, onSave, onClose, c }) {
  const [draft, setDraft] = useState(settings);
  useEffect(() => { if (visible) setDraft(settings); }, [visible, settings]);
  const field = (key, label, extra = {}) => (
    <View style={{ gap: 4 }}>
      <Text style={{ color: c.muted, fontSize: 12 }}>{label}</Text>
      <TextInput value={draft[key]} onChangeText={(v) => setDraft({ ...draft, [key]: v })}
        autoCapitalize="none" autoCorrect={false}
        style={[styles.field, { color: c.text, borderColor: c.border, backgroundColor: c.bg }]} {...extra} />
    </View>
  );
  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.modalBackdrop}>
        <View style={[styles.modal, { backgroundColor: c.card }]}>
          <Text style={[styles.title, { color: c.text }]}>Settings</Text>
          {field("serverUrl", "Server URL", { keyboardType: "url" })}
          <Text style={{ color: c.muted, fontSize: 11 }}>
            Android emulator: http://10.0.2.2:9080/translator/api · Phone: http://YOUR-PC-IP:9080/translator/api
          </Text>
          {field("username", "Username")}
          {field("password", "Password", { secureTextEntry: true })}
          <View style={styles.row}>
            <Button label="Cancel" onPress={onClose} c={c} style={styles.flex} />
            <Button label="Save" primary onPress={() => onSave({
              serverUrl: draft.serverUrl.trim(), username: draft.username.trim(), password: draft.password,
            })} style={styles.flex} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

// ------------------------------------------------------------------ styles

const lightColors = { bg: "#f7f5f2", card: "#ffffff", text: "#1d1d1f", muted: "#6b6b70", border: "#e4e0da" };
const darkColors = { bg: "#18181a", card: "#232326", text: "#f2f2f3", muted: "#a0a0a8", border: "#36363b" };

const styles = StyleSheet.create({
  flex: { flex: 1 },
  header: {
    paddingTop: Platform.OS === "android" ? 44 : 56,
    paddingBottom: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 3,
    borderBottomColor: RED,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  headerRight: { flexDirection: "row", alignItems: "center", gap: 8 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  title: { fontSize: 20, fontWeight: "700" },
  subtitle: { fontSize: 13 },
  body: { padding: 14, gap: 12, paddingBottom: 40 },
  card: { borderWidth: 1, borderRadius: 14, padding: 14, gap: 10 },
  chips: { gap: 6 },
  chip: { borderWidth: 1, borderRadius: 16, paddingHorizontal: 12, paddingVertical: 6 },
  input: { minHeight: 120, borderWidth: 1, borderRadius: 10, padding: 10, fontSize: 16, textAlignVertical: "top" },
  row: { flexDirection: "row", alignItems: "center", gap: 8, justifyContent: "space-between" },
  button: { borderRadius: 10, paddingVertical: 11, paddingHorizontal: 14 },
  segment: { flexDirection: "row", borderWidth: 1, borderRadius: 10, overflow: "hidden" },
  segmentItem: { paddingHorizontal: 14, paddingVertical: 7 },
  output: { fontSize: 24, lineHeight: 36 },
  error: { borderWidth: 1, borderRadius: 12, padding: 12, backgroundColor: "rgba(193,39,45,0.08)" },
  historyItem: { borderWidth: 1, borderRadius: 10, padding: 10, marginTop: 8 },
  toast: {
    position: "absolute", bottom: 40, alignSelf: "center", backgroundColor: "#333",
    paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20,
  },
  modalBackdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: "rgba(0,0,0,0.4)" },
  modal: { padding: 20, gap: 12, borderTopLeftRadius: 20, borderTopRightRadius: 20, paddingBottom: 40 },
  field: { borderWidth: 1, borderRadius: 10, padding: 10, fontSize: 15 },
});
