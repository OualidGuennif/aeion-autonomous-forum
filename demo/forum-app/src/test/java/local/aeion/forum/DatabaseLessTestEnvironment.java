package local.aeion.forum;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Feature 001: boot with only a session secret and an unreachable database.
 *
 * The changed JDBC URL proves the health path never opens a connection: if any startup observer,
 * ORM bootstrap or scheduled drain touched the datasource, this application would fail to start.
 */
public class DatabaseLessTestEnvironment implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        String unreachable = "jdbc:sqlite:/proc/agora-database-less/never.db";
        return Map.of(
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef",
                "AGORA_DATABASE_ENABLED", "false",
                "AEION_SQLITE_PATH", "/proc/agora-database-less/never.db",
                "QUARKUS_DATASOURCE_JDBC_URL", unreachable);
    }

    @Override
    public void stop() {
        // nothing to clean up: the test must never have created the database it names
    }
}
