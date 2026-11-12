package ma.aui.darija.api.error;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ma.aui.darija.api.dto.ErrorResponse;
import ma.aui.darija.service.InvalidRequestException;

/** Bad input -> {@code 400 Bad Request} with a JSON body. */
@Provider
public class InvalidRequestExceptionMapper implements ExceptionMapper<InvalidRequestException> {

    @Override
    public Response toResponse(InvalidRequestException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(400, "Bad Request", e.getMessage()))
                .build();
    }
}
