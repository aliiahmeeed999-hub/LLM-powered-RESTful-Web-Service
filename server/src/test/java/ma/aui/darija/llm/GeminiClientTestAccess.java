package ma.aui.darija.llm;

/** Exposes package-private parsing logic of {@link GeminiClient} to tests in other packages. */
public final class GeminiClientTestAccess {

    private GeminiClientTestAccess() {
    }

    public static String extract(int status, String body) {
        return GeminiClient.extractText(status, body);
    }
}
