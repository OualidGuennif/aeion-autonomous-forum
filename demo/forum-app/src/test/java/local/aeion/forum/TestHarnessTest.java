package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
