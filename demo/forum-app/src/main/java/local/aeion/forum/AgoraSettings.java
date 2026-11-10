// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the environment is an allowlist of typed, bounded settings, and nothing else is read.
 *
 * Every value is parsed here instead of by the framework, so an unknown or out-of-range value
 * refuses startup with a message that names only the key: configured values never enter a log, an
 * error text or a page. The database toggle selects database-less boot (the default): a deployment
 * with only a session secret carries no datasource and no Hibernate at all, and the health endpoint
 * answers without a connection. Enabling it restores the database-backed behaviour.
 */
@ApplicationScoped
public class AgoraSettings {

    static final String DATABASE_ENABLED_KEY = "AGORA_DATABASE_ENABLED";
    static final String REQUEST_MAX_BYTES_KEY = "AGORA_REQUEST_MAX_BYTES";
    static final String TEXT_MAX_CODEPOINTS_KEY = "AGORA_TEXT_MAX_CODEPOINTS";
    static final String RENDERED_MAX_BYTES_KEY = "AGORA_RENDERED_MAX_BYTES";
    static final String TIME_ZONE_KEY = "agora.time-zone";
    static final String DATASOURCE_URL_KEY = "quarkus.datasource.jdbc.url";

    static final int REQUEST_MAX_BYTES_MIN = 1;
    static final int REQUEST_MAX_BYTES_MAX = 16384;
    static final int REQUEST_MAX_BYTES_DEFAULT = 16384;
    static final int TEXT_MAX_CODEPOINTS_MIN = 1;
    static final int TEXT_MAX_CODEPOINTS_MAX = 4000;
    static final int TEXT_MAX_CODEPOINTS_DEFAULT = 4000;
    static final int RENDERED_MAX_BYTES_MIN = 1;
    static final int RENDERED_MAX_BYTES_MAX = 32768;
    static final int RENDERED_MAX_BYTES_DEFAULT = 32768;
    static final String TIME_ZONE_DEFAULT = "Europe/Paris";

    private final boolean databaseEnabled;
    private final boolean datasourceUrlConfigured;
    private final int requestMaxBytes;
    private final int textMaxCodepoints;
    private final int renderedMaxBytes;
    private final ZoneId businessTimeZone;

    @Inject
    public AgoraSettings(
            @ConfigProperty(name = "agora.database.enabled", defaultValue = "false")
            Optional<String> databaseEnabled,
            @ConfigProperty(name = "agora.request.max-bytes") Optional<String> requestMaxBytes,
            @ConfigProperty(name = "agora.text.max-codepoints") Optional<String> textMaxCodepoints,
            @ConfigProperty(name = "agora.rendered.max-bytes") Optional<String> renderedMaxBytes,
            @ConfigProperty(name = "agora.time-zone", defaultValue = TIME_ZONE_DEFAULT) Optional<String> timeZone,
            @ConfigProperty(name = DATASOURCE_URL_KEY, defaultValue = "") Optional<String> datasourceUrl) {
        this(databaseEnabled.orElse(null), requestMaxBytes.orElse(null), textMaxCodepoints.orElse(null),
                renderedMaxBytes.orElse(null), timeZone.orElse(null), datasourceUrl.orElse(null));
    }

    /** Direct construction, for a test that pins one documented key at a time. */
    AgoraSettings(String databaseEnabled, String requestMaxBytes, String textMaxCodepoints,
            String renderedMaxBytes, String timeZone, String datasourceUrl) {
        boolean database = parseDatabaseEnabled(databaseEnabled);
        boolean urlConfigured = datasourceUrl != null && !datasourceUrl.strip().isEmpty();
        if (database && !urlConfigured) {
            throw new IllegalStateException("datasource_required: " + DATABASE_ENABLED_KEY
                    + " is true and requires a datasource URL");
        }
        this.databaseEnabled = database;
        this.datasourceUrlConfigured = urlConfigured;
        this.requestMaxBytes = parseBound(requestMaxBytes, REQUEST_MAX_BYTES_KEY,
                REQUEST_MAX_BYTES_MIN, REQUEST_MAX_BYTES_MAX, REQUEST_MAX_BYTES_DEFAULT);
        this.textMaxCodepoints = parseBound(textMaxCodepoints, TEXT_MAX_CODEPOINTS_KEY,
                TEXT_MAX_CODEPOINTS_MIN, TEXT_MAX_CODEPOINTS_MAX, TEXT_MAX_CODEPOINTS_DEFAULT);
        this.renderedMaxBytes = parseBound(renderedMaxBytes, RENDERED_MAX_BYTES_KEY,
                RENDERED_MAX_BYTES_MIN, RENDERED_MAX_BYTES_MAX, RENDERED_MAX_BYTES_DEFAULT);
        this.businessTimeZone = parseTimeZone(timeZone);
    }

    /** Fail-closed database-backed features are on; false is the database-less boot. */
    public boolean databaseEnabled() {
        return databaseEnabled;
    }

    /** The request cap in bytes: what a request entity may weigh before it is refused. */
    public int requestMaxBytes() {
        return requestMaxBytes;
    }

    /** The normalized-text cap in codepoints: what a stored string may hold. */
    public int textMaxCodepoints() {
        return textMaxCodepoints;
    }

    /** The rendered cap in bytes: what a rendered page may emit. */
    public int renderedMaxBytes() {
        return renderedMaxBytes;
    }

    /** The business timezone; timestamps on the wire stay epoch nanoseconds either way. */
    public ZoneId businessTimeZone() {
        return businessTimeZone;
    }

    /** True when a datasource URL is configured; a database-enabled boot refuses without one. */
    boolean datasourceUrlConfigured() {
        return datasourceUrlConfigured;
    }

    private static boolean parseDatabaseEnabled(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return false;
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return "true".equals(normalized);
        }
        throw new IllegalStateException("setting_out_of_bounds: " + DATABASE_ENABLED_KEY + " must be true or false");
    }

    private static int parseBound(String raw, String key, int min, int max, int fallback) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // The failing value stays out of the refusal: the message names the key, nothing else.
            throw new IllegalStateException(rangeMessage(key, min, max));
        }
        if (parsed < min || parsed > max) {
            throw new IllegalStateException(rangeMessage(key, min, max));
        }
        return parsed;
    }

    private static ZoneId parseTimeZone(String raw) {
        String value = raw == null ? "" : raw.strip();
        try {
            return ZoneId.of(value.isEmpty() ? TIME_ZONE_DEFAULT : value);
        } catch (RuntimeException ignored) {
            throw new IllegalStateException("setting_out_of_bounds: " + TIME_ZONE_KEY + " must be a valid zone id");
        }
    }

    private static String rangeMessage(String key, int min, int max) {
        return "setting_out_of_bounds: " + key + " must be an integer between " + min + " and " + max;
    }
}
