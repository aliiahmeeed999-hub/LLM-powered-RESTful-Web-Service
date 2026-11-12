package ma.aui.darija.llm;

/** Raised when the LLM provider cannot produce a translation. */
public class LlmException extends RuntimeException {

    /** Why the call failed; mapped to an HTTP status by the REST layer. */
    public enum Kind {
        /** Missing API key / provider unknown -> 503. */
        NOT_CONFIGURED,
        /** Network error, timeout, 5xx from provider -> 502. */
        UNAVAILABLE,
        /** Provider quota exhausted (HTTP 429) -> 429. */
        RATE_LIMITED,
        /** Content blocked or empty answer -> 422. */
        REFUSED
    }

    private static final long serialVersionUID = 1L;

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
