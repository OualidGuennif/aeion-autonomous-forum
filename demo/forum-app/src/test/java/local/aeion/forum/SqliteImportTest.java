package local.aeion.forum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Feature 002: the SQLite cutover, exercised against the frozen legacy shape.
 *
 * The import reads a quiesced backup file read-only, lands exactly its rows - identifiers,
 * epoch-nanosecond timestamps and content, nothing appended - refuses anything that is not already
 * normalised or whose relations break, and records the boundary in the manifest. A replay changes
 * nothing, a restored copy re-validates, and one altered row is enough to block the cutover -
 * while the source file stays untouched throughout.
 */
class SqliteImportTest {

    private static final long BASE_NS = 1_790_000_000_000_000_000L;
    private static final String OWNER_ID = "a0000000-0000-4000-8000-000000000001";
    private static final String MEMBER_ID = "b0000000-0000-4000-8000-000000000002";
    private static final String THREAD_ONE = "c0000000-0000-4000-8000-000000000003";
    private static final String ARGON = "$argon2id$v=19$m=65536,t=3,p=1$fixturesalt$fixturehash";

    @Test
    void importsRowCountsIdentifiersTimestampsContentAndChain() throws Exception {
        Path backup = buildFixture();
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.ImportResult result =
                    DatabaseOperator.importSqlite(target(database), backup);
            assertEquals(7, result.tables());
            assertEquals(10, result.inserted(), "every legacy row lands, and nothing else");
            assertEquals(BASE_NS + 5_000_000_000L, result.boundaryNs());
            assertEquals(10, DatabaseOperator.validate(target(database)));

            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(2, count(statement, "accounts"));
                assertEquals(1, count(statement, "sessions"));
                assertEquals(2, count(statement, "threads"));
                assertEquals(2, count(statement, "schema_events"), "the legacy events, unchanged");
                assertEquals(1, count(statement, "thread_outbox"));
                ResultSet ids = statement.executeQuery(
                        "SELECT id FROM accounts ORDER BY created_ns");
                ids.next();
                assertEquals(OWNER_ID, ids.getString(1), "identifiers are stable");
                ResultSet stamps = statement.executeQuery(
                        "SELECT created_ns FROM threads WHERE id = '" + THREAD_ONE + "'");
                stamps.next();
                assertEquals(BASE_NS + 4_000_000_000L, stamps.getLong(1), "timestamps are preserved");
                ResultSet titles = statement.executeQuery(
                        "SELECT title FROM threads WHERE id = '" + THREAD_ONE + "'");
                titles.next();
                assertEquals("A quiet start", titles.getString(1), "content is imported as stored");
                ResultSet log = statement.executeQuery(
                        "SELECT received_ns, payload FROM thread_event_log "
                                + "WHERE event_id = 'f0000000-0000-4000-8000-000000000006'");
                log.next();
                assertEquals(BASE_NS + 5_000_000_000L, log.getLong(1),
                        "a column lands in its own column, whatever order the legacy table declares");
                assertEquals("{\"event_id\":\"f6\",\"type\":\"thread.created\"}", log.getString(2),
                        "the event payload is preserved byte for byte");
            }
        }
    }

    @Test
    void aReplayedImportDuplicatesNothing() throws Exception {
        Path backup = buildFixture();
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.importSqlite(target(database), backup);
            DatabaseOperator.ImportResult replay =
                    DatabaseOperator.importSqlite(target(database), backup);
            assertEquals(0, replay.inserted());
            assertEquals(10, replay.skipped());
            assertEquals(10, DatabaseOperator.validate(target(database)));
            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(2, count(statement, "accounts"));
                assertEquals(2, count(statement, "schema_events"));
            }
        }
    }

    @Test
    void aRestoredCopyReproducesCountsAndIntegrity() throws Exception {
        Path backup = buildFixture();
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.importSqlite(target(database), backup);
            String copy = EphemeralDatabase.randomTestName();
            try {
                database.executeAsAdmin("CREATE DATABASE " + copy + " TEMPLATE " + database.database());
                DatabaseOperator.Target copyTarget = new DatabaseOperator.Target(
                        target(database).url().replace(database.database(), copy),
                        target(database).username(), target(database).password(), null);
                assertEquals(10, DatabaseOperator.validate(copyTarget));
            } finally {
                database.executeAsAdmin("DROP DATABASE IF EXISTS " + copy + " WITH (FORCE)");
            }
        }
    }

    @Test
    void anAlteredRowBlocksTheCutover() throws Exception {
        Path backup = buildFixture();
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.importSqlite(target(database), backup);
            database.execute("UPDATE threads SET title = 'Altered by hand' WHERE id = '" + THREAD_ONE + "'");
            assertEquals("manifest_mismatch: threads", refusal(() -> DatabaseOperator.validate(target(database))));
        }
    }

    @Test
    void aFailedValidationLeavesTheSourceUntouched() throws Exception {
        Path backup = buildFixture();
        byte[] before = Files.readAllBytes(backup);
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.importSqlite(target(database), backup);
            database.execute("DELETE FROM sessions");
            assertEquals("manifest_mismatch: sessions",
                    refusal(() -> DatabaseOperator.validate(target(database))));
        }
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(backup)),
                "the immutable source must never be written");
    }

    @Test
    void importsAReadOnlyBackupAndLeavesItUntouched() throws Exception {
        Path backup = buildFixture();
        Files.setPosixFilePermissions(backup, PosixFilePermissions.fromString("r--r--r--"));
        byte[] before = Files.readAllBytes(backup);
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.ImportResult result =
                    DatabaseOperator.importSqlite(target(database), backup);
            assertEquals(7, result.tables());
            assertEquals(10, result.inserted(), "every legacy row lands, and nothing else");
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(backup)),
                    "an immutable source must never be written");
        }
    }

    @Test
    void importsRowsThatStillLiveInTheBackupWalSidecar() throws Exception {
        Path staged = stageWalBackup();
        byte[] before = Files.readAllBytes(staged);
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.ImportResult result =
                    DatabaseOperator.importSqlite(target(database), staged);
            assertEquals(11, result.inserted(), "a committed sidecar frame is part of the backup");
            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(3, count(statement, "threads"), "the newest thread is not read as absent");
            }
            assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(staged)),
                    "recovering a WAL must never reach back into the immutable source");
        }
    }

    @Test
    void importsAnIdentifierTheLegacyProductWroteInItsOwnShape() throws Exception {
        Path backup = buildFixture("threads", List.of(
                "'thread-first', 'A quiet start', 'The first imported body.', '" + MEMBER_ID + "', "
                        + (BASE_NS + 4_000_000_000L),
                "'D0000000-0000-4000-8000-0000000000AA', 'Second thread', 'Another imported body.', '"
                        + MEMBER_ID + "', " + (BASE_NS + 5_000_000_000L)));
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            assertEquals(10, DatabaseOperator.importSqlite(target(database), backup).inserted(),
                    "the import does not invent an id format the legacy product never declared");
            assertEquals(10, DatabaseOperator.validate(target(database)));
            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet ids = statement.executeQuery("SELECT id FROM threads ORDER BY created_ns")) {
                ids.next();
                assertEquals("thread-first", ids.getString(1), "an opaque id is preserved, not reformatted");
                ids.next();
                assertEquals("D0000000-0000-4000-8000-0000000000AA", ids.getString(1));
            }
        }
    }

    /** A quiesced WAL backup: its newest committed rows are still only in the -wal sidecar. */
    private static Path stageWalBackup() throws Exception {
        Path live = buildFixture();
        Path staged = Path.of("build", "test-db", "legacy-wal-" + UUID.randomUUID() + ".sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + live);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("INSERT INTO threads VALUES ('d2000000-0000-4000-8000-000000000007', "
                    + "'Committed after the checkpoint', 'A row that still lives in the WAL.', '"
                    + MEMBER_ID + "', " + (BASE_NS + 7_000_000_000L) + ")");
            Files.copy(live, staged);
            Files.copy(Path.of(live + "-wal"), Path.of(staged + "-wal"));
        }
        return staged;
    }

    @Test
    void importsAPartialSnapshotByImportingWhatItCarries() throws Exception {
        Path backup = buildFixture();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE thread_outbox");
            statement.execute("DROP TABLE thread_event_log");
        }
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.ImportResult result =
                    DatabaseOperator.importSqlite(target(database), backup);
            assertEquals(8, result.inserted(), "the tables the snapshot carries are imported");
            assertEquals(8, DatabaseOperator.validate(target(database)));
            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM thread_outbox")) {
                rows.next();
                assertEquals(0, rows.getLong(1), "an absent table imports as empty");
            }
        }
    }

    @Test
    void refusesContentThatIsNotNormalised() throws Exception {
        Path backup = buildFixture("threads", List.of(
                "'" + THREAD_ONE + "', ' padded title', 'A body that stands.', '" + MEMBER_ID + "', "
                        + (BASE_NS + 4_000_000_000L)));
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            assertEquals("import_not_normalized: threads.title",
                    refusal(() -> DatabaseOperator.importSqlite(target(database), backup)));
            try (Connection connection = database.dataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                assertEquals(0, count(statement, "threads"), "a refused import must insert nothing");
            }
        }
    }

    @Test
    void refusesABrokenRelation() throws Exception {
        String unknown = "e0000000-0000-4000-8000-0000000000ff";
        Path backup = buildFixture("sessions", List.of(
                "'" + "0123456789abcdef".repeat(4) + "', '" + unknown + "', " + BASE_NS + ", "
                        + (BASE_NS + 900_000_000_000L) + ", NULL"));
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            assertEquals("import_broken_relation: sessions.account_id",
                    refusal(() -> DatabaseOperator.importSqlite(target(database), backup)));
        }
    }

    @Test
    void refusesAPlausibleButImpossibleTimestamp() throws Exception {
        Path backup = buildFixture("accounts", List.of(
                "'" + OWNER_ID + "', 'owner-person', '" + ARGON + "', 'owner', -5, NULL, 0"));
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            assertEquals("import_bad_timestamp: accounts.created_ns",
                    refusal(() -> DatabaseOperator.importSqlite(target(database), backup)));
        }
    }

    @Test
    void refusesImportIntoATargetThatIsNotFresh() throws Exception {
        Path backup = buildFixture();
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            database.execute("INSERT INTO settings(name, value, updated_ns) VALUES ('pre.existing', 'x', 1)");
            assertEquals("import_target_not_fresh: settings",
                    refusal(() -> DatabaseOperator.importSqlite(target(database), backup)));
        }
    }

    @Test
    void refusesADifferentSourceOnReplay() throws Exception {
        Path first = buildFixture();
        Path second = buildFixture("threads", List.of(
                "'" + THREAD_ONE + "', 'A quiet start', 'The first imported body.', '" + MEMBER_ID + "', "
                        + (BASE_NS + 4_000_000_000L),
                "'d0000000-0000-4000-8000-000000000004', 'Second thread', 'Another imported body.', '"
                        + MEMBER_ID + "', " + (BASE_NS + 5_000_000_000L),
                "'d1000000-0000-4000-8000-000000000004', 'A later thread', 'Written before the quiesce.', '"
                        + MEMBER_ID + "', " + (BASE_NS + 6_000_000_000L)));
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            DatabaseOperator.importSqlite(target(database), first);
            assertEquals("import_source_mismatch: threads",
                    refusal(() -> DatabaseOperator.importSqlite(target(database), second)));
        }
    }

    @Test
    void theCommandLineReportsCountsAndNeverData() throws Exception {
        Path backup = buildFixture();
        try (EphemeralDatabase database = EphemeralDatabase.create()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
            Map<String, String> env = environment(database);
            assertEquals(0, DatabaseOperator.run(new String[] {"import-sqlite", backup.toString()},
                    env, new PrintStream(out), new PrintStream(err)));
            assertEquals(0, DatabaseOperator.run(new String[] {"validate"}, env,
                    new PrintStream(out), new PrintStream(err)));
            String printed = out + "\n" + err;
            assertTrue(printed.contains("import ok tables=7"), printed);
            assertTrue(printed.contains("validate ok rows=10"), printed);
            for (String secret : List.of("owner-person", "member-person", "Agora", "A quiet start")) {
                assertTrue(!printed.contains(secret), "output must not carry imported data: " + printed);
            }
        }
    }

    /** A legacy fixture with a valid base state; one table's rows can be replaced to break it. */
    private static Path buildFixture() throws Exception {
        return buildFixture(null, null);
    }

    private static Path buildFixture(String replaceTable, List<String> replacementRows) throws Exception {
        Path file = Path.of("build", "test-db", "legacy-" + UUID.randomUUID() + ".sqlite");
        Files.createDirectories(file.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            for (String sql : statements(Files.readString(
                    Path.of("src", "test", "resources", "db", "legacy-sqlite", "schema.sql")))) {
                statement.execute(sql);
            }
            execute(statement, "INSERT INTO settings VALUES ('forum.name', 'Agora', " + BASE_NS + ")");
            execute(statement, "INSERT INTO accounts VALUES ('" + OWNER_ID + "', 'owner-person', '"
                    + ARGON + "', 'owner', " + (BASE_NS + 1_000_000_000L) + ", NULL, 0)");
            execute(statement, "INSERT INTO accounts VALUES ('" + MEMBER_ID + "', 'member-person', '"
                    + ARGON + "', 'member', " + (BASE_NS + 2_000_000_000L) + ", NULL, 0)");
            execute(statement, "INSERT INTO sessions VALUES ('" + "0123456789abcdef".repeat(4)
                    + "', '" + OWNER_ID + "', " + (BASE_NS + 3_000_000_000L) + ", "
                    + (BASE_NS + 903_000_000_000L) + ", NULL)");
            execute(statement, "INSERT INTO threads VALUES ('" + THREAD_ONE + "', 'A quiet start', "
                    + "'The first imported body.', '" + MEMBER_ID + "', " + (BASE_NS + 4_000_000_000L) + ")");
            execute(statement, "INSERT INTO threads VALUES ('d0000000-0000-4000-8000-000000000004', "
                    + "'Second thread', 'Another imported body.', '" + MEMBER_ID + "', "
                    + (BASE_NS + 5_000_000_000L) + ")");
            execute(statement, "INSERT INTO schema_events VALUES (1, " + BASE_NS + ", 'V1__initial.sql', NULL)");
            execute(statement, "INSERT INTO schema_events VALUES (2, " + (BASE_NS + 2_000_000_000L)
                    + ", 'V2__threads_and_events.sql', 'applied')");
            execute(statement, "INSERT INTO thread_outbox VALUES "
                    + "('e0000000-0000-4000-8000-000000000005', 'agora.threads', "
                    + "'{\"event_id\":\"e5\",\"type\":\"thread.created\"}', " + (BASE_NS + 5_000_000_000L)
                    + ", 0, NULL, NULL)");
            execute(statement, "INSERT INTO thread_event_log(event_id, topic, received_ns, payload) "
                    + "VALUES ('f0000000-0000-4000-8000-000000000006', 'agora.threads', "
                    + (BASE_NS + 5_000_000_000L) + ", "
                    + "'{\"event_id\":\"f6\",\"type\":\"thread.created\"}')");
            if (replaceTable != null && replacementRows != null) {
                execute(statement, "DELETE FROM " + replaceTable);
                for (String blob : replacementRows) {
                    for (String row : rowsOf(blob)) {
                        execute(statement, "INSERT INTO " + replaceTable + " VALUES " + row);
                    }
                }
            }
        }
        return file;
    }

    private static List<String> rowsOf(String blob) {
        List<String> rows = new ArrayList<>();
        for (String row : blob.split("\n")) {
            if (!row.isBlank()) {
                rows.add("(" + row + ")");
            }
        }
        return rows;
    }

    private static void execute(Statement statement, String sql) throws Exception {
        statement.execute(sql);
    }

    private static List<String> statements(String script) {
        StringBuilder body = new StringBuilder();
        for (String line : script.split("\n")) {
            if (line.stripLeading().startsWith("--")) {
                continue;
            }
            body.append(line).append('\n');
        }
        List<String> parsed = new ArrayList<>();
        for (String statement : body.toString().split(";")) {
            if (!statement.isBlank()) {
                parsed.add(statement.strip());
            }
        }
        return parsed;
    }

    private static DatabaseOperator.Target target(EphemeralDatabase database) {
        String[] admin = EphemeralDatabase.adminCredentials.get();
        String url = EphemeralDatabase.withDatabase(admin[0], database.database());
        return new DatabaseOperator.Target(url, admin[1], admin[2],
                database.database().replaceFirst("agora_test_", "agora_app_"));
    }

    private static Map<String, String> environment(EphemeralDatabase database) {
        DatabaseOperator.Target owner = target(database);
        return Map.of(
                "QUARKUS_DATASOURCE_JDBC_URL", owner.url(),
                "QUARKUS_DATASOURCE_USERNAME", owner.username(),
                "QUARKUS_DATASOURCE_PASSWORD", owner.password(),
                "AEION_DB_APP_ROLE", owner.appRole());
    }

    private static long count(Statement statement, String table) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String refusal(ThrowingAction action) {
        try {
            action.run();
        } catch (DatabaseOperator.Refusal refused) {
            return refused.getMessage();
        } catch (Exception unexpected) {
            throw new AssertionError("refused with an unexpected failure, not a stable code", unexpected);
        }
        throw new AssertionError("expected a refusal");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
