package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Feature 002: the Flyway cutover, exercised the way the acceptance brief demands.
 *
 * Applying the migrations twice is idempotent, competing runners serialize on the database's own
 * lock, a failing transactional migration leaves no partial schema, and a tampered checksum or a
 * schema newer than the code is refused. The application role proves it cannot do any of this
 * itself: no DDL, no audit writes, only its DML.
 */
class MigrationsTest {

    @Test
    void appliesEveryTableTheMigrationsDeclare() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            for (String table : List.of(
                    "settings", "accounts", "sessions", "threads", "schema_events", "flyway_schema_history",
                    "thread_outbox", "thread_event_log", "import_manifest")) {
                assertTrue(database.hasTable(table), "missing table " + table);
            }
        }
    }

    @Test
    void aSecondRunAppliesNothing() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            long again = DatabaseOperator.migrate(target(database));
            assertEquals(0, again);
            assertEquals(3, historyRows(database));
        }
    }

    @Test
    void competingRunnersSerializeOnTheDatabaseLock() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.createEmpty()) {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Callable<Long>> runners = List.of(
                        () -> DatabaseOperator.migrate(ownerTarget(database)),
                        () -> DatabaseOperator.migrate(ownerTarget(database)));
                for (Future<Long> future : pool.invokeAll(runners)) {
                    future.get();
                }
            } finally {
                pool.shutdownNow();
            }
            assertEquals(3, historyRows(database), "the history must record every version exactly once");
            for (String table : List.of("settings", "accounts", "threads")) {
                assertTrue(database.hasTable(table));
            }
        }
    }

    @Test
    void aFailingMigrationLeavesNoPartialSchema() {
        try (EphemeralDatabase database = EphemeralDatabase.createEmpty()) {
            Flyway broken = Flyway.configure()
                    .dataSource(database.ownerDataSource())
                    .locations("classpath:db/migration-broken")
                    .load();
            assertThrows(RuntimeException.class, broken::migrate);
            assertFalse(database.hasTable("probe"), "a failed migration must leave no table behind");
            assertEquals(0, historyRows(database), "a failed migration must record nothing");
        }
    }

    @Test
    void anAlteredChecksumIsRefused() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            database.execute("UPDATE flyway_schema_history SET checksum = checksum - 7 WHERE version = '1'");
            assertEquals("schema_invalid", refusal(() -> DatabaseOperator.validate(target(database))));
        }
    }

    @Test
    void aSchemaNewerThanTheCodeIsRefused() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            database.execute("INSERT INTO flyway_schema_history(installed_rank, version, description, "
                    + "type, script, checksum, installed_by, execution_time, success) "
                    + "VALUES (99, '99', 'future', 'SQL', 'V99__future.sql', 0, 'test', 0, true)");
            assertEquals("schema_invalid", refusal(() -> DatabaseOperator.validate(target(database))));
        }
    }

    @Test
    void aFreshMigratedDatabaseValidatesWithoutAnImport() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            assertEquals(0, DatabaseOperator.validate(target(database)));
        }
    }

    @Test
    void theApplicationRoleCannotPerformDdlOrTouchAuditHistory() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                for (String ddl : List.of(
                        "CREATE TABLE forbidden (id TEXT)",
                        "ALTER TABLE accounts ADD COLUMN forbidden INTEGER",
                        "DROP TABLE settings")) {
                    assertThrows(SQLException.class, () -> statement.execute(ddl));
                }
                for (String audit : List.of(
                        "INSERT INTO schema_events(id, at_ns, migration, prev_digest, row_digest) "
                                + "VALUES (9999, 1, 'forged', 'x', 'y')",
                        "UPDATE schema_events SET at_ns = 0",
                        "DELETE FROM schema_events")) {
                    assertThrows(SQLException.class, () -> statement.execute(audit));
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM schema_events")) {
                    assertTrue(rows.next() && rows.getLong(1) >= 0, "reads stay allowed");
                }
            }
        }
    }

    @Test
    void theApplicationRefusesARoleThatCanWriteHistoryOrCreateSchema() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            IllegalStateException privileged = assertThrows(IllegalStateException.class,
                    () -> AppRoleGuard.requireLeastPrivilege(database.ownerDataSource()));
            assertTrue(privileged.getMessage().startsWith("app_role_not_least_privilege"),
                    privileged.getMessage());
            // The granted application role passes the same guard the boot will run.
            AppRoleGuard.requireLeastPrivilege(database.dataSource());
        }
    }

    @Test
    void theOperatorStripsHistoryWritesFromAnyRoleThatArrivedWithThem() throws Exception {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            String stray = database.database().replaceFirst("agora_test_", "agora_stray_");
            database.executeAsAdmin("CREATE ROLE " + stray + " LOGIN PASSWORD 'stray-pass'");
            // A role is cluster-wide, but table privileges live in one database: the grant runs on
            // the database under test, as its owner, the way a deployment's stray grant arrives.
            database.execute("GRANT ALL ON ALL TABLES IN SCHEMA public TO " + stray);
            try {
                // The second run applies nothing but still normalizes whatever the role arrived with.
                assertEquals(0, DatabaseOperator.migrate(target(database)));
                String url = EphemeralDatabase.withDatabase(
                        EphemeralDatabase.adminCredentials.get()[0], database.database());
                try (Connection connection = java.sql.DriverManager.getConnection(url, stray, "stray-pass");
                     Statement statement = connection.createStatement()) {
                    for (String write : List.of(
                            "UPDATE schema_events SET at_ns = 0",
                            "DELETE FROM schema_events",
                            "INSERT INTO schema_events(id, at_ns, migration, prev_digest, row_digest) "
                                    + "VALUES (9999, 1, 'forged', 'x', 'y')",
                            "TRUNCATE schema_events",
                            "UPDATE flyway_schema_history SET checksum = 0")) {
                        assertThrows(SQLException.class, () -> statement.execute(write));
                    }
                    try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM threads")) {
                        assertTrue(rows.next() && rows.getLong(1) >= 0, "product reads survive the sweep");
                    }
                }
            } finally {
                // The same database owns the stray privileges; only the role drop is cluster-level.
                database.execute("REVOKE ALL ON ALL TABLES IN SCHEMA public FROM " + stray);
                database.execute("REVOKE ALL ON SCHEMA public FROM " + stray);
                database.executeAsAdmin("DROP ROLE IF EXISTS " + stray);
            }
        }
    }

    @Test
    void theOperatorRefusesToMigrateWithoutSchemaOwnership() {
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            // Strip the cluster default that would let a non-owner create the schema on older
            // servers, so the refusal here is the operator's own decision on every version.
            database.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            assertEquals("operator_not_schema_owner",
                    refusal(() -> DatabaseOperator.migrate(applicationTarget(database))));
            assertEquals(3, historyRows(database), "the refused run must not have touched the history");
        }
    }

    @Test
    void theOperatorRefusesWithoutDatasourceEnvironmentAndPrintsNoCredentials() {
        Map<String, String> empty = Map.of();
        assertEquals(1, DatabaseOperator.run(new String[] {"migrate"}, empty,
                new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),
                new java.io.PrintStream(java.io.OutputStream.nullOutputStream())));
        Map<String, String> withSecret = Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/agora_operator_probe",
                "QUARKUS_DATASOURCE_USERNAME", "owner",
                "QUARKUS_DATASOURCE_PASSWORD", "operator-secret-password");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        assertEquals(1, DatabaseOperator.run(new String[] {"migrate"}, withSecret,
                new java.io.PrintStream(out), new java.io.PrintStream(err)));
        String printed = out + "\n" + err;
        assertFalse(printed.contains("operator-secret-password"), printed);
        assertFalse(printed.contains("jdbc:postgresql"), printed);
    }

    private static DatabaseOperator.Target target(EphemeralDatabase database) {
        String url = EphemeralDatabase.withDatabase(
                EphemeralDatabase.adminCredentials.get()[0], database.database());
        String[] admin = EphemeralDatabase.adminCredentials.get();
        return new DatabaseOperator.Target(url, admin[1], admin[2],
                database.database().replaceFirst("agora_test_", "agora_app_"));
    }

    /** The application role's own datasource environment, the shape a misconfigured boot would get. */
    private static DatabaseOperator.Target applicationTarget(EphemeralDatabase database) {
        String[] admin = EphemeralDatabase.adminCredentials.get();
        return new DatabaseOperator.Target(
                EphemeralDatabase.withDatabase(admin[0], database.database()),
                database.appRoleName(), database.appRolePassword(), null);
    }

    private static DatabaseOperator.Target ownerTarget(EphemeralDatabase database) {
        DatabaseOperator.Target withRole = target(database);
        return new DatabaseOperator.Target(withRole.url(), withRole.username(), withRole.password(), null);
    }

    private static long historyRows(EphemeralDatabase database) {
        try (Connection connection = database.ownerDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            rows.next();
            return rows.getLong(1);
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String refusal(Runnable action) {
        try {
            action.run();
        } catch (DatabaseOperator.Refusal refused) {
            return refused.getMessage();
        }
        throw new AssertionError("expected a refusal");
    }
}
