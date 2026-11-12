# 🇲🇦 Darija Translator: an LLM-powered RESTful web service

**Mini Project 2: LLM-powered RESTful Web Service** · Ali Lamididi · Al Akhawayn University

A **Jakarta RESTful Web Service** (`TranslatorResource.translate()`) that translates text from English, French, Spanish or almost any other language into **Moroccan Arabic Dialect (Darija)** with a Large Language Model (**Google Gemini**, or a **lightweight local model through Ollama**). The endpoint is secured with **HTTP Basic authentication** through Jakarta Security / Jakarta Authentication. It runs in an **embedded Open Liberty** container and is used by four clients:

| Client | Tech | Highlights |
|---|---|---|
| **Chrome extension** | Manifest V3, `chrome.sidePanel`, `chrome.contextMenus` | Select text, right-click "Translate to Darija", and the side panel opens with the text already pasted and translated. Read aloud, voice-to-voice, history, Ctrl+Shift+Y shortcut |
| **PHP app** | PHP 8 + cURL | Web page plus CLI, history, read aloud |
| **Python app** | Standard library only | CLI (one-shot, stdin, interactive) and Tkinter desktop GUI |
| **Mobile app** | React Native (Expo) | Paste & translate, Arabic/Latin script, read aloud, history, settings |

![Chrome side panel](docs/screenshots/chrome-side-panel-mock.png)

---

