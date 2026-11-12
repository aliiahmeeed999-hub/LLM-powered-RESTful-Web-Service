package ma.aui.darija.llm;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Google Gemini client using the public REST API
 * ({@code POST /v1beta/models/{model}:generateContent}).
 *
 * @see <a href="https://ai.google.dev/api/generate-content">Gemini generateContent</a>
 */
public class GeminiClient implements LlmClient {

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final double temperature;

    public GeminiClient(HttpClient http, String baseUrl, String apiKey, String model,
                        Duration timeout, double temperature) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.temperature = temperature;
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        if (!isConfigured()) {
            throw new LlmException(LlmException.Kind.NOT_CONFIGURED,
                    "GEMINI_API_KEY is not set. Add it to server.env or export it before starting the server.");
        }

        JsonObject body = Json.createObjectBuilder()
                .add("systemInstruction", Json.createObjectBuilder()
                        .add("parts", Json.createArrayBuilder()
                                .add(Json.createObjectBuilder().add("text", systemPrompt))))
                .add("contents", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("role", "user")
                                .add("parts", Json.createArrayBuilder()
                                        .add(Json.createObjectBuilder().add("text", userMessage)))))
                .add("generationConfig", Json.createObjectBuilder()
                        .add("temperature", temperature)
                        .add("candidateCount", 1))
                .build();

        String url = baseUrl + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "Cannot reach Gemini: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "Gemini call interrupted", e);
        }

        return extractText(response.statusCode(), response.body());
    }

    /** Parses a generateContent response (package-private for unit tests). */
    static String extractText(int status, String responseBody) {
        JsonObject json = parse(responseBody);

        if (status != 200) {
            String message = json != null && json.containsKey("error")
                    ? json.getJsonObject("error").getString("message", "unknown error")
                    : "HTTP " + status;
            LlmException.Kind kind = switch (status) {
                case 429 -> LlmException.Kind.RATE_LIMITED;
                case 400, 401, 403 -> LlmException.Kind.NOT_CONFIGURED;
                default -> LlmException.Kind.UNAVAILABLE;
            };
            throw new LlmException(kind, "Gemini error (" + status + "): " + message);
        }
        if (json == null) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "Gemini returned an unreadable response");
        }

        JsonObject feedback = json.getJsonObject("promptFeedback");
        if (feedback != null && feedback.containsKey("blockReason")) {
            throw new LlmException(LlmException.Kind.REFUSED,
                    "Gemini blocked the request: " + feedback.getString("blockReason"));
        }

        JsonArray candidates = json.getJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new LlmException(LlmException.Kind.REFUSED, "Gemini returned no candidates");
        }
        JsonObject candidate = candidates.getJsonObject(0);
        JsonObject content = candidate.getJsonObject("content");
        JsonArray parts = content == null ? null : content.getJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            String reason = candidate.getString("finishReason", "UNKNOWN");
            throw new LlmException(LlmException.Kind.REFUSED, "Gemini returned an empty answer (" + reason + ")");
        }

        StringBuilder text = new StringBuilder();
        for (JsonValue part : parts) {
            if (part.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject p = part.asJsonObject();
                // "thought" parts (thinking models) are not part of the answer
                if (p.containsKey("text") && !p.getBoolean("thought", false)) {
                    text.append(p.getString("text"));
                }
            }
        }
        return text.toString();
    }

    private static JsonObject parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        } catch (JsonParsingException | IllegalStateException | ClassCastException e) {
            return null;
        }
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
