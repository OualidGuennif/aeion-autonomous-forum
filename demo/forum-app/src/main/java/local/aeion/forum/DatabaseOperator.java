package local.aeion.forum;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feature 002: the operator-only migration and cutover job, a separate JVM from the application.
 *
 * Commands (the datasource environment is the same one the application reads, but here it holds
 * the migration-owner credentials, which never exist anywhere else):
 * {@code migrate} applies the versioned Flyway SQL and grants the application role its
 * least-privilege DML; {@code validate} refuses a schema that drifted, is behind, is ahead, or
 * whose imported rows no longer match the cutover manifest; {@code import-sqlite <readonly path>}
 * imports an immutable SQLite backup into a fresh database, once, and records the boundary.
 *
 * Every refusal exits nonzero with a stable code. Nothing here prints data, connection settings
 * or credentials; only counts and table names.
 */
public final class DatabaseOperator {

    static final long MAX_IMPORT_ROWS = 1_000_000;
    static final long MAX_TIMESTAMP_NS = 4_102_444_800_000_000_000L;

    /** The copy the job works on is bounded too: a backup this large is not a bounded import. */
    static final long MAX_IMPORT_BYTES = 1L << 30;

    /** Sidecars that belong to the backup file; a WAL can hold rows the main file does not. */
    private static final List<String> BACKUP_SIDECARS = List.of("-wal", "-shm");

    /** One run's target: the owner's datasource environment, plus the application role to grant. */
    record Target(String url, String username, String password, String appRole) {
    }

    record ImportResult(long tables, long inserted, long skipped, long boundaryNs) {
    }

    /** A refusal with a stable code; the message is a code, never a value. */
    static final class Refusal extends RuntimeException {
        Refusal(String code) {
            super(code);
        }
    }

    public static void main(String[] args) {
        int code = run(args, System.getenv(), System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
        try {
            execute(args, env, out);
            return 0;
        } catch (Refusal refusal) {
            err.println("operator_refused: " + refusal.getMessage());
            return 1;
        } catch (RuntimeException failure) {
            err.println("operator_refused: operator_failed");
            return 1;
        }
    }

    private static void execute(String[] args, Map<String, String> env, PrintStream out) {
        if (args.length == 0) {
            throw new Refusal("command_missing");
        }
        Target target = target(env);
        switch (args[0]) {
            case "migrate" -> out.println("migrate ok applied=" + migrate(target));
            case "validate" -> out.println("validate ok rows=" + validate(target));
            case "import-sqlite" -> {
                if (args.length != 2) {
                    throw new Refusal("path_missing");
                }
                ImportResult result = importSqlite(target, Path.of(args[1]));
                out.println("import ok tables=" + result.tables() + " inserted=" + result.inserted()
                        + " skipped=" + result.skipped() + " boundary_ns=" + result.boundaryNs());
            }
            default -> throw new Refusal("command_unknown");
        }
    }

    private static Target target(Map<String, String> env) {
        String url = env.get("QUARKUS_DATASOURCE_JDBC_URL");
        if (url == null || url.isBlank()) {
            throw new Refusal("datasource_env_missing");
        }
        DatabaseTls.requireVerifiedTransport(url);
        String username = env.getOrDefault("QUARKUS_DATASOURCE_USERNAME", "");
        String password = env.getOrDefault("QUARKUS_DATASOURCE_PASSWORD", "");
        return new Target(url, username, password, env.get("AEION_DB_APP_ROLE"));
    }

    /** Apply the versioned migrations, then grant the application role its DML-only rights. */
    static long migrate(Target target) {
        requireSchemaOwnership(target);
        long before = historyCount(target);
        try {
            Migrations.engine(target.url(), target.username(), target.password()).migrate();
        } catch (org.flywaydb.core.api.FlywayException failure) {
            throw new Refusal("migration_failed");
        }
        try (Connection connection = connect(target)) {
            if (target.appRole() != null && !target.appRole().isBlank()) {
                grantApplicationRole(connection, target.appRole());
            }
            stripForeignHistoryWrites(connection);
        } catch (SQLException error) {
            throw new Refusal("grant_failed");
        }
        return historyCount(target) - before;
    }

    /** The cutover gate: schema validity first, then the recorded import state, byte for byte. */
    static long validate(Target target) {
        try {
            Migrations.validate(Migrations.engine(target.url(), target.username(), target.password()));
        } catch (org.flywaydb.core.api.FlywayException refused) {
            throw new Refusal("schema_invalid");
        } catch (IllegalStateException refused) {
            String message = refused.getMessage() == null ? "" : refused.getMessage();
            if (message.startsWith("schema_behind")) {
                throw new Refusal("schema_behind");
            }
            if (message.startsWith("schema_ahead") || message.startsWith("schema_invalid")) {
                throw new Refusal("schema_invalid");
            }
            throw refused;
        }
        try (Connection connection = connect(target)) {
            return verifyImportedState(connection);
        } catch (SQLException error) {
            throw new Refusal("validate_failed");
        }
    }

    /** Import an immutable SQLite backup into a fresh database; a replay verifies and changes nothing. */
    static ImportResult importSqlite(Target target, Path backup) {
        if (!Files.isRegularFile(backup) || !Files.isReadable(backup)) {
            throw new Refusal("backup_unreadable");
        }
        migrate(target);
        try (Connection targetConnection = connect(target);
             LegacySource legacy = openLegacy(backup)) {
            Map<String, Boolean> present = presentTables(legacy.connection());
            verifyLegacyCore(present);
            Map<String, List<Object[]>> source = readSource(legacy.connection(), present);
            List<ManifestRow> manifest = readManifest(targetConnection);
            if (!manifest.isEmpty()) {
                return replay(source, manifest, targetConnection);
            }
            return freshImport(source, targetConnection);
        } catch (SQLException error) {
            throw new Refusal("import_failed");
        }
    }

    private static ImportResult freshImport(Map<String, List<Object[]>> source, Connection target) throws SQLException {
        requireFreshTarget(target);
        validateImport(source);
        long boundary = boundaryOf(source);
        long importedNs = importedNowNs();
        target.setAutoCommit(false);
        try {
            long inserted = 0;
            for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
                inserted += insert(table, source.get(table.name()), target);
            }
            advanceIdentity(target, source);
            writeManifest(target, source, boundary, importedNs);
            target.commit();
            return new ImportResult(LegacyCatalog.TABLES.size(), inserted, 0, boundary);
        } catch (SQLException | RuntimeException failure) {
            target.rollback();
            if (failure instanceof Refusal refusal) {
                throw refusal;
            }
            throw new Refusal("import_failed");
        } finally {
            target.setAutoCommit(true);
        }
    }

