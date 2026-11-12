package ma.aui.darija.api.dto;

import jakarta.json.bind.annotation.JsonbPropertyOrder;
import ma.aui.darija.service.TranslationResult;

import java.util.Locale;

/**
 * JSON answer of the translate endpoint.
 * <pre>
 * {
 *   "original": "Hello, how are you?",
 *   "translation": "سلام، كيداير؟",
 *   "sourceLang": "English",
 *   "targetLang": "Moroccan Arabic (Darija)",
 *   "script": "arabic",
 *   "provider": "gemini",
 *   "model": "gemini-2.5-flash",
 *   "cached": false,
 *   "elapsedMs": 812,
 *   "user": "ali"
 * }
 * </pre>
 */
@JsonbPropertyOrder({"original", "translation", "sourceLang", "targetLang", "script",
        "provider", "model", "cached", "elapsedMs", "user"})
public class TranslationResponse {

    public static final String TARGET = "Moroccan Arabic (Darija)";

    private String original;
    private String translation;
    private String sourceLang;
    private String targetLang = TARGET;
    private String script;
    private String provider;
    private String model;
    private boolean cached;
    private long elapsedMs;
    private String user;

    public TranslationResponse() {
    }

    public static TranslationResponse from(TranslationResult result, String user) {
        TranslationResponse r = new TranslationResponse();
        r.original = result.original();
        r.translation = result.translation();
        r.sourceLang = result.sourceLanguage();
        r.script = result.script().name().toLowerCase(Locale.ROOT);
        r.provider = result.provider();
        r.model = result.model();
        r.cached = result.cached();
        r.elapsedMs = result.elapsedMs();
        r.user = user;
        return r;
    }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getTranslation() { return translation; }
    public void setTranslation(String translation) { this.translation = translation; }

    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String sourceLang) { this.sourceLang = sourceLang; }

    public String getTargetLang() { return targetLang; }
    public void setTargetLang(String targetLang) { this.targetLang = targetLang; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
}
