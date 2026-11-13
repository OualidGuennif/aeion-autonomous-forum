package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Feature 003: the harness earns its place only if it meets the acceptance checks in its brief,
 * on PostgreSQL since the 002 cutover. Each check is one test here, and none of them touch a
 * developer's database.
 */
class TestHarnessTest {

    @Test
    void twoDatabasesRunAtOnceWithoutInterfering() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> suites = List.of(
                    () -> writeAndRead("parallel-a", "alpha"),
                    () -> writeAndRead("parallel-b", "beta"));
            List<Future<String>> futures = pool.invokeAll(suites);
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get());
            }
            assertEquals(List.of("alpha", "beta"), results);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aRowLeftByOneDatabaseIsAbsentFromTheNext() {
        try (EphemeralDatabase first = EphemeralDatabase.create()) {
            first.execute("INSERT INTO settings(name, value, updated_ns) VALUES ('left.over', 'yes', 1)");
            assertEquals(1, count(first, "settings"));
        }
        try (EphemeralDatabase second = EphemeralDatabase.create()) {
            assertEquals(0, count(second, "settings"));
        }
    }

    @Test
    void refusesANameTheHarnessDidNotGenerate() {
        IllegalStateException unmarked = assertThrows(IllegalStateException.class,
                () -> EphemeralDatabase.createAt("someone-elses-database"));
        assertTrue(unmarked.getMessage().startsWith("test_database_not_disposable"), unmarked.getMessage());
        IllegalStateException lookalike = assertThrows(IllegalStateException.class,
                () -> EphemeralDatabase.createAt("agora_test_prod"));
        assertTrue(lookalike.getMessage().startsWith("test_database_not_disposable"), lookalike.getMessage());
    }

    @Test
    void refusesToRunWithoutTheFixtureEnvironment() {
        EphemeralDatabase.AdminCredentials absent = () -> null;
        EphemeralDatabase.AdminCredentials original = EphemeralDatabase.adminCredentials;
        EphemeralDatabase.adminCredentials = absent;
        try {
            IllegalStateException refusal = assertThrows(IllegalStateException.class,
                    EphemeralDatabase::create);
            assertTrue(refusal.getMessage().startsWith("test_postgres_missing"), refusal.getMessage());
        } finally {
            EphemeralDatabase.adminCredentials = original;
        }
    }

    /**
     * The generated database name is a path segment, never a parameter: a fixture URL that names no
     * database still has to target the disposable one, so the name goes before its query string.
     */
    @Test
    void theGeneratedDatabaseNamePrecedesAnyQueryParameters() {
        String name = "agora_test_0123456789ab";
        assertEquals("jdbc:postgresql://127.0.0.1:5432/" + name + "?sslmode=require",
                EphemeralDatabase.withDatabase("jdbc:postgresql://127.0.0.1:5432?sslmode=require", name));
        assertEquals("jdbc:postgresql://127.0.0.1:5432/" + name,
                EphemeralDatabase.withDatabase("jdbc:postgresql://127.0.0.1:5432", name));
        assertEquals("jdbc:postgresql://127.0.0.1:5432/" + name + "?sslmode=require",
                EphemeralDatabase.withDatabase("jdbc:postgresql://127.0.0.1:5432/?sslmode=require", name));
        assertEquals("jdbc:postgresql://127.0.0.1:5432/" + name + "?sslmode=require",
                EphemeralDatabase.withDatabase("jdbc:postgresql://127.0.0.1:5432/postgres?sslmode=require", name));
    }

    @Test
    void closingDropsTheDatabaseAndItsRole() throws Exception {
        String name;
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            name = database.database();
            assertTrue(database.hasTable("accounts"));
        }
        try (Connection connection = java.sql.DriverManager.getConnection(
                EphemeralDatabase.adminCredentials.get()[0],
                EphemeralDatabase.adminCredentials.get()[1],
                EphemeralDatabase.adminCredentials.get()[2]);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM pg_database WHERE datname = '" + name + "'")) {
            rows.next();
            assertEquals(0, rows.getLong(1), "closing must drop the disposable database");
        }
    }

    /**
     * A fixture host the harness never approved is refused before a connection exists, so no state
     * of it can ever be reached. The refusal names a fixed code and never echoes the host.
     */
    @Test
    void refusesAnUnapprovedHostBeforeAnyConnection() {
        assertTrue(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/postgres"));
        assertTrue(EphemeralDatabase.isAllowedHost("jdbc:postgresql://localhost:5433/postgres"));
        assertTrue(EphemeralDatabase.isAllowedHost("jdbc:postgresql://[::1]:5432/postgres"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://db.example.com:5432/postgres"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://198.51.100.7:5432/postgres"));
    }

    /**
     * A fixture URL carries no credentials: those are the separate settings. So an authority that
     * names a userinfo section is never a loopback netloc the harness may claim — its real host is
     * the part after the last {@code @}, and the whole URL is refused rather than re-read.
     */
    @Test
    void refusesAUserinfoUrlInsteadOfReadingItAsLoopback() {
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1@evil.com:5432/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://admin@127.0.0.1:5432/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://admin:pass@localhost/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://admin@[::1]:5432/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1@198.51.100.7/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://@127.0.0.1:5432/db"));
        // Only the authority names the netloc: a parameter value is never mistaken for one, and
        // never smuggled into it.
        assertTrue(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/db?user=a&ssl=true"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://198.51.100.7:5432/db?user=a@b"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql:///postgres"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql:postgres"));
        assertFalse(EphemeralDatabase.isAllowedHost(null));

        for (String url : List.of("jdbc:postgresql://198.51.100.7:5432/postgres",
                "jdbc:postgresql://127.0.0.1@198.51.100.7:5432/postgres")) {
            EphemeralDatabase.AdminCredentials original = EphemeralDatabase.adminCredentials;
            EphemeralDatabase.adminCredentials = () -> new String[] {url, "admin", "secret"};
            try {
                IllegalStateException refusal = assertThrows(IllegalStateException.class,
                        EphemeralDatabase::create);
                assertTrue(refusal.getMessage().startsWith("test_postgres_host_not_allowed"),
                        refusal.getMessage());
                assertFalse(refusal.getMessage().contains("198.51.100.7"), "the host is never echoed");
                assertFalse(refusal.getMessage().contains("secret"), "the credential is never echoed");
            } finally {
                EphemeralDatabase.adminCredentials = original;
            }
        }
    }

    /**
     * A JDBC URL may name a list of hosts that pgjdbc tries in order, so only the first one being
     * loopback would still let a fixture reach a server it was never given. Exactly one allowlisted
     * host in the authority is therefore ever accepted: a host list, a protocol-address form or a
     * parameter naming the server is refused, with no connection attempted and so no DDL.
     */
    @Test
    void refusesAMultiHostUrlInsteadOfTrustingItsFirstHost() {
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1,evil.com/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432,evil.com:5432/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://localhost:5432,198.51.100.7:5432/postgres"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://[::1]:5432,evil.com:5432/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://evil.com:5432,127.0.0.1:5432/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1,127.0.0.1:5432/db"));
        // Only the documented loopback spellings, and nothing after the host or its port.
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.1/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://0x7f000001/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://LOCALHOST/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432#evil/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432x/db"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://(host=127.0.0.1,port=5432)/db"));
        // A parameter is never a second way to name the server; ordinary settings still pass.
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/db?serverName=evil.com"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/db?host=evil.com"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/db?SERVERNAME=evil.com"));
        assertFalse(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/db?service=production"));
        assertTrue(EphemeralDatabase.isAllowedHost("jdbc:postgresql://127.0.0.1:5432/db?sslmode=require&connectTimeout=3"));
        assertTrue(EphemeralDatabase.isAllowedHost("jdbc:postgresql://localhost:5432"));

        for (String url : List.of("jdbc:postgresql://127.0.0.1:5432,evil.com:5432/postgres",
                "jdbc:postgresql://localhost:5432,198.51.100.7:5432/postgres",
                "jdbc:postgresql://127.0.0.1:5432/postgres?serverName=198.51.100.7")) {
            EphemeralDatabase.AdminCredentials original = EphemeralDatabase.adminCredentials;
            EphemeralDatabase.adminCredentials = () -> new String[] {url, "admin", "secret"};
            try {
                IllegalStateException refusal = assertThrows(IllegalStateException.class,
                        EphemeralDatabase::create);
                assertTrue(refusal.getMessage().startsWith("test_postgres_host_not_allowed"),
                        refusal.getMessage());
                assertFalse(refusal.getMessage().contains("evil.com"), "the host is never echoed");
                assertFalse(refusal.getMessage().contains("198.51.100.7"), "the host is never echoed");
                assertFalse(refusal.getMessage().contains("secret"), "the credential is never echoed");
            } finally {
                EphemeralDatabase.adminCredentials = original;
            }
        }
    }

    /**
     * Teardown claims only what this instance made. A role that already existed when setup failed
     * is left exactly where it was found: it belongs to something other than this test.
     */
    @Test
    void teardownKeepsAPreExistingApplicationRole() throws Exception {
        try (EphemeralDatabase keeper = EphemeralDatabase.create()) {
            String name = EphemeralDatabase.randomTestName();
            String role = name.replaceFirst(EphemeralDatabase.PREFIX, "agora_app_");
            keeper.executeAsAdmin("CREATE ROLE " + role + " LOGIN PASSWORD 'pre-existing-pass'");
            try {
                assertEquals(1, count(keeper, "pg_roles", role), "the role arrived before this test");
                IllegalStateException refusal = assertThrows(IllegalStateException.class,
                        () -> EphemeralDatabase.createAt(name));
                assertTrue(refusal.getMessage().startsWith("test_db_unprepared"), refusal.getMessage());
                assertEquals(1, count(keeper, "pg_roles", role),
                        "a failed setup must never drop a role it did not create");
                assertEquals(0, count(keeper, "pg_database", name),
                        "the database this instance did make is still released");
            } finally {
                keeper.executeAsAdmin("DROP ROLE IF EXISTS " + role);
            }
        }
    }

    /** The harness's own roles are the only ones it reports, and the only ones it releases. */
    @Test
    void anInstanceTracksOnlyTheRolesItCreated() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            assertEquals(java.util.Set.of(database.appRoleName()), database.createdRoles());
        }
        try (EphemeralDatabase empty = EphemeralDatabase.createEmpty()) {
            assertTrue(empty.createdRoles().isEmpty(), "an empty fixture creates no role at all");
        }
    }

    /** A frozen clock and a seeded source: the same draws in the same order, in every fixture. */
    @Test
    void theClockIsFrozenAndTheRandomSourceIsSeeded() {
        try (EphemeralDatabase first = EphemeralDatabase.create();
             EphemeralDatabase second = EphemeralDatabase.create()) {
            assertTrue(first.clock().frozen(), "the harness clock never runs during a test");
            assertTrue(second.clock().frozen());
            assertEquals(first.clock().nowNs(), second.clock().nowNs());
            assertEquals(EphemeralDatabase.FROZEN_NS, first.clock().nowNs());
            List<Long> drawn = new ArrayList<>();
            List<Long> mirrored = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                drawn.add(first.random().nextLong());
                mirrored.add(second.random().nextLong());
            }
            assertEquals(drawn, mirrored, "the seeded source replays exactly");
            assertEquals(8, java.util.Set.copyOf(drawn).size(), "a fixed seed is not a constant");
            assertEquals(new java.util.Random(EphemeralDatabase.FROZEN_NS).nextLong(), drawn.get(0),
                    "the documented seed is the one every source starts from");
        }
    }

    /** The resource states the Quarkus injection hook itself, instead of inheriting it quietly. */
    @Test
    void theTestResourceImplementsTheQuarkusInjectionHook() throws Exception {
        Method inject = QuarkusTestDatabase.class.getMethod("inject",
                QuarkusTestResourceLifecycleManager.TestInjector.class);
        assertEquals(QuarkusTestDatabase.class, inject.getDeclaringClass());
        assertEquals(void.class, inject.getReturnType());
    }

    private static long count(EphemeralDatabase keeper, String catalog, String name) throws Exception {
        String column = catalog.equals("pg_roles") ? "rolname" : "datname";
        String[] admin = EphemeralDatabase.adminCredentials.get();
        try (Connection connection = java.sql.DriverManager.getConnection(admin[0], admin[1], admin[2]);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + catalog + " WHERE " + column + " = '" + name + "'")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String writeAndRead(String name, String value) {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            database.execute("INSERT INTO settings(name, value, updated_ns) VALUES ('" + name + "', '" + value + "', 1)");
            try (var connection = database.dataSource().getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT value FROM settings WHERE name = '" + name + "'")) {
                return rows.next() ? rows.getString(1) : "no row";
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }
    }

    private static long count(EphemeralDatabase database, String table) {
        try (var connection = database.dataSource().getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getLong(1) : -1;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
