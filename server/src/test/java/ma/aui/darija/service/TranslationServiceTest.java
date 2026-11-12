package ma.aui.darija.service;

import ma.aui.darija.config.AppConfig;
import ma.aui.darija.llm.LlmClient;
import ma.aui.darija.llm.LlmException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationServiceTest {

    /** Fake LLM that records calls and returns a canned answer. */
    static class FakeLlm implements LlmClient {
        int calls;
        String lastSystem;
        String lastUser;
        String answer = "\"سلام، كيداير؟\"";

        @Override
        public String complete(String systemPrompt, String userMessage) {
            calls++;
            lastSystem = systemPrompt;
            lastUser = userMessage;
            return answer;
        }

        @Override public String provider() { return "fake"; }
        @Override public String model() { return "fake-1"; }
        @Override public boolean isConfigured() { return true; }
    }

    private final FakeLlm llm = new FakeLlm();
    private final TranslationService service =
            new TranslationService(llm, AppConfig.fromMap(Map.of("MAX_TEXT_LENGTH", "50")));

    @Test
    void translatesAndCleansOutput() {
        TranslationResult r = service.translate("  Hello, how are you?  ", "en", null);
        assertEquals("سلام، كيداير؟", r.translation());
        assertEquals("Hello, how are you?", r.original());
        assertEquals("English", r.sourceLanguage());
        assertEquals(PromptBuilder.Script.ARABIC, r.script());
        assertFalse(r.cached());
        assertTrue(llm.lastUser.contains("Hello, how are you?"));
        assertTrue(llm.lastSystem.contains("from English"));
    }

    @Test
    void secondIdenticalRequestComesFromCache() {
        service.translate("Hello", "en", "arabic");
        TranslationResult second = service.translate("Hello", "en", "arabic");
        assertTrue(second.cached());
        assertEquals(1, llm.calls);

        service.translate("Hello", "en", "latin");   // different script -> new call
        assertEquals(2, llm.calls);
    }

    @Test
    void validatesInput() {
        assertThrows(InvalidRequestException.class, () -> service.translate(null, null, null));
        assertThrows(InvalidRequestException.class, () -> service.translate("   ", null, null));
        assertThrows(InvalidRequestException.class, () -> service.translate("x".repeat(51), null, null));
        assertThrows(InvalidRequestException.class, () -> service.translate("Hi", null, "cyrillic"));
        assertEquals(0, llm.calls);
    }

    @Test
    void emptyModelAnswerIsAnError() {
        llm.answer = "   ";
        LlmException e = assertThrows(LlmException.class, () -> service.translate("Hi", null, null));
        assertEquals(LlmException.Kind.REFUSED, e.getKind());
    }
}
