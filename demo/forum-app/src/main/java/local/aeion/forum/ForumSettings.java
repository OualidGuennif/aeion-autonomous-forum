package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the settings the application reads, bounded and checked before it serves anything.
 *
 * Only the documented keys are read. Everything else in the environment is ignored, and a refusal
 * names the key and its bound and never the value that was supplied: a bound typed on a keyboard by
 * mistake is not a credential, and it still does not belong in a log line or a support ticket.
 *
 * The bounds are ceilings, not suggestions. A deployment that asks for a 40 MB request body or a
 * 100 000-codepoint post does not have a wider forum, it has a forum one request can take down, so
 * starting refuses rather than serving and pretending.
 *
 * {@code agora.database.enabled} is the fail-closed switch for database-backed features. It is off
 * unless it is turned on, which is what lets a minimal deployment boot, answer {@code /healthz} and
 * hold no connection at all. Turned on, it makes the connection a required setting — and that
 * requirement is asked for only once the secret has been accepted, because a deployment missing both
 * has to be told about the one that stops it signing sessions.
 */
@ApplicationScoped
public class ForumSettings {

    /** The keys this class reads. Nothing else in the environment reaches the application. */
    static final List<String> DOCUMENTED_KEYS = List.of(
            "AGORA_REQUEST_MAX_BYTES",
            "AGORA_TEXT_MAX_CODEPOINTS",
            "AGORA_RENDERED_MAX_BYTES",
            "AGORA_BUSINESS_TIMEZONE",
            "AGORA_DATABASE_ENABLED",
            "AEION_SQLITE_PATH");

    /** One bounded setting: an inclusive range, and the value used when the key is absent. */
    record Bound(String key, int min, int max, int fallback) {
    }

    static final Bound REQUEST_MAX_BYTES = new Bound("AGORA_REQUEST_MAX_BYTES", 1, 16384, 16384);
    static final Bound TEXT_MAX_CODEPOINTS = new Bound("AGORA_TEXT_MAX_CODEPOINTS", 1, 4000, 4000);
    static final Bound RENDERED_MAX_BYTES = new Bound("AGORA_RENDERED_MAX_BYTES", 1, 32768, 32768);

    static final String DATABASE_ENABLED_KEY = "AGORA_DATABASE_ENABLED";
    static final String CONNECTION_URL_KEY = "AEION_SQLITE_PATH";
    static final String DATASOURCE_URL_KEY = "quarkus.datasource.jdbc.url";
    static final String BUSINESS_TIMEZONE_KEY = "AGORA_BUSINESS_TIMEZONE";
    static final String BUSINESS_TIMEZONE_DEFAULT = "Europe/Paris";

    /**
     * The URL this application ships as a development fallback. It is not an operator's answer: a
     * deployment that turns database-backed features on and still lands here has stated nothing.
     */
    static final String ASSUMED_DATASOURCE_URL = "jdbc:sqlite:/tmp/agora.db";

    private final int requestMaxBytes;
    private final int textMaxCodepoints;
    private final int renderedMaxBytes;
    private final ZoneId businessZone;
    private final boolean databaseEnabled;
    private final String connectionUrl;
    private final String datasourceUrl;

    @Inject
    public ForumSettings(
            @ConfigProperty(name = "agora.request.max.bytes") Optional<String> requestMaxBytes,
            @ConfigProperty(name = "agora.text.max.codepoints") Optional<String> textMaxCodepoints,
            @ConfigProperty(name = "agora.rendered.max.bytes") Optional<String> renderedMaxBytes,
            @ConfigProperty(name = "agora.business.timezone") Optional<String> businessTimezone,
            @ConfigProperty(name = "agora.database.enabled") Optional<String> databaseEnabled,
            @ConfigProperty(name = "agora.database.connection.url") Optional<String> connectionUrl,
            @ConfigProperty(name = "quarkus.datasource.jdbc.url") Optional<String> datasourceUrl) {
        this(requestMaxBytes.orElse(null), textMaxCodepoints.orElse(null), renderedMaxBytes.orElse(null),
                businessTimezone.orElse(null), databaseEnabled.orElse(null), connectionUrl.orElse(null),
                datasourceUrl.orElse(null));
    }

    /**
     * Build and validate the settings outside CDI, so a test can prove each refusal without a boot.
     * The datasource URL is the shipped development one, which states nothing by itself.
     */
    public static ForumSettings of(String requestMaxBytes, String textMaxCodepoints, String renderedMaxBytes,
            String businessTimezone, String databaseEnabled, String connectionUrl) {
        return of(requestMaxBytes, textMaxCodepoints, renderedMaxBytes, businessTimezone, databaseEnabled,
                connectionUrl, ASSUMED_DATASOURCE_URL);
    }

    /** The same with the datasource URL the deployment resolves, as the application reads it. */
    public static ForumSettings of(String requestMaxBytes, String textMaxCodepoints, String renderedMaxBytes,
            String businessTimezone, String databaseEnabled, String connectionUrl, String datasourceUrl) {
        return new ForumSettings(requestMaxBytes, textMaxCodepoints, renderedMaxBytes, businessTimezone,
                databaseEnabled, connectionUrl, datasourceUrl);
    }

