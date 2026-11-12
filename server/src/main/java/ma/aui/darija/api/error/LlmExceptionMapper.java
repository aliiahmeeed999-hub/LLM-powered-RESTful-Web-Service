package ma.aui.darija.api.error;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ma.aui.darija.api.dto.ErrorResponse;
import ma.aui.darija.llm.LlmException;

import java.util.logging.Logger;

/**
 * LLM failures -> meaningful HTTP status codes:
 * 503 (not configured), 502 (provider unreachable), 429 (quota), 422 (refused / empty).
 */
@Provider
public class LlmExceptionMapper implements ExceptionMapper<LlmException> {

    private static final Logger LOG = Logger.getLogger(LlmExceptionMapper.class.getName());

    @Override
    public Response toResponse(LlmException e) {
        int status = switch (e.getKind()) {
            case NOT_CONFIGURED -> 503;
            case UNAVAILABLE -> 502;
            case RATE_LIMITED -> 429;
            case REFUSED -> 422;
        };
        String error = switch (status) {
            case 503 -> "Service Unavailable";
            case 502 -> "Bad Gateway";
            case 429 -> "Too Many Requests";
            default -> "Unprocessable Content";
        };
        LOG.warning(() -> "LLM error [" + e.getKind() + "]: " + e.getMessage());

        Response.ResponseBuilder builder = Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(status, error, e.getMessage()));
        if (status == 429) {
            builder.header("Retry-After", "30");
        }
        return builder.build();
    }
}
