// Spec: knowledge/components/agora-product.md
package local.aeion.forum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

/**
 * Feature 002: the least-privilege grant is a startup condition, not a convention.
 *
 * The operator grants the application role DML on the product tables and nothing else. This guard
 * verifies that grant from the application's own connection before it serves: a role that can
 * write the audit chain, the migration history or the import manifest, or create schema objects,
 * is a migration authority in disguise, and the boot refuses it instead of serving through it.
 * Every refusal names the privilege and the table, never a value read from the database.
 */
final class AppRoleGuard {

    /** The recorded-history tables: writing any of them rewrites what already happened. */
    private static final List<String> HISTORY_TABLES =
            List.of("schema_events", "flyway_schema_history", "import_manifest");

    private static final List<String> WRITE_PRIVILEGES = List.of("INSERT", "UPDATE", "DELETE", "TRUNCATE");

    private AppRoleGuard() {
    }

    /** Refuse a connected role that can modify history or perform schema DDL; allow a least-privilege one. */
    static void requireLeastPrivilege(DataSource source) {
        try (Connection connection = source.getConnection()) {
            for (String table : HISTORY_TABLES) {
                for (String privilege : WRITE_PRIVILEGES) {
                    if (holds(connection, "SELECT has_table_privilege(?, ?)", table, privilege)) {
                        throw new IllegalStateException(
                                "app_role_not_least_privilege: " + privilege + " on " + table);
                    }
                }
            }
            if (holds(connection, "SELECT has_schema_privilege('public', 'CREATE')")) {
                throw new IllegalStateException("app_role_not_least_privilege: CREATE on schema public");
            }
            if (holds(connection, "SELECT has_database_privilege(current_database(), 'CREATE')")) {
                throw new IllegalStateException("app_role_not_least_privilege: CREATE on the database");
            }
        } catch (SQLException error) {
            throw new IllegalStateException("app_role_privileges_unknown", error);
        }
    }

    private static boolean holds(Connection connection, String query, String... parameters)
            throws SQLException {
        try (PreparedStatement read = connection.prepareStatement(query)) {
            for (int index = 0; index < parameters.length; index++) {
                read.setString(index + 1, parameters[index]);
            }
            try (ResultSet rows = read.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        }
    }
}