    private ForumSettings(String requestMaxBytes, String textMaxCodepoints, String renderedMaxBytes,
            String businessTimezone, String databaseEnabled, String connectionUrl, String datasourceUrl) {
        this.requestMaxBytes = bounded(REQUEST_MAX_BYTES, requestMaxBytes);
        this.textMaxCodepoints = bounded(TEXT_MAX_CODEPOINTS, textMaxCodepoints);
        this.renderedMaxBytes = bounded(RENDERED_MAX_BYTES, renderedMaxBytes);
        this.businessZone = zone(businessTimezone);
        this.databaseEnabled = flag(databaseEnabled);
        this.connectionUrl = connectionUrl;
        this.datasourceUrl = datasourceUrl;
    }

    /**
     * The request-body ceiling this deployment states, in bytes. Its range ends where the documented
     * ceiling is, so a value inside it is legal and a wider one is refused here rather than accepted
     * by a config file. The byte-level refusals themselves are the transport's 16 KiB limit and the
     * request-validation filter (feature 017, specified in {@code request-validation.md}).
     */
    public int requestMaxBytes() {
        return requestMaxBytes;
    }

    /**
     * Longest normalized text the application stores, in codepoints: the ceiling
     * {@code SettingsResource} writes under.
     */
    public int textMaxCodepoints() {
        return textMaxCodepoints;
    }

    /**
     * The byte budget a rendered page may take, declared here so the renderer cannot invent its own.
     */
    public int renderedMaxBytes() {
        return renderedMaxBytes;
    }

    /** The business calendar the product counts days in, refused unless it is a region-based zone. */
    public ZoneId businessZone() {
        return businessZone;
    }

    /** True when the database-backed features are turned on and their settings are required. */
    public boolean databaseEnabled() {
        return databaseEnabled;
    }

    /**
     * Force the settings to be built and checked now. A normal-scoped bean is a client proxy, so
     * without this the bounds would be checked on the first use and a deployment would be reachable —
     * and probed as alive — while carrying a configuration that was never acceptable.
     */
    public void selfCheck() {
        // Every bound was checked while this bean was built, so reaching here is the proof.
    }

    /**
     * Database-backed features are on, so the connection must have been stated by the operator.
     *
     * Stated means stated by any documented means: the connection key of this baseline, or a
     * datasource URL the operator put in place of it (including the URL a PostgreSQL deployment will
     * carry after the cutover). What is refused is the third case — features on, and the only URL the
     * application has is the one it ships to developers. A setting that always resolves, by default,
     * is a setting nobody can get wrong, and getting it wrong is what this check exists for.
     */
    public void requireConnectionStated() {
        if (databaseEnabled && !connectionStated()) {
            throw new IllegalStateException("database_connection_missing: " + CONNECTION_URL_KEY + " or "
                    + DATASOURCE_URL_KEY + " is required when " + DATABASE_ENABLED_KEY + " is true");
        }
    }

    /**
     * True when this deployment asked for database work and said where the database is.
     *
     * The gates that schedule migrations, outbox drains and event consumers use this rather than
     * {@link #databaseEnabled()} alone, so a deployment that is about to be refused for an unstated
     * connection never opens the shipped development file first: refusing cleanly also means leaving
     * nobody's {@code /tmp} with a half-migrated forum in it.
     */
    public boolean databaseWorkRequested() {
        return databaseEnabled && connectionStated();
    }

    private boolean connectionStated() {
        if (connectionUrl != null && !connectionUrl.isBlank()) {
            return true;
        }
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            return false;
        }
        String url = datasourceUrl.strip();
        // A scheme with no database behind it is not an answer: it is the empty connection key
        // expanded into a URL, and it must be refused like the absent key it came from.
        return !ASSUMED_DATASOURCE_URL.equals(url) && !"jdbc:sqlite:".equals(url);
    }

    /** A bounded setting resolves to a whole number inside its own range. */
    static int bounded(Bound bound, String raw) {
        if (raw == null || raw.isBlank()) {
            return bound.fallback();
        }
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException notAnInteger) {
            // Never the supplied text: an operator who typed it knows it, and a log is read by others.
            throw new IllegalStateException("setting_not_an_integer: " + bound.key()
                    + " must be a whole number between " + bound.min() + " and " + bound.max());
        }
        if (value < bound.min() || value > bound.max()) {
            throw new IllegalStateException("setting_out_of_range: " + bound.key()
                    + " must be between " + bound.min() + " and " + bound.max());
        }
        return value;
    }

    private static boolean flag(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String text = raw.strip().toLowerCase(Locale.ROOT);
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw new IllegalStateException(
                "setting_not_a_boolean: " + DATABASE_ENABLED_KEY + " must be true or false");
    }

    /**
     * The business calendar has to survive a clock change, so the zone must be a region id: a fixed
     * offset drifts out of the working day twice a year and nobody notices it in the data.
     */
    private static ZoneId zone(String raw) {
        String text = raw == null || raw.isBlank() ? BUSINESS_TIMEZONE_DEFAULT : raw.strip();
        ZoneId zone;
        try {
            zone = ZoneId.of(text);
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException("setting_invalid: " + BUSINESS_TIMEZONE_KEY
                    + " must be a region-based timezone such as " + BUSINESS_TIMEZONE_DEFAULT);
        }
        if (!zone.getId().contains("/")) {
            throw new IllegalStateException("setting_invalid: " + BUSINESS_TIMEZONE_KEY
                    + " must be a region-based timezone such as " + BUSINESS_TIMEZONE_DEFAULT);
        }
        return zone;
    }
}
