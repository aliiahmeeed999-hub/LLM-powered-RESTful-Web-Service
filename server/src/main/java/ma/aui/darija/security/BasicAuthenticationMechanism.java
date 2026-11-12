package ma.aui.darija.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * HTTP Basic authentication (RFC 7617) implemented as a Jakarta Security
 * {@link HttpAuthenticationMechanism}.
 *
 * <p>Jakarta Security bridges every {@code HttpAuthenticationMechanism} to a Jakarta
 * Authentication (JASPIC) {@code ServerAuthModule} registered with the container, so this class
 * is the application's Jakarta Authentication module. It is invoked by the container
 * before every request:</p>
 * <ol>
 *   <li>if an {@code Authorization: Basic ...} header is present, the credentials are
 *       validated through the {@link IdentityStoreHandler} (which uses
 *       {@link InMemoryIdentityStore}) and the caller is logged in for this request;</li>
 *   <li>if the resource is protected (see {@code web.xml}) and authentication failed or no
 *       credentials were sent, a {@code 401} with a {@code WWW-Authenticate} challenge and a
 *       JSON error body is returned;</li>
 *   <li>public resources ({@code /api/health}) are passed through untouched.</li>
 * </ol>
 * The mechanism is stateless: credentials must be sent on every request.
 */
@ApplicationScoped
public class BasicAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String REALM = "DarijaTranslator";

    @Inject
    IdentityStoreHandler identityStoreHandler;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request,
                                                HttpServletResponse response,
                                                HttpMessageContext context) {

        Optional<UsernamePasswordCredential> credential = parseBasicHeader(request.getHeader("Authorization"));

        if (credential.isPresent()) {
            CredentialValidationResult result = identityStoreHandler.validate(credential.get());
            if (result.getStatus() == CredentialValidationResult.Status.VALID) {
                return context.notifyContainerAboutLogin(result);
            }
            if (context.isProtected()) {
                return unauthorized(response, "Invalid username or password.");
            }
            return context.doNothing();
        }

        if (context.isProtected()) {
            return unauthorized(response, "Authentication required (HTTP Basic).");
        }
        return context.doNothing();
    }

    /**
     * Parses {@code Basic base64(user:password)}. Returns empty for any other scheme or a
     * malformed value.
     */
    static Optional<UsernamePasswordCredential> parseBasicHeader(String header) {
        if (header == null || header.length() < 6 || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return Optional.empty();
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int sep = decoded.indexOf(':');
            if (sep <= 0) {
                return Optional.empty();
            }
            return Optional.of(new UsernamePasswordCredential(decoded.substring(0, sep), decoded.substring(sep + 1)));
        } catch (IllegalArgumentException badBase64) {
            return Optional.empty();
        }
    }

    private static AuthenticationStatus unauthorized(HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                    + message.replace("\"", "'") + "\"}");
        } catch (IOException ignored) {
            // the status code alone is enough for the client
        }
        return AuthenticationStatus.SEND_FAILURE;
    }
}
