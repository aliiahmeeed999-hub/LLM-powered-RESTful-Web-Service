# Building an LLM-powered Darija translator with Jakarta REST, a Chrome side panel, and three more clients

*By Ali Lamididi, AI & Big Data student at Al Akhawayn University*

Moroccan Arabic (Darija) is the language most Moroccans speak every day, but mainstream translators usually turn it into Modern Standard Arabic or skip it. Large Language Models handle Darija surprisingly well when prompted carefully, so for my distributed-programming course I built a small system around one idea: **a single, secure REST endpoint that turns any text into Darija, used from wherever you are**, whether that is a browser, a PHP site, a Python script or a phone.

## The architecture in one picture

One server and four clients. The server is a Jakarta EE 10 WAR running on an **embedded Open Liberty** started by Maven (`mvn liberty:run`). Inside it the layers are plain:

* `TranslatorResource`: the JAX-RS endpoint (`POST /api/translate`), protected by `@RolesAllowed`.
* `TranslationService`: validation, caching, and calling the model.
* `LlmClient`: an interface with two implementations, `GeminiClient` (Google Gemini free tier) and `OllamaClient` (a 3B-parameter model running locally). A CDI producer picks one from an environment variable, so switching from cloud to offline only takes a config change.

## Prompting for *real* Darija

The first attempts came back in textbook Fusha. Three things fixed it:

1. **Say what Darija is *not*.** The system prompt explicitly rules out MSA and other dialects, and lists anchor words (`bghit`, `daba`, `bzzaf`, `wakha`) so the model adopts the register.
2. **Two scripts.** Many Moroccans write Darija in Latin letters with digits (3 = ع, 7 = ح, 9 = ق). The API takes `script=arabic|latin`, and each mode has its own short example in the prompt.
3. **Treat input as data.** The user text is wrapped in `<text>…</text>`, and the prompt says to translate anything inside, including questions and commands, never to answer or execute them. Without this, "What is the capital of France?" came back as *"Paris"* rather than its translation.

A low temperature (0.2) and a small output cleaner (strip quotes, code fences, "Translation:" prefixes) made the responses consistent.

## Security with Jakarta Security

Instead of the one-line `@BasicAuthenticationMechanismDefinition`, I wrote my own `HttpAuthenticationMechanism`. It is only about 60 lines, and it shows how the pieces fit together. Jakarta Security bridges the mechanism to a **Jakarta Authentication** `ServerAuthModule`, so the container calls it before every request. It decodes the Basic header, asks the `IdentityStoreHandler` to validate the credentials (my `InMemoryIdentityStore` reads users from an env var and compares passwords in constant time), and either logs the caller in or answers `401` with a `WWW-Authenticate` challenge and a JSON body. `web.xml` declares which URLs are protected, and leaves `OPTIONS` open for CORS preflight.

A Liberty detail worth knowing: roles returned by a custom identity store still need a mapping. Mapping the `translator` role to `ALL_AUTHENTICATED_USERS` in `server.xml` kept it simple.

## The Chrome extension: Manifest V3 and the side panel

This was the most fun part, and it had the most surprises:

* **`chrome.sidePanel.open()` must run synchronously inside the user gesture.** If you `await` anything first, the context-menu click no longer counts as a gesture and the call fails. The fix is to open the panel first and do the async work afterwards.
* **Handing the selection to the panel.** The service worker writes `{text, ts}` to `chrome.storage.session`. The panel reads it on load (when it was just opened) and also listens to `storage.onChanged` (when it was already open). The timestamp prevents processing the same selection twice.
* **`info.selectionText` drops line breaks**, so the worker also runs `getSelection()` in the page through `chrome.scripting` (allowed by `activeTab`) and falls back on restricted pages.
* **Microphone in a side panel:** Chrome won't show the permission prompt there. A small `permission.html` tab calls `getUserMedia` once, and since it shares the extension origin, speech recognition then works in the panel. With speech recognition, the REST call and `speechSynthesis` together, **voice-to-voice translation** takes about 30 lines.

## Three more clients, three styles

* **PHP:** a cURL wrapper class used by both a web page and a CLI script.
* **Python:** standard library only (`urllib`), so no `pip install` is needed. A CLI with an interactive mode, plus a Tkinter GUI that runs the HTTP call on a worker thread so the window never freezes.
* **React Native (Expo):** the same API with `expo-speech` for read aloud and `expo-clipboard` for "paste & translate". One catch: on an Android emulator the host machine is `10.0.2.2`, not `localhost`.

## Lessons learned

* Put the LLM behind your own interface early. Swapping Gemini for a local model was then a one-line config change.
* Cache translations. The Gemini free tier is rate-limited, and in demos people translate "Hello" ten times.
* Map provider failures to meaningful HTTP codes (429, 502, 503) so every client can show a useful message instead of "something went wrong".

The full source, UML diagrams and setup instructions are on GitHub: **[link to repository]**.
