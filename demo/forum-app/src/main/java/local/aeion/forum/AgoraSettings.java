package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the allowlisted runtime settings, read once and validated before anything serves.
 *
 * Only the keys named here are understood by this bean; the required session secret has its own
 * fail-closed validator in {@link SessionSecret}. Every value is parsed and range-checked at bean
 * creation, and a refusal names the key only: neither the supplied value nor any other setting is
 * echoed into a log line, an exception message or a page. A key the application does not know is
 * simply never read, so it cannot appear anywhere either.
 *
 * The defaults are the documented ceilings. {@code AGORA_DATABASE_ENABLED} defaults to false, so a
 * deployment can boot with nothing but a session secret; database-backed features are opt-in and
 * keep the existing SQLite behaviour when enabled.
 */
@ApplicationScoped
public class AgoraSettings {

    static final String DATABASE_ENABLED_KEY = "AGORA_DATABASE_ENABLED";
    static final String REQUEST_MAX_BYTES_KEY = "AGORA_REQUEST_MAX_BYTES";
    static final String TEXT_MAX_CODEPOINTS_KEY = "AGORA_TEXT_MAX_CODEPOINTS";
    static final String RENDERED_MAX_BYTES_KEY = "AGORA_RENDERED_MAX_BYTES";

    static final int REQUEST_MAX_BYTES_CEILING = 16384;
    static final int TEXT_MAX_CODEPOINTS_CEILING = 4000;
    static final int RENDERED_MAX_BYTES_CEILING = 32768;

    /** Business dates are rendered in the forum's one timezone, never in the host's. */
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Paris");

    private final boolean databaseEnabled;
    private final int requestMaxBytes;
    private final int textMaxCodepoints;
    private final int renderedMaxBytes;

    @Inject
    public AgoraSettings(
            @ConfigProperty(name = "agora.database.enabled", defaultValue = "false") String databaseEnabled,
            @ConfigProperty(name = "agora.request.max-bytes", defaultValue = "16384") String requestMaxBytes,
            @ConfigProperty(name = "agora.text.max-codepoints", defaultValue = "4000") String textMaxCodepoints,
            @ConfigProperty(name = "agora.rendered.max-bytes", defaultValue = "32768") String renderedMaxBytes) {
        this.databaseEnabled = bool(DATABASE_ENABLED_KEY, databaseEnabled);
        this.requestMaxBytes =
                number(REQUEST_MAX_BYTES_KEY, requestMaxBytes, 1, REQUEST_MAX_BYTES_CEILING);
        this.textMaxCodepoints =
                number(TEXT_MAX_CODEPOINTS_KEY, textMaxCodepoints, 1, TEXT_MAX_CODEPOINTS_CEILING);
        this.renderedMaxBytes =
                number(RENDERED_MAX_BYTES_KEY, renderedMaxBytes, 1, RENDERED_MAX_BYTES_CEILING);
    }

    /** Build settings outside CDI, for a test that wants explicit values. */
    static AgoraSettings of(boolean databaseEnabled, int requestMaxBytes, int textMaxCodepoints, int renderedMaxBytes) {
        return new AgoraSettings(
                Boolean.toString(databaseEnabled),
                Integer.toString(requestMaxBytes),
                Integer.toString(textMaxCodepoints),
                Integer.toString(renderedMaxBytes));
    }

    /** Force the bean to be built now: a normal-scoped proxy would otherwise validate lazily. */
    public void selfCheck() {
        // The constructor is the check; reaching here means every value passed it.
    }

    public boolean databaseEnabled() {
        return databaseEnabled;
    }

    public int requestMaxBytes() {
        return requestMaxBytes;
    }

    public int textMaxCodepoints() {
        return textMaxCodepoints;
    }

    public int renderedMaxBytes() {
        return renderedMaxBytes;
    }

    public ZoneId businessZone() {
        return BUSINESS_ZONE;
    }

    private static boolean bool(String key, String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw new IllegalStateException("invalid_setting: " + key + " must be true or false");
    }

    private static int number(String key, String raw, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException notANumber) {
            throw new IllegalStateException(
                    "invalid_setting: " + key + " must be an integer within its permitted range");
        }
        if (value < min || value > max) {
            throw new IllegalStateException(
                    "invalid_setting: " + key + " must be an integer within its permitted range");
        }
        return value;
    }
}
