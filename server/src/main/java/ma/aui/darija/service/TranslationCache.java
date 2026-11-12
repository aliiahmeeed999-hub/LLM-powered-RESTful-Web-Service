package ma.aui.darija.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small thread-safe LRU cache of translations. Saves LLM quota (the Gemini free tier is
 * rate limited) and makes repeated requests instant.
 */
public class TranslationCache {

    private final Map<String, String> entries;

    public TranslationCache(int maxEntries) {
        this.entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxEntries;
            }
        });
    }

    public static String key(String text, String sourceLanguage, PromptBuilder.Script script, String model) {
        return model + '\u0001' + script + '\u0001' + sourceLanguage + '\u0001' + text;
    }

    public String get(String key) {
        return entries.get(key);
    }

    public void put(String key, String translation) {
        entries.put(key, translation);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
