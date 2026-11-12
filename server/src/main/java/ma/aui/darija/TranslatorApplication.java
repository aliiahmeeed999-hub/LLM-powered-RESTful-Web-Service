package ma.aui.darija;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activates Jakarta RESTful Web Services and mounts every resource under {@code /api}.
 *
 * <p>Full base URL with the default Open Liberty configuration:
 * {@code http://localhost:9080/translator/api}</p>
 */
@ApplicationPath("/api")
public class TranslatorApplication extends Application {
    // All @Path resources and @Provider classes are discovered automatically.
}
