package local.aeion.forum;

import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the three bounded values the forum is allowed to read from the environment.
 *
 * A deployment may lower a cap, never raise it past its ceiling, and a value that is not an
 * integer in range refuses the start. A refusal names the key and the allowed range, and never
 * the offending value, so a log cannot turn into a mirror of the environment.
 *
 * The caps are read once, at startup, through {@link StartupChecks}: a half-configured deployment
 * would otherwise answer requests before refusing. {@code AGORA_RENDERED_MAX_BYTES} is validated
 * and carried here for the rendering feature; the other two are enforced in the request path
 * ({@link RequestValidationFilter} for bytes, {@link ThreadService} for normalized text).
 */
@jakarta.enterprise.context.ApplicationScoped
public class ForumBounds {

    static final String REQUEST_KEY = "AGORA_REQUEST_MAX_BYTES";
    static final String TEXT_KEY = "AGORA_TEXT_MAX_CODEPOINTS";
    static final String RENDERED_KEY = "AGORA_RENDERED_MAX_BYTES";

    static final int DEFAULT_REQUEST_BYTES = 16384;
    static final int DEFAULT_TEXT_CODEPOINTS = 4000;
    static final int DEFAULT_RENDERED_BYTES = 32768;

    static final int MIN_REQUEST_BYTES = 1;
    static final int MAX_REQUEST_BYTES = 16384;
    static final int MIN_TEXT_CODEPOINTS = 1;
    static final int MAX_TEXT_CODEPOINTS = 4000;
    static final int MIN_RENDERED_BYTES = 1;
    static final int MAX_RENDERED_BYTES = 32768;

    private final Optional<String> requestRaw;
    private final Optional<String> textRaw;
    private final Optional<String> renderedRaw;

    private volatile boolean settled;
    private volatile int requestBytes;
    private volatile int textPoints;
    private volatile int renderedBytes;

    @jakarta.inject.Inject
    public ForumBounds(@ConfigProperty(name = "agora.request.max-bytes") Optional<String> requestRaw,
                       @ConfigProperty(name = "agora.text.max-codepoints") Optional<String> textRaw,
                       @ConfigProperty(name = "agora.rendered.max-bytes") Optional<String> renderedRaw) {
        this.requestRaw = requestRaw;
        this.textRaw = textRaw;
        this.renderedRaw = renderedRaw;
    }

    /** Build the bounds outside CDI, for a test that wants specific keys. */
    ForumBounds(Optional<String> requestRaw, Optional<String> textRaw, Optional<String> renderedRaw,
                boolean direct) {
        this(requestRaw, textRaw, renderedRaw);
    }

    /**
     * Parse every bound now. Called at startup ({@link StartupChecks}); the same paths run on the
     * first read, so a test can call {@link #selfCheck()} directly and see the same refusal.
     */
    public void selfCheck() {
        ensure();
    }

    /** The request byte cap, refused with a 413 before a handler sees an over-sized body. */
    public int requestMaxBytes() {
        ensure();
        return requestBytes;
    }

    /** The normalized-text cap in codepoints, enforced on stripped values a caller stores. */
    public int textMaxCodepoints() {
        ensure();
        return textPoints;
    }

    /** The rendered-byte cap, validated and carried for the rendering feature. */
    public int renderedMaxBytes() {
        ensure();
        return renderedBytes;
    }

    private synchronized void ensure() {
        if (settled) {
            return;
        }
        requestBytes = bound(REQUEST_KEY, requestRaw, MIN_REQUEST_BYTES, MAX_REQUEST_BYTES, DEFAULT_REQUEST_BYTES);
        textPoints = bound(TEXT_KEY, textRaw, MIN_TEXT_CODEPOINTS, MAX_TEXT_CODEPOINTS, DEFAULT_TEXT_CODEPOINTS);
        renderedBytes = bound(RENDERED_KEY, renderedRaw, MIN_RENDERED_BYTES, MAX_RENDERED_BYTES, DEFAULT_RENDERED_BYTES);
        settled = true;
    }

    /**
     * Absent means the documented default. A present value must be plain digits within the
     * range; anything else refuses the start. The range is part of the message, the value is not.
     */
    static int bound(String key, Optional<String> raw, int min, int max, int fallback) {
        String value = raw == null ? null : raw.orElse(null);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw refused(key, min, max);
        }
        if (!trimmed.matches("\\d+")) {
            throw refused(key, min, max);
        }
        int parsed;
        try {
            parsed = Integer.parseInt(trimmed);
        } catch (NumberFormatException outOfNumeric) {
            throw refused(key, min, max);
        }
        if (parsed < min || parsed > max) {
            throw refused(key, min, max);
        }
        return parsed;
    }

    private static IllegalStateException refused(String key, int min, int max) {
        return new IllegalStateException("bound_invalid: " + key
                + " must be an integer between " + min + " and " + max);
    }
}