    private static ImportResult replay(Map<String, List<Object[]>> source, List<ManifestRow> manifest,
                                       Connection target) throws SQLException {
        // A replay must be the same backup: every table's digest is re-derived from the source
        // file and compared against the manifest before the database is trusted. Nothing here
        // writes a single row, so a replayed import can never duplicate what it landed.
        for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
            List<Object[]> rows = source.get(table.name());
            ManifestRow recorded = manifest.stream()
                    .filter(row -> row.tableName.equals(table.name()))
                    .findFirst()
                    .orElseThrow(() -> new Refusal("import_source_mismatch: " + table.name()));
            if (rows == null || rows.size() != recorded.rowCount
                    || !LegacyCatalog.digest(table, rows).equals(recorded.contentDigest)) {
                throw new Refusal("import_source_mismatch: " + table.name());
            }
        }
        long verified = verifyImportedState(target);
        return new ImportResult(manifest.size(), 0, verified, manifest.get(0).boundaryNs);
    }

    private static void requireFreshTarget(Connection target) throws SQLException {
        for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
            if (countRows(target, table.name()) > 0) {
                throw new Refusal("import_target_not_fresh: " + table.name());
            }
        }
    }

    private static long insert(LegacyCatalog.Table table, List<Object[]> rows, Connection target)
            throws SQLException {
        List<String> names = new ArrayList<>(table.names());
        if (table.name().equals("schema_events")) {
            names = new ArrayList<>(names);
            names.add("prev_digest");
            names.add("row_digest");
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(names.size(), "?"));
        String insert = "INSERT INTO " + table.name() + "(" + String.join(", ", names)
                + ") VALUES (" + placeholders + ")";
        long applied = 0;
        String previous = LegacyCatalog.GENESIS;
        try (PreparedStatement statement = target.prepareStatement(insert)) {
            for (Object[] row : rows) {
                for (int column = 0; column < table.columns().size(); column++) {
                    LegacyCatalog.Column spec = table.columns().get(column);
                    if (row[column] == null) {
                        statement.setNull(column + 1, spec.number() ? Types.BIGINT : Types.VARCHAR);
                    } else if (spec.number()) {
                        statement.setLong(column + 1, ((Number) row[column]).longValue());
                    } else {
                        statement.setString(column + 1, row[column].toString());
                    }
                }
                if (table.name().equals("schema_events")) {
                    long id = ((Number) row[0]).longValue();
                    long atNs = ((Number) row[1]).longValue();
                    String kind = String.valueOf(row[2]);
                    String detail = row[3] == null ? null : row[3].toString();
                    String digest = LegacyCatalog.chainDigest(previous, id, atNs, kind, detail);
                    statement.setString(names.size() - 1, previous);
                    statement.setString(names.size(), digest);
                    previous = digest;
                }
                applied += statement.executeUpdate();
            }
        }
        return applied;
    }

    /**
     * Move the audit chain's identity past the imported ids, so a later append never collides.
     *
     * Explicit ids do not touch the column's own sequence, and which statement reads that sequence
     * depends on how the schema declared the column: `ALTER TABLE ... RESTART` is the identity-native
     * form, `setval` on the owned sequence is the serial form. The catalog decides which one runs -
     * an error-driven try would abort the import transaction before the second statement is reached
     * - and a stale sequence refuses, because it would hand the application ids that the imported
     * history already uses.
     */
    private static void advanceIdentity(Connection target, Map<String, List<Object[]>> source)
            throws SQLException {
        long top = source.get("schema_events").stream()
                .mapToLong(row -> ((Number) row[0]).longValue())
                .max()
                .orElse(0);
        if (top <= 0) {
            return;
        }
        String identity = identityGeneration(target);
        try (Statement statement = target.createStatement()) {
            if (identity.isEmpty()) {
                statement.execute("SELECT setval(pg_get_serial_sequence('schema_events', 'id'), " + top + ")");
            } else {
                statement.execute("ALTER TABLE schema_events ALTER COLUMN id RESTART WITH " + (top + 1));
            }
        }
    }

    /** How schema_events.id is generated: 'a' or 'd' for an identity column, empty for a serial. */
    private static String identityGeneration(Connection target) throws SQLException {
        try (PreparedStatement read = target.prepareStatement(
                "SELECT COALESCE(attidentity::text, '') FROM pg_attribute "
                        + "WHERE attrelid = 'schema_events'::regclass AND attname = 'id'");
             ResultSet row = read.executeQuery()) {
            return row.next() ? row.getString(1) : "";
        }
    }

    private static void writeManifest(Connection target, Map<String, List<Object[]>> source,
                                      long boundary, long importedNs) throws SQLException {
        StringBuilder sourceDigest = new StringBuilder();
        try (PreparedStatement statement = target.prepareStatement(
                "INSERT INTO import_manifest(table_name, row_count, content_digest, boundary_ns, "
                        + "imported_ns, source_digest) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
                // The manifest describes exactly what the source held: every imported table's
                // count and digest, the cutover boundary and the chain of source digests.
                List<Object[]> rows = source.get(table.name());
                String digest = LegacyCatalog.digest(table, rows);
                sourceDigest.append(table.name()).append('=').append(digest).append('\n');
                statement.setString(1, table.name());
                statement.setLong(2, rows.size());
                statement.setString(3, digest);
                statement.setLong(4, boundary);
                statement.setLong(5, importedNs);
                statement.setString(6, LegacyCatalog.sha256Hex(sourceDigest.toString()));
                statement.executeUpdate();
            }
        }
    }

    private static long verifyImportedState(Connection connection) throws SQLException {
        verifyChain(connection);
        List<ManifestRow> manifest = readManifest(connection);
        long total = 0;
        for (ManifestRow row : manifest) {
            LegacyCatalog.Table table = LegacyCatalog.table(row.tableName);
            List<Object[]> rows = LegacyCatalog.readRows(connection, table);
            if (rows.size() != row.rowCount || !LegacyCatalog.digest(table, rows).equals(row.contentDigest)) {
                throw new Refusal("manifest_mismatch: " + row.tableName);
            }
            total += rows.size();
        }
        verifyManifestConsistency(manifest);
        return total;
    }

    private static void verifyManifestConsistency(List<ManifestRow> manifest) {
        if (manifest.isEmpty()) {
            return;
        }
        // The rows chain by digest in catalog order, exactly the way the import wrote them: each
        // row's source digest covers every table recorded before it. Any edit, drop or reorder of
        // a manifest row breaks the chain, so the recorded state cannot be trusted piecemeal.
        StringBuilder chain = new StringBuilder();
        String boundary = null;
        for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
            ManifestRow row = manifest.stream()
                    .filter(candidate -> candidate.tableName.equals(table.name()))
                    .findFirst()
                    .orElseThrow(() -> new Refusal("manifest_mismatch: source"));
            chain.append(table.name()).append('=').append(row.contentDigest).append('\n');
            if (!row.sourceDigest.equals(LegacyCatalog.sha256Hex(chain.toString()))) {
                throw new Refusal("manifest_mismatch: source");
            }
            String rowBoundary = row.boundaryNs + "|" + row.importedNs;
            if (boundary == null) {
                boundary = rowBoundary;
            } else if (!boundary.equals(rowBoundary)) {
                throw new Refusal("manifest_mismatch: source");
            }
        }
    }

    private static void verifyChain(Connection connection) throws SQLException {
        String previous = LegacyCatalog.GENESIS;
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT id, at_ns, migration, detail, prev_digest, row_digest FROM schema_events ORDER BY id");
             ResultSet rows = read.executeQuery()) {
            while (rows.next()) {
                long id = rows.getLong(1);
                long atNs = rows.getLong(2);
                String kind = rows.getString(3);
                String detail = rows.getString(4);
                String previousDigest = rows.getString(5);
                String rowDigest = rows.getString(6);
                if (!previousDigest.equals(previous)
                        || !rowDigest.equals(LegacyCatalog.chainDigest(previous, id, atNs, kind, detail))) {
                    throw new Refusal("audit_chain_broken");
                }
                previous = rowDigest;
            }
        }
    }

    private record ManifestRow(String tableName, long rowCount, String contentDigest,
                               long boundaryNs, long importedNs, String sourceDigest) {
    }

    private static List<ManifestRow> readManifest(Connection connection) throws SQLException {
        List<ManifestRow> rows = new ArrayList<>();
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT table_name, row_count, content_digest, boundary_ns, imported_ns, source_digest "
                        + "FROM import_manifest");
             ResultSet result = read.executeQuery()) {
            while (result.next()) {
                rows.add(new ManifestRow(result.getString(1), result.getLong(2), result.getString(3),
                        result.getLong(4), result.getLong(5), result.getString(6)));
            }
        } catch (SQLException missing) {
            // A database that was migrated but never received an import has no manifest at all.
            if (!"42P01".equals(missing.getSQLState())) {
                throw missing;
            }
        }
        return rows;
    }

    /**
     * Read the backup, one catalogued table at a time. A table the snapshot does not carry imports
     * as empty: the last SQLite commit owned the product's whole history, but a partial snapshot of
     * it is still a legal boundary, and the manifest records what each table held. A column the
     * snapshot predates is only filled where the target says a value may be absent (nullable, or
     * defaulted); a required column that is not there is a shape the import refuses by name, and a
     * read failure on a present table is never mistaken for absence.
     */
    private static Map<String, List<Object[]>> readSource(Connection legacy,
                                                          Map<String, Boolean> present) throws SQLException {
        Map<String, List<Object[]>> data = new java.util.LinkedHashMap<>();
        long total = 0;
        for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
            List<Object[]> rows = List.of();
            if (present.getOrDefault(table.name(), false)) {
                rows = LegacyCatalog.readRows(legacy, table, projection(table, snapshotColumns(legacy, table.name())));
            }
            total += rows.size();
            data.put(table.name(), rows);
        }
        if (total > MAX_IMPORT_ROWS) {
            throw new Refusal("import_too_large");
        }
        return data;
    }

    /** The canonical column list of one table, read through what the snapshot carries. */
    private static List<String> projection(LegacyCatalog.Table table, List<String> carried) {
        List<String> select = new ArrayList<>();
        for (LegacyCatalog.Column column : table.columns()) {
            if (carried.contains(column.name())) {
                select.add(column.name());
            } else if (column.absent() == LegacyCatalog.Absent.REQUIRED) {
                throw new Refusal("import_legacy_shape: " + table.name() + "." + column.name());
            } else if (column.absent() == LegacyCatalog.Absent.ZERO) {
                select.add("0");
            } else {
                select.add("NULL");
            }
        }
        return select;
    }

    /** The columns the snapshot carries for one table; the names come from SQLite's own schema. */
    private static List<String> snapshotColumns(Connection legacy, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = legacy.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        return columns;
    }

    /** Which catalogued tables the backup actually carries, straight from its schema. */
    private static Map<String, Boolean> presentTables(Connection legacy) throws SQLException {
        Map<String, Boolean> present = new java.util.HashMap<>();
        try (PreparedStatement read = legacy.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table'");
             ResultSet rows = read.executeQuery()) {
            while (rows.next()) {
                present.put(rows.getString(1), Boolean.TRUE);
            }
        }
        return present;
    }

    /**
     * The guard against a file that is not the product's database at all.
     *
     * The brief names accounts, sessions and the audit chain as the import's subject, so a backup
     * without the two tables every forum row hangs from is some other SQLite database, and
     * importing it would silently produce an empty forum whose manifest verifies perfectly. Other
     * catalogued tables may be absent: the last SQLite commit owned the whole history, but a
     * partial snapshot of it is still a legal cutover boundary, and the audit chain is verified
     * over the events the snapshot actually carries.
     */
    private static void verifyLegacyCore(Map<String, Boolean> present) {
        for (String table : List.of("accounts", "sessions")) {
            if (!present.getOrDefault(table, false)) {
                throw new Refusal("import_legacy_schema_missing: " + table);
            }
        }
    }

    /**
     * The normalisation, timestamp and relation checks the import runs per row. Content must arrive
     * already normalised because the product only ever wrote stripped text, and every value must
     * still fit the column width the product declares; the import rewrites nothing, so a row that
     * breaks either rule cannot be landed and refuses the whole run instead of drifting silently.
     */
    private static void validateImport(Map<String, List<Object[]>> source) {
        Set<String> accounts = new java.util.HashSet<>();
        for (Object[] row : source.get("accounts")) {
            identifier(row[0], "accounts.id");
            normalized(row[1], "accounts.username", 64);
            if (text(row[2]) == null) {
                throw new Refusal("import_invalid: accounts.password");
            }
            String role = text(row[3]);
            if (role == null || !Set.of("owner", "member").contains(role)) {
                throw new Refusal("import_invalid: accounts.role");
            }
            inRange(row[4], "accounts.created_ns");
            nullableInRange(row[5], "accounts.locked_ns");
            if (row[6] == null || ((Number) row[6]).longValue() < 0) {
                throw new Refusal("import_out_of_range: accounts.failures");
            }
            accounts.add(text(row[0]));
        }
        for (Object[] row : source.get("sessions")) {
            identifier(row[0], "sessions.id");
            if (!accounts.contains(text(row[1]))) {
                throw new Refusal("import_broken_relation: sessions.account_id");
            }
            long issued = inRange(row[2], "sessions.issued_ns");
            long expires = inRange(row[3], "sessions.expires_ns");
            if (expires <= issued) {
                throw new Refusal("import_out_of_range: sessions.expires_ns");
            }
            nullableInRange(row[4], "sessions.revoked_ns");
        }
        for (Object[] row : source.get("threads")) {
            identifier(row[0], "threads.id");
            normalized(row[1], "threads.title", 200);
            normalized(row[2], "threads.body", 4000);
            if (!accounts.contains(text(row[3]))) {
                throw new Refusal("import_broken_relation: threads.author_id");
            }
            inRange(row[4], "threads.created_ns");
        }
        for (Object[] row : source.get("settings")) {
            String name = text(row[0]);
            if (name == null || name.isBlank() || name.length() > 120) {
                throw new Refusal("import_invalid: settings.name");
            }
            // A stored value may be empty: refusing to *write* a blank is the API's rule, and the
            // import rewrites nothing, so turning a stored blank into a refused cutover would be a
            // new policy invented at migration time. The width the product declares still bounds it.
            String value = text(row[1]);
            if (value == null || value.length() > 4000) {
                throw new Refusal("import_invalid: settings.value");
            }
            inRange(row[2], "settings.updated_ns");
        }
        long lastId = Long.MIN_VALUE;
        for (Object[] row : source.get("schema_events")) {
            if (row[0] == null) {
                throw new Refusal("import_bad_id: schema_events.id");
            }
            // The ids arrive as the legacy runner issued them, ascending and dense from 1 or from 0;
            // the chain links in that order, so only a repeat (or a value that is not a number at
            // all) is a break the import cannot represent.
            long id = ((Number) row[0]).longValue();
            if (id <= lastId) {
                throw new Refusal("import_out_of_range: schema_events.id");
            }
            lastId = id;
            inRange(row[1], "schema_events.at_ns");
            String kind = text(row[2]);
            if (kind == null || kind.isBlank() || kind.length() > 120) {
                throw new Refusal("import_invalid: schema_events.migration");
            }
            if (text(row[3]) != null && text(row[3]).length() > 4000) {
                throw new Refusal("import_invalid: schema_events.detail");
            }
        }
        for (Object[] row : source.get("thread_outbox")) {
            identifier(row[0], "thread_outbox.event_id");
            bounded(row[1], "thread_outbox.topic", 120);
            bounded(row[2], "thread_outbox.payload", 16384);
            inRange(row[3], "thread_outbox.created_ns");
            if (row[4] == null || ((Number) row[4]).longValue() < 0) {
                throw new Refusal("import_out_of_range: thread_outbox.attempts");
            }
            nullableInRange(row[5], "thread_outbox.published_ns");
            if (text(row[6]) != null && text(row[6]).length() > 200) {
                throw new Refusal("import_invalid: thread_outbox.last_error");
            }
        }
        for (Object[] row : source.get("thread_event_log")) {
            identifier(row[0], "thread_event_log.event_id");
            bounded(row[1], "thread_event_log.topic", 120);
            bounded(row[2], "thread_event_log.payload", 16384);
            inRange(row[3], "thread_event_log.received_ns");
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** Text as the product wrote it: one stripped value that still fits its declared width. */
    private static void normalized(Object value, String where, int max) {
        String content = text(value);
        if (content == null || content.isBlank() || !content.equals(content.strip())
                || content.length() > max) {
            throw new Refusal("import_not_normalized: " + where);
        }
    }

    private static void bounded(Object value, String where, int max) {
        String content = text(value);
        if (content == null || content.isBlank() || content.length() > max) {
            throw new Refusal("import_invalid: " + where);
        }
    }

    private static long inRange(Object value, String where) {
        if (value == null) {
            throw new Refusal("import_bad_timestamp: " + where);
        }
        long ns = ((Number) value).longValue();
        if (ns < 0 || ns > MAX_TIMESTAMP_NS) {
            throw new Refusal("import_bad_timestamp: " + where);
        }
        return ns;
    }

    private static void nullableInRange(Object value, String where) {
        if (value != null) {
            inRange(value, where);
        }
    }

    /**
     * An identifier the import preserves byte for byte. The shape belongs to the legacy product -
     * account and thread ids were random UUIDs, a session id is the SHA-256 of the cookie, an event
     * id is a UUID again - so the import demands only what its own job needs: present, bounded, one
     * line, and carried over unchanged so the relations and the manifest digests still agree.
     */
    private static void identifier(Object value, String where) {
        String id = text(value);
        if (id == null || id.isBlank() || id.length() > 128 || id.matches(".*[\\r\\n\\t\u0000].*")) {
            throw new Refusal("import_bad_id: " + where);
        }
    }

    private static long importedNowNs() {
        return System.currentTimeMillis() * 1_000_000L;
    }

    private static long boundaryOf(Map<String, List<Object[]>> source) {
        long boundary = 0;
        for (LegacyCatalog.Table table : LegacyCatalog.TABLES) {
            int timestamp = timestampColumn(table);
            if (timestamp < 0) {
                continue;
            }
            for (Object[] row : source.get(table.name())) {
                if (row[timestamp] != null) {
                    boundary = Math.max(boundary, ((Number) row[timestamp]).longValue());
                }
            }
        }
        return boundary;
    }

    private static int timestampColumn(LegacyCatalog.Table table) {
        return switch (table.name()) {
            case "settings" -> 2;
            case "accounts" -> 4;
            case "sessions" -> 2;
            case "threads" -> 4;
            case "schema_events" -> 1;
            case "thread_outbox" -> 3;
            case "thread_event_log" -> 3;
            default -> -1;
        };
    }

    /**
     * Migration runs only as a role that can create the schema it applies: on a cluster where
     * {@code PUBLIC} still carries {@code CREATE}, an application-side role would otherwise be
     * able to run the job and end up owning the history it is meant to stay out of.
     */
    private static void requireSchemaOwnership(Target target) {
        try (Connection connection = connect(target);
             PreparedStatement read = connection.prepareStatement(
                     "SELECT has_schema_privilege('public', 'CREATE')");
             ResultSet rows = read.executeQuery()) {
            rows.next();
            if (!rows.getBoolean(1)) {
                throw new Refusal("operator_not_schema_owner");
            }
        } catch (SQLException error) {
            throw new Refusal("migration_failed");
        }
    }

    /**
     * Grant the application role DML on product tables and nothing else, whatever privileges the
     * role arrived with: the audit tables, the manifest and schema creation are revoked first, so
     * the end state is the documented least privilege. {@code CREATE} is also stripped from
     * {@code PUBLIC} on that schema, so the application role's status never depends on cluster
     * defaults. The role must be separate from the migration owner: one role holding both
     * authorities defeats the split. The name is quoted and escaped everywhere, so any
     * PostgreSQL-safe identifier is accepted and none of them can alter statement text.
     */
    static void grantApplicationRole(Connection connection, String role) throws SQLException {
        if (role == null || role.isBlank() || role.length() > 63
                || !role.matches("[A-Za-z0-9_$][A-Za-z0-9_$-]*")) {
            throw new Refusal("app_role_invalid");
        }
        String quoted = quoteIdentifier(role);
        if (isConnectedRole(connection, role)) {
            throw new Refusal("app_role_is_owner");
        }
        // Schema-wide privileges are normalized whenever the operator may rewrite them. It may not
        // when it was granted CREATE on a database it does not own, and PostgreSQL reports that in
        // one SQLSTATE, so the statements are attempted and only a lack of privilege is skipped:
        // whatever the topology, the end state below is checked and never assumed, and an
        // application role that can still create objects refuses the run.
        revokeSchemaCreation(connection, quoted);
        for (String table : List.of("schema_events", "flyway_schema_history", "import_manifest")) {
            execute(connection, "REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON TABLE " + table
                    + " FROM " + quoted);
        }
        execute(connection, "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE settings, accounts, "
                + "sessions, threads, thread_outbox, thread_event_log TO " + quoted);
        execute(connection, "GRANT SELECT ON TABLE schema_events, flyway_schema_history TO " + quoted);
        if (hasSchemaPrivilege(connection, role)) {
            throw new Refusal("app_role_not_least_privilege: CREATE on schema public");
        }
    }

    /** A safe quoted identifier: the escaping removes the only character that could break out. */
    private static String quoteIdentifier(String role) {
        return "\"" + role.replace("\"", "\"\"") + "\"";
    }

    /**
     * Strip schema-wide creation from {@code PUBLIC} and from the application role, then leave it
     * exactly USAGE on the schema. Every statement needs schema ownership or the grant option; when
     * the operator has neither, PostgreSQL says so with {@code 42501} and the run continues to the
     * end-state check, which refuses an application role that can still create objects.
     */
    private static void revokeSchemaCreation(Connection connection, String quoted) throws SQLException {
        try {
            execute(connection, "REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            execute(connection, "REVOKE ALL ON SCHEMA public FROM " + quoted);
            execute(connection, "GRANT USAGE ON SCHEMA public TO " + quoted);
        } catch (SQLException notTheSchemaOwner) {
            if (!"42501".equals(notTheSchemaOwner.getSQLState())) {
                throw notTheSchemaOwner;
            }
        }
    }

    /** True when the named role can create objects in schema public after the grants. */
    private static boolean hasSchemaPrivilege(Connection connection, String role)
            throws SQLException {
        String literal = "'" + role.replace("'", "''") + "'";
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT has_schema_privilege(" + literal + ", 'public', 'CREATE')");
             ResultSet rows = read.executeQuery()) {
            rows.next();
            return rows.getBoolean(1);
        }
    }

    /**
     * After a migration, no role except the running migration owner may write the recorded
     * history. This is swept by privilege, not by name: whatever a deployment granted any role
     * before the operator ran, the audit chain, the migration history and the manifest end up
     * writable only by the authority that maintains them, so the application can never inherit a
     * second migration authority through a grant nobody remembered.
     */
    private static void stripForeignHistoryWrites(Connection connection) throws SQLException {
        for (String table : List.of("schema_events", "flyway_schema_history", "import_manifest")) {
            execute(connection, "REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER "
                    + "ON TABLE " + table + " FROM PUBLIC");
            for (String holder : historyWriteHolders(connection, table)) {
                execute(connection, "REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER "
                        + "ON TABLE " + table + " FROM " + holder + " CASCADE");
            }
        }
    }

    /** The roles holding any write-shaped privilege on one history table, named and quoted. */
    private static List<String> historyWriteHolders(Connection connection, String table)
            throws SQLException {
        String current = currentUserName(connection);
        List<String> holders = new ArrayList<>();
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT DISTINCT pg_get_userbyid(item.grantee) FROM pg_class rel "
                        + "JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                        + "CROSS JOIN LATERAL aclexplode(COALESCE(rel.relacl, ARRAY[]::aclitem[])) item "
                        + "WHERE ns.nspname = 'public' AND rel.relname = ? AND item.grantee <> 0 "
                        + "AND item.privilege_type IN "
                        + "('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER')")) {
            read.setString(1, table);
            try (ResultSet rows = read.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString(1);
                    if (!name.equalsIgnoreCase(current) && !name.startsWith("pg_")) {
                        holders.add("\"" + name.replace("\"", "\"\"") + "\"");
                    }
                }
            }
        }
        return holders;
    }

    private static String currentUserName(Connection connection) throws SQLException {
        try (PreparedStatement read = connection.prepareStatement("SELECT current_user");
             ResultSet rows = read.executeQuery()) {
            rows.next();
            return rows.getString(1);
        }
    }

    /** True when the role named for the grants is the one this operator session connected as. */
    private static boolean isConnectedRole(Connection connection, String role) throws SQLException {
        try (PreparedStatement read = connection.prepareStatement("SELECT current_user");
             ResultSet rows = read.executeQuery()) {
            rows.next();
            String connected = rows.getString(1).replace("\"", "");
            return connected.equalsIgnoreCase(role);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Connection connect(Target target) throws SQLException {
        return DriverManager.getConnection(target.url(), target.username(), target.password());
    }

    private static long historyCount(Target target) {
        try (Connection connection = connect(target);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history")) {
            return rows.next() ? rows.getLong(1) : 0;
        } catch (SQLException missing) {
            return 0;
        }
    }

    private static long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getLong(1) : 0;
        }
    }

    /**
     * Open the backup so SQLite itself can never touch it.
     *
     * The import reads a private copy of the file together with its sidecars: no statement,
     * recovery step, journal or shared-memory file can then reach the immutable source, whether it
     * sits on a read-only mount, carries no write bits, or was copied while a WAL still held
     * committed rows. The sidecars matter because the newest committed rows of a quiesced backup can
     * live only in the WAL, and reading the main file alone would import them as absent. The copy
     * lives in a private directory that is deleted when the source closes, and the byte bound keeps
     * copying itself bounded. Where no scratch space exists at all, the source is opened in place as
     * a handle SQLite cannot write through, and the bytes it holds are read the same way.
     */
    private static LegacySource openLegacy(Path backup) {
        requireBoundedBackup(backup);
        try {
            Path directory = Files.createTempDirectory("agora-legacy-");
            Path copy = directory.resolve(backup.getFileName().toString());
            Files.copy(backup, copy);
            for (String sidecar : BACKUP_SIDECARS) {
                Path sourceSidecar = backup.resolveSibling(backup.getFileName() + sidecar);
                if (Files.isRegularFile(sourceSidecar)) {
                    Files.copy(sourceSidecar, directory.resolve(copy.getFileName() + sidecar));
                }
            }
            return new LegacySource(DriverManager.getConnection("jdbc:sqlite:" + copy), directory);
        } catch (IOException | SQLException copyFailed) {
            return openSourceInPlace(backup);
        }
    }

    /**
     * The fallback: a handle on the source that cannot write it - the driver's own read-only open
     * mode, then {@code query_only} as a second lock - and the scratch is dropped if it was made.
     */
    private static LegacySource openSourceInPlace(Path backup) {
        java.util.Properties properties = new java.util.Properties();
        // SQLITE_OPEN_READONLY, the flag sqlite-jdbc's own read-only setting writes.
        properties.setProperty("open_mode", "1");
        Connection connection;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + backup, properties);
        } catch (SQLException readOnlyUnsupported) {
            throw new Refusal("backup_unreadable");
        }
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA query_only = ON");
        } catch (SQLException error) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // The handle is dropped; the source was never written by it.
            }
            throw new Refusal("backup_unreadable");
        }
        return new LegacySource(connection, null);
    }

    /** The bound on the backup and its sidecars together: a bounded job copies a bounded file. */
    private static void requireBoundedBackup(Path backup) {
        long bytes;
        try {
            bytes = Files.size(backup);
            for (String sidecar : BACKUP_SIDECARS) {
                Path neighbour = backup.resolveSibling(backup.getFileName() + sidecar);
                if (Files.isRegularFile(neighbour)) {
                    bytes += Files.size(neighbour);
                }
            }
        } catch (IOException unreadable) {
            throw new Refusal("backup_unreadable");
        }
        if (bytes > MAX_IMPORT_BYTES) {
            throw new Refusal("import_too_large");
        }
    }

    /** The source to read from: SQLite's handle, plus the private copy's directory if there is one. */
    private record LegacySource(Connection connection, Path scratch) implements AutoCloseable {
        /** Close the source and drop the private copy; the immutable backup itself is never touched. */
        @Override
        public void close() throws SQLException {
            connection.close();
            deleteQuietly(scratch);
        }
    }

    private static void deleteQuietly(Path scratch) {
        if (scratch == null) {
            return;
        }
        try (var entries = Files.walk(scratch)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {
            // A private copy that cannot be deleted cannot affect the immutable source.
        }
    }
}
