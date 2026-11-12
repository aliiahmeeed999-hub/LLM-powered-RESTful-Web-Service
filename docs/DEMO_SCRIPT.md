# Demo video script (≈ 5 minutes)

Record with OBS Studio, or Windows `Win+Alt+R`, at 1080p. Before you start, have these windows ready: IntelliJ/VS Code with the repo open, a terminal, Chrome, Postman, and the phone or emulator (mirror it with `scrcpy` for Android).

**Before recording:** `cd server && mvn liberty:run` (wait for *ready to run a smarter planet*), extension loaded, PHP server on :8000, Expo app open.

| Time | Show | Say (key points) |
|---|---|---|
| **0:00–0:20** | README on GitHub | "LLM-powered REST service that translates any language into Moroccan Darija. One Jakarta REST endpoint, secured with Basic auth, used by a Chrome extension, PHP, Python and React Native." |
| **0:20–0:40** | `docs/uml/deployment-diagram.png`, then `class-diagram.png` | Server node vs. client nodes. Layers: resource → service → LlmClient interface (Gemini / Ollama). |
| **0:40–1:40** | **Code walkthrough**: `TranslatorResource.java` | `@Path("/translate")`, `@RolesAllowed`, the three `translate` variants (JSON, text/plain, GET). |
| | `TranslationService` + `PromptBuilder` | validation, cache, the Darija system prompt (Arabic vs Latin script, `<text>` delimiters against prompt injection). |
| | `GeminiClient` | `java.net.http` POST to `generateContent`, JSON-P parsing, error → status mapping. |
| | `BasicAuthenticationMechanism` + `InMemoryIdentityStore` + `web.xml` | Jakarta Security mechanism (bridged to Jakarta Authentication), 401 + `WWW-Authenticate`, constant-time compare. |
| **1:40–2:20** | Terminal: `./test-api.sh`, then Postman *Run collection* | health is public, no creds → 401, wrong password → 401, JSON translation, Latin script, text/plain, empty → 400. All Postman tests green. |
| **2:20–3:30** | **Chrome extension** on a news site (e.g. bbc.com) | select a sentence → right-click **Translate to Darija** → panel opens, text pasted and translated. Toggle Latin. Click 🔊. Use 🗣️ Voice → Voice: say "Where is the nearest pharmacy?" and hear the Darija. Show history and Ctrl+Shift+Y. Change the password in Settings to show the 401 message. |
| **3:30–4:00** | PHP page at `localhost:8000`, then `php cli.php "..."` | form, script selector, read aloud, history. |
| **4:00–4:25** | `python darija_client.py -i`, then `python gui.py` | CLI interactive mode, Tkinter GUI. |
| **4:25–4:50** | Phone / emulator | paste & translate, Arabic/Latin, read aloud, settings with the LAN IP. |
| **4:50–5:00** | Optional: `server.env` → `LLM_PROVIDER=ollama`, restart, translate | "Same API, local lightweight LLM, no key needed." Close with the GitHub link. |

Tips: zoom the editor to 140%, hide bookmarks and personal tabs, and blur or cover your API key if `server.env` appears on screen.
