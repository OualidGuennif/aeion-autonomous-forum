package local.aeion.forum;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Feature 001: boot the whole application database-less, the way it starts in production with only
 * a session secret. The overrides win over both the plain configuration key and its %test form
 * (a QuarkusTestResource would set system properties at ordinal 400, so it sets neither key here),
 * and the secret meets the production contract: 32+ bytes, no placeholder.
 */
public class DatabaselessProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef",
                "agora.database.enabled", "false",
                "%test.agora.database.enabled", "false");
    }
}
