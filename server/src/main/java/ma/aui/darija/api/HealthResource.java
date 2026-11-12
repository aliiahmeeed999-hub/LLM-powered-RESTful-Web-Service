package ma.aui.darija.api;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import ma.aui.darija.service.TranslationService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public (unauthenticated) status endpoint: {@code GET /api/health}.
 * Lets clients check that the server is up before asking for credentials.
 */
@Path("/health")
@RequestScoped
public class HealthResource {

    @Inject
    TranslationService translationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("service", "Darija Translator");
        status.put("provider", translationService.providerName());
        status.put("model", translationService.modelName());
        status.put("llmConfigured", translationService.isLlmConfigured());
        status.put("time", Instant.now().toString());
        return status;
    }
}