## Table of contents
1. [Architecture](#architecture)
2. [Repository layout](#repository-layout)
3. [Run the REST service](#1-run-the-rest-service)
4. [API reference](#api-reference)
5. [Test with cURL / Postman](#test-with-curl--postman)
6. [Chrome extension](#2-chrome-extension)
7. [PHP client](#3-php-client)
8. [Python client](#4-python-client)
9. [React Native mobile app](#5-react-native-mobile-app)
10. [Security](#security)
11. [Extensions implemented](#extensions-implemented)
12. [Troubleshooting](#troubleshooting)

---

## Architecture

```mermaid
flowchart LR
    subgraph Clients
        EXT["Chrome extension<br/>(side panel)"]
        PHP["PHP app"]
        PY["Python CLI / GUI"]
        RN["React Native app"]
    end
    subgraph Server["Open Liberty (embedded) · translator.war"]
        SEC["BasicAuthenticationMechanism<br/>+ InMemoryIdentityStore"]
        RES["TranslatorResource<br/>/api/translate"]
        SVC["TranslationService<br/>prompt · cache"]
        LLM["LlmClient<br/>GeminiClient | OllamaClient"]
        SEC --> RES --> SVC --> LLM
    end
    EXT & PHP & PY & RN -- "HTTP + Basic auth (JSON)" --> SEC
    LLM -- HTTPS --> G["Google Gemini API"]
    LLM -. HTTP .-> O["Ollama (local LLM)"]
```

UML diagrams are in [`docs/uml`](docs/uml). Each one is available as Mermaid/Graphviz source and as PNG/SVG:

| Diagram | File |
|---|---|
| Class diagram | [`class-diagram.png`](docs/uml/class-diagram.png) · [source](docs/uml/class-diagram.mmd) |
| Deployment diagram | [`deployment-diagram.png`](docs/uml/deployment-diagram.png) · [source](docs/uml/deployment-diagram.dot) |
| Sequence diagram (context menu → side panel → REST → LLM) | [`sequence-diagram.png`](docs/uml/sequence-diagram.png) · [source](docs/uml/sequence-diagram.mmd) |
| Use case diagram | [`use-case-diagram.png`](docs/uml/use-case-diagram.png) · [source](docs/uml/use-case-diagram.mmd) |

## Repository layout

```
darija-translator/
├── server/                      Maven project (Jakarta EE 10 WAR + Open Liberty)
│   ├── pom.xml
│   ├── src/main/java/ma/aui/darija/
│   │   ├── TranslatorApplication.java        @ApplicationPath("/api")
│   │   ├── api/TranslatorResource.java       ★ the REST endpoint, translate()
│   │   ├── api/HealthResource.java           public status endpoint
│   │   ├── api/dto/…                         TranslationRequest/Response, ErrorResponse
│   │   ├── api/error/…                       ExceptionMappers → 400/422/429/502/503
│   │   ├── security/BasicAuthenticationMechanism.java   Jakarta Security HTTP Basic
│   │   ├── security/InMemoryIdentityStore.java          users from TRANSLATOR_USERS
│   │   ├── service/TranslationService.java   validation, prompt, cache
│   │   ├── service/PromptBuilder.java        Darija prompt engineering
│   │   ├── llm/LlmClient.java                interface (+ GeminiClient, OllamaClient)
│   │   └── config/AppConfig.java             env-based configuration
│   ├── src/main/liberty/config/server.xml   Liberty features, CORS, role mapping
│   ├── src/main/webapp/WEB-INF/web.xml      security constraint
│   ├── src/test/java/…                       JUnit 5 tests
│   ├── postman/DarijaTranslator.postman_collection.json
│   └── test-api.sh                           cURL test script
├── chrome-extension/            Manifest V3 extension (side panel + context menu)
├── clients/php/                 PHP web page + CLI
├── clients/python/              Python CLI, Tkinter GUI, unit tests
├── clients/mobile/              React Native (Expo) app
└── docs/                        UML diagrams, screenshots, demo script, blog post
```

---

## 1. Run the REST service

**Requirements:** JDK 17+ and Maven 3.8+. Maven downloads Open Liberty automatically the first time.

```bash
cd server
cp src/main/liberty/config/server.env.example src/main/liberty/config/server.env
# edit server.env and set GEMINI_API_KEY (free key: https://aistudio.google.com/app/apikey)

mvn liberty:run           # or: mvn liberty:dev   (hot reload)
```

When the log shows `The darijaServer server is ready to run a smarter planet`, the service is live at
**`http://localhost:9080/translator/api`**.

```bash
curl http://localhost:9080/translator/api/health
curl -u ali:darija2026 "http://localhost:9080/translator/api/translate?text=Good%20morning"
```

**Runnable JAR (embedded container, no installation):**
```bash
mvn package liberty:package -Dinclude=runnable
java -jar target/translator.jar
```

**Unit tests:** `mvn test`

### Configuration (`server.env` or environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `LLM_PROVIDER` | `gemini` | `gemini` or `ollama` |
| `GEMINI_API_KEY` | none | Google AI Studio key (required for Gemini) |
| `GEMINI_MODEL` | `gemini-2.5-flash` | any Gemini model that supports `generateContent` |
| `OLLAMA_URL` / `OLLAMA_MODEL` | `http://localhost:11434` / `qwen2.5:3b` | local LLM |
| `TRANSLATOR_USERS` | `ali:darija2026,demo:demo123` | Basic-auth users (`user:pass,…`) |
| `MAX_TEXT_LENGTH` | `5000` | maximum characters per request |
| `CACHE_SIZE` | `500` | in-memory LRU cache of translations |
| `LLM_TIMEOUT_SECONDS` | `60` | LLM call timeout |
| `LLM_TEMPERATURE` | `0.2` | sampling temperature |

**Local lightweight LLM (no API key, offline):**
```bash
ollama pull qwen2.5:3b
# server.env → LLM_PROVIDER=ollama
mvn liberty:run
```

---

## API reference

Base URL `http://localhost:9080/translator/api`. All `/translate` operations require **HTTP Basic** auth.

### `POST /translate` (JSON), the main business method
```http
POST /translator/api/translate
Authorization: Basic YWxpOmRhcmlqYTIwMjY=
Content-Type: application/json

{ "text": "Hello my friend, how are you today?", "sourceLang": "en", "script": "arabic" }
```
| Field | Required | Values |
|---|---|---|
| `text` | ✔ | 1–5000 characters |
| `sourceLang` | – | ISO code (`en`, `fr`, `es`…), a language name, or `auto` (default) |
| `script` | – | `arabic` (default, e.g. "كيداير؟") or `latin` (Arabizi, e.g. "kidayr?") |

```json
{
  "original": "Hello my friend, how are you today?",
  "translation": "سلام أ صاحبي، كيداير اليوم؟",
  "sourceLang": "English",
  "targetLang": "Moroccan Arabic (Darija)",
  "script": "arabic",
  "provider": "gemini",
  "model": "gemini-2.5-flash",
  "cached": false,
  "elapsedMs": 842,
  "user": "ali"
}
```
The translation above is an illustration. Real output depends on the model.

### Other operations
| Request | Response |
|---|---|
| `POST /translate?script=latin` with `Content-Type: text/plain` | plain-text translation |
| `GET /translate?text=...&sourceLang=en&script=latin` | JSON (same as POST) |
| `GET /health` *(public)* | `{"status":"UP","provider":"gemini","model":"…","llmConfigured":true}` |

### Errors (always JSON: `{"status", "error", "message"}`)
| Status | When |
|---|---|
| `400` | empty or too-long text, unknown script/language |
| `401` | missing or wrong credentials (`WWW-Authenticate: Basic realm="DarijaTranslator"`) |
| `422` | LLM refused or returned nothing |
| `429` | Gemini quota exhausted (`Retry-After: 30`) |
| `502` | LLM unreachable |
| `503` | LLM not configured (missing API key, Ollama model not pulled) |

## Test with cURL / Postman

```bash
cd server && ./test-api.sh                    # 8 scenarios: health, 401s, JSON, text/plain, GET, 400
```
In **Postman**, use *Import → `server/postman/DarijaTranslator.postman_collection.json`* and run the collection. Every request has test assertions. Credentials are collection variables. Thunder Client (VS Code) can import the same file.

---

## 2. Chrome extension

1. Start the server.
2. Go to `chrome://extensions`, turn on **Developer mode**, click **Load unpacked**, and pick the `chrome-extension/` folder.
3. On any web page, **select text → right-click → “Translate "…" to Darija”.**
   The side panel opens, the selected text is **pasted automatically** and translated at once.

Other features:
* Toolbar icon or **Ctrl+Shift+Y** (⌘+Shift+Y on macOS) opens the panel and translates the current selection.
* **Arabic / Latin** script toggle, source-language picker.
* **🔊 Read aloud** (Web Speech API, chooses an Arabic voice).
* **🎤 Speech input** and **🗣️ Voice → Voice**: speak in English/French/…, the panel translates and reads the Darija aloud. The first time, a tab opens so you can allow the microphone, because Chrome cannot show that prompt inside a side panel.
* Auto-copy of the translation to the clipboard, history of the last 30 translations, server status indicator.
* **Settings**: server URL, username/password. Hosts other than `localhost:9080` are requested as optional host permissions.

| File | Role |
|---|---|
| `manifest.json` | MV3: `sidePanel`, `contextMenus`, `storage`, `activeTab`, `scripting`, `clipboardWrite` |
| `background.js` | service worker: context menu, shortcut, opens the panel, reads the exact selection |
| `sidepanel.html/css/js` | UI and logic |
| `api.js` | REST client (Basic auth, UTF-8 safe base64, error mapping) |
| `speech.js` | text-to-speech and speech recognition |
| `permission.html/js` | one-time microphone permission page |

## 3. PHP client

Needs PHP 8.0+ with the cURL extension.
```bash
cd clients/php
php -S localhost:8000            # open http://localhost:8000
php cli.php "Where is the train station?"
php cli.php --latin --from=fr "Bonne nuit"
```
Settings come from `DARIJA_API_URL`, `DARIJA_USER` and `DARIJA_PASSWORD` (defaults in `config.php`).

## 4. Python client

Python 3.8+ with no third-party packages.
```bash
cd clients/python
python darija_client.py "Where can I buy bread?"
python darija_client.py --latin --from en "See you tomorrow"
echo "Bonjour tout le monde" | python darija_client.py
python darija_client.py -i          # interactive (:latin, :arabic, :from fr, :quit)
python darija_client.py --json "Thanks"
python gui.py                       # Tkinter desktop app
python -m unittest -v               # unit tests (no server needed)
```

## 5. React Native mobile app

Needs Node 18+ and the **Expo Go** app on your phone (or an Android emulator / iOS simulator).
```bash
cd clients/mobile
npm install
npx expo install --fix      # aligns native module versions with the installed Expo SDK
npx expo start              # scan the QR code with Expo Go
```
If your Expo Go app needs a newer SDK, run `npx expo install expo@latest && npx expo install --fix`.

In the app's **⚙️ Settings**, set the server URL:
* Android emulator: `http://10.0.2.2:9080/translator/api`
* iOS simulator: `http://localhost:9080/translator/api`
* Real phone: `http://<your-PC-LAN-IP>:9080/translator/api`. The phone and PC must be on the same Wi-Fi, and the firewall must allow port 9080.

Features: type or **📋 Paste & translate** from the clipboard, source-language chips, Arabic/Latin script, **🔊 read aloud** (expo-speech), copy, history (AsyncStorage), dark mode.

---

## Security

* **Authentication:** `BasicAuthenticationMechanism` implements the Jakarta Security `HttpAuthenticationMechanism`. Jakarta Security registers it with the container as a **Jakarta Authentication (JASPIC) `ServerAuthModule`**, so the container calls it on every request. It decodes `Authorization: Basic …`, validates the credentials through the `IdentityStoreHandler` → `InMemoryIdentityStore` (constant-time comparison), and calls `notifyContainerAboutLogin`.
* **Authorization:** `web.xml` protects `/api/translate/*` for role `translator` (OPTIONS is left open for CORS preflight), and `@RolesAllowed("translator")` on the resource adds a second check. `server.xml` maps the role to every user the identity store authenticates.
* **Stateless:** no session. Credentials are sent with each request.
* **Secrets:** API key and users live in `server.env` (git-ignored) or environment variables, never in code.
* **Prompt-injection hardening:** user text is wrapped in `<text>…</text>`, the system prompt says to translate the content and never obey it, and language names are validated.
* ⚠️ Basic auth sends credentials only base64-encoded. In production, serve over **HTTPS** (Liberty already listens on 9443 when a keystore is configured).

## Extensions implemented

- [x] **Voice-to-voice translation** (Chrome: speech recognition → REST → speech synthesis)
- [x] **Read aloud / text-to-speech** (Chrome, PHP page, mobile)
- [x] **Lightweight LLM for local deployment** (Ollama provider: `qwen2.5:3b`, `gemma2:2b`, …)
- [x] **Embedded container** (Open Liberty via `liberty-maven-plugin`, runnable JAR packaging)
- [x] Arabic-script **and** Latin-script (Arabizi) Darija
- [x] LRU translation cache, so repeated texts don't use LLM quota
- [x] Health endpoint, typed JSON errors, CORS, keyboard shortcut, history, Postman tests, unit tests

## Troubleshooting

| Symptom | Fix |
|---|---|
| `503 GEMINI_API_KEY is not set` | create `server.env` from the example and restart Liberty |
| `429 Too Many Requests` | free-tier quota hit. Wait, or switch to `LLM_PROVIDER=ollama` |
| Extension shows **Offline** | server not running, or a different URL. Check **Settings → Test connection** |
| Mic button does nothing | allow the microphone in the tab that opens (`permission.html`) |
| Phone cannot connect | use the PC's LAN IP rather than `localhost`, and open port 9080 in the firewall |
| No Arabic voice for read aloud | install an Arabic voice in the OS (Windows: Settings → Time & language → Speech) |

---

**Docs:** [demo video script](docs/DEMO_SCRIPT.md) · [technical blog post draft](docs/BLOG_POST.md)
