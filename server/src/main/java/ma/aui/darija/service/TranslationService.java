package ma.aui.darija.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ma.aui.darija.config.AppConfig;
import ma.aui.darija.llm.LlmClient;
import ma.aui.darija.llm.LlmException;

import java.util.logging.Logger;

/**
 * Business logic: validates the input, builds the prompt, calls the LLM, cleans and caches
 * the answer.
 */
@ApplicationScoped
public class TranslationService {

    private static final Logger LOG = Logger.getLogger(TranslationService.class.getName());

    @Inject
    LlmClient llm;

    @Inject
    AppConfig config;

    private TranslationCache cache;

    /** Required by CDI. */
    public TranslationService() {
    }

    /** Constructor for unit tests (no container). */
    public TranslationService(LlmClient llm, AppConfig config) {
        this.llm = llm;
        this.config = config;
        init();
    }

    @PostConstruct
    void init() {
        cache = new TranslationCache(config.cacheSize());
    }

    /**
     * Translates {@code text} into Moroccan Darija.
     *
     * @param text       text to translate (required, at most {@code MAX_TEXT_LENGTH} chars)
     * @param sourceLang ISO code, language name or {@code auto}/null for auto-detection
     * @param scriptName {@code arabic} (default) or {@code latin}
     */
    public TranslationResult translate(String text, String sourceLang, String scriptName) {
        if (text == null || text.isBlank()) {
            throw new InvalidRequestException("The 'text' to translate is required.");
        }
        String trimmed = text.strip();
        if (trimmed.length() > config.maxTextLength()) {
            throw new InvalidRequestException("Text too long: " + trimmed.length()
                    + " characters (max " + config.maxTextLength() + ").");
        }
        String source = PromptBuilder.normalizeSourceLanguage(sourceLang);
        PromptBuilder.Script script = PromptBuilder.Script.parse(scriptName);

        long start = System.nanoTime();
        String key = TranslationCache.key(trimmed, source, script, llm.provider() + "/" + llm.model());
        String cached = cache.get(key);
        if (cached != null) {
            return new TranslationResult(trimmed, cached, source, script, llm.provider(), llm.model(),
                    true, elapsedMs(start));
        }

        String raw = llm.complete(PromptBuilder.systemPrompt(source, script), PromptBuilder.userMessage(trimmed));
        String translation = PromptBuilder.cleanOutput(raw);
        if (translation.isEmpty()) {
            throw new LlmException(LlmException.Kind.REFUSED, "The model returned an empty translation.");
        }
        cache.put(key, translation);

        long ms = elapsedMs(start);
        LOG.fine(() -> "Translated " + trimmed.length() + " chars in " + ms + " ms");
        return new TranslationResult(trimmed, translation, source, script, llm.provider(), llm.model(), false, ms);
    }

    public String providerName() {
        return llm.provider();
    }

    public String modelName() {
        return llm.model();
    }

    public boolean isLlmConfigured() {
        return llm.isConfigured();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
