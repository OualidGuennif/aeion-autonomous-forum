package local.aeion.forum;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Feature 003 for a running application: every {@code @QuarkusTest} class reaches its own
 * ephemeral database instead of the developer's {@code /tmp/agora.db}.
 *
 * The URL is handed to Quarkus before the application starts, which is why this is a lifecycle
 * manager rather than a field: the datasource is built once, at boot. Nothing here is reachable
 * from the shipped jar; it only exists on the test classpath.
 */
public class QuarkusTestDatabase implements QuarkusTestResourceLifecycleManager {

    private EphemeralDatabase database;

    @Override
    public Map<String, String> start() {
        database = EphemeralDatabase.create();
        return Map.of(
                "AEION_SQLITE_PATH", database.file().toString(),
                "AEION_RELEASE", "test-ephemeral",
                // The application refuses to start without a real secret; a test gets a fixed one.
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef",
                // These suites open their own database-backed plane, so the gate says so explicitly
                // (feature 001): the same-fact deployment without the key would boot database-less.
                "AGORA_DATABASE_ENABLED", "true",
                // A database-backed deployment must name its PostgreSQL connection settings (they
                // are validated as present and carried for the cutover; this suite keeps the
                // committed SQLite plane through AEION_SQLITE_PATH, so the settings are never dialed).
                "AGORA_DATABASE_URL", "jdbc:postgresql://disposable-test.invalid:5432/agora_test",
                "AGORA_DATABASE_USER", "agora_test",
                "AGORA_DATABASE_PASSWORD", "disposable-test-password");
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
