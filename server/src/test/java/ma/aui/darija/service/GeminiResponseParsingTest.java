package ma.aui.darija.service;

import ma.aui.darija.llm.GeminiClientTestAccess;
import ma.aui.darija.llm.LlmException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiResponseParsingTest {

    @Test
    void extractsTextAndSkipsThoughts() {
        String body = """
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"thinking...","thought":true},
                  {"text":"لاباس عليك؟"}]},"finishReason":"STOP"}]}
                """;
        assertEquals("لاباس عليك؟", GeminiClientTestAccess.extract(200, body));
    }

    @Test
    void mapsHttpErrors() {
        String quota = "{\"error\":{\"code\":429,\"message\":\"Resource has been exhausted\"}}";
        LlmException e = assertThrows(LlmException.class, () -> GeminiClientTestAccess.extract(429, quota));
        assertEquals(LlmException.Kind.RATE_LIMITED, e.getKind());

        LlmException badKey = assertThrows(LlmException.class,
                () -> GeminiClientTestAccess.extract(400, "{\"error\":{\"message\":\"API key not valid\"}}"));
        assertEquals(LlmException.Kind.NOT_CONFIGURED, badKey.getKind());
    }

    @Test
    void blockedPromptIsRefused() {
        String body = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        LlmException e = assertThrows(LlmException.class, () -> GeminiClientTestAccess.extract(200, body));
        assertEquals(LlmException.Kind.REFUSED, e.getKind());
    }
}
