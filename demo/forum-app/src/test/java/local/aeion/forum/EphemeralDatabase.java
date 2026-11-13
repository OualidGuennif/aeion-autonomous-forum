package local.aeion.forum;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Feature 003, on PostgreSQL since the 002 cutover: a database that exists for one test and is
 * dropped afterwards, together with the private application role created for it.
 *
 * Four properties, all enforced here:
 *  * <b>Isolated.</b> Every instance is a new database named {@code agora_test_<hex>}, so two
 *    suites run at once and a row one test leaves cannot change the next test's result.
 *  * <b>Marked disposable.</b> Only names the harness itself generates are ever managed; any other
 *    name, or a missing fixture environment, is refused before a single statement runs.
 *  * <b>Local, allowlisted transport.</b> The fixture host must be loopback, settled before any
 *    connection opens; exactly one host in the URL authority names it, so a {@code user@host} URL
 *    or a host list with a remote fallback is refused.
 *  * <b>Same schema as a deployment.</b> Migrations come from the application's own Flyway files,
 *    applied by the operator code a deployment uses, with the app role on DML-only grants.
 *
 * Teardown releases only what this instance created: the database it made and the roles it made; a
 * role that already existed is left as found, even when setup then fails. Each instance carries the
 * frozen clock and seeded random source the suite is deterministic on.
 */
public final class EphemeralDatabase implements AutoCloseable {

    static final String PREFIX = "agora_test_";

    /** The frozen instant every harness clock stops at, and the seed every random source starts from. */
    static final long FROZEN_NS = 1_790_000_000_000_000_000L;

    /** The only hosts a fixture may point at: the loopback transports, never a deployment's. */
    static final List<String> ALLOWED_HOSTS = List.of("127.0.0.1", "localhost", "::1");

    /** Parameters that could name the server themselves, so a fixture URL may never carry one. */
    private static final Set<String> SERVER_NAMING_PARAMETERS = Set.of(
            "host", "hostname", "server", "servername", "service", "servicefile", "pgservicefile");

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
    private final Random random;
    /** Owned state only: the database and roles this instance created, and so may drop. */
    private final Set<String> createdRoles = new LinkedHashSet<>();
    private boolean ownsDatabase;

    private EphemeralDatabase(String database, String appRole, PGSimpleDataSource ownerSource,
                              PGSimpleDataSource appSource, String adminUrl, String adminUsername,
                              String adminPassword, ForumClock clock, Random random) {
        this.database = database;
        this.appRole = appRole;
        this.ownerSource = ownerSource;
        this.appSource = appSource;
        this.adminUrl = adminUrl;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.clock = clock;
        this.random = random;
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
        // Settled before anything is opened: an unapproved host is refused with no connection and
        // therefore no DDL, which is what keeps a fixture run away from a deployment's cluster.
        requireAllowedHost(admin[0]);
        String database = name;
        String appRole = database.replaceFirst(PREFIX, "agora_app_");
        String adminUrl = admin[0];
        String adminUsername = admin[1];
        String adminPassword = admin[2];
        ForumClock clock = new ForumClock();
        clock.freeze(FROZEN_NS);
        EphemeralDatabase instance = new EphemeralDatabase(database, appRole,
                source(withDatabase(adminUrl, database), adminUsername, adminPassword),
                source(withDatabase(adminUrl, database), appRole, appRole + "-pass"),
                adminUrl, adminUsername, adminPassword, clock, new Random(FROZEN_NS));
        try {
            createDatabase(instance, database);
            if (migrate) {
                instance.createApplicationRole(appRole);
                DatabaseOperator.migrate(new DatabaseOperator.Target(
                        withDatabase(adminUrl, database), adminUsername, adminPassword, appRole));
            }
        } catch (RuntimeException | SQLException failure) {
            // Teardown claims only what this instance made: a role it never created is left alone,
            // and the refusal the caller sees stays the one that actually happened.
            RuntimeException refusal = failure instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("test_db_unprepared: " + database, failure);
            try {
                instance.close();
            } catch (RuntimeException cleanup) {
                refusal.addSuppressed(cleanup);
            }
            throw refusal;
        }
        return instance;
    }

