package ma.aui.darija.llm;

/**
 * Abstraction over a Large Language Model able to answer a single-turn chat request.
 * Implementations: {@link GeminiClient} (Google Gemini, cloud, free tier) and
 * {@link OllamaClient} (lightweight model running locally).
 */
public interface LlmClient {

    /**
     * Sends one request to the model.
     *
     * @param systemPrompt instructions that frame the model's behaviour
     * @param userMessage  the actual content to process
     * @return the raw text produced by the model
     * @throws LlmException when the provider is unreachable, misconfigured or refuses the request
     */
    String complete(String systemPrompt, String userMessage) throws LlmException;

    /** Short provider id, e.g. {@code gemini}. */
    String provider();

    /** Model name, e.g. {@code gemini-2.5-flash}. */
    String model();

    /** {@code true} when the client has everything it needs (API key, URL...). */
    boolean isConfigured();
}
