package ma.aui.darija.service;

/**
 * Outcome of a translation, independent of the transport (REST DTOs are built from it).
 */
public record TranslationResult(
        String original,
        String translation,
        String sourceLanguage,
        PromptBuilder.Script script,
        String provider,
        String model,
        boolean cached,
        long elapsedMs) {
}
