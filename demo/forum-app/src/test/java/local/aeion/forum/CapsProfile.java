package local.aeion.forum;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Feature 001: a deployment that pins the documented bounds through the environment. The override
 * names the environment keys directly, which is how an operator would supply them, and the typed
 * settings must map and bound them the same way. The boot stays database-less.
 */
public class CapsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "AGORA_SESSION_SECRET", "ephemeral-suite-secret-0123456789abcdef",
                "AGORA_REQUEST_MAX_BYTES", "1024",
                "AGORA_TEXT_MAX_CODEPOINTS", "400",
                "AGORA_RENDERED_MAX_BYTES", "1024",
                "agora.database.enabled", "false",
                "%test.agora.database.enabled", "false");
    }
}
