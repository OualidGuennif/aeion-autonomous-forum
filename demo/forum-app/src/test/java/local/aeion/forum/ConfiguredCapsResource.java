package local.aeion.forum;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Feature 001: a boot with lowered caps, so the tests prove the environment is read, not hardcoded.
 *
 * The byte cap is lowered to 1000, the normalized-text cap to 10 codepoints, and the rendered cap
 * is valid at its floor. The suite runs the database-backed plane, so the database flag and the
 * PostgreSQL settings are named as any deployment would name them.
 */
public class ConfiguredCapsResource implements QuarkusTestResourceLifecycleManager {

    static final long REQUEST_CAP = 1000;
    static final int TEXT_CAP = 10;

    private EphemeralDatabase database;

    @Override
    public Map<String, String> start() {
        database = EphemeralDatabase.create();
        return Map.ofEntries(
                Map.entry("AEION_SQLITE_PATH", database.file().toString()),
                Map.entry("AGORA_SESSION_SECRET", "caps-suite-secret-0123456789abcdeg"),
                Map.entry("AGORA_DATABASE_ENABLED", "true"),
                Map.entry("AGORA_DATABASE_URL", "jdbc:postgresql://disposable-test.invalid:5432/agora_test"),
                Map.entry("AGORA_DATABASE_USER", "agora_test"),
                Map.entry("AGORA_DATABASE_PASSWORD", "disposable-test-password"),
                Map.entry("AGORA_REQUEST_MAX_BYTES", "1000"),
                Map.entry("AGORA_TEXT_MAX_CODEPOINTS", "10"),
                Map.entry("AGORA_RENDERED_MAX_BYTES", "1"));
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
