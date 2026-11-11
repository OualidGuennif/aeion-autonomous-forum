package local.aeion.forum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Feature 002: the one migration engine, with no CDI in it so a test can drive it directly.
 *
 * Since the PostgreSQL cutover the engine is Flyway: every migration is a versioned SQL file under
 * {@code classpath:db/migration}, applied in order, recorded in {@code flyway_schema_history}, and
 * checksummed. The custom SQLite runner this class once held is gone - keeping two authorities
 * would let a database drift between them. The application, the test harness and the operator job
 * all call this same code, so a test proves the schema a deployment actually gets.
 */
public final class Migrations {

    static final String LOCATION = "classpath:db/migration";

    private Migrations() {
    }

    /** The engine for a datasource, the way every caller reaches Flyway. */
    public static Flyway engine(DataSource source) {
        return Flyway.configure().dataSource(source).locations(LOCATION).load();
    }

    /** The engine for explicit credentials, the way the isolated operator job reaches Flyway. */
    public static Flyway engine(String url, String username, String password) {
        return Flyway.configure().dataSource(url, username, password).locations(LOCATION).load();
    }

    /** Apply every pending migration; returns how many were applied now. */
    public static long apply(DataSource source) {
        long before = appliedCount(source);
        engine(source).migrate();
        return appliedCount(source) - before;
    }

    /**
     * Refuse anything the running code does not recognise: an altered checksum, a schema newer
     * than the code, a failed migration, or a pending one. The application never migrates; a
     * database behind the code is an operator's repair.
     */
    public static void validate(DataSource source) {
        validate(engine(source));
    }

    public static void validate(Flyway flyway) {
        flyway.validate();
        // Flyway's default validation ignores a database that is ahead of the code; the cutover
        // gate and the application boot must refuse it instead. Any event this code never shipped,
        // a future version or one whose resolution is gone, stops here before the pending check.
        for (MigrationInfo info : flyway.info().all()) {
            String state = String.valueOf(info.getState());
            if (state.startsWith("FUTURE_") || state.startsWith("MISSING_")) {
                throw new IllegalStateException("schema_ahead: applied migration " + info.getVersion()
                        + " is not in the shipped migrations");
            }
        }
        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            throw new IllegalStateException("schema_behind: the schema is pending migration; run the operator");
        }
    }

    /** The applied history rows, oldest first, in the shape {@code /api/schema} reports. */
    public static List<Map<String, Object>> history(DataSource source) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String query = "SELECT version, script, installed_on FROM flyway_schema_history ORDER BY installed_rank";
        try (Connection connection = source.getConnection();
             PreparedStatement read = connection.prepareStatement(query);
             ResultSet result = read.executeQuery()) {
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("version", result.getString(1));
                row.put("name", result.getString(2));
                row.put("applied_ns", result.getTimestamp(3).getTime() * 1_000_000L);
                rows.add(row);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("schema_unreadable", error);
        }
        return rows;
    }

    private static long appliedCount(DataSource source) {
        try (Connection connection = source.getConnection();
             PreparedStatement read = connection.prepareStatement(
                     "SELECT COUNT(*) FROM flyway_schema_history");
             ResultSet rows = read.executeQuery()) {
            return rows.next() ? rows.getLong(1) : 0;
        } catch (SQLException missing) {
            return 0;
        }
    }
}
