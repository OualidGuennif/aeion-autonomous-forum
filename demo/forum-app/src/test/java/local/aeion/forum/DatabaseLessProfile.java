package local.aeion.forum;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Feature 001: a database-less boot for tests that prove the forum serves on the secret alone.
 *
 * The override names both the plain key and the {@code %test} key: a resource lifecycle manager
 * writes system properties (ordinal 400) above profile overrides (ordinal 275), so any future
 * {@code AGORA_DATABASE_ENABLED} set globally cannot silently re-enable the database here either.
 * No {@code QuarkusTestDatabase} resource is attached: nothing provides a database to this profile.
 */
public class DatabaseLessProfile implements QuarkusTestProfile {

    /** A 37-byte, non-placeholder test secret, exactly what a minimal deployment is allowed to have. */
    static final String SECRET = "db-less-suite-secret-0123456789abcdef";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "agora.session.secret", SECRET,
                "agora.database.enabled", "false",
                "%test.agora.database.enabled", "false");
    }
}
