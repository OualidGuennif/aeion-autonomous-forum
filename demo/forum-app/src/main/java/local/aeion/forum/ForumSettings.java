// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the typed allowlist of the settings a deployment may give this application.
 *
 * Each documented key is mapped here with an explicit default and range. A value that is not an
 * integer, falls outside its range, or is not a real time zone refuses the startup it is needed
 * for and names only the key, never the raw value. Keys nobody documented are simply absent from
 * this allowlist: they stay unset, they are never logged and they can never reach a response.
 */
@ApplicationScoped
public class ForumSettings {

    static final String REQUEST_KEY = "AGORA_REQUEST_MAX_BYTES";
    static final String TEXT_KEY = "AGORA_TEXT_MAX_CODEPOINTS";
    static final String RENDERED_KEY = "AGORA_RENDERED_MAX_BYTES";
    static final String DATABASE_KEY = "AGORA_DATABASE_ENABLED";
    static final String TIMEZONE_KEY = "agora.business.timezone";

    static final int REQUEST_MAX = 16384;
    static final int TEXT_MAX = 4000;
    static final int RENDERED_MAX = 32768;

    private final int requestMaxBytes;
    private final int textMaxCodepoints;
    private final int renderedMaxBytes;
    private final boolean databaseEnabled;
    private final ZoneId businessZone;

    @Inject
    public ForumSettings(
            @ConfigProperty(name = "agora.requests.max-bytes") Optional<String> requestRaw,
            @ConfigProperty(name = "agora.text.max-codepoints") Optional<String> textRaw,
            @ConfigProperty(name = "agora.rendered.max-bytes") Optional<String> renderedRaw,
            @ConfigProperty(name = "agora.database.enabled") Optional<String> enabledRaw,
            @ConfigProperty(name = "agora.business.timezone", defaultValue = "Europe/Paris") Optional<String> zoneRaw) {
        this.requestMaxBytes = bounded(REQUEST_KEY, value(requestRaw), REQUEST_MAX);
        this.textMaxCodepoints = bounded(TEXT_KEY, value(textRaw), TEXT_MAX);
        this.renderedMaxBytes = bounded(RENDERED_KEY, value(renderedRaw), RENDERED_MAX);
        this.databaseEnabled = truth(DATABASE_KEY, value(enabledRaw));
        this.businessZone = zone(value(zoneRaw));
    }

    /** Build and validate an allowlisted tuple outside CDI, for a test with known settings. */
    public static ForumSettings of(String requestRaw, String textRaw, String renderedRaw,
            String enabledRaw, String zoneRaw) {
        return new ForumSettings(Optional.ofNullable(requestRaw), Optional.ofNullable(textRaw),
                Optional.ofNullable(renderedRaw), Optional.ofNullable(enabledRaw),
                Optional.ofNullable(zoneRaw));
    }

    private static String value(Optional<String> raw) {
        return raw == null ? "" : raw.orElse("");
    }

    /** Largest accepted request body, 16384 bytes: the same envelope every handler receives. */
    public int requestMaxBytes() {
        return requestMaxBytes;
    }

    /** Longest accepted normalized text, 4000 codepoints. */
    public int textMaxCodepoints() {
        return textMaxCodepoints;
    }

    /** Largest accepted rendered document, 32768 bytes: the ceiling a response may reach. */
    public int renderedMaxBytes() {
        return renderedMaxBytes;
    }

    /**
     * False means database-less boot: no migration, no outbox drain, no connection is opened, and
     * the process runs on the session secret alone. True is the database-backed deployment.
     */
    public boolean databaseEnabled() {
        return databaseEnabled;
    }

    /** The one business time zone of the forum: Europe/Paris, read back as a real zone rule. */
    public ZoneId businessZone() {
        return businessZone;
    }

    /** Force the CDI proxy to run the validating constructor now, the way StartupChecks proves it. */
    public void selfCheck() {
        requestMaxBytes();
    }

    private static int bounded(String key, String raw, int max) {
        String value = raw.strip();
        if (value.isEmpty()) {
            return max;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException notAnInteger) {
            throw refusal(key, "must be an integer from 1 to " + max);
        }
        if (parsed < 1 || parsed > max) {
            throw refusal(key, "must be an integer from 1 to " + max);
        }
        return parsed;
    }

    private static boolean truth(String key, String raw) {
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "", "false", "0", "no", "off" -> false;
            case "true", "1", "yes", "on" -> true;
            default -> throw refusal(key, "must be true or false");
        };
    }

    private static ZoneId zone(String raw) {
        try {
            return ZoneId.of(raw.strip().isEmpty() ? "Europe/Paris" : raw.strip());
        } catch (RuntimeException invalid) {
            throw refusal(TIMEZONE_KEY, "must be a valid IANA time zone");
        }
    }

    private static IllegalStateException refusal(String key, String rule) {
        return new IllegalStateException("settings_invalid: " + key + " " + rule);
    }
}
