# Prompts Log: AI assistance used in this project

**Student:** Ali Lamididi · **Project:** Mini Project 2, LLM-powered RESTful Web Service (Darija Translator)
**AI assistant:** Claude (Anthropic), model Claude Opus 5.5, in an agentic session with file, shell and browser tools
**Date:** 12 November 2026

This log has two parts:

* **Part A, the actual session log.** These are the prompts I really sent, copied word for word in the order I sent them, with a summary of what the assistant produced after each one.
* **Part B, a reproducible prompt sequence.** This is a cleaner, step-by-step set of prompts that I wrote **after** the project was built. It documents how the work breaks down and lets anyone regenerate or extend the project. These prompts were **not** used to build the submitted code.

---

## Part A: Actual session log

### Prompt 1

> *(attached: `project_2_requirements_LLM-powered_RESTful_Web_Service.pdf`, the official assignment)*
>
> 2. Deliverables:
>
> * To complete the project, the following deliverables must be submitted:
>    * Fully Functional Maven Project: Source code for the RESTful web service application.
>    * Fully Functional Projects: Source code for the Chrome extension and the other clients.
>    * Technical Architecture (UML):
>       * Optional: Use Case diagrams or Sequence diagrams to further clarify user interactions and message flow.
>       * Class Diagram: Detailing the software structure and relationships between classes.
>       * Deployment Diagram: Illustrating the physical network nodes (Server vs. Clients)
>    * Demo Video: A 5-minute video showcasing a walkthrough of the source code and a live demonstration of the application's features.
>    * GitHub Link: A shared link to your GitHub repository containing the full source code, commit history, and a descriptive README.md.
>    * Optional Technical Post: A link to a blog post (e.g., Medium, Dev.to, or a personal forum) detailing the development process, technical challenges, and final implementation.
>
> 3. Guidelines
>
> * Make sure your attachments are not empty and not corrupted. Your attachments should have the following format: <StudentName>_project<Number>.zip or .rar
> * Projects submitted by email will be discarded.
>
> name: Ali Lamididi

**What the assistant did (one long agentic run):**

| Step | Output | How it was checked |
|---|---|---|
| Read the requirements PDF and planned the architecture | Jakarta EE 10 WAR on embedded Open Liberty; `LlmClient` interface with Gemini and Ollama implementations | – |
| REST service (`server/`) | `TranslatorResource.translate()` (JSON, text/plain, GET), `TranslationService`, `PromptBuilder` (Darija prompt in Arabic or Latin script), LRU cache, exception mappers, `HealthResource` | Maven Central was not reachable in the sandbox, so the code was compiled with `javac` against hand-written API stubs. 17 JUnit tests were run and passed. |
| Security | Custom Jakarta Security `HttpAuthenticationMechanism` (HTTP Basic, bridged to Jakarta Authentication), `InMemoryIdentityStore`, `web.xml` constraint, `@RolesAllowed`, Liberty role mapping | Unit tests for header parsing and constant-time comparison. **Not yet run inside Liberty.** |
| Chrome extension (MV3) | Context menu, `chrome.sidePanel`, automatic paste of the selection, auto-translate, Arabic/Latin toggle, read aloud, voice-to-voice, history, settings, Ctrl+Shift+Y | Loaded in headless Chrome and tested end to end against a mock server with the same API |
| PHP client | Web page, CLI, cURL wrapper class | `php -l`, then live requests to the mock server |
| Python client | Standard-library CLI (one-shot, stdin, interactive), Tkinter GUI, unit tests | 6 unit tests plus live requests to the mock server |
| React Native (Expo) client | `App.js`, `src/api.js` (UTF-8 base64 Basic auth), paste & translate, read aloud, history, settings | Syntax checked. API module tested against the mock server. **App UI not run.** |
| UML | Class, deployment, sequence and use case diagrams (Mermaid/Graphviz source plus PNG/SVG) | Rendered and visually inspected |
| Docs | README, Postman collection, `test-api.sh`, demo video script, blog post draft, git history (11 commits), zip | – |

**Known limitations reported by the assistant:** the real Liberty server and the live Gemini calls could not be tested from the sandbox. I had to run `mvn test`, `mvn liberty:run` and `./test-api.sh` on my own machine, and align the Expo SDK version with `npx expo install --fix`.

### Prompt 2

> the instructor asked for a prompts log with all the prompts the student generated to build the project. I need a promt log with good prompt for generating this project so I can attach it with the files

**What the assistant did:** wrote this file. Part A records the real prompts, and Part B adds a reusable prompt sequence that is clearly labelled as such.

### My own work after the AI session

*(Fill this in honestly before submitting. Examples: ran the server locally, added my Gemini key, fixed X, tested on my phone, recorded the video, pushed to GitHub, any follow-up prompts.)*

- [ ] Ran `mvn test` and `mvn liberty:run` locally. Result: …
- [ ] Ran `./test-api.sh` and Postman. Result: …
- [ ] Tested the Chrome extension, PHP, Python and mobile clients against the real server: …
- [ ] Changes I made myself: …
- [ ] Additional prompts I sent (copy them here verbatim): …

---

## Part B: Reproducible prompt sequence (written afterwards)

> These prompts break the project into steps. Use them one at a time with any coding assistant, reviewing and running the code after each step.

**B1. Context and architecture**
> I'm building a university project: a Java RESTful web service that translates text from any language into Moroccan Arabic (Darija) using an LLM (Google Gemini free tier, with an option for a local Ollama model). Clients: a Chrome MV3 extension using `chrome.sidePanel`, a PHP app, a Python app and a React Native app. Propose an architecture: packages, classes, the REST contract (paths, JSON request/response, error codes), how authentication works, and how the LLM provider can be swapped. Don't write code yet.

