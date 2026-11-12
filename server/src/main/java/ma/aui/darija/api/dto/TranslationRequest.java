package ma.aui.darija.api.dto;

/**
 * JSON body of {@code POST /api/translate}.
 * <pre>{ "text": "Hello, how are you?", "sourceLang": "en", "script": "arabic" }</pre>
 * Only {@code text} is required.
 */
public class TranslationRequest {

    private String text;
    private String sourceLang;
    private String script;

    public TranslationRequest() {
    }

    public TranslationRequest(String text, String sourceLang, String script) {
        this.text = text;
        this.sourceLang = sourceLang;
        this.script = script;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public void setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
