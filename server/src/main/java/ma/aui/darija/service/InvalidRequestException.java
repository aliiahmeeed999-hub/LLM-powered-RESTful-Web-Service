package ma.aui.darija.service;

/** Client error (bad input). Mapped to HTTP 400 by the REST layer. */
public class InvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }
}