**B2. Maven project and embedded server**
> Create the Maven `pom.xml` for a Jakarta EE 10 WAR (`jakarta.jakartaee-api` provided, Java 17) that runs on embedded Open Liberty with `liberty-maven-plugin` (`mvn liberty:run`, plus runnable-JAR packaging). Write `server.xml` with only the features I need (restfulWS, jsonb, jsonp, cdi, appSecurity), HTTP port 9080, context root `/translator`, and CORS for `/translator/api`. Configuration (API key, model, users) must come from environment variables / `server.env`, never from code.

**B3. LLM layer**
> Write an `LlmClient` interface (`complete(systemPrompt, userMessage)`, `provider()`, `model()`, `isConfigured()`) and two implementations using `java.net.http.HttpClient` and JSON-P: `GeminiClient` (`POST /v1beta/models/{model}:generateContent`, `x-goog-api-key` header, `systemInstruction`, skip "thought" parts, handle `promptFeedback.blockReason`) and `OllamaClient` (`POST /api/chat`, `stream:false`). Map failures to an `LlmException` with kinds NOT_CONFIGURED, UNAVAILABLE, RATE_LIMITED and REFUSED. Add a CDI producer that picks the implementation from `LLM_PROVIDER`.

**B4. Prompt engineering for Darija**
> Write a `PromptBuilder` that creates a system prompt for translating into natural Moroccan Darija, not Modern Standard Arabic. It should support two output scripts: Arabic letters, or Latin "Arabizi" with digits (3 = ع, 7 = ح, 9 = ق). It must output only the translation, keep names, numbers and URLs, and resist prompt injection by wrapping user text in `<text>` tags and telling the model to translate commands rather than obey them. Also write `cleanOutput()` to strip quotes, code fences and "Translation:" prefixes, and normalise source-language codes. Include JUnit 5 tests.

**B5. Service and REST resource**
> Write `TranslationService` (`@ApplicationScoped`): validate the text (not empty, at most `MAX_TEXT_LENGTH`), build the prompt, call the `LlmClient`, clean and cache the result in a thread-safe LRU cache, and return a `TranslationResult` record. Then write `TranslatorResource` at `@Path("/translate")` with a business method `translate()`: POST JSON `{text, sourceLang, script}` returns a JSON `TranslationResponse`; also support POST text/plain → text/plain and GET `?text=`. Add a public `/health` resource and ExceptionMappers returning JSON errors (400, 422, 429, 502, 503). Include unit tests with a fake LlmClient.

**B6. Security with Jakarta Authentication**
> Secure `/api/translate` with HTTP Basic authentication using Jakarta Security. Implement a custom `HttpAuthenticationMechanism` that parses the `Authorization` header, validates it through `IdentityStoreHandler`, calls `notifyContainerAboutLogin` on success, and returns 401 with `WWW-Authenticate` and a JSON body when a protected resource is called without valid credentials. Add an `IdentityStore` that reads users from `TRANSLATOR_USERS` and compares passwords in constant time. Add the `web.xml` security constraint (leave OPTIONS open for CORS), `@RolesAllowed`, and the Liberty role mapping. Explain how this relates to the Jakarta Authentication (JASPIC) SPI.

**B7. Testing with HTTP tools**
> Give me a bash script with cURL calls covering health, no credentials (401), wrong password (401), JSON translation, Latin script, text/plain, GET, and empty text (400). Also generate a Postman v2.1 collection with Basic auth variables and test assertions for each request.

**B8. Chrome extension**
> Build a Manifest V3 Chrome extension. Add a right-click context-menu entry "Translate "%s" to Darija" that opens `chrome.sidePanel` (called synchronously inside the user gesture) and hands the selected text to the panel through `chrome.storage.session`. Use `chrome.scripting` with activeTab to read the exact selection, falling back to `selectionText`. In the side panel, paste the text automatically, call the REST endpoint with Basic auth, and show the result right-to-left. Add an Arabic/Latin toggle, copy to clipboard, read aloud with `speechSynthesis` (Arabic voice), speech input and voice-to-voice with `webkitSpeechRecognition` (plus a permission page for the microphone), history, a settings form (server URL, username, password, optional host permissions), a status indicator, and a Ctrl+Shift+Y shortcut.

**B9. PHP client**
> Write a PHP 8 client: a `DarijaTranslatorClient` class using cURL with Basic auth and JSON, typed exceptions, a web page (form with source language and script, result, history in the session, read-aloud button, escaped output), and a CLI script. Read credentials from environment variables.

**B10. Python client**
> Write a Python 3 client using only the standard library (urllib): a reusable client class, an argparse CLI (text argument, stdin, `--latin`, `--from`, `--json`, interactive mode), a Tkinter GUI that calls the API on a worker thread, and unittest tests with a mocked urlopen.

**B11. React Native app**
> Create an Expo React Native app that calls the same API with Basic auth. Write a UTF-8 safe base64 encoder that doesn't depend on `btoa`. Features: text input, paste from clipboard and translate, source-language chips, Arabic/Latin toggle, result with copy and read aloud (expo-speech), history and settings persisted with AsyncStorage, dark mode. Explain which server URL to use on an Android emulator (10.0.2.2), an iOS simulator, and a real phone.

**B12. UML and documentation**
> From the final code, generate UML diagrams: a class diagram (packages api, service, llm, security, config, with interfaces, CDI annotations and relationships), a deployment diagram (server node with Open Liberty and the WAR, four client nodes, the Gemini cloud and an optional Ollama node, with protocols on the links), and a sequence diagram of the flow context menu → side panel → authentication → resource → service → LLM. Then write a README with setup for every component, the API reference, a configuration table, security notes and troubleshooting, plus a 5-minute demo video script and a technical blog post draft.
