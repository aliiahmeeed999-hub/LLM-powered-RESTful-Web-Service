package ma.aui.darija.api;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import ma.aui.darija.api.dto.TranslationRequest;
import ma.aui.darija.api.dto.TranslationResponse;
import ma.aui.darija.service.InvalidRequestException;
import ma.aui.darija.service.TranslationResult;
import ma.aui.darija.service.TranslationService;

/**
 * <b>TranslatorResource</b> - the RESTful endpoint of the project.
 *
 * <p>Base URL: {@code http://localhost:9080/translator/api/translate}.
 * Every method requires HTTP Basic authentication (role {@code translator}).</p>
 *
 * <table>
 *   <caption>Operations</caption>
 *   <tr><th>Method</th><th>Content</th><th>Description</th></tr>
 *   <tr><td>POST</td><td>application/json</td><td>{@code {"text": "...", "sourceLang": "en", "script": "arabic"}}
 *       &rarr; JSON {@link TranslationResponse}</td></tr>
 *   <tr><td>POST</td><td>text/plain</td><td>raw text &rarr; raw Darija text</td></tr>
 *   <tr><td>GET</td><td>query params</td><td>{@code ?text=...&sourceLang=en&script=latin} &rarr; JSON</td></tr>
 * </table>
 */
@Path("/translate")
@RequestScoped
@RolesAllowed("translator")
public class TranslatorResource {

    public static final String HEADER_PROVIDER = "X-Translation-Provider";
    public static final String HEADER_CACHE = "X-Translation-Cache";

    @Inject
    TranslationService translationService;

    @Context
    SecurityContext securityContext;

    /** {@code POST /api/translate} with a JSON body - the main business method. */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response translate(TranslationRequest request) {
        if (request == null) {
            throw new InvalidRequestException("A JSON body {\"text\": \"...\"} is required.");
        }
        TranslationResult result = translationService.translate(
                request.getText(), request.getSourceLang(), request.getScript());
        return json(result);
    }

    /** {@code POST /api/translate} with a {@code text/plain} body: returns only the translation. */
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN + ";charset=UTF-8")
    public Response translate(String text,
                              @QueryParam("sourceLang") String sourceLang,
                              @QueryParam("script") @DefaultValue("arabic") String script) {
        TranslationResult result = translationService.translate(text, sourceLang, script);
        return Response.ok(result.translation())
                .header(HEADER_PROVIDER, result.provider() + "/" + result.model())
                .header(HEADER_CACHE, result.cached() ? "HIT" : "MISS")
                .build();
    }

    /** {@code GET /api/translate?text=...} - handy for a quick test in the browser or cURL. */
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response translateQuery(@QueryParam("text") String text,
                                   @QueryParam("sourceLang") String sourceLang,
                                   @QueryParam("script") @DefaultValue("arabic") String script) {
        return json(translationService.translate(text, sourceLang, script));
    }

    private Response json(TranslationResult result) {
        String user = securityContext.getUserPrincipal() == null
                ? null : securityContext.getUserPrincipal().getName();
        return Response.ok(TranslationResponse.from(result, user))
                .header(HEADER_PROVIDER, result.provider() + "/" + result.model())
                .header(HEADER_CACHE, result.cached() ? "HIT" : "MISS")
                .build();
    }
}
