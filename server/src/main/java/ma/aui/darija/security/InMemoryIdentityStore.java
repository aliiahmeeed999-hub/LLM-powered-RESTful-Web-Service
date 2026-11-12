package ma.aui.darija.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import ma.aui.darija.config.AppConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Jakarta Security {@link IdentityStore} that validates username/password pairs against the
 * users configured in {@code TRANSLATOR_USERS}. Every valid caller gets the
 * {@value #ROLE} role (group).
 */
@ApplicationScoped
public class InMemoryIdentityStore implements IdentityStore {

    public static final String ROLE = "translator";

    private static final Logger LOG = Logger.getLogger(InMemoryIdentityStore.class.getName());

    @Inject
    AppConfig config;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential upc)) {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        String caller = upc.getCaller();
        Map<String, String> users = config.users();
        String expected = users.get(caller);

        // Always run the comparison (against a dummy value if the user is unknown)
        // so that response time does not reveal which usernames exist.
        boolean match = constantTimeEquals(expected == null ? "\u0000invalid" : expected,
                upc.getPasswordAsString());

        if (expected != null && match) {
            return new CredentialValidationResult(caller, Set.of(ROLE));
        }
        LOG.warning(() -> "Rejected credentials for caller '" + caller + "'");
        return CredentialValidationResult.INVALID_RESULT;
    }

    static boolean constantTimeEquals(String a, String b) {
        if (b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