    /** The application role for a disposable database name; never claimed when it already exists. */
    private void createApplicationRole(String role) throws SQLException {
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + role + "-pass'");
            createdRoles.add(role);
        }
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

    /**
     * The only authority a fixture may name: one allowlisted loopback host and at most one port,
     * matched as a whole. So a comma-separated host list, a userinfo section, a protocol-address
     * form or any trailing text fails the match instead of being read host by host — a loopback
     * first host can never certify a remote fallback, and pgjdbc's host failover never runs.
     */
    private static final Pattern LOOPBACK_AUTHORITY = Pattern.compile(loopbackAuthorityPattern());

    private static String loopbackAuthorityPattern() {
        StringBuilder hosts = new StringBuilder("(?:");
        for (int index = 0; index < ALLOWED_HOSTS.size(); index++) {
            String host = ALLOWED_HOSTS.get(index);
            hosts.append(index == 0 ? "" : "|");
            hosts.append(host.indexOf(':') >= 0
                    ? "\\[" + Pattern.quote(host) + "\\]" : Pattern.quote(host));
        }
        return hosts.append(")(?::[0-9]{1,5})?").toString();
    }

    /**
     * True only for a URL whose one and only host is an allowlisted loopback transport. Credentials
     * never travel inside the URL, so an authority carrying a userinfo section is refused outright
     * rather than re-read: its real netloc is whatever follows the last {@code @}.
     */
    static boolean isAllowedHost(String url) {
        if (url == null) {
            return false;
        }
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return false;
        }
        String rest = url.substring(scheme + 3);
        int query = rest.indexOf('?');
        if (query >= 0) {
            if (!parametersNameNoServer(rest.substring(query + 1))) {
                return false;
            }
            rest = rest.substring(0, query);
        }
        int slash = rest.indexOf('/');
        String authority = slash >= 0 ? rest.substring(0, slash) : rest;
        // An empty host names a local socket directory, not a loopback TCP transport the harness
        // may claim, so the pattern refuses it rather than assuming it is safe.
        return LOOPBACK_AUTHORITY.matcher(authority).matches();
    }

    /** True when no query parameter names the server: only the authority is ever allowed to. */
    private static boolean parametersNameNoServer(String query) {
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String name = (equals >= 0 ? pair.substring(0, equals) : pair)
                    .strip().toLowerCase(Locale.ROOT);
            if (SERVER_NAMING_PARAMETERS.contains(name)) {
                return false;
            }
        }
        return true;
    }

    /** The refusal that keeps every statement this harness runs on an allowlisted local host. */
    static void requireAllowedHost(String url) {
        if (!isAllowedHost(url)) {
            throw new IllegalStateException("test_postgres_host_not_allowed");
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

    private static void createDatabase(EphemeralDatabase instance, String name) throws SQLException {
        try (Connection connection = instance.adminConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
            instance.ownsDatabase = true;
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_unprepared: " + name, error);
        }
    }

    /** The cluster admin connection, opened only after the host has been allowlisted. */
    private Connection adminConnection() throws SQLException {
        requireAllowedHost(adminUrl);
        return java.sql.DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
    }

    /**
     * Rewrite the database segment of a {@code jdbc:postgresql://host:port/db?params} URL, inserting
     * it when the fixture names none. It always lands before the query: pgjdbc reads everything
     * after {@code ?} as parameters, so a name appended there would be one and the connection would
     * stay on the driver's default database, which is exactly the database the harness must never
     * migrate.
     */
    static String withDatabase(String url, String database) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            throw new IllegalStateException("test_postgres_url_invalid");
        }
        String rest = url.substring(scheme + 3);
        int query = rest.indexOf('?');
        String parameters = query >= 0 ? rest.substring(query) : "";
        String authority = query >= 0 ? rest.substring(0, query) : rest;
        int slash = authority.indexOf('/');
        String hostPort = slash >= 0 ? authority.substring(0, slash) : authority;
        return url.substring(0, scheme + 3) + hostPort + "/" + database + parameters;
    }

    private static PGSimpleDataSource source(String url, String username, String password) {
        // Built, never opened, before the host is settled: a data source can only ever name an
        // allowlisted loopback host, whatever the fixture environment supplied.
        requireAllowedHost(url);
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

    /** The frozen clock and the seeded source: a harness result never depends on when it ran. */
    public ForumClock clock() {
        return clock;
    }

    /**
     * The suite's random source: seeded from the same fixed seed every instance starts from, so a
     * drawn value is a function of the draw's position, not of the wall clock or of which database
     * a test happened to get.
     */
    public Random random() {
        return random;
    }

    /** The roles this instance created; a role it never made is a role it must never drop. */
    public Set<String> createdRoles() {
        return Set.copyOf(createdRoles);
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
        try (Connection connection = adminConnection();
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

    /**
     * Release only what this instance made: the database it created and the roles it created.
     *
     * Idempotent, so a setup failure that already cleaned up can be cleaned up again by the caller.
     * A role that was already there is left exactly as it was found, because dropping one would
     * take an authority away from something the harness never owned.
     */
    @Override
    public void close() {
        if (!ownsDatabase && createdRoles.isEmpty()) {
            return;
        }
        try (Connection connection = adminConnection();
             Statement statement = connection.createStatement()) {
            if (ownsDatabase) {
                statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
                ownsDatabase = false;
            }
            for (String role : List.copyOf(createdRoles)) {
                statement.execute("DROP ROLE IF EXISTS " + role);
                createdRoles.remove(role);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("test_db_release_failed: " + database, error);
        }
    }
}
