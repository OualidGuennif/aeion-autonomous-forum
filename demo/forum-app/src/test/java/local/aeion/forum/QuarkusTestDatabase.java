package local.aeion.forum;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector;
import java.util.Map;
import java.util.Random;

/**
 * Feature 003 for a running application, on PostgreSQL since the 002 cutover: every
 * {@code @QuarkusTest} class reaches its own disposable database, migrated once by the operator
 * code and reached through a least-privilege application role, instead of sharing state with any
 * other suite.
 *
 * The URL is handed to Quarkus before the application starts, which is why this is a lifecycle
 * manager rather than a field: the datasource is built once, at boot. Nothing here is reachable
 * from the shipped jar; it only exists on the test classpath.
 *
 * Injection follows the official Quarkus lifecycle contract: {@link #inject(TestInjector)} is
 * implemented rather than inherited, so a test class that declares the resource as a field gets it
 * through Quarkus' own injector, and {@link #database()} carries the disposable database that
 * HTTP and async tests clean up explicitly when a client rollback cannot cover the server.
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

    /**
     * The SPI hook Quarkus calls to hand this resource to the test class. Delegating to the
     * interface's own default keeps the official behaviour — the resource is injected into the
     * fields that declare it — and states here that the resource owns that contract instead of
     * relying on a test to reach the database some other way.
     */
    @Override
    public void inject(TestInjector testInjector) {
        QuarkusTestResourceLifecycleManager.super.inject(testInjector);
    }

    /**
     * The legacy hook, kept as an explicit no-op: nothing is injected outside the SPI injector.
     * Stated here as well so a test that reaches the resource the old way still gets the documented
     * behaviour — configuration from {@code start()}, fields through {@code inject(TestInjector)}.
     */
    public void inject(Object testInstance) {
        // Empty on purpose. The datasource settings arrive from start(); the resource's own fields
        // arrive through the Quarkus injector. Assigning a field from here would bypass both.
    }

    /** The database this resource owns, for a test that must clean up past a client rollback. */
    public EphemeralDatabase database() {
        return database;
    }

    /** The database this resource owns, or the refusal that says no suite is running. */
    private EphemeralDatabase owned() {
        EphemeralDatabase current = database;
        if (current == null) {
            throw new IllegalStateException("test_database_not_started");
        }
        return current;
    }

    /** The frozen clock of the owned database; a suite that needs the harness instant asks for it. */
    public ForumClock clock() {
        return owned().clock();
    }

    /** The owned database's seeded source, the deterministic twin of {@link #clock()}. */
    public Random random() {
        return owned().random();
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
