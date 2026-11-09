package local.aeion.forum;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Feature 001: the connection settings of the forum's database.
 *
 * The approved architecture is PostgreSQL, and these are the keys a deployment sets for it: the
 * URL, the user and the password. The transition baseline still opens a SQLite file, because the
 * PostgreSQL driver is not part of this build and a dependency swap is not this feature's work.
 * The settings are read and validated either way, so the cutover changes a driver and not the
 * shape of a deployment.
 *
 * The rule is fail-closed and narrow: a deployment that enables the database-backed features must
 * have configured a connection, and a PostgreSQL URL needs its credentials. A refusal names the
 * key it is missing and never the value it did not get.
 */
@ApplicationScoped
public class DatabaseConnection {

    /** The environment keys a deployment sets. */
    static final String URL = "AGORA_DATABASE_URL";
    static final String USER = "AGORA_DATABASE_USER";
    static final String PASSWORD = "AGORA_DATABASE_PASSWORD";

    /** The transitional baseline's own connection setting: the file it opens. */
    static final String SQLITE_PATH = "AEION_SQLITE_PATH";

    private static final String JDBC_PREFIX = "jdbc:";
    private static final String POSTGRESQL_PREFIX = "jdbc:postgresql:";
    private static final String POSTGRESQL_ALIAS_PREFIX = "jdbc:postgres:";

    private final String url;
    private final String user;
    private final String password;
    private final String sqlitePath;

    @jakarta.inject.Inject
    public DatabaseConnection(
            @ConfigProperty(name = "agora.database.url") Optional<String> url,
            @ConfigProperty(name = "agora.database.user") Optional<String> user,
            @ConfigProperty(name = "agora.database.password") Optional<String> password,
            @ConfigProperty(name = "agora.database.sqlite.path") Optional<String> sqlitePath) {
        this.url = orEmpty(url);
        this.user = orEmpty(user);
        this.password = orEmpty(password);
        this.sqlitePath = orEmpty(sqlitePath);
    }

    /** Build a connection from raw strings, for a test that wants a known value. */
    public static DatabaseConnection of(String url, String user, String password, String sqlitePath) {
        return new DatabaseConnection(
                Optional.ofNullable(url),
                Optional.ofNullable(user),
                Optional.ofNullable(password),
                Optional.ofNullable(sqlitePath));
    }

    /** The JDBC URL of the forum's database; empty when the deployment configured none. */
    public String url() {
        return url;
    }

    /** The user the forum connects with; empty when the deployment configured none. */
    public String user() {
        return user;
    }

    /** The password the forum connects with; empty when the deployment configured none. */
    public String password() {
        return password;
    }

    /** The transitional baseline's file; empty when the deployment configured none. */
    public String sqlitePath() {
        return sqlitePath;
    }

    /**
     * Check the connection settings of a deployment that enables the database-backed features.
     *
     * With the database off there is nothing to connect to, and nothing is required: that is the
     * skeleton the liveness endpoint serves.
     */
    public void selfCheck(boolean databaseEnabled) {
        if (!databaseEnabled) {
            return;
        }
        if (url.isBlank()) {
            if (sqlitePath.isBlank()) {
                throw new IllegalStateException("settings_database_url_missing: " + URL
                        + " is required when the database-backed features are enabled");
            }
            return;
        }
        if (!url.startsWith(JDBC_PREFIX)) {
            throw new IllegalStateException("settings_database_url_invalid: " + URL
                    + " must be a JDBC URL");
        }
        if (isPostgreSql(url) && (user.isBlank() || password.isBlank())) {
            throw new IllegalStateException("settings_database_credentials_missing: " + USER
                    + " and " + PASSWORD + " are required for a PostgreSQL database");
        }
    }

    private static boolean isPostgreSql(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith(POSTGRESQL_PREFIX) || lower.startsWith(POSTGRESQL_ALIAS_PREFIX);
    }

    private static String orEmpty(Optional<String> value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value.get();
        return text == null ? "" : text;
    }
}
