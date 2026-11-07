package local.aeion.forum;

import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: which database plane the deployment runs on, decided once at startup.
 *
 * {@code AGORA_DATABASE_ENABLED} selects the database-backed features. When the key is absent it
 * means false, so a deployment may boot with only the session secret: no migration, no ORM work
 * and no connection happens, and database settings it does not provide are simply absent, never
 * invented. A value that is neither {@code true} nor {@code false} refuses the start, naming only
 * the key.
 *
 * {@code true} enables the committed database-backed plane and additionally requires the
 * PostgreSQL connection settings: a database-backed deployment without its connection settings is
 * half-configured, so it refuses instead of starting. Those settings are proven by the gate itself
 * the first time anyone asks it a question — so a startup observer (the migration runner, the outbox
 * drain, whose order CDI leaves undefined) can never dial the datasource through an enabled gate that
 * was never validated. The settings are validated as present and carried for the PostgreSQL cutover;
 * the runtime engine switch is that separate feature's work.
 */
@jakarta.enterprise.context.ApplicationScoped
public class DatabaseMode {

    static final String ENABLED_KEY = "AGORA_DATABASE_ENABLED";
    static final String URL_KEY = "AGORA_DATABASE_URL";
    static final String USER_KEY = "AGORA_DATABASE_USER";
    static final String PASSWORD_KEY = "AGORA_DATABASE_PASSWORD";
    static final String MISSING_PREFIX = "database_setting_missing: ";

    private final Optional<String> enabledRaw;
    private final Optional<String> postgreSqlUrl;
    private final Optional<String> postgreSqlUser;
    private final Optional<String> postgreSqlPassword;

    private volatile boolean settled;
    private volatile boolean enabledState;

    @jakarta.inject.Inject
    public DatabaseMode(@ConfigProperty(name = "agora.database.enabled") Optional<String> enabledRaw,
                        @ConfigProperty(name = "agora.database.url") Optional<String> postgreSqlUrl,
                        @ConfigProperty(name = "agora.database.user") Optional<String> postgreSqlUser,
                        @ConfigProperty(name = "agora.database.password") Optional<String> postgreSqlPassword) {
        this.enabledRaw = enabledRaw;
        this.postgreSqlUrl = postgreSqlUrl;
        this.postgreSqlUser = postgreSqlUser;
        this.postgreSqlPassword = postgreSqlPassword;
    }

    /** Build the mode outside CDI, for a test that wants specific keys. */
    DatabaseMode(Optional<String> enabledRaw, Optional<String> postgreSqlUrl,
                 Optional<String> postgreSqlUser, Optional<String> postgreSqlPassword, boolean direct) {
        this(enabledRaw, postgreSqlUrl, postgreSqlUser, postgreSqlPassword);
    }

    /**
     * Force the whole gate to be settled and proven now: the flag, and — when the plane is enabled —
     * the PostgreSQL connection settings it requires.
     */
    public void selfCheck() {
        ensure();
    }

    /**
     * True only when the deployment asked for the database-backed features. Asking the gate is also
     * what proves it: an enabled plane whose connection settings are missing refuses here, so no
     * startup work can reach the datasource through this gate before the required values are
     * validated.
     */
    public boolean enabled() {
        ensure();
        return enabledState;
    }

    private static void require(String key, Optional<String> value) {
        if (value == null || value.orElse("").isBlank()) {
            throw new IllegalStateException(MISSING_PREFIX + key
                    + " is required when " + ENABLED_KEY + "=true");
        }
    }

    private synchronized void ensure() {
        if (settled) {
            return;
        }
        String raw = enabledRaw == null ? null : enabledRaw.orElse(null);
        if (raw != null) {
            String trimmed = raw.strip();
            if (trimmed.isEmpty()) {
                throw new IllegalStateException(
                        "database_flag_invalid: " + ENABLED_KEY + " must be true or false");
            }
            if ("true".equalsIgnoreCase(trimmed)) {
                enabledState = true;
            } else if ("false".equalsIgnoreCase(trimmed)) {
                enabledState = false;
            } else {
                throw new IllegalStateException(
                        "database_flag_invalid: " + ENABLED_KEY + " must be true or false");
            }
        }
        if (enabledState) {
            require(URL_KEY, postgreSqlUrl);
            require(USER_KEY, postgreSqlUser);
            require(PASSWORD_KEY, postgreSqlPassword);
        }
        settled = true;
    }
}
