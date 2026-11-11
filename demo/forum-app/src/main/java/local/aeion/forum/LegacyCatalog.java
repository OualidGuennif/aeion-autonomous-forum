package local.aeion.forum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature 002: the one description of the imported tables, shared by the import and its verifier.
 *
 * Every digest is computed over a canonical serialisation: fixed column order, cells joined by
 * unit separators, rows sorted by primary-key bytes in Java. Neither database's collation is
 * trusted, so an import over SQLite and a re-check over PostgreSQL always agree. The audit chain
 * links every schema_events row to its predecessor by digest.
 */
final class LegacyCatalog {

    /** What a column the snapshot does not carry imports as: never guessed, only declared. */
    enum Absent {
        /** The column is required: an absent one is a shape the import refuses. */
        REQUIRED,
        /** The column is nullable in the target, so an absent one imports as no value. */
        NULL,
        /** The target gives it a default, so an absent one imports as that default. */
        ZERO
    }

    /** One column; {@code number} marks the epoch-nanosecond and counter columns. */
    record Column(String name, boolean number, Absent absent) {
        Column(String name, boolean number) {
            this(name, number, Absent.REQUIRED);
        }
    }

    /** One imported table: its key and its columns in canonical order. */
    record Table(String name, String key, List<Column> columns) {
        List<String> names() {
            return columns.stream().map(Column::name).toList();
        }
    }

    static final String GENESIS = "0".repeat(64);

    static final Table SETTINGS = new Table("settings", "name", List.of(
            new Column("name", false), new Column("value", false), new Column("updated_ns", true)));
    static final Table ACCOUNTS = new Table("accounts", "id", List.of(
            new Column("id", false), new Column("username", false), new Column("password", false),
            new Column("role", false), new Column("created_ns", true),
            new Column("locked_ns", true, Absent.NULL),
            new Column("failures", true, Absent.ZERO)));
    static final Table SESSIONS = new Table("sessions", "id", List.of(
            new Column("id", false), new Column("account_id", false), new Column("issued_ns", true),
            new Column("expires_ns", true), new Column("revoked_ns", true, Absent.NULL)));
    static final Table THREADS = new Table("threads", "id", List.of(
            new Column("id", false), new Column("title", false), new Column("body", false),
            new Column("author_id", false), new Column("created_ns", true)));
    static final Table EVENTS = new Table("schema_events", "id", List.of(
            new Column("id", true), new Column("at_ns", true), new Column("migration", false),
            new Column("detail", false, Absent.NULL)));
    static final Table OUTBOX = new Table("thread_outbox", "event_id", List.of(
            new Column("event_id", false), new Column("topic", false), new Column("payload", false),
            new Column("created_ns", true), new Column("attempts", true, Absent.ZERO),
            new Column("published_ns", true, Absent.NULL), new Column("last_error", false, Absent.NULL)));
    static final Table EVENT_LOG = new Table("thread_event_log", "event_id", List.of(
            new Column("event_id", false), new Column("topic", false), new Column("payload", false),
            new Column("received_ns", true)));

    static final List<Table> TABLES = List.of(
            SETTINGS, ACCOUNTS, SESSIONS, THREADS, EVENTS, OUTBOX, EVENT_LOG);

    private LegacyCatalog() {
    }

    /** The table with that name; only the catalogued tables ever exist here. */
    static Table table(String name) {
        return TABLES.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("catalog_unknown_table: " + name));
    }

    /** Every row of the table, sorted by primary key in Java: byte order for text, numeric else. */
    static List<Object[]> readRows(Connection connection, Table table) {
        return readRows(connection, table, table.names());
    }

    /**
     * The table's rows, read through an explicit projection.
     *
     * The projection is one expression per canonical column, in canonical order, so a snapshot that
     * predates a nullable or defaulted column still reads as the same row shape the verifier digests
     * later from PostgreSQL - the two sides always agree because the catalog, not the file, decides
     * the order. Column names here come from this catalog or from the snapshot's own schema.
     */
    static List<Object[]> readRows(Connection connection, Table table, List<String> projection) {
        String query = "SELECT " + String.join(", ", projection) + " FROM " + table.name();
        int key = table.columns().stream().map(Column::name).toList().indexOf(table.key());
        boolean numericKey = table.columns().get(key).number();
        List<Object[]> rows = new ArrayList<>();
        try (PreparedStatement read = connection.prepareStatement(query);
             ResultSet result = read.executeQuery()) {
            while (result.next()) {
                Object[] row = new Object[table.columns().size()];
                for (int column = 0; column < row.length; column++) {
                    if (!table.columns().get(column).number()) {
                        row[column] = result.getString(column + 1);
                    } else {
                        // wasNull keeps nullable counters (locks, revocations, publications) null.
                        long number = result.getLong(column + 1);
                        row[column] = result.wasNull() ? null : (Object) number;
                    }
                }
                rows.add(row);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("catalog_read_failed: " + table.name(), error);
        }
        // A numeric key orders by value, never by spelling: the audit chain (ids 1, 2, 10, ...)
        // must link chronologically, and a string sort would hash 1, 10, 11, ..., 2, 3 instead.
        rows.sort((Object[] left, Object[] right) -> numericKey
                ? Long.compare(((Number) left[key]).longValue(), ((Number) right[key]).longValue())
                : cell(left[key]).compareTo(cell(right[key])));
        return rows;
    }

    /** The canonical digest of the rows as they sit in the database, sorted, escaped, hashed. */
    static String digest(Table table, List<Object[]> rows) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(table.name()).append('(').append(String.join(",", table.names())).append(')');
        for (Object[] row : rows) {
            buffer.append('\u001e');
            for (Object value : row) {
                buffer.append('\u001f').append(cell(value));
            }
        }
        return sha256Hex(buffer.toString());
    }

    /** The digest of one audit-chain row, linked to its predecessor. */
    static String chainDigest(String previous, long id, long atNs, String kind, String detail) {
        return sha256Hex(previous + "|" + id + "|" + atNs + "|" + kind + "|" + (detail == null ? "" : detail));
    }

    private static String cell(Object value) {
        if (value == null) {
            return "\u0000";
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
        }
        String text = value.toString();
        return text.replace("\\", "\\\\")
                .replace("\u001e", "\\rs")
                .replace("\u001f", "\\us");
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
