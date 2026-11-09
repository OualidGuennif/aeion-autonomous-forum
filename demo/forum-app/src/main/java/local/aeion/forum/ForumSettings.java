package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the application's typed configuration, read once and validated before serving.
 *
 * Every setting here has a bounded default and an environment key an operator sets. The rules are
 * small on purpose:
 *
 * <ul>
 *   <li>an unknown environment variable is ignored — nothing reads the whole environment;</li>
 *   <li>a value that is missing, not a number or out of range refuses the <em>start</em>, not the
 *       first request, so a half-configured deployment is never reachable;</li>
 *   <li>a refusal names the key it refused and nothing else. No value, no length and no default
 *       is written to a log, a page or an exception message.</li>
 * </ul>
 *
 * The values are read but never rendered: no endpoint exposes them, and the health endpoint
 * answers liveness only.
 */
@ApplicationScoped
public class ForumSettings {

    /** The environment keys an operator sets. A refusal names one of them, never a value. */
    static final String DATABASE_ENABLED = "AGORA_DATABASE_ENABLED";
    static final String REQUEST_MAX_BYTES = "AGORA_REQUEST_MAX_BYTES";
    static final String TEXT_MAX_CODEPOINTS = "AGORA_TEXT_MAX_CODEPOINTS";
    static final String RENDERED_MAX_BYTES = "AGORA_RENDERED_MAX_BYTES";

    /** The business timezone of the forum: dates are rendered in Europe/Paris. */
    static final String BUSINESS_TIMEZONE = "Europe/Paris";

    /** The ceilings of the bounded settings. A value above one of them refuses the start. */
    static final int REQUEST_MAX_BYTES_LIMIT = 16384;
    static final int TEXT_MAX_CODEPOINTS_LIMIT = 4000;
    static final int RENDERED_MAX_BYTES_LIMIT = 32768;

    private final String databaseEnabled;
    private final String requestMaxBytes;
    private final String textMaxCodepoints;
    private final String renderedMaxBytes;
    private final String businessTimezone;

    @jakarta.inject.Inject
    public ForumSettings(
            @ConfigProperty(name = "agora.database.enabled") Optional<String> databaseEnabled,
            @ConfigProperty(name = "agora.request.max.bytes") Optional<String> requestMaxBytes,
            @ConfigProperty(name = "agora.text.max.codepoints") Optional<String> textMaxCodepoints,
            @ConfigProperty(name = "agora.rendered.max.bytes") Optional<String> renderedMaxBytes,
            @ConfigProperty(name = "agora.business.timezone") Optional<String> businessTimezone) {
        this.databaseEnabled = orNull(databaseEnabled);
        this.requestMaxBytes = orNull(requestMaxBytes);
        this.textMaxCodepoints = orNull(textMaxCodepoints);
        this.renderedMaxBytes = orNull(renderedMaxBytes);
        this.businessTimezone = orNull(businessTimezone);
    }

    /** Build a configuration from raw strings, for a test that wants a known value. */
    public static ForumSettings of(String databaseEnabled, String requestMaxBytes,
            String textMaxCodepoints, String renderedMaxBytes) {
        return new ForumSettings(
                Optional.ofNullable(databaseEnabled),
                Optional.ofNullable(requestMaxBytes),
                Optional.ofNullable(textMaxCodepoints),
                Optional.ofNullable(renderedMaxBytes),
                Optional.empty());
    }

    /**
     * True when the database-backed features are enabled.
     *
     * Absent means disabled: a deployment that says nothing about its database gets the skeleton
     * alone — liveness, no migrations, no ORM and no connection.
     */
    public boolean databaseEnabled() {
        String raw = databaseEnabled;
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> throw new IllegalStateException("settings_database_enabled_invalid: "
                    + DATABASE_ENABLED + " must be true or false");
        };
    }

    /** The request body ceiling in bytes; a body above it is refused with 413. */
    public int requestMaxBytes() {
        return bounded("settings_request_max_bytes_invalid", REQUEST_MAX_BYTES, requestMaxBytes,
                1, REQUEST_MAX_BYTES_LIMIT);
    }

    /** The cap of a normalized text, counted in code points and not in UTF-16 units. */
    public int textMaxCodepoints() {
        return bounded("settings_text_max_codepoints_invalid", TEXT_MAX_CODEPOINTS, textMaxCodepoints,
                1, TEXT_MAX_CODEPOINTS_LIMIT);
    }

    /** The ceiling of a rendered text, counted in UTF-8 bytes. */
    public int renderedMaxBytes() {
        return bounded("settings_rendered_max_bytes_invalid", RENDERED_MAX_BYTES, renderedMaxBytes,
                1, RENDERED_MAX_BYTES_LIMIT);
    }

    /** The business timezone; dates are rendered in it. */
    public ZoneId businessZone() {
        String raw = businessTimezone == null || businessTimezone.isBlank()
                ? BUSINESS_TIMEZONE
                : businessTimezone.strip();
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException unknown) {
            throw new IllegalStateException("settings_business_timezone_invalid: "
                    + "agora.business.timezone must be a known time zone");
        }
    }

    /**
     * Validate every setting.
     *
     * Called during startup, after the session secret has been checked, so the refusal of a
     * missing secret names only that key. A setting that is not a number, or is outside its
     * range, stops the process here instead of failing a request later.
     */
    public void selfCheck() {
        databaseEnabled();
        requestMaxBytes();
        textMaxCodepoints();
        renderedMaxBytes();
        businessZone();
    }

    /**
     * Read a bounded integer.
     *
     * The refusal names the key and the range it accepts. The value that was refused is not part
     * of the message: an operator reads what is wrong, not what they typed.
     */
    private static int bounded(String code, String key, String raw, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return max;
        }
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException notANumber) {
            throw new IllegalStateException(code + ": " + key + " must be an integer between "
                    + min + " and " + max);
        }
        if (value < min || value > max) {
            throw new IllegalStateException(code + ": " + key + " must be an integer between "
                    + min + " and " + max);
        }
        return value;
    }

    private static String orNull(Optional<String> value) {
        return value == null ? null : value.orElse(null);
    }
}
