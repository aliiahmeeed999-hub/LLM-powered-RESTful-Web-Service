package ma.aui.darija.config;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Central, read-only configuration of the service.
 *
 * <p>Every value is read from an environment variable first (Open Liberty exports the
 * lines of {@code server.env} as environment variables), then from a JVM system property
 * with the same name, then falls back to a sensible default.</p>
 */
@ApplicationScoped
public class AppConfig {

    public static final String DEFAULT_USERS = "ali:darija2026,demo:demo123";

    private final Function<String, String> source;

    /** Constructor used by CDI: environment variables, then system properties. */
    public AppConfig() {
        this(name -> {
            String value = System.getenv(name);
            return (value == null || value.isBlank()) ? System.getProperty(name) : value;
        });
    }

    /** Constructor used by unit tests to inject arbitrary values. */
    public AppConfig(Function<String, String> source) {
        this.source = source;
    }

    public static AppConfig fromMap(Map<String, String> values) {
        return new AppConfig(values::get);
    }

    // ------------------------------------------------------------------ LLM

    /** {@code gemini} (default) or {@code ollama}. */
    public String llmProvider() {
        return get("LLM_PROVIDER", "gemini").trim().toLowerCase(Locale.ROOT);
    }

    public String geminiApiKey() {
        return get("GEMINI_API_KEY", "");
    }

    public String geminiModel() {
        return get("GEMINI_MODEL", "gemini-2.5-flash");
    }

    public String geminiBaseUrl() {
        return stripTrailingSlash(get("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta"));
    }

    public String ollamaUrl() {
        return stripTrailingSlash(get("OLLAMA_URL", "http://localhost:11434"));
    }

    public String ollamaModel() {
        return get("OLLAMA_MODEL", "qwen2.5:3b");
    }

    public Duration llmTimeout() {
        return Duration.ofSeconds(getInt("LLM_TIMEOUT_SECONDS", 60));
    }

    /** Sampling temperature; low values give more literal, repeatable translations. */
    public double temperature() {
        try {
            return Double.parseDouble(get("LLM_TEMPERATURE", "0.2"));
        } catch (NumberFormatException e) {
            return 0.2;
        }
    }

    // ------------------------------------------------------------ Limits

    public int maxTextLength() {
        return getInt("MAX_TEXT_LENGTH", 5000);
    }

    public int cacheSize() {
        return getInt("CACHE_SIZE", 500);
    }

    // ---------------------------------------------------------- Security

    /**
     * Users allowed to call the API, parsed from {@code TRANSLATOR_USERS}
     * ({@code "user1:pass1,user2:pass2"}). Passwords may contain ':'.
     */
    public Map<String, String> users() {
        String raw = get("TRANSLATOR_USERS", DEFAULT_USERS);
        Map<String, String> users = new LinkedHashMap<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            int sep = trimmed.indexOf(':');
            if (sep > 0 && sep < trimmed.length() - 1) {
                users.put(trimmed.substring(0, sep), trimmed.substring(sep + 1));
            }
        }
        return Collections.unmodifiableMap(users);
    }

    // ----------------------------------------------------------- helpers

    private String get(String name, String defaultValue) {
        String value = source.apply(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private int getInt(String name, int defaultValue) {
        try {
            int value = Integer.parseInt(get(name, String.valueOf(defaultValue)));
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
