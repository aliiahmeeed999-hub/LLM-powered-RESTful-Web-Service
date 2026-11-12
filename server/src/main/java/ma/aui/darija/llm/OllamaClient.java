package ma.aui.darija.llm;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.stream.JsonParsingException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Client for a lightweight LLM deployed locally with <a href="https://ollama.com">Ollama</a>
 * ({@code POST /api/chat}). No API key, no data leaves the machine.
 * Suggested models: {@code qwen2.5:3b}, {@code gemma2:2b}, {@code llama3.2:3b}.
 */
public class OllamaClient implements LlmClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final double temperature;

    public OllamaClient(HttpClient http, String baseUrl, String model, Duration timeout, double temperature) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout;
        this.temperature = temperature;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        JsonObject body = Json.createObjectBuilder()
                .add("model", model)
                .add("stream", false)
                .add("messages", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("role", "system").add("content", systemPrompt))
                        .add(Json.createObjectBuilder().add("role", "user").add("content", userMessage)))
                .add("options", Json.createObjectBuilder().add("temperature", temperature))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "Cannot reach Ollama at " + baseUrl + " (is `ollama serve` running?): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "Ollama call interrupted", e);
        }

        JsonObject json;
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            json = reader.readObject();
        } catch (JsonParsingException | IllegalStateException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "Ollama returned an unreadable response");
        }

        if (response.statusCode() != 200) {
            String message = json.getString("error", "HTTP " + response.statusCode());
            LlmException.Kind kind = response.statusCode() == 404
                    ? LlmException.Kind.NOT_CONFIGURED   // model not pulled
                    : LlmException.Kind.UNAVAILABLE;
            throw new LlmException(kind, "Ollama error: " + message);
        }

        JsonObject message = json.getJsonObject("message");
        String content = message == null ? null : message.getString("content", null);
        if (content == null || content.isBlank()) {
            throw new LlmException(LlmException.Kind.REFUSED, "Ollama returned an empty answer");
        }
        return content;
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }
}
