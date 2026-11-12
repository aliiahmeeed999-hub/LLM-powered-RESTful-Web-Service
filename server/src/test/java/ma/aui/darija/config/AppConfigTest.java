package ma.aui.darija.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    @Test
    void defaults() {
        AppConfig config = AppConfig.fromMap(Map.of());
        assertEquals("gemini", config.llmProvider());
        assertEquals("gemini-2.5-flash", config.geminiModel());
        assertEquals(Duration.ofSeconds(60), config.llmTimeout());
        assertEquals(5000, config.maxTextLength());
        assertEquals("darija2026", config.users().get("ali"));
    }

    @Test
    void parsesUsers() {
        AppConfig config = AppConfig.fromMap(Map.of("TRANSLATOR_USERS", " bob:secret , eve:pa:ss, broken, :nouser"));
        assertEquals(2, config.users().size());
        assertEquals("secret", config.users().get("bob"));
        assertEquals("pa:ss", config.users().get("eve"));
    }

    @Test
    void invalidNumbersFallBackToDefaults() {
        AppConfig config = AppConfig.fromMap(Map.of("MAX_TEXT_LENGTH", "abc", "CACHE_SIZE", "-3",
                "OLLAMA_URL", "http://gpu-box:11434/", "LLM_PROVIDER", " Ollama "));
        assertEquals(5000, config.maxTextLength());
        assertEquals(500, config.cacheSize());
        assertEquals("http://gpu-box:11434", config.ollamaUrl());
        assertEquals("ollama", config.llmProvider());
    }
}
