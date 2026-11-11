package local.aeion.forum;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Feature 003 for a running application, on PostgreSQL since the 002 cutover: every
 * {@code @QuarkusTest} class reaches its own disposable database, migrated once by the operator
 * code and reached through a least-privilege application role, instead of sharing state with any
 * other suite.
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
                "quarkus.datasource.jdbc.url", appUrl(database.database()),
                "quarkus.datasource.username", database.database().replaceFirst("agora_test_", "agora_app_"),
                "quarkus.datasource.password", database.database().replaceFirst("agora_test_", "agora_app_") + "-pass",
                "AEION_RELEASE", "test-ephemeral",
                // The application refuses to start without a real secret; a test gets a fixed one.
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef");
    }

    private static String appUrl(String database) {
        String[] admin = EphemeralDatabase.adminCredentials.get();
        return EphemeralDatabase.withDatabase(admin[0], database);
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
