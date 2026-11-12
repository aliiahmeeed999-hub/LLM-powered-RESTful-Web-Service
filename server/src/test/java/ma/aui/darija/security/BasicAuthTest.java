package ma.aui.darija.security;

import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAuthTest {

    private static String basic(String userPass) {
        return "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesValidHeader() {
        Optional<UsernamePasswordCredential> c = BasicAuthenticationMechanism.parseBasicHeader(basic("ali:p:ss"));
        assertTrue(c.isPresent());
        assertEquals("ali", c.get().getCaller());
        assertEquals("p:ss", c.get().getPasswordAsString());
    }

    @Test
    void schemeIsCaseInsensitive() {
        assertTrue(BasicAuthenticationMechanism.parseBasicHeader(basic("a:b").replace("Basic", "basic")).isPresent());
    }

    @Test
    void rejectsMalformedHeaders() {
        assertFalse(BasicAuthenticationMechanism.parseBasicHeader(null).isPresent());
        assertFalse(BasicAuthenticationMechanism.parseBasicHeader("Bearer abc").isPresent());
        assertFalse(BasicAuthenticationMechanism.parseBasicHeader("Basic !!!notbase64").isPresent());
        assertFalse(BasicAuthenticationMechanism.parseBasicHeader(basic("nocolon")).isPresent());
        assertFalse(BasicAuthenticationMechanism.parseBasicHeader(basic(":emptyuser")).isPresent());
    }

    @Test
    void constantTimeEquals() {
        assertTrue(InMemoryIdentityStore.constantTimeEquals("darija2026", "darija2026"));
        assertFalse(InMemoryIdentityStore.constantTimeEquals("darija2026", "darija2027"));
        assertFalse(InMemoryIdentityStore.constantTimeEquals("darija2026", null));
    }
}
