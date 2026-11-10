package local.aeion.forum;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Feature 001: a database-less boot whose datasource is deliberately invalid and unreachable.
 * Liveness must not depend on the datasource in any way, so this boots with a session secret and a
 * URL no driver could ever open; the same email contract as a broken operator environment.
 */
public class UnreachableDatasourceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef",
                "agora.database.enabled", "false",
                "%test.agora.database.enabled", "false",
                "AEION_SQLITE_PATH", "/no-such-directory/agora-broken.db");
    }
}
