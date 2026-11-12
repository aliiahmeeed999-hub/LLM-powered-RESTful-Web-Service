package ma.aui.darija.llm;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import ma.aui.darija.config.AppConfig;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * CDI producer (factory) that picks the {@link LlmClient} implementation from
 * {@code LLM_PROVIDER}. The rest of the application only depends on the interface.
 */
@ApplicationScoped
public class LlmClientProducer {

    private static final Logger LOG = Logger.getLogger(LlmClientProducer.class.getName());

    @Inject
    AppConfig config;

    @Produces
    @ApplicationScoped
    public LlmClient llmClient() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        LlmClient client = switch (config.llmProvider()) {
            case "ollama" -> new OllamaClient(http, config.ollamaUrl(), config.ollamaModel(),
                    config.llmTimeout(), config.temperature());
            case "gemini" -> new GeminiClient(http, config.geminiBaseUrl(), config.geminiApiKey(),
                    config.geminiModel(), config.llmTimeout(), config.temperature());
            default -> {
                LOG.warning("Unknown LLM_PROVIDER '" + config.llmProvider() + "', falling back to gemini");
                yield new GeminiClient(http, config.geminiBaseUrl(), config.geminiApiKey(),
                        config.geminiModel(), config.llmTimeout(), config.temperature());
            }
        };

        LOG.info(() -> "LLM provider: " + client.provider() + " / model: " + client.model()
                + (client.isConfigured() ? "" : "  (NOT CONFIGURED - check server.env)"));
        return client;
    }
}
