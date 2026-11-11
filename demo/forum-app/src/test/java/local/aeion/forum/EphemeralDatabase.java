package local.aeion.forum;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Feature 003, on PostgreSQL since the 002 cutover: a database that exists for one test and is
 * dropped afterwards, together with its private application role.
 *
 * Three properties, all enforced here:
 *  * <b>Isolated.</b> Every instance is a new database named {@code agora_test_<hex>}, so two
 *    suites can run at once and a row one test leaves cannot change the next test's result.
 *  * <b>Marked disposable.</b> Only names the harness itself generates are ever managed; any other
 *    name, or a missing fixture environment, is refused before a single statement runs.
 *  * <b>Same schema as a deployment.</b> Migrations come from the application's own Flyway files,
 *    applied by the operator code a deployment uses, with the app role on DML-only grants.
 *
 * The admin settings (AEION_TEST_POSTGRES_JDBC_URL/USERNAME/PASSWORD) are fixture-only and exist
 * only in the test environment.
 */
public final class EphemeralDatabase implements AutoCloseable {

    static final String PREFIX = "agora_test_";

    /** The fixture-only admin credentials; overridable so a test can prove the refusal path. */
    interface AdminCredentials {
        String[] get();
    }

    static AdminCredentials adminCredentials = EphemeralDatabase::readEnvironmentCredentials;

    private final String database;
    private final String appRole;
    private final PGSimpleDataSource ownerSource;
    private final PGSimpleDataSource appSource;
    private final String adminUrl;
    private final String adminUsername;
    private final String adminPassword;
    private final ForumClock clock;

    private EphemeralDatabase(String database, String appRole, PGSimpleDataSource ownerSource,
                              PGSimpleDataSource appSource, String adminUrl, String adminUsername,
                              String adminPassword, ForumClock clock) {
        this.database = database;
        this.appRole = appRole;
        this.ownerSource = ownerSource;
        this.appSource = appSource;
        this.adminUrl = adminUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.clock = clock;
    }

    /** Create a marked, isolated, migrated database with its application role; the caller closes it. */
    public static EphemeralDatabase create() {
        return open(randomName(), true);
    }

    /**
     * Create a marked, isolated database with no schema at all: the shape a migration test needs,
     * where the migrations under test are the first thing the database ever sees.
     */
    public static EphemeralDatabase createEmpty() {
        return open(randomName(), false);
    }

    /** Create a marked, isolated, migrated database under an explicit, prefix-checked name. */
    static EphemeralDatabase createAt(String name) {
        return open(name, true);
    }

    private static EphemeralDatabase open(String name, boolean migrate) {
        requireDisposableName(name);
        String[] admin = adminCredentials.get();
        if (admin == null) {
            throw new IllegalStateException("test_postgres_missing: AEION_TEST_POSTGRES_JDBC_URL "
                    + "and its credentials are required for the ephemeral test database");
        }
        String database = name;
        String appRole = database.replaceFirst("agora_test_", "agora_app_");
        String adminUrl = admin[0];
        String adminUsername = admin[1];
        String adminPassword = admin[2];
        createDatabase(adminUrl, adminUsername, adminPassword, database);
        String ownerUrl = withDatabase(adminUrl, database);
        ForumClock clock = new ForumClock();
        clock.freeze(1_790_000_000_000_000_000L);
        EphemeralDatabase instance = new EphemeralDatabase(database, appRole,
                source(ownerUrl, adminUsername, adminPassword), source(ownerUrl, appRole, appRole + "-pass"),
                adminUrl, adminUsername, adminPassword, clock);
        try {
            if (migrate) {
                try (Connection connection = instance.ownerSource.getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("CREATE ROLE " + appRole + " LOGIN PASSWORD '"
                            + appRole + "-pass'");
                }
                DatabaseOperator.migrate(new DatabaseOperator.Target(
                        ownerUrl, adminUsername, adminPassword, appRole));
            }
        } catch (RuntimeException | SQLException failure) {
            instance.close();
            throw failure instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("test_db_unprepared: " + database, failure);
        }
        return instance;
    }

    static String randomTestName() {
        return PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String randomName() {
        return randomTestName();
    }

    /** Refuse any name the harness did not generate: the prefix plus exactly twelve hex digits. */
    static void requireDisposableName(String name) {
        if (name == null || !name.matches(PREFIX + "[0-9a-f]{12}")) {
            throw new IllegalStateException("test_database_not_disposable: " + name);
        }
    }

    private static String[] readEnvironmentCredentials() {
        String url = System.getenv("AEION_TEST_POSTGRES_JDBC_URL");
        if (url == null || url.isBlank()) {
            return null;
        }
        return new String[] {
                url,
                System.getenv().getOrDefault("AEION_TEST_POSTGRES_USERNAME", ""),
                System.getenv().getOrDefault("AEION_TEST_POSTGRES_PASSWORD", "")};
    }

    private static void createDatabase(String adminUrl, String username, String password, String name) {
        try (Connection connection = java.sql.DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_unprepared: " + name, error);
        }
    }

    /** Rewrite the database name of a {@code jdbc:postgresql://host:port/db?params} URL. */
    static String withDatabase(String url, String database) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            throw new IllegalStateException("test_postgres_url_invalid");
        }
        String rest = url.substring(scheme + 3);
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return url + "/" + database;
        }
        String hostPort = rest.substring(0, slash);
        String tail = rest.substring(slash);
        int query = tail.indexOf('?');
        String parameters = query >= 0 ? tail.substring(query) : "";
        return url.substring(0, scheme + 3) + hostPort + "/" + database + parameters;
    }

    private static PGSimpleDataSource source(String url, String username, String password) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(username);
        source.setPassword(password);
        return source;
    }

    /** The application's own view of the database: least-privilege, DML only, never an owner. */
    public DataSource dataSource() {
        return appSource;
    }

    /** The migration-owner view, for fixture setup and assertions an operator would run. */
    public DataSource ownerDataSource() {
        return ownerSource;
    }

    /** The application role's login, for fixtures that drive the operator or the app as that role. */
    String appRoleName() {
        return appRole;
    }

    /** The application role's fixture password, paired with {@link #appRoleName()}. */
    String appRolePassword() {
        return appRole + "-pass";
    }

    public ForumClock clock() {
        return clock;
    }

    public String database() {
        return database;
    }

    /** Run one statement as the owner; the harness is for probes, not for application writes. */
    public void execute(String sql) {
        try (Connection connection = ownerSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_statement_failed", error);
        }
    }

    /** Run one statement on the cluster's admin connection, outside any disposable database. */
    public void executeAsAdmin(String sql) {
        try (Connection connection = java.sql.DriverManager.getConnection(
                adminUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_admin_statement_failed", error);
        }
    }

    /** True when the database carries a table with that name, as the migration owner sees it. */
    public boolean hasTable(String table) {
        try (Connection connection = ownerSource.getConnection();
             var read = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                             + "AND table_name = ?")) {
            read.setString(1, table);
            try (ResultSet rows = read.executeQuery()) {
                return rows.next() && rows.getLong(1) == 1;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_inspect_failed", error);
        }
    }

    /** Drop the database and its role: a test must not leave a database behind. */
    @Override
    public void close() {
        try (Connection connection = java.sql.DriverManager.getConnection(
                adminUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            statement.execute("DROP ROLE IF EXISTS " + appRole);
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_release_failed: " + database, error);
        }
    }
}
